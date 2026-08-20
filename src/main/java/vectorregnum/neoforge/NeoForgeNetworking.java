package vectorregnum.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import vectorregnum.neoforge.editor.CircleEditorClientNetworking;
import vectorregnum.neoforge.editor.CircleEditorNetworking;
import vectorregnum.neoforge.ponder.PonderTraceClientNetworking;
import vectorregnum.neoforge.ponder.PonderTraceNetworking;
import vectorregnum.neoforge.presentation.ClientPresentationRuntime;
import vectorregnum.neoforge.presentation.PresentationNetworking;
import vectorregnum.neoforge.progression.ProgressionSync;

/**
 * Single mod-bus entrypoint for the NeoForge play payload registry.
 *
 * <p>The event is fired once per physical side. Client-only handler methods are
 * reached only on the client branch, so a dedicated server never initializes a
 * client presentation class merely by loading this common registrar.</p>
 */
public final class NeoForgeNetworking {
    public static final String PROTOCOL_VERSION = "1";

    private NeoForgeNetworking() {
    }

    /**
     * Attach this method to the mod event bus with
     * {@code modBus.addListener(NeoForgeNetworking::register)}.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        CircleEditorNetworking.register(registrar);
        PonderTraceNetworking.register(registrar);
        ProgressionSync.register(registrar);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Keep these references behind the physical-side branch; they are
            // never initialized by a dedicated-server registration pass.
            CircleEditorClientNetworking.register(registrar);
            PonderTraceClientNetworking.register(registrar);
            ClientPresentationRuntime.register(registrar);
        } else {
            PresentationNetworking.register(registrar);
        }
    }
}
