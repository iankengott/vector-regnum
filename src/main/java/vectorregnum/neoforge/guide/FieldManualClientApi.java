package vectorregnum.neoforge.guide;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;

/**
 * Client integration seam. A Fabric client initializer should create one
 * opener and invoke it from the Field Manual item/key/command interaction.
 */
public final class FieldManualClientApi {
    private FieldManualClientApi() { }

    @FunctionalInterface
    public interface ScreenHost {
        void open(GuideScreenController controller);
    }

    public static Runnable defaultOpener(ScreenHost host, Supplier<Set<String>> progression) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(progression, "progression");
        return () -> {
            try {
                GuideBook book = GuideDataLoader.loadDefault(FieldManualClientApi.class.getClassLoader());
                host.open(new GuideScreenController(book, progression.get()));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to open the Vector-Regnum Field Manual", exception);
            }
        };
    }

    /** Ready-to-register native opener for a Fabric client entrypoint. */
    public static Runnable minecraftOpener(Supplier<Set<String>> progression) {
        return minecraftOpener(progression, () -> { });
    }

    /** Opens the visual manual and gives Ponder cards a real client action. */
    public static Runnable minecraftOpener(Supplier<Set<String>> progression,
            Runnable ponderOpener) {
        Objects.requireNonNull(progression, "progression");
        Objects.requireNonNull(ponderOpener, "ponderOpener");
        return () -> {
            try {
                ClassLoader loader = FieldManualClientApi.class.getClassLoader();
                GuideBook book = GuideDataLoader.loadDefault(loader);
                GuideRecipeCatalog recipes = GuideRecipeCatalog.load(book, loader);
                MinecraftClient.getInstance().setScreen(new FieldManualScreen(
                        new GuideScreenController(book, progression.get()), ponderOpener, recipes));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to open the Vector-Regnum Field Manual", exception);
            }
        };
    }
}
