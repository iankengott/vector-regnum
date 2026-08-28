package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.Element;

class ManaAffinityTest {
    @Test
    void channelValuesFollowCanonicalElementOrderAndRoundTrip() {
        assertEquals(14, ManaAffinity.channelValues().size());
        assertFalse(ManaAffinity.channelValues().contains(ManaAffinity.LEGACY_FROST));
        for (ManaAffinity affinity : ManaAffinity.channelValues()) {
            assertEquals(affinity, ManaAffinity.fromId(affinity.getSerializedName()).orElseThrow());
            assertEquals(affinity, ManaAffinity.fromElement(affinity.element()));
        }
        assertEquals(ManaAffinity.ICE, ManaAffinity.fromId("frost").orElseThrow());
        assertEquals(ManaAffinity.ICE, ManaAffinity.LEGACY_FROST.canonical());
        assertEquals(ManaAffinity.LEGACY_FROST,
                ManaCrystalNodeBlock.AFFINITY.getValue("frost").orElseThrow());
        assertEquals(ManaAffinity.LEGACY_FROST,
                ManaReservoirBlock.AFFINITY.getValue("frost").orElseThrow());
        assertFalse(ManaAffinity.fromId("not-an-element").isPresent());
    }

    @Test
    void arcaneIsAChannelButNotANaturalElement() {
        assertTrue(ManaAffinity.ARCANE.element().isNeutralMana());
        assertFalse(ManaAffinity.ARCANE.element().isNatural());
        assertEquals(12, Element.ordinary().size());
        assertEquals(13, Element.natural().size());
    }
}
