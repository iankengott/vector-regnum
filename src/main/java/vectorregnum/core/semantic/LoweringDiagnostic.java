package vectorregnum.core.semantic;

import java.util.Objects;
import vectorregnum.core.vm2.SourceLocation;

/** Exact semantic-backend failure without leaking adapter exceptions. */
public record LoweringDiagnostic(Code code, String message, SourceLocation source,
        int instructionIndex) {
    public LoweringDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
        if (message.isBlank() || instructionIndex < 0) throw new IllegalArgumentException("invalid diagnostic");
    }

    public enum Code { MISSING_OPCODE_LOWERER, INVALID_OPERAND, LOWERING_FAILED }
}
