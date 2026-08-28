package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(50, ManaDrawRules.offeredMana(100, 1, ManaAffinity.ARCANE, ManaAffinity.FIRE));
        assertEquals(25, ManaDrawRules.offeredMana(100, 1, ManaAffinity.FIRE, ManaAffinity.ICE));
        assertEquals(25, ManaDrawRules.offeredMana(100, 1, ManaAffinity.VOID, ManaAffinity.FIRE));
    }

    @Test
    void canonicalMatrixIsExhaustiveSymmetricAndBandLimited() {
        assertEquals(14, ManaAffinity.channelValues().size());
        int[][] expected = {
                {100,75,75,50,25,25,25,25,50,50,75,75,25,50},
                {75,100,75,50,25,25,25,50,25,50,25,50,25,50},
                {75,75,100,75,50,25,25,25,50,75,50,25,25,50},
                {50,50,75,100,75,50,25,25,25,75,50,25,25,50},
                {25,25,50,75,100,75,50,25,25,50,25,50,25,50},
                {25,25,25,50,75,100,75,50,25,25,50,25,25,50},
                {25,25,25,25,50,75,100,75,50,25,25,50,25,50},
                {25,50,25,25,25,50,75,100,75,50,25,50,25,50},
                {50,25,50,25,25,25,50,75,100,25,50,25,25,50},
                {50,50,75,75,50,25,25,50,25,100,75,75,25,50},
                {75,25,50,50,25,50,25,25,50,75,100,75,25,50},
                {75,50,25,25,50,25,50,50,25,75,75,100,25,50},
                {25,25,25,25,25,25,25,25,25,25,25,25,100,50},
                {50,50,50,50,50,50,50,50,50,50,50,50,50,100}
        };
        for (ManaAffinity source : ManaAffinity.channelValues()) {
            for (ManaAffinity requested : ManaAffinity.channelValues()) {
                double forward = ManaDrawRules.compatibility(source, requested);
                double reverse = ManaDrawRules.compatibility(requested, source);
                int sourceIndex = source.ordinal();
                int requestedIndex = requested.ordinal();
                assertEquals(expected[sourceIndex][requestedIndex] / 100.0, forward, 1.0e-9,
                        source + " and " + requested + " must match the canonical matrix");
                assertEquals(forward, reverse, 1.0e-9,
                        source + " and " + requested + " must be symmetric");
                assertTrue(forward == 1.0 || forward == 0.75 || forward == 0.5 || forward == 0.25,
                        source + " and " + requested + " must use a canonical efficiency band");
                if (source == requested) {
                    assertEquals(1.0, forward, 1.0e-9);
                } else {
                    assertTrue(forward >= 0.25 && forward < 1.0);
                }
            }
        }
    }

    @Test
    void shardsGrowCapacityButNeverExceedLimit() {
        assertEquals(300, ManaDrawRules.capacityAfterShard(200, 500));
        assertEquals(500, ManaDrawRules.capacityAfterShard(450, 500));
        assertThrows(IllegalArgumentException.class,
                () -> ManaDrawRules.capacityAfterShard(600, 500));
    }
}
