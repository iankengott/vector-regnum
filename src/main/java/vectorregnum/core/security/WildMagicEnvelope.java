package vectorregnum.core.security;

import java.util.Objects;
import vectorregnum.core.WildMagicCategory;

/** Deterministic bounded fallout values for a Wild Magic category. */
public record WildMagicEnvelope(WildMagicCategory category, double radius, double magnitude,
        int durationTicks, int targetLimit, long variationSeed) {
    public WildMagicEnvelope {
        Objects.requireNonNull(category, "category");
        if (!Double.isFinite(radius) || radius < 0.0 || radius > MechanicLimits.MAX_RANGE
                || !Double.isFinite(magnitude) || magnitude <= 0.0 || magnitude > 20.0
                || durationTicks < 1 || durationTicks > MechanicLimits.MAX_DURATION_TICKS
                || targetLimit < 1 || targetLimit > MechanicLimits.MAX_TARGETS) {
            throw new IllegalArgumentException("Wild Magic envelope exceeds bounds");
        }
    }
}
