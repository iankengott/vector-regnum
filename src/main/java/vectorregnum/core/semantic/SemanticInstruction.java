package vectorregnum.core.semantic;

import java.util.Map;
import java.util.Objects;
import vectorregnum.core.vm2.SourceLocation;

/** Immutable semantic instruction with typed operands and retained authored source. */
public record SemanticInstruction(SemanticOpcode opcode, Map<String, SemanticValue> operands,
        SourceLocation source) {
    public SemanticInstruction {
        Objects.requireNonNull(opcode, "opcode");
        operands = Map.copyOf(Objects.requireNonNull(operands, "operands"));
        Objects.requireNonNull(source, "source");
        if (operands.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                || operands.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("semantic operand names/values cannot be blank or null");
        }
        if (opcode == SemanticOpcode.CREATE_FORM
                && (!(operands.get("spec") instanceof SemanticValue.CreationValue)
                || operands.size() != 1)) {
            throw new IllegalArgumentException("CREATE_FORM requires exactly one creation 'spec'");
        }
        if (opcode == SemanticOpcode.EXECUTE && !operands.isEmpty()) {
            throw new IllegalArgumentException("EXECUTE does not accept operands");
        }
        SemanticSchema.validate(opcode, operands);
    }

    public static SemanticInstruction simple(SemanticOpcode opcode, SourceLocation source) {
        return new SemanticInstruction(opcode, Map.of(), source);
    }

    public static SemanticInstruction creation(CreationSpec spec, SourceLocation source) {
        return new SemanticInstruction(SemanticOpcode.CREATE_FORM,
                Map.of("spec", new SemanticValue.CreationValue(spec)), source);
    }

    public CreationSpec creationSpec() {
        if (!(operands.get("spec") instanceof SemanticValue.CreationValue creation)) {
            throw new IllegalStateException(opcode + " is not a creation instruction");
        }
        return creation.value();
    }
}
