package vectorregnum.core.vm2;

import java.util.Objects;

/** Precise, machine-readable diagnostic produced before VM execution. */
public record StackDiagnostic(Code code, String message, SourceLocation source,
        int instructionPointer) {
    public StackDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
        if (message.isBlank() || instructionPointer < 0) {
            throw new IllegalArgumentException("invalid stack diagnostic");
        }
    }

    public enum Code {
        STACK_UNDERFLOW,
        TYPE_MISMATCH,
        STACK_OVERFLOW,
        CONTROL_FLOW_MERGE
    }
}
