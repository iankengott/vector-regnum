package vectorregnum.neoforge.presentation;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import vectorregnum.neoforge.editor.CircleEditorSnapshotPayload;
import vectorregnum.neoforge.ponder.PonderTracePayload;

/** Registers the presentation wire boundary on the dedicated server. */
public final class PresentationNetworking {
    private PresentationNetworking() { }

    /**
     * Registers every client-bound payload type with inert server-side handlers.
     * The physical client registers its real handlers through
     * {@link ClientPresentationRuntime#register(PayloadRegistrar)} instead.
     */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(CircleEditorSnapshotPayload.TYPE, CircleEditorSnapshotPayload.CODEC,
                PresentationNetworking::ignoreCircleSnapshot)
                .playToClient(PonderTracePayload.TYPE, PonderTracePayload.CODEC,
                        PresentationNetworking::ignorePonderTrace)
                .playToClient(PresentationStartPayload.TYPE, PresentationStartPayload.CODEC,
                PresentationNetworking::ignoreStart)
                .playToClient(PresentationSignalPayload.TYPE, PresentationSignalPayload.CODEC,
                        PresentationNetworking::ignoreSignal);
    }

    /** Retained for callers from the pre-port lifecycle bootstrap. */
    public static void initialize() {
        // Payload registration now belongs to RegisterPayloadHandlersEvent.
    }

    private static void ignoreStart(PresentationStartPayload payload, IPayloadContext context) {
        // A server never receives its own client-bound payload.
    }

    private static void ignoreCircleSnapshot(
            CircleEditorSnapshotPayload payload, IPayloadContext context) {
        // A server never receives its own client-bound payload.
    }

    private static void ignorePonderTrace(PonderTracePayload payload, IPayloadContext context) {
        // A server never receives its own client-bound payload.
    }

    private static void ignoreSignal(PresentationSignalPayload payload, IPayloadContext context) {
        // A server never receives its own client-bound payload.
    }
}
