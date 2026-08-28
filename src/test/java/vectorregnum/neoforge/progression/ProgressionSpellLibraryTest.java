package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProgressionSpellLibraryTest {
    @Test
    void libraryHasMultipleSpellsAndEveryGameplayCategory() {
        assertTrue(ProgressionSpellLibrary.ALL.size() >= 15);
        assertEquals(EnumSet.allOf(SpellCategory.class),
                ProgressionSpellLibrary.ALL.stream()
                        .map(SpellDefinition::category)
                        .collect(() -> EnumSet.noneOf(SpellCategory.class), Set::add, Set::addAll));
    }

    @Test
    void idsAreUniqueAndProgramsAreBoundedExecutableDefinitions() {
        Set<String> ids = new HashSet<>();
        for (SpellDefinition spell : ProgressionSpellLibrary.ALL) {
            assertTrue(ids.add(spell.id()), spell.id());
            assertEquals(LibraryOpcode.EXECUTE, spell.program().getLast().opcode());
            assertTrue(spell.program().size() <= 12, spell.id());
            assertTrue(spell.program().stream()
                    .filter(step -> step.opcode() == LibraryOpcode.REPEAT_BOUNDED)
                    .allMatch(step -> ((Number) step.operands().get("count")).doubleValue() <= 8));
        }
        assertEquals(ids, ProgressionSpellLibrary.BY_ID.keySet());
    }

    @Test
    void progressionGatesDefinitions() {
        SpellDefinition oracle = ProgressionSpellLibrary.BY_ID.get("redstone_oracle");
        assertFalse(oracle.isUnlocked(ProgressionState.EMPTY));
        assertTrue(oracle.isUnlocked(ProgressionState.EMPTY.unlock(ProgressionUnlock.AUTOMATION_WEAVING)));
    }

    @Test
    void frostLibraryIdentityIsCanonicalizedToIce() {
        assertTrue(ProgressionSpellLibrary.BY_ID.containsKey("chain_ice"));
        assertFalse(ProgressionSpellLibrary.BY_ID.containsKey("chain_frost"));
        assertTrue(ProgressionSpellLibrary.BY_ID.get("chain_ice").program().stream()
                .anyMatch(step -> step.opcode() == LibraryOpcode.ELEMENT_ICE));
    }
}
