package vectorregnum.core;

import java.util.Objects;
import java.util.Optional;

/** Immutable final successful spell state. */
public record SpellSnapshot(
        Vec3 origin,
        Optional<Vec3> direction,
        Optional<Element> element,
        Shape shape,
        double radius,
        double magnitude) {

    public SpellSnapshot {
        Objects.requireNonNull(origin, "origin");
        direction = Objects.requireNonNull(direction, "direction");
        element = Objects.requireNonNull(element, "element");
        Objects.requireNonNull(shape, "shape");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
        if (!Double.isFinite(magnitude) || magnitude <= 0.0) {
            throw new IllegalArgumentException("magnitude must be finite and positive");
        }
    }
}
