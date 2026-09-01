package vectorregnum.api.v1;

import java.util.Objects;
import java.util.UUID;

/** Immutable, bounded intent for a combat integration to request disruption. */
public record DisruptionRequest(UUID attackerId, UUID targetId, String sourceId,
        boolean stanceReady, boolean weaponReady, long timingWindowTicks) {
    /** Shared priority-26 disruption bound. */
    public static final int MAX_TIMING_WINDOW_TICKS = 10;

    public DisruptionRequest {
        Objects.requireNonNull(attackerId, "attackerId");
        Objects.requireNonNull(targetId, "targetId");
        sourceId = ApiValidation.sourceId(sourceId);
        if (timingWindowTicks < 0L || timingWindowTicks > MAX_TIMING_WINDOW_TICKS) {
            throw new IllegalArgumentException("timingWindowTicks must be between 0 and "
                    + MAX_TIMING_WINDOW_TICKS);
        }
    }
}
