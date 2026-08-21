package vectorregnum.core.presentation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PresentationModulePlan(String rendererId, PresentationCueKind cueKind, boolean truthLayer,
        double intensity, Map<String, Double> parameters, List<PresentationModuleKind> modules) {
    public static final String NAMESPACE = "vector_regnum";
    public static final int MAX_PARAMETERS = 16;
    public static final int MAX_PARAMETER_KEY_LENGTH = 48;
    private static final String ID_PATTERN = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    public PresentationModulePlan {
        Objects.requireNonNull(rendererId, "rendererId");
        Objects.requireNonNull(cueKind, "cueKind");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(modules, "modules");
        if (!rendererId.matches(ID_PATTERN) || rendererId.length() > 128) {
            throw new IllegalArgumentException("invalid renderer id");
        }
        if (!NAMESPACE.equals(rendererId.substring(0, rendererId.indexOf(':')))) {
            throw new IllegalArgumentException("renderer namespace must be " + NAMESPACE);
        }
        if (!Double.isFinite(intensity) || intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("plan intensity must be 0..1");
        }
        Map<String, Double> copiedParameters = Map.copyOf(parameters);
        if (copiedParameters.size() > MAX_PARAMETERS
                || copiedParameters.keySet().stream().anyMatch(key -> key.isBlank()
                        || key.length() > MAX_PARAMETER_KEY_LENGTH)
                || copiedParameters.values().stream().anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("plan parameters exceed bounded schema");
        }
        List<PresentationModuleKind> copiedModules = List.copyOf(modules);
        if (copiedModules.isEmpty()) {
            throw new IllegalArgumentException("plan requires at least one presentation module");
        }
        if (truthLayer && copiedModules.stream().allMatch(PresentationModuleKind::isCosmeticOnly)) {
            throw new IllegalArgumentException("truth plans require a concrete module");
        }
        parameters = copiedParameters;
        modules = copiedModules;
    }
}