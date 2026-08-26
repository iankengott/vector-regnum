package vectorregnum.core.presentation;

/** Curated sensory families retained as numeric IR parameters for renderer neutrality. */
public enum PresentationElement {
    ARCANE(0),
    FIRE(1),
    ICE(2),
    VOID(3),
    WATER(4),
    AIR(5),
    EARTH(6),
    LIGHTNING(7),
    TIME(8),
    SPACE(9),
    LIGHT(10),
    DARK(11),
    NATURE(12),
    SOUND(13);

    private final int code;

    PresentationElement(int code) { this.code = code; }

    public double parameter() {
        return code;
    }

    public static PresentationElement fromParameter(double value) {
        int index = (int) Math.clamp(Math.round(value), 0L, 13L);
        for (PresentationElement element : values()) {
            if (element.code == index) return element;
        }
        return ARCANE;
    }
}
