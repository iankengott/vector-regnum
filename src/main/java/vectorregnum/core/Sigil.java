package vectorregnum.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable compatibility source sigil. */
public final class Sigil {
    private final String type;
    private final List<Object> parameters;

    public Sigil(String type, Object... parameters) {
        this.type = Objects.requireNonNull(type, "type");
        Objects.requireNonNull(parameters, "parameters");

        List<Object> copy = new ArrayList<>(parameters.length);
        for (Object parameter : parameters) {
            if (!isSupportedImmutableParameter(parameter)) {
                throw new IllegalArgumentException(
                        "Sigil parameters must be null, text, or immutable numeric values");
            }
            copy.add(parameter);
        }
        this.parameters = Collections.unmodifiableList(copy);
    }

    public String type() {
        return type;
    }

    public List<Object> parameters() {
        return parameters;
    }

    private static boolean isSupportedImmutableParameter(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }
}
