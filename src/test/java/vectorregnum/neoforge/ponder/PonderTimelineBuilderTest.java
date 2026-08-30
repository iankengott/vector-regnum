package vectorregnum.neoforge.ponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vectorregnum.core.CastContext;
import vectorregnum.core.CastResult;
import vectorregnum.core.Sigil;
import vectorregnum.core.SpellCompiler;
import vectorregnum.core.SpellEngine;
import vectorregnum.core.Vec3;
import vectorregnum.core.WildMagicCategory;
import vectorregnum.core.circle.CircleCompilation;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.Vm2CircleCompilation;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.RuntimeValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.SpellVm;
import vectorregnum.core.vm2.TickResult;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.VmMessage;
import vectorregnum.core.vm2.WorldAccess;
import vectorregnum.core.vm2.WorldEffect;

class PonderTimelineBuilderTest {
    @Test
    void timelineUsesActualCompilationManaTicksAndWorldEffects() {
        List<PlacedSigil> order = List.of(sigil(0, 0, "VM_DURATION"),
                sigil(0, 1, "VM_DELAY"), sigil(0, 2, "VM_PUSH_SELF"),
                sigil(0, 3, "VM_PUSH_VECTOR"), sigil(1, 0, "VM_IMPULSE"),
                sigil(1, 1, "EXECUTE"));
        Program program = new Program(List.of(
                Instruction.duration(8, at(0)), Instruction.delay(2, at(1)),
                Instruction.push(new RuntimeValue.EntityValue("caster"), at(2)),
                Instruction.push(new RuntimeValue.VectorValue(new Vector3(1, 0, 0)), at(3)),
                Instruction.impulse(16, 0, at(4)), Instruction.halt(at(5))));
        SpellVm vm = new SpellVm(program, worldWithCaster());
        List<TickResult> ticks = new ArrayList<>();
        while (!vm.isTerminal()) ticks.add(vm.tick());

        PonderTimeline timeline = PonderTimelineBuilder.fromVm2("step", "Vector Step",
                new Vm2CircleCompilation(order, program, List.of()), ticks);
        assertEquals(List.of("VM_DURATION", "VM_DELAY", "VM_PUSH_SELF", "VM_PUSH_VECTOR",
                        "VM_IMPULSE", "EXECUTE"),
                timeline.steps().stream().filter(step -> step.phase() == PonderTimeline.Phase.COMPILATION)
                        .skip(1).map(step -> step.cues().getFirst().source().orElseThrow().sigilId()).toList());
        assertTrue(timeline.steps().stream().anyMatch(step -> step.phase() == PonderTimeline.Phase.MANA
                && step.cues().stream().anyMatch(cue -> "physical_work".equals(cue.data().get("dimension")))));
        assertTrue(timeline.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.WAIT));
        assertTrue(timeline.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.WORLD_EFFECT
                        && "Impulse".equals(cue.data().get("effect"))));
        assertTrue(timeline.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.SET_PIECE));
        assertTrue(timeline.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.MANA_FLOW));
    }

    @Test
    void actualVmFaultAndCompatibilityMiscastRetainSourcesAndConsumeEscrow() {
        List<PlacedSigil> vmOrder = List.of(sigil(0, 0, "VM_PUSH_BOOLEAN"),
                sigil(0, 1, "VM_PUSH_NUMBER"), sigil(0, 2, "VM_ADD"));
        Program faulty = new Program(List.of(
                Instruction.push(new RuntimeValue.BooleanValue(true), at(0)),
                Instruction.push(new RuntimeValue.NumberValue(2), at(1)), Instruction.add(at(2))));
        TickResult fault = new SpellVm(faulty, WorldAccess.EMPTY).tick();
        PonderTimeline vm = PonderTimelineBuilder.fromVm2("fault", "Type Fault",
                new Vm2CircleCompilation(vmOrder, faulty, List.of()), List.of(fault));
        assertTrue(vm.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.RUNTIME_FAULT
                        && cue.source().orElseThrow().clockwiseSlot() == 2));

        List<Sigil> source = List.of(new Sigil("ORIGIN_SELF"), new Sigil("ELEMENT_ARCANE"),
                new Sigil("EXPAND", 10.0), new Sigil("SHAPE_AURA"), new Sigil("EXECUTE"));
        List<PlacedSigil> order = List.of(sigil(0, 0, "ORIGIN_SELF"), sigil(0, 1, "ELEMENT_ARCANE"),
                sigil(0, 2, "EXPAND"), sigil(0, 3, "SHAPE_AURA"), sigil(0, 4, "EXECUTE"));
        var compiled = SpellCompiler.compile(source);
        CastResult result = new SpellEngine().cast(compiled,
                new CastContext("caster", Vec3.ZERO, new Vec3(1, 0, 0), 42));
        PonderTimeline miscast = PonderTimelineBuilder.fromCompatibility("wild", "Wild Magic",
                new CircleCompilation(order, source, compiled, List.of()), result);
        assertTrue(miscast.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.WILD_MAGIC
                        && WildMagicCategory.UNSTRUCTURED_ELEMENT_BURST.name()
                                .equals(cue.data().get("category"))));
        assertTrue(miscast.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.ESCROW_STATE
                        && "consumed".equals(cue.data().get("state"))));
    }

    @Test
    void controllerProvidesBoundedPlaybackAndScrubbing() {
        PonderTimeline timeline = new PonderTimeline("tiny", "Tiny", List.of(
                new PonderTimeline.Step(0, 1, PonderTimeline.Phase.COMPILATION, "One", "First",
                        List.of(new PonderTimeline.Cue(PonderTimeline.CueType.FOCUS_CIRCLE,
                                Optional.empty(), java.util.Map.of("sigils", "1")))),
                new PonderTimeline.Step(1, 2, PonderTimeline.Phase.EXECUTION, "Two", "Second",
                        List.of(new PonderTimeline.Cue(PonderTimeline.CueType.EXECUTION_CURSOR,
                                Optional.empty(), java.util.Map.of("status", "HALTED"))))));
        PonderController controller = new PonderController(timeline);
        controller.play();
        assertTrue(controller.tick());
        assertEquals(1, controller.stepIndex());
        assertFalse(controller.tick());
        assertTrue(controller.finished());
        controller.replay();
        assertEquals(0, controller.stepIndex());
    }

    @Test
    void liveReplacementFollowsTailButAChangedLessonRestarts() {
        PonderTimeline initial = timeline("live", 2);
        PonderController controller = new PonderController(initial);
        controller.seekStep(1);
        controller.play();
        controller.replaceTimeline(timeline("live", 4));
        assertEquals(3, controller.stepIndex());

        controller.replaceTimeline(timeline("another", 3));
        assertEquals(0, controller.stepIndex());
        assertTrue(controller.playing());
    }

    @Test
    void aLargeEffectBatchIsCompactedWithinTheWireCueBound() {
        Program program = new Program(List.of(Instruction.halt(at(0))));
        List<WorldEffect> effects = new ArrayList<>();
        for (int index = 0; index < 80; index++) {
            effects.add(new WorldEffect.Impulse("entity-" + index, Vector3.ZERO, 1));
        }
        TickResult result = new TickResult(TickResult.Status.HALTED, 0, 1, effects,
                Optional.empty());
        PonderTimeline timeline = PonderTimelineBuilder.fromVm2("effects", "Many effects",
                new Vm2CircleCompilation(List.of(sigil(0, 0, "EXECUTE")), program, List.of()),
                List.of(result));
        PonderTimeline.Step execution = timeline.steps().stream()
                .filter(step -> step.phase() == PonderTimeline.Phase.EXECUTION).findFirst().orElseThrow();
        assertTrue(execution.cues().size() <= PonderTimeline.MAX_CUES_PER_STEP);
        assertTrue(execution.cues().stream().anyMatch(cue ->
                cue.type() == PonderTimeline.CueType.TRACE_COMPACTED
                        && "52".equals(cue.data().get("omitted_effects"))));
    }

    @Test
    void authoritativeVmMessagesRetainGlobalSequenceBranchAndText() {
        Program program = new Program(List.of(Instruction.halt(at(0))));
        VmMessage.Signal signal = new VmMessage.Signal(7, 4, 1, "charge",
                new Vector3(1, 2, 3), new RuntimeValue.NumberValue(2.5), 8);
        VmMessage.Output output = new VmMessage.Output(8, 4, 0, Vector3.ZERO,
                "control complete", 8);
        TickResult result = new TickResult(TickResult.Status.RUNNING, 0, 2, List.of(),
                List.of(signal, output), Optional.empty());

        PonderTimeline timeline = PonderTimelineBuilder.fromVm2("messages", "Messages",
                new Vm2CircleCompilation(List.of(sigil(0, 0, "EXECUTE")), program, List.of()),
                List.of(result));
        PonderTimeline.Step execution = timeline.steps().stream()
                .filter(step -> step.phase() == PonderTimeline.Phase.EXECUTION)
                .findFirst().orElseThrow();
        List<PonderTimeline.Cue> messages = execution.cues().stream()
                .filter(cue -> "vm_message".equals(cue.data().get("trace"))).toList();

        assertEquals(List.of("SIGNAL", "OUTPUT"), messages.stream()
                .map(cue -> cue.data().get("operation")).toList());
        assertEquals("7", messages.getFirst().data().get("sequence"));
        assertEquals("1", messages.getFirst().data().get("branch"));
        assertEquals("charge", messages.getFirst().data().get("channel"));
        assertEquals("8", messages.getLast().data().get("sequence"));
        assertEquals("control complete", messages.getLast().data().get("text"));
        assertTrue(execution.narration().contains("Signal charge=2.5"));
        assertTrue(execution.narration().contains("Owner output \"control complete\""));
    }

    @Test
    void messageBatchIsCompactedWithoutDroppingTheTraceTruthCue() {
        Program program = new Program(List.of(Instruction.halt(at(0))));
        List<VmMessage> messages = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            messages.add(new VmMessage.Output(index, index, index % 2, Vector3.ZERO,
                    "message-" + index, 4));
        }
        TickResult result = new TickResult(TickResult.Status.HALTED, 0, 1, List.of(),
                messages, Optional.empty());

        PonderTimeline timeline = PonderTimelineBuilder.fromVm2("message-limit", "Message limit",
                new Vm2CircleCompilation(List.of(sigil(0, 0, "EXECUTE")), program, List.of()),
                List.of(result));
        PonderTimeline.Step execution = timeline.steps().stream()
                .filter(step -> step.phase() == PonderTimeline.Phase.EXECUTION)
                .findFirst().orElseThrow();

        assertTrue(execution.cues().size() <= PonderTimeline.MAX_CUES_PER_STEP);
        assertTrue(execution.cues().stream().anyMatch(cue ->
                cue.type() == PonderTimeline.CueType.TRACE_COMPACTED
                        && "32".equals(cue.data().get("omitted_messages"))));
        assertTrue(execution.narration().contains("additional message(s) were compacted"));
    }

    private static PonderTimeline timeline(String id, int steps) {
        List<PonderTimeline.Step> result = new ArrayList<>();
        for (int index = 0; index < steps; index++) {
            result.add(new PonderTimeline.Step(index, 4, PonderTimeline.Phase.EXECUTION,
                    "Step " + index, "Trace step " + index,
                    List.of(new PonderTimeline.Cue(PonderTimeline.CueType.EXECUTION_CURSOR,
                            Optional.empty(), java.util.Map.of("status", "RUNNING")))));
        }
        return new PonderTimeline(id, "Trace", result);
    }

    private static PlacedSigil sigil(int ring, int slot, String type) {
        return new PlacedSigil(new CircleCoordinate(ring, slot), type);
    }

    private static SourceLocation at(int index) { return SourceLocation.at(index, "S" + index); }

    private static WorldAccess worldWithCaster() {
        WorldAccess.EntitySnapshot caster = new WorldAccess.EntitySnapshot("caster", Vector3.ZERO,
                1, "player", Set.of());
        return new WorldAccess() {
            @Override public Optional<EntitySnapshot> entity(String id) {
                return id.equals("caster") ? Optional.of(caster) : Optional.empty();
            }
            @Override public Optional<RaycastHit> raycast(Vector3 origin, Vector3 direction,
                    double range, SelectionFilter filter) { return Optional.empty(); }
            @Override public List<EntitySnapshot> select(Vector3 center, double radius,
                    SelectionFilter filter) { return List.of(); }
        };
    }
}
