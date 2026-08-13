package vectorregnum.core.semantic;

import java.util.List;

/** Ordered backend fragments and any source-bound failures. */
public record LoweringResult<T>(List<T> output, List<LoweringDiagnostic> diagnostics) {
    public LoweringResult {
        output = List.copyOf(output);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean successful() { return diagnostics.isEmpty(); }
}
