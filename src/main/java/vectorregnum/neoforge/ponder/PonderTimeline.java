package vectorregnum.neoforge.ponder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A bounded, renderer-neutral Create-style teaching sequence. */
public record PonderTimeline(String id, String title, List<Step> steps) {
    public static final int MAX_STEPS = 256;
    public static final int MAX_STEP_TICKS = 200;
    public static final int MAX_CUES_PER_STEP = 32;
    public static final int MAX_ID_LENGTH = 128;
    public static final int MAX_TITLE_LENGTH = 256;
    public static final int MAX_NARRATION_LENGTH = 1_024;
    public static final int MAX_CUE_DATA_ENTRIES = 16;

    public PonderTimeline {
        id = requireText(id, "id", MAX_ID_LENGTH);
        title = requireText(title, "title", MAX_TITLE_LENGTH);
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty() || steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("ponder timeline needs 1.." + MAX_STEPS + " steps");
        }
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).index() != index) {
                throw new IllegalArgumentException("ponder steps must have contiguous indices");
            }
        }
    }

    public enum Phase { COMPILATION, MANA, EXECUTION, FAULT }

    public enum CueType {
        FOCUS_CIRCLE, HIGHLIGHT_SIGIL, TRACE_CLOCKWISE, MOVE_INWARD,
        MANA_SEGMENT, EXECUTION_CURSOR, WORLD_EFFECT, WAIT, BUDGET_YIELD,
        TRACE_COMPACTED, COMPILER_FAULT, RUNTIME_FAULT, WILD_MAGIC,
        SET_PIECE, CAMERA_FOCUS, MANA_FLOW, SCROLL_STATE, FAULT_FRACTURE,
        CASTING_METHOD, CAST_QUOTE, REAGENT_CONTRIBUTION, ESCROW_STATE
    }

    public record SourceRef(int sourceIndex, int ring, int clockwiseSlot, String sigilId) {
        public SourceRef {
            if (sourceIndex < 0 || ring < 0 || clockwiseSlot < 0) {
                throw new IllegalArgumentException("negative ponder source coordinate");
            }
            sigilId = requireText(sigilId, "sigilId", MAX_ID_LENGTH);
        }
    }

    public record Cue(CueType type, Optional<SourceRef> source, Map<String, String> data) {
        public Cue {
            Objects.requireNonNull(type, "type");
            source = Objects.requireNonNull(source, "source");
            data = Map.copyOf(Objects.requireNonNull(data, "data"));
            if (data.size() > MAX_CUE_DATA_ENTRIES || data.entrySet().stream().anyMatch(entry ->
                    entry.getKey() == null || entry.getKey().isBlank() || entry.getKey().length() > 64
                            || entry.getValue() == null || entry.getValue().isBlank()
                            || entry.getValue().length() > 1_024)) {
                throw new IllegalArgumentException("ponder cue data cannot be blank");
            }
        }
    }

    public record Step(int index, int durationTicks, Phase phase, String title,
            String narration, List<Cue> cues) {
        public Step {
            if (index < 0 || durationTicks < 1 || durationTicks > MAX_STEP_TICKS) {
                throw new IllegalArgumentException("invalid ponder step bounds");
            }
            Objects.requireNonNull(phase, "phase");
            title = requireText(title, "title", MAX_TITLE_LENGTH);
            narration = requireText(narration, "narration", MAX_NARRATION_LENGTH);
            cues = List.copyOf(Objects.requireNonNull(cues, "cues"));
            if (cues.isEmpty() || cues.size() > MAX_CUES_PER_STEP) {
                throw new IllegalArgumentException("ponder step needs 1.." + MAX_CUES_PER_STEP + " cues");
            }
        }
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1.." + maximumLength + " characters");
        }
        return value;
    }
}
