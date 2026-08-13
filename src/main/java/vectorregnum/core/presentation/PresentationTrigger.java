package vectorregnum.core.presentation;

import java.util.Objects;
import java.util.Optional;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.vm2.Opcode;

/** Execution hook that starts a presentation cue. */
public record PresentationTrigger(Kind kind, Optional<Opcode> opcode,
        Optional<SemanticOpcode> semanticOpcode) {
    public PresentationTrigger {
        Objects.requireNonNull(kind, "kind");
        opcode = Objects.requireNonNull(opcode, "opcode");
        semanticOpcode = Objects.requireNonNull(semanticOpcode, "semanticOpcode");
        if (opcode.isPresent() && semanticOpcode.isPresent()) {
            throw new IllegalArgumentException("a presentation trigger cannot carry two opcodes");
        }
        if (kind == Kind.OPCODE && opcode.isEmpty() && semanticOpcode.isEmpty()) {
            throw new IllegalArgumentException("OPCODE triggers require a VM or semantic opcode");
        }
        if (kind != Kind.OPCODE && (opcode.isPresent() || semanticOpcode.isPresent())) {
            throw new IllegalArgumentException("only OPCODE triggers carry an opcode");
        }
    }

    public static PresentationTrigger cast() {
        return new PresentationTrigger(Kind.CAST, Optional.empty(), Optional.empty());
    }
    public static PresentationTrigger opcode(Opcode opcode) {
        return new PresentationTrigger(Kind.OPCODE, Optional.of(opcode), Optional.empty());
    }
    public static PresentationTrigger semantic(SemanticOpcode opcode) {
        return new PresentationTrigger(Kind.OPCODE, Optional.empty(), Optional.of(opcode));
    }
    public static PresentationTrigger worldEffect() {
        return new PresentationTrigger(Kind.WORLD_EFFECT, Optional.empty(), Optional.empty());
    }
    public static PresentationTrigger fault() {
        return new PresentationTrigger(Kind.FAULT, Optional.empty(), Optional.empty());
    }
    public static PresentationTrigger halt() {
        return new PresentationTrigger(Kind.HALT, Optional.empty(), Optional.empty());
    }

    public enum Kind { CAST, OPCODE, WORLD_EFFECT, FAULT, HALT }
}
