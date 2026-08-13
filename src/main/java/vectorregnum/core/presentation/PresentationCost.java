package vectorregnum.core.presentation;

/** Conservative resource declaration for one cue or a whole program. */
public record PresentationCost(int emitters, int particlesPerSecond, int meshVertices,
        int lights, int sounds, int screenPasses, int repetitions) {
    public static final PresentationCost ZERO = new PresentationCost(0, 0, 0, 0, 0, 0, 0);

    public PresentationCost {
        if (emitters < 0 || particlesPerSecond < 0 || meshVertices < 0 || lights < 0
                || sounds < 0 || screenPasses < 0 || repetitions < 0) {
            throw new IllegalArgumentException("presentation costs cannot be negative");
        }
    }

    public PresentationCost plus(PresentationCost other) {
        return new PresentationCost(Math.addExact(emitters, other.emitters),
                Math.addExact(particlesPerSecond, other.particlesPerSecond),
                Math.addExact(meshVertices, other.meshVertices), Math.addExact(lights, other.lights),
                Math.addExact(sounds, other.sounds), Math.addExact(screenPasses, other.screenPasses),
                Math.addExact(repetitions, other.repetitions));
    }
}
