package vectorregnum.fabric.presentation;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the presentation wire boundary during normal common initialization. */
public final class PresentationNetworking {
    private static boolean initialized;

    private PresentationNetworking() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.playS2C().register(PresentationStartPayload.ID,
                PresentationStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PresentationSignalPayload.ID,
                PresentationSignalPayload.CODEC);
    }
}
