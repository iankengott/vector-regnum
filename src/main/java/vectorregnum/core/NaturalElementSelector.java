package vectorregnum.core;

import java.util.Objects;
import java.util.UUID;

/** Deterministic natural-element assignment with a bounded rare-Void outcome. */
public final class NaturalElementSelector {
    public static final int VOID_DENOMINATOR = 64;

    private NaturalElementSelector() { }

    public static Element select(UUID identity) {
        Objects.requireNonNull(identity, "identity");
        int rareBucket = (int) (identity.getLeastSignificantBits() & (VOID_DENOMINATOR - 1L));
        if (rareBucket == 0) return Element.VOID;
        int ordinaryIndex = (int) Long.remainderUnsigned(
                identity.getMostSignificantBits() ^ Long.rotateLeft(identity.getLeastSignificantBits(), 17),
                Element.ordinary().size());
        return Element.ordinary().get(ordinaryIndex);
    }
}
