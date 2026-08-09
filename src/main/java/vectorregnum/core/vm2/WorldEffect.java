package vectorregnum.core.vm2;

import java.util.List;
import java.util.Objects;

/** Validated loader-neutral mutations for a Fabric adapter to apply server-side. */
public sealed interface WorldEffect permits WorldEffect.Impulse, WorldEffect.Acceleration,
        WorldEffect.Damping, WorldEffect.FollowPath, WorldEffect.MoveToward,
        WorldEffect.KeepDistance {
    String entityId();
    int durationTicks();

    record Impulse(String entityId, Vector3 impulse, int durationTicks) implements WorldEffect {
        public Impulse { validate(entityId, durationTicks); Objects.requireNonNull(impulse, "impulse"); }
    }
    record Acceleration(String entityId, Vector3 acceleration, int durationTicks) implements WorldEffect {
        public Acceleration { validate(entityId, durationTicks); Objects.requireNonNull(acceleration, "acceleration"); }
    }
    record Damping(String entityId, double factor, int durationTicks) implements WorldEffect {
        public Damping {
            validate(entityId, durationTicks);
            if (!Double.isFinite(factor) || factor < 0 || factor > 1) {
                throw new IllegalArgumentException("damping factor must be 0..1");
            }
        }
    }
    record FollowPath(String entityId, List<Vector3> points, double speed,
            int durationTicks) implements WorldEffect {
        public FollowPath {
            validate(entityId, durationTicks);
            points = List.copyOf(Objects.requireNonNull(points, "points"));
            if (points.isEmpty()) throw new IllegalArgumentException("path cannot be empty");
            if (!Double.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("speed must be positive");
        }
    }
    record MoveToward(String entityId, Vector3 point, double speed,
            int durationTicks) implements WorldEffect {
        public MoveToward {
            validate(entityId, durationTicks); Objects.requireNonNull(point, "point");
            if (!Double.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("speed must be positive");
        }
    }
    record KeepDistance(String entityId, String targetId, double distance,
            int durationTicks) implements WorldEffect {
        public KeepDistance {
            validate(entityId, durationTicks); Objects.requireNonNull(targetId, "targetId");
            if (targetId.isBlank()) throw new IllegalArgumentException("target cannot be blank");
            if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("distance must be non-negative");
        }
    }

    private static void validate(String id, int duration) {
        Objects.requireNonNull(id, "entityId");
        if (id.isBlank()) throw new IllegalArgumentException("entityId cannot be blank");
        if (duration < 1) throw new IllegalArgumentException("duration must be at least one tick");
    }
}
