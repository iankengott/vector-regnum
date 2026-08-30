package vectorregnum.core.vm2;

import java.util.Objects;
import java.util.regex.Pattern;

/** Typed metadata for priority-24 instructions; never contains runtime or Minecraft state. */
public sealed interface AdvancedOperand permits AdvancedOperand.Named,
        AdvancedOperand.IteratorSpec, AdvancedOperand.WatchSpec,
        AdvancedOperand.RangeSpec, AdvancedOperand.ForkSpec {
    int MAX_IDENTIFIER_CHARS = 32;
    Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    static String checkedName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.length() > MAX_IDENTIFIER_CHARS || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "identifier must match [a-z][a-z0-9_]{0,31}");
        }
        return value;
    }

    record Named(String name) implements AdvancedOperand {
        public Named { name = checkedName(name); }
    }

    /** Structured list traversal: BEGIN targets exit; NEXT targets the body. */
    record IteratorSpec(String name, int target, int maximumSteps) implements AdvancedOperand {
        public IteratorSpec {
            name = checkedName(name);
            if (target < 0 || maximumSteps < 1) {
                throw new IllegalArgumentException("invalid iterator target/step bound");
            }
        }
    }

    record WatchSpec(String variable, double declaredRange) implements AdvancedOperand {
        public WatchSpec {
            variable = checkedName(variable);
            finitePositive(declaredRange, "watch range");
        }
    }

    record RangeSpec(double declaredRange, int samples) implements AdvancedOperand {
        public RangeSpec {
            finitePositive(declaredRange, "declared range");
            if (samples < 1) throw new IllegalArgumentException("samples must be positive");
        }
    }

    record ForkSpec(String name, int start, int endExclusive) implements AdvancedOperand {
        public ForkSpec {
            name = checkedName(name);
            if (start < 0 || endExclusive <= start) {
                throw new IllegalArgumentException("invalid fork range");
            }
        }
    }

    private static void finitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
