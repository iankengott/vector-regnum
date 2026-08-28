package vectorregnum.core.casting;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, reviewable quote for one casting request.
 *
 * <p>A quote keeps the undiscounted four-dimensional baseline, every selected
 * reagent's requested and actually applied discount, and the final bounded
 * values. Keeping both discount values lets a UI explain caps and floors
 * instead of displaying only a silently changed total.</p>
 */
public final class CastQuote {
    private final CastingMethod method;
    private final CastCost undiscounted;
    private final ReagentLoadout loadout;
    private final List<ReagentContribution> contributions;
    private final CastCost finalCost;

    public CastQuote(CastingMethod method, CastCost undiscounted, ReagentLoadout loadout,
            List<ReagentContribution> contributions, CastCost finalCost) {
        this.method = Objects.requireNonNull(method, "method");
        this.undiscounted = Objects.requireNonNull(undiscounted, "undiscounted");
        this.loadout = Objects.requireNonNull(loadout, "loadout");
        Objects.requireNonNull(contributions, "contributions");
        this.contributions = List.copyOf(contributions);
        this.finalCost = Objects.requireNonNull(finalCost, "finalCost");
        validate();
    }

    public CastingMethod method() {
        return method;
    }

    /** The cost before any optional reagent is applied. */
    public CastCost undiscounted() {
        return undiscounted;
    }

    /** Alias emphasizing that this is the quote's baseline vector. */
    public CastCost baseline() {
        return undiscounted;
    }

    public ReagentLoadout loadout() {
        return loadout;
    }

    /** Every non-empty selected reagent kind, in deterministic kind order. */
    public List<ReagentContribution> contributions() {
        return contributions;
    }

    /** Alias for UI and adapter code that calls entries applied reagents. */
    public List<ReagentContribution> reagentContributions() {
        return contributions;
    }

    /** The final cost after caps and floors have been applied. */
    public CastCost finalCost() {
        return finalCost;
    }

    /** Alias for callers that describe the result as bounded values. */
    public CastCost bounded() {
        return finalCost;
    }

    public boolean hasDiscount() {
        return !ReagentContribution.approximatelyEqual(undiscounted, finalCost);
    }

    public CastCost totalAppliedDiscount() {
        return undiscounted.subtractClamped(finalCost);
    }

    private void validate() {
        if (!finalCost.atMost(undiscounted)
                && !ReagentContribution.approximatelyEqual(finalCost, undiscounted)) {
            throw new IllegalArgumentException("final cost cannot exceed undiscounted cost");
        }

        Set<ReagentKind> seen = new HashSet<>();
        CastCost appliedTotal = CastCost.ZERO;
        for (ReagentContribution contribution : contributions) {
            if (!seen.add(contribution.kind())) {
                throw new IllegalArgumentException("duplicate contribution for " + contribution.kind());
            }
            if (loadout.units(contribution.kind()) != contribution.units()) {
                throw new IllegalArgumentException("contribution units do not match loadout for "
                        + contribution.kind());
            }
            appliedTotal = appliedTotal.plus(contribution.appliedDiscount());
        }
        for (ReagentKind kind : ReagentKind.values()) {
            boolean represented = seen.contains(kind);
            if (represented != (loadout.units(kind) > 0)) {
                throw new IllegalArgumentException("loadout and contributions disagree for " + kind);
            }
        }
        CastCost expectedReduction = undiscounted.subtractClamped(finalCost);
        if (!ReagentContribution.approximatelyEqual(expectedReduction, appliedTotal)) {
            throw new IllegalArgumentException("contributions do not produce the final quote");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof CastQuote other)) return false;
        return method == other.method && undiscounted.equals(other.undiscounted)
                && loadout.equals(other.loadout) && contributions.equals(other.contributions)
                && finalCost.equals(other.finalCost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, undiscounted, loadout, contributions, finalCost);
    }

    @Override
    public String toString() {
        return "CastQuote[method=" + method + ", undiscounted=" + undiscounted
                + ", contributions=" + contributions + ", finalCost=" + finalCost + "]";
    }
}
