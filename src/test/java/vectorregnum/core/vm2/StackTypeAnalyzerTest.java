package vectorregnum.core.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.RuntimeValue.BooleanValue;
import vectorregnum.core.vm2.RuntimeValue.EntityValue;
import vectorregnum.core.vm2.RuntimeValue.NumberValue;
import vectorregnum.core.vm2.RuntimeValue.VectorValue;

class StackTypeAnalyzerTest {
    private static SourceLocation at(int index) { return new SourceLocation(index, 3, index + 1, "S" + index); }

    @Test
    void validatesPolymorphicArithmeticAndWorldOperands() {
        Program program = new Program(List.of(
                Instruction.push(new EntityValue("caster"), at(0)),
                Instruction.push(new VectorValue(new Vector3(1, 0, 0)), at(1)),
                Instruction.push(new NumberValue(2), at(2)),
                Instruction.multiply(at(3)),
                Instruction.impulse(4, 0, at(4)),
                Instruction.halt(at(5))));

        StackAnalysis analysis = StackTypeAnalyzer.analyze(program);
        assertTrue(analysis.valid(), () -> analysis.diagnostics().toString());
        assertEquals(List.of(StackType.ENTITY, StackType.VECTOR, StackType.NUMBER),
                analysis.entryStacks().get(3));
        assertEquals(3, analysis.maximumDepth());
    }

    @Test
    void reportsUnderflowAndTypeMismatchAtExactAuthoredSource() {
        StackAnalysis underflow = StackTypeAnalyzer.analyze(
                new Program(List.of(Instruction.pop(at(0)))));
        assertEquals(StackDiagnostic.Code.STACK_UNDERFLOW,
                underflow.diagnostics().getFirst().code());
        assertEquals(at(0), underflow.diagnostics().getFirst().source());

        StackAnalysis mismatch = StackTypeAnalyzer.analyze(new Program(List.of(
                Instruction.push(new BooleanValue(true), at(0)),
                Instruction.push(new NumberValue(2), at(1)),
                Instruction.add(at(2)))));
        StackDiagnostic diagnostic = mismatch.diagnostics().getFirst();
        assertEquals(StackDiagnostic.Code.TYPE_MISMATCH, diagnostic.code());
        assertEquals(at(2), diagnostic.source());
        assertTrue(diagnostic.message().contains("boolean"));
        assertTrue(diagnostic.message().contains("number"));
    }

    @Test
    void rejectsBranchesAndLoopsThatMergeDifferentStackShapes() {
        Program branch = new Program(List.of(
                Instruction.push(new BooleanValue(true), at(0)),
                Instruction.jumpIfFalse(3, at(1)),
                Instruction.push(new NumberValue(2), at(2)),
                Instruction.halt(at(3))));
        StackAnalysis analysis = StackTypeAnalyzer.analyze(branch);
        assertFalse(analysis.valid());
        assertTrue(analysis.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == StackDiagnostic.Code.CONTROL_FLOW_MERGE
                        && diagnostic.source().equals(at(3))));
    }
}
