package vectorregnum.neoforge.progression;

import vectorregnum.core.vm2.ManaCostModel;

/** Quotes curated semantic programs through the same named vm2 cost dimensions. */
public final class LibrarySpellCostModel {
    private LibrarySpellCostModel() {
    }

    public static ManaCostModel.Breakdown estimate(SpellDefinition spell) {
        ManaCostModel.Input total = ManaCostModel.Input.ZERO;
        for (SpellInstruction instruction : spell.program()) {
            total = total.plus(cost(instruction));
        }
        return ManaCostModel.estimate(total);
    }

    private static ManaCostModel.Input cost(SpellInstruction instruction) {
        double work = 0.0;
        double range = 0.0;
        int duration = 0;
        double rarity = 0.0;
        int memory = 1;
        int perception = 0;
        int control = 0;
        switch (instruction.opcode()) {
            case SELECT_NEARBY_ENTITIES -> {
                range = operand(instruction, "radius", 0.0);
                perception = Math.max(1, (int) Math.ceil(range / 4.0));
            }
            case RAYCAST_BLOCK, RAYCAST_ENTITY -> {
                range = 24.0;
                perception = 2;
            }
            case FILTER_HOSTILE, FILTER_LIVING -> perception = 1;
            case FILTER_ORE -> perception = 32;
            case SET_RADIUS -> range = operand(instruction, "blocks", 0.0);
            case SET_MAGNITUDE -> {
                double magnitude = operand(instruction, "power", 1.0);
                work = magnitude * magnitude * 10.0;
            }
            case SET_DURATION -> duration = boundedInt(instruction, "ticks");
            case APPLY_DAMAGE -> work = 25.0;
            case APPLY_IMPULSE -> work = 20.0;
            case APPLY_SLOW, APPLY_FEATHERFALL -> rarity = 0.5;
            case PLACE_LIGHT -> rarity = 2.0;
            case BREAK_BLOCKS -> work = 60.0;
            case TRANSMUTE_BLOCK -> rarity = 4.0;
            case EMIT_PARTICLES -> perception = 1;
            case EMIT_REDSTONE -> rarity = 1.0;
            case REPEAT_BOUNDED -> {
                int count = boundedInt(instruction, "count");
                control = count;
                work = count * 5.0;
                perception = count;
            }
            case WAIT_TICKS -> {
                duration = boundedInt(instruction, "ticks");
                control = 1;
            }
            default -> { }
        }
        return new ManaCostModel.Input(work, range, duration, rarity,
                memory, perception, control);
    }

    private static int boundedInt(SpellInstruction instruction, String key) {
        double value = operand(instruction, key, 0.0);
        if (value < 0.0 || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            throw new IllegalArgumentException(instruction.opcode() + " needs integer " + key);
        }
        return (int) value;
    }

    private static double operand(SpellInstruction instruction, String key, double fallback) {
        Object value = instruction.operands().get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0.0) {
            throw new IllegalArgumentException(instruction.opcode() + " has invalid " + key);
        }
        return number.doubleValue();
    }
}
