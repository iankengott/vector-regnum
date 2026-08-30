package vectorregnum.core.vm2;

import java.util.List;
import java.util.Objects;

/** Closed typed value set accepted by the spell VM. */
public sealed interface RuntimeValue permits RuntimeValue.NumberValue,
        RuntimeValue.BooleanValue, RuntimeValue.PointValue, RuntimeValue.VectorValue,
        RuntimeValue.EntityValue, RuntimeValue.TextValue, RuntimeValue.ListValue {

    ValueType type();

    int MAX_TEXT_CHARS = 256;
    int MAX_LIST_VALUES = 1_024;
    int MAX_LIST_DEPTH = 16;

    /** Append-only: presentation and persisted diagnostics may retain enum ordinals. */
    enum ValueType { NUMBER, BOOLEAN, POINT, VECTOR, ENTITY, LIST, TEXT }

    record NumberValue(double value) implements RuntimeValue {
        public NumberValue {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("number must be finite");
        }
        @Override public ValueType type() { return ValueType.NUMBER; }
    }

    record BooleanValue(boolean value) implements RuntimeValue {
        @Override public ValueType type() { return ValueType.BOOLEAN; }
    }

    record PointValue(Vector3 value) implements RuntimeValue {
        public PointValue { Objects.requireNonNull(value, "value"); }
        @Override public ValueType type() { return ValueType.POINT; }
    }

    record VectorValue(Vector3 value) implements RuntimeValue {
        public VectorValue { Objects.requireNonNull(value, "value"); }
        @Override public ValueType type() { return ValueType.VECTOR; }
    }

    /** Stable adapter-owned entity identifier; never contains a Minecraft Entity reference. */
    record EntityValue(String id) implements RuntimeValue {
        public EntityValue {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) throw new IllegalArgumentException("entity id cannot be blank");
        }
        @Override public ValueType type() { return ValueType.ENTITY; }
    }

    record TextValue(String value) implements RuntimeValue {
        public TextValue {
            Objects.requireNonNull(value, "value");
            if (value.isBlank() || value.length() > MAX_TEXT_CHARS) {
                throw new IllegalArgumentException("text must be 1.." + MAX_TEXT_CHARS + " characters");
            }
        }
        @Override public ValueType type() { return ValueType.TEXT; }
    }

    record ListValue(List<RuntimeValue> values) implements RuntimeValue {
        public ListValue {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("lists cannot contain null");
            }
            validateShape(values, 1, new int[1]);
        }
        @Override public ValueType type() { return ValueType.LIST; }

        private static void validateShape(List<RuntimeValue> values, int depth, int[] count) {
            if (depth > MAX_LIST_DEPTH) {
                throw new IllegalArgumentException("list nesting exceeds " + MAX_LIST_DEPTH);
            }
            for (RuntimeValue value : values) {
                if (++count[0] > MAX_LIST_VALUES) {
                    throw new IllegalArgumentException("list exceeds " + MAX_LIST_VALUES + " values");
                }
                if (value instanceof ListValue nested) {
                    validateShape(nested.values(), depth + 1, count);
                }
            }
        }
    }
}
