package vectorregnum.neoforge.presentation;

import java.lang.reflect.InvocationTargetException;
import net.neoforged.fml.ModList;
import vectorregnum.core.presentation.PresentationAccessibility;
import vectorregnum.neoforge.VectorRegnumMod;

/** Loads Veil only on a physical client and permanently fails back to built-in rendering. */
final class OptionalPresentationBackend {
    private static final ClientPresentationBackend NONE = new NoBackend();
    private static ClientPresentationBackend backend = NONE;
    private static boolean initialized;

    private OptionalPresentationBackend() { }

    static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("veil")) {
            VectorRegnumMod.LOGGER.info("Vector-Regnum presentation backend: built-in (Veil absent)");
            return;
        }
        try {
            Class<?> type = Class.forName(
                    "vectorregnum.neoforge.presentation.VeilPresentationBackend",
                    true, OptionalPresentationBackend.class.getClassLoader());
            backend = (ClientPresentationBackend) type.getDeclaredConstructor().newInstance();
            String version = ModList.get().getModContainerById("veil")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
            VectorRegnumMod.LOGGER.info(
                    "Vector-Regnum presentation backend: {} (Veil {})", backend.id(), version);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | NoSuchMethodException | InvocationTargetException | LinkageError exception) {
            fail(exception);
        }
    }

    static String id() {
        return backend.id();
    }

    static boolean veilActive() {
        return backend != NONE;
    }

    static void cueStarted(PresentationCueContext cue, PresentationAccessibility accessibility) {
        invoke(() -> backend.cueStarted(cue, accessibility));
    }

    static void cueTick(PresentationCueContext cue, PresentationAccessibility accessibility,
            int localAge, int duration, double envelope) {
        invoke(() -> backend.cueTick(cue, accessibility, localAge, duration, envelope));
    }

    static void cueEnded(long cueId) {
        invoke(() -> backend.cueEnded(cueId));
    }

    static void resourceReloaded() {
        invoke(backend::resourceReloaded);
    }

    static void clear() {
        invoke(backend::clear);
    }

    static void setBackendForTests(ClientPresentationBackend testBackend) {
        backend = testBackend == null ? NONE : testBackend;
        initialized = true;
    }

    static void resetForTests() {
        backend = NONE;
        initialized = false;
    }

    private static void invoke(Runnable action) {
        if (backend == NONE) return;
        try {
            action.run();
        } catch (RuntimeException | LinkageError exception) {
            fail(exception);
        }
    }

    private static void fail(Throwable failure) {
        ClientPresentationBackend failed = backend;
        backend = NONE;
        if (failed != NONE) {
            try {
                failed.clear();
            } catch (RuntimeException | LinkageError cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        VectorRegnumMod.LOGGER.error(
                "Veil presentation backend failed; built-in truth rendering remains active", failure);
    }

    private static final class NoBackend implements ClientPresentationBackend {
        @Override public String id() { return "built-in"; }
        @Override public void cueStarted(PresentationCueContext cue,
                PresentationAccessibility accessibility) { }
        @Override public void cueTick(PresentationCueContext cue,
                PresentationAccessibility accessibility, int localAge, int duration,
                double envelope) { }
        @Override public void cueEnded(long cueId) { }
        @Override public void resourceReloaded() { }
        @Override public void clear() { }
    }
}
