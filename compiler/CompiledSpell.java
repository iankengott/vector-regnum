package vectorregnum.compiler;

import java.util.List;

public class CompiledSpell {
    public final List<Instruction> instructions;
    public final double totalManaCost;
    public final int totalComplexity;

    public CompiledSpell(List<Instruction> instructions) {
        this.instructions = instructions;
        double cost = 0;
        int comp = 0;
        for (Instruction inst : instructions) {
            cost += inst.manaCost;
            comp += inst.complexity;
        }
        this.totalManaCost = Math.round(cost * 100.0) / 100.0; // round to 2 decimal places
        this.totalComplexity = comp;
    }
}
