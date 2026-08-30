package vectorregnum.core.security;

/** Shared hard ceilings for recovered mechanics. */
public final class MechanicLimits {
    public static final double MAX_RANGE = 32.0;
    public static final int MAX_DURATION_TICKS = 1_200;
    public static final int MAX_TARGETS = 16;
    public static final int MAX_ATTENTION_DURATION_TICKS = 40;
    public static final double MAX_ATTENTION_ANGLE_DEGREES = 45.0;
    public static final double MAX_ATTENTION_STEP_DEGREES = 3.0;
    public static final int MAX_DISRUPTION_WINDOW_TICKS = 10;

    private MechanicLimits() { }
}
