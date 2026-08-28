package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class ManaTuningItemsTest {
    @Test
    void tuningItemsCoverEachCanonicalChannelExactlyOnce() {
        assertEquals(ManaAffinity.channelValues().size(), ManaTuningItems.entries().size());
        assertEquals(ManaAffinity.channelValues().size(),
                ManaTuningItems.entries().values().stream().distinct().count());
        assertEquals(ManaAffinity.WATER,
                ManaTuningItems.affinity(Items.PRISMARINE_CRYSTALS.getDefaultInstance()).orElseThrow());
        assertEquals(ManaAffinity.SPACE,
                ManaTuningItems.affinity(Items.ENDER_PEARL.getDefaultInstance()).orElseThrow());
        assertEquals(ManaAffinity.VOID,
                ManaTuningItems.affinity(Items.ENDER_EYE.getDefaultInstance()).orElseThrow());
    }

    @Test
    void unmappedItemsDoNotTuneAChannel() {
        assertTrue(ManaTuningItems.affinity(Items.STICK.getDefaultInstance()).isEmpty());
    }
}
