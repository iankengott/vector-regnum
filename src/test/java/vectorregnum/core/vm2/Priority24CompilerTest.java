package vectorregnum.core.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleValue;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.Vm2CircleCompilation;
import vectorregnum.core.circle.Vm2CircleCompiler;

class Priority24CompilerTest {
    @Test
    void textVariablesAreTypedAndDefinitelyAssigned() {
        Program program = new Program(List.of(
                Instruction.push(new RuntimeValue.TextValue("hello"), at(0)),
                Instruction.storeVariable("message", at(1)),
                Instruction.loadVariable("message", at(2)),
                Instruction.halt(at(3))));

        StackAnalysis analysis = StackTypeAnalyzer.analyze(program);
        assertTrue(analysis.valid(), () -> analysis.diagnostics().toString());
        assertEquals(List.of(StackType.TEXT), analysis.entryStacks().get(3));

        StackAnalysis missing = StackTypeAnalyzer.analyze(new Program(List.of(
                Instruction.loadVariable("missing", at(0)))));
        assertEquals(StackDiagnostic.Code.VARIABLE_NOT_FOUND,
                missing.diagnostics().getFirst().code());
        assertEquals(at(0), missing.diagnostics().getFirst().source());
    }

    @Test
    void variableTypeCannotChange() {
        StackAnalysis analysis = StackTypeAnalyzer.analyze(new Program(List.of(
                Instruction.push(new RuntimeValue.NumberValue(1), at(0)),
                Instruction.storeVariable("value", at(1)),
                Instruction.push(new RuntimeValue.BooleanValue(true), at(2)),
                Instruction.storeVariable("value", at(3)))));

        assertTrue(analysis.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == StackDiagnostic.Code.VARIABLE_TYPE_MISMATCH
                        && diagnostic.source().equals(at(3))));
    }

    @Test
    void iteratorAnalyzesEmptyAndNonemptyPaths() {
        Program program = new Program(List.of(
                Instruction.push(new RuntimeValue.ListValue(List.of(
                        new RuntimeValue.NumberValue(1))), at(0)),
                Instruction.iteratorBegin("items", 4, 4, at(1)),
                Instruction.pop(at(2)),
                Instruction.iteratorNext("items", 2, at(3)),
                Instruction.halt(at(4))));

        StackAnalysis analysis = StackTypeAnalyzer.analyze(program);
        assertTrue(analysis.valid(), () -> analysis.diagnostics().toString());
        assertEquals(List.of(StackType.NUMBER), analysis.entryStacks().get(2));
        assertEquals(List.of(), analysis.entryStacks().get(4));
    }

    @Test
    void homogeneousListIteratorsRetainTheirElementTypes() {
        assertEquals(StackType.NUMBER_LIST, StackType.of(new RuntimeValue.ListValue(List.of(
                new RuntimeValue.NumberValue(1)))));
        assertEquals(StackType.BOOLEAN_LIST, StackType.of(new RuntimeValue.ListValue(List.of(
                new RuntimeValue.BooleanValue(true)))));
        assertEquals(StackType.VECTOR_LIST, StackType.of(new RuntimeValue.ListValue(List.of(
                new RuntimeValue.VectorValue(new Vector3(1, 0, 0))))));
        assertEquals(StackType.TEXT_LIST, StackType.of(new RuntimeValue.ListValue(List.of(
                new RuntimeValue.TextValue("one")))));

        Program arithmetic = new Program(List.of(
                Instruction.push(new RuntimeValue.ListValue(List.of(
                        new RuntimeValue.NumberValue(1))), at(0)),
                Instruction.iteratorBegin("numbers", 6, 1, at(1)),
                Instruction.push(new RuntimeValue.NumberValue(1), at(2)),
                Instruction.add(at(3)),
                Instruction.pop(at(4)),
                Instruction.iteratorNext("numbers", 2, at(5)),
                Instruction.halt(at(6))));
        StackAnalysis analysis = StackTypeAnalyzer.analyze(arithmetic);
        assertTrue(analysis.valid(), () -> analysis.diagnostics().toString());
        assertEquals(List.of(StackType.NUMBER, StackType.NUMBER),
                analysis.entryStacks().get(3));
    }

    @Test
    void collisionWatchSignalAndOutputHaveConcreteStackEffects() {
        Program program = new Program(List.of(
                Instruction.push(new RuntimeValue.EntityValue("entity"), at(0)),
                Instruction.push(new RuntimeValue.PointValue(new Vector3(0, 0, 0)), at(1)),
                Instruction.collision(4, 1, at(2)),
                Instruction.pop(at(3)),
                Instruction.push(new RuntimeValue.NumberValue(1), at(4)),
                Instruction.storeVariable("value", at(5)),
                Instruction.push(new RuntimeValue.PointValue(new Vector3(0, 0, 0)), at(6)),
                Instruction.watchVariable("value", 4, at(7)),
                Instruction.push(new RuntimeValue.NumberValue(2), at(8)),
                Instruction.push(new RuntimeValue.PointValue(new Vector3(0, 0, 0)), at(9)),
                Instruction.signal(4, at(10)),
                Instruction.push(new RuntimeValue.TextValue("done"), at(11)),
                Instruction.push(new RuntimeValue.PointValue(new Vector3(0, 0, 0)), at(12)),
                Instruction.output(4, at(13)),
                Instruction.halt(at(14))));

        StackAnalysis analysis = StackTypeAnalyzer.analyze(program);
        assertTrue(analysis.valid(), () -> analysis.diagnostics().toString());
        assertEquals(List.of(), analysis.entryStacks().get(14));

        Program typedOutputAndPointCollision = new Program(List.of(
                Instruction.push(new RuntimeValue.PointValue(new Vector3(0, 0, 0)), at(20)),
                Instruction.push(new RuntimeValue.PointValue(new Vector3(0, 0, 0)), at(21)),
                Instruction.collision(4, 1, at(22)),
                Instruction.push(new RuntimeValue.PointValue(new Vector3(0, 0, 0)), at(23)),
                Instruction.output(4, at(24)),
                Instruction.halt(at(25))));
        StackAnalysis typed = StackTypeAnalyzer.analyze(typedOutputAndPointCollision);
        assertTrue(typed.valid(), () -> typed.diagnostics().toString());

        Program badSignalPoint = new Program(List.of(
                Instruction.push(new RuntimeValue.NumberValue(1), at(30)),
                Instruction.push(new RuntimeValue.TextValue("not_a_point"), at(31)),
                Instruction.signal(4, at(32))));
        StackAnalysis invalidSignal = StackTypeAnalyzer.analyze(badSignalPoint);
        assertTrue(invalidSignal.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == StackDiagnostic.Code.TYPE_MISMATCH));
    }

    @Test
    void forkBodiesMustBeStackNeutralAtBranchEnd() {
        Program valid = new Program(List.of(
                Instruction.fork("spark", 3, 5, at(0)),
                Instruction.join(at(1)),
                Instruction.jump(5, at(2)),
                Instruction.delay(0, at(3)),
                Instruction.branchEnd(at(4)),
                Instruction.halt(at(5))));
        assertTrue(StackTypeAnalyzer.analyze(valid).valid());

        Program invalid = new Program(List.of(
                Instruction.fork("spark", 3, 5, at(0)),
                Instruction.join(at(1)),
                Instruction.jump(5, at(2)),
                Instruction.push(new RuntimeValue.NumberValue(1), at(3)),
                Instruction.branchEnd(at(4)),
                Instruction.halt(at(5))));
        StackAnalysis analysis = StackTypeAnalyzer.analyze(invalid);
        assertTrue(analysis.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == StackDiagnostic.Code.BRANCH_STACK_MISMATCH));

        Program rootMutationBetweenForks = new Program(List.of(
                Instruction.push(new RuntimeValue.NumberValue(1), at(10)),
                Instruction.fork("first", 6, 8, at(11)),
                Instruction.pop(at(12)),
                Instruction.fork("second", 8, 10, at(13)),
                Instruction.join(at(14)),
                Instruction.jump(10, at(15)),
                Instruction.delay(0, at(16)),
                Instruction.branchEnd(at(17)),
                Instruction.delay(0, at(18)),
                Instruction.branchEnd(at(19)),
                Instruction.halt(at(20))));
        StackAnalysis ordered = StackTypeAnalyzer.analyze(rootMutationBetweenForks);
        assertTrue(ordered.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == StackDiagnostic.Code.BRANCH_STACK_MISMATCH),
                () -> "root/child scheduling mismatch was not rejected: " + ordered.diagnostics());
    }

    @Test
    void compilerLowersTextVariablesAndPreservesSourceLocations() {
        MagicCircle circle = new MagicCircle(1, "variables", "Variables", 1, 8, List.of(
                sigil(0, 0, "VM_PUSH_TEXT", new CircleValue.TextValue("hello")),
                sigil(0, 1, "VM_STORE_VARIABLE", new CircleValue.TextValue("message")),
                sigil(0, 2, "VM_LOAD_VARIABLE", new CircleValue.TextValue("message")),
                sigil(0, 3, "VM_PUSH_ORIGIN"),
                sigil(0, 4, "VM_OUTPUT", CircleValue.number(4)),
                sigil(0, 5, "EXECUTE")));

        Vm2CircleCompilation compiled = Vm2CircleCompiler.compile(circle,
                new Vm2CircleCompiler.Context("caster", new Vector3(0, 0, 0),
                        new Vector3(0, 0, 1)));
        assertFalse(compiled.hasErrors(), () -> compiled.diagnostics().toString());
        List<Instruction> instructions = compiled.compiledProgram().orElseThrow().instructions();
        assertEquals(Opcode.PUSH, instructions.get(0).opcode());
        assertEquals(Opcode.STORE_VARIABLE, instructions.get(1).opcode());
        assertEquals(2, instructions.get(2).source().sourceIndex());
        assertEquals(Opcode.OUTPUT, instructions.get(4).opcode());
    }

    @Test
    void paletteExposesAdvancedIdentifiers() {
        var palette = vectorregnum.neoforge.editor.SigilPalette.defaults();
        assertTrue(palette.entry("VM_PUSH_TEXT").isPresent());
        assertTrue(palette.entry("VM_FORK").isPresent());
        assertEquals("message", ((CircleValue.TextValue) palette.entry("VM_STORE_VARIABLE")
                .orElseThrow().parseParameters(List.of("message")).getFirst()).value());
        assertTrue(palette.entry("VM_STORE_VARIABLE").orElseThrow()
                .parseParameters(List.of("message")).size() == 1);
    }

    private static SourceLocation at(int index) {
        return new SourceLocation(index, 2, index + 1, "P24_" + index);
    }

    private static PlacedSigil sigil(int ring, int slot, String type, CircleValue... values) {
        return new PlacedSigil(new CircleCoordinate(ring, slot), type, List.of(values));
    }
}
