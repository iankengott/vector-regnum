package vectorregnum.core.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.RuntimeValue.NumberValue;
import vectorregnum.core.vm2.RuntimeValue.PointValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.SpellVm;
import vectorregnum.core.vm2.TickResult;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.WorldAccess;

class PresentationFoundationTest {
    private static SourceLocation at(int index) { return SourceLocation.at(index, "P" + index); }

    @Test
    void presentationIrRequiresTruthAndEnforcesDeclaredBudgets() {
        PresentationInstruction truth = new PresentationInstruction(
                PresentationTrigger.worldEffect(), PresentationPhase.IMPACT,
                PresentationCueKind.PARTICLES, "vector_regnum:impact/fire",
                PresentationBinding.IMPACT_POINT, 0, 10, .7, true,
                Map.of("radius", 2.0), new PresentationCost(1, 30, 0, 0, 1, 0, 1));
        PresentationProgram program = new PresentationProgram("vector_regnum:test", 42,
                List.of(truth), PresentationBudget.DEFAULT);
        assertEquals(30, program.declaredCost().particlesPerSecond());

        PresentationInstruction cosmeticOnly = new PresentationInstruction(
                PresentationTrigger.cast(), PresentationPhase.INVOCATION,
                PresentationCueKind.RUNES, "vector_regnum:runes/arcane",
                PresentationBinding.AUTHORED_CIRCLE, 0, 10, .2, false,
                Map.of(), PresentationCost.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new PresentationProgram(
                "vector_regnum:no_truth", 0, List.of(cosmeticOnly), PresentationBudget.DEFAULT));

        PresentationBudget tiny = new PresentationBudget(1, 20,
                new PresentationCost(1, 1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PresentationProgram(
                "vector_regnum:too_costly", 0, List.of(truth), tiny));
    }

    @Test
    void vmEmitsOnlyExecutedResolvedEventsInStableSequence() {
        Program program = new Program(List.of(
                Instruction.delay(1, at(0)),
                Instruction.push(new PointValue(new Vector3(0, 0, 0)), at(1)),
                Instruction.push(new NumberValue(4), at(2)),
                Instruction.select(WorldAccess.SelectionFilter.ANY, 4, 1, at(3)),
                Instruction.halt(at(4))));
        List<ExecutionEvent> events = new ArrayList<>();
        SpellVm vm = new SpellVm(program, WorldAccess.EMPTY, events::add);
        assertEquals(TickResult.Status.WAITING, vm.tick().status());
        assertEquals(TickResult.Status.HALTED, vm.tick().status());

        for (int index = 0; index < events.size(); index++) {
            assertEquals(index, events.get(index).sequence());
        }
        assertInstanceOf(ExecutionEvent.Started.class, events.get(0));
        assertInstanceOf(ExecutionEvent.DelayStarted.class, events.get(1));
        assertTrue(events.stream().anyMatch(ExecutionEvent.ValuesResolved.class::isInstance));
        assertInstanceOf(ExecutionEvent.Halted.class, events.getLast());
        assertEquals(5, events.stream().filter(ExecutionEvent.StepExecuted.class::isInstance).count());
    }

    @Test
    void presentationSinkFailuresCannotChangeAuthoritativeExecution() {
        Program program = new Program(List.of(Instruction.push(new NumberValue(1), at(0)),
                Instruction.halt(at(1))));
        SpellVm vm = new SpellVm(program, WorldAccess.EMPTY, event -> {
            throw new IllegalStateException("renderer unavailable");
        });
        assertEquals(TickResult.Status.HALTED, vm.tick().status());
        assertTrue(vm.fault().isEmpty());
        assertEquals(new NumberValue(1), vm.stackTopFirst().getFirst());
    }
}
