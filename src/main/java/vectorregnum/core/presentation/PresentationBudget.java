package vectorregnum.core.presentation;

/** Compile-time ceilings retained with a program for client-side runtime enforcement. */
public record PresentationBudget(int maximumCues, int maximumDurationTicks,
        PresentationCost maximumCost) {
    public static final PresentationBudget DEFAULT = new PresentationBudget(64, 1_200,
            new PresentationCost(32, 512, 8_192, 8, 16, 2, 64));

    public PresentationBudget {
        if (maximumCues < 1 || maximumDurationTicks < 1) {
            throw new IllegalArgumentException("presentation budget limits must be positive");
        }
        if (maximumCost == null) throw new NullPointerException("maximumCost");
    }

    public boolean allows(PresentationCost cost) {
        return cost.emitters() <= maximumCost.emitters()
                && cost.particlesPerSecond() <= maximumCost.particlesPerSecond()
                && cost.meshVertices() <= maximumCost.meshVertices()
                && cost.lights() <= maximumCost.lights()
                && cost.sounds() <= maximumCost.sounds()
                && cost.screenPasses() <= maximumCost.screenPasses()
                && cost.repetitions() <= maximumCost.repetitions();
    }
}
