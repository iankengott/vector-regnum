package vectorregnum.core.presentation;

import java.util.Locale;

/** Small versioned text codec for client sensory preferences. */
public final class PresentationAccessibilityCodec {
    private static final int VERSION = 1;

    private PresentationAccessibilityCodec() { }

    public static String encode(PresentationAccessibility value) {
        if (value == null) throw new NullPointerException("value");
        return String.format(Locale.ROOT, "%d|%s|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f|%s|%s",
                VERSION, value.quality().name(), value.particleDensity(), value.darknessAndFog(),
                value.flashIntensity(), value.chromaticIntensity(), value.cameraMovement(),
                value.audioIntensity(), value.reducedMotion(), value.photosensitive());
    }

    public static PresentationAccessibility decode(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("settings cannot be null");
        String[] fields = encoded.trim().split("\\|", -1);
        if (fields.length != 10 || !Integer.toString(VERSION).equals(fields[0])) {
            throw new IllegalArgumentException("unsupported accessibility settings version");
        }
        try {
            return new PresentationAccessibility(PresentationQuality.valueOf(fields[1]),
                    Double.parseDouble(fields[2]), Double.parseDouble(fields[3]),
                    Double.parseDouble(fields[4]), Double.parseDouble(fields[5]),
                    Double.parseDouble(fields[6]), Double.parseDouble(fields[7]),
                    Boolean.parseBoolean(fields[8]), Boolean.parseBoolean(fields[9]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid accessibility settings", exception);
        }
    }
}
