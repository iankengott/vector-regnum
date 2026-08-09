package vectorregnum.core;

import java.util.Objects;

public record SpellFault(
        FaultCode code,
        String message,
        int sourceIndex,
        WildMagicCategory wildMagicCategory) {

    public SpellFault {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(wildMagicCategory, "wildMagicCategory");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
        if (sourceIndex < 0) {
            throw new IllegalArgumentException("sourceIndex must be non-negative");
        }
    }
}
