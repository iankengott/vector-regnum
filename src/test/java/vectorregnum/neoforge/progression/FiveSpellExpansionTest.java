package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vectorregnum.core.presentation.PresentationCompiler;
import vectorregnum.core.presentation.PresentationTrigger;
import vectorregnum.core.semantic.LoweringContext;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.semantic.SemanticVmLowerer;
import vectorregnum.neoforge.LibrarySemanticAdapter;

class FiveSpellExpansionTest {
    @Test
    void requestedExpansionIsExactlyFiveBoundedDefinitions() {
        assertEquals(Set.of("fireball", "teleport", "storm_arc", "tidal_prison", "stone_aegis"),
                ProgressionSpellLibrary.FIVE_SPELL_EXPANSION_IDS);
        for (String id : ProgressionSpellLibrary.FIVE_SPELL_EXPANSION_IDS) {
            SpellDefinition spell = ProgressionSpellLibrary.BY_ID.get(id);
            assertTrue(spell != null, id);
            assertTrue(spell.program().size() <= 12, id);
        }
    }

    @Test
    void expansionCompilesThroughTheSharedVmAndCostModel() {
        for (String id : ProgressionSpellLibrary.FIVE_SPELL_EXPANSION_IDS) {
            var semantic = LibrarySemanticAdapter.adapt(ProgressionSpellLibrary.BY_ID.get(id));
            var program = SemanticVmLowerer.lowerChecked(semantic,
                    new LoweringContext(id, 0L, Map.of()));
            assertTrue(program.manaCost().total() > 1.0, id);
            assertTrue(program.instructions().size() >= semantic.instructions().size(), id);
        }
        assertTrue(compiled("fireball").declaredCost().physicalWork() > 0.0);
        assertTrue(compiled("teleport").declaredCost().rarity() > 0.0);
    }

    @Test
    void onlyTheTwoTimedSpellsCommitUpkeepDurations() {
        assertEquals(120, durationTicks("tidal_prison"));
        assertEquals(160, durationTicks("stone_aegis"));
        assertEquals(0, durationTicks("fireball"));
        assertEquals(0, durationTicks("teleport"));
        assertEquals(0, durationTicks("storm_arc"));
    }

    @Test
    void ExplosionAndTeleportCompileMandatoryTruthCues() {
        assertTrue(hasTruthTrigger("fireball", SemanticOpcode.APPLY_EXPLOSION));
        assertTrue(hasTruthTrigger("teleport", SemanticOpcode.TELEPORT_CASTER));
    }

    private static vectorregnum.core.vm2.Program compiled(String id) {
        var semantic = LibrarySemanticAdapter.adapt(ProgressionSpellLibrary.BY_ID.get(id));
        return SemanticVmLowerer.lowerChecked(semantic, new LoweringContext(id, 0L, Map.of()));
    }

    private static int durationTicks(String id) {
        return ProgressionSpellLibrary.BY_ID.get(id).program().stream()
                .filter(step -> step.opcode() == LibraryOpcode.SET_DURATION)
                .mapToInt(step -> ((Number) step.operands().get("ticks")).intValue())
                .max().orElse(0);
    }

    private static boolean hasTruthTrigger(String id, SemanticOpcode opcode) {
        var program = compiled(id);
        return PresentationCompiler.compile(id, 5L, program).instructions().stream()
                .anyMatch(cue -> cue.truthLayer()
                        && cue.trigger().equals(PresentationTrigger.semantic(opcode)));
    }
}
