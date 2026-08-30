package vectorregnum.core.vm2;

import java.util.Objects;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.vm2.WorldAccess.SelectionFilter;

/**
 * Immutable bytecode instruction. Binary operations pop right then left. World
 * operations use the operand orders enforced by {@link SpellVm}.
 */
public record Instruction(Opcode opcode, SourceLocation source, RuntimeValue literal,
        int argument, int secondArgument, SelectionFilter filter, ManaCostModel.Input cost,
        SemanticInstruction semantic, AdvancedOperand advanced) {
    public static final int MAX_DURATION_TICKS = 1_200;

    /** Backward-compatible constructor for priorities 1-23 instructions. */
    public Instruction(Opcode opcode, SourceLocation source, RuntimeValue literal,
            int argument, int secondArgument, SelectionFilter filter, ManaCostModel.Input cost,
            SemanticInstruction semantic) {
        this(opcode, source, literal, argument, secondArgument, filter, cost, semantic, null);
    }

    public Instruction {
        Objects.requireNonNull(opcode, "opcode");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cost, "cost");
        switch (opcode) {
            case PUSH -> Objects.requireNonNull(literal, "PUSH literal");
            case DELAY -> {
                if (argument < 0) throw new IllegalArgumentException(opcode + " ticks cannot be negative");
            }
            case SET_DURATION -> {
                if (argument < 1 || argument > MAX_DURATION_TICKS) {
                    throw new IllegalArgumentException(
                            "duration must be 1.." + MAX_DURATION_TICKS + " ticks");
                }
            }
            case JUMP, JUMP_IF_FALSE -> {
                if (argument < 0) throw new IllegalArgumentException("jump target cannot be negative");
            }
            case LOOP -> {
                if (argument < 0 || secondArgument < 1) {
                    throw new IllegalArgumentException("loop target/count invalid");
                }
            }
            case SELECT_RADIUS, RAYCAST_ENTITIES -> Objects.requireNonNull(filter, "selection filter");
            case SEMANTIC -> Objects.requireNonNull(semantic, "semantic instruction");
            case STORE_VARIABLE, LOAD_VARIABLE, CANCEL_BRANCH ->
                    requireAdvanced(advanced, AdvancedOperand.Named.class, opcode);
            case ITERATOR_BEGIN, ITERATOR_NEXT ->
                    requireAdvanced(advanced, AdvancedOperand.IteratorSpec.class, opcode);
            case COLLISION, SIGNAL, OUTPUT ->
                    requireAdvanced(advanced, AdvancedOperand.RangeSpec.class, opcode);
            case WATCH_VARIABLE ->
                    requireAdvanced(advanced, AdvancedOperand.WatchSpec.class, opcode);
            case FORK -> requireAdvanced(advanced, AdvancedOperand.ForkSpec.class, opcode);
            default -> { }
        }
    }

    private static void requireAdvanced(
            AdvancedOperand operand, Class<? extends AdvancedOperand> expected, Opcode opcode) {
        if (!expected.isInstance(operand)) {
            throw new IllegalArgumentException(opcode + " requires " + expected.getSimpleName());
        }
    }

    private static Instruction simple(Opcode opcode, SourceLocation source) {
        return new Instruction(opcode, source, null, 0, 0, null,
                ManaCostModel.Input.ZERO, null, null);
    }

    public static Instruction push(RuntimeValue value, SourceLocation source) {
        return new Instruction(Opcode.PUSH, source, Objects.requireNonNull(value), 0, 0, null,
                new ManaCostModel.Input(0, 0, 0, 0, 1, 0, 0), null, null);
    }
    public static Instruction pop(SourceLocation s) { return simple(Opcode.POP, s); }
    public static Instruction dup(SourceLocation s) { return simple(Opcode.DUP, s); }
    public static Instruction add(SourceLocation s) { return simple(Opcode.ADD, s); }
    public static Instruction subtract(SourceLocation s) { return simple(Opcode.SUBTRACT, s); }
    public static Instruction multiply(SourceLocation s) { return simple(Opcode.MULTIPLY, s); }
    public static Instruction divide(SourceLocation s) { return simple(Opcode.DIVIDE, s); }
    public static Instruction equalsValue(SourceLocation s) { return simple(Opcode.EQUALS, s); }
    public static Instruction lessThan(SourceLocation s) { return simple(Opcode.LESS_THAN, s); }
    public static Instruction greaterThan(SourceLocation s) { return simple(Opcode.GREATER_THAN, s); }
    public static Instruction not(SourceLocation s) { return simple(Opcode.NOT, s); }
    public static Instruction and(SourceLocation s) { return simple(Opcode.AND, s); }
    public static Instruction or(SourceLocation s) { return simple(Opcode.OR, s); }
    public static Instruction halt(SourceLocation s) { return simple(Opcode.HALT, s); }

    public static Instruction jump(int target, SourceLocation source) {
        return control(Opcode.JUMP, target, 0, source);
    }
    public static Instruction jumpIfFalse(int target, SourceLocation source) {
        return control(Opcode.JUMP_IF_FALSE, target, 0, source);
    }
    /** At the end of a body, jump to target until the body has run {@code iterations} times. */
    public static Instruction loop(int target, int iterations, SourceLocation source) {
        return control(Opcode.LOOP, target, iterations, source);
    }
    private static Instruction control(Opcode op, int target, int count, SourceLocation source) {
        return new Instruction(op, source, null, target, count, null,
                new ManaCostModel.Input(0, 0, 0, 0, 0, 0, Math.max(1, count)), null, null);
    }

    public static Instruction delay(int ticks, SourceLocation source) {
        return new Instruction(Opcode.DELAY, source, null, ticks, 0, null,
                new ManaCostModel.Input(0, 0, ticks, 0, 0, 0, 0), null, null);
    }
    public static Instruction duration(int ticks, SourceLocation source) {
        return new Instruction(Opcode.SET_DURATION, source, null, ticks, 0, null,
                new ManaCostModel.Input(0, 0, ticks, 0, 0, 0, 0), null, null);
    }
    public static Instruction select(SelectionFilter filter, double declaredRange, int samples,
            SourceLocation source) {
        return perception(Opcode.SELECT_RADIUS, filter, declaredRange, samples, source);
    }
    public static Instruction raycast(SelectionFilter filter, double declaredRange, int samples,
            SourceLocation source) {
        return perception(Opcode.RAYCAST_ENTITIES, filter, declaredRange, samples, source);
    }
    private static Instruction perception(Opcode opcode, SelectionFilter filter, double range,
            int samples, SourceLocation source) {
        return new Instruction(opcode, source, null, 0, 0, filter,
                new ManaCostModel.Input(0, range, 0, 0, 1, samples, 0), null, null);
    }

    public static Instruction impulse(double declaredWork, double rarity, SourceLocation source) {
        return physics(Opcode.IMPULSE, declaredWork, rarity, source);
    }
    public static Instruction acceleration(double declaredWork, double rarity, SourceLocation source) {
        return physics(Opcode.ACCELERATION, declaredWork, rarity, source);
    }
    public static Instruction damping(double declaredWork, double rarity, SourceLocation source) {
        return physics(Opcode.DAMPING, declaredWork, rarity, source);
    }
    public static Instruction followPath(double declaredWork, double rarity, SourceLocation source) {
        return physics(Opcode.FOLLOW_PATH, declaredWork, rarity, source);
    }
    public static Instruction moveToward(double declaredWork, double rarity, SourceLocation source) {
        return physics(Opcode.MOVE_TOWARD, declaredWork, rarity, source);
    }
    public static Instruction keepDistance(double declaredWork, double rarity, SourceLocation source) {
        return physics(Opcode.KEEP_DISTANCE, declaredWork, rarity, source);
    }
    private static Instruction physics(Opcode opcode, double work, double rarity, SourceLocation source) {
        return new Instruction(opcode, source, null, 0, 0, null,
                new ManaCostModel.Input(work, 0, 0, rarity, 0, 0, 0), null, null);
    }

    /** Loader-neutral semantic step. It consumes no VM stack and emits one ordered effect. */
    public static Instruction semantic(SemanticInstruction instruction, ManaCostModel.Input cost) {
        Objects.requireNonNull(instruction, "instruction");
        return new Instruction(Opcode.SEMANTIC, instruction.source(), null, 0, 0, null,
                Objects.requireNonNull(cost, "cost"), instruction, null);
    }

    public static Instruction storeVariable(String name, SourceLocation source) {
        return advanced(Opcode.STORE_VARIABLE, new AdvancedOperand.Named(name), source,
                new ManaCostModel.Input(0, 0, 0, 0, 1, 0, 0));
    }

    public static Instruction loadVariable(String name, SourceLocation source) {
        return advanced(Opcode.LOAD_VARIABLE, new AdvancedOperand.Named(name), source,
                new ManaCostModel.Input(0, 0, 0, 0, 1, 0, 0));
    }

    public static Instruction iteratorBegin(String name, int exitTarget, int maximumSteps,
            SourceLocation source) {
        return advanced(Opcode.ITERATOR_BEGIN,
                new AdvancedOperand.IteratorSpec(name, exitTarget, maximumSteps), source,
                new ManaCostModel.Input(0, 0, 0, 0, 1, 0, maximumSteps));
    }

    public static Instruction iteratorNext(String name, int bodyTarget, SourceLocation source) {
        return advanced(Opcode.ITERATOR_NEXT,
                new AdvancedOperand.IteratorSpec(name, bodyTarget, 1), source,
                new ManaCostModel.Input(0, 0, 0, 0, 0, 0, 1));
    }

    public static Instruction collision(double declaredRange, int samples, SourceLocation source) {
        return advanced(Opcode.COLLISION,
                new AdvancedOperand.RangeSpec(declaredRange, samples), source,
                new ManaCostModel.Input(0, declaredRange, 0, 0, 0, samples, 0));
    }

    public static Instruction watchVariable(String variable, double declaredRange,
            SourceLocation source) {
        return advanced(Opcode.WATCH_VARIABLE,
                new AdvancedOperand.WatchSpec(variable, declaredRange), source,
                new ManaCostModel.Input(0, declaredRange, 0, 0, 1, 0, 1));
    }

    public static Instruction signal(double declaredRange, SourceLocation source) {
        return advanced(Opcode.SIGNAL, new AdvancedOperand.RangeSpec(declaredRange, 1), source,
                new ManaCostModel.Input(0, declaredRange, 0, 0, 0, 0, 1));
    }

    public static Instruction output(double declaredRange, SourceLocation source) {
        return advanced(Opcode.OUTPUT, new AdvancedOperand.RangeSpec(declaredRange, 1), source,
                new ManaCostModel.Input(0, declaredRange, 0, 0, 1, 0, 1));
    }

    public static Instruction fork(String name, int start, int endExclusive,
            SourceLocation source) {
        return advanced(Opcode.FORK, new AdvancedOperand.ForkSpec(name, start, endExclusive),
                source, new ManaCostModel.Input(0, 0, 0, 0, 1, 0, 1));
    }

    public static Instruction join(SourceLocation source) {
        return new Instruction(Opcode.JOIN, source, null, 0, 0, null,
                new ManaCostModel.Input(0, 0, 0, 0, 0, 0, 1), null, null);
    }

    public static Instruction cancelBranch(String name, SourceLocation source) {
        return advanced(Opcode.CANCEL_BRANCH, new AdvancedOperand.Named(name), source,
                new ManaCostModel.Input(0, 0, 0, 0, 0, 0, 1));
    }

    public static Instruction branchEnd(SourceLocation source) {
        return new Instruction(Opcode.BRANCH_END, source, null, 0, 0, null,
                new ManaCostModel.Input(0, 0, 0, 0, 0, 0, 1), null, null);
    }

    private static Instruction advanced(Opcode opcode, AdvancedOperand operand,
            SourceLocation source, ManaCostModel.Input cost) {
        return new Instruction(opcode, source, null, 0, 0, null, cost, null, operand);
    }
}
