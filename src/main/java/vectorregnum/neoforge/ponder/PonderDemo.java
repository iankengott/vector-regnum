package vectorregnum.neoforge.ponder;

import net.minecraft.client.Minecraft;

/** Deterministic offline teaching set used when no server trace can be requested. */
public final class PonderDemo {
    private PonderDemo() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PonderScreen(
                new PonderController(PonderLessonLibrary.primer())));
    }
}
