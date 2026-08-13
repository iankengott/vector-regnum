package vectorregnum.core.vm2;

import java.util.List;
import java.util.Map;

/** Immutable result of static stack analysis. Stack snapshots are bottom-first. */
public record StackAnalysis(List<StackDiagnostic> diagnostics,
        Map<Integer, List<StackType>> entryStacks, int maximumDepth) {
    public StackAnalysis {
        diagnostics = List.copyOf(diagnostics);
        entryStacks = entryStacks.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        if (maximumDepth < 0) throw new IllegalArgumentException("maximumDepth cannot be negative");
    }

    public boolean valid() {
        return diagnostics.isEmpty();
    }
}
