package vectorregnum.core.presentation;

import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.vm2.Opcode;

/** Compact authoritative hook received by the non-authoritative client runtime. */
public record PresentationSignal(long sequence, long tick, PresentationTrigger.Kind kind,
        Optional<Opcode> opcode, Optional<SemanticOpcode> semanticOpcode,
        int sourceIndex, double x, double y, double z) {
    public PresentationSignal {
        if (sequence < 0 || tick < 0 || sourceIndex < -1) {
            throw new IllegalArgumentException("negative presentation signal metadata");
        }
        Objects.requireNonNull(kind, "kind");
        opcode = Objects.requireNonNull(opcode, "opcode");
        semanticOpcode = Objects.requireNonNull(semanticOpcode, "semanticOpcode");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("presentation signal position must be finite");
        }
        new PresentationTrigger(kind, opcode, semanticOpcode);
    }

    public boolean matches(PresentationInstruction instruction) {
        PresentationTrigger trigger = instruction.trigger();
        return trigger.kind() == kind && trigger.opcode().equals(opcode)
                && trigger.semanticOpcode().equals(semanticOpcode);
    }
}
