package vectorregnum.core.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.RuntimeValue.BooleanValue;
import vectorregnum.core.vm2.RuntimeValue.EntityValue;
import vectorregnum.core.vm2.RuntimeValue.ListValue;
import vectorregnum.core.vm2.RuntimeValue.NumberValue;
import vectorregnum.core.vm2.RuntimeValue.PointValue;
import vectorregnum.core.vm2.RuntimeValue.VectorValue;
import vectorregnum.core.vm2.WorldAccess.EntitySnapshot;
import vectorregnum.core.vm2.WorldAccess.RaycastHit;
import vectorregnum.core.vm2.WorldAccess.SelectionFilter;

class SpellVmTest {
    private static SourceLocation at(int index) { return new SourceLocation(index, index + 2, 7, "S" + index); }
    private static NumberValue number(double value) { return new NumberValue(value); }
    private static EntityValue entity(String id) { return new EntityValue(id); }
    private static PointValue point(double x, double y, double z) { return new PointValue(new Vector3(x, y, z)); }
    private static VectorValue vector(double x, double y, double z) { return new VectorValue(new Vector3(x, y, z)); }

    @Test
    void typedValuesAreFiniteAndListsAreImmutable() {
        assertThrows(IllegalArgumentException.class, () -> number(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Vector3(0, Double.POSITIVE_INFINITY, 0));
        ArrayList<RuntimeValue> source = new ArrayList<>(List.of(number(1)));
        ListValue value = new ListValue(source);
        source.clear();
        assertEquals(1, value.values().size());
        assertThrows(UnsupportedOperationException.class, () -> value.values().add(number(2)));
        assertEquals(RuntimeValue.ValueType.POINT, point(1, 2, 3).type());
        assertEquals(RuntimeValue.ValueType.ENTITY, entity("zombie-1").type());
    }

    @Test
    void arithmeticSupportsNumbersVectorsAndPoints() {
        Program program = new Program(List.of(
                Instruction.push(number(6), at(0)), Instruction.push(number(2), at(1)),
                Instruction.divide(at(2)), Instruction.push(vector(1, 2, 3), at(3)),
                Instruction.push(number(2), at(4)), Instruction.multiply(at(5)),
                Instruction.push(point(10, 10, 10), at(6)), Instruction.push(vector(1, 2, 3), at(7)),
                Instruction.add(at(8)), Instruction.halt(at(9))));
        SpellVm vm = run(program, WorldAccess.EMPTY);
        assertEquals(new PointValue(new Vector3(11, 12, 13)), vm.stackTopFirst().get(0));
        assertEquals(new VectorValue(new Vector3(2, 4, 6)), vm.stackTopFirst().get(1));
        assertEquals(number(3), vm.stackTopFirst().get(2));
    }

    @Test
    void typeFaultPreservesExactSourceCoordinates() {
        Program program = new Program(List.of(Instruction.push(new BooleanValue(true), at(0)),
                Instruction.push(number(2), at(1)), Instruction.add(at(2))));
        TickResult result = new SpellVm(program, WorldAccess.EMPTY).tick();
        VmFault fault = result.fault().orElseThrow();
        assertEquals(TickResult.Status.FAULTED, result.status());
        assertEquals(VmFault.Code.TYPE_MISMATCH, fault.code());
        assertEquals(new SourceLocation(2, 4, 7, "S2"), fault.source());
        assertEquals(2, fault.instructionPointer());
    }

    @Test
    void delaysResumeOnLaterTicksAndDurationReachesEffect() {
        FakeWorld world = new FakeWorld();
        Program program = new Program(List.of(Instruction.push(entity("a"), at(0)),
                Instruction.duration(20, at(1)), Instruction.delay(2, at(2)),
                Instruction.push(vector(1, 0, 0), at(3)), Instruction.impulse(5, 0, at(4)),
                Instruction.halt(at(5))));
        SpellVm vm = new SpellVm(program, world);
        assertEquals(TickResult.Status.WAITING, vm.tick().status());
        assertEquals(TickResult.Status.WAITING, vm.tick().status());
        TickResult finished = vm.tick();
        assertEquals(TickResult.Status.HALTED, finished.status());
        WorldEffect.Impulse effect = assertInstanceOf(WorldEffect.Impulse.class, finished.effects().getFirst());
        assertEquals(20, effect.durationTicks());
        assertEquals(new Vector3(1, 0, 0), effect.impulse());
    }

    @Test
    void branchesAndBoundedLoopsExecuteDeterministically() {
        Program branch = new Program(List.of(Instruction.push(new BooleanValue(false), at(0)),
                Instruction.jumpIfFalse(3, at(1)), Instruction.push(number(99), at(2)),
                Instruction.push(number(7), at(3)), Instruction.halt(at(4))));
        assertEquals(number(7), run(branch, WorldAccess.EMPTY).stackTopFirst().getFirst());

        Program loop = new Program(List.of(Instruction.push(number(0), at(0)),
                Instruction.push(number(1), at(1)), Instruction.add(at(2)),
                Instruction.loop(1, 3, at(3)), Instruction.halt(at(4))));
        assertEquals(number(3), run(loop, WorldAccess.EMPTY).stackTopFirst().getFirst());
    }

    @Test
    void excessiveLoopFaultsAtLoopSource() {
        Program program = new Program(List.of(Instruction.push(number(1), at(0)),
                Instruction.loop(0, 6, at(1))));
        VmLimits limits = new VmLimits(20, 20, 100, 100, 5, 100, 10);
        TickResult result = new SpellVm(program, WorldAccess.EMPTY, limits).tick();
        assertEquals(VmFault.Code.LOOP_LIMIT, result.fault().orElseThrow().code());
        assertEquals(at(1), result.fault().orElseThrow().source());
    }

    @Test
    void perTickBudgetYieldsAndLifetimeInstructionBudgetFaults() {
        Program program = new Program(List.of(Instruction.push(number(1), at(0)),
                Instruction.push(number(2), at(1)), Instruction.add(at(2)), Instruction.halt(at(3))));
        VmLimits yielding = new VmLimits(20, 2, 100, 100, 10, 100, 10);
        SpellVm vm = new SpellVm(program, WorldAccess.EMPTY, yielding);
        assertEquals(TickResult.Status.BUDGET_YIELD, vm.tick().status());
        assertEquals(TickResult.Status.HALTED, vm.tick().status());

        VmLimits capped = new VmLimits(20, 10, 2, 100, 10, 100, 10);
        TickResult fault = new SpellVm(program, WorldAccess.EMPTY, capped).tick();
        assertEquals(VmFault.Code.TOTAL_INSTRUCTION_LIMIT, fault.fault().orElseThrow().code());
        assertEquals(at(2), fault.fault().orElseThrow().source());
    }

    @Test
    void selectionAndRaycastReturnStableTypedEntityLists() {
        FakeWorld world = new FakeWorld();
        Program program = new Program(List.of(
                Instruction.push(point(0, 0, 0), at(0)), Instruction.push(number(8), at(1)),
                Instruction.select(SelectionFilter.ANY, 8, 2, at(2)),
                Instruction.push(point(0, 0, 0), at(3)), Instruction.push(vector(0, 0, 4), at(4)),
                Instruction.push(number(20), at(5)), Instruction.raycast(SelectionFilter.ANY, 20, 1, at(6)),
                Instruction.halt(at(7))));
        SpellVm vm = run(program, world);
        assertEquals(new ListValue(List.of(entity("b"))), vm.stackTopFirst().get(0));
        assertEquals(new ListValue(List.of(entity("a"), entity("b"))), vm.stackTopFirst().get(1));
        assertEquals(new Vector3(0, 0, 1), world.lastRayDirection);
    }

    @Test
    void invalidPerceptionRangeFaultsBeforeWorldCall() {
        FakeWorld world = new FakeWorld();
        Program program = new Program(List.of(Instruction.push(point(0, 0, 0), at(0)),
                Instruction.push(number(129), at(1)),
                Instruction.select(SelectionFilter.ANY, 129, 1, at(2))));
        TickResult result = new SpellVm(program, world).tick();
        assertEquals(VmFault.Code.INVALID_QUERY, result.fault().orElseThrow().code());
        assertEquals(0, world.selectCalls);
    }

    @Test
    void everyPhysicsOperationEmitsAValidatedAdapterCommand() {
        List<Instruction> code = new ArrayList<>();
        int i = 0;
        code.add(Instruction.duration(12, at(i++)));
        code.add(Instruction.push(entity("a"), at(i++))); code.add(Instruction.push(vector(1, 2, 3), at(i++))); code.add(Instruction.impulse(10, 0, at(i++)));
        code.add(Instruction.push(entity("a"), at(i++))); code.add(Instruction.push(vector(0, 1, 0), at(i++))); code.add(Instruction.acceleration(10, 0, at(i++)));
        code.add(Instruction.push(entity("a"), at(i++))); code.add(Instruction.push(number(.8), at(i++))); code.add(Instruction.damping(2, 0, at(i++)));
        code.add(Instruction.push(entity("a"), at(i++))); code.add(Instruction.push(new ListValue(List.of(point(1, 0, 0), point(2, 0, 0))), at(i++))); code.add(Instruction.push(number(2), at(i++))); code.add(Instruction.followPath(20, 0, at(i++)));
        code.add(Instruction.push(entity("a"), at(i++))); code.add(Instruction.push(point(5, 0, 0), at(i++))); code.add(Instruction.push(number(1), at(i++))); code.add(Instruction.moveToward(8, 0, at(i++)));
        code.add(Instruction.push(entity("a"), at(i++))); code.add(Instruction.push(entity("b"), at(i++))); code.add(Instruction.push(number(3), at(i++))); code.add(Instruction.keepDistance(8, 0, at(i++)));
        code.add(Instruction.halt(at(i)));
        SpellVm vm = run(new Program(code), new FakeWorld());
        assertEquals(6, vm.allEffects().size());
        assertInstanceOf(WorldEffect.Impulse.class, vm.allEffects().get(0));
        assertInstanceOf(WorldEffect.Acceleration.class, vm.allEffects().get(1));
        assertInstanceOf(WorldEffect.Damping.class, vm.allEffects().get(2));
        assertInstanceOf(WorldEffect.FollowPath.class, vm.allEffects().get(3));
        assertInstanceOf(WorldEffect.MoveToward.class, vm.allEffects().get(4));
        assertInstanceOf(WorldEffect.KeepDistance.class, vm.allEffects().get(5));
        assertTrue(vm.allEffects().stream().allMatch(effect -> effect.durationTicks() == 12));
    }

    @Test
    void stackOverflowAndUnderflowAreAuthoredFaults() {
        TickResult underflow = new SpellVm(new Program(List.of(Instruction.pop(at(0)))), WorldAccess.EMPTY).tick();
        assertEquals(VmFault.Code.STACK_UNDERFLOW, underflow.fault().orElseThrow().code());
        VmLimits tiny = new VmLimits(1, 10, 10, 10, 10, 10, 10);
        Program pushes = new Program(List.of(Instruction.push(number(1), at(0)), Instruction.push(number(2), at(1))));
        TickResult overflow = new SpellVm(pushes, WorldAccess.EMPTY, tiny).tick();
        assertEquals(VmFault.Code.STACK_OVERFLOW, overflow.fault().orElseThrow().code());
    }

    @Test
    void declaredDurationCannotOutliveTheServerSafetyEnvelope() {
        assertThrows(IllegalArgumentException.class,
                () -> Instruction.duration(Instruction.MAX_DURATION_TICKS + 1, at(0)));
    }

    private static SpellVm run(Program program, WorldAccess world) {
        SpellVm vm = new SpellVm(program, world);
        for (int i = 0; i < 100 && !vm.isTerminal(); i++) vm.tick();
        assertTrue(vm.isTerminal(), "VM did not terminate");
        assertTrue(vm.fault().isEmpty(), () -> "unexpected fault: " + vm.fault());
        return vm;
    }

    private static final class FakeWorld implements WorldAccess {
        private final EntitySnapshot a = new EntitySnapshot("a", new Vector3(1, 0, 0), 2, "mob", Set.of("hostile"));
        private final EntitySnapshot b = new EntitySnapshot("b", new Vector3(2, 0, 0), 3, "mob", Set.of());
        private Vector3 lastRayDirection;
        private int selectCalls;
        @Override public Optional<EntitySnapshot> entity(String id) {
            return List.of(a, b).stream().filter(value -> value.id().equals(id)).findFirst();
        }
        @Override public Optional<RaycastHit> raycast(Vector3 origin, Vector3 direction, double range, SelectionFilter filter) {
            lastRayDirection = direction;
            return Optional.of(new RaycastHit(b.position(), Optional.of(b), 2));
        }
        @Override public List<EntitySnapshot> select(Vector3 center, double radius, SelectionFilter filter) {
            selectCalls++;
            return List.of(b, a);
        }
    }
}
