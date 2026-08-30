package vectorregnum.core.circle;

import vectorregnum.core.vm2.AdvancedOperand;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.RuntimeValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.StackAnalysis;
import vectorregnum.core.vm2.StackDiagnostic;
import vectorregnum.core.vm2.StackTypeAnalyzer;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.VmLimits;
import vectorregnum.core.vm2.WorldAccess;
import vectorregnum.core.semantic.CreationForm;
import vectorregnum.core.semantic.CreationMaterial;
import vectorregnum.core.semantic.CreationSpec;
import vectorregnum.core.semantic.SemanticCostModel;
import vectorregnum.core.semantic.SemanticInstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Direct clockwise/inward lowering for the typed player-authored VM language. */
public final class Vm2CircleCompiler {
    private static final Set<String> SIMPLE = Set.of(
            "VM_POP", "VM_DUP", "VM_ADD", "VM_SUBTRACT", "VM_MULTIPLY", "VM_DIVIDE",
            "VM_EQUALS", "VM_LESS_THAN", "VM_GREATER_THAN", "VM_NOT", "VM_AND", "VM_OR");

    private Vm2CircleCompiler() {
    }

    public static Vm2CircleCompilation compile(MagicCircle circle, Context context) {
        List<PlacedSigil> ordered = circle.executionOrder();
        List<CircleDiagnostic> diagnostics = new ArrayList<>();
        List<Instruction> instructions = new ArrayList<>();
        if (ordered.isEmpty()) {
            diagnostics.add(error("EMPTY_CIRCLE", "Place at least one vm2 sigil", null, -1));
        }
        for (int index = 0; index < ordered.size(); index++) {
            PlacedSigil sigil = ordered.get(index);
            try {
                instructions.add(lower(sigil, index, ordered.size(), context));
            } catch (CompileFault fault) {
                diagnostics.add(error(fault.code, fault.getMessage(), sigil.coordinate(), index));
            } catch (RuntimeException exception) {
                diagnostics.add(error("INVALID_VM_INSTRUCTION", safeMessage(exception),
                        sigil.coordinate(), index));
            }
        }
        if (!ordered.isEmpty() && !ordered.getLast().type().equals("EXECUTE")) {
            diagnostics.add(error("MISSING_TERMINAL_EXECUTE",
                    "A vm2 circle must end with EXECUTE", ordered.getLast().coordinate(), ordered.size() - 1));
        }
        for (int index = 0; index < Math.max(0, ordered.size() - 1); index++) {
            if (ordered.get(index).type().equals("EXECUTE")) {
                diagnostics.add(error("EARLY_EXECUTE", "EXECUTE must be the final sigil",
                        ordered.get(index).coordinate(), index));
            }
        }
        if (!diagnostics.isEmpty()) {
            return new Vm2CircleCompilation(ordered, null, diagnostics);
        }
        try {
            Program program = new Program(instructions);
            StackAnalysis analysis = StackTypeAnalyzer.analyze(program);
            for (StackDiagnostic diagnostic : analysis.diagnostics()) {
                int sourceIndex = diagnostic.source().sourceIndex();
                CircleCoordinate coordinate = sourceIndex >= 0 && sourceIndex < ordered.size()
                        ? ordered.get(sourceIndex).coordinate() : null;
                diagnostics.add(error("STATIC_" + diagnostic.code().name(),
                        diagnostic.message(), coordinate, sourceIndex));
            }
            return new Vm2CircleCompilation(ordered,
                    diagnostics.isEmpty() ? program : null, diagnostics);
        } catch (RuntimeException exception) {
            diagnostics.add(error("INVALID_CONTROL_FLOW", safeMessage(exception), null, -1));
            return new Vm2CircleCompilation(ordered, null, diagnostics);
        }
    }

    public static boolean isVm2Circle(MagicCircle circle) {
        return circle.sigils().stream().anyMatch(sigil -> sigil.type().startsWith("VM_"));
    }

    private static Instruction lower(PlacedSigil sigil, int index, int instructionCount,
            Context context) {
        String type = sigil.type();
        SourceLocation source = new SourceLocation(index, sigil.coordinate().ring() + 1,
                sigil.coordinate().clockwiseSlot() + 1, type);
        if (SIMPLE.contains(type)) {
            requireCount(sigil, 0);
            return switch (type) {
                case "VM_POP" -> Instruction.pop(source);
                case "VM_DUP" -> Instruction.dup(source);
                case "VM_ADD" -> Instruction.add(source);
                case "VM_SUBTRACT" -> Instruction.subtract(source);
                case "VM_MULTIPLY" -> Instruction.multiply(source);
                case "VM_DIVIDE" -> Instruction.divide(source);
                case "VM_EQUALS" -> Instruction.equalsValue(source);
                case "VM_LESS_THAN" -> Instruction.lessThan(source);
                case "VM_GREATER_THAN" -> Instruction.greaterThan(source);
                case "VM_NOT" -> Instruction.not(source);
                case "VM_AND" -> Instruction.and(source);
                case "VM_OR" -> Instruction.or(source);
                default -> throw new AssertionError(type);
            };
        }
        return switch (type) {
            case "VM_PUSH_SELF" -> push(sigil, new RuntimeValue.EntityValue(context.casterEntityId()), source);
            case "VM_PUSH_ORIGIN" -> push(sigil, new RuntimeValue.PointValue(context.origin()), source);
            case "VM_PUSH_LOOK" -> push(sigil, new RuntimeValue.VectorValue(context.lookVector()), source);
            case "VM_PUSH_NUMBER" -> {
                requireCount(sigil, 1);
                yield Instruction.push(new RuntimeValue.NumberValue(number(sigil, 0)), source);
            }
            case "VM_PUSH_BOOLEAN" -> {
                requireCount(sigil, 1);
                if (!(sigil.parameters().getFirst() instanceof CircleValue.BooleanValue value)) {
                    throw fault("PARAMETER_TYPE", "VM_PUSH_BOOLEAN needs true or false");
                }
                yield Instruction.push(new RuntimeValue.BooleanValue(value.value()), source);
            }
            case "VM_PUSH_VECTOR" -> {
                requireCount(sigil, 3);
                yield Instruction.push(new RuntimeValue.VectorValue(new Vector3(
                        number(sigil, 0), number(sigil, 1), number(sigil, 2))), source);
            }
            case "VM_PUSH_POINT" -> {
                requireCount(sigil, 3);
                yield Instruction.push(new RuntimeValue.PointValue(new Vector3(
                        number(sigil, 0), number(sigil, 1), number(sigil, 2))), source);
            }
            case "VM_PUSH_ENTITY" -> {
                requireCount(sigil, 1);
                if (!(sigil.parameters().getFirst() instanceof CircleValue.TextValue value)) {
                    throw fault("PARAMETER_TYPE", "VM_PUSH_ENTITY needs text:<uuid>");
                }
                yield Instruction.push(new RuntimeValue.EntityValue(value.value()), source);
            }
            case "VM_PUSH_TEXT" -> {
                requireCount(sigil, 1);
                String value = textValue(sigil, 0, "text");
                if (value.isBlank() || value.length() > RuntimeValue.MAX_TEXT_CHARS) {
                    throw fault("INVALID_TEXT", "VM_PUSH_TEXT needs 1.."
                            + RuntimeValue.MAX_TEXT_CHARS + " characters");
                }
                yield Instruction.push(new RuntimeValue.TextValue(value), source);
            }
            case "VM_PUSH_POINT_LIST" -> pointList(sigil, source);
            case "VM_JUMP" -> Instruction.jump(target(sigil, 0, instructionCount, "jump"), source);
            case "VM_JUMP_IF_FALSE" ->
                    Instruction.jumpIfFalse(target(sigil, 0, instructionCount, "conditional jump"), source);
            case "VM_LOOP" -> {
                requireCount(sigil, 2);
                yield Instruction.loop(target(sigil, 0, instructionCount, "loop"),
                        integer(sigil, 1), source);
            }
            case "VM_DELAY" -> Instruction.delay(oneInteger(sigil), source);
            case "VM_DURATION" -> Instruction.duration(oneInteger(sigil), source);
            case "VM_SELECT_RADIUS", "VM_SELECT_HOSTILE" -> {
                requireCount(sigil, 2);
                WorldAccess.SelectionFilter filter = type.equals("VM_SELECT_HOSTILE")
                        ? new WorldAccess.SelectionFilter(Optional.empty(), Set.of("hostile"), false)
                        : WorldAccess.SelectionFilter.ANY;
                yield Instruction.select(filter, number(sigil, 0), integer(sigil, 1), source);
            }
            case "VM_RAYCAST_ENTITIES" -> {
                requireCount(sigil, 2);
                yield Instruction.raycast(WorldAccess.SelectionFilter.ANY,
                        number(sigil, 0), integer(sigil, 1), source);
            }
            case "VM_IMPULSE" -> physics(sigil, source, Physics.IMPULSE);
            case "VM_ACCELERATION" -> physics(sigil, source, Physics.ACCELERATION);
            case "VM_DAMPING" -> physics(sigil, source, Physics.DAMPING);
            case "VM_FOLLOW_PATH" -> physics(sigil, source, Physics.FOLLOW_PATH);
            case "VM_MOVE_TOWARD" -> physics(sigil, source, Physics.MOVE_TOWARD);
            case "VM_KEEP_DISTANCE" -> physics(sigil, source, Physics.KEEP_DISTANCE);
            case "VM_CREATE_FORM" -> creation(sigil, source);
            case "VM_STORE_VARIABLE" -> {
                requireCount(sigil, 1);
                yield Instruction.storeVariable(identifier(sigil, 0, "variable"), source);
            }
            case "VM_LOAD_VARIABLE" -> {
                requireCount(sigil, 1);
                yield Instruction.loadVariable(identifier(sigil, 0, "variable"), source);
            }
            case "VM_ITERATOR_BEGIN" -> {
                requireCount(sigil, 3);
                yield Instruction.iteratorBegin(identifier(sigil, 0, "iterator"),
                        target(sigil, 1, instructionCount, "iterator exit"),
                        boundedInteger(sigil, 2, VmLimits.DEFAULT.maxIteratorSteps(),
                                "iterator steps"),
                        source);
            }
            case "VM_ITERATOR_NEXT" -> {
                requireCount(sigil, 2);
                yield Instruction.iteratorNext(identifier(sigil, 0, "iterator"),
                        target(sigil, 1, instructionCount, "iterator body"), source);
            }
            case "VM_COLLISION" -> {
                requireCount(sigil, 2);
                yield Instruction.collision(positiveRange(sigil, 0),
                        boundedInteger(sigil, 1, VmLimits.DEFAULT.maxSelectionResults(),
                                "collision samples"),
                        source);
            }
            case "VM_WATCH_VARIABLE" -> {
                requireCount(sigil, 2);
                yield Instruction.watchVariable(identifier(sigil, 0, "variable"),
                        positiveRange(sigil, 1), source);
            }
            case "VM_SIGNAL" -> {
                requireCount(sigil, 1);
                yield Instruction.signal(positiveRange(sigil, 0), source);
            }
            case "VM_OUTPUT" -> {
                requireCount(sigil, 1);
                yield Instruction.output(positiveRange(sigil, 0), source);
            }
            case "VM_FORK" -> {
                requireCount(sigil, 3);
                int start = target(sigil, 1, instructionCount, "branch start");
                int end = integer(sigil, 2);
                if (end > instructionCount || end <= start) {
                    throw fault("INVALID_BRANCH_RANGE",
                            "branch end must be after start and within the circle");
                }
                yield Instruction.fork(identifier(sigil, 0, "branch"), start, end, source);
            }
            case "VM_JOIN", "JOIN" -> {
                requireCount(sigil, 0);
                yield Instruction.join(source);
            }
            case "VM_CANCEL_BRANCH" -> {
                requireCount(sigil, 1);
                yield Instruction.cancelBranch(identifier(sigil, 0, "branch"), source);
            }
            case "VM_BRANCH_END", "BRANCH_END" -> {
                requireCount(sigil, 0);
                yield Instruction.branchEnd(source);
            }
            case "EXECUTE" -> {
                requireCount(sigil, 0);
                yield Instruction.halt(source);
            }
            default -> throw fault("UNKNOWN_VM_SIGIL", "Unknown vm2 sigil " + type);
        };
    }

    private static Instruction push(PlacedSigil sigil, RuntimeValue value, SourceLocation source) {
        requireCount(sigil, 0);
        return Instruction.push(value, source);
    }

    private static Instruction pointList(PlacedSigil sigil, SourceLocation source) {
        if (sigil.parameters().isEmpty() || sigil.parameters().size() % 3 != 0) {
            throw fault("PARAMETER_COUNT", "VM_PUSH_POINT_LIST needs x,y,z triples");
        }
        List<RuntimeValue> points = new ArrayList<>();
        for (int index = 0; index < sigil.parameters().size(); index += 3) {
            points.add(new RuntimeValue.PointValue(new Vector3(number(sigil, index),
                    number(sigil, index + 1), number(sigil, index + 2))));
        }
        return Instruction.push(new RuntimeValue.ListValue(points), source);
    }

    private static Instruction physics(PlacedSigil sigil, SourceLocation source, Physics physics) {
        requireCount(sigil, 2);
        double work = number(sigil, 0);
        double rarity = number(sigil, 1);
        return switch (physics) {
            case IMPULSE -> Instruction.impulse(work, rarity, source);
            case ACCELERATION -> Instruction.acceleration(work, rarity, source);
            case DAMPING -> Instruction.damping(work, rarity, source);
            case FOLLOW_PATH -> Instruction.followPath(work, rarity, source);
            case MOVE_TOWARD -> Instruction.moveToward(work, rarity, source);
            case KEEP_DISTANCE -> Instruction.keepDistance(work, rarity, source);
        };
    }

    private static Instruction creation(PlacedSigil sigil, SourceLocation source) {
        requireCount(sigil, 5);
        String material = text(sigil, 0, "material");
        String form = text(sigil, 1, "form");
        boolean permanent;
        if (sigil.parameters().get(4) instanceof CircleValue.BooleanValue value) permanent = value.value();
        else throw fault("PARAMETER_TYPE", "VM_CREATE_FORM parameter 5 (permanent) must be true or false");
        try {
            CreationSpec spec = new CreationSpec(CreationMaterial.valueOf(material.toUpperCase()),
                    CreationForm.valueOf(form.toUpperCase()), number(sigil, 2), integer(sigil, 3), permanent);
            SemanticInstruction semantic = SemanticInstruction.creation(spec, source);
            return Instruction.semantic(semantic, SemanticCostModel.cost(semantic));
        } catch (IllegalArgumentException exception) {
            throw fault("INVALID_CREATION_FORM", exception.getMessage());
        }
    }

    private static String text(PlacedSigil sigil, int index, String name) {
        return textValue(sigil, index, name);
    }

    private static String textValue(PlacedSigil sigil, int index, String name) {
        if (!(sigil.parameters().get(index) instanceof CircleValue.TextValue value)) {
            throw fault("PARAMETER_TYPE", sigil.type() + " parameter " + (index + 1)
                    + " (" + name + ") must be text");
        }
        return value.value();
    }

    private static String identifier(PlacedSigil sigil, int index, String name) {
        String value = textValue(sigil, index, name);
        try {
            return AdvancedOperand.checkedName(value);
        } catch (IllegalArgumentException exception) {
            throw fault("INVALID_IDENTIFIER", sigil.type() + " parameter " + (index + 1)
                    + " (" + name + ") " + exception.getMessage());
        }
    }

    private static double number(PlacedSigil sigil, int index) {
        if (index >= sigil.parameters().size()
                || !(sigil.parameters().get(index) instanceof CircleValue.NumberValue value)) {
            throw fault("PARAMETER_TYPE", sigil.type() + " parameter " + (index + 1) + " must be numeric");
        }
        double result = value.value().doubleValue();
        if (!Double.isFinite(result)) throw fault("INVALID_NUMBER", "Number must be finite");
        return result;
    }

    private static int integer(PlacedSigil sigil, int index) {
        double value = number(sigil, index);
        if (value != Math.rint(value) || value < 0 || value > Integer.MAX_VALUE) {
            throw fault("PARAMETER_TYPE", "Control parameters must be non-negative integers");
        }
        return (int) value;
    }

    private static int boundedInteger(PlacedSigil sigil, int index, int maximum, String name) {
        int value = integer(sigil, index);
        if (value < 1 || value > maximum) {
            throw fault("PARAMETER_RANGE", name + " must be 1.." + maximum);
        }
        return value;
    }

    private static double positiveRange(PlacedSigil sigil, int index) {
        double value = number(sigil, index);
        if (value <= 0 || value > VmLimits.DEFAULT.maxPerceptionRange()) {
            throw fault("PARAMETER_RANGE", "range must be greater than 0 and at most "
                    + VmLimits.DEFAULT.maxPerceptionRange());
        }
        return value;
    }

    private static int target(PlacedSigil sigil, int index, int instructionCount, String name) {
        int value = integer(sigil, index);
        if (value >= instructionCount) {
            throw fault("TARGET_OUT_OF_RANGE", name + " target must be within the circle");
        }
        return value;
    }

    private static int oneInteger(PlacedSigil sigil) {
        requireCount(sigil, 1);
        return integer(sigil, 0);
    }

    private static void requireCount(PlacedSigil sigil, int expected) {
        if (sigil.parameters().size() != expected) {
            throw fault("PARAMETER_COUNT", sigil.type() + " expects " + expected
                    + " parameter(s), got " + sigil.parameters().size());
        }
    }

    private static CircleDiagnostic error(
            String code, String message, CircleCoordinate coordinate, int sourceIndex) {
        return new CircleDiagnostic(CircleDiagnostic.Severity.ERROR, code, message,
                coordinate, sourceIndex);
    }

    private static CompileFault fault(String code, String message) {
        return new CompileFault(code, message);
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public record Context(String casterEntityId, Vector3 origin, Vector3 lookVector) {
        public Context {
            if (casterEntityId == null || casterEntityId.isBlank()) {
                throw new IllegalArgumentException("caster entity id cannot be blank");
            }
            if (origin == null || lookVector == null) throw new NullPointerException("vm2 context vectors");
        }
    }

    private enum Physics { IMPULSE, ACCELERATION, DAMPING, FOLLOW_PATH, MOVE_TOWARD, KEEP_DISTANCE }

    private static final class CompileFault extends RuntimeException {
        private final String code;

        private CompileFault(String code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }
}
