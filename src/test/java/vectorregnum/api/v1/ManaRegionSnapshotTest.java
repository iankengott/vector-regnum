package vectorregnum.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManaRegionSnapshotTest {
    @Test
    void snapshotCopiesAndCanonicalizesSummary() {
        Map<String, Double> supplied = new LinkedHashMap<>();
        supplied.put("frost", 2.5);
        supplied.put("arcane", 1.0);
        ManaRegionSnapshot snapshot = new ManaRegionSnapshot("minecraft:overworld", 4, 5, 6,
                12, supplied, 2, false, false);
        supplied.put("fire", 8.0);

        assertEquals(Map.of("arcane", 1.0, "ice", 2.5), snapshot.manaByElement());
        assertEquals(2.5, snapshot.manaByElement().get("ice"));
        assertTrue(snapshot.manaByElement() != supplied);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.manaByElement().put("fire", 1.0));
    }

    @Test
    void radiusAndEntryBoundsAreEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManaRegionSnapshot("minecraft:overworld", 0, 0, 0,
                        ManaRegionSnapshot.MAX_QUERY_RADIUS + 1, Map.of(), 0, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ManaRegionSnapshot("minecraft:overworld", 0, 0, 0, 0,
                        Map.of(), ManaRegionSnapshot.MAX_QUERY_ENTRIES + 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ManaRegionSnapshot("minecraft:overworld", 0, 0, 0, 0,
                        Map.of("fire", -1.0), 1, false, false));
        Map<String, Double> tooMany = new LinkedHashMap<>();
        for (int index = 0; index < ManaRegionSnapshot.MAX_QUERY_ENTRIES + 1; index++) {
            tooMany.put("fire" + index, 1.0);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ManaRegionSnapshot("minecraft:overworld", 0, 0, 0, 0,
                        tooMany, 0, false, false));
    }
}
