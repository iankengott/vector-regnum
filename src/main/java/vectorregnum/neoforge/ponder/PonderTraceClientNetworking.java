package vectorregnum.neoforge.ponder;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Client-only receiver and request path for server-authored traces. */
public final class PonderTraceClientNetworking {
    private PonderTraceClientNetworking() {
    }

    /** Registers the server-to-client Ponder trace payload. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(PonderTracePayload.TYPE, PonderTracePayload.CODEC,
                PonderTraceClientNetworking::handleTrace);
    }

    /** Runs on the client main thread because the registrar uses its default handler thread. */
    public static void handleTrace(PonderTracePayload payload, IPayloadContext ignored) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof PonderScreen screen) {
            screen.acceptLiveTimeline(payload.timeline());
        } else {
            client.setScreen(new PonderScreen(new PonderController(payload.timeline())));
        }
    }

    /** Retained as a no-op source-compatibility hook for the client entrypoint. */
    public static void initialize() {
    }

    public static void requestLatest(String source) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null
                && client.getConnection().hasChannel(PonderTraceRequestPayload.TYPE)) {
            client.getConnection().send(new PonderTraceRequestPayload(source));
        } else {
            PonderDemo.open();
        }
    }

    public static void stopWatching() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null
                && client.getConnection().hasChannel(PonderTraceRequestPayload.TYPE)) {
            client.getConnection().send(new PonderTraceRequestPayload("close"));
        }
    }
}
