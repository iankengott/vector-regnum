package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressionStateTest {
    @Test
    void unlocksAreImmutableIdempotentAndSerializableById() {
        ProgressionState base = ProgressionState.EMPTY;
        ProgressionState unlocked = base.unlock(ProgressionUnlock.CRYSTAL_HARVEST)
                .unlock(ProgressionUnlock.MANA_STORAGE);

        assertFalse(base.has(ProgressionUnlock.CRYSTAL_HARVEST));
        assertTrue(unlocked.hasAll(List.of(
                ProgressionUnlock.CRYSTAL_HARVEST,
                ProgressionUnlock.MANA_STORAGE)));
        assertSame(unlocked, unlocked.unlock(ProgressionUnlock.MANA_STORAGE));
        assertEquals(unlocked, ProgressionState.fromIds(unlocked.ids()));
    }
}
