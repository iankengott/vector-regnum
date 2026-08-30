package vectorregnum.core.vm2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.presentation.ExecutionEvent;
import vectorregnum.core.presentation.ExecutionEventSink;
import vectorregnum.core.vm2.RuntimeValue.BooleanValue;
import vectorregnum.core.vm2.RuntimeValue.EntityValue;
import vectorregnum.core.vm2.RuntimeValue.ListValue;
import vectorregnum.core.vm2.RuntimeValue.NumberValue;
import vectorregnum.core.vm2.RuntimeValue.PointValue;
import vectorregnum.core.vm2.RuntimeValue.TextValue;
import vectorregnum.core.vm2.RuntimeValue.VectorValue;
import vectorregnum.core.vm2.WorldAccess.CollisionTarget;
import vectorregnum.core.vm2.WorldAccess.EntitySnapshot;
import vectorregnum.core.vm2.WorldAccess.RaycastHit;

/** Stateful, single-cast, tick-resumable VM. Only its owning server tick may call it. */
public final class SpellVm {
    private final Program program;
    private final WorldAccess world;
    private final VmLimits limits;
    private final ExecutionEventSink eventSink;
    /** One shared atomic LIFO stack for the main path and every logical branch. */
    private final Deque<RuntimeValue> stack = new ArrayDeque<>();
    private final Map<String, RuntimeValue> variables = new LinkedHashMap<>();
    private final Map<String, IteratorState> iterators = new LinkedHashMap<>();
    private final Map<String, Watcher> watchers = new LinkedHashMap<>();
    private final List<BranchState> branches = new ArrayList<>();
    private final Map<String, BranchState> namedBranches = new LinkedHashMap<>();
    private final Map<BranchLoop, Integer> loopPasses = new LinkedHashMap<>();
    private final List<WorldEffect> allEffects = new ArrayList<>();
    private final List<VmMessage> allMessages = new ArrayList<>();
    private int durationTicks = 1;
    private int nextBranchId = 1;
    private int totalBranchesCreated = 1;
    private int iteratorSteps;
    private int signalsEmitted;
    private int outputsEmitted;
    private int outputChars;
    private long totalInstructions;
    private long lifetimeTicks;
    private long eventSequence;
    private long messageSequence;
    private boolean halted;
    private boolean terminalEventPublished;
    private boolean started;
    private VmFault fault;
    private BranchState executingBranch;

    public SpellVm(Program program, WorldAccess world, VmLimits limits,
            ExecutionEventSink eventSink) {
        this.program = Objects.requireNonNull(program, "program");
        this.world = Objects.requireNonNull(world, "world");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
        branches.add(new BranchState(0, "main", 0, program.instructions().size(), 0));
    }

    public SpellVm(Program program, WorldAccess world, VmLimits limits) {
        this(program, world, limits, ExecutionEventSink.NOOP);
    }

    public SpellVm(Program program, WorldAccess world) {
        this(program, world, VmLimits.DEFAULT, ExecutionEventSink.NOOP);
    }

    public SpellVm(Program program, WorldAccess world, ExecutionEventSink eventSink) {
        this(program, world, VmLimits.DEFAULT, eventSink);
    }

    public TickResult tick() {
        if (fault != null) return result(TickResult.Status.FAULTED, 0, List.of(), List.of());
        if (halted) return result(TickResult.Status.HALTED, 0, List.of(), List.of());
        if (!started) {
            started = true;
            publish(new ExecutionEvent.Started(nextEventSequence(), 0));
        }
        lifetimeTicks++;
        if (lifetimeTicks > limits.maxLifetimeTicks()) {
            fail(VmFault.Code.LIFETIME_TICK_LIMIT, "spell exceeded lifetime tick limit",
                    currentSource());
            publishFault();
            return result(TickResult.Status.FAULTED, 0, List.of(), List.of());
        }

        List<WorldEffect> effects = new ArrayList<>();
        List<VmMessage> messages = new ArrayList<>();
        try {
            return branches.size() == 1
                    ? tickSingle(mainBranch(), effects, messages)
                    : tickParallel(effects, messages);
        } catch (ExecutionFault executionFault) {
            fault = executionFault.fault;
            publishFault();
            // Gameplay messages are staged. A later same-tick fault discards them atomically.
            return result(TickResult.Status.FAULTED, executionFault.executed,
                    effects, List.of());
        } catch (RuntimeException adapterFailure) {
            fail(VmFault.Code.WORLD_ADAPTER_ERROR,
                    "world adapter rejected operation: "
                            + adapterFailure.getClass().getSimpleName(), currentSource());
            publishFault();
            return result(TickResult.Status.FAULTED, 0, effects, List.of());
        } finally {
            executingBranch = null;
        }
    }

    private TickResult tickSingle(BranchState branch, List<WorldEffect> effects,
            List<VmMessage> messages) {
        if (prepare(branch)) return result(TickResult.Status.WAITING, 0, effects, messages);
        int executed = 0;
        while (executed < limits.maxInstructionsPerTick()) {
            if (branch.instructionPointer >= branch.endExclusive
                    || branch.instructionPointer >= program.instructions().size()) {
                halted = true;
                publishHalted(currentSource());
                return result(TickResult.Status.HALTED, executed, effects, messages);
            }
            checkTotalInstructionLimit(branch, executed);
            Instruction instruction = program.instructions().get(branch.instructionPointer);
            int executedPointer = branch.instructionPointer;
            executingBranch = branch;
            totalInstructions++;
            executed++;
            ExecutionControl control = execute(branch, instruction, effects, messages);
            publishStep(branch, executedPointer, instruction);
            if (control == ExecutionControl.HALTED) {
                publishHalted(instruction.source());
                return result(TickResult.Status.HALTED, executed, effects, messages);
            }
            if (control.status != null || branches.size() > 1) {
                TickResult.Status status = control.status != null
                        ? control.status : TickResult.Status.RUNNING;
                return result(status, executed, effects, messages);
            }
        }
        return result(TickResult.Status.BUDGET_YIELD, executed, effects, messages);
    }

    private TickResult tickParallel(List<WorldEffect> effects, List<VmMessage> messages) {
        int executed = 0;
        boolean waiting = false;
        List<BranchState> snapshot = branches.stream()
                .sorted(Comparator.comparingInt(value -> value.id)).toList();
        for (BranchState branch : snapshot) {
            if (!branches.contains(branch)) continue;
            if (prepare(branch)) {
                waiting = true;
                continue;
            }
            if (branch.instructionPointer >= branch.endExclusive) {
                if (branch.id == 0) {
                    if (!namedBranches.isEmpty()) {
                        throw authored(VmFault.Code.UNJOINED_BRANCH,
                                "main path ended before joining active branches", branch, executed);
                    }
                    halted = true;
                    publishHalted(currentSource());
                    return result(TickResult.Status.HALTED, executed, effects, messages);
                }
                finishBranch(branch);
                continue;
            }
            if (executed >= limits.maxInstructionsPerTick()) {
                return result(TickResult.Status.BUDGET_YIELD, executed, effects, messages);
            }
            checkTotalInstructionLimit(branch, executed);
            Instruction instruction = program.instructions().get(branch.instructionPointer);
            int executedPointer = branch.instructionPointer;
            executingBranch = branch;
            totalInstructions++;
            executed++;
            ExecutionControl control = execute(branch, instruction, effects, messages);
            publishStep(branch, executedPointer, instruction);
            if (control == ExecutionControl.HALTED) {
                publishHalted(instruction.source());
                return result(TickResult.Status.HALTED, executed, effects, messages);
            }
            waiting |= control == ExecutionControl.WAITING;
        }
        return result(executed == 0 && waiting ? TickResult.Status.WAITING
                : TickResult.Status.RUNNING, executed, effects, messages);
    }

    private boolean prepare(BranchState branch) {
        executingBranch = branch;
        if (branch.waitingForJoin) {
            if (!namedBranches.isEmpty()) return true;
            branch.waitingForJoin = false;
            branch.instructionPointer++;
        }
        if (branch.waitingTicks > 0) {
            branch.waitingTicks--;
            return true;
        }
        return false;
    }

    private void checkTotalInstructionLimit(BranchState branch, int executed) {
        if (totalInstructions >= limits.maxTotalInstructions()) {
            throw authored(VmFault.Code.TOTAL_INSTRUCTION_LIMIT,
                    "spell exceeded total instruction limit", branch, executed);
        }
    }

    private ExecutionControl execute(BranchState branch, Instruction instruction,
            List<WorldEffect> effects, List<VmMessage> messages) {
        switch (instruction.opcode()) {
            case PUSH -> { push(instruction.literal()); branch.instructionPointer++; }
            case POP -> { requireDepth(1); stack.pop(); branch.instructionPointer++; }
            case DUP -> { RuntimeValue value = peek(0); requireCapacity(1); stack.push(value); branch.instructionPointer++; }
            case ADD -> { add(); branch.instructionPointer++; }
            case SUBTRACT -> { subtract(); branch.instructionPointer++; }
            case MULTIPLY -> { multiply(); branch.instructionPointer++; }
            case DIVIDE -> { divide(); branch.instructionPointer++; }
            case EQUALS -> { RuntimeValue right = peek(0), left = peek(1); replace(2,
                    new BooleanValue(left.equals(right))); branch.instructionPointer++; }
            case LESS_THAN -> { compare(true); branch.instructionPointer++; }
            case GREATER_THAN -> { compare(false); branch.instructionPointer++; }
            case NOT -> { BooleanValue value = expect(BooleanValue.class, peek(0));
                replace(1, new BooleanValue(!value.value())); branch.instructionPointer++; }
            case AND -> { logic(true); branch.instructionPointer++; }
            case OR -> { logic(false); branch.instructionPointer++; }
            case JUMP -> branch.instructionPointer = instruction.argument();
            case JUMP_IF_FALSE -> {
                boolean condition = expect(BooleanValue.class, peek(0)).value();
                stack.pop();
                branch.instructionPointer = condition
                        ? branch.instructionPointer + 1 : instruction.argument();
            }
            case LOOP -> loop(branch, instruction);
            case DELAY -> {
                branch.instructionPointer++;
                if (instruction.argument() > 0) {
                    branch.waitingTicks = instruction.argument() - 1;
                    publish(new ExecutionEvent.DelayStarted(nextEventSequence(), lifetimeTicks,
                            branch.instructionPointer - 1, instruction.source(),
                            instruction.argument()));
                    return ExecutionControl.WAITING;
                }
            }
            case SET_DURATION -> { durationTicks = instruction.argument(); branch.instructionPointer++; }
            case SELECT_RADIUS -> select(branch, instruction);
            case RAYCAST_ENTITIES -> raycast(branch, instruction);
            case IMPULSE -> emitImpulse(branch, effects);
            case ACCELERATION -> emitAcceleration(branch, effects);
            case DAMPING -> emitDamping(branch, effects);
            case FOLLOW_PATH -> emitPath(branch, effects);
            case MOVE_TOWARD -> emitMove(branch, effects);
            case KEEP_DISTANCE -> emitKeepDistance(branch, effects);
            case SEMANTIC -> {
                emit(new WorldEffect.SemanticStep(instruction.semantic()), effects);
                branch.instructionPointer++;
            }
            case HALT -> {
                if (!namedBranches.isEmpty()) {
                    throw authored(VmFault.Code.UNJOINED_BRANCH,
                            "HALT reached with active branches", branch, 0);
                }
                branch.instructionPointer++;
                halted = true;
                return ExecutionControl.HALTED;
            }
            case STORE_VARIABLE -> storeVariable(branch, instruction, messages);
            case LOAD_VARIABLE -> loadVariable(branch, instruction);
            case ITERATOR_BEGIN -> {
                if (iteratorBegin(branch, instruction)) return ExecutionControl.WAITING;
            }
            case ITERATOR_NEXT -> {
                if (iteratorNext(branch, instruction)) return ExecutionControl.WAITING;
            }
            case COLLISION -> collision(branch, instruction);
            case WATCH_VARIABLE -> watchVariable(branch, instruction);
            case SIGNAL -> signal(branch, instruction, messages);
            case OUTPUT -> output(branch, instruction, messages);
            case FORK -> {
                fork(branch, instruction);
                return ExecutionControl.RUNNING;
            }
            case JOIN -> {
                if (!namedBranches.isEmpty()) {
                    branch.waitingForJoin = true;
                    return ExecutionControl.WAITING;
                }
                branch.instructionPointer++;
            }
            case CANCEL_BRANCH -> cancelBranch(branch, instruction);
            case BRANCH_END -> {
                if (branch.id == 0) {
                    throw authored(VmFault.Code.UNJOINED_BRANCH,
                            "main path cannot execute BRANCH_END", branch, 0);
                }
                finishBranch(branch);
                return ExecutionControl.RUNNING;
            }
        }
        return ExecutionControl.CONTINUE;
    }

    private void add() {
        RuntimeValue right = peek(0), left = peek(1), result;
        if (left instanceof NumberValue a && right instanceof NumberValue b) {
            result = number(a.value() + b.value());
        } else if (left instanceof VectorValue a && right instanceof VectorValue b) {
            result = new VectorValue(a.value().plus(b.value()));
        } else if (left instanceof PointValue a && right instanceof VectorValue b) {
            result = new PointValue(a.value().plus(b.value()));
        } else {
            throw type("ADD expects number+number, vector+vector, or point+vector", left, right);
        }
        replace(2, result);
    }

    private void subtract() {
        RuntimeValue right = peek(0), left = peek(1), result;
        if (left instanceof NumberValue a && right instanceof NumberValue b) {
            result = number(a.value() - b.value());
        } else if (left instanceof VectorValue a && right instanceof VectorValue b) {
            result = new VectorValue(a.value().minus(b.value()));
        } else if (left instanceof PointValue a && right instanceof PointValue b) {
            result = new VectorValue(a.value().minus(b.value()));
        } else if (left instanceof PointValue a && right instanceof VectorValue b) {
            result = new PointValue(a.value().minus(b.value()));
        } else {
            throw type("SUBTRACT operands are incompatible", left, right);
        }
        replace(2, result);
    }

    private void multiply() {
        RuntimeValue right = peek(0), left = peek(1), result;
        if (left instanceof NumberValue a && right instanceof NumberValue b) {
            result = number(a.value() * b.value());
        } else if (left instanceof VectorValue vector && right instanceof NumberValue scalar) {
            result = new VectorValue(vector.value().scaled(scalar.value()));
        } else if (left instanceof NumberValue scalar && right instanceof VectorValue vector) {
            result = new VectorValue(vector.value().scaled(scalar.value()));
        } else {
            throw type("MULTIPLY expects numbers or vector and scalar", left, right);
        }
        replace(2, result);
    }

    private void divide() {
        RuntimeValue right = peek(0), left = peek(1);
        double divisor = expect(NumberValue.class, right).value();
        if (Math.abs(divisor) <= 1.0e-12) {
            throw authored(VmFault.Code.DIVIDE_BY_ZERO, "division by zero");
        }
        RuntimeValue result;
        if (left instanceof NumberValue value) result = number(value.value() / divisor);
        else if (left instanceof VectorValue value) {
            result = new VectorValue(value.value().scaled(1.0 / divisor));
        } else throw type("DIVIDE expects number/number or vector/number", left, right);
        replace(2, result);
    }

    private void compare(boolean less) {
        double right = expect(NumberValue.class, peek(0)).value();
        double left = expect(NumberValue.class, peek(1)).value();
        replace(2, new BooleanValue(less ? left < right : left > right));
    }

    private void logic(boolean and) {
        boolean right = expect(BooleanValue.class, peek(0)).value();
        boolean left = expect(BooleanValue.class, peek(1)).value();
        replace(2, new BooleanValue(and ? left && right : left || right));
    }

    private void loop(BranchState branch, Instruction instruction) {
        if (instruction.secondArgument() > limits.maxLoopIterations()) {
            throw authored(VmFault.Code.LOOP_LIMIT,
                    "loop declares " + instruction.secondArgument() + " iterations; limit is "
                            + limits.maxLoopIterations());
        }
        BranchLoop key = new BranchLoop(branch.id, branch.instructionPointer);
        int pass = loopPasses.merge(key, 1, Integer::sum);
        if (pass < instruction.secondArgument()) branch.instructionPointer = instruction.argument();
        else { loopPasses.remove(key); branch.instructionPointer++; }
    }

    private void select(BranchState branch, Instruction instruction) {
        double radius = positiveRange(peek(0));
        Vector3 center = point(peek(1));
        List<RuntimeValue> found = world.select(center, radius, instruction.filter()).stream()
                .sorted(Comparator.comparing(EntitySnapshot::id))
                .limit((long) limits.maxSelectionResults() + 1)
                .map(snapshot -> (RuntimeValue) new EntityValue(snapshot.id())).toList();
        if (found.size() > limits.maxSelectionResults()) {
            throw authored(VmFault.Code.INVALID_QUERY, "selection result limit exceeded");
        }
        replace(2, new ListValue(found));
        publishValues(branch, instruction, found);
        branch.instructionPointer++;
    }

    private void raycast(BranchState branch, Instruction instruction) {
        double range = positiveRange(peek(0));
        Vector3 direction = vector(peek(1));
        Vector3 origin = point(peek(2));
        if (direction.length() <= 1.0e-12) {
            throw authored(VmFault.Code.INVALID_QUERY, "raycast direction is zero");
        }
        Optional<RaycastHit> hit = world.raycast(origin, direction.normalized(), range,
                instruction.filter());
        List<RuntimeValue> values = hit.flatMap(RaycastHit::entity)
                .map(entity -> List.<RuntimeValue>of(new EntityValue(entity.id())))
                .orElseGet(List::of);
        replace(3, new ListValue(values));
        publishValues(branch, instruction, values);
        branch.instructionPointer++;
    }

    private void emitImpulse(BranchState branch, List<WorldEffect> effects) {
        Vector3 value = vector(peek(0));
        String entity = entityId(peek(1));
        replace(2);
        emit(new WorldEffect.Impulse(entity, value, durationTicks), effects);
        branch.instructionPointer++;
    }

    private void emitAcceleration(BranchState branch, List<WorldEffect> effects) {
        Vector3 value = vector(peek(0));
        String entity = entityId(peek(1));
        replace(2);
        emit(new WorldEffect.Acceleration(entity, value, durationTicks), effects);
        branch.instructionPointer++;
    }

    private void emitDamping(BranchState branch, List<WorldEffect> effects) {
        double factor = expect(NumberValue.class, peek(0)).value();
        String entity = entityId(peek(1));
        if (factor < 0 || factor > 1) {
            throw authored(VmFault.Code.TYPE_MISMATCH, "damping must be 0..1");
        }
        replace(2);
        emit(new WorldEffect.Damping(entity, factor, durationTicks), effects);
        branch.instructionPointer++;
    }

    private void emitPath(BranchState branch, List<WorldEffect> effects) {
        double speed = positive(peek(0), "path speed");
        ListValue path = expect(ListValue.class, peek(1));
        String entity = entityId(peek(2));
        List<Vector3> points = path.values().stream().map(value -> {
            if (!(value instanceof PointValue point)) {
                throw authored(VmFault.Code.INVALID_PATH, "path contains non-point value");
            }
            return point.value();
        }).toList();
        if (points.isEmpty()) throw authored(VmFault.Code.INVALID_PATH, "path is empty");
        replace(3);
        emit(new WorldEffect.FollowPath(entity, points, speed, durationTicks), effects);
        branch.instructionPointer++;
    }

    private void emitMove(BranchState branch, List<WorldEffect> effects) {
        double speed = positive(peek(0), "move speed");
        Vector3 target = point(peek(1));
        String entity = entityId(peek(2));
        replace(3);
        emit(new WorldEffect.MoveToward(entity, target, speed, durationTicks), effects);
        branch.instructionPointer++;
    }

    private void emitKeepDistance(BranchState branch, List<WorldEffect> effects) {
        double distance = expect(NumberValue.class, peek(0)).value();
        String target = entityId(peek(1));
        String entity = entityId(peek(2));
        if (distance < 0) {
            throw authored(VmFault.Code.TYPE_MISMATCH, "distance cannot be negative");
        }
        replace(3);
        emit(new WorldEffect.KeepDistance(entity, target, distance, durationTicks), effects);
        branch.instructionPointer++;
    }

    private void storeVariable(BranchState branch, Instruction instruction,
            List<VmMessage> messages) {
        String name = named(instruction);
        RuntimeValue value = peek(0);
        RuntimeValue previous = variables.get(name);
        if (previous == null && variables.size() >= limits.maxVariables()) {
            throw authored(VmFault.Code.VARIABLE_LIMIT, "variable limit exceeded");
        }
        if (previous != null && previous.type() != value.type()) {
            throw authored(VmFault.Code.VARIABLE_TYPE_MISMATCH,
                    "variable " + name + " cannot change from " + previous.type()
                            + " to " + value.type());
        }
        Watcher watcher = watchers.get(name);
        boolean changed = !Objects.equals(previous, value);
        if (changed && watcher != null) requireSignalCapacity(1);
        stack.pop();
        variables.put(name, value);
        if (changed && watcher != null) {
            emitSignal(branch, name, watcher.point, value, watcher.declaredRange, messages);
        }
        branch.instructionPointer++;
    }

    private void loadVariable(BranchState branch, Instruction instruction) {
        String name = named(instruction);
        RuntimeValue value = variables.get(name);
        if (value == null) {
            throw authored(VmFault.Code.VARIABLE_NOT_FOUND, "variable not found: " + name);
        }
        push(value);
        branch.instructionPointer++;
    }

    /** Returns true only when the first item was exposed and the tick must yield. */
    private boolean iteratorBegin(BranchState branch, Instruction instruction) {
        AdvancedOperand.IteratorSpec spec = iterator(instruction);
        ListValue list = expect(ListValue.class, peek(0));
        if (spec.maximumSteps() > limits.maxIteratorSteps()
                || list.values().size() > spec.maximumSteps()) {
            throw authored(VmFault.Code.ITERATOR_STEP_LIMIT,
                    "iterator " + spec.name() + " exceeds declared/global step limit");
        }
        if (iterators.containsKey(spec.name())) {
            throw authored(VmFault.Code.ITERATOR_LIMIT,
                    "iterator already active: " + spec.name());
        }
        if (!list.values().isEmpty() && iterators.size() >= limits.maxIterators()) {
            throw authored(VmFault.Code.ITERATOR_LIMIT, "iterator limit exceeded");
        }
        if (list.values().isEmpty()) {
            stack.pop();
            branch.instructionPointer = spec.target();
            return false;
        }
        requireIteratorStepCapacity();
        // The list is replaced by its first item, so stack capacity cannot grow.
        stack.pop();
        IteratorState state = new IteratorState(spec.name(), branch.id,
                list.values(), 0, spec.maximumSteps());
        iterators.put(spec.name(), state);
        iteratorSteps++;
        push(list.values().getFirst());
        branch.instructionPointer++;
        return true;
    }

    /** Returns true only when a next item was exposed and the tick must yield. */
    private boolean iteratorNext(BranchState branch, Instruction instruction) {
        AdvancedOperand.IteratorSpec spec = iterator(instruction);
        IteratorState state = iterators.get(spec.name());
        if (state == null || state.ownerBranchId != branch.id) {
            throw authored(VmFault.Code.ITERATOR_NOT_FOUND,
                    "iterator not active in this branch: " + spec.name());
        }
        if (state.index + 1 >= state.values.size()) {
            iterators.remove(spec.name());
            branch.instructionPointer++;
            return false;
        }
        if (state.index + 1 >= state.maximumSteps) {
            throw authored(VmFault.Code.ITERATOR_STEP_LIMIT,
                    "iterator declared step limit exceeded: " + spec.name());
        }
        requireIteratorStepCapacity();
        requireCapacity(1);
        state.index++;
        iteratorSteps++;
        stack.push(state.values.get(state.index));
        branch.instructionPointer = spec.target();
        return true;
    }

    private void collision(BranchState branch, Instruction instruction) {
        AdvancedOperand.RangeSpec spec = range(instruction);
        validateDeclaredRange(spec.declaredRange());
        if (spec.samples() > limits.maxSelectionResults()) {
            throw authored(VmFault.Code.INVALID_QUERY,
                    "collision samples exceed " + limits.maxSelectionResults());
        }
        CollisionTarget right = collisionTarget(peek(0));
        CollisionTarget left = collisionTarget(peek(1));
        boolean result = world.collides(left, right, spec.declaredRange());
        replace(2, new BooleanValue(result));
        publishValues(branch, instruction, List.of(new BooleanValue(result)));
        branch.instructionPointer++;
    }

    private void watchVariable(BranchState branch, Instruction instruction) {
        AdvancedOperand.WatchSpec spec = watch(instruction);
        validateDeclaredRange(spec.declaredRange());
        if (!variables.containsKey(spec.variable())) {
            throw authored(VmFault.Code.VARIABLE_NOT_FOUND,
                    "cannot watch missing variable: " + spec.variable());
        }
        Vector3 point = point(peek(0));
        if (!watchers.containsKey(spec.variable()) && watchers.size() >= limits.maxWatchers()) {
            throw authored(VmFault.Code.WATCHER_LIMIT, "watcher limit exceeded");
        }
        stack.pop();
        watchers.put(spec.variable(), new Watcher(spec.variable(), point,
                spec.declaredRange(), branch.id));
        branch.instructionPointer++;
    }

    private void signal(BranchState branch, Instruction instruction, List<VmMessage> messages) {
        AdvancedOperand.RangeSpec spec = range(instruction);
        validateDeclaredRange(spec.declaredRange());
        Vector3 point = point(peek(0));
        RuntimeValue payload = peek(1);
        requireSignalCapacity(1);
        replace(2);
        emitSignal(branch, "signal", point, payload, spec.declaredRange(), messages);
        branch.instructionPointer++;
    }

    private void output(BranchState branch, Instruction instruction, List<VmMessage> messages) {
        AdvancedOperand.RangeSpec spec = range(instruction);
        validateDeclaredRange(spec.declaredRange());
        Vector3 point = point(peek(0));
        RuntimeValue value = peek(1);
        String text = display(value);
        int effectiveCharacterLimit = Math.min(limits.maxOutputChars(),
                RuntimeValue.MAX_TEXT_CHARS);
        if ((long) outputChars + text.length() > effectiveCharacterLimit) {
            throw authored(VmFault.Code.OUTPUT_TOO_LARGE,
                    "runtime output exceeds " + effectiveCharacterLimit + " characters");
        }
        if (outputsEmitted >= limits.maxOutputs()) {
            throw authored(VmFault.Code.OUTPUT_LIMIT, "output limit exceeded");
        }
        replace(2);
        VmMessage.Output output = new VmMessage.Output(messageSequence++, lifetimeTicks,
                branch.id, point, text, spec.declaredRange());
        outputsEmitted++;
        outputChars += text.length();
        messages.add(output);
        branch.instructionPointer++;
    }

    private void fork(BranchState parent, Instruction instruction) {
        AdvancedOperand.ForkSpec spec = fork(instruction);
        if (namedBranches.containsKey(spec.name())) {
            throw authored(VmFault.Code.DUPLICATE_BRANCH,
                    "branch already active: " + spec.name());
        }
        if (branches.size() >= limits.maxActiveBranches()
                || totalBranchesCreated >= limits.maxTotalBranches()) {
            throw authored(VmFault.Code.BRANCH_LIMIT, "branch limit exceeded");
        }
        BranchState child = new BranchState(nextBranchId++, spec.name(), spec.start(),
                spec.endExclusive(), lifetimeTicks);
        totalBranchesCreated++;
        branches.add(child);
        namedBranches.put(spec.name(), child);
        parent.instructionPointer++;
    }

    private void cancelBranch(BranchState branch, Instruction instruction) {
        BranchState target = namedBranches.get(named(instruction));
        if (target != null) finishBranch(target);
        branch.instructionPointer++;
    }

    private void finishBranch(BranchState branch) {
        branches.remove(branch);
        namedBranches.remove(branch.name, branch);
        iterators.entrySet().removeIf(entry -> entry.getValue().ownerBranchId == branch.id);
        loopPasses.keySet().removeIf(key -> key.branchId == branch.id);
    }

    private void emitSignal(BranchState branch, String channel, Vector3 point,
            RuntimeValue payload, double declaredRange, List<VmMessage> messages) {
        VmMessage.Signal signal = new VmMessage.Signal(messageSequence++, lifetimeTicks,
                branch.id, channel, point, payload, declaredRange);
        signalsEmitted++;
        messages.add(signal);
    }

    private void requireSignalCapacity(int additional) {
        if ((long) signalsEmitted + additional > limits.maxSignals()) {
            throw authored(VmFault.Code.SIGNAL_LIMIT, "signal limit exceeded");
        }
    }

    private void requireIteratorStepCapacity() {
        if (iteratorSteps >= limits.maxIteratorSteps()) {
            throw authored(VmFault.Code.ITERATOR_STEP_LIMIT,
                    "global iterator step limit exceeded");
        }
    }

    private CollisionTarget collisionTarget(RuntimeValue value) {
        if (value instanceof EntityValue entity) {
            if (world.entity(entity.id()).isEmpty()) {
                throw authored(VmFault.Code.ENTITY_NOT_FOUND,
                        "entity not found: " + entity.id());
            }
            return new CollisionTarget.EntityTarget(entity.id());
        }
        if (value instanceof PointValue point) return new CollisionTarget.PointTarget(point.value());
        throw authored(VmFault.Code.TYPE_MISMATCH,
                "COLLISION expects entity or point but found " + value.type());
    }

    private void validateDeclaredRange(double value) {
        if (value > limits.maxPerceptionRange()) {
            throw authored(VmFault.Code.INVALID_QUERY,
                    "declared range exceeds " + limits.maxPerceptionRange());
        }
    }

    private double positiveRange(RuntimeValue value) {
        double range = expect(NumberValue.class, value).value();
        if (range < 0 || range > limits.maxPerceptionRange()) {
            throw authored(VmFault.Code.INVALID_QUERY,
                    "range must be 0.." + limits.maxPerceptionRange());
        }
        return range;
    }

    private double positive(RuntimeValue value, String name) {
        double result = expect(NumberValue.class, value).value();
        if (result <= 0) throw authored(VmFault.Code.TYPE_MISMATCH, name + " must be positive");
        return result;
    }

    private String entityId(RuntimeValue value) {
        String id = expect(EntityValue.class, value).id();
        if (world.entity(id).isEmpty()) {
            throw authored(VmFault.Code.ENTITY_NOT_FOUND, "entity not found: " + id);
        }
        return id;
    }

    private Vector3 vector(RuntimeValue value) { return expect(VectorValue.class, value).value(); }
    private Vector3 point(RuntimeValue value) { return expect(PointValue.class, value).value(); }

    private String display(RuntimeValue value) {
        return switch (value) {
            case NumberValue number -> Double.toString(number.value());
            case BooleanValue bool -> Boolean.toString(bool.value());
            case PointValue point -> "point" + point.value();
            case VectorValue vector -> "vector" + vector.value();
            case EntityValue entity -> entity.id();
            case TextValue text -> text.value();
            case ListValue list -> list.values().stream().map(this::display)
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        };
    }

    private void emit(WorldEffect effect, List<WorldEffect> emitted) {
        emitted.add(effect);
        allEffects.add(effect);
        publish(new ExecutionEvent.WorldEffectEmitted(nextEventSequence(), lifetimeTicks,
                currentPointer(), currentSource(), effect));
    }

    private void publishValues(BranchState branch, Instruction instruction,
            List<RuntimeValue> values) {
        publish(new ExecutionEvent.ValuesResolved(nextEventSequence(), lifetimeTicks,
                branch.instructionPointer, instruction.opcode(), instruction.source(), values));
    }

    private void publishStep(BranchState branch, int executedPointer, Instruction instruction) {
        publish(new ExecutionEvent.StepExecuted(nextEventSequence(), lifetimeTicks,
                branch.id, executedPointer, branch.instructionPointer,
                instruction.opcode(), instruction.source()));
    }

    private NumberValue number(double value) {
        if (!Double.isFinite(value)) {
            throw authored(VmFault.Code.NUMERIC_OVERFLOW, "numeric result is not finite");
        }
        return new NumberValue(value);
    }

    private void push(RuntimeValue value) {
        requireCapacity(1);
        stack.push(Objects.requireNonNull(value));
    }

    private void requireCapacity(int additional) {
        if ((long) stack.size() + additional > limits.maxStackDepth()) {
            throw authored(VmFault.Code.STACK_OVERFLOW, "stack depth limit exceeded");
        }
    }

    private RuntimeValue peek(int depth) {
        requireDepth(depth + 1);
        int index = 0;
        for (RuntimeValue value : stack) {
            if (index++ == depth) return value;
        }
        throw new AssertionError("validated stack depth disappeared");
    }

    private void requireDepth(int count) {
        if (stack.size() < count) {
            throw authored(VmFault.Code.STACK_UNDERFLOW,
                    "stack needs " + count + " value(s), found " + stack.size());
        }
    }

    private void replace(int consumed, RuntimeValue... produced) {
        requireDepth(consumed);
        long resulting = (long) stack.size() - consumed + produced.length;
        if (resulting > limits.maxStackDepth()) {
            throw authored(VmFault.Code.STACK_OVERFLOW, "stack depth limit exceeded");
        }
        for (int index = 0; index < consumed; index++) stack.pop();
        for (int index = produced.length - 1; index >= 0; index--) {
            stack.push(Objects.requireNonNull(produced[index]));
        }
    }

    private <T extends RuntimeValue> T expect(Class<T> expected, RuntimeValue actual) {
        if (!expected.isInstance(actual)) {
            throw authored(VmFault.Code.TYPE_MISMATCH,
                    "expected " + expected.getSimpleName() + " but found " + actual.type());
        }
        return expected.cast(actual);
    }

    private ExecutionFault type(String message, RuntimeValue left, RuntimeValue right) {
        return authored(VmFault.Code.TYPE_MISMATCH,
                message + " (found " + left.type() + ", " + right.type() + ")");
    }

    private ExecutionFault authored(VmFault.Code code, String message) {
        return authored(code, message, executingBranch == null ? mainBranch() : executingBranch, 0);
    }

    private ExecutionFault authored(VmFault.Code code, String message,
            BranchState branch, int executed) {
        int pointer = Math.min(branch.instructionPointer,
                Math.max(0, program.instructions().size() - 1));
        SourceLocation source = program.instructions().isEmpty()
                ? SourceLocation.at(0, "END") : program.instructions().get(pointer).source();
        return new ExecutionFault(new VmFault(code, message, source, pointer), executed);
    }

    private void fail(VmFault.Code code, String message, SourceLocation source) {
        fault = new VmFault(code, message, source, Math.max(0, currentPointer()));
    }

    private int currentPointer() {
        BranchState branch = executingBranch == null ? mainBranch() : executingBranch;
        return Math.min(branch.instructionPointer, Math.max(0, program.instructions().size()));
    }

    private SourceLocation currentSource() {
        if (program.instructions().isEmpty()) return SourceLocation.at(0, "END");
        return program.instructions().get(Math.min(currentPointer(),
                program.instructions().size() - 1)).source();
    }

    private TickResult result(TickResult.Status status, int executed,
            List<WorldEffect> effects, List<VmMessage> messages) {
        if (status != TickResult.Status.FAULTED && !messages.isEmpty()) {
            allMessages.addAll(messages);
        }
        return new TickResult(status, mainBranch().instructionPointer, executed,
                effects, messages, Optional.ofNullable(fault));
    }

    private void publishHalted(SourceLocation source) {
        if (terminalEventPublished) return;
        terminalEventPublished = true;
        publish(new ExecutionEvent.Halted(nextEventSequence(), lifetimeTicks,
                mainBranch().instructionPointer, source));
    }

    private void publishFault() {
        if (terminalEventPublished || fault == null) return;
        terminalEventPublished = true;
        publish(new ExecutionEvent.Faulted(nextEventSequence(), lifetimeTicks, fault));
    }

    private long nextEventSequence() { return eventSequence++; }

    private void publish(ExecutionEvent event) {
        try {
            eventSink.accept(event);
        } catch (RuntimeException ignored) {
            // Presentation is non-authoritative and must never change VM execution.
        }
    }

    private BranchState mainBranch() {
        return branches.stream().filter(branch -> branch.id == 0).findFirst()
                .orElseThrow(() -> new IllegalStateException("main branch missing"));
    }

    private static String named(Instruction instruction) {
        return ((AdvancedOperand.Named) instruction.advanced()).name();
    }

    private static AdvancedOperand.IteratorSpec iterator(Instruction instruction) {
        return (AdvancedOperand.IteratorSpec) instruction.advanced();
    }

    private static AdvancedOperand.WatchSpec watch(Instruction instruction) {
        return (AdvancedOperand.WatchSpec) instruction.advanced();
    }

    private static AdvancedOperand.RangeSpec range(Instruction instruction) {
        return (AdvancedOperand.RangeSpec) instruction.advanced();
    }

    private static AdvancedOperand.ForkSpec fork(Instruction instruction) {
        return (AdvancedOperand.ForkSpec) instruction.advanced();
    }

    public List<RuntimeValue> stackTopFirst() { return List.copyOf(stack); }
    public Map<String, RuntimeValue> variables() { return Map.copyOf(variables); }
    public List<WorldEffect> allEffects() { return List.copyOf(allEffects); }
    public List<VmMessage> allMessages() { return List.copyOf(allMessages); }
    public List<String> activeBranchNames() {
        if (isTerminal()) return List.of();
        return namedBranches.values().stream().sorted(Comparator.comparingInt(value -> value.id))
                .map(value -> value.name).toList();
    }
    public long totalInstructions() { return totalInstructions; }
    public long lifetimeTicks() { return lifetimeTicks; }
    public int durationTicks() { return durationTicks; }
    public int totalBranchesCreated() { return totalBranchesCreated; }
    public int iteratorSteps() { return iteratorSteps; }
    public int signalsEmitted() { return signalsEmitted; }
    public int outputsEmitted() { return outputsEmitted; }
    public boolean isTerminal() { return halted || fault != null; }
    public Optional<VmFault> fault() { return Optional.ofNullable(fault); }
    public ManaCostModel.Breakdown declaredManaCost() { return program.manaCost(); }

    private enum ExecutionControl {
        CONTINUE(null), WAITING(TickResult.Status.WAITING),
        RUNNING(TickResult.Status.RUNNING), HALTED(TickResult.Status.HALTED);

        private final TickResult.Status status;
        ExecutionControl(TickResult.Status status) { this.status = status; }
    }

    private static final class BranchState {
        private final int id;
        private final String name;
        private final int endExclusive;
        @SuppressWarnings("unused") private final long startTick;
        private int instructionPointer;
        private int waitingTicks;
        private boolean waitingForJoin;

        private BranchState(int id, String name, int instructionPointer,
                int endExclusive, long startTick) {
            this.id = id;
            this.name = name;
            this.instructionPointer = instructionPointer;
            this.endExclusive = endExclusive;
            this.startTick = startTick;
        }
    }

    private static final class IteratorState {
        @SuppressWarnings("unused") private final String name;
        private final int ownerBranchId;
        private final List<RuntimeValue> values;
        private final int maximumSteps;
        private int index;

        private IteratorState(String name, int ownerBranchId, List<RuntimeValue> values,
                int index, int maximumSteps) {
            this.name = name;
            this.ownerBranchId = ownerBranchId;
            this.values = List.copyOf(values);
            this.index = index;
            this.maximumSteps = maximumSteps;
        }
    }

    private record Watcher(String variable, Vector3 point,
            double declaredRange, int ownerBranchId) { }
    private record BranchLoop(int branchId, int instructionPointer) { }

    private static final class ExecutionFault extends RuntimeException {
        private final VmFault fault;
        private final int executed;

        private ExecutionFault(VmFault fault, int executed) {
            super(null, null, false, false);
            this.fault = fault;
            this.executed = executed;
        }
    }
}
