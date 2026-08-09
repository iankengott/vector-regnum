package vectorregnum.compiler;

public class Instruction {
    public final Opcode opcode;
    public final Object[] operands;
    public final int originalIndex;
    
    public final double manaCost;
    public final int complexity;

    public Instruction(Opcode opcode, int originalIndex, Object... operands) {
        this.opcode = opcode;
        this.originalIndex = originalIndex;
        this.operands = operands != null ? operands : new Object[0];
        
        this.manaCost = calculateManaCost();
        this.complexity = opcode.complexityWeight;
    }
    
    private double calculateManaCost() {
        double cost = opcode.baseManaCost;
        if (opcode == Opcode.EXPAND_AREA && operands.length > 0 && operands[0] instanceof Double) {
            double radius = (Double) operands[0];
            // Exponential scaling: base + (radius^1.5 * 5.0)
            cost += Math.pow(radius, 1.5) * 5.0; 
        } else if (opcode == Opcode.AMPLIFY && operands.length > 0 && operands[0] instanceof Double) {
            double amp = (Double) operands[0];
            cost *= amp; // Multiplies the cost of the instruction
        }
        return cost;
    }
}
