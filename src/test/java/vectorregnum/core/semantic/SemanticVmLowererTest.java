package vectorregnum.core.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.SpellVm;
import vectorregnum.core.vm2.WorldAccess;
import vectorregnum.core.vm2.WorldEffect;

class SemanticVmLowererTest {
    @Test
    void completeRegistryHasAnExecutablePathForEverySemanticOpcode() {
        assertTrue(SemanticVmLowerer.missingOpcodes().isEmpty());
        List<SemanticInstruction> steps = new ArrayList<>();
        int index = 0;
        for (SemanticOpcode opcode : SemanticOpcode.values()) {
            if (opcode == SemanticOpcode.EXECUTE) continue;
            steps.add(valid(opcode, index++));
        }
        steps.add(SemanticInstruction.simple(SemanticOpcode.EXECUTE,
                SourceLocation.at(index, "EXECUTE")));
        var program = SemanticVmLowerer.lowerChecked(new SemanticProgram(steps),
                new LoweringContext("coverage", 1, Map.of()));
        assertTrue(program.instructions().stream().anyMatch(i -> i.opcode() == Opcode.SEMANTIC));
        SpellVm vm = new SpellVm(program, WorldAccess.EMPTY);
        while (!vm.isTerminal()) vm.tick();
        assertTrue(vm.fault().isEmpty());
        assertEquals(SemanticOpcode.values().length,
                vm.allEffects().stream().filter(WorldEffect.SemanticStep.class::isInstance).count());
    }

    @Test
    void creationExecutesAsBoundedCostedVmEffectAtExactSource() {
        SourceLocation source = new SourceLocation(7, 3, 5, "CREATE_FORM");
        CreationSpec spec = new CreationSpec(CreationMaterial.ICE,
                CreationForm.BARRIER, 12, 80, false);
        var program = SemanticVmLowerer.lowerChecked(new SemanticProgram(List.of(
                SemanticInstruction.creation(spec, source),
                SemanticInstruction.simple(SemanticOpcode.EXECUTE, SourceLocation.at(8, "EXECUTE")))),
                new LoweringContext("ice_wall", 2, Map.of()));
        SpellVm vm = new SpellVm(program, WorldAccess.EMPTY);
        while (!vm.isTerminal()) vm.tick();
        WorldEffect.SemanticStep effect = assertInstanceOf(WorldEffect.SemanticStep.class,
                vm.allEffects().getFirst());
        assertEquals(source, effect.instruction().source());
        assertEquals(spec, effect.instruction().creationSpec());
        assertTrue(program.manaCost().rarity() > 0);
        assertTrue(program.manaCost().physicalWork() > 0);
    }

    @Test
    void boundedRepeatQuotesEveryExecutionOfThePreviousAction() {
        SourceLocation action = SourceLocation.at(0, "BREAK_BLOCKS");
        SourceLocation repeat = SourceLocation.at(1, "REPEAT_BOUNDED");
        SemanticInstruction breakBlocks = text(SemanticOpcode.BREAK_BLOCKS,
                "mode", "mature_crops", action);
        SemanticProgram once = new SemanticProgram(List.of(breakBlocks,
                SemanticInstruction.simple(SemanticOpcode.EXECUTE,
                        SourceLocation.at(1, "EXECUTE"))));
        SemanticProgram eight = new SemanticProgram(List.of(breakBlocks,
                number(SemanticOpcode.WAIT_TICKS, "ticks", 100,
                        SourceLocation.at(1, "WAIT_TICKS")),
                number(SemanticOpcode.REPEAT_BOUNDED, "count", 8, repeat),
                SemanticInstruction.simple(SemanticOpcode.EXECUTE,
                        SourceLocation.at(3, "EXECUTE"))));
        var context = new LoweringContext("repeat-cost", 1, Map.of());
        var onceProgram = SemanticVmLowerer.lowerChecked(once, context);
        var eightProgram = SemanticVmLowerer.lowerChecked(eight, context);
        assertTrue(eightProgram.declaredCost().physicalWork()
                >= onceProgram.declaredCost().physicalWork() * 8.0);

    }

    private static SemanticInstruction valid(SemanticOpcode opcode, int index) {
        SourceLocation source = SourceLocation.at(index, opcode.name());
        return switch (opcode) {
            case SELECT_NEARBY_ENTITIES -> number(opcode, "radius", 8, source);
            case SET_RADIUS -> number(opcode, "blocks", 4, source);
            case SET_MAGNITUDE -> number(opcode, "power", 2, source);
            case SET_TARGET_LIMIT -> number(opcode, "count", 4, source);
            case SET_DURATION -> number(opcode, "ticks", 20, source);
            case EMIT_REDSTONE -> number(opcode, "strength", 15, source);
            case REPEAT_BOUNDED -> number(opcode, "count", 2, source);
            case WAIT_TICKS -> number(opcode, "ticks", 0, source);
            case APPLY_IMPULSE -> text(opcode, "direction", "away", source);
            case BREAK_BLOCKS -> text(opcode, "mode", "safe", source);
            case TRANSMUTE_BLOCK -> text(opcode, "into", "minecraft:stone", source);
            case EMIT_PARTICLES -> text(opcode, "style", "outline", source);
            case CREATE_FORM -> SemanticInstruction.creation(new CreationSpec(
                    CreationMaterial.LIGHT, CreationForm.FIELD, 1, 20, false), source);
            default -> SemanticInstruction.simple(opcode, source);
        };
    }

    private static SemanticInstruction number(SemanticOpcode opcode, String key,
            double value, SourceLocation source) {
        return new SemanticInstruction(opcode,
                Map.of(key, new SemanticValue.NumberValue(value)), source);
    }

    private static SemanticInstruction text(SemanticOpcode opcode, String key,
            String value, SourceLocation source) {
        return new SemanticInstruction(opcode,
                Map.of(key, new SemanticValue.TextValue(value)), source);
    }
}
