package vectorregnum.fabric.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManaTransportRulesTest {
    @Test
    void transferRespectsThroughputAndAccountsForEveryUnit() {
        var source = new ManaReservoir(ManaReservoir.Tier.RUNED_CELL, ManaAffinity.FIRE, 500);
        var target = new ManaReservoir(ManaReservoir.Tier.RUNED_CELL, ManaAffinity.FIRE, 0);

        var result = ManaTransportRules.transfer(source, target, 300, 12,
                ManaTransportRules.ConduitTier.RUNED);

        assertEquals(95, result.delivered());
        assertEquals(5, result.dissipated());
        assertEquals(400, result.source().stored());
        assertEquals(95, result.destination().stored());
        assertEquals(source.stored() + target.stored(),
                result.source().stored() + result.destination().stored() + result.dissipated());
    }

    @Test
    void affinityAndDestinationCapacityBoundExtraction() {
        var source = new ManaReservoir(ManaReservoir.Tier.RUNED_CELL, ManaAffinity.FIRE, 500);
        var target = new ManaReservoir(ManaReservoir.Tier.CRYSTAL_VIAL, ManaAffinity.FROST, 195);

        var result = ManaTransportRules.transfer(source, target, 100, 4,
                ManaTransportRules.ConduitTier.RESONANT);

        assertEquals(5, result.delivered());
        assertEquals(15, result.dissipated());
        assertEquals(200, result.destination().stored());
    }

    @Test
    void outOfRangeTransferIsAStableNoOp() {
        var source = new ManaReservoir(ManaReservoir.Tier.CRYSTAL_VIAL,
                ManaAffinity.ARCANE, 100);
        var target = new ManaReservoir(ManaReservoir.Tier.CRYSTAL_VIAL,
                ManaAffinity.ARCANE, 0);
        var result = ManaTransportRules.transfer(source, target, 50, 9,
                ManaTransportRules.ConduitTier.RAW_CRYSTAL);

        assertEquals(source, result.source());
        assertEquals(target, result.destination());
        assertEquals(0, result.extracted());
    }
}
