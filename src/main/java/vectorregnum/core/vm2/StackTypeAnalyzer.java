package vectorregnum.core.vm2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Forward data-flow analysis for the VM's exact operand stack contracts. */
public final class StackTypeAnalyzer {
    private StackTypeAnalyzer() {
    }

    public static StackAnalysis analyze(Program program) {
        return analyze(program, VmLimits.DEFAULT.maxStackDepth());
    }

    public static StackAnalysis analyze(Program program, int maximumStackDepth) {
        Objects.requireNonNull(program, "program");
        if (maximumStackDepth < 1) throw new IllegalArgumentException("maximumStackDepth must be positive");
        List<Instruction> code = program.instructions();
        if (code.isEmpty()) return new StackAnalysis(List.of(), Map.of(), 0);

        Map<Integer, List<StackType>> entries = new LinkedHashMap<>();
        List<StackDiagnostic> diagnostics = new ArrayList<>();
        Set<String> diagnosticKeys = new LinkedHashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        entries.put(0, List.of());
        pending.add(0);
        int maximumDepth = 0;

        while (!pending.isEmpty()) {
            int pointer = pending.removeFirst();
            Instruction instruction = code.get(pointer);
            List<StackType> stack = new ArrayList<>(entries.get(pointer));
            maximumDepth = Math.max(maximumDepth, stack.size());
            try {
                transfer(instruction, stack);
            } catch (AnalysisFault fault) {
                addDiagnostic(diagnostics, diagnosticKeys, new StackDiagnostic(
                        fault.code, fault.getMessage(), instruction.source(), pointer));
                continue;
            }
            maximumDepth = Math.max(maximumDepth, stack.size());
            if (stack.size() > maximumStackDepth) {
                addDiagnostic(diagnostics, diagnosticKeys, new StackDiagnostic(
                        StackDiagnostic.Code.STACK_OVERFLOW,
                        "stack depth " + stack.size() + " exceeds limit " + maximumStackDepth,
                        instruction.source(), pointer));
                continue;
            }
            for (int successor : successors(pointer, instruction, code.size())) {
                List<StackType> incoming = List.copyOf(stack);
                List<StackType> existing = entries.putIfAbsent(successor, incoming);
                if (existing == null) {
                    pending.add(successor);
                } else if (!existing.equals(incoming)) {
                    Instruction destination = code.get(successor);
                    addDiagnostic(diagnostics, diagnosticKeys, new StackDiagnostic(
                            StackDiagnostic.Code.CONTROL_FLOW_MERGE,
                            "control-flow paths enter with incompatible stacks: "
                                    + display(existing) + " and " + display(incoming),
                            destination.source(), successor));
                }
            }
        }
        return new StackAnalysis(diagnostics, entries, maximumDepth);
    }

    private static void transfer(Instruction instruction, List<StackType> stack) {
        switch (instruction.opcode()) {
            case PUSH -> stack.add(StackType.of(instruction.literal()));
            case POP -> pop(stack, instruction.opcode());
            case DUP -> stack.add(peek(stack, instruction.opcode()));
            case ADD -> binaryAlternatives(stack, instruction.opcode(), List.of(
                    signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                    signature(StackType.VECTOR, StackType.VECTOR, StackType.VECTOR),
                    signature(StackType.POINT, StackType.VECTOR, StackType.POINT)));
            case SUBTRACT -> binaryAlternatives(stack, instruction.opcode(), List.of(
                    signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                    signature(StackType.VECTOR, StackType.VECTOR, StackType.VECTOR),
                    signature(StackType.POINT, StackType.POINT, StackType.VECTOR),
                    signature(StackType.POINT, StackType.VECTOR, StackType.POINT)));
            case MULTIPLY -> binaryAlternatives(stack, instruction.opcode(), List.of(
                    signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                    signature(StackType.VECTOR, StackType.NUMBER, StackType.VECTOR),
                    signature(StackType.NUMBER, StackType.VECTOR, StackType.VECTOR)));
            case DIVIDE -> binaryAlternatives(stack, instruction.opcode(), List.of(
                    signature(StackType.NUMBER, StackType.NUMBER, StackType.NUMBER),
                    signature(StackType.VECTOR, StackType.NUMBER, StackType.VECTOR)));
            case EQUALS -> {
                requireDepth(stack, 2, instruction.opcode());
                pop(stack, instruction.opcode());
                pop(stack, instruction.opcode());
                stack.add(StackType.BOOLEAN);
            }
            case LESS_THAN, GREATER_THAN -> binary(stack, instruction.opcode(),
                    StackType.NUMBER, StackType.NUMBER, StackType.BOOLEAN);
            case NOT -> unary(stack, instruction.opcode(), StackType.BOOLEAN, StackType.BOOLEAN);
            case AND, OR -> binary(stack, instruction.opcode(),
                    StackType.BOOLEAN, StackType.BOOLEAN, StackType.BOOLEAN);
            case JUMP, LOOP, DELAY, SET_DURATION, SEMANTIC, HALT -> { }
            case JUMP_IF_FALSE -> requirePop(stack, instruction.opcode(), StackType.BOOLEAN);
            case SELECT_RADIUS -> {
                requirePop(stack, instruction.opcode(), StackType.NUMBER);
                requirePop(stack, instruction.opcode(), StackType.POINT);
                stack.add(StackType.ENTITY_LIST);
            }
            case RAYCAST_ENTITIES -> {
                requirePop(stack, instruction.opcode(), StackType.NUMBER);
                requirePop(stack, instruction.opcode(), StackType.VECTOR);
                requirePop(stack, instruction.opcode(), StackType.POINT);
                stack.add(StackType.ENTITY_LIST);
            }
            case IMPULSE, ACCELERATION -> {
                requirePop(stack, instruction.opcode(), StackType.VECTOR);
                requirePop(stack, instruction.opcode(), StackType.ENTITY);
            }
            case DAMPING -> {
                requirePop(stack, instruction.opcode(), StackType.NUMBER);
                requirePop(stack, instruction.opcode(), StackType.ENTITY);
            }
            case FOLLOW_PATH -> {
                requirePop(stack, instruction.opcode(), StackType.NUMBER);
                requirePop(stack, instruction.opcode(), StackType.POINT_LIST);
                requirePop(stack, instruction.opcode(), StackType.ENTITY);
            }
            case MOVE_TOWARD -> {
                requirePop(stack, instruction.opcode(), StackType.NUMBER);
                requirePop(stack, instruction.opcode(), StackType.POINT);
                requirePop(stack, instruction.opcode(), StackType.ENTITY);
            }
            case KEEP_DISTANCE -> {
                requirePop(stack, instruction.opcode(), StackType.NUMBER);
                requirePop(stack, instruction.opcode(), StackType.ENTITY);
                requirePop(stack, instruction.opcode(), StackType.ENTITY);
            }
        }
    }

    private static List<Integer> successors(int pointer, Instruction instruction, int size) {
        return switch (instruction.opcode()) {
            case HALT -> List.of();
            case JUMP -> List.of(instruction.argument());
            case JUMP_IF_FALSE -> pointer + 1 < size
                    ? List.of(instruction.argument(), pointer + 1) : List.of(instruction.argument());
            case LOOP -> pointer + 1 < size
                    ? List.of(instruction.argument(), pointer + 1) : List.of(instruction.argument());
            default -> pointer + 1 < size ? List.of(pointer + 1) : List.of();
        };
    }

    private static void unary(List<StackType> stack, Opcode opcode,
            StackType operand, StackType result) {
        requirePop(stack, opcode, operand);
        stack.add(result);
    }

    private static void binary(List<StackType> stack, Opcode opcode, StackType left,
            StackType right, StackType result) {
        requireDepth(stack, 2, opcode);
        StackType actualRight = pop(stack, opcode);
        StackType actualLeft = pop(stack, opcode);
        if (actualLeft != left || actualRight != right) {
            throw mismatch(opcode, List.of(left, right), List.of(actualLeft, actualRight));
        }
        stack.add(result);
    }

    private static void binaryAlternatives(List<StackType> stack, Opcode opcode,
            List<Signature> alternatives) {
        requireDepth(stack, 2, opcode);
        StackType actualRight = stack.get(stack.size() - 1);
        StackType actualLeft = stack.get(stack.size() - 2);
        for (Signature alternative : alternatives) {
            if (alternative.left == actualLeft && alternative.right == actualRight) {
                stack.remove(stack.size() - 1);
                stack.remove(stack.size() - 1);
                stack.add(alternative.result);
                return;
            }
        }
        String expected = alternatives.stream().map(Signature::display)
                .reduce((left, right) -> left + ", " + right).orElse("none");
        throw new AnalysisFault(StackDiagnostic.Code.TYPE_MISMATCH,
                opcode + " expects " + expected + " but found ("
                        + actualLeft.displayName() + ", " + actualRight.displayName() + ")");
    }

    private static void requirePop(List<StackType> stack, Opcode opcode, StackType expected) {
        StackType actual = pop(stack, opcode);
        if (actual != expected) throw mismatch(opcode, List.of(expected), List.of(actual));
    }

    private static StackType peek(List<StackType> stack, Opcode opcode) {
        requireDepth(stack, 1, opcode);
        return stack.getLast();
    }

    private static StackType pop(List<StackType> stack, Opcode opcode) {
        requireDepth(stack, 1, opcode);
        return stack.removeLast();
    }

    private static void requireDepth(List<StackType> stack, int count, Opcode opcode) {
        if (stack.size() < count) {
            throw new AnalysisFault(StackDiagnostic.Code.STACK_UNDERFLOW,
                    opcode + " needs " + count + " stack value(s), found " + stack.size());
        }
    }

    private static AnalysisFault mismatch(Opcode opcode,
            List<StackType> expected, List<StackType> actual) {
        return new AnalysisFault(StackDiagnostic.Code.TYPE_MISMATCH,
                opcode + " expects " + display(expected) + " but found " + display(actual));
    }

    private static String display(List<StackType> stack) {
        return stack.stream().map(StackType::displayName).toList().toString();
    }

    private static Signature signature(StackType left, StackType right, StackType result) {
        return new Signature(left, right, result);
    }

    private static void addDiagnostic(List<StackDiagnostic> diagnostics, Set<String> keys,
            StackDiagnostic diagnostic) {
        String key = diagnostic.instructionPointer() + ":" + diagnostic.code() + ":" + diagnostic.message();
        if (keys.add(key)) diagnostics.add(diagnostic);
    }

    private record Signature(StackType left, StackType right, StackType result) {
        private String display() {
            return "(" + left.displayName() + ", " + right.displayName() + ")";
        }
    }

    private static final class AnalysisFault extends RuntimeException {
        private final StackDiagnostic.Code code;

        private AnalysisFault(StackDiagnostic.Code code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }
}
