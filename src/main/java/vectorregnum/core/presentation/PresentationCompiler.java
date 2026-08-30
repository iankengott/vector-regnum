package vectorregnum.core.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.semantic.SemanticSchema;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.Program;

/**
 * Deterministically lowers authoritative spell meaning into a curated sensory score.
 * Renderers are fixed identifiers and bounded numeric parameters; authored code can
 * never inject shaders, sounds, or arbitrary client behavior.
 */
public final class PresentationCompiler {
    private static final PresentationCost GESTURE = new PresentationCost(1, 18, 96, 0, 0, 0, 1);
    private static final PresentationCost MICRO = new PresentationCost(1, 8, 0, 0, 0, 0, 1);
    private static final PresentationCost SOUND = new PresentationCost(0, 0, 0, 0, 1, 0, 1);
    private static final PresentationCost LIGHT = new PresentationCost(0, 4, 0, 1, 0, 0, 1);
    private static final PresentationCost SCREEN = new PresentationCost(0, 0, 0, 0, 0, 1, 1);

    private PresentationCompiler() { }

    public static PresentationProgram compile(String spellId, long seed, Program program) {
        if (program == null) throw new NullPointerException("program");
        String id = normalizedId(spellId);
        List<SemanticInstruction> semantics = program.instructions().stream()
                .filter(instruction -> instruction.opcode() == Opcode.SEMANTIC)
                .map(Instruction::semantic).toList();
        Style style = Style.from(semantics);
        List<PresentationInstruction> cues = new ArrayList<>();
        addPrelude(cues, style);
        if (semantics.isEmpty()) {
            addVmCues(cues, program, style);
        } else {
            addSemanticCues(cues, semantics, style);
        }
        addEnding(cues, style);
        return new PresentationProgram("vector_regnum:" + id, seed, bounded(cues),
                PresentationBudget.DEFAULT);
    }

    private static List<PresentationInstruction> bounded(List<PresentationInstruction> candidates) {
        PresentationBudget budget = PresentationBudget.DEFAULT;
        List<PresentationInstruction> accepted = new ArrayList<>();
        PresentationCost cost = PresentationCost.ZERO;
        for (PresentationInstruction candidate : candidates) {
            if (accepted.size() >= budget.maximumCues()) break;
            PresentationCost next = cost.plus(candidate.cost());
            if (budget.allows(next)) {
                accepted.add(candidate);
                cost = next;
            }
        }
        return List.copyOf(accepted);
    }

    private static void addPrelude(List<PresentationInstruction> cues, Style style) {
        Map<String, Double> parameters = style.parameters();
        cues.add(cue(PresentationTrigger.cast(), PresentationPhase.INVOCATION,
                PresentationCueKind.RUNES, "truth/invocation_circle",
                PresentationBinding.CAST_ORIGIN, 0, 14, .72, true, parameters, GESTURE));
        cues.add(cue(PresentationTrigger.cast(), PresentationPhase.GATHERING,
                PresentationCueKind.PARTICLES, "sensory/gathering_motes",
                PresentationBinding.CAST_ORIGIN, 1, 18, .34, false, parameters, MICRO));
        PresentationCueKind atmosphere = switch (style.element) {
            case FIRE, VOID, DARK -> PresentationCueKind.DARKNESS;
            case ICE, WATER, AIR, EARTH, NATURE, SOUND -> PresentationCueKind.FOG;
            case ARCANE, LIGHT, LIGHTNING, TIME, SPACE -> PresentationCueKind.LIGHT;
        };
        String atmosphereRenderer = switch (atmosphere) {
            case DARKNESS -> "atmosphere/contrast_dimming";
            case FOG -> "atmosphere/ice_haze";
            default -> "atmosphere/elemental_glow";
        };
        cues.add(cue(PresentationTrigger.cast(), PresentationPhase.GATHERING,
                atmosphere, atmosphereRenderer,
                PresentationBinding.CAST_ORIGIN, 2, 14, .28, false, parameters,
                atmosphere == PresentationCueKind.LIGHT ? LIGHT : SCREEN));
        cues.add(cue(PresentationTrigger.cast(), PresentationPhase.INVOCATION,
                PresentationCueKind.SPATIAL_SOUND, "sound/invocation",
                PresentationBinding.CAST_ORIGIN, 0, 1, .42, false, parameters, SOUND));
    }

    private static void addSemanticCues(List<PresentationInstruction> cues,
            List<SemanticInstruction> semantics, Style style) {
        for (SemanticInstruction step : semantics) {
            PresentationTrigger trigger = PresentationTrigger.semantic(step.opcode());
            Map<String, Double> parameters = style.parametersWith(step);
            switch (step.opcode()) {
                case RAYCAST_BLOCK, RAYCAST_ENTITY -> cues.add(cue(trigger,
                        PresentationPhase.RELEASE, PresentationCueKind.BEAM,
                        "truth/raycast", PresentationBinding.DIRECTION, 0, 8, .58,
                        true, parameters, GESTURE));
                case SELECT_NEARBY_ENTITIES -> cues.add(cue(trigger,
                        PresentationPhase.GATHERING, PresentationCueKind.SURFACE,
                        "truth/selection_boundary", PresentationBinding.AFFECTED_AREA,
                        0, 16, .45, true, parameters, GESTURE));
                case FILTER_HOSTILE, FILTER_LIVING, FILTER_ORE -> cues.add(cue(trigger,
                        PresentationPhase.GATHERING, PresentationCueKind.RUNES,
                        "sensory/search_lattice", PresentationBinding.TARGET_SET,
                        0, 12, .28, false, parameters, MICRO));
                case SHAPE_PROJECTILE -> {
                    cues.add(cue(trigger, PresentationPhase.TRAVEL, PresentationCueKind.RIBBON,
                            "truth/projectile", PresentationBinding.DIRECTION,
                            0, 24, .82, true, parameters, GESTURE));
                    cues.add(cue(trigger, PresentationPhase.TRAVEL, PresentationCueKind.TRAIL,
                            "sensory/projectile_wake", PresentationBinding.DIRECTION,
                            1, 28, .32, false, parameters, MICRO));
                }
                case SHAPE_AURA, SHAPE_BARRIER -> cues.add(cue(trigger,
                        PresentationPhase.SUSTAIN, PresentationCueKind.VOLUME,
                        step.opcode() == SemanticOpcode.SHAPE_BARRIER
                                ? "truth/barrier" : "truth/aura",
                        PresentationBinding.AFFECTED_AREA, 0, style.durationTicks,
                        .66, true, parameters, GESTURE));
                case APPLY_DAMAGE, APPLY_SLOW, APPLY_IMPULSE -> {
                    cues.add(cue(trigger, PresentationPhase.IMPACT, PresentationCueKind.PARTICLES,
                            "truth/impact", PresentationBinding.IMPACT_POINT,
                            0, 10, .68, true, parameters, GESTURE));
                    cues.add(cue(trigger, PresentationPhase.CONTACT,
                            PresentationCueKind.MATERIAL_RESPONSE, "sensory/material_contact",
                            PresentationBinding.IMPACT_POINT, 0, 14, .30, false,
                            parameters, MICRO));
                }
                case APPLY_EXPLOSION -> {
                    cues.add(cue(trigger, PresentationPhase.IMPACT,
                            PresentationCueKind.PARTICLES, "truth/bounded_explosion",
                            PresentationBinding.IMPACT_POINT, 0, 16, .88,
                            true, parameters, GESTURE));
                    cues.add(cue(trigger, PresentationPhase.AFTERMATH,
                            PresentationCueKind.AFTERMATH, "aftermath/heat_wake",
                            PresentationBinding.AFFECTED_AREA, 2, 24, .34,
                            false, parameters, MICRO));
                }
                case TELEPORT_CASTER -> {
                    cues.add(cue(trigger, PresentationPhase.TRAVEL,
                            PresentationCueKind.RIBBON, "truth/teleport_path",
                            PresentationBinding.DIRECTION, 0, 10, .76,
                            true, parameters, GESTURE));
                    cues.add(cue(trigger, PresentationPhase.IMPACT,
                            PresentationCueKind.PARTICLES, "truth/teleport_arrival",
                            PresentationBinding.IMPACT_POINT, 0, 14, .64,
                            true, parameters, GESTURE));
                }
                case APPLY_FEATHERFALL -> cues.add(cue(trigger, PresentationPhase.SUSTAIN,
                        PresentationCueKind.AIR, "truth/featherfall",
                        PresentationBinding.TARGET, 0, style.durationTicks, .52,
                        true, withParameter(parameters, "radius", .65), GESTURE));
                case PLACE_LIGHT, CREATE_FORM, TRANSMUTE_BLOCK -> cues.add(cue(trigger,
                        PresentationPhase.CONTACT, PresentationCueKind.SURFACE,
                        "truth/world_change", PresentationBinding.IMPACT_POINT,
                        0, 18, .60, true, parameters, GESTURE));
                case BREAK_BLOCKS -> {
                    cues.add(cue(trigger, PresentationPhase.IMPACT,
                            PresentationCueKind.MATERIAL_RESPONSE, "truth/material_fracture",
                            PresentationBinding.AFFECTED_AREA, 0, 18, .64, true,
                            parameters, GESTURE));
                    cues.add(cue(trigger, PresentationPhase.AFTERMATH,
                            PresentationCueKind.AFTERMATH, "aftermath/displaced_dust",
                            PresentationBinding.AFFECTED_AREA, 4, 28, .26, false,
                            parameters, MICRO));
                }
                case EMIT_PARTICLES -> cues.add(cue(trigger, PresentationPhase.SUSTAIN,
                        PresentationCueKind.PARTICLES, "truth/resonance_reveal",
                        PresentationBinding.TARGET_SET, 0, 24, .52, true,
                        parameters, GESTURE));
                case EMIT_REDSTONE -> cues.add(cue(trigger, PresentationPhase.RELEASE,
                        PresentationCueKind.BEAM, "truth/redstone_pulse",
                        PresentationBinding.AFFECTED_AREA, 0, 14, .60, true,
                        parameters, GESTURE));
                case REPEAT_BOUNDED -> cues.add(cue(trigger, PresentationPhase.SUSTAIN,
                        PresentationCueKind.RUNES, "sensory/bounded_echo",
                        PresentationBinding.CAST_ORIGIN, 0, 20, .30, false,
                        parameters, MICRO));
                case WAIT_TICKS -> cues.add(cue(trigger, PresentationPhase.TENSION,
                        PresentationCueKind.AIR, "truth/delay_hold",
                        PresentationBinding.CAST_ORIGIN, 0,
                        Math.min(200, SemanticSchema.integer(step.operands(), "ticks")),
                        .38, true, parameters, GESTURE));
                default -> { }
            }
        }
    }

    private static void addVmCues(List<PresentationInstruction> cues, Program program, Style style) {
        for (Instruction instruction : program.instructions()) {
            switch (instruction.opcode()) {
                case IMPULSE, ACCELERATION, FOLLOW_PATH, MOVE_TOWARD, KEEP_DISTANCE ->
                        cues.add(cue(PresentationTrigger.opcode(instruction.opcode()),
                                PresentationPhase.TRAVEL, PresentationCueKind.RIBBON,
                                "truth/vector_motion", PresentationBinding.TARGET,
                                0, Math.min(80, Math.max(12, instruction.secondArgument())),
                                .58, true, style.parameters(), GESTURE));
                case SELECT_RADIUS, RAYCAST_ENTITIES -> cues.add(cue(
                        PresentationTrigger.opcode(instruction.opcode()),
                        PresentationPhase.GATHERING,
                        instruction.opcode() == Opcode.SELECT_RADIUS
                                ? PresentationCueKind.SURFACE : PresentationCueKind.BEAM,
                        instruction.opcode() == Opcode.SELECT_RADIUS
                                ? "truth/selection_boundary" : "truth/raycast",
                        instruction.opcode() == Opcode.SELECT_RADIUS
                                ? PresentationBinding.AFFECTED_AREA : PresentationBinding.DIRECTION,
                        0, 14, .45, true, style.parameters(), GESTURE));
                case DELAY -> cues.add(cue(PresentationTrigger.opcode(Opcode.DELAY),
                        PresentationPhase.TENSION, PresentationCueKind.AIR,
                        "truth/delay_hold", PresentationBinding.CAST_ORIGIN,
                        0, Math.min(200, Math.max(1, instruction.argument())), .38,
                        true, style.parameters(), GESTURE));
                case STORE_VARIABLE, LOAD_VARIABLE, WATCH_VARIABLE -> cues.add(cue(
                        PresentationTrigger.opcode(instruction.opcode()),
                        PresentationPhase.GATHERING, PresentationCueKind.RUNES,
                        "truth/shared_memory", PresentationBinding.CAST_ORIGIN,
                        0, 12, .42, true, style.parameters(), GESTURE));
                case ITERATOR_BEGIN, ITERATOR_NEXT -> cues.add(cue(
                        PresentationTrigger.opcode(instruction.opcode()),
                        PresentationPhase.SUSTAIN, PresentationCueKind.RIBBON,
                        "truth/sequential_iterator", PresentationBinding.CAST_ORIGIN,
                        0, 12, .40, true, style.parameters(), GESTURE));
                case COLLISION -> cues.add(cue(PresentationTrigger.opcode(Opcode.COLLISION),
                        PresentationPhase.IMPACT, PresentationCueKind.SURFACE,
                        "truth/collision_result", PresentationBinding.TARGET,
                        0, 10, .58, true, style.parameters(), GESTURE));
                case SIGNAL, OUTPUT -> cues.add(cue(
                        PresentationTrigger.opcode(instruction.opcode()),
                        PresentationPhase.IMPACT, PresentationCueKind.PARTICLES,
                        "truth/authoritative_output", PresentationBinding.CAST_ORIGIN,
                        0, 10, .52, true, style.parameters(), GESTURE));
                case FORK, JOIN, CANCEL_BRANCH, BRANCH_END -> cues.add(cue(
                        PresentationTrigger.opcode(instruction.opcode()),
                        PresentationPhase.SUSTAIN, PresentationCueKind.BEAM,
                        "truth/logical_branch", PresentationBinding.CAST_ORIGIN,
                        0, 12, .46, true, style.parameters(), GESTURE));
                default -> { }
            }
        }
    }

    private static void addEnding(List<PresentationInstruction> cues, Style style) {
        Map<String, Double> parameters = style.parameters();
        cues.add(cue(PresentationTrigger.halt(), PresentationPhase.DECAY,
                PresentationCueKind.AFTERMATH, "aftermath/residue",
                PresentationBinding.CAST_ORIGIN, 0, 36, .28, false, parameters, MICRO));
        cues.add(cue(PresentationTrigger.halt(), PresentationPhase.DECAY,
                PresentationCueKind.SPATIAL_SOUND, "sound/resonant_tail",
                PresentationBinding.CAST_ORIGIN, 0, 1, .34, false, parameters, SOUND));
        cues.add(cue(PresentationTrigger.fault(), PresentationPhase.IMPACT,
                PresentationCueKind.RUNES, "truth/fractured_program",
                PresentationBinding.CAST_ORIGIN, 0, 24, .78, true, parameters, GESTURE));
        cues.add(cue(PresentationTrigger.fault(), PresentationPhase.IMPACT,
                PresentationCueKind.SCREEN, "screen/fault_pressure",
                PresentationBinding.SCREEN, 0, 8, .32, false, parameters, SCREEN));
        cues.add(cue(PresentationTrigger.fault(), PresentationPhase.IMPACT,
                PresentationCueKind.SPATIAL_SOUND, "sound/fault",
                PresentationBinding.CAST_ORIGIN, 0, 1, .52, false, parameters, SOUND));
    }

    private static PresentationInstruction cue(PresentationTrigger trigger,
            PresentationPhase phase, PresentationCueKind kind, String renderer,
            PresentationBinding binding, int offset, int duration, double intensity,
            boolean truth, Map<String, Double> parameters, PresentationCost cost) {
        return new PresentationInstruction(trigger, phase, kind,
                "vector_regnum:" + renderer, binding, offset, Math.max(1, duration),
                intensity, truth, parameters, cost);
    }

    private static Map<String, Double> withParameter(
            Map<String, Double> parameters, String key, double value) {
        java.util.LinkedHashMap<String, Double> adjusted =
                new java.util.LinkedHashMap<>(parameters);
        adjusted.put(key, value);
        return Map.copyOf(adjusted);
    }

    private static String normalizedId(String id) {
        String normalized = Optional.ofNullable(id).orElse("authored_spell")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = "authored_spell";
        return normalized.substring(0, Math.min(96, normalized.length()));
    }

    private record Style(PresentationElement element, double radius, double magnitude,
            int durationTicks) {
        private static Style from(List<SemanticInstruction> instructions) {
            PresentationElement element = instructions.stream().map(SemanticInstruction::opcode)
                    .map(opcode -> switch (opcode) {
                        case ELEMENT_FIRE -> PresentationElement.FIRE;
                        case ELEMENT_ICE -> PresentationElement.ICE;
                        case ELEMENT_VOID -> PresentationElement.VOID;
                        case ELEMENT_ARCANE -> PresentationElement.ARCANE;
                        case ELEMENT_WATER -> PresentationElement.WATER;
                        case ELEMENT_AIR -> PresentationElement.AIR;
                        case ELEMENT_EARTH -> PresentationElement.EARTH;
                        case ELEMENT_LIGHTNING -> PresentationElement.LIGHTNING;
                        case ELEMENT_TIME -> PresentationElement.TIME;
                        case ELEMENT_SPACE -> PresentationElement.SPACE;
                        case ELEMENT_LIGHT -> PresentationElement.LIGHT;
                        case ELEMENT_DARK -> PresentationElement.DARK;
                        case ELEMENT_NATURE -> PresentationElement.NATURE;
                        case ELEMENT_SOUND -> PresentationElement.SOUND;
                        default -> null;
                    }).filter(java.util.Objects::nonNull).findFirst()
                    .orElse(PresentationElement.ARCANE);
            double radius = number(instructions, SemanticOpcode.SET_RADIUS, "blocks",
                    number(instructions, SemanticOpcode.SELECT_NEARBY_ENTITIES, "radius", 2.0));
            double magnitude = number(instructions, SemanticOpcode.SET_MAGNITUDE, "power", 1.0);
            int duration = (int) number(instructions, SemanticOpcode.SET_DURATION, "ticks", 32.0);
            return new Style(element, Math.clamp(radius, .5, 32.0),
                    Math.clamp(magnitude, .25, 8.0), Math.clamp(duration, 8, 200));
        }

        private Map<String, Double> parameters() {
            return Map.of("element", element.parameter(), "radius", radius,
                    "magnitude", magnitude, "duration", (double) durationTicks);
        }

        private Map<String, Double> parametersWith(SemanticInstruction instruction) {
            java.util.LinkedHashMap<String, Double> values = new java.util.LinkedHashMap<>(parameters());
            instruction.operands().forEach((key, value) -> {
                if (value instanceof vectorregnum.core.semantic.SemanticValue.NumberValue number
                        && values.size() < 16) {
                    values.put(key, number.value());
                }
            });
            return Map.copyOf(values);
        }

        private static double number(List<SemanticInstruction> instructions,
                SemanticOpcode opcode, String key, double fallback) {
            return instructions.stream().filter(step -> step.opcode() == opcode).findFirst()
                    .map(step -> SemanticSchema.number(step.operands(), key)).orElse(fallback);
        }
    }
}
