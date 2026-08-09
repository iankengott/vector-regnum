package vectorregnum.core.vm2;

import java.util.Objects;

/** Authored/runtime fault with the exact retained source and instruction pointer. */
public record VmFault(Code code, String message, SourceLocation source, int instructionPointer) {
    public VmFault {
        Objects.requireNonNull(code, "code"); Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
        if (message.isBlank() || instructionPointer < 0) throw new IllegalArgumentException("invalid fault");
    }

    public enum Code {
        STACK_UNDERFLOW, STACK_OVERFLOW, TYPE_MISMATCH, DIVIDE_BY_ZERO, NUMERIC_OVERFLOW,
        LOOP_LIMIT, TOTAL_INSTRUCTION_LIMIT, LIFETIME_TICK_LIMIT, INVALID_QUERY,
        WORLD_ADAPTER_ERROR, INVALID_PATH, ENTITY_NOT_FOUND
    }
}
