package vectorregnum.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import vectorregnum.core.Sigil;
import vectorregnum.core.circle.SpellMedium;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.automation.AutomationRule;
import vectorregnum.neoforge.automation.AutomationContent;
import vectorregnum.neoforge.automation.AutomationRelayBlockEntity;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ProgressionContent;
import vectorregnum.neoforge.progression.ProgressionData;
import vectorregnum.neoforge.progression.ProgressionSpellLibrary;
import vectorregnum.neoforge.progression.ProgressionUnlock;

import java.util.List;
import java.util.Map;

public final class VectorRegnumCommands {
    private VectorRegnumCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("vectorregnum")
                .executes(context -> status(context.getSource()));

        var cast = Commands.literal("cast");
        for (Map.Entry<String, List<Sigil>> entry : SpellPresets.CASTABLE.entrySet()) {
            cast.then(Commands.literal(entry.getKey())
                    .executes(context -> cast(context.getSource(), entry.getValue(), true)));
        }

        var miscast = Commands.literal("miscast")
                .requires(source -> source.hasPermission(2));
        for (Map.Entry<String, List<Sigil>> entry : SpellPresets.MISCASTS.entrySet()) {
            miscast.then(Commands.literal(entry.getKey())
                    .executes(context -> cast(context.getSource(), entry.getValue(), true)));
        }

        root.then(cast);
        root.then(miscast);
        var mana = Commands.literal("mana")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("refill")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> refill(context.getSource())))
                .then(Commands.literal("give_source")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> giveSource(context.getSource())));
        var attune = Commands.literal("attune");
        for (ManaAffinity affinity : ManaAffinity.channelValues()) {
            attune.then(Commands.literal(affinity.name().toLowerCase())
                    .executes(context -> attune(context.getSource(), affinity)));
        }
        mana.then(attune);
        root.then(mana);
        root.then(Commands.literal("give_tome")
                .requires(source -> source.hasPermission(2))
                .executes(context -> giveTome(context.getSource())));
        root.then(Commands.literal("guide")
                .executes(context -> giveGuide(context.getSource())));
        root.then(circleCommands());
        root.then(reagentCommands());
        root.then(libraryCommands());
        root.then(researchCommands());
        root.then(vmCommands());
        root.then(automationCommands());
        root.then(Commands.literal("progression")
                .executes(context -> progression(context.getSource()))
                .then(Commands.literal("unlock_all")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> unlockAll(context.getSource()))));
        root.then(Commands.literal("devkit")
                .requires(source -> source.hasPermission(2))
                .executes(context -> devkit(context.getSource())));
        root.then(Commands.literal("showcase")
                .requires(source -> source.hasPermission(2))
                .executes(context -> showcase(context.getSource())));
        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
            circleCommands() {
        var circle = Commands.literal("circle")
                .executes(context -> showCircle(context.getSource()));
        circle.then(Commands.literal("new")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> newCircle(context.getSource(),
                                StringArgumentType.getString(context, "id")))));
        circle.then(Commands.literal("starter")
                .executes(context -> starterCircle(context.getSource())));
        circle.then(Commands.literal("vm_starter")
                .executes(context -> vmStarterCircle(context.getSource())));
        circle.then(Commands.literal("show")
                .executes(context -> showCircle(context.getSource())));
        circle.then(Commands.literal("compile")
                .executes(context -> compileCircle(context.getSource())));
        circle.then(Commands.literal("cast")
                .executes(context -> castCircle(context.getSource())));
        circle.then(Commands.literal("ritual")
                .executes(context -> ritualCircle(context.getSource())));
        var quote = Commands.literal("quote");
        for (CastingMethod method : CastingMethod.values()) {
            quote.then(Commands.literal(method.stableId())
                    .executes(context -> quoteCircle(context.getSource(), method)));
        }
        circle.then(quote);
        circle.then(Commands.literal("undo")
                .executes(context -> undoCircle(context.getSource())));
        circle.then(Commands.literal("place")
                .then(Commands.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 63))
                                .then(Commands.argument("sigil", StringArgumentType.word())
                                        .executes(context -> placeSigil(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "ring"),
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                StringArgumentType.getString(context, "sigil")))))));
        circle.then(Commands.literal("parameter")
                .then(Commands.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 63))
                                .then(Commands.argument("number", StringArgumentType.word())
                                        .executes(context -> parameterize(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "ring"),
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                StringArgumentType.getString(context, "number")))))));
        circle.then(Commands.literal("params")
                .then(Commands.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 63))
                                .then(Commands.argument("values", StringArgumentType.greedyString())
                                        .executes(context -> parameterizeValues(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "ring"),
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                StringArgumentType.getString(context, "values")))))));
        circle.then(Commands.literal("remove")
                .then(Commands.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, 63))
                                .executes(context -> removeSigil(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "ring"),
                                        IntegerArgumentType.getInteger(context, "slot"))))));
        var bind = Commands.literal("bind");
        for (SpellMedium medium : SpellMedium.values()) {
            bind.then(Commands.literal(medium.name().toLowerCase())
                    .executes(context -> bindMedium(context.getSource(), medium)));
        }
        circle.then(bind);
        return circle;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
            reagentCommands() {
        var reagents = Commands.literal("reagents")
                .executes(context -> reagentStatus(context.getSource()))
                .then(Commands.literal("clear")
                        .executes(context -> clearReagents(context.getSource())));
        var stage = Commands.literal("stage");
        for (ReagentKind kind : ReagentKind.values()) {
            stage.then(Commands.literal(kind.stableId())
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(context -> stageReagent(context.getSource(), kind,
                                    IntegerArgumentType.getInteger(context, "count")))));
        }
        stage.then(Commands.literal("offering")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> stageOffering(context.getSource(),
                                IntegerArgumentType.getInteger(context, "count")))));
        reagents.then(stage);
        return reagents;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
            libraryCommands() {
        var library = Commands.literal("library")
                .executes(context -> listLibrary(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> listLibrary(context.getSource())));
        var cast = Commands.literal("cast");
        ProgressionSpellLibrary.ALL.forEach(spell -> cast.then(Commands.literal(spell.id())
                .executes(context -> castLibrary(context.getSource(), spell.id()))));
        library.then(cast);
        return library;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
            researchCommands() {
        var research = Commands.literal("research")
                .executes(context -> progression(context.getSource()));
        for (ProgressionUnlock unlock : ProgressionUnlock.values()) {
            research.then(Commands.literal(unlock.id())
                    .executes(context -> research(context.getSource(), unlock)));
        }
        return research;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
            vmCommands() {
        return Commands.literal("vm")
                .then(Commands.literal("demo")
                        .executes(context -> vmDemo(context.getSource())))
                .then(Commands.literal("probe")
                        .executes(context -> vmProbe(context.getSource())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
            automationCommands() {
        var automation = Commands.literal("automation")
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> giveAutomationRelay(context.getSource())))
                .then(Commands.literal("program")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> programAutomation(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> triggerAutomation(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> inspectAutomation(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))));
        var rule = Commands.literal("rule");
        for (AutomationRule.TriggerMode mode : AutomationRule.TriggerMode.values()) {
            rule.then(Commands.literal(mode.name().toLowerCase())
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(Commands.argument("threshold",
                                    IntegerArgumentType.integer(1, 15))
                                    .then(Commands.argument("cooldown",
                                            IntegerArgumentType.integer(1,
                                                    AutomationRule.MAX_COOLDOWN_TICKS))
                                            .executes(context -> configureAutomationRule(
                                                    context.getSource(),
                                                    BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                    mode,
                                                    IntegerArgumentType.getInteger(context,
                                                            "threshold"),
                                                    IntegerArgumentType.getInteger(context,
                                                            "cooldown")))))));
        }
        automation.then(rule);
        return automation;
    }

    private static int cast(CommandSourceStack source, List<Sigil> spell, boolean chargeMana) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) {
            source.sendSuccess(() -> Component.literal("No player is connected"), false);
            return 0;
        }
        return CastService.cast(player, spell, chargeMana) instanceof vectorregnum.core.CastResult.Success
                ? 1 : 0;
    }

    private static int showcase(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) {
            source.sendSuccess(() -> Component.literal("No player is connected"), false);
            return 0;
        }
        SpellVisualManager.startShowcase(player);
        CastService.cast(player, SpellPresets.FIREBOLT, false);
        CastService.cast(player, SpellPresets.ICE_NOVA, false);
        player.sendSystemMessage(Component.literal("VECTOR-REGNUM • VISUAL COMPILATION")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) {
            source.sendSuccess(() -> Component.literal("Vector-Regnum is loaded; no player is connected"), false);
            return 1;
        }
        player.sendSystemMessage(Component.literal(String.format(
                        "Vector-Regnum mana: %.2f / %.2f μ • natural %s • channel %s",
                        ManaData.available(player), ManaData.capacity(player),
                        ManaData.naturalElement(player).id(),
                        ManaData.channelAffinity(player).name().toLowerCase()))
                .withStyle(ChatFormatting.AQUA), false);
        BlockPos crystal = ManaData.attunedSource(player);
        if (crystal != null) {
            player.sendSystemMessage(Component.literal("Attuned crystal source: " + ManaData.attunedDimension(player)
                            + " " + crystal.toShortString())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int refill(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) {
            source.sendSuccess(() -> Component.literal("No player is connected"), false);
            return 0;
        }
        ManaData.refill(player);
        player.sendSystemMessage(Component.literal("Development refill restored the channel to capacity")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int giveSource(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        player.getInventory().placeItemBackInInventory(new ItemStack(ProgressionContent.manaCrystalNodeItem()));
        player.getInventory().placeItemBackInInventory(new ItemStack(ProgressionContent.manaCrystalShard(), 8));
        player.sendSystemMessage(Component.literal("Received an immovable source node and 8 capacity shards")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int attune(CommandSourceStack source, ManaAffinity affinity) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        ManaData.setChannelAffinity(player, affinity);
        player.sendSystemMessage(Component.literal("Channel tuned to " + affinity.name().toLowerCase()
                        + " resonance")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int newCircle(CommandSourceStack source, String id) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        try {
            CircleAuthoringService.newCircle(player, id);
            return 1;
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
    }

    private static int starterCircle(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CircleAuthoringService.loadStarter(player);
        return 1;
    }

    private static int vmStarterCircle(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CircleAuthoringService.loadVmStarter(player);
        return 1;
    }

    private static int showCircle(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CircleAuthoringService.show(player);
        return 1;
    }

    private static int compileCircle(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        return CircleAuthoringService.compile(player).hasErrors() ? 0 : 1;
    }

    private static int castCircle(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.cast(player) ? 1 : noPlayerIfNull(source, player);
    }

    private static int ritualCircle(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.ritual(player)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int quoteCircle(CommandSourceStack source, CastingMethod method) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.quote(player, method).isPresent()
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int reagentStatus(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CastingResourceService.reportStaged(player);
        return 1;
    }

    private static int stageReagent(CommandSourceStack source, ReagentKind kind, int count) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CastingResourceService.stage(player, kind, count)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int stageOffering(CommandSourceStack source, int count) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CastingResourceService.stageOffering(player, count)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int clearReagents(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CastingResourceService.clearStaged(player);
        return 1;
    }

    private static int undoCircle(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.undo(player) ? 1 : noPlayerIfNull(source, player);
    }

    private static int placeSigil(CommandSourceStack source, int ring, int slot, String sigil) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.place(player, ring, slot, sigil)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int parameterize(CommandSourceStack source, int ring, int slot, String number) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.parameterize(player, ring, slot, number)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int parameterizeValues(
            CommandSourceStack source, int ring, int slot, String values) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.parameterizeValues(player, ring, slot, values)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int removeSigil(CommandSourceStack source, int ring, int slot) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.remove(player, ring, slot)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int bindMedium(CommandSourceStack source, SpellMedium medium) {
        ServerPlayer player = targetPlayer(source);
        return player != null && CircleAuthoringService.giveMedium(player, medium)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int listLibrary(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        LibrarySpellService.list(player);
        return 1;
    }

    private static int castLibrary(CommandSourceStack source, String id) {
        ServerPlayer player = targetPlayer(source);
        return player != null && LibrarySpellService.cast(player, id)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int research(CommandSourceStack source, ProgressionUnlock unlock) {
        ServerPlayer player = targetPlayer(source);
        return player != null && LibrarySpellService.research(player, unlock)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int progression(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        player.sendSystemMessage(Component.literal("Research: " + ProgressionData.get(player).ids())
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int unlockAll(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        int changed = ProgressionData.unlockAll(player);
        player.sendSystemMessage(Component.literal("Development research unlocks added: " + changed)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int vmDemo(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        return player != null && NeoForgeVmService.launchVectorStep(player, true, 20, 1.0)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int vmProbe(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        var report = NeoForgeVmService.perceptionProbe(player, 16.0);
        player.sendSystemMessage(Component.literal(String.format(
                        "vm2 probe: %d entities • %.2f μ (range %.2f, perception %.2f)",
                        report.entityCount(), report.cost().total(), report.cost().range(),
                        report.cost().perception())).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int giveAutomationRelay(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        player.getInventory().placeItemBackInInventory(new ItemStack(AutomationContent.automationRelayItem()));
        player.sendSystemMessage(Component.literal("Received one programmable automation relay")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int programAutomation(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        AutomationRelayBlockEntity relay = automationRelay(source, pos);
        if (relay == null) return 0;
        if (!SpellSecurityPolicy.canModifyBlock(player, pos, source.getLevel().getBlockState(pos))) {
            player.sendSystemMessage(Component.literal("Claims or world permissions prevent programming this relay")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (CircleAuthoringService.compile(player).hasErrors()) {
            player.sendSystemMessage(Component.literal("Fix the current circle before programming a relay")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (!relay.configure(player, CircleAuthoringService.session(player).current(),
                AutomationRule.risingEdge())) {
            player.sendSystemMessage(Component.literal("Only the relay owner may replace its program")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.sendSystemMessage(Component.literal("Relay programmed with the current circle • rising edge • 20t cooldown")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int configureAutomationRule(CommandSourceStack source, BlockPos pos,
            AutomationRule.TriggerMode mode, int threshold, int cooldown) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        AutomationRelayBlockEntity relay = automationRelay(source, pos);
        if (relay == null) return 0;
        if (!SpellSecurityPolicy.canModifyBlock(player, pos, source.getLevel().getBlockState(pos))) {
            player.sendSystemMessage(Component.literal("Claims or world permissions prevent changing this relay")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (!relay.reconfigureRule(player, new AutomationRule(mode, threshold, cooldown))) {
            player.sendSystemMessage(Component.literal("Program and own this relay before changing its rule")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.sendSystemMessage(Component.literal("Relay rule updated: " + mode.name().toLowerCase()
                        + " at " + threshold + ", cooldown " + cooldown + "t")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int triggerAutomation(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        AutomationRelayBlockEntity relay = automationRelay(source, pos);
        if (relay == null) return 0;
        if (!SpellSecurityPolicy.canModifyBlock(player, pos, source.getLevel().getBlockState(pos))) {
            player.sendSystemMessage(Component.literal("Claims or world permissions prevent triggering this relay")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (!relay.requestRemote(player)) {
            player.sendSystemMessage(Component.literal("Remote request rejected: relay is unprogrammed, foreign, or busy")
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        player.sendSystemMessage(Component.literal("Remote activation queued at " + pos.toShortString())
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int inspectAutomation(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        AutomationRelayBlockEntity relay = automationRelay(source, pos);
        if (relay == null) return 0;
        relay.reportStatus(player);
        return 1;
    }

    private static AutomationRelayBlockEntity automationRelay(
            CommandSourceStack source, BlockPos pos) {
        if (!source.getLevel().hasChunkAt(pos)) {
            source.sendFailure(Component.literal("Automation refuses unloaded target chunks"));
            return null;
        }
        if (!(source.getLevel().getBlockEntity(pos) instanceof AutomationRelayBlockEntity relay)) {
            source.sendFailure(Component.literal("No automation relay exists at " + pos.toShortString()));
            return null;
        }
        return relay;
    }

    private static int devkit(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        ManaData.refill(player);
        ProgressionData.unlockAll(player);
        player.getInventory().placeItemBackInInventory(new ItemStack(ProgressionContent.manaCrystalNodeItem()));
        player.getInventory().placeItemBackInInventory(new ItemStack(ProgressionContent.manaCrystalShard(), 8));
        CircleAuthoringService.loadStarter(player);
        CircleAuthoringService.giveMedium(player, SpellMedium.SCROLL);
        CircleAuthoringService.giveMedium(player, SpellMedium.BOOK);
        CircleAuthoringService.giveMedium(player, SpellMedium.ENGRAVING);
        CircleAuthoringService.giveMedium(player, SpellMedium.TABLET);
        for (ReagentKind kind : ReagentKind.values()) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(CastingResourceService.reagentItem(kind), 16));
        }
        player.getInventory().placeItemBackInInventory(
                new ItemStack(CastingResourceService.offeringItem(), 16));
        player.sendSystemMessage(Component.literal("Vector-Regnum priority-22 development kit equipped")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        return 1;
    }

    private static int noPlayerIfNull(CommandSourceStack source, ServerPlayer player) {
        return player == null ? noPlayer(source) : 0;
    }

    private static int noPlayer(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("No player is connected"), false);
        return 0;
    }

    private static int giveTome(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) {
            source.sendSuccess(() -> Component.literal("No player is connected"), false);
            return 0;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(VectorRegnumContent.SIGIL_TOME.get()));
        player.sendSystemMessage(Component.literal("Received a Firebolt Sigil Tome")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int giveGuide(CommandSourceStack source) {
        ServerPlayer player = targetPlayer(source);
        if (player == null) {
            source.sendSuccess(() -> Component.literal("No player is connected"), false);
            return 0;
        }
        TutorialGuide.give(player);
        return 1;
    }

    private static ServerPlayer targetPlayer(CommandSourceStack source) {
        ServerPlayer direct = source.getPlayer();
        if (direct != null) {
            return direct;
        }
        return source.getServer().getPlayerList().getPlayers().stream()
                .findFirst()
                .orElse(null);
    }
}
