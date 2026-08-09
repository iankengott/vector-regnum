package vectorregnum.fabric.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ManaStorageTest {
    @Test
    void insertionAndExtractionAreFiniteAndClamped() {
        ManaStorage storage = new ManaStorage(500);
        assertEquals(500, storage.insert(800));
        assertEquals(500, storage.stored());
        assertEquals(500, storage.extract(900));
        assertEquals(0, storage.stored());
    }

    @Test
    void transferConservesManaAndRespectsDestinationCapacity() {
        ManaStorage source = new ManaStorage(1_000, 700);
        ManaStorage destination = new ManaStorage(400, 250);

        assertEquals(150, ManaStorage.transfer(source, destination, 300));
        assertEquals(550, source.stored());
        assertEquals(400, destination.stored());
        assertEquals(950, source.stored() + destination.stored());
    }

    @Test
    void rejectsInvalidAmountsAndBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ManaStorage(0));
        assertThrows(IllegalArgumentException.class, () -> new ManaStorage(10, 11));
        assertThrows(IllegalArgumentException.class, () -> new ManaStorage(10).insert(-1));
    }
}
