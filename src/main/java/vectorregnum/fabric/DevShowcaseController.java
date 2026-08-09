package vectorregnum.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Makes Loom visual checks reproducible without shipping automation into release builds. */
public final class DevShowcaseController {
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private DevShowcaseController() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING.put(handler.player.getUuid(), 100));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PENDING.remove(handler.player.getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID, Integer>> iterator = PENDING.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                int ticks = entry.getValue() - 1;
                if (ticks > 0) {
                    entry.setValue(ticks);
                    continue;
                }
                iterator.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    runShowcase(player);
                }
            }
        });
    }

    private static void runShowcase(ServerPlayerEntity player) {
        ManaData.refill(player);
        player.getServerWorld().setTimeOfDay(6000L);
        player.giveItemStack(new ItemStack(VectorRegnumContent.SIGIL_TOME));
        SpellVisualManager.startShowcase(player);
        CastService.cast(player, SpellPresets.FIREBOLT, false);
        CastService.cast(player, SpellPresets.FROST_NOVA, false);
        player.sendMessage(Text.literal("VECTOR-REGNUM • AUTOMATED VISUAL CHECKPOINT")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        VectorRegnumMod.LOGGER.info(
                "VISUAL_CHECKPOINT_READY player={} duration_ticks=240",
                player.getGameProfile().getName());
    }
}
