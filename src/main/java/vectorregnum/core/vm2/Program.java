package vectorregnum.core.vm2;

import java.util.List;
import java.util.Objects;

/** Immutable validated bytecode plus its deterministic pre-cast mana quote. */
public final class Program {
    private final List<Instruction> instructions;
    private final ManaCostModel.Input declaredCost;
    private final ManaCostModel.Breakdown manaCost;

    public Program(List<Instruction> instructions) {
        this.instructions = copyAndValidate(instructions);
        this.declaredCost = aggregate(this.instructions);
        this.manaCost = ManaCostModel.estimate(declaredCost);
    }

    public List<Instruction> instructions() { return instructions; }
    public ManaCostModel.Input declaredCost() { return declaredCost; }
    public ManaCostModel.Breakdown manaCost() { return manaCost; }

    private static List<Instruction> copyAndValidate(List<Instruction> input) {
        List<Instruction> copy = List.copyOf(Objects.requireNonNull(input, "instructions"));
        for (int index = 0; index < copy.size(); index++) {
            Instruction instruction = Objects.requireNonNull(copy.get(index), "instruction");
            if (instruction.opcode() == Opcode.JUMP || instruction.opcode() == Opcode.JUMP_IF_FALSE
                    || instruction.opcode() == Opcode.LOOP) {
                if (instruction.argument() >= copy.size()) {
                    throw new IllegalArgumentException("jump target outside program at " + index);
                }
            }
            if (instruction.opcode() == Opcode.LOOP && instruction.argument() >= index) {
                throw new IllegalArgumentException("LOOP must target an earlier body instruction");
            }
        }
        return copy;
    }

    private static ManaCostModel.Input aggregate(List<Instruction> instructions) {
        Objects.requireNonNull(instructions, "instructions");
        ManaCostModel.Input total = ManaCostModel.Input.ZERO;
        for (int index = 0; index < instructions.size(); index++) {
            Instruction instruction = instructions.get(index);
            total = total.plus(Objects.requireNonNull(instruction, "instruction").cost());
            if (instruction.opcode() == Opcode.LOOP && instruction.secondArgument() > 1) {
                ManaCostModel.Input body = ManaCostModel.Input.ZERO;
                for (int bodyIndex = instruction.argument(); bodyIndex < index; bodyIndex++) {
                    body = body.plus(instructions.get(bodyIndex).cost());
                }
                total = total.plus(body.times(instruction.secondArgument() - 1));
            }
        }
        return total;
    }
}
