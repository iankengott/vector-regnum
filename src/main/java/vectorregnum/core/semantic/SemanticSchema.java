package vectorregnum.core.semantic;

import java.util.Map;
import java.util.Set;

/** Exact operand contracts and safety envelopes for the stable semantic vocabulary. */
public final class SemanticSchema {
    private static final Set<SemanticOpcode> NO_OPERANDS = Set.of(
            SemanticOpcode.ORIGIN_SELF, SemanticOpcode.ORIGIN_TARGET,
            SemanticOpcode.LOOK_VECTOR, SemanticOpcode.RAYCAST_BLOCK,
            SemanticOpcode.RAYCAST_ENTITY, SemanticOpcode.FILTER_HOSTILE,
            SemanticOpcode.FILTER_LIVING, SemanticOpcode.FILTER_ORE,
            SemanticOpcode.ELEMENT_FIRE, SemanticOpcode.ELEMENT_FROST,
            SemanticOpcode.ELEMENT_ARCANE, SemanticOpcode.ELEMENT_VOID,
            SemanticOpcode.SHAPE_PROJECTILE, SemanticOpcode.SHAPE_AURA,
            SemanticOpcode.SHAPE_BARRIER, SemanticOpcode.APPLY_DAMAGE,
            SemanticOpcode.APPLY_SLOW, SemanticOpcode.APPLY_FEATHERFALL,
            SemanticOpcode.PLACE_LIGHT, SemanticOpcode.EXECUTE);

    private SemanticSchema() { }

    public static void validate(SemanticOpcode opcode, Map<String, SemanticValue> operands) {
        if (NO_OPERANDS.contains(opcode)) {
            exact(opcode, operands, Set.of());
            return;
        }
        switch (opcode) {
            case SELECT_NEARBY_ENTITIES -> number(opcode, operands, "radius", 0.0, 128.0, false);
            case SET_RADIUS -> number(opcode, operands, "blocks", 0.0, 128.0, false);
            case SET_MAGNITUDE -> number(opcode, operands, "power", 0.0, 16.0, false);
            case SET_DURATION -> integer(opcode, operands, "ticks", 1, 1_200);
            case EMIT_REDSTONE -> integer(opcode, operands, "strength", 0, 15);
            case REPEAT_BOUNDED -> integer(opcode, operands, "count", 1, 1_024);
            case WAIT_TICKS -> integer(opcode, operands, "ticks", 0, 1_200);
            case APPLY_IMPULSE -> oneText(opcode, operands, Set.of("direction", "target"),
                    Set.of("down", "away", "caster"));
            case BREAK_BLOCKS -> text(opcode, operands, "mode", Set.of("safe", "mature_crops"));
            case TRANSMUTE_BLOCK -> text(opcode, operands, "into", Set.of());
            case EMIT_PARTICLES -> text(opcode, operands, "style", Set.of("outline", "vein_trace"));
            case CREATE_FORM -> exact(opcode, operands, Set.of("spec"));
            default -> throw new IllegalStateException("No semantic schema for " + opcode);
        }
    }

    public static double number(Map<String, SemanticValue> operands, String key) {
        if (!(operands.get(key) instanceof SemanticValue.NumberValue number)) {
            throw new IllegalArgumentException("operand '" + key + "' must be a number");
        }
        return number.value();
    }

    public static int integer(Map<String, SemanticValue> operands, String key) {
        return (int) number(operands, key);
    }

    public static String text(Map<String, SemanticValue> operands, String key) {
        if (!(operands.get(key) instanceof SemanticValue.TextValue text)) {
            throw new IllegalArgumentException("operand '" + key + "' must be text");
        }
        return text.value();
    }

    private static void number(SemanticOpcode opcode, Map<String, SemanticValue> operands,
            String key, double minimum, double maximum, boolean inclusiveMinimum) {
        exact(opcode, operands, Set.of(key));
        double value = number(operands, key);
        if ((inclusiveMinimum ? value < minimum : value <= minimum) || value > maximum) {
            throw new IllegalArgumentException(opcode + " operand '" + key + "' must be "
                    + (inclusiveMinimum ? minimum + ".." : "> " + minimum + " and <= ") + maximum);
        }
    }

    private static void integer(SemanticOpcode opcode, Map<String, SemanticValue> operands,
            String key, int minimum, int maximum) {
        exact(opcode, operands, Set.of(key));
        double value = number(operands, key);
        if (value != Math.rint(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(opcode + " operand '" + key + "' must be an integer "
                    + minimum + ".." + maximum);
        }
    }

    private static void text(SemanticOpcode opcode, Map<String, SemanticValue> operands,
            String key, Set<String> allowed) {
        exact(opcode, operands, Set.of(key));
        String value = text(operands, key);
        if (!allowed.isEmpty() && !allowed.contains(value)) {
            throw new IllegalArgumentException(opcode + " operand '" + key + "' must be one of " + allowed);
        }
    }

    private static void oneText(SemanticOpcode opcode, Map<String, SemanticValue> operands,
            Set<String> keys, Set<String> allowed) {
        if (operands.size() != 1 || keys.stream().noneMatch(operands::containsKey)) {
            throw new IllegalArgumentException(opcode + " requires exactly one of " + keys);
        }
        String key = operands.keySet().iterator().next();
        text(opcode, operands, key, allowed);
    }

    private static void exact(SemanticOpcode opcode, Map<String, SemanticValue> operands,
            Set<String> expected) {
        if (!operands.keySet().equals(expected)) {
            throw new IllegalArgumentException(opcode + " requires operands " + expected);
        }
    }
}
