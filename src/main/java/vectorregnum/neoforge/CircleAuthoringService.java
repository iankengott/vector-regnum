package vectorregnum.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import vectorregnum.core.CastResult;
import vectorregnum.core.Element;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.casting.CastQuote;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.core.circle.CircleAuthoringCompiler;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleDiagnostic;
import vectorregnum.core.circle.CircleEditorSession;
import vectorregnum.core.circle.CirclePersistence;
import vectorregnum.core.circle.CircleValue;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.core.circle.SpellArtifactPersistence;
import vectorregnum.core.circle.SpellMedium;
import vectorregnum.core.circle.Vm2CircleCompilation;
import vectorregnum.core.circle.Vm2CircleCompiler;
import vectorregnum.neoforge.editor.CircleEditorInteraction;
import vectorregnum.neoforge.editor.CircleEditorAnchor;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.neoforge.ponder.PonderTraceNetworking;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Player-facing, server-authoritative editor and spell-media integration. */
public final class CircleAuthoringService {
    private static final String ARTIFACT_KEY = "vector_regnum_artifact";
    private static final double EDITOR_ANCHOR_RANGE = 8.0;
    private static final Map<UUID, CircleEditorSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, CircleEditorAnchor> EDITOR_ANCHORS = new ConcurrentHashMap<>();

    private CircleAuthoringService() {
    }

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                SESSIONS.put(player.getUUID(), load(player));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            SESSIONS.remove(event.getEntity().getUUID());
            EDITOR_ANCHORS.remove(event.getEntity().getUUID());
        });
    }

    public static CircleEditorSession session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> load(player));
    }

    public static void newCircle(ServerPlayer player, String id) {
        replace(player, MagicCircle.empty(id, displayName(id), 3, 8));
        player.sendSystemMessage(Component.literal("New 3-ring circle: " + id)
                .withStyle(ChatFormatting.GOLD));
        showEditorPreview(player, session(player).current(), List.of());
    }

    public static void loadStarter(ServerPlayer player) {
        replace(player, starterCircle());
        player.sendSystemMessage(Component.literal("Loaded the editable Fire Aura example")
                .withStyle(ChatFormatting.GOLD));
        show(player);
    }

    public static void loadVmStarter(ServerPlayer player) {
        replace(player, vmStarterCircle());
        player.sendSystemMessage(Component.literal("Loaded a typed delayed Vector Step circle")
                .withStyle(ChatFormatting.GOLD));
        show(player);
    }

    public static boolean place(ServerPlayer player, int ring, int slot, String sigil) {
        CircleEditorSession.EditResult result = session(player).place(
                new CircleCoordinate(ring, slot), sigil.toUpperCase());
        finishEdit(player, result);
        return result.changed();
    }

    public static boolean parameterize(
            ServerPlayer player, int ring, int slot, String canonicalNumber) {
        CircleEditorSession.EditResult result;
        try {
            result = session(player).parameterize(new CircleCoordinate(ring, slot),
                    List.of(new CircleValue.NumberValue(canonicalNumber)));
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal("Invalid finite number: " + canonicalNumber)
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        finishEdit(player, result);
        return result.changed();
    }

    public static boolean parameterizeValues(
            ServerPlayer player, int ring, int slot, String rawValues) {
        try {
            List<CircleValue> values = CircleEditorInteraction.parseParameterInput(rawValues).stream()
                    .map(CircleAuthoringService::parseValue)
                    .toList();
            CircleEditorSession.EditResult result = session(player).parameterize(
                    new CircleCoordinate(ring, slot), values);
            finishEdit(player, result);
            return result.changed();
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal("Invalid parameter list: " + exception.getMessage())
                    .withStyle(ChatFormatting.RED));
            return false;
        }
    }

    public static boolean remove(ServerPlayer player, int ring, int slot) {
        CircleEditorSession.EditResult result = session(player).remove(new CircleCoordinate(ring, slot));
        finishEdit(player, result);
        return result.changed();
    }

    public static boolean move(ServerPlayer player, int sourceRing, int sourceSlot,
            int destinationRing, int destinationSlot) {
        CircleEditorSession.EditResult result = session(player).move(
                new CircleCoordinate(sourceRing, sourceSlot),
                new CircleCoordinate(destinationRing, destinationSlot));
        finishEdit(player, result);
        return result.changed();
    }

    public static boolean undo(ServerPlayer player) {
        CircleEditorSession.EditResult result = session(player).undo();
        finishEdit(player, result);
        return result.changed();
    }

    public static AuthoringCompilation compile(ServerPlayer player) {
        MagicCircle circle = session(player).current();
        if (Vm2CircleCompiler.isVm2Circle(circle)) {
            Vm2CircleCompilation typed = Vm2CircleCompiler.compile(circle,
                    vmContext(player, editorOrigin(player)));
            sendVmCompilation(player, typed);
            showEditorPreview(player, circle, typed.diagnostics());
            PonderTraceNetworking.publishCompilation(player, "server-compiler-trace",
                    circle.name() + " — authoritative compilation", typed);
            return new AuthoringCompilation(null, typed);
        }
        CircleCompilation legacy = session(player).compilePreview();
        sendCompilation(player, legacy);
        showEditorPreview(player, circle, legacy.diagnostics());
        return new AuthoringCompilation(legacy, null);
    }

    /** Captures the server's current block raycast; the client never supplies coordinates. */
    public static EditorAnchorResult captureEditorAnchor(ServerPlayer player) {
        HitResult rawHit = player.pick(EDITOR_ANCHOR_RANGE, 1.0F, false);
        if (!(rawHit instanceof BlockHitResult hit) || rawHit.getType() != HitResult.Type.BLOCK) {
            return EditorAnchorResult.rejected("Look at a block face within 8 blocks, then try again");
        }
        BlockPos position = hit.getBlockPos();
        if (!player.serverLevel().hasChunkAt(position)
                || player.serverLevel().getBlockState(position).isAir()) {
            return EditorAnchorResult.rejected("The targeted block face is not loaded or solid");
        }
        CircleEditorAnchor anchor = new CircleEditorAnchor(
                player.serverLevel().dimension().location().toString(),
                position.getX(), position.getY(), position.getZ(),
                CircleEditorAnchor.Face.valueOf(hit.getDirection().name()));
        EDITOR_ANCHORS.put(player.getUUID(), anchor);
        showEditorPreview(player, session(player).current(), List.of());
        return EditorAnchorResult.accepted("Anchored to " + anchor.description());
    }

    public static EditorAnchorResult clearEditorAnchor(ServerPlayer player) {
        CircleEditorAnchor removed = EDITOR_ANCHORS.remove(player.getUUID());
        showEditorPreview(player, session(player).current(), List.of());
        return removed == null
                ? EditorAnchorResult.rejected("No editor anchor was set")
                : EditorAnchorResult.accepted("World-face anchor cleared");
    }

    public static String editorAnchorDescription(ServerPlayer player) {
        return validEditorAnchor(player).map(CircleEditorAnchor::description).orElse("");
    }

    public static boolean cast(ServerPlayer player) {
        return activateCircleAt(player, session(player).current(), true, player.getEyePosition(),
                CastingMethod.BARE, true, ItemStack.EMPTY, ignored -> { });
    }

    public static boolean ritual(ServerPlayer player) {
        return activateCircleAt(player, session(player).current(), true, player.getEyePosition(),
                CastingMethod.RITUAL, true, ItemStack.EMPTY, ignored -> { });
    }

    public static Optional<CastQuote> quote(ServerPlayer player, CastingMethod method) {
        MagicCircle circle = session(player).current();
        if (Vm2CircleCompiler.isVm2Circle(circle)) {
            Vm2CircleCompilation compilation = Vm2CircleCompiler.compile(circle,
                    vmContext(player, editorOrigin(player)));
            sendVmCompilation(player, compilation);
            if (compilation.hasErrors()) return Optional.empty();
            var program = compilation.compiledProgram().orElseThrow();
            Element element = spellElement(compilation.executionOrder());
            CastCost baseline = CastingResourceService.baseline(method,
                    ManaData.adjustedCost(player, program.manaCost().total(), element),
                    program.instructions().size(),
                    ManaData.adjustedUpkeep(player, program.manaCost().duration(), element),
                    ManaData.instability(player, element));
            return Optional.of(CastingResourceService.quoteAndReport(player, method, baseline, true));
        }
        CircleCompilation compilation = CircleAuthoringCompiler.compile(circle);
        sendCompilation(player, compilation);
        if (compilation.hasErrors()) return Optional.empty();
        Element element = spellElement(compilation.executionOrder());
        CastCost baseline = CastingResourceService.baseline(method,
                ManaData.adjustedCost(player, compilation.compiledSpell().totalManaCost(), element),
                compilation.compiledSpell().instructionCount(), 0.0,
                ManaData.instability(player, element));
        return Optional.of(CastingResourceService.quoteAndReport(player, method, baseline, true));
    }

    public static void show(ServerPlayer player) {
        MagicCircle circle = session(player).current();
        player.sendSystemMessage(Component.literal(circle.name() + " • " + circle.ringCount() + " rings • "
                        + circle.sigils().size() + " sigils")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (circle.sigils().isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "Empty — use /vectorregnum circle place <ring> <slot> <sigil>"));
        } else {
            for (int index = 0; index < circle.executionOrder().size(); index++) {
                PlacedSigil sigil = circle.executionOrder().get(index);
                String parameters = sigil.parameters().isEmpty() ? "" : " " + sigil.parameters();
                player.sendSystemMessage(Component.literal(String.format("%d. r%d:s%d  %s%s", index + 1,
                        sigil.coordinate().ring(), sigil.coordinate().clockwiseSlot(),
                        sigil.type(), parameters)).withStyle(ChatFormatting.GRAY));
            }
        }
        showEditorPreview(player, circle, List.of());
    }

    public static boolean giveMedium(ServerPlayer player, SpellMedium medium) {
        AuthoringCompilation compilation = compile(player);
        if (compilation.hasErrors()) {
            return false;
        }
        MagicCircle circle = session(player).current();
        ItemStack blank = new ItemStack(switch (medium) {
            case SCROLL -> SpellMediaContent.spellScroll();
            case BOOK -> SpellMediaContent.spellBook();
            case ENGRAVING -> SpellMediaContent.engravedSpellCircleItem();
            case TABLET -> SpellMediaContent.carvedTabletItem();
        });
        if (!player.isCreative() && !consumeBlank(player, blank)) {
            player.sendSystemMessage(Component.literal("Craft a blank " + medium.name().toLowerCase()
                            + " before binding")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        String id = "artifact-" + UUID.randomUUID();
        SpellArtifact artifact = switch (medium) {
            case SCROLL -> SpellArtifact.scroll(id, circle);
            case BOOK -> SpellArtifact.book(id, circle);
            case ENGRAVING -> SpellArtifact.engraving(id, circle);
            case TABLET -> SpellArtifact.tablet(id, circle);
        };
        ItemStack stack = createArtifactStack(artifact);
        player.getInventory().placeItemBackInInventory(stack);
        player.sendSystemMessage(Component.literal("Bound " + circle.name() + " into a "
                        + medium.name().toLowerCase())
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    static InteractionResultHolder<ItemStack> useHandheldArtifact(
            Player player, Level world, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(SpellMediaContent.spellScroll())
                && !stack.is(SpellMediaContent.spellBook())) {
            return InteractionResultHolder.pass(stack);
        }
        if (world.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.fail(stack);
        }
        Optional<SpellArtifact> decoded = readArtifact(stack);
        if (decoded.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.literal("This medium contains no valid spell")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        SpellArtifact artifact = decoded.orElseThrow();
        Item mediumItem = stack.getItem();
        if (artifact.state() == SpellArtifact.State.CONSUMED) {
            serverPlayer.sendSystemMessage(Component.literal("This scroll has already been consumed")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        CastingMethod method = artifact.medium() == SpellMedium.SCROLL
                ? CastingMethod.SCROLL : CastingMethod.SPELLBOOK;
        Consumer<ResourceEscrow.Outcome> terminal = outcome -> {
            if (outcome == ResourceEscrow.Outcome.SUCCESS && artifact.medium() == SpellMedium.BOOK) {
                writeArtifact(stack, artifact.recordSuccessfulActivation().artifact());
            }
            if (outcome.consumesResources() && artifact.medium() == SpellMedium.SCROLL) {
                serverPlayer.sendSystemMessage(Component.literal(
                                outcome == ResourceEscrow.Outcome.SUCCESS
                                        ? "The successful scroll burns into silver ash"
                                        : "The fault consumes the committed scroll")
                        .withStyle(ChatFormatting.GOLD));
            }
        };
        if (activateCircleAt(serverPlayer, artifact.circle(), true, serverPlayer.getEyePosition(),
                method, true, stack, terminal)) {
            player.getCooldowns().addCooldown(mediumItem, 20);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    public static boolean activateCircleAt(ServerPlayer player, MagicCircle circle,
            boolean chargeMana, Vec3 origin) {
        return activateCircleAt(player, circle, chargeMana, origin,
                CastingMethod.BARE, true, ItemStack.EMPTY, ignored -> { });
    }

    public static boolean activateCircleAt(ServerPlayer player, MagicCircle circle,
            boolean chargeMana, Vec3 origin, CastingMethod method, boolean useStaged,
            ItemStack mediumStack, Consumer<ResourceEscrow.Outcome> terminal) {
        if (Vm2CircleCompiler.isVm2Circle(circle)) {
            Vm2CircleCompilation compilation = Vm2CircleCompiler.compile(circle, vmContext(player, origin));
            sendVmCompilation(player, compilation);
            SpellVisualManager.showAuthoredCircleAt(player, circle, compilation.diagnostics(), origin);
            if (compilation.hasErrors()) {
                PonderTraceNetworking.publishCompilation(player, "server-compiler-fault",
                        circle.name() + " — compiler fault", compilation);
            }
            return !compilation.hasErrors() && NeoForgeVmService.startAuthored(player,
                    compilation, chargeMana, circle.name(), method, useStaged,
                    mediumStack, terminal);
        }
        CircleCompilation compilation = CircleAuthoringCompiler.compile(circle);
        sendCompilation(player, compilation);
        SpellVisualManager.showAuthoredCircleAt(player, circle, compilation.diagnostics(), origin);
        if (compilation.hasErrors()) return false;
        CastResult result = CastService.castAt(player, compilation.compatibilitySource(), chargeMana,
                origin, player.getViewVector(1.0F), method, useStaged, mediumStack);
        PonderTraceNetworking.publishCompatibility(player, "server-authored-compatibility-trace",
                circle.name() + " — authoritative result", compilation, result);
        ResourceEscrow.Outcome outcome = result instanceof CastResult.Success
                ? ResourceEscrow.Outcome.SUCCESS
                : result instanceof CastResult.SpellFailure
                        ? ResourceEscrow.Outcome.GENUINE_SPELL_FAULT
                        : ResourceEscrow.Outcome.ENGINE_FAILURE;
        terminal.accept(outcome);
        return !(result instanceof CastResult.EngineFailure);
    }

    private static Element spellElement(List<PlacedSigil> sigils) {
        return sigils.stream().map(PlacedSigil::type)
                .filter(type -> type.startsWith("ELEMENT_"))
                .map(type -> type.substring("ELEMENT_".length()))
                .map(Element::fromId).flatMap(Optional::stream).findFirst()
                .orElse(Element.ARCANE);
    }

    public static ItemStack createArtifactStack(SpellArtifact artifact) {
        ItemStack stack = switch (artifact.medium()) {
            case SCROLL -> new ItemStack(SpellMediaContent.spellScroll());
            case BOOK -> new ItemStack(SpellMediaContent.spellBook());
            case ENGRAVING -> new ItemStack(SpellMediaContent.engravedSpellCircleItem());
            case TABLET -> new ItemStack(SpellMediaContent.carvedTabletItem());
        };
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(artifact.circle().name() + " "
                + switch (artifact.medium()) {
                    case SCROLL -> "Scroll";
                    case BOOK -> "Spellbook";
                    case ENGRAVING -> "Engraving";
                    case TABLET -> "Tablet";
                }).withStyle(ChatFormatting.LIGHT_PURPLE));
        writeArtifact(stack, artifact);
        if (artifact.medium().installationRequired()) {
            CompoundTag blockData = new CompoundTag();
            blockData.putString(SpellTabletBlockEntity.PAYLOAD_KEY,
                    SpellArtifactPersistence.encode(artifact));
            BlockItem.setBlockEntityData(stack, SpellMediaContent.tabletBlockEntity(), blockData);
        }
        return stack;
    }

    public static Optional<SpellArtifact> readArtifact(ItemStack stack) {
        CustomData component = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!component.contains(ARTIFACT_KEY)) {
            return Optional.empty();
        }
        try {
            return Optional.of(SpellArtifactPersistence.decode(component.copyTag().getString(ARTIFACT_KEY)));
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Rejected corrupt spell medium", exception);
            return Optional.empty();
        }
    }

    private static boolean hasArtifactData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .contains(ARTIFACT_KEY);
    }

    private static void writeArtifact(ItemStack stack, SpellArtifact artifact) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                nbt -> nbt.putString(ARTIFACT_KEY, SpellArtifactPersistence.encode(artifact)));
    }

    private static boolean consumeBlank(ServerPlayer player, ItemStack expected) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(expected.getItem()) && !hasArtifactData(candidate)) {
                candidate.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static CircleEditorSession load(ServerPlayer player) {
        try {
            return new CircleEditorSession(CirclePersistence.decode(
                    player.getData(PlayerAttachmentContent.AUTHORED_CIRCLE)));
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Reset corrupt saved circle for {}",
                    player.getGameProfile().getName(), exception);
            MagicCircle fallback = starterCircle();
            player.setData(PlayerAttachmentContent.AUTHORED_CIRCLE, CirclePersistence.encode(fallback));
            return new CircleEditorSession(fallback);
        }
    }

    private static void replace(ServerPlayer player, MagicCircle circle) {
        SESSIONS.put(player.getUUID(), new CircleEditorSession(circle));
        persist(player);
    }

    private static void finishEdit(ServerPlayer player, CircleEditorSession.EditResult result) {
        if (result.changed()) {
            persist(player);
            player.sendSystemMessage(Component.literal(
                    "Circle updated • " + result.circle().sigils().size() + " sigils")
                    .withStyle(ChatFormatting.AQUA), true);
            showEditorPreview(player, result.circle(), List.of());
        } else {
            sendDiagnostics(player, result.diagnostics());
        }
    }

    private static void persist(ServerPlayer player) {
        player.setData(PlayerAttachmentContent.AUTHORED_CIRCLE,
                CirclePersistence.encode(session(player).current()));
    }

    private static void sendCompilation(ServerPlayer player, CircleCompilation compilation) {
        if (compilation.hasErrors()) {
            player.sendSystemMessage(Component.literal("CIRCLE REJECTED • " + compilation.diagnostics().size()
                            + " diagnostic(s)").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            sendDiagnostics(player, compilation.diagnostics());
        } else {
            player.sendSystemMessage(Component.literal("CIRCLE COMPILED • clockwise/outside-in • "
                            + compilation.executionOrder().size() + " instructions")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        }
    }

    private static void sendVmCompilation(
            ServerPlayer player, Vm2CircleCompilation compilation) {
        if (compilation.hasErrors()) {
            player.sendSystemMessage(Component.literal("VM2 CIRCLE REJECTED • "
                            + compilation.diagnostics().size() + " diagnostic(s)")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            sendDiagnostics(player, compilation.diagnostics());
            return;
        }
        var cost = compilation.compiledProgram().orElseThrow().manaCost();
        player.sendSystemMessage(Component.literal(String.format(java.util.Locale.ROOT,
                        "VM2 CIRCLE COMPILED • %d instructions • %.2f μ "
                                + "[work %.2f, range %.2f, time %.2f, rarity %.2f, "
                                + "memory %.2f, perception %.2f, control %.2f]",
                        compilation.executionOrder().size(), cost.total(), cost.physicalWork(),
                        cost.range(), cost.duration(), cost.rarity(), cost.memory(),
                        cost.perception(), cost.controlFlow()))
                .withStyle(ChatFormatting.GREEN));
    }

    private static void sendDiagnostics(ServerPlayer player, List<CircleDiagnostic> diagnostics) {
        for (CircleDiagnostic diagnostic : diagnostics) {
            String location = diagnostic.location().map(coordinate ->
                    " r" + coordinate.ring() + ":s" + coordinate.clockwiseSlot()).orElse("");
            player.sendSystemMessage(Component.literal(
                    diagnostic.code() + location + " — " + diagnostic.message())
                    .withStyle(diagnostic.severity() == CircleDiagnostic.Severity.ERROR
                            ? ChatFormatting.RED : ChatFormatting.YELLOW));
        }
    }

    static MagicCircle starterCircle() {
        return new MagicCircle(MagicCircle.CURRENT_SCHEMA_VERSION,
                "starter-fire-aura", "Starter Fire Aura", 3, 8, List.of(
                new PlacedSigil(new CircleCoordinate(0, 0), "ORIGIN_SELF"),
                new PlacedSigil(new CircleCoordinate(0, 1), "ELEMENT_FIRE"),
                new PlacedSigil(new CircleCoordinate(0, 2), "SHAPE_AURA"),
                new PlacedSigil(new CircleCoordinate(1, 0), "EXPAND",
                        List.of(new CircleValue.NumberValue("2.5"))),
                new PlacedSigil(new CircleCoordinate(2, 0), "EXECUTE")));
    }

    private static MagicCircle vmStarterCircle() {
        return new MagicCircle(MagicCircle.CURRENT_SCHEMA_VERSION,
                "typed-vector-step", "Typed Vector Step", 3, 8, List.of(
                new PlacedSigil(new CircleCoordinate(0, 0), "VM_DURATION",
                        List.of(new CircleValue.NumberValue("1"))),
                new PlacedSigil(new CircleCoordinate(0, 1), "VM_DELAY",
                        List.of(new CircleValue.NumberValue("20"))),
                new PlacedSigil(new CircleCoordinate(0, 2), "VM_PUSH_SELF"),
                new PlacedSigil(new CircleCoordinate(0, 3), "VM_PUSH_LOOK"),
                new PlacedSigil(new CircleCoordinate(0, 4), "VM_PUSH_NUMBER",
                        List.of(new CircleValue.NumberValue("1.2"))),
                new PlacedSigil(new CircleCoordinate(0, 5), "VM_MULTIPLY"),
                new PlacedSigil(new CircleCoordinate(1, 0), "VM_IMPULSE", List.of(
                        new CircleValue.NumberValue("20"), new CircleValue.NumberValue("0"))),
                new PlacedSigil(new CircleCoordinate(2, 0), "EXECUTE")));
    }

    private static CircleValue parseValue(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            return new CircleValue.BooleanValue(Boolean.parseBoolean(raw));
        }
        if (raw.startsWith("text:")) {
            return new CircleValue.TextValue(raw.substring("text:".length()));
        }
        return new CircleValue.NumberValue(raw);
    }

    private static Optional<CircleEditorAnchor> validEditorAnchor(ServerPlayer player) {
        CircleEditorAnchor anchor = EDITOR_ANCHORS.get(player.getUUID());
        if (anchor == null) {
            return Optional.empty();
        }
        String currentDimension = player.serverLevel().dimension().location().toString();
        BlockPos position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        if (!anchor.dimension().equals(currentDimension)
                || !player.serverLevel().hasChunkAt(position)
                || player.serverLevel().getBlockState(position).isAir()) {
            EDITOR_ANCHORS.remove(player.getUUID(), anchor);
            return Optional.empty();
        }
        return Optional.of(anchor);
    }

    private static Vec3 editorOrigin(ServerPlayer player) {
        return validEditorAnchor(player).map(CircleAuthoringService::anchorCenter)
                .orElseGet(player::getEyePosition);
    }

    private static Vec3 anchorCenter(CircleEditorAnchor anchor) {
        return Vec3.atCenterOf(new BlockPos(anchor.x(), anchor.y(), anchor.z())).add(
                anchor.face().offsetX() * 0.505,
                anchor.face().offsetY() * 0.505,
                anchor.face().offsetZ() * 0.505);
    }

    private static void showEditorPreview(ServerPlayer player, MagicCircle circle,
            List<CircleDiagnostic> diagnostics) {
        Optional<CircleEditorAnchor> anchor = validEditorAnchor(player);
        if (anchor.isEmpty()) {
            SpellVisualManager.showAuthoredCircle(player, circle, diagnostics);
            return;
        }
        CircleEditorAnchor fixed = anchor.orElseThrow();
        SpellVisualManager.showAuthoredCircleOnFace(player, circle, diagnostics,
                anchorCenter(fixed), Direction.valueOf(fixed.face().name()));
    }

    private static Vm2CircleCompiler.Context vmContext(ServerPlayer player, Vec3 origin) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        return new Vm2CircleCompiler.Context(player.getStringUUID(),
                new Vector3(origin.x, origin.y, origin.z), new Vector3(look.x, look.y, look.z));
    }

    public record AuthoringCompilation(
            CircleCompilation compatibility, Vm2CircleCompilation vm2) {
        public AuthoringCompilation {
            if ((compatibility == null) == (vm2 == null)) {
                throw new IllegalArgumentException("exactly one compiler result is required");
            }
        }

        public boolean hasErrors() {
            return compatibility != null ? compatibility.hasErrors() : vm2.hasErrors();
        }

        public List<CircleDiagnostic> diagnostics() {
            return compatibility != null ? compatibility.diagnostics() : vm2.diagnostics();
        }
    }

    public record EditorAnchorResult(boolean accepted, String message) {
        public EditorAnchorResult {
            if (message == null || message.isBlank() || message.length() > 256) {
                throw new IllegalArgumentException("anchor result needs a bounded message");
            }
        }

        public static EditorAnchorResult accepted(String message) {
            return new EditorAnchorResult(true, message);
        }

        public static EditorAnchorResult rejected(String message) {
            return new EditorAnchorResult(false, message);
        }
    }

    private static String displayName(String id) {
        String[] words = id.replace('.', '-').replace('_', '-').split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? "Untitled Circle" : result.toString();
    }
}
