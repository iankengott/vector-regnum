package vectorregnum.api.v1;

/** Immutable, pre-modifier dimensions of one quoted cast. */
public record CastParameters(double mana, double castingTime, double upkeep, double instability) {
    public CastParameters {
        ApiValidation.nonNegative(mana, "mana");
        ApiValidation.nonNegative(castingTime, "castingTime");
        ApiValidation.nonNegative(upkeep, "upkeep");
        ApiValidation.nonNegative(instability, "instability");
    }
}
