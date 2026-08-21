package vectorregnum.core.presentation;

/**
 * Curated bounded motif vocabulary for authoritative world traces. Numeric only;
 * each backend resolves the style into its own concrete rendering, so the wire
 * format stays loader-neutral and never names a vanilla particle class.
 */
public enum PresentationParticleStyle {
    MOTES,
    CLOUD,
    SMOKE,
    LARGE_SMOKE,
    SPARK,
    END_ROD,
    TOTEM,
    WITCH,
    EXPLOSION,
    EXPLOSION_EMITTER;

    /** Bounds client-side synthesis; anything outside the curated list is discarded. */
    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length;
    }
}
