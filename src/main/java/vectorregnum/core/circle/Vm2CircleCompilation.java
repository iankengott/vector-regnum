package vectorregnum.core.circle;

import vectorregnum.core.vm2.Program;

import java.util.List;
import java.util.Optional;

/** Result of lowering a clockwise authored circle directly into vm2 bytecode. */
public record Vm2CircleCompilation(
        List<PlacedSigil> executionOrder, Program program, List<CircleDiagnostic> diagnostics) {
    public Vm2CircleCompilation {
        executionOrder = List.copyOf(executionOrder);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.severity() == CircleDiagnostic.Severity.ERROR);
    }

    public Optional<Program> compiledProgram() {
        return Optional.ofNullable(program);
    }
}
