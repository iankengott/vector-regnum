package vectorregnum.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import vectorregnum.core.Sigil;
import vectorregnum.core.circle.SpellMedium;
import vectorregnum.fabric.progression.ManaAffinity;
import vectorregnum.fabric.progression.ProgressionContent;
import vectorregnum.fabric.progression.ProgressionData;
import vectorregnum.fabric.progression.ProgressionSpellLibrary;
import vectorregnum.fabric.progression.ProgressionUnlock;

import java.util.List;
import java.util.Map;

public final class VectorRegnumCommands {
    private VectorRegnumCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("vectorregnum")
                .executes(context -> status(context.getSource()));

        var cast = CommandManager.literal("cast");
        for (Map.Entry<String, List<Sigil>> entry : SpellPresets.CASTABLE.entrySet()) {
            cast.then(CommandManager.literal(entry.getKey())
                    .executes(context -> cast(context.getSource(), entry.getValue(), true)));
        }

        var miscast = CommandManager.literal("miscast")
                .requires(source -> source.hasPermissionLevel(2));
        for (Map.Entry<String, List<Sigil>> entry : SpellPresets.MISCASTS.entrySet()) {
            miscast.then(CommandManager.literal(entry.getKey())
                    .executes(context -> cast(context.getSource(), entry.getValue(), true)));
        }

        root.then(cast);
        root.then(miscast);
        var mana = CommandManager.literal("mana")
                .executes(context -> status(context.getSource()))
                .then(CommandManager.literal("refill")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> refill(context.getSource())))
                .then(CommandManager.literal("give_source")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> giveSource(context.getSource())));
        var attune = CommandManager.literal("attune");
        for (ManaAffinity affinity : ManaAffinity.values()) {
            attune.then(CommandManager.literal(affinity.name().toLowerCase())
                    .executes(context -> attune(context.getSource(), affinity)));
        }
        mana.then(attune);
        root.then(mana);
        root.then(CommandManager.literal("give_tome")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> giveTome(context.getSource())));
        root.then(CommandManager.literal("guide")
                .executes(context -> giveGuide(context.getSource())));
        root.then(circleCommands());
        root.then(libraryCommands());
        root.then(researchCommands());
        root.then(vmCommands());
        root.then(CommandManager.literal("progression")
                .executes(context -> progression(context.getSource()))
                .then(CommandManager.literal("unlock_all")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> unlockAll(context.getSource()))));
        root.then(CommandManager.literal("devkit")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> devkit(context.getSource())));
        root.then(CommandManager.literal("showcase")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> showcase(context.getSource())));
        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>
            circleCommands() {
        var circle = CommandManager.literal("circle")
                .executes(context -> showCircle(context.getSource()));
        circle.then(CommandManager.literal("new")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(context -> newCircle(context.getSource(),
                                StringArgumentType.getString(context, "id")))));
        circle.then(CommandManager.literal("starter")
                .executes(context -> starterCircle(context.getSource())));
        circle.then(CommandManager.literal("vm_starter")
                .executes(context -> vmStarterCircle(context.getSource())));
        circle.then(CommandManager.literal("show")
                .executes(context -> showCircle(context.getSource())));
        circle.then(CommandManager.literal("compile")
                .executes(context -> compileCircle(context.getSource())));
        circle.then(CommandManager.literal("cast")
                .executes(context -> castCircle(context.getSource())));
        circle.then(CommandManager.literal("undo")
                .executes(context -> undoCircle(context.getSource())));
        circle.then(CommandManager.literal("place")
                .then(CommandManager.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(0, 63))
                                .then(CommandManager.argument("sigil", StringArgumentType.word())
                                        .executes(context -> placeSigil(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "ring"),
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                StringArgumentType.getString(context, "sigil")))))));
        circle.then(CommandManager.literal("parameter")
                .then(CommandManager.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(0, 63))
                                .then(CommandManager.argument("number", StringArgumentType.word())
                                        .executes(context -> parameterize(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "ring"),
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                StringArgumentType.getString(context, "number")))))));
        circle.then(CommandManager.literal("params")
                .then(CommandManager.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(0, 63))
                                .then(CommandManager.argument("values", StringArgumentType.greedyString())
                                        .executes(context -> parameterizeValues(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "ring"),
                                                IntegerArgumentType.getInteger(context, "slot"),
                                                StringArgumentType.getString(context, "values")))))));
        circle.then(CommandManager.literal("remove")
                .then(CommandManager.argument("ring", IntegerArgumentType.integer(0, 15))
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(0, 63))
                                .executes(context -> removeSigil(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "ring"),
                                        IntegerArgumentType.getInteger(context, "slot"))))));
        var bind = CommandManager.literal("bind");
        for (SpellMedium medium : SpellMedium.values()) {
            bind.then(CommandManager.literal(medium.name().toLowerCase())
                    .executes(context -> bindMedium(context.getSource(), medium)));
        }
        circle.then(bind);
        return circle;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>
            libraryCommands() {
        var library = CommandManager.literal("library")
                .executes(context -> listLibrary(context.getSource()))
                .then(CommandManager.literal("list")
                        .executes(context -> listLibrary(context.getSource())));
        var cast = CommandManager.literal("cast");
        ProgressionSpellLibrary.ALL.forEach(spell -> cast.then(CommandManager.literal(spell.id())
                .executes(context -> castLibrary(context.getSource(), spell.id()))));
        library.then(cast);
        return library;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>
            researchCommands() {
        var research = CommandManager.literal("research")
                .executes(context -> progression(context.getSource()));
        for (ProgressionUnlock unlock : ProgressionUnlock.values()) {
            research.then(CommandManager.literal(unlock.id())
                    .executes(context -> research(context.getSource(), unlock)));
        }
        return research;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource>
            vmCommands() {
        return CommandManager.literal("vm")
                .then(CommandManager.literal("demo")
                        .executes(context -> vmDemo(context.getSource())))
                .then(CommandManager.literal("probe")
                        .executes(context -> vmProbe(context.getSource())));
    }

    private static int cast(ServerCommandSource source, List<Sigil> spell, boolean chargeMana) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("No player is connected"), false);
            return 0;
        }
        return CastService.cast(player, spell, chargeMana) instanceof vectorregnum.core.CastResult.Success
                ? 1 : 0;
    }

    private static int showcase(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("No player is connected"), false);
            return 0;
        }
        SpellVisualManager.startShowcase(player);
        CastService.cast(player, SpellPresets.FIREBOLT, false);
        CastService.cast(player, SpellPresets.FROST_NOVA, false);
        player.sendMessage(Text.literal("VECTOR-REGNUM • VISUAL COMPILATION")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        return 1;
    }

    private static int status(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("Vector-Regnum is loaded; no player is connected"), false);
            return 1;
        }
        player.sendMessage(Text.literal(String.format(
                        "Vector-Regnum mana: %.2f / %.2f μ • %s affinity",
                        ManaData.available(player), ManaData.capacity(player),
                        ManaData.affinity(player).name().toLowerCase()))
                .formatted(Formatting.AQUA), false);
        BlockPos crystal = ManaData.attunedSource(player);
        if (crystal != null) {
            player.sendMessage(Text.literal("Attuned crystal source: " + ManaData.attunedDimension(player)
                            + " " + crystal.toShortString())
                    .formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static int refill(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("No player is connected"), false);
            return 0;
        }
        ManaData.refill(player);
        player.sendMessage(Text.literal("Development refill restored the channel to capacity")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int giveSource(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        player.getInventory().offerOrDrop(new ItemStack(ProgressionContent.MANA_CRYSTAL_NODE_ITEM));
        player.getInventory().offerOrDrop(new ItemStack(ProgressionContent.MANA_CRYSTAL_SHARD, 8));
        player.sendMessage(Text.literal("Received an immovable source node and 8 capacity shards")
                .formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int attune(ServerCommandSource source, ManaAffinity affinity) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        ManaData.setAffinity(player, affinity);
        player.sendMessage(Text.literal("Channel tuned to " + affinity.name().toLowerCase()
                        + " resonance")
                .formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int newCircle(ServerCommandSource source, String id) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        try {
            CircleAuthoringService.newCircle(player, id);
            return 1;
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Text.literal(exception.getMessage()).formatted(Formatting.RED), false);
            return 0;
        }
    }

    private static int starterCircle(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CircleAuthoringService.loadStarter(player);
        return 1;
    }

    private static int vmStarterCircle(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CircleAuthoringService.loadVmStarter(player);
        return 1;
    }

    private static int showCircle(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        CircleAuthoringService.show(player);
        return 1;
    }

    private static int compileCircle(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        return CircleAuthoringService.compile(player).hasErrors() ? 0 : 1;
    }

    private static int castCircle(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && CircleAuthoringService.cast(player) ? 1 : noPlayerIfNull(source, player);
    }

    private static int undoCircle(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && CircleAuthoringService.undo(player) ? 1 : noPlayerIfNull(source, player);
    }

    private static int placeSigil(ServerCommandSource source, int ring, int slot, String sigil) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && CircleAuthoringService.place(player, ring, slot, sigil)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int parameterize(ServerCommandSource source, int ring, int slot, String number) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && CircleAuthoringService.parameterize(player, ring, slot, number)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int parameterizeValues(
            ServerCommandSource source, int ring, int slot, String values) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && CircleAuthoringService.parameterizeValues(player, ring, slot, values)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int removeSigil(ServerCommandSource source, int ring, int slot) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && CircleAuthoringService.remove(player, ring, slot)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int bindMedium(ServerCommandSource source, SpellMedium medium) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && CircleAuthoringService.giveMedium(player, medium)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int listLibrary(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        LibrarySpellService.list(player);
        return 1;
    }

    private static int castLibrary(ServerCommandSource source, String id) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && LibrarySpellService.cast(player, id)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int research(ServerCommandSource source, ProgressionUnlock unlock) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && LibrarySpellService.research(player, unlock)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int progression(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        player.sendMessage(Text.literal("Research: " + ProgressionData.get(player).ids())
                .formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int unlockAll(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        int changed = ProgressionData.unlockAll(player);
        player.sendMessage(Text.literal("Development research unlocks added: " + changed)
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int vmDemo(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        return player != null && FabricVmService.launchVectorStep(player, true, 20, 1.0)
                ? 1 : noPlayerIfNull(source, player);
    }

    private static int vmProbe(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        var report = FabricVmService.perceptionProbe(player, 16.0);
        player.sendMessage(Text.literal(String.format(
                        "vm2 probe: %d entities • %.2f μ (range %.2f, perception %.2f)",
                        report.entityCount(), report.cost().total(), report.cost().range(),
                        report.cost().perception())).formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int devkit(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) return noPlayer(source);
        ManaData.refill(player);
        ProgressionData.unlockAll(player);
        player.getInventory().offerOrDrop(new ItemStack(ProgressionContent.MANA_CRYSTAL_NODE_ITEM));
        player.getInventory().offerOrDrop(new ItemStack(ProgressionContent.MANA_CRYSTAL_SHARD, 8));
        CircleAuthoringService.loadStarter(player);
        CircleAuthoringService.giveMedium(player, SpellMedium.SCROLL);
        CircleAuthoringService.giveMedium(player, SpellMedium.BOOK);
        CircleAuthoringService.giveMedium(player, SpellMedium.TABLET);
        player.sendMessage(Text.literal("Vector-Regnum 1–10 development kit equipped")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        return 1;
    }

    private static int noPlayerIfNull(ServerCommandSource source, ServerPlayerEntity player) {
        return player == null ? noPlayer(source) : 0;
    }

    private static int noPlayer(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("No player is connected"), false);
        return 0;
    }

    private static int giveTome(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("No player is connected"), false);
            return 0;
        }
        player.giveItemStack(new ItemStack(VectorRegnumContent.SIGIL_TOME));
        player.sendMessage(Text.literal("Received a Firebolt Sigil Tome")
                .formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int giveGuide(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("No player is connected"), false);
            return 0;
        }
        TutorialGuide.give(player);
        return 1;
    }

    private static ServerPlayerEntity targetPlayer(ServerCommandSource source) {
        ServerPlayerEntity direct = source.getPlayer();
        if (direct != null) {
            return direct;
        }
        return source.getServer().getPlayerManager().getPlayerList().stream()
                .findFirst()
                .orElse(null);
    }
}
