package vectorregnum.core.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.ManaCostModel;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.StackAnalysis;
import vectorregnum.core.vm2.StackTypeAnalyzer;

/** Complete generic semantic-to-vm2 backend shared by curated and authored semantics. */
public final class SemanticVmLowerer {
    private static final OpcodeLoweringRegistry<Instruction> REGISTRY = buildRegistry();

    private SemanticVmLowerer() { }

    public static Set<SemanticOpcode> missingOpcodes() { return REGISTRY.missingOpcodes(); }

    public static LoweringResult<Instruction> lower(
            SemanticProgram program, LoweringContext context) {
        LoweringResult<Instruction> result = REGISTRY.lower(program, context);
        if (!result.successful()) return result;

        List<Instruction> costed = new ArrayList<>(result.output().size());
        ManaCostModel.Input previousActionCost = null;
        for (Instruction lowered : result.output()) {
            if (lowered.opcode() == vectorregnum.core.vm2.Opcode.SEMANTIC) {
                SemanticInstruction semantic = lowered.semantic();
                if (semantic.opcode() == SemanticOpcode.REPEAT_BOUNDED) {
                    int count = SemanticSchema.integer(semantic.operands(), "count");
                    if (previousActionCost != null) {
                        lowered = Instruction.semantic(semantic, lowered.cost().plus(
                                previousActionCost.times(count - 1)));
                    }
                } else if (SemanticCostModel.isRepeatableAction(semantic.opcode())) {
                    previousActionCost = SemanticCostModel.cost(semantic);
                }
            }
            costed.add(lowered);
        }
        return new LoweringResult<>(costed, result.diagnostics());
    }

    public static Program lowerChecked(SemanticProgram semantic, LoweringContext context) {
        LoweringResult<Instruction> result = lower(semantic, context);
        if (!result.successful()) {
            LoweringDiagnostic first = result.diagnostics().getFirst();
            throw new IllegalArgumentException(first.code() + " at semantic instruction "
                    + first.instructionIndex() + " (source " + first.source().sourceIndex()
                    + "): " + first.message());
        }
        Program program = new Program(result.output());
        StackAnalysis analysis = StackTypeAnalyzer.analyze(program);
        if (!analysis.valid()) {
            throw new IllegalArgumentException("semantic lowering produced invalid vm2 stack: "
                    + analysis.diagnostics().getFirst().message());
        }
        return program;
    }

    private static OpcodeLoweringRegistry<Instruction> buildRegistry() {
        OpcodeLoweringRegistry.Builder<Instruction> builder = OpcodeLoweringRegistry.builder();
        for (SemanticOpcode opcode : SemanticOpcode.values()) {
            builder.register(opcode, SemanticVmLowerer::lowerStep);
        }
        return builder.build();
    }

    private static List<Instruction> lowerStep(
            SemanticInstruction instruction, LoweringContext context) {
        List<Instruction> output = new ArrayList<>();
        ManaCostModel.Input cost = SemanticCostModel.cost(instruction);
        boolean nativeCost = instruction.opcode() == SemanticOpcode.SET_DURATION
                || instruction.opcode() == SemanticOpcode.WAIT_TICKS;
        output.add(Instruction.semantic(instruction,
                nativeCost ? ManaCostModel.Input.ZERO : cost));
        if (instruction.opcode() == SemanticOpcode.SET_DURATION) {
            output.add(Instruction.duration(
                    SemanticSchema.integer(instruction.operands(), "ticks"), instruction.source()));
        } else if (instruction.opcode() == SemanticOpcode.WAIT_TICKS) {
            output.add(Instruction.delay(
                    SemanticSchema.integer(instruction.operands(), "ticks"), instruction.source()));
        } else if (instruction.opcode() == SemanticOpcode.EXECUTE) {
            output.add(Instruction.halt(instruction.source()));
        }
        return List.copyOf(output);
    }
}
