package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ManaDrawRulesTest {
    @Test
    void drawFallsByInverseSquare() {
        assertEquals(100, ManaDrawRules.offeredMana(100, 1, ManaAffinity.FIRE, ManaAffinity.FIRE));
        assertEquals(25, ManaDrawRules.offeredMana(100, 2, ManaAffinity.FIRE, ManaAffinity.FIRE));
        assertEquals(4, ManaDrawRules.offeredMana(100, 5, ManaAffinity.FIRE, ManaAffinity.FIRE));
    }

    @Test
    void elementalCompatibilityChangesUsefulDraw() {
        assertEquals(100, ManaDrawRules.offeredMana(100, 1, ManaAffinity.FIRE, ManaAffinity.FIRE));
        assertEquals(75, ManaDrawRules.offeredMana(100, 1, ManaAffinity.ARCANE, ManaAffinity.FIRE));
        assertEquals(25, ManaDrawRules.offeredMana(100, 1, ManaAffinity.FIRE, ManaAffinity.FROST));
        assertEquals(40, ManaDrawRules.offeredMana(100, 1, ManaAffinity.VOID, ManaAffinity.FIRE));
    }

    @Test
    void shardsGrowCapacityButNeverExceedLimit() {
        assertEquals(300, ManaDrawRules.capacityAfterShard(200, 500));
        assertEquals(500, ManaDrawRules.capacityAfterShard(450, 500));
        assertThrows(IllegalArgumentException.class,
                () -> ManaDrawRules.capacityAfterShard(600, 500));
    }
}
