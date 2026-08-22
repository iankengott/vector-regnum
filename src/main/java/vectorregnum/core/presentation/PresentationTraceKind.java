package vectorregnum.core.presentation;

/**
 * Geometry families for compact authoritative world traces. Servers emit these
 * numeric events instead of particles; the receiving client renders them through
 * its active presentation backend (Quasar motifs under Veil, guarded vanilla
 * fallback particles otherwise).
 */
public enum PresentationTraceKind {
    /** Short-lived expanding puff at one point. */
    BURST,
    /** Gentle drifting motes around one point. */
    MOTES,
    /** Horizontal ring that grows into its radius. */
    RING,
    /** Straight segment from the primary point to the optional target point. */
    BEAM;

    private static final PresentationTraceKind[] VALUES = values();

    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length;
    }
}
