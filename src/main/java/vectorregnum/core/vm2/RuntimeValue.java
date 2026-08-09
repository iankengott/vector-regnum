package vectorregnum.core.vm2;

import java.util.List;
import java.util.Objects;

/** Closed typed value set accepted by the spell VM. */
public sealed interface RuntimeValue permits RuntimeValue.NumberValue,
        RuntimeValue.BooleanValue, RuntimeValue.PointValue, RuntimeValue.VectorValue,
        RuntimeValue.EntityValue, RuntimeValue.ListValue {

    ValueType type();

    enum ValueType { NUMBER, BOOLEAN, POINT, VECTOR, ENTITY, LIST }

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

    record ListValue(List<RuntimeValue> values) implements RuntimeValue {
        public ListValue {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("lists cannot contain null");
            }
        }
        @Override public ValueType type() { return ValueType.LIST; }
    }
}
