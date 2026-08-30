package vectorregnum.core.security;

import java.util.Objects;

/** Immutable facts supplied by the authoritative adapter for one mechanic use. */
public record MechanicRequest(MechanicCapability capability, double range, int durationTicks,
        int targetCount, boolean sameDimension, boolean sourceLoaded, boolean targetLoaded,
        boolean permissionAllowed, boolean pvpAllowed, boolean friendlyFireAllowed,
        boolean deterministic) {
    public MechanicRequest {
        Objects.requireNonNull(capability, "capability");
        if (!Double.isFinite(range) || range < 0.0) {
            throw new IllegalArgumentException("range must be finite and non-negative");
        }
        if (durationTicks < 1 || durationTicks > MechanicLimits.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("duration exceeds mechanic bound");
        }
        if (targetCount < 0 || targetCount > MechanicLimits.MAX_TARGETS) {
            throw new IllegalArgumentException("target count exceeds mechanic bound");
        }
    }
}
