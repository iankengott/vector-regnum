package vectorregnum.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import vectorregnum.core.CastResult;
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
import vectorregnum.core.vm2.Vector3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player-facing, server-authoritative editor and spell-media integration. */
public final class CircleAuthoringService {
    private static final String ARTIFACT_KEY = "vector_regnum_artifact";
    private static final Map<UUID, CircleEditorSession> SESSIONS = new ConcurrentHashMap<>();

    private static final AttachmentType<String> SAVED_CIRCLE = AttachmentRegistry.<String>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "authored_circle"),
            builder -> builder
                    .initializer(() -> CirclePersistence.encode(starterCircle()))
                    .persistent(Codec.STRING)
                    .copyOnDeath());

    private CircleAuthoringService() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                SESSIONS.put(handler.player.getUuid(), load(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SESSIONS.remove(handler.player.getUuid()));
    }

    public static CircleEditorSession session(ServerPlayerEntity player) {
        return SESSIONS.computeIfAbsent(player.getUuid(), ignored -> load(player));
    }

    public static void newCircle(ServerPlayerEntity player, String id) {
        replace(player, MagicCircle.empty(id, displayName(id), 3, 8));
        player.sendMessage(Text.literal("New 3-ring circle: " + id).formatted(Formatting.GOLD), false);
        SpellVisualManager.showAuthoredCircle(player, session(player).current(), List.of());
    }

    public static void loadStarter(ServerPlayerEntity player) {
        replace(player, starterCircle());
        player.sendMessage(Text.literal("Loaded the editable Fire Aura example")
                .formatted(Formatting.GOLD), false);
        show(player);
    }

    public static void loadVmStarter(ServerPlayerEntity player) {
        replace(player, vmStarterCircle());
        player.sendMessage(Text.literal("Loaded a typed delayed Vector Step circle")
                .formatted(Formatting.GOLD), false);
        show(player);
    }

    public static boolean place(ServerPlayerEntity player, int ring, int slot, String sigil) {
        CircleEditorSession.EditResult result = session(player).place(
                new CircleCoordinate(ring, slot), sigil.toUpperCase());
        finishEdit(player, result);
        return result.changed();
    }

    public static boolean parameterize(
            ServerPlayerEntity player, int ring, int slot, String canonicalNumber) {
        CircleEditorSession.EditResult result;
        try {
            result = session(player).parameterize(new CircleCoordinate(ring, slot),
                    List.of(new CircleValue.NumberValue(canonicalNumber)));
        } catch (RuntimeException exception) {
            player.sendMessage(Text.literal("Invalid finite number: " + canonicalNumber)
                    .formatted(Formatting.RED), false);
            return false;
        }
        finishEdit(player, result);
        return result.changed();
    }

    public static boolean parameterizeValues(
            ServerPlayerEntity player, int ring, int slot, String rawValues) {
        try {
            List<CircleValue> values = java.util.Arrays.stream(rawValues.trim().split("[ ,]+"))
                    .filter(value -> !value.isBlank())
                    .map(CircleAuthoringService::parseValue)
                    .toList();
            CircleEditorSession.EditResult result = session(player).parameterize(
                    new CircleCoordinate(ring, slot), values);
            finishEdit(player, result);
            return result.changed();
        } catch (RuntimeException exception) {
            player.sendMessage(Text.literal("Invalid parameter list: " + exception.getMessage())
                    .formatted(Formatting.RED), false);
            return false;
        }
    }

    public static boolean remove(ServerPlayerEntity player, int ring, int slot) {
        CircleEditorSession.EditResult result = session(player).remove(new CircleCoordinate(ring, slot));
        finishEdit(player, result);
        return result.changed();
    }

    public static boolean undo(ServerPlayerEntity player) {
        CircleEditorSession.EditResult result = session(player).undo();
        finishEdit(player, result);
        return result.changed();
    }

    public static AuthoringCompilation compile(ServerPlayerEntity player) {
        MagicCircle circle = session(player).current();
        if (Vm2CircleCompiler.isVm2Circle(circle)) {
            Vm2CircleCompilation typed = Vm2CircleCompiler.compile(circle,
                    vmContext(player, player.getEyePos()));
            sendVmCompilation(player, typed);
            SpellVisualManager.showAuthoredCircle(player, circle, typed.diagnostics());
            return new AuthoringCompilation(null, typed);
        }
        CircleCompilation legacy = session(player).compilePreview();
        sendCompilation(player, legacy);
        SpellVisualManager.showAuthoredCircle(player, circle, legacy.diagnostics());
        return new AuthoringCompilation(legacy, null);
    }

    public static boolean cast(ServerPlayerEntity player) {
        return activateCircleAt(player, session(player).current(), true, player.getEyePos());
    }

    public static void show(ServerPlayerEntity player) {
        MagicCircle circle = session(player).current();
        player.sendMessage(Text.literal(circle.name() + " • " + circle.ringCount() + " rings • "
                        + circle.sigils().size() + " sigils")
                .formatted(Formatting.AQUA, Formatting.BOLD), false);
        if (circle.sigils().isEmpty()) {
            player.sendMessage(Text.literal("Empty — use /vectorregnum circle place <ring> <slot> <sigil>"), false);
        } else {
            for (int index = 0; index < circle.executionOrder().size(); index++) {
                PlacedSigil sigil = circle.executionOrder().get(index);
                String parameters = sigil.parameters().isEmpty() ? "" : " " + sigil.parameters();
                player.sendMessage(Text.literal(String.format("%d. r%d:s%d  %s%s", index + 1,
                        sigil.coordinate().ring(), sigil.coordinate().clockwiseSlot(),
                        sigil.type(), parameters)).formatted(Formatting.GRAY), false);
            }
        }
        SpellVisualManager.showAuthoredCircle(player, circle, List.of());
    }

    public static boolean giveMedium(ServerPlayerEntity player, SpellMedium medium) {
        AuthoringCompilation compilation = compile(player);
        if (compilation.hasErrors()) {
            return false;
        }
        MagicCircle circle = session(player).current();
        ItemStack blank = new ItemStack(switch (medium) {
            case SCROLL -> SpellMediaContent.SPELL_SCROLL;
            case BOOK -> SpellMediaContent.SPELL_BOOK;
            case TABLET -> SpellMediaContent.CARVED_TABLET_ITEM;
        });
        if (!player.getAbilities().creativeMode && !consumeBlank(player, blank)) {
            player.sendMessage(Text.literal("Craft a blank " + medium.name().toLowerCase()
                            + " before binding")
                    .formatted(Formatting.RED), false);
            return false;
        }
        long identity = player.getUuid().getMostSignificantBits()
                ^ player.getUuid().getLeastSignificantBits();
        String id = "artifact-" + Long.toUnsignedString(identity, 36) + "-"
                + Long.toUnsignedString(player.getServerWorld().getTime(), 36);
        SpellArtifact artifact = switch (medium) {
            case SCROLL -> SpellArtifact.scroll(id, circle);
            case BOOK -> SpellArtifact.book(id, circle);
            case TABLET -> SpellArtifact.tablet(id, circle);
        };
        ItemStack stack = createArtifactStack(artifact);
        player.getInventory().offerOrDrop(stack);
        player.sendMessage(Text.literal("Bound " + circle.name() + " into a "
                        + medium.name().toLowerCase())
                .formatted(Formatting.GOLD), false);
        return true;
    }

    static TypedActionResult<ItemStack> useHandheldArtifact(
            PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(SpellMediaContent.SPELL_SCROLL)
                && !stack.isOf(SpellMediaContent.SPELL_BOOK)) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient()) {
            return TypedActionResult.success(stack, true);
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            return TypedActionResult.fail(stack);
        }
        Optional<SpellArtifact> decoded = readArtifact(stack);
        if (decoded.isEmpty()) {
            serverPlayer.sendMessage(Text.literal("This medium contains no valid spell")
                    .formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }
        SpellArtifact artifact = decoded.orElseThrow();
        if (artifact.state() == SpellArtifact.State.CONSUMED) {
            serverPlayer.sendMessage(Text.literal("This scroll has already been consumed")
                    .formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }
        if (activateCircleAt(serverPlayer, artifact.circle(), true, serverPlayer.getEyePos())) {
            SpellArtifact.Transition transition = artifact.recordSuccessfulActivation();
            if (artifact.medium() == SpellMedium.SCROLL) {
                // Single-use is a spell-medium rule, not a survival inventory rule.
                stack.decrement(1);
                serverPlayer.sendMessage(Text.literal("The successful scroll burns into silver ash")
                        .formatted(Formatting.GOLD), false);
            } else {
                writeArtifact(stack, transition.artifact());
            }
            player.getItemCooldownManager().set(stack.getItem(), 20);
        }
        return TypedActionResult.success(stack, false);
    }

    public static boolean activateCircleAt(ServerPlayerEntity player, MagicCircle circle,
            boolean chargeMana, Vec3d origin) {
        if (Vm2CircleCompiler.isVm2Circle(circle)) {
            Vm2CircleCompilation compilation = Vm2CircleCompiler.compile(circle, vmContext(player, origin));
            sendVmCompilation(player, compilation);
            SpellVisualManager.showAuthoredCircleAt(player, circle, compilation.diagnostics(), origin);
            return !compilation.hasErrors() && FabricVmService.startAuthored(player,
                    compilation.compiledProgram().orElseThrow(), chargeMana, circle.name());
        }
        CircleCompilation compilation = CircleAuthoringCompiler.compile(circle);
        sendCompilation(player, compilation);
        SpellVisualManager.showAuthoredCircleAt(player, circle, compilation.diagnostics(), origin);
        return !compilation.hasErrors() && CastService.castAt(player,
                compilation.compatibilitySource(), chargeMana, origin,
                player.getRotationVec(1.0F)) instanceof CastResult.Success;
    }

    public static ItemStack createArtifactStack(SpellArtifact artifact) {
        ItemStack stack = switch (artifact.medium()) {
            case SCROLL -> new ItemStack(SpellMediaContent.SPELL_SCROLL);
            case BOOK -> new ItemStack(SpellMediaContent.SPELL_BOOK);
            case TABLET -> new ItemStack(SpellMediaContent.CARVED_TABLET_ITEM);
        };
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(artifact.circle().name() + " "
                + switch (artifact.medium()) {
                    case SCROLL -> "Scroll";
                    case BOOK -> "Spellbook";
                    case TABLET -> "Tablet";
                }).formatted(Formatting.LIGHT_PURPLE));
        writeArtifact(stack, artifact);
        if (artifact.medium() == SpellMedium.TABLET) {
            NbtCompound blockData = new NbtCompound();
            blockData.putString(SpellTabletBlockEntity.PAYLOAD_KEY,
                    SpellArtifactPersistence.encode(artifact));
            BlockItem.setBlockEntityData(stack, SpellMediaContent.TABLET_BLOCK_ENTITY, blockData);
        }
        return stack;
    }

    public static Optional<SpellArtifact> readArtifact(ItemStack stack) {
        NbtComponent component = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        if (!component.contains(ARTIFACT_KEY)) {
            return Optional.empty();
        }
        try {
            return Optional.of(SpellArtifactPersistence.decode(component.getNbt().getString(ARTIFACT_KEY)));
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Rejected corrupt spell medium", exception);
            return Optional.empty();
        }
    }

    private static void writeArtifact(ItemStack stack, SpellArtifact artifact) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack,
                nbt -> nbt.putString(ARTIFACT_KEY, SpellArtifactPersistence.encode(artifact)));
    }

    private static boolean consumeBlank(ServerPlayerEntity player, ItemStack expected) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack candidate = player.getInventory().getStack(slot);
            if (candidate.isOf(expected.getItem()) && readArtifact(candidate).isEmpty()) {
                candidate.decrement(1);
                return true;
            }
        }
        return false;
    }

    private static CircleEditorSession load(ServerPlayerEntity player) {
        try {
            return new CircleEditorSession(CirclePersistence.decode(player.getAttachedOrCreate(SAVED_CIRCLE)));
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Reset corrupt saved circle for {}",
                    player.getGameProfile().getName(), exception);
            MagicCircle fallback = starterCircle();
            player.setAttached(SAVED_CIRCLE, CirclePersistence.encode(fallback));
            return new CircleEditorSession(fallback);
        }
    }

    private static void replace(ServerPlayerEntity player, MagicCircle circle) {
        SESSIONS.put(player.getUuid(), new CircleEditorSession(circle));
        persist(player);
    }

    private static void finishEdit(ServerPlayerEntity player, CircleEditorSession.EditResult result) {
        if (result.changed()) {
            persist(player);
            player.sendMessage(Text.literal("Circle updated • " + result.circle().sigils().size() + " sigils")
                    .formatted(Formatting.AQUA), true);
            SpellVisualManager.showAuthoredCircle(player, result.circle(), List.of());
        } else {
            sendDiagnostics(player, result.diagnostics());
        }
    }

    private static void persist(ServerPlayerEntity player) {
        player.setAttached(SAVED_CIRCLE, CirclePersistence.encode(session(player).current()));
    }

    private static void sendCompilation(ServerPlayerEntity player, CircleCompilation compilation) {
        if (compilation.hasErrors()) {
            player.sendMessage(Text.literal("CIRCLE REJECTED • " + compilation.diagnostics().size()
                            + " diagnostic(s)").formatted(Formatting.RED, Formatting.BOLD), false);
            sendDiagnostics(player, compilation.diagnostics());
        } else {
            player.sendMessage(Text.literal("CIRCLE COMPILED • clockwise/outside-in • "
                            + compilation.executionOrder().size() + " instructions")
                    .formatted(Formatting.GREEN, Formatting.BOLD), false);
        }
    }

    private static void sendVmCompilation(
            ServerPlayerEntity player, Vm2CircleCompilation compilation) {
        if (compilation.hasErrors()) {
            player.sendMessage(Text.literal("VM2 CIRCLE REJECTED • "
                            + compilation.diagnostics().size() + " diagnostic(s)")
                    .formatted(Formatting.RED, Formatting.BOLD), false);
            sendDiagnostics(player, compilation.diagnostics());
            return;
        }
        var cost = compilation.compiledProgram().orElseThrow().manaCost();
        player.sendMessage(Text.literal(String.format(java.util.Locale.ROOT,
                        "VM2 CIRCLE COMPILED • %d instructions • %.2f μ "
                                + "[work %.2f, range %.2f, time %.2f, rarity %.2f, "
                                + "memory %.2f, perception %.2f, control %.2f]",
                        compilation.executionOrder().size(), cost.total(), cost.physicalWork(),
                        cost.range(), cost.duration(), cost.rarity(), cost.memory(),
                        cost.perception(), cost.controlFlow()))
                .formatted(Formatting.GREEN), false);
    }

    private static void sendDiagnostics(ServerPlayerEntity player, List<CircleDiagnostic> diagnostics) {
        for (CircleDiagnostic diagnostic : diagnostics) {
            String location = diagnostic.location().map(coordinate ->
                    " r" + coordinate.ring() + ":s" + coordinate.clockwiseSlot()).orElse("");
            player.sendMessage(Text.literal(diagnostic.code() + location + " — " + diagnostic.message())
                    .formatted(diagnostic.severity() == CircleDiagnostic.Severity.ERROR
                            ? Formatting.RED : Formatting.YELLOW), false);
        }
    }

    private static MagicCircle starterCircle() {
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

    private static Vm2CircleCompiler.Context vmContext(ServerPlayerEntity player, Vec3d origin) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        return new Vm2CircleCompiler.Context(player.getUuidAsString(),
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
