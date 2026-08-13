package vectorregnum.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import vectorregnum.fabric.progression.ManaAffinity;
import vectorregnum.fabric.progression.ManaReservoir;
import vectorregnum.fabric.progression.ManaSourceGrowthRules;
import vectorregnum.fabric.progression.ManaTransportRules;
import vectorregnum.fabric.world.ManaCrystalGeology;

/**
 * Compile-safe scenarios shaped like future Fabric GameTests. A registered
 * GameTest adapter only needs to replace the records with blocks and ticks.
 */
class ManaProgressionGameTestHarnessTest {
    @Test
    void naturalVeinReloadRechargeAndStorageRoundTripContract() {
        long seed = 72_001L;
        int plannedX = 0;
        int plannedZ = 0;
        ManaCrystalGeology.VeinPlan plan = null;
        search:
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                var candidate = ManaCrystalGeology.planForChunk(seed, x, z);
                if (candidate.isPresent()) {
                    plannedX = x;
                    plannedZ = z;
                    plan = candidate.orElseThrow();
                    break search;
                }
            }
        }
        assertTrue(plan != null, "fixture seed should locate a natural vein");
        assertEquals(plan, ManaCrystalGeology.planForChunk(seed, plannedX, plannedZ).orElseThrow());

        var environment = new ManaSourceGrowthRules.Environment(true, true, 0);
        var sourceState = ManaSourceGrowthRules.advance(
                ManaSourceGrowthRules.SourceState.youngNatural(), environment,
                3L * ManaSourceGrowthRules.TICKS_PER_DAY);
        var reloadedState = new ManaSourceGrowthRules.SourceState(
                sourceState.origin(), sourceState.growthStage(), sourceState.charges(),
                sourceState.rechargeProgressTicks(), sourceState.growthProgressTicks());
        assertEquals(sourceState, reloadedState);

        var source = new ManaReservoir(ManaReservoir.Tier.RUNED_CELL, ManaAffinity.ARCANE,
                reloadedState.charges() * 100);
        var storage = new ManaReservoir(ManaReservoir.Tier.CRYSTAL_VIAL,
                ManaAffinity.ARCANE, 0);
        var transfer = ManaTransportRules.transfer(source, storage, 100, 12,
                ManaTransportRules.ConduitTier.RUNED);
        assertEquals(95, transfer.delivered());
        assertEquals(5, transfer.dissipated());
    }

    @Test
    void unloadedChunkDoesNotRechargeContract() {
        var state = ManaSourceGrowthRules.SourceState.youngNatural();
        var unloaded = new ManaSourceGrowthRules.Environment(false, true, 0);
        assertEquals(state, ManaSourceGrowthRules.advance(state, unloaded,
                10L * ManaSourceGrowthRules.TICKS_PER_DAY));
    }
}
