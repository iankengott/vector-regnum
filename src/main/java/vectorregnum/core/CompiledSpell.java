package vectorregnum.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Immutable output of the compatibility compiler. */
public final class CompiledSpell {
    private final List<Instruction> instructions;
    private final double totalManaCost;
    private final long totalComplexity;
    private final int sourceSize;
    private final List<Integer> sourceIndices;

    CompiledSpell(List<Instruction> instructions, int sourceSize) {
        if (sourceSize < 0) {
            throw new IllegalArgumentException("sourceSize must be non-negative");
        }
        this.instructions = List.copyOf(instructions);
        this.sourceSize = sourceSize;
        if (this.instructions.stream()
                .anyMatch(instruction -> instruction.sourceIndex() >= sourceSize)) {
            throw new IllegalArgumentException("Instruction source index is outside the source program");
        }
        this.sourceIndices = this.instructions.stream().map(Instruction::sourceIndex).toList();

        double cost = 0.0;
        long complexity = 0L;
        for (Instruction instruction : this.instructions) {
            cost += instruction.manaCost();
            if (!Double.isFinite(cost)) {
                throw new IllegalArgumentException("Compiled mana total overflowed");
            }
            complexity = Math.addExact(complexity, instruction.complexity());
        }
        this.totalManaCost = BigDecimal.valueOf(cost)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        this.totalComplexity = complexity;
    }

    List<Instruction> instructions() {
        return instructions;
    }

    public int instructionCount() {
        return instructions.size();
    }

    public List<Integer> sourceIndices() {
        return sourceIndices;
    }

    public double totalManaCost() {
        return totalManaCost;
    }

    public long totalComplexity() {
        return totalComplexity;
    }

    public int sourceSize() {
        return sourceSize;
    }
}
