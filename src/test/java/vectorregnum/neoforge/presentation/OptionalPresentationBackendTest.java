package vectorregnum.neoforge.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vectorregnum.core.presentation.PresentationAccessibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalPresentationBackendTest {
    @AfterEach
    void reset() {
        OptionalPresentationBackend.resetForTests();
    }

    @Test
    void runtimeFailurePermanentlyFallsBackAndCleansUp() {
        ThrowingBackend backend = new ThrowingBackend();
        OptionalPresentationBackend.setBackendForTests(backend);
        assertTrue(OptionalPresentationBackend.veilActive());

        OptionalPresentationBackend.cueEnded(12);

        assertEquals("built-in", OptionalPresentationBackend.id());
        assertFalse(OptionalPresentationBackend.veilActive());
        assertTrue(backend.cleared);
        OptionalPresentationBackend.cueEnded(13);
        assertEquals(1, backend.calls);
    }

    private static final class ThrowingBackend implements ClientPresentationBackend {
        private int calls;
        private boolean cleared;

        @Override public String id() { return "throwing"; }
        @Override public void cueStarted(PresentationCueContext cue,
                PresentationAccessibility accessibility) { }
        @Override public void cueTick(PresentationCueContext cue,
                PresentationAccessibility accessibility, int localAge, int duration,
                double envelope) { }
        @Override public void cueEnded(long cueId) {
            calls++;
            throw new IllegalStateException("expected test failure");
        }
        @Override public void resourceReloaded() { }
        @Override public void clear() { cleared = true; }
    }
}
