package vectorregnum.fabric.progression;

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
        if (source == requested) {
            return 1.0;
        }
        if (source == ManaAffinity.ARCANE || requested == ManaAffinity.ARCANE) {
            return 0.75;
        }
        if ((source == ManaAffinity.FIRE && requested == ManaAffinity.FROST)
                || (source == ManaAffinity.FROST && requested == ManaAffinity.FIRE)) {
            return 0.25;
        }
        if (source == ManaAffinity.VOID || requested == ManaAffinity.VOID) {
            return 0.4;
        }
        return 0.6;
    }

    public static int capacityAfterShard(int currentCapacity, int maximumCapacity) {
        if (currentCapacity < 0 || maximumCapacity < currentCapacity) {
            throw new IllegalArgumentException("Invalid capacity bounds");
        }
        return Math.min(maximumCapacity, currentCapacity + CAPACITY_PER_SHARD);
    }
}
