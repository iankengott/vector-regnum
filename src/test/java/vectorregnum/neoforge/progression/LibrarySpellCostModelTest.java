package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LibrarySpellCostModelTest {
    @Test
    void everySemanticProgramReceivesAFiniteNamedVm2Quote() {
        for (SpellDefinition spell : ProgressionSpellLibrary.ALL) {
            var cost = LibrarySpellCostModel.estimate(spell);
            assertTrue(Double.isFinite(cost.total()) && cost.total() > 1.0, spell.id());
            assertTrue(cost.memory() > 0.0, spell.id());
        }
    }

    @Test
    void distantPerceptionAndAutomationPayTheirRelevantDimensions() {
        var life = LibrarySpellCostModel.estimate(ProgressionSpellLibrary.BY_ID.get("life_sense"));
        assertTrue(life.range() > 0.0);
        assertTrue(life.perception() > 0.0);
        var harvest = LibrarySpellCostModel.estimate(ProgressionSpellLibrary.BY_ID.get("harvest_cycle"));
        assertTrue(harvest.duration() > 0.0);
        assertTrue(harvest.controlFlow() > 0.0);
        assertTrue(harvest.physicalWork() > 0.0);
    }
}
