package vectorregnum.core.semantic;

import java.util.Objects;

/** Closed operand value set for semantic instructions. */
public sealed interface SemanticValue permits SemanticValue.NumberValue,
        SemanticValue.TextValue, SemanticValue.BooleanValue, SemanticValue.CreationValue {
    record NumberValue(double value) implements SemanticValue {
        public NumberValue {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("semantic number must be finite");
        }
    }

    record TextValue(String value) implements SemanticValue {
        public TextValue {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) throw new IllegalArgumentException("semantic text cannot be blank");
        }
    }

    record BooleanValue(boolean value) implements SemanticValue { }

    record CreationValue(CreationSpec value) implements SemanticValue {
        public CreationValue { Objects.requireNonNull(value, "value"); }
    }
}
