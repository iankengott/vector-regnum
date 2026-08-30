package vectorregnum.core;

import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.security.WildMagicEnvelope;
import vectorregnum.core.security.WildMagicResolver;

/** Loader-neutral commands for the Fabric adapter to apply server-side. */
public sealed interface EffectCommand
        permits EffectCommand.Projectile, EffectCommand.Aura, EffectCommand.WildMagic {

    record Projectile(
            String casterId,
            Vec3 origin,
            Vec3 direction,
            Optional<Element> element,
            double radius,
            double magnitude) implements EffectCommand {
        public Projectile {
            requireCasterAndOrigin(casterId, origin);
            Objects.requireNonNull(direction, "direction");
            element = Objects.requireNonNull(element, "element");
            if (direction.isEffectivelyZero()) {
                throw new IllegalArgumentException("Projectile direction cannot be zero");
            }
            validateEffectNumbers(radius, magnitude);
        }
    }

    record Aura(
            String casterId,
            Vec3 origin,
            Optional<Element> element,
            double radius,
            double magnitude) implements EffectCommand {
        public Aura {
            requireCasterAndOrigin(casterId, origin);
            element = Objects.requireNonNull(element, "element");
            validateEffectNumbers(radius, magnitude);
        }
    }

    record WildMagic(
            String casterId,
            WildMagicCategory category,
            Optional<Vec3> origin,
            Optional<Element> element,
            Optional<Shape> shape,
            int sourceIndex,
            String reason,
            long variationSeed) implements EffectCommand {
        public WildMagic {
            Objects.requireNonNull(casterId, "casterId");
            Objects.requireNonNull(category, "category");
            origin = Objects.requireNonNull(origin, "origin");
            element = Objects.requireNonNull(element, "element");
            shape = Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(reason, "reason");
            if (casterId.isBlank()) {
                throw new IllegalArgumentException("casterId cannot be blank");
            }
            if (sourceIndex < 0) {
                throw new IllegalArgumentException("sourceIndex must be non-negative");
            }
        }

        /** Resolves deterministic, bounded fallout for this command. */
        public WildMagicEnvelope envelope() {
            return WildMagicResolver.resolve(category, variationSeed);
        }
    }

    private static void requireCasterAndOrigin(String casterId, Vec3 origin) {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(origin, "origin");
        if (casterId.isBlank()) {
            throw new IllegalArgumentException("casterId cannot be blank");
        }
    }

    private static void validateEffectNumbers(double radius, double magnitude) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
        if (!Double.isFinite(magnitude) || magnitude <= 0.0) {
            throw new IllegalArgumentException("magnitude must be finite and positive");
        }
    }
}
