package vectorregnum.neoforge.ponder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vectorregnum.core.CastResult;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.Vm2CircleCompilation;

/** Owns per-player completed traces; gameplay never trusts or waits on the client. */
public final class PonderTraceNetworking {
    private static final Map<UUID, PonderTimeline> LATEST = new ConcurrentHashMap<>();
    private static final Set<UUID> LIVE_SUBSCRIBERS = ConcurrentHashMap.newKeySet();
    private static boolean initialized;

    private PonderTraceNetworking() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.playC2S().register(PonderTraceRequestPayload.ID,
                PonderTraceRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PonderTracePayload.ID, PonderTracePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PonderTraceRequestPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (payload.source().equals("close")) {
                        LIVE_SUBSCRIBERS.remove(context.player().getUuid());
                        return;
                    }
                    LIVE_SUBSCRIBERS.add(context.player().getUuid());
                    sendLatest(context.player());
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LATEST.remove(handler.player.getUuid());
            LIVE_SUBSCRIBERS.remove(handler.player.getUuid());
        });
    }

    /** Publishes a completed trace and closes any one-shot live subscription. */
    public static void publish(ServerPlayerEntity player, PonderTimeline timeline) {
        LATEST.put(player.getUuid(), timeline);
        sendIfSubscribed(player, timeline);
        LIVE_SUBSCRIBERS.remove(player.getUuid());
    }

    /**
     * Updates the retained trace while a server VM is active. Only a player who
     * explicitly requested Ponder receives these bounded snapshots.
     */
    public static void publishLive(ServerPlayerEntity player, PonderTimeline timeline) {
        LATEST.put(player.getUuid(), timeline);
        sendIfSubscribed(player, timeline);
    }

    public static void publishCompatibility(ServerPlayerEntity player, String id, String title,
            CircleCompilation compilation, CastResult result) {
        try {
            publish(player, PonderTimelineBuilder.fromCompatibility(id, title, compilation, result));
        } catch (RuntimeException exception) {
            // Presentation is deliberately non-authoritative and can never alter a cast result.
            vectorregnum.neoforge.VectorRegnumMod.LOGGER.warn(
                    "Could not retain compatibility Ponder trace {}", id, exception);
        }
    }

    public static void publishCompilation(ServerPlayerEntity player, String id, String title,
            Vm2CircleCompilation compilation) {
        try {
            publish(player, PonderTimelineBuilder.fromVm2(id, title, compilation, java.util.List.of()));
        } catch (RuntimeException exception) {
            vectorregnum.neoforge.VectorRegnumMod.LOGGER.warn(
                    "Could not retain compiler Ponder trace {}", id, exception);
        }
    }

    private static void sendLatest(ServerPlayerEntity player) {
        PonderTimeline timeline = LATEST.get(player.getUuid());
        if (timeline == null) {
            timeline = PonderLessonLibrary.primer();
            player.sendMessage(Text.literal(
                    "Opening the workshop primer; cast a spell to replace it with a live server trace.")
                    .formatted(Formatting.GOLD), true);
        }
        send(player, timeline);
    }

    private static void sendIfSubscribed(ServerPlayerEntity player, PonderTimeline timeline) {
        if (LIVE_SUBSCRIBERS.contains(player.getUuid())) send(player, timeline);
    }

    private static void send(ServerPlayerEntity player, PonderTimeline timeline) {
        if (ServerPlayNetworking.canSend(player, PonderTracePayload.ID)) {
            ServerPlayNetworking.send(player, PonderTracePayload.of(timeline));
        }
    }
}
