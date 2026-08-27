package vectorregnum.core.casting;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable optional-reagent selection for one cast.
 *
 * <p>The four typed entries are the discount-bearing reagents. Ritual offerings
 * are tracked separately because they are committed to a ritual but are not a
 * discount dimension. Use one of the factories taking a {@link CastingPolicy}
 * so an adapter cannot construct an over-cap reservation.</p>
 */
public final class ReagentLoadout {
    private static final ReagentLoadout EMPTY = new ReagentLoadout(new EnumMap<>(ReagentKind.class), 0);

    private final Map<ReagentKind, Integer> units;
    private final int offeringUnits;
    private final int totalUnits;

    private ReagentLoadout(Map<ReagentKind, Integer> units, int offeringUnits) {
        EnumMap<ReagentKind, Integer> copy = new EnumMap<>(ReagentKind.class);
        int total = 0;
        for (Map.Entry<ReagentKind, Integer> entry : units.entrySet()) {
            ReagentKind kind = Objects.requireNonNull(entry.getKey(), "reagent kind");
            Integer value = Objects.requireNonNull(entry.getValue(), "units for " + kind);
            if (value < 0) {
                throw new IllegalArgumentException("units for " + kind + " cannot be negative");
            }
            if (value == 0) {
                continue;
            }
            try {
                total = Math.addExact(total, value);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("reagent unit count overflow", exception);
            }
            copy.put(kind, value);
        }
        if (offeringUnits < 0) {
            throw new IllegalArgumentException("offering units cannot be negative");
        }
        this.units = Collections.unmodifiableMap(copy);
        this.offeringUnits = offeringUnits;
        this.totalUnits = total;
    }

    /** An empty optional-reagent and offering selection. */
    public static ReagentLoadout empty() {
        return EMPTY;
    }

    /** Creates an optional-reagent loadout with no ritual offering. */
    public static ReagentLoadout of(Map<ReagentKind, Integer> units,
            CastingPolicy policy) {
        return of(units, 0, policy);
    }

    /** Creates a loadout and rejects any count beyond the server policy caps. */
    public static ReagentLoadout of(Map<ReagentKind, Integer> units, int offeringUnits,
            CastingPolicy policy) {
        Objects.requireNonNull(units, "units");
        ReagentLoadout loadout = new ReagentLoadout(units, offeringUnits);
        loadout.validateAgainst(Objects.requireNonNull(policy, "policy"));
        return loadout;
    }

    /** Convenience factory for one typed reagent kind. */
    public static ReagentLoadout of(ReagentKind kind, int units, CastingPolicy policy) {
        return of(kind, units, 0, policy);
    }

    /** Convenience factory for one typed reagent kind and an offering pool. */
    public static ReagentLoadout of(ReagentKind kind, int units, int offeringUnits,
            CastingPolicy policy) {
        return of(Map.of(Objects.requireNonNull(kind, "kind"), units), offeringUnits, policy);
    }

    /** Returns a loadout with one kind replaced, preserving immutability. */
    public ReagentLoadout with(ReagentKind kind, int newUnits, CastingPolicy policy) {
        Objects.requireNonNull(kind, "kind");
        EnumMap<ReagentKind, Integer> updated = new EnumMap<>(ReagentKind.class);
        updated.putAll(units);
        updated.put(kind, newUnits);
        return of(updated, offeringUnits, policy);
    }

    /** Returns a loadout with a replaced ritual offering count. */
    public ReagentLoadout withOfferingUnits(int newOfferingUnits, CastingPolicy policy) {
        return of(units, newOfferingUnits, policy);
    }

    /** Immutable typed reagent counts, in enum declaration order. */
    public Map<ReagentKind, Integer> units() {
        return units;
    }

    public int units(ReagentKind kind) {
        return units.getOrDefault(Objects.requireNonNull(kind, "kind"), 0);
    }

    public int totalUnits() {
        return totalUnits;
    }

    public int offeringUnits() {
        return offeringUnits;
    }

    public boolean hasOffering() {
        return offeringUnits > 0;
    }

    public boolean isEmpty() {
        return totalUnits == 0 && offeringUnits == 0;
    }

    /** Re-checks caps when a loadout is used with a different server policy. */
    public void validateAgainst(CastingPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (offeringUnits > policy.maxOfferingUnits()) {
            throw new IllegalArgumentException("offering units exceed the configured cap");
        }
        if (totalUnits > policy.maxTotalUnits()) {
            throw new IllegalArgumentException("reagent units exceed the configured total cap");
        }
        for (ReagentKind kind : ReagentKind.values()) {
            if (units(kind) > policy.maxUnitsPerKind()) {
                throw new IllegalArgumentException("reagent units for " + kind
                        + " exceed the configured per-kind cap");
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ReagentLoadout other)) return false;
        return offeringUnits == other.offeringUnits && units.equals(other.units);
    }

    @Override
    public int hashCode() {
        return Objects.hash(units, offeringUnits);
    }

    @Override
    public String toString() {
        return "ReagentLoadout[units=" + units + ", offeringUnits=" + offeringUnits + "]";
    }
}
