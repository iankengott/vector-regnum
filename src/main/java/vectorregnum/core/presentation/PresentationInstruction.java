package vectorregnum.core.presentation;

import java.util.Map;
import java.util.Objects;

/** One bounded cue in a presentation program. Numeric parameters are renderer-defined. */
public record PresentationInstruction(PresentationTrigger trigger, PresentationPhase phase,
        PresentationCueKind cueKind, String rendererId, PresentationBinding binding,
        int startOffsetTicks, int durationTicks, double intensity, boolean truthLayer,
        Map<String, Double> parameters, PresentationCost cost) {
    private static final String ID_PATTERN = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    public PresentationInstruction {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(cueKind, "cueKind");
        Objects.requireNonNull(rendererId, "rendererId");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(cost, "cost");
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
        if (!rendererId.matches(ID_PATTERN) || rendererId.length() > 128) {
            throw new IllegalArgumentException("invalid renderer id");
        }
        if (startOffsetTicks < 0 || durationTicks < 1) {
            throw new IllegalArgumentException("cue timing must be non-negative with positive duration");
        }
        if (!Double.isFinite(intensity) || intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("cue intensity must be 0..1");
        }
        if (parameters.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                || parameters.values().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("presentation parameters must be named and finite");
        }
        if (parameters.size() > 16 || parameters.keySet().stream().anyMatch(key -> key.length() > 48)) {
            throw new IllegalArgumentException("presentation parameters exceed bounded schema");
        }
    }
}
