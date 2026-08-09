package vectorregnum.core.circle;

import java.math.BigDecimal;
import java.util.Objects;

/** Typed, persistence-safe sigil parameter. */
public sealed interface CircleValue permits CircleValue.NumberValue,
        CircleValue.TextValue, CircleValue.BooleanValue {

    record NumberValue(BigDecimal value) implements CircleValue {
        public NumberValue {
            Objects.requireNonNull(value, "value");
            value = value.stripTrailingZeros();
            if (value.signum() == 0) {
                value = BigDecimal.ZERO;
            }
        }

        public NumberValue(String value) {
            this(new BigDecimal(Objects.requireNonNull(value, "value")));
        }

        public String canonicalText() {
            return value.toPlainString();
        }
    }

    record TextValue(String value) implements CircleValue {
        public TextValue {
            Objects.requireNonNull(value, "value");
            if (value.length() > 4096) {
                throw new IllegalArgumentException("text parameter is longer than 4096 characters");
            }
        }
    }

    record BooleanValue(boolean value) implements CircleValue {
    }

    static CircleValue number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("number must be finite");
        }
        return new NumberValue(BigDecimal.valueOf(value));
    }

    static CircleValue text(String value) {
        return new TextValue(value);
    }

    static CircleValue bool(boolean value) {
        return new BooleanValue(value);
    }
}
