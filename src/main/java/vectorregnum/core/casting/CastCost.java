package vectorregnum.core.casting;

import java.util.Objects;

/**
 * The four cost dimensions that are quoted before a spell is admitted.
 *
 * <p>The values are deliberately loader-neutral. A NeoForge adapter can map
 * {@code mana} to the player's mana pool and the other dimensions to VM
 * duration, upkeep, and fault-risk calculations without making this contract
 * depend on Minecraft classes.</p>
 */
public record CastCost(double mana, double castingTime, double upkeep, double instability) {
    /** A zero cost in every dimension. */
    public static final CastCost ZERO = new CastCost(0.0, 0.0, 0.0, 0.0);

    public CastCost {
        finiteNonNegative(mana, "mana");
        finiteNonNegative(castingTime, "castingTime");
        finiteNonNegative(upkeep, "upkeep");
        finiteNonNegative(instability, "instability");
    }

    /** Adds two cost vectors, rejecting floating-point overflow. */
    public CastCost plus(CastCost other) {
        Objects.requireNonNull(other, "other");
        return new CastCost(safeAdd(mana, other.mana), safeAdd(castingTime, other.castingTime),
                safeAdd(upkeep, other.upkeep), safeAdd(instability, other.instability));
    }

    /** Multiplies every dimension by a finite non-negative scalar. */
    public CastCost times(double multiplier) {
        finiteNonNegative(multiplier, "multiplier");
        return new CastCost(safeMultiply(mana, multiplier), safeMultiply(castingTime, multiplier),
                safeMultiply(upkeep, multiplier), safeMultiply(instability, multiplier));
    }

    /** Multiplies every dimension by a non-negative reagent count. */
    public CastCost times(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("multiplier cannot be negative");
        }
        return times((double) multiplier);
    }

    /** Subtracts a vector, clamping each dimension at zero. */
    public CastCost subtractClamped(CastCost other) {
        Objects.requireNonNull(other, "other");
        return new CastCost(Math.max(0.0, mana - other.mana),
                Math.max(0.0, castingTime - other.castingTime),
                Math.max(0.0, upkeep - other.upkeep),
                Math.max(0.0, instability - other.instability));
    }

    /** Component-wise minimum. */
    public CastCost min(CastCost other) {
        Objects.requireNonNull(other, "other");
        return new CastCost(Math.min(mana, other.mana), Math.min(castingTime, other.castingTime),
                Math.min(upkeep, other.upkeep), Math.min(instability, other.instability));
    }

    /** Component-wise maximum. */
    public CastCost max(CastCost other) {
        Objects.requireNonNull(other, "other");
        return new CastCost(Math.max(mana, other.mana), Math.max(castingTime, other.castingTime),
                Math.max(upkeep, other.upkeep), Math.max(instability, other.instability));
    }

    /** Returns the dimension associated with one reagent kind. */
    public double value(ReagentKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case MANA -> mana;
            case CASTING_TIME -> castingTime;
            case UPKEEP -> upkeep;
            case INSTABILITY -> instability;
        };
    }

    /** Creates a vector containing one dimension and zero in the others. */
    public static CastCost forKind(ReagentKind kind, double value) {
        finiteNonNegative(value, "value");
        return switch (Objects.requireNonNull(kind, "kind")) {
            case MANA -> new CastCost(value, 0.0, 0.0, 0.0);
            case CASTING_TIME -> new CastCost(0.0, value, 0.0, 0.0);
            case UPKEEP -> new CastCost(0.0, 0.0, value, 0.0);
            case INSTABILITY -> new CastCost(0.0, 0.0, 0.0, value);
        };
    }

    /** Whether every component is no greater than {@code other}. */
    public boolean atMost(CastCost other) {
        Objects.requireNonNull(other, "other");
        return mana <= other.mana && castingTime <= other.castingTime
                && upkeep <= other.upkeep && instability <= other.instability;
    }

    /** Whether every component is no less than {@code other}. */
    public boolean atLeast(CastCost other) {
        Objects.requireNonNull(other, "other");
        return mana >= other.mana && castingTime >= other.castingTime
                && upkeep >= other.upkeep && instability >= other.instability;
    }

    private static double safeAdd(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("cost overflow");
        }
        return result;
    }

    private static double safeMultiply(double value, double multiplier) {
        double result = value * multiplier;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("cost overflow");
        }
        return result;
    }

    private static void finiteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
