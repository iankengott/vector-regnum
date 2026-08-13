package vectorregnum.core.presentation;

/** Curated sensory families retained as numeric IR parameters for renderer neutrality. */
public enum PresentationElement {
    ARCANE,
    FIRE,
    FROST,
    VOID;

    public double parameter() {
        return ordinal();
    }

    public static PresentationElement fromParameter(double value) {
        int index = (int) Math.round(value);
        return values()[Math.clamp(index, 0, values().length - 1)];
    }
}
