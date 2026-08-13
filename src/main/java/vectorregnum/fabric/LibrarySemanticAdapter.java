package vectorregnum.fabric;

import java.util.LinkedHashMap;
import java.util.Map;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.semantic.SemanticProgram;
import vectorregnum.core.semantic.SemanticValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.fabric.progression.LibraryOpcode;
import vectorregnum.fabric.progression.SpellDefinition;
import vectorregnum.fabric.progression.SpellInstruction;

/** Lossless boundary from stable curated-library opcodes into shared spell semantics. */
public final class LibrarySemanticAdapter {
    private LibrarySemanticAdapter() { }

    public static SemanticOpcode semanticOpcode(LibraryOpcode opcode) {
        return SemanticOpcode.valueOf(opcode.name());
    }

    public static SemanticProgram adapt(SpellDefinition spell) {
        return new SemanticProgram(java.util.stream.IntStream.range(0, spell.program().size())
                .mapToObj(index -> adapt(spell.program().get(index), index)).toList());
    }

    static SemanticInstruction adapt(SpellInstruction instruction, int index) {
        SemanticOpcode opcode = semanticOpcode(instruction.opcode());
        Map<String, SemanticValue> operands = new LinkedHashMap<>();
        instruction.operands().forEach((key, value) -> {
            SemanticValue converted = switch (value) {
                case Number number -> new SemanticValue.NumberValue(number.doubleValue());
                case String text -> new SemanticValue.TextValue(text);
                case Boolean bool -> new SemanticValue.BooleanValue(bool);
                default -> throw new IllegalArgumentException(instruction.opcode()
                        + " has unsupported operand '" + key + "' type "
                        + value.getClass().getSimpleName());
            };
            operands.put(key, converted);
        });
        return new SemanticInstruction(opcode, operands,
                SourceLocation.at(index, instruction.opcode().name()));
    }
}
