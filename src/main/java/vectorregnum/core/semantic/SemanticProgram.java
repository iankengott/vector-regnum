package vectorregnum.core.semantic;

import java.util.List;
import java.util.Objects;

/** Immutable, terminal semantic program suitable for multiple lowering backends. */
public record SemanticProgram(List<SemanticInstruction> instructions) {
    public SemanticProgram {
        instructions = List.copyOf(Objects.requireNonNull(instructions, "instructions"));
        if (instructions.isEmpty()) throw new IllegalArgumentException("semantic program cannot be empty");
        if (instructions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("semantic instructions cannot be null");
        }
        if (instructions.getLast().opcode() != SemanticOpcode.EXECUTE) {
            throw new IllegalArgumentException("semantic program must end with EXECUTE");
        }
        for (int index = 0; index < instructions.size() - 1; index++) {
            if (instructions.get(index).opcode() == SemanticOpcode.EXECUTE) {
                throw new IllegalArgumentException("EXECUTE must be the final semantic instruction");
            }
        }
    }
}
