package vectorregnum.neoforge.progression;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Keeps client-only guide gating in sync without making the client authoritative. */
public final class ProgressionSync {
    private static boolean initialized;

    private ProgressionSync() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        PayloadTypeRegistry.playS2C().register(ProgressionPayload.ID, ProgressionPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
    }

    public static void send(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, ProgressionPayload.ID)) {
            ServerPlayNetworking.send(player, ProgressionPayload.of(ProgressionData.get(player)));
        }
    }
}
