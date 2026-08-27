package vectorregnum.core.casting;

import java.util.Objects;

/** One auditable reagent entry in a {@link CastQuote}. */
public record ReagentContribution(ReagentKind kind, int units,
        CastCost requestedDiscount, CastCost appliedDiscount) {
    static final double EPSILON = 1.0e-9;

    public ReagentContribution {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requestedDiscount, "requestedDiscount");
        Objects.requireNonNull(appliedDiscount, "appliedDiscount");
        if (units < 1) {
            throw new IllegalArgumentException("contribution units must be positive");
        }
        if (!atMostWithTolerance(appliedDiscount, requestedDiscount)) {
            throw new IllegalArgumentException("applied discount cannot exceed requested discount");
        }
    }

    /** The amount actually reducing the final quote. */
    public CastCost discount() {
        return appliedDiscount;
    }

    /** Whether the policy had to clip this contribution. */
    public boolean wasCapped() {
        return !approximatelyEqual(requestedDiscount, appliedDiscount);
    }

    static boolean approximatelyEqual(CastCost left, CastCost right) {
        return Math.abs(left.mana() - right.mana()) <= EPSILON
                && Math.abs(left.castingTime() - right.castingTime()) <= EPSILON
                && Math.abs(left.upkeep() - right.upkeep()) <= EPSILON
                && Math.abs(left.instability() - right.instability()) <= EPSILON;
    }

    private static boolean atMostWithTolerance(CastCost value, CastCost limit) {
        return value.mana() <= limit.mana() + EPSILON
                && value.castingTime() <= limit.castingTime() + EPSILON
                && value.upkeep() <= limit.upkeep() + EPSILON
                && value.instability() <= limit.instability() + EPSILON;
    }
}
