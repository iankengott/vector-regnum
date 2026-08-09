package vectorregnum.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compatibility frontend for the prototype's ordered string sigils. */
public final class SpellCompiler {
    private SpellCompiler() {
    }

    public static CompiledSpell compile(List<Sigil> source) {
        Objects.requireNonNull(source, "source");
        List<Sigil> sigils = List.copyOf(source);
        List<Instruction> instructions = new ArrayList<>(sigils.size());
        double runningCost = 0.0;

        for (int sourceIndex = 0; sourceIndex < sigils.size(); sourceIndex++) {
            Instruction instruction = compileSigil(sigils.get(sourceIndex), sourceIndex);
            double nextCost = runningCost + instruction.manaCost();
            if (!Double.isFinite(nextCost)) {
                instruction = Instruction.fault(sourceIndex, FaultCode.NUMERIC_OVERFLOW,
                        "Total mana cost is too large", 0.0, instruction.complexity());
            } else {
                runningCost = nextCost;
            }
            instructions.add(instruction);
        }

        return new CompiledSpell(instructions, sigils.size());
    }

    private static Instruction compileSigil(Sigil sigil, int sourceIndex) {
        String type = sigil.type();
        List<Object> parameters = sigil.parameters();

        if (type.startsWith("ELEMENT_")) {
            if (!parameters.isEmpty()) {
                return operandCountFault(sourceIndex, Opcode.APPLY_ELEMENT, 0, parameters.size());
            }
            String id = type.substring("ELEMENT_".length());
            return Element.fromId(id)
                    .<Instruction>map(element -> Instruction.element(sourceIndex, element))
                    .orElseGet(() -> Instruction.fault(sourceIndex, FaultCode.UNKNOWN_ELEMENT,
                            "Unknown element '" + id + "'", Opcode.APPLY_ELEMENT.baseManaCost,
                            Opcode.APPLY_ELEMENT.complexityWeight));
        }

        if (type.startsWith("SHAPE_")) {
            if (!parameters.isEmpty()) {
                return operandCountFault(sourceIndex, Opcode.RESOLVE_SHAPE, 0, parameters.size());
            }
            String id = type.substring("SHAPE_".length());
            return Shape.fromId(id)
                    .<Instruction>map(shape -> Instruction.shape(sourceIndex, shape))
                    .orElseGet(() -> Instruction.fault(sourceIndex, FaultCode.UNKNOWN_SHAPE,
                            "Unknown shape '" + id + "'", Opcode.RESOLVE_SHAPE.baseManaCost,
                            Opcode.RESOLVE_SHAPE.complexityWeight));
        }

        return switch (type) {
            case "ORIGIN_SELF" -> noOperandInstruction(
                    Opcode.SET_ORIGIN, sourceIndex, parameters);
            case "VECTOR_FORWARD" -> noOperandInstruction(
                    Opcode.SET_VECTOR, sourceIndex, parameters);
            case "EXECUTE" -> noOperandInstruction(
                    Opcode.EXECUTE_EFFECT, sourceIndex, parameters);
            case "EXPAND" -> scalarInstruction(
                    Opcode.EXPAND_AREA, sourceIndex, parameters);
            case "AMPLIFY" -> scalarInstruction(
                    Opcode.AMPLIFY, sourceIndex, parameters);
            default -> Instruction.fault(sourceIndex, FaultCode.UNKNOWN_SIGIL,
                    "Unknown sigil '" + type + "'", 0.0, 0);
        };
    }

    private static Instruction noOperandInstruction(
            Opcode opcode, int sourceIndex, List<Object> parameters) {
        if (!parameters.isEmpty()) {
            return operandCountFault(sourceIndex, opcode, 0, parameters.size());
        }
        return Instruction.simple(opcode, sourceIndex);
    }

    private static Instruction scalarInstruction(
            Opcode opcode, int sourceIndex, List<Object> parameters) {
        if (parameters.size() != 1) {
            return operandCountFault(sourceIndex, opcode, 1, parameters.size());
        }

        Object parameter = parameters.getFirst();
        if (!(parameter instanceof Number number)) {
            String actual = parameter == null ? "null" : parameter.getClass().getSimpleName();
            return Instruction.fault(sourceIndex, FaultCode.TYPE_MISMATCH,
                    "Expected a numeric operand but got " + actual,
                    opcode.baseManaCost, opcode.complexityWeight);
        }

        double value = number.doubleValue();
        if (!Double.isFinite(value) || value <= 0.0) {
            return Instruction.fault(sourceIndex, FaultCode.INVALID_NUMBER,
                    "Operand must be finite and greater than zero",
                    opcode.baseManaCost, opcode.complexityWeight);
        }

        double cost = opcode == Opcode.EXPAND_AREA
                ? opcode.baseManaCost + Math.pow(value, 1.5) * 5.0
                : opcode.baseManaCost * value;
        if (!Double.isFinite(cost) || cost < 0.0) {
            return Instruction.fault(sourceIndex, FaultCode.NUMERIC_OVERFLOW,
                    "Operand produces a non-finite mana cost",
                    opcode.baseManaCost, opcode.complexityWeight);
        }
        return Instruction.scalar(opcode, sourceIndex, value, cost);
    }

    private static Instruction operandCountFault(
            int sourceIndex, Opcode opcode, int expected, int actual) {
        return Instruction.fault(sourceIndex, FaultCode.OPERAND_COUNT,
                "Expected " + expected + " operands but got " + actual,
                opcode.baseManaCost, opcode.complexityWeight);
    }
}
