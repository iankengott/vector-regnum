package vectorregnum.neoforge.progression;

import vectorregnum.core.ElementalAffinityMatrix;

/** Pure rules for finite remote draws; suitable for both the current bridge and vm2. */
public final class ManaDrawRules {
    public static final int DEFAULT_BASE_DRAW = 100;
    public static final int CAPACITY_PER_SHARD = 100;

    private ManaDrawRules() {
    }

    public static int offeredMana(int baseDraw, double distance,
            ManaAffinity source, ManaAffinity requested) {
        if (baseDraw < 0 || !Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Draw and distance must be finite and non-negative");
        }
        double clampedDistance = Math.max(1.0, distance);
        double compatibility = compatibility(source, requested);
        return (int) Math.floor(baseDraw * compatibility / (clampedDistance * clampedDistance));
    }

    public static double compatibility(ManaAffinity source, ManaAffinity requested) {
        if (source == null || requested == null) {
            throw new IllegalArgumentException("Source and requested affinity are required");
        }
        return ElementalAffinityMatrix.canonical().efficiency(
                source.element(), requested.element());
    }

    public static int capacityAfterShard(int currentCapacity, int maximumCapacity) {
        if (currentCapacity < 0 || maximumCapacity < currentCapacity) {
            throw new IllegalArgumentException("Invalid capacity bounds");
        }
        return Math.min(maximumCapacity, currentCapacity + CAPACITY_PER_SHARD);
    }
}
