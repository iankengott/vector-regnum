package vectorregnum.core.casting;

/**
 * The six distinct ways a spell can enter the casting pipeline.
 *
 * <p>Portable media is intentionally represented separately by the existing
 * circle {@code SpellMedium} type. This enum describes the casting contract,
 * so a future adapter can use the same escrow rules for a bare cast, a
 * ritual, or an installed circle.</p>
 */
public enum CastingMethod {
    /** An immediate cast made directly by the caster. */
    BARE(false, false, false, false),
    /** A cast backed by an explicitly approved offering pool. */
    RITUAL(true, false, false, false),
    /** Construction of a persistent, installed circle. */
    ENGRAVING(false, false, false, true),
    /** A reusable portable spellbook activation. */
    SPELLBOOK(false, true, false, false),
    /** A portable activation that is consumed at terminal settlement. */
    SCROLL(false, true, true, false),
    /** Activation of a circle already installed at a world anchor. */
    INSTALLED_CIRCLE(false, false, false, true);

    private final boolean offeringRequired;
    private final boolean portable;
    private final boolean singleUse;
    private final boolean installationRequired;

    CastingMethod(boolean offeringRequired, boolean portable, boolean singleUse,
            boolean installationRequired) {
        this.offeringRequired = offeringRequired;
        this.portable = portable;
        this.singleUse = singleUse;
        this.installationRequired = installationRequired;
    }

    /** Whether an approved non-empty ritual offering is required. */
    public boolean offeringRequired() {
        return offeringRequired;
    }

    /** Alias that reads naturally at call sites validating a ritual request. */
    public boolean requiresOffering() {
        return offeringRequired;
    }

    /** Whether the method travels with the caster as portable media. */
    public boolean portable() {
        return portable;
    }

    /** Alias for adapters that use JavaBean-style predicates. */
    public boolean isPortable() {
        return portable;
    }

    /** Whether the portable representation is consumed after settlement. */
    public boolean singleUse() {
        return singleUse;
    }

    /** Whether the method requires or produces a world-installed anchor. */
    public boolean installationRequired() {
        return installationRequired;
    }

    /** Alias for adapters that use JavaBean-style predicates. */
    public boolean requiresInstallation() {
        return installationRequired;
    }

    public String stableId() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String stableLabel() {
        return stableId().replace('_', ' ');
    }

    /** Exact pre-reagent wind-up used by the server for this casting method. */
    public double baseCastingTicks(int instructionCount) {
        if (instructionCount < 0) {
            throw new IllegalArgumentException("instruction count cannot be negative");
        }
        double methodTicks = switch (this) {
            case BARE -> 20.0;
            case RITUAL -> 60.0;
            case ENGRAVING -> 30.0;
            case SPELLBOOK -> 16.0;
            case SCROLL -> 12.0;
            case INSTALLED_CIRCLE -> 8.0;
        };
        return methodTicks + Math.min(100.0, instructionCount * 0.5);
    }
}
