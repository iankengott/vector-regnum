package vectorregnum.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vectorregnum.core.Sigil;

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
        root.then(CommandManager.literal("mana")
                .executes(context -> status(context.getSource()))
                .then(CommandManager.literal("refill")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> refill(context.getSource()))));
        root.then(CommandManager.literal("give_tome")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> giveTome(context.getSource())));
        root.then(CommandManager.literal("showcase")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> showcase(context.getSource())));
        dispatcher.register(root);
    }

    private static int cast(ServerCommandSource source, List<Sigil> spell, boolean chargeMana) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("No player is connected"), false);
            return 0;
        }
        CastService.cast(player, spell, chargeMana);
        return 1;
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
                        "Vector-Regnum mana: %.2f / %.2f μ",
                        ManaData.available(player), ManaData.STARTING_MANA))
                .formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int refill(ServerCommandSource source) {
        ServerPlayerEntity player = targetPlayer(source);
        if (player == null) {
            source.sendFeedback(() -> Text.literal("No player is connected"), false);
            return 0;
        }
        ManaData.refill(player);
        player.sendMessage(Text.literal("Mana crystal attunement restored to 500 μ")
                .formatted(Formatting.GREEN), false);
        return 1;
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
