package vectorregnum.core.vm2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.vm2.RuntimeValue.BooleanValue;
import vectorregnum.core.vm2.RuntimeValue.EntityValue;
import vectorregnum.core.vm2.RuntimeValue.ListValue;
import vectorregnum.core.vm2.RuntimeValue.NumberValue;
import vectorregnum.core.vm2.RuntimeValue.PointValue;
import vectorregnum.core.vm2.RuntimeValue.VectorValue;
import vectorregnum.core.vm2.WorldAccess.EntitySnapshot;
import vectorregnum.core.vm2.WorldAccess.RaycastHit;

/** Stateful, single-cast, tick-resumable stack VM. Instances are not thread-safe. */
public final class SpellVm {
    private final Program program;
    private final WorldAccess world;
    private final VmLimits limits;
    private final Deque<RuntimeValue> stack = new ArrayDeque<>();
    private final List<WorldEffect> allEffects = new ArrayList<>();
    private final Map<Integer, Integer> loopPasses = new HashMap<>();
    private int instructionPointer;
    private int waitingTicks;
    private int durationTicks = 1;
    private long totalInstructions;
    private long lifetimeTicks;
    private boolean halted;
    private VmFault fault;

    public SpellVm(Program program, WorldAccess world, VmLimits limits) {
        this.program = Objects.requireNonNull(program, "program");
        this.world = Objects.requireNonNull(world, "world");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public SpellVm(Program program, WorldAccess world) {
        this(program, world, VmLimits.DEFAULT);
    }

    public TickResult tick() {
        if (fault != null) return result(TickResult.Status.FAULTED, 0, List.of());
        if (halted) return result(TickResult.Status.HALTED, 0, List.of());
        lifetimeTicks++;
        if (lifetimeTicks > limits.maxLifetimeTicks()) {
            fail(VmFault.Code.LIFETIME_TICK_LIMIT, "spell exceeded lifetime tick limit", currentSource());
            return result(TickResult.Status.FAULTED, 0, List.of());
        }
        if (waitingTicks > 0) {
            waitingTicks--;
            return result(TickResult.Status.WAITING, 0, List.of());
        }

        List<WorldEffect> emitted = new ArrayList<>();
        int executed = 0;
        try {
            while (executed < limits.maxInstructionsPerTick()) {
                if (instructionPointer >= program.instructions().size()) {
                    halted = true;
                    return result(TickResult.Status.HALTED, executed, emitted);
                }
                if (totalInstructions >= limits.maxTotalInstructions()) {
                    throw authored(VmFault.Code.TOTAL_INSTRUCTION_LIMIT,
                            "spell exceeded total instruction limit");
                }
                Instruction instruction = program.instructions().get(instructionPointer);
                totalInstructions++;
                executed++;
                TickResult.Status terminal = execute(instruction, emitted);
                if (terminal != null) return result(terminal, executed, emitted);
            }
            return result(TickResult.Status.BUDGET_YIELD, executed, emitted);
        } catch (ExecutionFault executionFault) {
            fault = executionFault.fault;
            return result(TickResult.Status.FAULTED, executed, emitted);
        } catch (RuntimeException adapterFailure) {
            fail(VmFault.Code.WORLD_ADAPTER_ERROR,
                    "world adapter rejected operation: " + adapterFailure.getClass().getSimpleName(),
                    currentSource());
            return result(TickResult.Status.FAULTED, executed, emitted);
        }
    }

    private TickResult.Status execute(Instruction instruction, List<WorldEffect> emitted) {
        switch (instruction.opcode()) {
            case PUSH -> { push(instruction.literal()); instructionPointer++; }
            case POP -> { pop(); instructionPointer++; }
            case DUP -> { RuntimeValue value = pop(); push(value); push(value); instructionPointer++; }
            case ADD -> { add(); instructionPointer++; }
            case SUBTRACT -> { subtract(); instructionPointer++; }
            case MULTIPLY -> { multiply(); instructionPointer++; }
            case DIVIDE -> { divide(); instructionPointer++; }
            case EQUALS -> { RuntimeValue right = pop(), left = pop(); push(new BooleanValue(left.equals(right))); instructionPointer++; }
            case LESS_THAN -> { compare(true); instructionPointer++; }
            case GREATER_THAN -> { compare(false); instructionPointer++; }
            case NOT -> { push(new BooleanValue(!expect(BooleanValue.class, pop()).value())); instructionPointer++; }
            case AND -> { boolean right = expect(BooleanValue.class, pop()).value(); boolean left = expect(BooleanValue.class, pop()).value(); push(new BooleanValue(left && right)); instructionPointer++; }
            case OR -> { boolean right = expect(BooleanValue.class, pop()).value(); boolean left = expect(BooleanValue.class, pop()).value(); push(new BooleanValue(left || right)); instructionPointer++; }
            case JUMP -> instructionPointer = instruction.argument();
            case JUMP_IF_FALSE -> instructionPointer = expect(BooleanValue.class, pop()).value()
                    ? instructionPointer + 1 : instruction.argument();
            case LOOP -> loop(instruction);
            case DELAY -> {
                instructionPointer++;
                if (instruction.argument() > 0) {
                    waitingTicks = instruction.argument() - 1;
                    return TickResult.Status.WAITING;
                }
            }
            case SET_DURATION -> { durationTicks = instruction.argument(); instructionPointer++; }
            case SELECT_RADIUS -> { select(instruction); instructionPointer++; }
            case RAYCAST_ENTITIES -> { raycast(instruction); instructionPointer++; }
            case IMPULSE -> { Vector3 value = vector(pop()); emit(new WorldEffect.Impulse(entityId(pop()), value, durationTicks), emitted); instructionPointer++; }
            case ACCELERATION -> { Vector3 value = vector(pop()); emit(new WorldEffect.Acceleration(entityId(pop()), value, durationTicks), emitted); instructionPointer++; }
            case DAMPING -> { emitDamping(emitted); instructionPointer++; }
            case FOLLOW_PATH -> { emitPath(emitted); instructionPointer++; }
            case MOVE_TOWARD -> { emitMove(emitted); instructionPointer++; }
            case KEEP_DISTANCE -> { emitKeepDistance(emitted); instructionPointer++; }
            case HALT -> { instructionPointer++; halted = true; return TickResult.Status.HALTED; }
        }
        return null;
    }

    private void add() {
        RuntimeValue right = pop(), left = pop();
        if (left instanceof NumberValue a && right instanceof NumberValue b) {
            push(number(a.value() + b.value()));
        } else if (left instanceof VectorValue a && right instanceof VectorValue b) {
            push(new VectorValue(a.value().plus(b.value())));
        } else if (left instanceof PointValue a && right instanceof VectorValue b) {
            push(new PointValue(a.value().plus(b.value())));
        } else {
            throw type("ADD expects number+number, vector+vector, or point+vector", left, right);
        }
    }

    private void subtract() {
        RuntimeValue right = pop(), left = pop();
        if (left instanceof NumberValue a && right instanceof NumberValue b) {
            push(number(a.value() - b.value()));
        } else if (left instanceof VectorValue a && right instanceof VectorValue b) {
            push(new VectorValue(a.value().minus(b.value())));
        } else if (left instanceof PointValue a && right instanceof PointValue b) {
            push(new VectorValue(a.value().minus(b.value())));
        } else if (left instanceof PointValue a && right instanceof VectorValue b) {
            push(new PointValue(a.value().minus(b.value())));
        } else {
            throw type("SUBTRACT operands are incompatible", left, right);
        }
    }

    private void multiply() {
        RuntimeValue right = pop(), left = pop();
        if (left instanceof NumberValue a && right instanceof NumberValue b) {
            push(number(a.value() * b.value()));
        } else if (left instanceof VectorValue vector && right instanceof NumberValue scalar) {
            push(new VectorValue(vector.value().scaled(scalar.value())));
        } else if (left instanceof NumberValue scalar && right instanceof VectorValue vector) {
            push(new VectorValue(vector.value().scaled(scalar.value())));
        } else {
            throw type("MULTIPLY expects numbers or vector and scalar", left, right);
        }
    }

    private void divide() {
        RuntimeValue right = pop(), left = pop();
        double divisor = expect(NumberValue.class, right).value();
        if (Math.abs(divisor) <= 1.0e-12) throw authored(VmFault.Code.DIVIDE_BY_ZERO, "division by zero");
        if (left instanceof NumberValue number) push(number(number.value() / divisor));
        else if (left instanceof VectorValue vector) push(new VectorValue(vector.value().scaled(1.0 / divisor)));
        else throw type("DIVIDE expects number/number or vector/number", left, right);
    }

    private void compare(boolean less) {
        double right = expect(NumberValue.class, pop()).value();
        double left = expect(NumberValue.class, pop()).value();
        push(new BooleanValue(less ? left < right : left > right));
    }

    private void loop(Instruction instruction) {
        if (instruction.secondArgument() > limits.maxLoopIterations()) {
            throw authored(VmFault.Code.LOOP_LIMIT, "loop declares " + instruction.secondArgument()
                    + " iterations; limit is " + limits.maxLoopIterations());
        }
        int pass = loopPasses.merge(instructionPointer, 1, Integer::sum);
        if (pass < instruction.secondArgument()) instructionPointer = instruction.argument();
        else { loopPasses.remove(instructionPointer); instructionPointer++; }
    }

    private void select(Instruction instruction) {
        double radius = positiveRange(pop());
        Vector3 center = point(pop());
        List<RuntimeValue> found = world.select(center, radius, instruction.filter()).stream()
                .sorted(Comparator.comparing(EntitySnapshot::id))
                .limit((long) limits.maxSelectionResults() + 1)
                .map(snapshot -> (RuntimeValue) new EntityValue(snapshot.id()))
                .toList();
        if (found.size() > limits.maxSelectionResults()) {
            throw authored(VmFault.Code.INVALID_QUERY, "selection result limit exceeded");
        }
        push(new ListValue(found));
    }

    private void raycast(Instruction instruction) {
        double range = positiveRange(pop());
        Vector3 direction = vector(pop());
        Vector3 origin = point(pop());
        if (direction.length() <= 1.0e-12) throw authored(VmFault.Code.INVALID_QUERY, "raycast direction is zero");
        Optional<RaycastHit> hit = world.raycast(origin, direction.normalized(), range, instruction.filter());
        List<RuntimeValue> result = hit.flatMap(RaycastHit::entity)
                .map(entity -> List.<RuntimeValue>of(new EntityValue(entity.id()))).orElseGet(List::of);
        push(new ListValue(result));
    }

    private double positiveRange(RuntimeValue value) {
        double range = expect(NumberValue.class, value).value();
        if (range < 0 || range > limits.maxPerceptionRange()) {
            throw authored(VmFault.Code.INVALID_QUERY, "range must be 0.." + limits.maxPerceptionRange());
        }
        return range;
    }

    private void emitDamping(List<WorldEffect> emitted) {
        double factor = expect(NumberValue.class, pop()).value();
        String entity = entityId(pop());
        if (factor < 0 || factor > 1) throw authored(VmFault.Code.TYPE_MISMATCH, "damping must be 0..1");
        emit(new WorldEffect.Damping(entity, factor, durationTicks), emitted);
    }

    private void emitPath(List<WorldEffect> emitted) {
        double speed = positive(pop(), "path speed");
        ListValue path = expect(ListValue.class, pop());
        String entity = entityId(pop());
        List<Vector3> points = path.values().stream().map(value -> {
            if (!(value instanceof PointValue point)) throw authored(VmFault.Code.INVALID_PATH, "path contains non-point value");
            return point.value();
        }).toList();
        if (points.isEmpty()) throw authored(VmFault.Code.INVALID_PATH, "path is empty");
        emit(new WorldEffect.FollowPath(entity, points, speed, durationTicks), emitted);
    }

    private void emitMove(List<WorldEffect> emitted) {
        double speed = positive(pop(), "move speed");
        Vector3 target = point(pop());
        String entity = entityId(pop());
        emit(new WorldEffect.MoveToward(entity, target, speed, durationTicks), emitted);
    }

    private void emitKeepDistance(List<WorldEffect> emitted) {
        double distance = expect(NumberValue.class, pop()).value();
        String target = entityId(pop());
        String entity = entityId(pop());
        if (distance < 0) throw authored(VmFault.Code.TYPE_MISMATCH, "distance cannot be negative");
        emit(new WorldEffect.KeepDistance(entity, target, distance, durationTicks), emitted);
    }

    private double positive(RuntimeValue value, String name) {
        double result = expect(NumberValue.class, value).value();
        if (result <= 0) throw authored(VmFault.Code.TYPE_MISMATCH, name + " must be positive");
        return result;
    }

    private String entityId(RuntimeValue value) {
        String id = expect(EntityValue.class, value).id();
        if (world.entity(id).isEmpty()) throw authored(VmFault.Code.ENTITY_NOT_FOUND, "entity not found: " + id);
        return id;
    }

    private Vector3 vector(RuntimeValue value) { return expect(VectorValue.class, value).value(); }
    private Vector3 point(RuntimeValue value) { return expect(PointValue.class, value).value(); }

    private void emit(WorldEffect effect, List<WorldEffect> emitted) {
        emitted.add(effect); allEffects.add(effect);
    }

    private NumberValue number(double value) {
        if (!Double.isFinite(value)) throw authored(VmFault.Code.NUMERIC_OVERFLOW, "numeric result is not finite");
        return new NumberValue(value);
    }

    private void push(RuntimeValue value) {
        if (stack.size() >= limits.maxStackDepth()) throw authored(VmFault.Code.STACK_OVERFLOW, "stack depth limit exceeded");
        stack.push(Objects.requireNonNull(value));
    }

    private RuntimeValue pop() {
        if (stack.isEmpty()) throw authored(VmFault.Code.STACK_UNDERFLOW, "stack is empty");
        return stack.pop();
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
        return new ExecutionFault(new VmFault(code, message, currentSource(), instructionPointer));
    }

    private void fail(VmFault.Code code, String message, SourceLocation source) {
        fault = new VmFault(code, message, source, Math.min(instructionPointer,
                Math.max(0, program.instructions().size())));
    }

    private SourceLocation currentSource() {
        if (!program.instructions().isEmpty()) {
            return program.instructions().get(Math.min(instructionPointer,
                    program.instructions().size() - 1)).source();
        }
        return SourceLocation.at(0, "END");
    }

    private TickResult result(TickResult.Status status, int executed, List<WorldEffect> effects) {
        return new TickResult(status, instructionPointer, executed, effects, Optional.ofNullable(fault));
    }

    public List<RuntimeValue> stackTopFirst() { return List.copyOf(stack); }
    public List<WorldEffect> allEffects() { return List.copyOf(allEffects); }
    public long totalInstructions() { return totalInstructions; }
    public long lifetimeTicks() { return lifetimeTicks; }
    public int durationTicks() { return durationTicks; }
    public boolean isTerminal() { return halted || fault != null; }
    public Optional<VmFault> fault() { return Optional.ofNullable(fault); }
    public ManaCostModel.Breakdown declaredManaCost() { return program.manaCost(); }

    private static final class ExecutionFault extends RuntimeException {
        private final VmFault fault;
        private ExecutionFault(VmFault fault) { super(null, null, false, false); this.fault = fault; }
    }
}
