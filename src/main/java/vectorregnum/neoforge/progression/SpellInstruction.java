package vectorregnum.neoforge.progression;

import java.util.Map;

public record SpellInstruction(LibraryOpcode opcode, Map<String, Object> operands) {
    public SpellInstruction {
        operands = Map.copyOf(operands);
    }

    public static SpellInstruction op(LibraryOpcode opcode) {
        return new SpellInstruction(opcode, Map.of());
    }

    public static SpellInstruction number(LibraryOpcode opcode, String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Spell operands must be finite");
        }
        return new SpellInstruction(opcode, Map.of(name, value));
    }

    public static SpellInstruction text(LibraryOpcode opcode, String name, String value) {
        return new SpellInstruction(opcode, Map.of(name, value));
    }
}
