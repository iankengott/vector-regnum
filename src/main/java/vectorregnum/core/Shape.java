package vectorregnum.core;

import java.util.Locale;
import java.util.Optional;

/** Shapes currently supported by the compatibility language. */
public enum Shape {
    PROJECTILE,
    AURA;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Shape> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(id.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
