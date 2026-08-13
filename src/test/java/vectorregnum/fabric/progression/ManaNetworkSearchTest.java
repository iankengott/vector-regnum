package vectorregnum.fabric.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManaNetworkSearchTest {
    @Test
    void rawNetworkAcceptsEightConduitsButNotNine() {
        Set<Integer> eightConduits = Set.of(1, 2, 3, 4, 5, 6, 7, 8);
        var found = ManaNetworkSearch.find(0, 8, 256,
                position -> List.of(position - 1, position + 1),
                eightConduits::contains, position -> position == 9);
        assertTrue(found.isPresent());
        assertEquals(8, found.orElseThrow().conduitDistance());

        Set<Integer> nineConduits = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertTrue(ManaNetworkSearch.find(0, 8, 256,
                position -> List.of(position - 1, position + 1),
                nineConduits::contains, position -> position == 10).isEmpty());
    }

    @Test
    void visitedBudgetStopsAdversarialGraph() {
        var found = ManaNetworkSearch.find(0, 64, 3,
                position -> List.of(position * 3 + 1, position * 3 + 2, position * 3 + 3),
                position -> true, position -> position == 100);
        assertTrue(found.isEmpty());
    }

    @Test
    void everyConduitTierReachesItsAdvertisedMaximum() {
        for (ManaTransportRules.ConduitTier tier : ManaTransportRules.ConduitTier.values()) {
            int source = tier.maximumDistance() + 1;
            var found = ManaNetworkSearch.find(0, tier.maximumDistance(), 512,
                    position -> List.of(position - 1, position + 1),
                    position -> position > 0 && position < source,
                    position -> position == source);
            assertEquals(tier.maximumDistance(), found.orElseThrow().conduitDistance());
        }
    }
}
