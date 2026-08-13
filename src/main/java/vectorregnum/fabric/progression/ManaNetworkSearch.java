package vectorregnum.fabric.progression;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Bounded breadth-first search shared by the server adapter and pure tests. */
public final class ManaNetworkSearch {
    private ManaNetworkSearch() {
    }

    public static <P> Optional<Found<P>> find(P origin, int maximumConduits, int maximumVisited,
            Function<P, Iterable<P>> neighbors, Predicate<P> isConduit, Predicate<P> isSource) {
        if (origin == null || neighbors == null || isConduit == null || isSource == null
                || maximumConduits < 0 || maximumVisited <= 0) {
            throw new IllegalArgumentException("Invalid mana network search bounds");
        }
        ArrayDeque<Step<P>> pending = new ArrayDeque<>();
        Set<P> visited = new HashSet<>();
        visited.add(origin);
        pending.addLast(new Step<>(origin, 0));

        while (!pending.isEmpty() && visited.size() < maximumVisited) {
            Step<P> current = pending.removeFirst();
            for (P neighbor : neighbors.apply(current.position())) {
                if (neighbor == null || !visited.add(neighbor)) {
                    continue;
                }
                if (isSource.test(neighbor)) {
                    return Optional.of(new Found<>(neighbor, current.conduitDistance(), visited.size()));
                }
                int nextDistance = current.conduitDistance() + 1;
                if (nextDistance <= maximumConduits && isConduit.test(neighbor)) {
                    pending.addLast(new Step<>(neighbor, nextDistance));
                }
                if (visited.size() >= maximumVisited) {
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private record Step<P>(P position, int conduitDistance) {
    }

    public record Found<P>(P position, int conduitDistance, int visited) {
        public Found {
            if (position == null || conduitDistance < 0 || visited <= 0) {
                throw new IllegalArgumentException("Invalid mana network result");
            }
        }
    }
}
