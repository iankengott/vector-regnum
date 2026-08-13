package vectorregnum.fabric.ponder;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-only receiver and request path for server-authored traces. */
public final class PonderTraceClientNetworking {
    private static boolean initialized;

    private PonderTraceClientNetworking() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(PonderTracePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().currentScreen instanceof PonderScreen screen) {
                        screen.acceptLiveTimeline(payload.timeline());
                    } else {
                        context.client().setScreen(new PonderScreen(
                                new PonderController(payload.timeline())));
                    }
                }));
    }

    public static void requestLatest(String source) {
        if (ClientPlayNetworking.canSend(PonderTraceRequestPayload.ID)) {
            ClientPlayNetworking.send(new PonderTraceRequestPayload(source));
        } else {
            PonderDemo.open();
        }
    }

    public static void stopWatching() {
        if (ClientPlayNetworking.canSend(PonderTraceRequestPayload.ID)) {
            ClientPlayNetworking.send(new PonderTraceRequestPayload("close"));
        }
    }
}
