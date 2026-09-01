package vectorregnum.api.v1;

import java.util.Objects;

/** Four bounded multiplicative adjustments supplied by one integration. */
public record CastModifier(double manaFactor, double castingTimeFactor,
        double upkeepFactor, double instabilityFactor) {
    public static final double MIN_FACTOR = 0.5;
    public static final double MAX_FACTOR = 2.0;
    public static final CastModifier IDENTITY = new CastModifier(1.0, 1.0, 1.0, 1.0);

    public CastModifier {
        ApiValidation.factor(manaFactor, "manaFactor");
        ApiValidation.factor(castingTimeFactor, "castingTimeFactor");
        ApiValidation.factor(upkeepFactor, "upkeepFactor");
        ApiValidation.factor(instabilityFactor, "instabilityFactor");
    }

    public static CastModifier identity() {
        return IDENTITY;
    }

    /**
     * Multiplies two modifiers and clamps every aggregate dimension to the v1
     * interval.  Clamping makes composition deterministic even when several
     * optional companions contribute a discount or surcharge.
     */
    public CastModifier combine(CastModifier other) {
        Objects.requireNonNull(other, "other");
        return new CastModifier(
                ApiValidation.clampFactor(manaFactor * other.manaFactor),
                ApiValidation.clampFactor(castingTimeFactor * other.castingTimeFactor),
                ApiValidation.clampFactor(upkeepFactor * other.upkeepFactor),
                ApiValidation.clampFactor(instabilityFactor * other.instabilityFactor));
    }

    /** Applies this modifier to a quote, retaining finite non-negative values. */
    public CastParameters apply(CastParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        return new CastParameters(
                finiteProduct(parameters.mana(), manaFactor, "mana"),
                finiteProduct(parameters.castingTime(), castingTimeFactor, "castingTime"),
                finiteProduct(parameters.upkeep(), upkeepFactor, "upkeep"),
                finiteProduct(parameters.instability(), instabilityFactor, "instability"));
    }

    private static double finiteProduct(double value, double factor, String name) {
        double result = value * factor;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException(name + " modifier overflowed");
        }
        return result;
    }
}
