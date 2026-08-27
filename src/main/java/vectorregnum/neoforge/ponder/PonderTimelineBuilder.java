package vectorregnum.neoforge.ponder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.CastResult;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleDiagnostic;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.Vm2CircleCompilation;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.ManaCostModel;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.TickResult;
import vectorregnum.core.vm2.VmFault;
import vectorregnum.core.vm2.WorldEffect;

/** Converts actual compiler/runtime results into deterministic teaching cues. */
public final class PonderTimelineBuilder {
    private final String id;
    private final String title;
    private final List<PonderTimeline.Step> steps = new ArrayList<>();

    private PonderTimelineBuilder(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public static PonderTimeline fromVm2(String id, String title,
            Vm2CircleCompilation compilation, List<TickResult> tickResults) {
        Objects.requireNonNull(compilation, "compilation");
        Objects.requireNonNull(tickResults, "tickResults");
        PonderTimelineBuilder builder = new PonderTimelineBuilder(id, title);
        builder.compilation(compilation.executionOrder(), compilation.diagnostics());
        compilation.compiledProgram().ifPresent(program -> {
            builder.mana(program.manaCost());
            builder.execution(program, compilation.executionOrder(), tickResults);
        });
        return builder.finish();
    }

    public static PonderTimeline fromCompatibility(String id, String title,
            CircleCompilation compilation, CastResult result) {
        Objects.requireNonNull(compilation, "compilation");
        Objects.requireNonNull(result, "result");
        PonderTimelineBuilder builder = new PonderTimelineBuilder(id, title);
        builder.compilation(compilation.executionOrder(), compilation.diagnostics());
        compilation.spell().ifPresent(spell -> builder.scalarMana(spell.totalManaCost(),
                spell.totalComplexity()));
        builder.compatibilityResult(compilation.executionOrder(), result);
        return builder.finish();
    }

    private void compilation(List<PlacedSigil> order, List<CircleDiagnostic> diagnostics) {
        add(24, PonderTimeline.Phase.COMPILATION, "Read the circle",
                "Compilation begins at north on the outermost ring.",
                cue(PonderTimeline.CueType.FOCUS_CIRCLE, null,
                        Map.of("sigils", Integer.toString(order.size()))),
                cue(PonderTimeline.CueType.SET_PIECE, null,
                        Map.of("stage", "scribe_workshop", "artifact", "scroll")),
                cue(PonderTimeline.CueType.CAMERA_FOCUS, null,
                        Map.of("target", "circle", "motion", "orbit")));
        PlacedSigil previous = null;
        for (int index = 0; index < order.size(); index++) {
            PlacedSigil sigil = order.get(index);
            PonderTimeline.CueType trace = previous != null
                    && previous.coordinate().ring() != sigil.coordinate().ring()
                    ? PonderTimeline.CueType.MOVE_INWARD : PonderTimeline.CueType.TRACE_CLOCKWISE;
            add(12, PonderTimeline.Phase.COMPILATION, "Compile " + sigil.type(),
                    "Step " + (index + 1) + " reads ring " + sigil.coordinate().ring()
                            + ", slot " + sigil.coordinate().clockwiseSlot() + ".",
                    cue(PonderTimeline.CueType.HIGHLIGHT_SIGIL, source(index, sigil),
                            Map.of("order", Integer.toString(index + 1), "sigil", sigil.type())),
                    cue(trace, source(index, sigil), Map.of("direction", "clockwise_then_inward")),
                    cue(PonderTimeline.CueType.CAMERA_FOCUS, source(index, sigil),
                            Map.of("target", "sigil", "motion", "track")));
            previous = sigil;
        }
        for (CircleDiagnostic diagnostic : diagnostics) {
            Optional<PonderTimeline.SourceRef> at = sourceForDiagnostic(diagnostic, order);
            add(32, PonderTimeline.Phase.FAULT, "Compiler fault: " + diagnostic.code(),
                    diagnostic.message(), cue(PonderTimeline.CueType.COMPILER_FAULT, at.orElse(null),
                            Map.of("severity", diagnostic.severity().name(), "code", diagnostic.code())),
                    cue(PonderTimeline.CueType.FAULT_FRACTURE, at.orElse(null),
                            Map.of("kind", "compiler", "severity", diagnostic.severity().name())));
        }
    }

    private void mana(ManaCostModel.Breakdown cost) {
        LinkedHashMap<String, Double> segments = new LinkedHashMap<>();
        segments.put("base", cost.base());
        segments.put("physical_work", cost.physicalWork());
        segments.put("range", cost.range());
        segments.put("duration", cost.duration());
        segments.put("rarity", cost.rarity());
        segments.put("memory", cost.memory());
        segments.put("perception", cost.perception());
        segments.put("control_flow", cost.controlFlow());
        for (Map.Entry<String, Double> segment : segments.entrySet()) {
            add(10, PonderTimeline.Phase.MANA, humanize(segment.getKey()),
                    humanize(segment.getKey()) + " contributes " + decimal(segment.getValue()) + " μ.",
                    cue(PonderTimeline.CueType.MANA_SEGMENT, null,
                            Map.of("dimension", segment.getKey(), "amount", decimal(segment.getValue()),
                                    "total", decimal(cost.total()))),
                    cue(PonderTimeline.CueType.MANA_FLOW, null,
                            Map.of("from", "crystal", "to", "scroll", "amount", decimal(segment.getValue()))));
        }
    }

    private void scalarMana(double total, long complexity) {
        add(24, PonderTimeline.Phase.MANA, "Compatibility mana quote",
                "The compiled scroll declares " + decimal(total) + " μ before it can run.",
                cue(PonderTimeline.CueType.MANA_SEGMENT, null,
                        Map.of("dimension", "compatibility_total", "amount", decimal(total),
                                "complexity", Long.toString(complexity))),
                cue(PonderTimeline.CueType.MANA_FLOW, null,
                        Map.of("from", "crystal", "to", "scroll", "amount", decimal(total))));
    }

    private void execution(Program program, List<PlacedSigil> order, List<TickResult> results) {
        int available = PonderTimeline.MAX_STEPS - steps.size();
        if (available < 1) return;
        List<IndexedResult> shown = boundedResults(results, available);
        int previousTick = -1;
        for (IndexedResult indexed : shown) {
            int tick = indexed.tick();
            if (previousTick >= 0 && tick > previousTick + 1) {
                int omitted = tick - previousTick - 1;
                add(16, PonderTimeline.Phase.EXECUTION, "Trace compacted",
                        omitted + " repetitive server ticks were omitted while retaining the terminal state.",
                        cue(PonderTimeline.CueType.TRACE_COMPACTED, null, Map.of(
                                "omitted_ticks", Integer.toString(omitted),
                                "resumes_at_tick", Integer.toString(tick))));
            }
            TickResult result = Objects.requireNonNull(indexed.result(), "tick result");
            Optional<PonderTimeline.SourceRef> at = sourceForPointer(program, order,
                    result.instructionPointer());
            PonderTimeline.CueType statusCue = switch (result.status()) {
                case WAITING -> PonderTimeline.CueType.WAIT;
                case BUDGET_YIELD -> PonderTimeline.CueType.BUDGET_YIELD;
                case FAULTED -> PonderTimeline.CueType.RUNTIME_FAULT;
                default -> PonderTimeline.CueType.EXECUTION_CURSOR;
            };
            List<PonderTimeline.Cue> cues = new ArrayList<>();
            cues.add(cue(statusCue, at.orElse(null), Map.of(
                    "tick", Integer.toString(tick),
                    "status", result.status().name(),
                    "instruction_pointer", Integer.toString(result.instructionPointer()),
                    "instructions_executed", Integer.toString(result.instructionsExecuted()))));
            int shownEffects = Math.min(result.effects().size(),
                    PonderTimeline.MAX_CUES_PER_STEP - 4);
            for (int effectIndex = 0; effectIndex < shownEffects; effectIndex++) {
                WorldEffect effect = result.effects().get(effectIndex);
                cues.add(cue(PonderTimeline.CueType.WORLD_EFFECT, at.orElse(null), Map.of(
                        "effect", effect.getClass().getSimpleName(),
                        "entity", effect.entityId(),
                        "duration_ticks", Integer.toString(effect.durationTicks()))));
            }
            if (shownEffects < result.effects().size()) {
                cues.add(cue(PonderTimeline.CueType.TRACE_COMPACTED, at.orElse(null), Map.of(
                        "omitted_effects", Integer.toString(result.effects().size() - shownEffects),
                        "reason", "per_step_cue_limit")));
            }
            result.fault().ifPresent(fault -> cues.add(runtimeFaultCue(fault, order)));
            if (result.status() == TickResult.Status.FAULTED) {
                cues.add(cue(PonderTimeline.CueType.FAULT_FRACTURE, at.orElse(null),
                        Map.of("kind", "runtime", "severity", "ERROR")));
            }
            add(result.status() == TickResult.Status.WAITING ? 16 : 10,
                    result.status() == TickResult.Status.FAULTED
                            ? PonderTimeline.Phase.FAULT : PonderTimeline.Phase.EXECUTION,
                    "VM tick " + tick + ": " + humanize(result.status().name()),
                    executionNarration(result), cues.toArray(PonderTimeline.Cue[]::new));
            previousTick = tick;
        }
    }

    private static List<IndexedResult> boundedResults(List<TickResult> results, int available) {
        if (results.size() <= available) {
            List<IndexedResult> all = new ArrayList<>(results.size());
            for (int tick = 0; tick < results.size(); tick++) {
                all.add(new IndexedResult(tick, Objects.requireNonNull(results.get(tick), "tick result")));
            }
            return all;
        }
        if (available == 1) {
            return List.of(new IndexedResult(results.size() - 1,
                    Objects.requireNonNull(results.getLast(), "tick result")));
        }
        int resultSlots = available - 1;
        int head = resultSlots / 2;
        int tail = resultSlots - head;
        List<IndexedResult> sampled = new ArrayList<>(resultSlots);
        for (int tick = 0; tick < head; tick++) {
            sampled.add(new IndexedResult(tick, Objects.requireNonNull(results.get(tick), "tick result")));
        }
        for (int tick = results.size() - tail; tick < results.size(); tick++) {
            sampled.add(new IndexedResult(tick, Objects.requireNonNull(results.get(tick), "tick result")));
        }
        return sampled;
    }

    private void compatibilityResult(List<PlacedSigil> order, CastResult result) {
        if (result instanceof CastResult.SpellFailure failure) {
            PonderTimeline.SourceRef at = order.isEmpty() ? null : source(
                    Math.min(failure.fault().sourceIndex(), order.size() - 1),
                    order.get(Math.min(failure.fault().sourceIndex(), order.size() - 1)));
            add(40, PonderTimeline.Phase.FAULT,
                    "Wild Magic: " + humanize(failure.fault().wildMagicCategory().name()),
                    failure.fault().message(), cue(PonderTimeline.CueType.WILD_MAGIC, at,
                            Map.of("fault", failure.fault().code().name(),
                                    "category", failure.fault().wildMagicCategory().name())),
                    cue(PonderTimeline.CueType.FAULT_FRACTURE, at,
                            Map.of("kind", "wild_magic",
                                    "severity", failure.fault().wildMagicCategory().name())),
                    cue(PonderTimeline.CueType.SCROLL_STATE, null,
                            Map.of("state", "genuine_fault_consumed", "medium", "scroll")),
                    cue(PonderTimeline.CueType.ESCROW_STATE, null,
                            Map.of("state", "consumed", "reason", "genuine_spell_fault")));
            return;
        }
        PonderTimeline.CueType type = result.effects().stream().anyMatch(EffectCommand.WildMagic.class::isInstance)
                ? PonderTimeline.CueType.WILD_MAGIC : PonderTimeline.CueType.WORLD_EFFECT;
        add(24, result.status() == CastResult.Status.SUCCESS
                        ? PonderTimeline.Phase.EXECUTION : PonderTimeline.Phase.FAULT,
                "Cast result: " + humanize(result.status().name()),
                "The authoritative cast reports " + result.status().name().toLowerCase() + ".",
                cue(type, null, Map.of("status", result.status().name(),
                        "effects", Integer.toString(result.effects().size()))),
                cue(PonderTimeline.CueType.SCROLL_STATE, null,
                        Map.of("state", result.status() == CastResult.Status.SUCCESS
                                ? "accepted_and_consumed" : "refunded_after_failure",
                                "medium", "scroll")),
                cue(PonderTimeline.CueType.ESCROW_STATE, null,
                        Map.of("state", result.status() == CastResult.Status.SUCCESS
                                ? "consumed" : "refunded",
                                "reason", result.status() == CastResult.Status.SUCCESS
                                        ? "success" : "engine_failure")));
    }

    private PonderTimeline finish() {
        return new PonderTimeline(id, title, steps);
    }

    private void add(int ticks, PonderTimeline.Phase phase, String stepTitle,
            String narration, PonderTimeline.Cue... cues) {
        if (steps.size() >= PonderTimeline.MAX_STEPS) {
            throw new IllegalArgumentException("Ponder exceeds " + PonderTimeline.MAX_STEPS + " steps");
        }
        steps.add(new PonderTimeline.Step(steps.size(), ticks, phase, stepTitle,
                narration, List.of(cues)));
    }

    private static PonderTimeline.Cue cue(PonderTimeline.CueType type,
            PonderTimeline.SourceRef source, Map<String, String> data) {
        return new PonderTimeline.Cue(type, Optional.ofNullable(source), data);
    }

    private static PonderTimeline.SourceRef source(int index, PlacedSigil sigil) {
        return new PonderTimeline.SourceRef(index, sigil.coordinate().ring(),
                sigil.coordinate().clockwiseSlot(), sigil.type());
    }

    private static Optional<PonderTimeline.SourceRef> sourceForDiagnostic(
            CircleDiagnostic diagnostic, List<PlacedSigil> order) {
        if (diagnostic.sourceIndex() >= 0 && diagnostic.sourceIndex() < order.size()) {
            return Optional.of(source(diagnostic.sourceIndex(), order.get(diagnostic.sourceIndex())));
        }
        CircleCoordinate coordinate = diagnostic.coordinate();
        if (coordinate == null) return Optional.empty();
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).coordinate().equals(coordinate)) return Optional.of(source(index, order.get(index)));
        }
        return Optional.empty();
    }

    private static Optional<PonderTimeline.SourceRef> sourceForPointer(Program program,
            List<PlacedSigil> order, int pointer) {
        if (program.instructions().isEmpty() || order.isEmpty()) return Optional.empty();
        Instruction instruction = program.instructions().get(Math.min(pointer,
                program.instructions().size() - 1));
        SourceLocation location = instruction.source();
        if (location.sourceIndex() >= order.size()) return Optional.empty();
        return Optional.of(source(location.sourceIndex(), order.get(location.sourceIndex())));
    }

    private static PonderTimeline.Cue runtimeFaultCue(VmFault fault, List<PlacedSigil> order) {
        PonderTimeline.SourceRef source = fault.source().sourceIndex() < order.size()
                ? source(fault.source().sourceIndex(), order.get(fault.source().sourceIndex())) : null;
        return cue(PonderTimeline.CueType.RUNTIME_FAULT, source,
                Map.of("code", fault.code().name(), "message", fault.message(),
                        "instruction_pointer", Integer.toString(fault.instructionPointer())));
    }

    private static String executionNarration(TickResult result) {
        return switch (result.status()) {
            case RUNNING -> "The server VM continues after executing " + result.instructionsExecuted() + " instructions.";
            case WAITING -> "A declared delay pauses execution without blocking the server tick.";
            case BUDGET_YIELD -> "The per-tick work bound yields safely; execution resumes next tick.";
            case HALTED -> "The program reaches its terminal sigil and the cast completes.";
            case FAULTED -> result.fault().map(VmFault::message).orElse("The VM rejects the operation.");
        };
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String humanize(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record IndexedResult(int tick, TickResult result) {
    }
}
