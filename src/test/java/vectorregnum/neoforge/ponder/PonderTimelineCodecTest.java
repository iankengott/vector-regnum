package vectorregnum.neoforge.ponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.Vm2CircleCompilation;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.TickResult;
import vectorregnum.core.vm2.VmFault;

class PonderTimelineCodecTest {
    @Test
    void explicitWireFormatRoundTripsSourcesAndCueData() {
        PonderTimeline original = new PonderTimeline("server-trace", "Authoritative trace", List.of(
                new PonderTimeline.Step(0, 12, PonderTimeline.Phase.FAULT,
                        "Runtime fault", "The exact authored sigil failed.", List.of(
                                new PonderTimeline.Cue(PonderTimeline.CueType.RUNTIME_FAULT,
                                        Optional.of(new PonderTimeline.SourceRef(2, 1, 3, "VM_ADD")),
                                        Map.of("code", "TYPE_MISMATCH", "tick", "8"))))));

        assertEquals(original, PonderTimelineCodec.decode(PonderTimelineCodec.encode(original)));
    }

    @Test
    void malformedAndOversizedPayloadsAreRejectedBeforeRendering() {
        assertThrows(IllegalArgumentException.class, () -> PonderTimelineCodec.decode("{}"));
        assertThrows(IllegalArgumentException.class, () -> PonderTimelineCodec.decode(
                "x".repeat(PonderTimelineCodec.MAX_ENCODED_LENGTH + 1)));
    }

    @Test
    void longServerTraceIsCompactedButRetainsTerminalFault() {
        SourceLocation source = new SourceLocation(0, 1, 1, "VM_ADD");
        Program program = new Program(List.of(Instruction.halt(source)));
        List<TickResult> ticks = new ArrayList<>();
        for (int tick = 0; tick < 400; tick++) {
            ticks.add(new TickResult(TickResult.Status.WAITING, 0, 0, List.of(), Optional.empty()));
        }
        VmFault fault = new VmFault(VmFault.Code.TYPE_MISMATCH, "actual terminal failure",
                source, 0);
        ticks.add(new TickResult(TickResult.Status.FAULTED, 0, 1, List.of(), Optional.of(fault)));

        PonderTimeline timeline = PonderTimelineBuilder.fromVm2("long-trace", "Long trace",
                new Vm2CircleCompilation(
                        List.of(new PlacedSigil(new CircleCoordinate(0, 0), "VM_ADD")),
                        program, List.of()), ticks);

        assertEquals(PonderTimeline.MAX_STEPS, timeline.steps().size());
        assertTrue(timeline.steps().stream().flatMap(step -> step.cues().stream())
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.TRACE_COMPACTED));
        assertTrue(timeline.steps().getLast().cues().stream()
                .anyMatch(cue -> cue.type() == PonderTimeline.CueType.RUNTIME_FAULT
                        && "TYPE_MISMATCH".equals(cue.data().get("code"))));
    }
}
