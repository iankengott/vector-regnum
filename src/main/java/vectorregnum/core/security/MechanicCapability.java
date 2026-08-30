package vectorregnum.core.security;

/** Curated recovered mechanics; authored data can name these capabilities only. */
public enum MechanicCapability {
    /** Visual output with no gameplay consequence. */
    RENDER_ONLY(false),
    /** A bounded attempt to interrupt one active spell. */
    SPELL_DISRUPTION(true),
    /** A bounded server-authoritative attention adjustment. */
    FORCED_ATTENTION(true),
    /** Deterministic bounded fallout after a spell fault. */
    WILD_MAGIC(true);

    private final boolean gameplay;

    MechanicCapability(boolean gameplay) { this.gameplay = gameplay; }

    public boolean gameplay() { return gameplay; }
}
