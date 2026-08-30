package vectorregnum.core.vm2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vectorregnum.core.presentation.ExecutionEvent;
import vectorregnum.core.vm2.RuntimeValue.BooleanValue;
import vectorregnum.core.vm2.RuntimeValue.EntityValue;
import vectorregnum.core.vm2.RuntimeValue.ListValue;
import vectorregnum.core.vm2.RuntimeValue.NumberValue;
import vectorregnum.core.vm2.RuntimeValue.PointValue;
import vectorregnum.core.vm2.RuntimeValue.TextValue;
import vectorregnum.core.vm2.WorldAccess.CollisionTarget;
import vectorregnum.core.vm2.WorldAccess.EntitySnapshot;
import vectorregnum.core.vm2.WorldAccess.RaycastHit;
import vectorregnum.core.vm2.WorldAccess.SelectionFilter;

class Priority24VmTest {
    private static SourceLocation at(int index) {
        return new SourceLocation(index, index + 1, 1, "P24_" + index);
    }

    private static NumberValue number(double value) {
        return new NumberValue(value);
    }

    private static PointValue point(double x, double y, double z) {
        return new PointValue(new Vector3(x, y, z));
    }

    private static EntityValue entity(String id) {
        return new EntityValue(id);
    }

    @Test
    void namedVariablesStoreLoadAndFaultWithoutPartialMutation() {
        Program roundTrip = new Program(List.of(
                Instruction.push(number(7), at(0)),
                Instruction.storeVariable("answer", at(1)),
                Instruction.loadVariable("answer", at(2)),
                Instruction.halt(at(3))));
        SpellVm vm = new SpellVm(roundTrip, WorldAccess.EMPTY);
        assertEquals(TickResult.Status.HALTED, vm.tick().status());
        assertEquals(number(7), vm.variables().get("answer"));
        assertEquals(List.of(number(7)), vm.stackTopFirst());

        Program missing = new Program(List.of(Instruction.loadVariable("missing", at(10))));
        TickResult missingResult = new SpellVm(missing, WorldAccess.EMPTY).tick();
        assertEquals(VmFault.Code.VARIABLE_NOT_FOUND, missingResult.fault().orElseThrow().code());

        Program typeFault = new Program(List.of(
                Instruction.push(number(1), at(20)),
                Instruction.storeVariable("value", at(21)),
                Instruction.push(new BooleanValue(true), at(22)),
                Instruction.storeVariable("value", at(23))));
        SpellVm typed = new SpellVm(typeFault, WorldAccess.EMPTY);
        TickResult typedResult = typed.tick();
        assertEquals(VmFault.Code.VARIABLE_TYPE_MISMATCH, typedResult.fault().orElseThrow().code());
        assertEquals(number(1), typed.variables().get("value"));
        assertEquals(List.of(new BooleanValue(true)), typed.stackTopFirst());

        VmLimits capped = limits(1, 16, 1_024, 8, 32, 32, 128, 64, 256);
        Program variableCap = new Program(List.of(
                Instruction.push(number(1), at(30)),
                Instruction.storeVariable("first", at(31)),
                Instruction.push(number(2), at(32)),
                Instruction.storeVariable("second", at(33))));
        SpellVm cappedVm = new SpellVm(variableCap, WorldAccess.EMPTY, capped);
        TickResult capResult = cappedVm.tick();
        assertEquals(VmFault.Code.VARIABLE_LIMIT, capResult.fault().orElseThrow().code());
        assertEquals(number(1), cappedVm.variables().get("first"));
        assertEquals(List.of(number(2)), cappedVm.stackTopFirst());
    }

    @Test
    void iteratorIsStableOneItemPerTickAndExhaustionIsBounded() {
        SpellVm vm = new SpellVm(iteratorProgram(List.of(number(1), number(2), number(3)), 3),
                WorldAccess.EMPTY);
        List<TickResult> ticks = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        for (int index = 0; index < 12 && !vm.isTerminal(); index++) {
            TickResult tick = vm.tick();
            ticks.add(tick);
            assertTrue(tick.messages().size() <= 1,
                    "iterator body must expose at most one item per tick");
            outputs.addAll(tick.messages().stream()
                    .map(VmMessage.Output.class::cast)
                    .map(VmMessage.Output::text)
                    .toList());
        }
        assertTrue(vm.isTerminal());
        assertTrue(vm.fault().isEmpty(), () -> "unexpected iterator fault: " + vm.fault());
        assertEquals(List.of("1.0", "2.0", "3.0"), outputs);
        assertEquals(3, vm.iteratorSteps());
        assertEquals(List.of(TickResult.Status.WAITING, TickResult.Status.WAITING,
                TickResult.Status.WAITING, TickResult.Status.HALTED),
                ticks.stream().map(TickResult::status).toList());
    }

    @Test
    void emptyIteratorSkipsBodyAndGlobalStepCapFaultsAtBegin() {
        SpellVm empty = new SpellVm(iteratorProgram(List.of(), 3), WorldAccess.EMPTY);
        assertEquals(TickResult.Status.HALTED, empty.tick().status());
        assertEquals(List.of(), empty.allMessages());
        assertEquals(0, empty.iteratorSteps());

        VmLimits oneStep = limits(64, 16, 1, 8, 32, 32, 128, 64, 256);
        Program tooMany = iteratorProgram(List.of(number(1), number(2)), 2);
        SpellVm tooManyVm = new SpellVm(tooMany, WorldAccess.EMPTY, oneStep);
        TickResult result = tooManyVm.tick();
        assertEquals(VmFault.Code.ITERATOR_STEP_LIMIT, result.fault().orElseThrow().code());
        assertEquals(List.of(new ListValue(List.of(number(1), number(2)))),
                tooManyVm.stackTopFirst());
    }

    @Test
    void collisionCallsValidatedAdapterAndLeavesStackAtomicOnAdapterFailure() {
        CollisionWorld world = new CollisionWorld(false);
        Program program = new Program(List.of(
                Instruction.push(point(1, 2, 3), at(50)),
                Instruction.push(entity("target"), at(51)),
                Instruction.collision(12, 2, at(52)),
                Instruction.halt(at(53))));
        SpellVm vm = new SpellVm(program, world);
        assertEquals(TickResult.Status.HALTED, vm.tick().status());
        assertEquals(List.of(new BooleanValue(true)), vm.stackTopFirst());
        assertEquals(new CollisionTarget.PointTarget(new Vector3(1, 2, 3)), world.first);
        assertEquals(new CollisionTarget.EntityTarget("target"), world.second);
        assertEquals(12.0, world.range);

        CollisionWorld failingWorld = new CollisionWorld(true);
        SpellVm failing = new SpellVm(program, failingWorld);
        TickResult failed = failing.tick();
        assertEquals(VmFault.Code.WORLD_ADAPTER_ERROR, failed.fault().orElseThrow().code());
        assertEquals(List.of(entity("target"), point(1, 2, 3)), failing.stackTopFirst());
        assertEquals(1, failingWorld.calls);

        VmLimits oneSample = new VmLimits(64, 128, 10_000, 1_200, 1_024,
                128.0, 1, 64, 16, 1_024, 8, 32, 32, 128, 64, 256);
        Program overdeclared = new Program(List.of(
                Instruction.push(point(0, 0, 0), at(54)),
                Instruction.push(point(0, 0, 0), at(55)),
                Instruction.collision(12, 2, at(56))));
        CollisionWorld untouched = new CollisionWorld(false);
        SpellVm bounded = new SpellVm(overdeclared, untouched, oneSample);
        TickResult boundedResult = bounded.tick();
        assertEquals(VmFault.Code.INVALID_QUERY, boundedResult.fault().orElseThrow().code());
        assertEquals(List.of(point(0, 0, 0), point(0, 0, 0)), bounded.stackTopFirst());
        assertEquals(0, untouched.calls);

        Program unavailable = new Program(List.of(
                Instruction.push(point(0, 0, 0), at(57)),
                Instruction.push(point(0, 0, 0), at(58)),
                Instruction.collision(12, 1, at(59))));
        SpellVm unsupported = new SpellVm(unavailable, WorldAccess.EMPTY);
        TickResult unsupportedResult = unsupported.tick();
        assertEquals(VmFault.Code.WORLD_ADAPTER_ERROR,
                unsupportedResult.fault().orElseThrow().code());
        assertEquals(List.of(point(0, 0, 0), point(0, 0, 0)),
                unsupported.stackTopFirst());
    }

    @Test
    void watcherIsChangeOnlyAndDuplicateRegistrationIsIdempotent() {
        Program program = new Program(List.of(
                Instruction.push(number(1), at(60)),
                Instruction.storeVariable("watched", at(61)),
                Instruction.push(point(0, 0, 0), at(62)),
                Instruction.watchVariable("watched", 8, at(63)),
                Instruction.push(point(0, 0, 0), at(64)),
                Instruction.watchVariable("watched", 8, at(65)),
                Instruction.push(number(1), at(66)),
                Instruction.storeVariable("watched", at(67)),
                Instruction.push(number(2), at(68)),
                Instruction.storeVariable("watched", at(69)),
                Instruction.push(number(2), at(70)),
                Instruction.storeVariable("watched", at(71)),
                Instruction.halt(at(72))));
        VmLimits watcherCap = limits(64, 16, 1_024, 8, 32, 1, 128, 64, 256);
        SpellVm vm = new SpellVm(program, WorldAccess.EMPTY, watcherCap);
        TickResult result = vm.tick();
        assertEquals(TickResult.Status.HALTED, result.status());
        assertEquals(1, vm.signalsEmitted());
        assertEquals(1, vm.allMessages().size());
        VmMessage.Signal signal = assertInstanceOf(VmMessage.Signal.class,
                vm.allMessages().getFirst());
        assertEquals("watched", signal.channel());
        assertEquals(number(2), signal.payload());
    }

    @Test
    void explicitSignalsAndOutputsPreserveOrderAndRespectCaps() {
        Program ordered = new Program(List.of(
                Instruction.push(number(5), at(80)),
                Instruction.push(point(1, 0, 0), at(81)),
                Instruction.signal(8, at(82)),
                Instruction.push(new TextValue("finished"), at(83)),
                Instruction.push(point(2, 0, 0), at(84)),
                Instruction.output(8, at(85)),
                Instruction.halt(at(86))));
        SpellVm vm = new SpellVm(ordered, WorldAccess.EMPTY);
        TickResult result = vm.tick();
        assertEquals(TickResult.Status.HALTED, result.status());
        assertEquals(2, result.messages().size());
        assertEquals(List.of(0L, 1L), result.messages().stream()
                .map(VmMessage::sequence).toList());
        assertEquals(VmMessage.Signal.class, result.messages().get(0).getClass());
        assertEquals(VmMessage.Output.class, result.messages().get(1).getClass());
        assertEquals("finished", ((VmMessage.Output) result.messages().get(1)).text());
        assertEquals(0, ((VmMessage.Signal) result.messages().get(0)).branchId());
    }

    @Test
    void signalAndOutputCountAndSizeCapsFaultWithoutReturningPartialMessages() {
        VmLimits signalCap = limits(64, 16, 1_024, 8, 32, 32, 1, 64, 256);
        Program twoSignals = new Program(List.of(
                Instruction.push(number(1), at(90)), Instruction.push(point(0, 0, 0), at(91)),
                Instruction.signal(8, at(92)),
                Instruction.push(number(2), at(93)), Instruction.push(point(0, 0, 0), at(94)),
                Instruction.signal(8, at(95))));
        SpellVm signals = new SpellVm(twoSignals, WorldAccess.EMPTY, signalCap);
        TickResult signalResult = signals.tick();
        assertEquals(VmFault.Code.SIGNAL_LIMIT, signalResult.fault().orElseThrow().code());
        assertTrue(signalResult.messages().isEmpty());
        assertEquals(1, signals.signalsEmitted());

        VmLimits outputCap = limits(64, 16, 1_024, 8, 32, 32, 128, 1, 256);
        Program twoOutputs = new Program(List.of(
                Instruction.push(number(1), at(100)), Instruction.push(point(0, 0, 0), at(101)),
                Instruction.output(8, at(102)),
                Instruction.push(number(2), at(103)), Instruction.push(point(0, 0, 0), at(104)),
                Instruction.output(8, at(105))));
        SpellVm outputs = new SpellVm(twoOutputs, WorldAccess.EMPTY, outputCap);
        TickResult outputResult = outputs.tick();
        assertEquals(VmFault.Code.OUTPUT_LIMIT, outputResult.fault().orElseThrow().code());
        assertTrue(outputResult.messages().isEmpty());
        assertEquals(1, outputs.outputsEmitted());

        VmLimits sizeCap = limits(64, 16, 1_024, 8, 32, 32, 128, 64, 3);
        Program longOutput = new Program(List.of(
                Instruction.push(new TextValue("long"), at(110)),
                Instruction.push(point(0, 0, 0), at(111)),
                Instruction.output(8, at(112))));
        TickResult sizeResult = new SpellVm(longOutput, WorldAccess.EMPTY, sizeCap).tick();
        assertEquals(VmFault.Code.OUTPUT_TOO_LARGE, sizeResult.fault().orElseThrow().code());
        assertTrue(sizeResult.messages().isEmpty());
    }

    @Test
    void forkUsesStableBranchIdsAndSharedStackBeforeJoin() {
        List<ExecutionEvent> events = new ArrayList<>();
        SpellVm vm = new SpellVm(sharedStackBranchProgram(), WorldAccess.EMPTY,
                VmLimits.DEFAULT, events::add);
        List<List<Integer>> branchIdsByTick = new ArrayList<>();
        for (int index = 0; index < 32 && !vm.isTerminal(); index++) {
            long tick = vm.lifetimeTicks() + 1;
            vm.tick();
            branchIdsByTick.add(events.stream()
                    .filter(ExecutionEvent.StepExecuted.class::isInstance)
                    .map(ExecutionEvent.StepExecuted.class::cast)
                    .filter(step -> step.tick() == tick)
                    .map(ExecutionEvent.StepExecuted::branchId)
                    .toList());
        }
        assertTrue(vm.isTerminal(), "structured branch program did not terminate");
        assertTrue(vm.fault().isEmpty(), () -> "unexpected branch fault: " + vm.fault());
        assertEquals(List.of(number(42)), vm.stackTopFirst());
        assertEquals(List.of(), vm.activeBranchNames());
        assertEquals(3, vm.totalBranchesCreated());
        assertEquals(List.of(
                List.of(0, 0),
                List.of(0, 1),
                List.of(0, 1, 2),
                List.of(2),
                List.of(0, 0)), branchIdsByTick);
    }

    @Test
    void cancelIsIdempotentBranchCapIsHardAndUnjoinedHaltFaults() {
        SpellVm cancelled = new SpellVm(cancelProgram(), WorldAccess.EMPTY);
        for (int index = 0; index < 32 && !cancelled.isTerminal(); index++) cancelled.tick();
        assertTrue(cancelled.isTerminal(), "cancelled branch program did not terminate");
        assertTrue(cancelled.fault().isEmpty(), () -> "unexpected cancel fault: " + cancelled.fault());
        assertEquals(List.of(), cancelled.activeBranchNames());
        assertEquals(2, cancelled.totalBranchesCreated());
        assertEquals(List.of(), cancelled.stackTopFirst());

        VmLimits oneChild = new VmLimits(64, 128, 10_000, 1_200, 1_024, 128.0, 128,
                64, 16, 1_024, 2, 32, 32, 128, 64, 256);
        SpellVm capped = new SpellVm(sharedStackBranchProgram(), WorldAccess.EMPTY, oneChild);
        capped.tick();
        TickResult branchLimit = capped.tick();
        assertEquals(VmFault.Code.BRANCH_LIMIT, branchLimit.fault().orElseThrow().code());
        assertEquals(2, capped.totalBranchesCreated());

        SpellVm unjoined = new SpellVm(unjoinedHaltProgram(), WorldAccess.EMPTY);
        unjoined.tick();
        TickResult unjoinedResult = unjoined.tick();
        assertEquals(VmFault.Code.UNJOINED_BRANCH, unjoinedResult.fault().orElseThrow().code());
        assertEquals(at(1), unjoinedResult.fault().orElseThrow().source());
    }

    @Test
    void legacySevenArgumentVmLimitsConstructorRetainsPriority23Defaults() {
        VmLimits legacy = new VmLimits(10, 11, 12, 13, 14, 15, 16);
        assertEquals(10, legacy.maxStackDepth());
        assertEquals(11, legacy.maxInstructionsPerTick());
        assertEquals(12, legacy.maxTotalInstructions());
        assertEquals(13, legacy.maxLifetimeTicks());
        assertEquals(14, legacy.maxLoopIterations());
        assertEquals(15, legacy.maxPerceptionRange());
        assertEquals(16, legacy.maxSelectionResults());
        assertEquals(64, legacy.maxVariables());
        assertEquals(16, legacy.maxIterators());
        assertEquals(1_024, legacy.maxIteratorSteps());
        assertEquals(8, legacy.maxActiveBranches());
        assertEquals(32, legacy.maxTotalBranches());
        assertEquals(32, legacy.maxWatchers());
        assertEquals(128, legacy.maxSignals());
        assertEquals(64, legacy.maxOutputs());
        assertEquals(256, legacy.maxOutputChars());
    }

    private static Program iteratorProgram(List<RuntimeValue> values, int maximumSteps) {
        return new Program(List.of(
                Instruction.push(new ListValue(values), at(120)),
                Instruction.iteratorBegin("items", 5, maximumSteps, at(121)),
                Instruction.push(point(0, 0, 0), at(122)),
                Instruction.output(8, at(123)),
                Instruction.iteratorNext("items", 2, at(124)),
                Instruction.halt(at(125))));
    }

    private static Program sharedStackBranchProgram() {
        return new Program(List.of(
                Instruction.push(number(99), at(130)),
                Instruction.fork("consume", 5, 7, at(131)),
                Instruction.fork("produce", 7, 9, at(132)),
                Instruction.join(at(133)),
                Instruction.jump(9, at(134)),
                Instruction.pop(at(135)),
                Instruction.branchEnd(at(136)),
                Instruction.push(number(42), at(137)),
                Instruction.branchEnd(at(138)),
                Instruction.halt(at(139))));
    }

    private static Program cancelProgram() {
        return new Program(List.of(
                Instruction.fork("cancelled", 5, 7, at(140)),
                Instruction.cancelBranch("cancelled", at(141)),
                Instruction.cancelBranch("cancelled", at(142)),
                Instruction.join(at(143)),
                Instruction.jump(7, at(144)),
                Instruction.push(number(99), at(145)),
                Instruction.branchEnd(at(146)),
                Instruction.halt(at(147))));
    }

    private static Program unjoinedHaltProgram() {
        return new Program(List.of(
                Instruction.fork("unfinished", 4, 6, at(0)),
                Instruction.halt(at(1)),
                Instruction.join(at(2)),
                Instruction.jump(6, at(3)),
                Instruction.push(number(1), at(4)),
                Instruction.branchEnd(at(5)),
                Instruction.halt(at(6))));
    }

    private static VmLimits limits(int maxVariables, int maxIterators, int maxIteratorSteps,
            int maxActiveBranches, int maxTotalBranches, int maxWatchers, int maxSignals,
            int maxOutputs, int maxOutputChars) {
        return new VmLimits(64, 128, 10_000, 1_200, 1_024, 128.0, 128,
                maxVariables, maxIterators, maxIteratorSteps, maxActiveBranches,
                maxTotalBranches, maxWatchers, maxSignals, maxOutputs, maxOutputChars);
    }

    private static final class CollisionWorld implements WorldAccess {
        private final EntitySnapshot target = new EntitySnapshot("target", new Vector3(2, 2, 2),
                1, "mob", Set.of());
        private final boolean fail;
        private int calls;
        private CollisionTarget first;
        private CollisionTarget second;
        private double range;

        private CollisionWorld(boolean fail) {
            this.fail = fail;
        }

        @Override
        public Optional<EntitySnapshot> entity(String id) {
            return id.equals(target.id()) ? Optional.of(target) : Optional.empty();
        }

        @Override
        public Optional<RaycastHit> raycast(Vector3 origin, Vector3 direction,
                double maxDistance, SelectionFilter filter) {
            return Optional.empty();
        }

        @Override
        public List<EntitySnapshot> select(Vector3 center, double radius, SelectionFilter filter) {
            return List.of();
        }

        @Override
        public boolean collides(CollisionTarget first, CollisionTarget second, double maximumRange) {
            calls++;
            this.first = first;
            this.second = second;
            range = maximumRange;
            if (fail) throw new IllegalStateException("adapter rejected collision");
            return true;
        }
    }
}
