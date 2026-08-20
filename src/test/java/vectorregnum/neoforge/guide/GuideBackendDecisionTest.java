package vectorregnum.neoforge.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuideBackendDecisionTest {
    @Test
    void explicitFabric1211MatrixSelectsTheNativePrototype() {
        GuideBackendDecision.Candidate selected = GuideBackendDecision.selected();
        assertEquals(GuideBackendDecision.SELECTED_BACKEND, selected.id());
        assertTrue(selected.fabric1211());
        assertEquals(27, selected.score());
        assertFalse(GuideBackendDecision.candidates().stream()
                .filter(candidate -> candidate.id().equals("guideme"))
                .findFirst().orElseThrow().fabric1211());
    }
}
