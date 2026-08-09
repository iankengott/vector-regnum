package vectorregnum.core.vm2;

/** Hard server-safety limits; per-tick exhaustion yields while lifetime limits fault. */
public record VmLimits(int maxStackDepth, int maxInstructionsPerTick,
        long maxTotalInstructions, long maxLifetimeTicks, int maxLoopIterations,
        double maxPerceptionRange, int maxSelectionResults) {
    public static final VmLimits DEFAULT = new VmLimits(256, 128, 10_000, 1_200,
            1_024, 128.0, 128);

    public VmLimits {
        if (maxStackDepth < 1 || maxInstructionsPerTick < 1 || maxTotalInstructions < 1
                || maxLifetimeTicks < 1 || maxLoopIterations < 1 || maxSelectionResults < 1
                || !Double.isFinite(maxPerceptionRange) || maxPerceptionRange <= 0) {
            throw new IllegalArgumentException("all VM limits must be positive");
        }
    }
}
