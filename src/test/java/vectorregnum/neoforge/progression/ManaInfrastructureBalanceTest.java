package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManaInfrastructureBalanceTest {
    @Test
    void storageTiersCoverEarlyMidAndLateGameEconomy() {
        assertEquals(2 * ManaCrystalNodeBlock.MANA_PER_CHARGE,
                ManaReservoir.Tier.CRYSTAL_VIAL.capacity());
        assertEquals(10 * ManaCrystalNodeBlock.MANA_PER_CHARGE,
                ManaReservoir.Tier.RUNED_CELL.capacity());
        assertEquals(80 * ManaCrystalNodeBlock.MANA_PER_CHARGE,
                ManaReservoir.Tier.RESONANT_VAULT.capacity());
    }

    @Test
    void conduitTiersApplyDocumentedPerChargeEfficiency() {
        assertEquals(80, ManaReservoirBlockEntity.expectedDelivery(100,
                ManaAffinity.FIRE, ManaAffinity.FIRE, 8,
                ManaTransportRules.ConduitTier.RAW_CRYSTAL));
        assertEquals(95, ManaReservoirBlockEntity.expectedDelivery(100,
                ManaAffinity.FIRE, ManaAffinity.FIRE, 24,
                ManaTransportRules.ConduitTier.RUNED));
        assertEquals(100, ManaReservoirBlockEntity.expectedDelivery(100,
                ManaAffinity.FIRE, ManaAffinity.FIRE, 64,
                ManaTransportRules.ConduitTier.RESONANT));
    }

    @Test
    void affinityMismatchCannotIncreaseTransportYield() {
        assertEquals(20, ManaReservoirBlockEntity.expectedDelivery(100,
                ManaAffinity.FIRE, ManaAffinity.ICE, 8,
                ManaTransportRules.ConduitTier.RAW_CRYSTAL));
        assertEquals(23, ManaReservoirBlockEntity.expectedDelivery(100,
                ManaAffinity.FIRE, ManaAffinity.ICE, 24,
                ManaTransportRules.ConduitTier.RUNED));
        assertEquals(25, ManaReservoirBlockEntity.expectedDelivery(100,
                ManaAffinity.FIRE, ManaAffinity.ICE, 64,
                ManaTransportRules.ConduitTier.RESONANT));
    }

    @Test
    void persistedPendingTransferIsBoundedAndPreventsRetuning() {
        var restored = ManaReservoirBlockEntity.restorePending(500, "fire", 1000,
                ManaTransportRules.ConduitTier.RAW_CRYSTAL);
        assertEquals(100, restored.input());
        assertEquals(ManaAffinity.FIRE, restored.affinity());
        assertEquals(8, restored.distance());
        assertEquals(false, ManaReservoirBlockEntity.canRetune(0, restored.input()));
        assertEquals(true, ManaReservoirBlockEntity.canRetune(0, 0));
    }

    @Test
    void oldFrostPendingTransfersMigrateToIce() {
        var restored = ManaReservoirBlockEntity.restorePending(100, "frost", 8,
                ManaTransportRules.ConduitTier.RAW_CRYSTAL);
        assertEquals(ManaAffinity.ICE, restored.affinity());
        assertEquals(ManaAffinity.ARCANE,
                ManaReservoirBlockEntity.restorePending(100, "not-an-element", 8,
                        ManaTransportRules.ConduitTier.RAW_CRYSTAL).affinity());
    }
}
