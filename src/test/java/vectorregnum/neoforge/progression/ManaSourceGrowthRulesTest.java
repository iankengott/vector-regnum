package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ManaSourceGrowthRulesTest {
    private static final ManaSourceGrowthRules.Environment HEALTHY =
            new ManaSourceGrowthRules.Environment(true, true, 0);

    @Test
    void naturalSourceRechargesAndMaturesOnDeterministicTicks() {
        var afterDay = ManaSourceGrowthRules.advance(
                ManaSourceGrowthRules.SourceState.youngNatural(),
                HEALTHY, ManaSourceGrowthRules.TICKS_PER_DAY);
        assertEquals(1, afterDay.charges());
        assertEquals(0, afterDay.growthStage());

        var afterThreeDays = ManaSourceGrowthRules.advance(
                ManaSourceGrowthRules.SourceState.youngNatural(),
                HEALTHY, 3L * ManaSourceGrowthRules.TICKS_PER_DAY);
        assertEquals(1, afterThreeDays.growthStage());
        assertEquals(3, afterThreeDays.charges());
        assertEquals(4, ManaSourceGrowthRules.capacityForStage(afterThreeDays.growthStage()));
    }

    @Test
    void constructedAndUnloadedSourcesRemainFinite() {
        var constructed = ManaSourceGrowthRules.SourceState.fullConstructed();
        assertEquals(constructed, ManaSourceGrowthRules.advance(constructed, HEALTHY,
                ManaSourceGrowthRules.MAX_CATCH_UP_TICKS));

        var natural = ManaSourceGrowthRules.SourceState.youngNatural();
        var unloaded = new ManaSourceGrowthRules.Environment(false, true, 0);
        assertEquals(natural, ManaSourceGrowthRules.advance(natural, unloaded,
                ManaSourceGrowthRules.MAX_CATCH_UP_TICKS));
    }

    @Test
    void competitionSlowsRechargeAndCatchUpIsBounded() {
        var crowded = new ManaSourceGrowthRules.Environment(true, true, 3);
        var initial = ManaSourceGrowthRules.SourceState.youngNatural();
        var afterDay = ManaSourceGrowthRules.advance(initial, crowded,
                ManaSourceGrowthRules.TICKS_PER_DAY);
        assertEquals(0, afterDay.charges());

        var afterHugeGap = ManaSourceGrowthRules.advance(initial, HEALTHY, Long.MAX_VALUE);
        assertEquals(2, afterHugeGap.growthStage());
        assertEquals(6, afterHugeGap.charges());
        assertThrows(IllegalArgumentException.class,
                () -> ManaSourceGrowthRules.advance(initial, HEALTHY, -1));
    }
}
