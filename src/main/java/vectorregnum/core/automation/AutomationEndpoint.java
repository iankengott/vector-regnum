package vectorregnum.core.automation;

import java.util.Objects;

/** Immutable, dimension-qualified destination for an automation invocation. */
public record AutomationEndpoint(String dimension, int x, int y, int z) {
    public AutomationEndpoint {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || dimension.length() > 128) {
            throw new IllegalArgumentException("dimension must contain 1 to 128 characters");
        }
    }

    public String stableKey() {
        return dimension + ":" + x + ":" + y + ":" + z;
    }
}
