package vectorregnum.neoforge.ponder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authored teaching set used before a player has a cast trace. Unlike gameplay
 * traces this does not claim that an effect occurred; each fault is explicitly
 * labelled as a safe workshop reconstruction.
 */
public final class PonderLessonLibrary {
    private PonderLessonLibrary() {
    }

    public static PonderTimeline primer() {
        List<PonderTimeline.Step> steps = new ArrayList<>();
        add(steps, 36, PonderTimeline.Phase.COMPILATION, "The scribe's workshop",
                "A bound scroll rests on the warded dais. The circle is a program, read from north clockwise and then inward.",
                cue(PonderTimeline.CueType.SET_PIECE, null,
                        Map.of("stage", "scribe_workshop", "artifact", "scroll")),
                cue(PonderTimeline.CueType.FOCUS_CIRCLE, null, Map.of("sigils", "6")),
                cue(PonderTimeline.CueType.CAMERA_FOCUS, null,
                        Map.of("target", "circle", "motion", "orbit")));
        sourceStep(steps, 0, 0, 0, "VM_DURATION", "Begin at north",
                "The duration contract is read first on the outer ring.", false);
        sourceStep(steps, 1, 0, 2, "VM_DELAY", "Continue clockwise",
                "Each outer sigil is compiled in clockwise order.", false);
        sourceStep(steps, 2, 1, 0, "VM_PUSH_SELF", "Move inward",
                "Only after the outer ring is exhausted does compilation move inward.", true);
        add(steps, 28, PonderTimeline.Phase.MANA, "Mana is itemized",
                "Work, range, duration, rarity, memory, perception, and control flow remain visible instead of becoming one mysterious price.",
                cue(PonderTimeline.CueType.MANA_SEGMENT, null,
                        Map.of("dimension", "physical_work", "amount", "16", "total", "27.5")),
                cue(PonderTimeline.CueType.MANA_FLOW, null,
                        Map.of("from", "crystal", "to", "scroll", "amount", "16")),
                cue(PonderTimeline.CueType.CAMERA_FOCUS, null,
                        Map.of("target", "mana_conduit", "motion", "track")));
        add(steps, 26, PonderTimeline.Phase.EXECUTION, "The server executes",
                "The authoritative VM advances its cursor and emits a bounded world effect. The teaching scene only replays that result.",
                cue(PonderTimeline.CueType.EXECUTION_CURSOR, source(4, 1, 2, "VM_IMPULSE"),
                        Map.of("tick", "3", "status", "RUNNING", "instruction_pointer", "4",
                                "instructions_executed", "3")),
                cue(PonderTimeline.CueType.WORLD_EFFECT, source(4, 1, 2, "VM_IMPULSE"),
                        Map.of("effect", "Impulse", "entity", "training_dummy",
                                "duration_ticks", "1")),
                cue(PonderTimeline.CueType.CAMERA_FOCUS, null,
                        Map.of("target", "training_dummy", "motion", "impact")));
        add(steps, 26, PonderTimeline.Phase.EXECUTION, "A successful scroll is consumed",
                "Consumption happens only after the server accepts the first successful cast. Rejected and faulted attempts retain the scroll.",
                cue(PonderTimeline.CueType.SCROLL_STATE, null,
                        Map.of("state", "accepted_and_consumed", "medium", "scroll")),
                cue(PonderTimeline.CueType.SET_PIECE, null,
                        Map.of("stage", "scribe_workshop", "artifact", "scroll")));
        add(steps, 34, PonderTimeline.Phase.FAULT, "Compiler fault reconstruction",
                "A missing terminal EXECUTE is rejected before mana is spent. The exact physical sigil remains marked.",
                cue(PonderTimeline.CueType.COMPILER_FAULT, source(5, 1, 4, "VM_PUSH_VECTOR"),
                        Map.of("severity", "ERROR", "code", "MISSING_TERMINAL_EXECUTE")),
                cue(PonderTimeline.CueType.FAULT_FRACTURE, source(5, 1, 4, "VM_PUSH_VECTOR"),
                        Map.of("kind", "compiler", "severity", "ERROR")));
        add(steps, 34, PonderTimeline.Phase.FAULT, "Runtime fault reconstruction",
                "A bounded VM can still reject an invalid runtime value. Execution stops at the responsible source; no client can override it.",
                cue(PonderTimeline.CueType.RUNTIME_FAULT, source(3, 1, 1, "VM_ADD"),
                        Map.of("code", "TYPE_MISMATCH", "message", "ADD requires two numbers",
                                "instruction_pointer", "3")),
                cue(PonderTimeline.CueType.FAULT_FRACTURE, source(3, 1, 1, "VM_ADD"),
                        Map.of("kind", "runtime", "severity", "ERROR")));
        wildMagic(steps, "Internal mana detonation", "INTERNAL_MANA_DETONATION",
                "An early structural collapse vents mana inside the ward. The reconstruction marks the inward implosion without applying damage.",
                "implosion");
        wildMagic(steps, "Unstructured element burst", "UNSTRUCTURED_ELEMENT_BURST",
                "A partially resolved element escapes without a stable shape. The reconstruction separates the elemental burst from a valid effect.",
                "element_burst");
        wildMagic(steps, "Violent miscast", "VIOLENT_MISCAST",
                "A late failure has enough compiled structure to become a violent, displaced result. Server bounds and the ward contain this lesson.",
                "violent_miscast");
        add(steps, 30, PonderTimeline.Phase.COMPILATION, "Now replay your own trace",
                "Cast or compile a circle, then press K. This authored primer is replaced by the latest bounded server trace, live while a VM is running.",
                cue(PonderTimeline.CueType.SET_PIECE, null,
                        Map.of("stage", "scribe_workshop", "artifact", "scroll")),
                cue(PonderTimeline.CueType.CAMERA_FOCUS, null,
                        Map.of("target", "circle", "motion", "settle")));
        return new PonderTimeline("workshop-primer", "Spell and Scroll Ponder", steps);
    }

    private static void sourceStep(List<PonderTimeline.Step> steps, int index, int ring, int slot,
            String sigil, String title, String narration, boolean inward) {
        PonderTimeline.SourceRef source = source(index, ring, slot, sigil);
        add(steps, 22, PonderTimeline.Phase.COMPILATION, title, narration,
                cue(PonderTimeline.CueType.HIGHLIGHT_SIGIL, source,
                        Map.of("order", Integer.toString(index + 1), "sigil", sigil)),
                cue(inward ? PonderTimeline.CueType.MOVE_INWARD
                                : PonderTimeline.CueType.TRACE_CLOCKWISE,
                        source, Map.of("direction", "clockwise_then_inward")),
                cue(PonderTimeline.CueType.CAMERA_FOCUS, source,
                        Map.of("target", "sigil", "motion", "track")));
    }

    private static void wildMagic(List<PonderTimeline.Step> steps, String title, String category,
            String narration, String visual) {
        PonderTimeline.SourceRef source = source(4, 1, 3, "EXECUTE");
        add(steps, 42, PonderTimeline.Phase.FAULT, title, narration,
                cue(PonderTimeline.CueType.WILD_MAGIC, source,
                        Map.of("fault", "AUTHORED_RECONSTRUCTION", "category", category,
                                "visual", visual)),
                cue(PonderTimeline.CueType.FAULT_FRACTURE, source,
                        Map.of("kind", "wild_magic", "severity", category)),
                cue(PonderTimeline.CueType.CAMERA_FOCUS, null,
                        Map.of("target", "warded_dais", "motion", "impact")));
    }

    private static void add(List<PonderTimeline.Step> steps, int duration,
            PonderTimeline.Phase phase, String title, String narration,
            PonderTimeline.Cue... cues) {
        steps.add(new PonderTimeline.Step(steps.size(), duration, phase, title, narration,
                List.of(cues)));
    }

    private static PonderTimeline.Cue cue(PonderTimeline.CueType type,
            PonderTimeline.SourceRef source, Map<String, String> data) {
        return new PonderTimeline.Cue(type, Optional.ofNullable(source), data);
    }

    private static PonderTimeline.SourceRef source(int index, int ring, int slot, String sigil) {
        return new PonderTimeline.SourceRef(index, ring, slot, sigil);
    }
}
