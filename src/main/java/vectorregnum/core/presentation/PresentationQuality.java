package vectorregnum.core.presentation;

/** User-selected detail ceiling; mandatory telegraphs are never removed. */
public enum PresentationQuality {
    MINIMAL(0.25),
    BALANCED(0.60),
    FULL(1.0);

    private final double density;

    PresentationQuality(double density) {
        this.density = density;
    }

    public double density() {
        return density;
    }
}
