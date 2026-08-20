package vectorregnum.neoforge.ponder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import vectorregnum.core.CastResult;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.Vm2CircleCompilation;

/** Owns per-player completed traces; gameplay never trusts or waits on the client. */
public final class PonderTraceNetworking {
    private static final Map<UUID, PonderTimeline> LATEST = new ConcurrentHashMap<>();
    private static final Set<UUID> LIVE_SUBSCRIBERS = ConcurrentHashMap.newKeySet();
    private PonderTraceNetworking() {
    }

    /** Registers the client-to-server Ponder request payload. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(PonderTraceRequestPayload.TYPE, PonderTraceRequestPayload.CODEC,
                PonderTraceNetworking::handleRequest);
    }

    /** Handles requests on NeoForge's main server thread (the registrar default). */
    public static void handleRequest(PonderTraceRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.source().equals("close")) {
            LIVE_SUBSCRIBERS.remove(player.getUUID());
            return;
        }
        LIVE_SUBSCRIBERS.add(player.getUUID());
        sendLatest(player);
    }

    /** Called by the common disconnect lifecycle hook so retained traces cannot leak. */
    public static void onDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LATEST.remove(player.getUUID());
        LIVE_SUBSCRIBERS.remove(player.getUUID());
    }

    /** Publishes a completed trace and closes any one-shot live subscription. */
    public static void publish(ServerPlayer player, PonderTimeline timeline) {
        LATEST.put(player.getUUID(), timeline);
        sendIfSubscribed(player, timeline);
        LIVE_SUBSCRIBERS.remove(player.getUUID());
    }

    /**
     * Updates the retained trace while a server VM is active. Only a player who
     * explicitly requested Ponder receives these bounded snapshots.
     */
    public static void publishLive(ServerPlayer player, PonderTimeline timeline) {
        LATEST.put(player.getUUID(), timeline);
        sendIfSubscribed(player, timeline);
    }

    public static void publishCompatibility(ServerPlayer player, String id, String title,
            CircleCompilation compilation, CastResult result) {
        try {
            publish(player, PonderTimelineBuilder.fromCompatibility(id, title, compilation, result));
        } catch (RuntimeException exception) {
            // Presentation is deliberately non-authoritative and can never alter a cast result.
            vectorregnum.neoforge.VectorRegnumMod.LOGGER.warn(
                    "Could not retain compatibility Ponder trace {}", id, exception);
        }
    }

    public static void publishCompilation(ServerPlayer player, String id, String title,
            Vm2CircleCompilation compilation) {
        try {
            publish(player, PonderTimelineBuilder.fromVm2(id, title, compilation, java.util.List.of()));
        } catch (RuntimeException exception) {
            vectorregnum.neoforge.VectorRegnumMod.LOGGER.warn(
                    "Could not retain compiler Ponder trace {}", id, exception);
        }
    }

    private static void sendLatest(ServerPlayer player) {
        PonderTimeline timeline = LATEST.get(player.getUUID());
        if (timeline == null) {
            timeline = PonderLessonLibrary.primer();
            player.sendSystemMessage(Component.literal(
                    "Opening the workshop primer; cast a spell to replace it with a live server trace.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
        send(player, timeline);
    }

    private static void sendIfSubscribed(ServerPlayer player, PonderTimeline timeline) {
        if (LIVE_SUBSCRIBERS.contains(player.getUUID())) send(player, timeline);
    }

    private static void send(ServerPlayer player, PonderTimeline timeline) {
        if (player.connection.hasChannel(PonderTracePayload.TYPE)) {
            player.connection.send(PonderTracePayload.of(timeline));
        }
    }
}
