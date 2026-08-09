package vectorregnum.core.circle;

import java.util.Comparator;
import java.util.List;

/** Canonical geometric traversal: north, clockwise around the outer ring, then inward. */
public final class CircleOrder {
    private static final Comparator<PlacedSigil> CLOCKWISE_THEN_INWARD = Comparator
            .comparingInt((PlacedSigil sigil) -> sigil.coordinate().ring())
            .thenComparingInt(sigil -> sigil.coordinate().clockwiseSlot());

    private CircleOrder() {
    }

    public static List<PlacedSigil> clockwiseThenInward(List<PlacedSigil> sigils) {
        return sigils.stream().sorted(CLOCKWISE_THEN_INWARD).toList();
    }
}
