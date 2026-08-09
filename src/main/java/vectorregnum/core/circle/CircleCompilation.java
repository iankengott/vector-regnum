package vectorregnum.core.circle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.CompiledSpell;
import vectorregnum.core.Sigil;

/** Complete compile preview suitable for an editor UI. */
public record CircleCompilation(
        List<PlacedSigil> executionOrder,
        List<Sigil> compatibilitySource,
        CompiledSpell compiledSpell,
        List<CircleDiagnostic> diagnostics) {
    public CircleCompilation {
        executionOrder = List.copyOf(executionOrder);
        compatibilitySource = List.copyOf(compatibilitySource);
        diagnostics = List.copyOf(diagnostics);
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics cannot contain null");
        }
        if (diagnostics.stream().anyMatch(
                diagnostic -> diagnostic.severity() == CircleDiagnostic.Severity.ERROR)
                && compiledSpell != null) {
            throw new IllegalArgumentException("an erroneous preview cannot publish bytecode");
        }
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == CircleDiagnostic.Severity.ERROR);
    }

    public Optional<CompiledSpell> spell() {
        return Optional.ofNullable(compiledSpell);
    }
}
