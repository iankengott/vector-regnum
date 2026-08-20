package vectorregnum.neoforge.progression;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Keeps client-only guide gating in sync without making the client authoritative. */
public final class ProgressionSync {
    private static final AtomicReference<Set<String>> CLIENT_UNLOCKS =
            new AtomicReference<>(Set.of());

    private ProgressionSync() {
    }

    /** Registers the server-to-client progression snapshot payload. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ProgressionPayload.TYPE, ProgressionPayload.CODEC,
                ProgressionSync::handleClient);
    }

    /** Handles snapshots on the client main thread (the registrar default). */
    public static void handleClient(ProgressionPayload payload, IPayloadContext ignored) {
        CLIENT_UNLOCKS.set(Set.copyOf(payload.unlocks()));
    }

    /** Returns the latest server-authoritative unlock set received by this client. */
    public static Set<String> clientUnlocks() {
        return CLIENT_UNLOCKS.get();
    }

    /** Client entrypoint compatibility hook; input is always copied and bounded by the payload codec. */
    public static void setClientUnlocks(Set<String> unlocks) {
        CLIENT_UNLOCKS.set(unlocks == null ? Set.of() : Set.copyOf(unlocks));
    }

    public static void send(ServerPlayer player) {
        if (player.connection.hasChannel(ProgressionPayload.TYPE)) {
            player.connection.send(ProgressionPayload.of(ProgressionData.get(player)));
        }
    }
}
