package vectorregnum.core.vm2;

/** Hard server-safety limits; per-tick exhaustion yields while lifetime limits fault. */
public record VmLimits(int maxStackDepth, int maxInstructionsPerTick,
        long maxTotalInstructions, long maxLifetimeTicks, int maxLoopIterations,
        double maxPerceptionRange, int maxSelectionResults,
        int maxVariables, int maxIterators, int maxIteratorSteps,
        int maxActiveBranches, int maxTotalBranches, int maxWatchers,
        int maxSignals, int maxOutputs, int maxOutputChars) {
    public static final VmLimits DEFAULT = new VmLimits(256, 128, 10_000, 1_200,
            1_024, 128.0, 128, 64, 16, 1_024, 8, 32, 32, 128, 64, 256);

    /** Backward-compatible constructor for the priorities 1-23 safety envelope. */
    public VmLimits(int maxStackDepth, int maxInstructionsPerTick,
            long maxTotalInstructions, long maxLifetimeTicks, int maxLoopIterations,
            double maxPerceptionRange, int maxSelectionResults) {
        this(maxStackDepth, maxInstructionsPerTick, maxTotalInstructions, maxLifetimeTicks,
                maxLoopIterations, maxPerceptionRange, maxSelectionResults,
                64, 16, 1_024, 8, 32, 32, 128, 64, 256);
    }

    public VmLimits {
        if (maxStackDepth < 1 || maxInstructionsPerTick < 1 || maxTotalInstructions < 1
                || maxLifetimeTicks < 1 || maxLoopIterations < 1 || maxSelectionResults < 1
                || !Double.isFinite(maxPerceptionRange) || maxPerceptionRange <= 0
                || maxVariables < 1 || maxIterators < 1 || maxIteratorSteps < 1
                || maxActiveBranches < 1 || maxTotalBranches < maxActiveBranches
                || maxWatchers < 1 || maxSignals < 1 || maxOutputs < 1
                || maxOutputChars < 1) {
            throw new IllegalArgumentException("all VM limits must be positive");
        }
    }
}
