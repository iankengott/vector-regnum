package vectorregnum.core.semantic;

import vectorregnum.core.vm2.ManaCostModel;

/** Deterministic named vm2 cost dimensions for one validated semantic step. */
public final class SemanticCostModel {
    private SemanticCostModel() { }

    public static boolean isRepeatableAction(SemanticOpcode opcode) {
        return switch (opcode) {
            case APPLY_DAMAGE, APPLY_EXPLOSION, APPLY_IMPULSE, APPLY_SLOW, APPLY_FEATHERFALL, PLACE_LIGHT,
                    BREAK_BLOCKS, TRANSMUTE_BLOCK, CREATE_FORM, EMIT_PARTICLES,
                    EMIT_REDSTONE, RENDER, FORCE_ATTENTION -> true;
            default -> false;
        };
    }

    public static ManaCostModel.Input cost(SemanticInstruction instruction) {
        double work = 0, range = 0, rarity = 0;
        int duration = 0, memory = 1, perception = 0, control = 0;
        switch (instruction.opcode()) {
            case SELECT_NEARBY_ENTITIES -> {
                range = SemanticSchema.number(instruction.operands(), "radius");
                perception = Math.max(1, (int) Math.ceil(range / 4.0));
            }
            case RAYCAST_BLOCK, RAYCAST_ENTITY -> { range = 24; perception = 2; }
            case FILTER_HOSTILE, FILTER_LIVING -> perception = 1;
            case FILTER_ORE -> perception = 32;
            case SET_RADIUS -> range = SemanticSchema.number(instruction.operands(), "blocks");
            case SET_MAGNITUDE -> {
                double power = SemanticSchema.number(instruction.operands(), "power");
                work = power * power * 10.0;
            }
            case SET_TARGET_LIMIT ->
                    control = SemanticSchema.integer(instruction.operands(), "count");
            case SET_DURATION -> duration = SemanticSchema.integer(instruction.operands(), "ticks");
            case APPLY_DAMAGE -> work = 25;
            case APPLY_EXPLOSION -> { work = 80; rarity = 1; }
            case APPLY_IMPULSE -> work = 20;
            case APPLY_SLOW, APPLY_FEATHERFALL -> rarity = 0.5;
            case PLACE_LIGHT -> rarity = 2;
            case BREAK_BLOCKS -> work = 60;
            case TRANSMUTE_BLOCK -> rarity = 4;
            case CREATE_FORM -> { return instruction.creationSpec().declaredCost(); }
            case EMIT_PARTICLES -> perception = 1;
            case EMIT_REDSTONE -> rarity = 1;
            case REPEAT_BOUNDED -> {
                int count = SemanticSchema.integer(instruction.operands(), "count");
                control = count; work = count * 5.0; perception = count;
            }
            case WAIT_TICKS -> {
                duration = SemanticSchema.integer(instruction.operands(), "ticks"); control = 1;
            }
            case TELEPORT_CASTER -> { work = 100; rarity = 3; }
            case RENDER -> perception = 1;
            case FORCE_ATTENTION -> {
                range = SemanticSchema.number(instruction.operands(), "range");
                duration = SemanticSchema.integer(instruction.operands(), "ticks");
                control = 2;
                rarity = 2;
            }
            default -> { }
        }
        return new ManaCostModel.Input(work, range, duration, rarity, memory, perception, control);
    }
}
