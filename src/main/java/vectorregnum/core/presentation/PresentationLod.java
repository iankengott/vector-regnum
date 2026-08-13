package vectorregnum.core.presentation;

/** Distance LOD that preserves truth cues while shedding secondary layers. */
public enum PresentationLod {
    NEAR(1.0, true, true),
    MID(0.62, true, false),
    FAR(0.30, false, false),
    TELEGRAPH_ONLY(0.12, false, false);

    public static final double MAXIMUM_DISTANCE = 96.0;

    private final double density;
    private final boolean expressiveLayers;
    private final boolean screenLayers;

    PresentationLod(double density, boolean expressiveLayers, boolean screenLayers) {
        this.density = density;
        this.expressiveLayers = expressiveLayers;
        this.screenLayers = screenLayers;
    }

    public double density() { return density; }
    public boolean expressiveLayers() { return expressiveLayers; }
    public boolean screenLayers() { return screenLayers; }

    public boolean renders(PresentationInstruction instruction) {
        if (instruction.truthLayer()) return true;
        if (!expressiveLayers) return false;
        return screenLayers || (instruction.cueKind() != PresentationCueKind.SCREEN
                && instruction.cueKind() != PresentationCueKind.CAMERA
                && instruction.cueKind() != PresentationCueKind.DARKNESS
                && instruction.cueKind() != PresentationCueKind.FOG);
    }

    public static PresentationLod select(double distance, PresentationQuality quality) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        PresentationLod distanceLod = distance <= 16.0 ? NEAR
                : distance <= 40.0 ? MID : distance <= MAXIMUM_DISTANCE ? FAR : TELEGRAPH_ONLY;
        if (quality == PresentationQuality.MINIMAL) return TELEGRAPH_ONLY;
        if (quality == PresentationQuality.BALANCED && distanceLod == NEAR) return MID;
        return distanceLod;
    }
}
