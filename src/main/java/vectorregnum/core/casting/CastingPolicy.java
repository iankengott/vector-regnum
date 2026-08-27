package vectorregnum.core.casting;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-owned bounds and reagent potency used to produce a {@link CastQuote}.
 *
 * <p>Discounts are absolute cost-vector reductions per reagent unit. The
 * policy applies them in the stable {@link ReagentKind} declaration order,
 * first respecting the aggregate discount caps and then respecting the final
 * cost floors. This makes the quote deterministic and makes each contribution
 * auditable.</p>
 */
public final class CastingPolicy {
    /** Conservative defaults suitable for a fresh server. */
    public static final CastingPolicy DEFAULT = createCanonical();

    private final CastCost floors;
    private final CastCost discountCaps;
    private final int maxUnitsPerKind;
    private final int maxTotalUnits;
    private final int maxOfferingUnits;
    private final Map<ReagentKind, CastCost> discountPerUnit;

    /**
     * Creates a policy with explicit loadout and discount bounds.
     *
     * @param floors minimum final value for each cost dimension
     * @param discountCaps maximum aggregate reduction in each dimension
     * @param maxUnitsPerKind maximum optional reagent units of one kind
     * @param maxTotalUnits maximum optional reagent units across all kinds
     * @param maxOfferingUnits maximum ritual offering units
     * @param discountPerUnit absolute reduction contributed by one unit of each kind;
     *        omitted kinds contribute zero
     */
    public CastingPolicy(CastCost floors, CastCost discountCaps, int maxUnitsPerKind,
            int maxTotalUnits, int maxOfferingUnits,
            Map<ReagentKind, CastCost> discountPerUnit) {
        this.floors = Objects.requireNonNull(floors, "floors");
        this.discountCaps = Objects.requireNonNull(discountCaps, "discountCaps");
        if (maxUnitsPerKind < 0 || maxTotalUnits < 0 || maxOfferingUnits < 0) {
            throw new IllegalArgumentException("reagent and offering caps cannot be negative");
        }
        this.maxUnitsPerKind = maxUnitsPerKind;
        this.maxTotalUnits = maxTotalUnits;
        this.maxOfferingUnits = maxOfferingUnits;
        this.discountPerUnit = immutableDiscounts(discountPerUnit);
    }

    /** Convenience constructor where the total optional-reagent cap is per-kind × dimensions. */
    public CastingPolicy(CastCost floors, CastCost discountCaps, int maxUnitsPerKind,
            Map<ReagentKind, CastCost> discountPerUnit) {
        this(floors, discountCaps, maxUnitsPerKind,
                safeTotalCap(maxUnitsPerKind), maxUnitsPerKind, discountPerUnit);
    }

    /** Convenience constructor for policies that do not permit optional discounts. */
    public CastingPolicy(CastCost floors, CastCost discountCaps, int maxUnitsPerKind,
            int maxTotalUnits, int maxOfferingUnits) {
        this(floors, discountCaps, maxUnitsPerKind, maxTotalUnits, maxOfferingUnits, Map.of());
    }

    public CastCost floors() {
        return floors;
    }

    /** Alias for callers that describe floors as minimum final costs. */
    public CastCost minimumCosts() {
        return floors;
    }

    public CastCost discountCaps() {
        return discountCaps;
    }

    /** Alias for callers that describe caps as maximum discounts. */
    public CastCost maximumDiscounts() {
        return discountCaps;
    }

    public int maxUnitsPerKind() {
        return maxUnitsPerKind;
    }

    public int maxTotalUnits() {
        return maxTotalUnits;
    }

    public int maxOfferingUnits() {
        return maxOfferingUnits;
    }

    public Map<ReagentKind, CastCost> discountPerUnit() {
        return discountPerUnit;
    }

    public CastCost discountPerUnit(ReagentKind kind) {
        return discountPerUnit.get(Objects.requireNonNull(kind, "kind"));
    }

    /** Alias for adapter code that calls the values reagent contributions. */
    public CastCost reagentDiscount(ReagentKind kind) {
        return discountPerUnit(kind);
    }

    /**
     * Quotes a validated baseline and loadout under this server policy.
     *
     * @throws IllegalArgumentException if the loadout exceeds policy caps, a ritual
     *         has no offering, or the baseline is below a configured floor
     */
    public CastQuote quote(CastingMethod method, CastCost undiscounted,
            ReagentLoadout loadout) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(undiscounted, "undiscounted");
        Objects.requireNonNull(loadout, "loadout");
        loadout.validateAgainst(this);
        if (method.requiresOffering() && loadout.offeringUnits() == 0) {
            throw new IllegalArgumentException("" + method + " requires at least one offering unit");
        }
        if (!undiscounted.atLeast(floors)) {
            throw new IllegalArgumentException("undiscounted cost is below the configured floor");
        }

        CastCost current = undiscounted;
        CastCost appliedTotal = CastCost.ZERO;
        var contributions = new java.util.ArrayList<ReagentContribution>();
        for (ReagentKind kind : ReagentKind.values()) {
            int units = loadout.units(kind);
            if (units == 0) {
                continue;
            }
            CastCost requested = discountPerUnit(kind).times(units);
            CastCost remainingCap = discountCaps.subtractClamped(appliedTotal);
            CastCost headroom = current.subtractClamped(floors);
            CastCost applied = requested.min(remainingCap).min(headroom);
            contributions.add(new ReagentContribution(kind, units, requested, applied));
            appliedTotal = appliedTotal.plus(applied);
            current = current.subtractClamped(applied);
        }
        return new CastQuote(method, undiscounted, loadout, contributions, current);
    }

    /** Quotes a cast with no optional reagent or ritual offering. */
    public CastQuote quote(CastingMethod method, CastCost undiscounted) {
        return quote(method, undiscounted, ReagentLoadout.empty());
    }

    /** Returns the immutable canonical policy. */
    public static CastingPolicy canonical() {
        return DEFAULT;
    }

    /** Validates a loadout independently of a quote. */
    public void validate(ReagentLoadout loadout) {
        Objects.requireNonNull(loadout, "loadout").validateAgainst(this);
    }

    private static Map<ReagentKind, CastCost> immutableDiscounts(
            Map<ReagentKind, CastCost> discounts) {
        Objects.requireNonNull(discounts, "discountPerUnit");
        EnumMap<ReagentKind, CastCost> copy = new EnumMap<>(ReagentKind.class);
        for (Map.Entry<ReagentKind, CastCost> entry : discounts.entrySet()) {
            ReagentKind kind = entry.getKey();
            if (kind == null) {
                throw new IllegalArgumentException("discount kind cannot be null");
            }
            CastCost value = entry.getValue();
            if (value == null) {
                throw new IllegalArgumentException("discount for " + kind + " cannot be null");
            }
            copy.put(kind, value);
        }
        for (ReagentKind kind : ReagentKind.values()) {
            copy.putIfAbsent(kind, CastCost.ZERO);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static int safeTotalCap(int maxUnitsPerKind) {
        try {
            return Math.multiplyExact(maxUnitsPerKind, ReagentKind.values().length);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("reagent cap is too large", exception);
        }
    }

    private static CastingPolicy createCanonical() {
        return new CastingPolicy(
                new CastCost(1.0, 0.0, 0.0, 0.0),
                new CastCost(100.0, 100.0, 50.0, 1.0),
                8,
                16,
                64,
                Map.of(
                        ReagentKind.MANA, new CastCost(5.0, 0.0, 0.0, 0.0),
                        ReagentKind.CASTING_TIME, new CastCost(0.0, 2.0, 0.0, 0.0),
                        ReagentKind.UPKEEP, new CastCost(0.0, 0.0, 1.0, 0.0),
                        ReagentKind.INSTABILITY, new CastCost(0.0, 0.0, 0.0, 0.1)));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof CastingPolicy other)) return false;
        return maxUnitsPerKind == other.maxUnitsPerKind
                && maxTotalUnits == other.maxTotalUnits
                && maxOfferingUnits == other.maxOfferingUnits
                && floors.equals(other.floors)
                && discountCaps.equals(other.discountCaps)
                && discountPerUnit.equals(other.discountPerUnit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(floors, discountCaps, maxUnitsPerKind, maxTotalUnits,
                maxOfferingUnits, discountPerUnit);
    }

    @Override
    public String toString() {
        return "CastingPolicy[floors=" + floors + ", discountCaps=" + discountCaps
                + ", maxUnitsPerKind=" + maxUnitsPerKind + ", maxTotalUnits="
                + maxTotalUnits + ", maxOfferingUnits=" + maxOfferingUnits
                + ", discountPerUnit=" + discountPerUnit + "]";
    }
}
