package vectorregnum.core;

import java.util.Objects;

/** Internal immutable compatibility bytecode instruction. */
record Instruction(
        Opcode opcode,
        int sourceIndex,
        Element element,
        Shape shape,
        double scalar,
        FaultCode faultCode,
        String faultMessage,
        double manaCost,
        int complexity) {

    Instruction {
        Objects.requireNonNull(opcode, "opcode");
        if (sourceIndex < 0) {
            throw new IllegalArgumentException("sourceIndex must be non-negative");
        }
        if (!Double.isFinite(manaCost) || manaCost < 0.0) {
            throw new IllegalArgumentException("manaCost must be finite and non-negative");
        }
        if (complexity < 0) {
            throw new IllegalArgumentException("complexity must be non-negative");
        }
        if (opcode == Opcode.FAULT) {
            Objects.requireNonNull(faultCode, "faultCode");
            Objects.requireNonNull(faultMessage, "faultMessage");
        }
    }

    static Instruction simple(Opcode opcode, int sourceIndex) {
        return new Instruction(opcode, sourceIndex, null, null, 0.0, null, null,
                opcode.baseManaCost, opcode.complexityWeight);
    }

    static Instruction element(int sourceIndex, Element element) {
        return new Instruction(Opcode.APPLY_ELEMENT, sourceIndex,
                Objects.requireNonNull(element), null, 0.0, null, null,
                Opcode.APPLY_ELEMENT.baseManaCost, Opcode.APPLY_ELEMENT.complexityWeight);
    }

    static Instruction shape(int sourceIndex, Shape shape) {
        return new Instruction(Opcode.RESOLVE_SHAPE, sourceIndex,
                null, Objects.requireNonNull(shape), 0.0, null, null,
                Opcode.RESOLVE_SHAPE.baseManaCost, Opcode.RESOLVE_SHAPE.complexityWeight);
    }

    static Instruction scalar(Opcode opcode, int sourceIndex, double value, double manaCost) {
        if (opcode != Opcode.EXPAND_AREA && opcode != Opcode.AMPLIFY) {
            throw new IllegalArgumentException("Opcode does not accept a scalar: " + opcode);
        }
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("scalar must be finite and positive");
        }
        return new Instruction(opcode, sourceIndex, null, null, value, null, null,
                manaCost, opcode.complexityWeight);
    }

    static Instruction fault(
            int sourceIndex,
            FaultCode code,
            String message,
            double manaCost,
            int complexity) {
        return new Instruction(Opcode.FAULT, sourceIndex, null, null, 0.0,
                code, message, manaCost, complexity);
    }
}
