package vectorregnum.core.semantic;

import java.util.Map;
import java.util.Objects;
import vectorregnum.core.vm2.RuntimeValue;

/** Stable cast-independent inputs available to semantic lowering rules. */
public record LoweringContext(String spellId, long deterministicSeed,
        Map<String, RuntimeValue> bindings) {
    public LoweringContext {
        Objects.requireNonNull(spellId, "spellId");
        if (spellId.isBlank()) throw new IllegalArgumentException("spellId cannot be blank");
        bindings = Map.copyOf(Objects.requireNonNull(bindings, "bindings"));
    }
}
