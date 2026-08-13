package vectorregnum.core.presentation;

/** Independent client-only presentation controls. Values never influence gameplay. */
public record PresentationAccessibility(PresentationQuality quality, double particleDensity,
        double darknessAndFog, double flashIntensity, double chromaticIntensity,
        double cameraMovement, double audioIntensity, boolean reducedMotion,
        boolean photosensitive) {
    public static final PresentationAccessibility DEFAULT = new PresentationAccessibility(
            PresentationQuality.FULL, 1.0, 1.0, 0.65, 0.35, 0.35, 1.0, false, false);

    public PresentationAccessibility {
        if (quality == null) throw new NullPointerException("quality");
        particleDensity = unit(particleDensity, "particleDensity");
        darknessAndFog = unit(darknessAndFog, "darknessAndFog");
        flashIntensity = unit(flashIntensity, "flashIntensity");
        chromaticIntensity = unit(chromaticIntensity, "chromaticIntensity");
        cameraMovement = unit(cameraMovement, "cameraMovement");
        audioIntensity = unit(audioIntensity, "audioIntensity");
        if (photosensitive) {
            flashIntensity = Math.min(flashIntensity, 0.15);
            chromaticIntensity = 0.0;
        }
        if (reducedMotion) cameraMovement = 0.0;
    }

    public double effectiveParticleDensity() {
        return particleDensity * quality.density() * (reducedMotion ? 0.55 : 1.0);
    }

    private static double unit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be 0..1");
        }
        return value;
    }
}
