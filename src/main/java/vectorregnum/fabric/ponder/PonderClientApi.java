package vectorregnum.fabric.ponder;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import vectorregnum.core.circle.Vm2CircleCompilation;
import vectorregnum.core.vm2.TickResult;

/** Client registration seam for a screen or in-world Ponder renderer. */
public final class PonderClientApi {
    private PonderClientApi() { }

    @FunctionalInterface
    public interface SceneHost {
        void open(PonderController controller);
    }

    public static void openVmTrace(SceneHost host, String id, String title,
            Vm2CircleCompilation compilation, List<TickResult> actualTicks) {
        Objects.requireNonNull(host, "host").open(new PonderController(
                PonderTimelineBuilder.fromVm2(id, title, compilation, actualTicks)));
    }

    /** Ready-to-call native client path once a client entrypoint forwards a trace. */
    public static void openMinecraftVmTrace(String id, String title,
            Vm2CircleCompilation compilation, List<TickResult> actualTicks) {
        openVmTrace(controller -> MinecraftClient.getInstance().setScreen(new PonderScreen(controller)),
                id, title, compilation, actualTicks);
    }
}
