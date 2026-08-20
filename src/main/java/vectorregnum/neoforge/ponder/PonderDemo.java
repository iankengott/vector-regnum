package vectorregnum.neoforge.ponder;

import net.minecraft.client.MinecraftClient;

/** Deterministic offline teaching set used when no server trace can be requested. */
public final class PonderDemo {
    private PonderDemo() {
    }

    public static void open() {
        MinecraftClient.getInstance().setScreen(new PonderScreen(
                new PonderController(PonderLessonLibrary.primer())));
    }
}
