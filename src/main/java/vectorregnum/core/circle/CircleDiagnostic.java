package vectorregnum.core.circle;

import java.util.Objects;
import java.util.Optional;

/** Human-facing compiler/editor feedback with a stable machine-readable code. */
public record CircleDiagnostic(
        Severity severity,
        String code,
        String message,
        CircleCoordinate coordinate,
        int sourceIndex) {
    public enum Severity { INFO, WARNING, ERROR }

    public CircleDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (code.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("diagnostic code and message cannot be blank");
        }
        if (sourceIndex < -1) {
            throw new IllegalArgumentException("sourceIndex must be -1 or non-negative");
        }
    }

    public Optional<CircleCoordinate> location() {
        return Optional.ofNullable(coordinate);
    }
}
