package vectorregnum.fabric.ponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vectorregnum.core.WildMagicCategory;

class PonderLessonLibraryTest {
    @Test
    void primerCoversTheCompleteSpellAndScrollTeachingContract() {
        PonderTimeline primer = PonderLessonLibrary.primer();

        assertEquals(EnumSet.allOf(PonderTimeline.Phase.class), primer.steps().stream()
                .map(PonderTimeline.Step::phase).collect(Collectors.toSet()));
        assertTrue(hasCue(primer, PonderTimeline.CueType.SET_PIECE));
        assertTrue(hasCue(primer, PonderTimeline.CueType.TRACE_CLOCKWISE));
        assertTrue(hasCue(primer, PonderTimeline.CueType.MOVE_INWARD));
        assertTrue(hasCue(primer, PonderTimeline.CueType.MANA_SEGMENT));
        assertTrue(hasCue(primer, PonderTimeline.CueType.MANA_FLOW));
        assertTrue(hasCue(primer, PonderTimeline.CueType.EXECUTION_CURSOR));
        assertTrue(hasCue(primer, PonderTimeline.CueType.WORLD_EFFECT));
        assertTrue(hasCue(primer, PonderTimeline.CueType.COMPILER_FAULT));
        assertTrue(hasCue(primer, PonderTimeline.CueType.RUNTIME_FAULT));
        assertTrue(hasCue(primer, PonderTimeline.CueType.SCROLL_STATE));

        Set<String> categories = primer.steps().stream().flatMap(step -> step.cues().stream())
                .filter(cue -> cue.type() == PonderTimeline.CueType.WILD_MAGIC)
                .map(cue -> cue.data().get("category")).collect(Collectors.toSet());
        assertEquals(Set.of(WildMagicCategory.INTERNAL_MANA_DETONATION.name(),
                WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST.name(),
                WildMagicCategory.VIOLENT_MISCAST.name()), categories);
    }

    @Test
    void authoredReconstructionsRemainExplicitlyNonGameplay() {
        PonderTimeline primer = PonderLessonLibrary.primer();
        assertTrue(primer.steps().stream()
                .filter(step -> step.cues().stream()
                        .anyMatch(cue -> cue.type() == PonderTimeline.CueType.WILD_MAGIC))
                .allMatch(step -> step.narration().toLowerCase().contains("reconstruction")
                        || step.narration().toLowerCase().contains("lesson")));
    }

    private static boolean hasCue(PonderTimeline timeline, PonderTimeline.CueType type) {
        return timeline.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == type);
    }
}
