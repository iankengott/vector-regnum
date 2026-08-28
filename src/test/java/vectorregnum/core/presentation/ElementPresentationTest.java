package vectorregnum.core.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.ManaCostModel;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.SourceLocation;

class ElementPresentationTest {
    @Test
    void explicitPresentationCodesPreserveLegacySlotsAndAppendNewElements() {
        assertEquals(0, PresentationElement.ARCANE.parameter());
        assertEquals(1, PresentationElement.FIRE.parameter());
        assertEquals(2, PresentationElement.ICE.parameter());
        assertEquals(3, PresentationElement.VOID.parameter());
        assertEquals(PresentationElement.SOUND, PresentationElement.fromParameter(13));
    }

    @Test
    void everySemanticElementMapsToItsOwnPresentationElement() {
        for (SemanticOpcode opcode : elementOpcodes()) {
            SemanticInstruction semantic = SemanticInstruction.simple(opcode,
                    SourceLocation.at(0, opcode.name()));
            Program program = new Program(List.of(
                    Instruction.semantic(semantic, ManaCostModel.Input.ZERO),
                    Instruction.halt(SourceLocation.at(1, "EXECUTE"))));
            PresentationProgram presentation = PresentationCompiler.compile(opcode.name(), 1, program);
            double code = presentation.instructions().getFirst().parameters().get("element");
            assertEquals(expected(opcode).parameter(), code, opcode.name());
            assertTrue(presentation.instructions().stream().anyMatch(PresentationInstruction::truthLayer));
        }
    }

    private static List<SemanticOpcode> elementOpcodes() {
        return List.of(SemanticOpcode.ELEMENT_WATER, SemanticOpcode.ELEMENT_FIRE,
                SemanticOpcode.ELEMENT_AIR, SemanticOpcode.ELEMENT_EARTH,
                SemanticOpcode.ELEMENT_LIGHTNING, SemanticOpcode.ELEMENT_TIME,
                SemanticOpcode.ELEMENT_SPACE, SemanticOpcode.ELEMENT_LIGHT,
                SemanticOpcode.ELEMENT_DARK, SemanticOpcode.ELEMENT_NATURE,
                SemanticOpcode.ELEMENT_ICE, SemanticOpcode.ELEMENT_SOUND,
                SemanticOpcode.ELEMENT_VOID, SemanticOpcode.ELEMENT_ARCANE);
    }

    private static PresentationElement expected(SemanticOpcode opcode) {
        return switch (opcode) {
            case ELEMENT_WATER -> PresentationElement.WATER;
            case ELEMENT_FIRE -> PresentationElement.FIRE;
            case ELEMENT_AIR -> PresentationElement.AIR;
            case ELEMENT_EARTH -> PresentationElement.EARTH;
            case ELEMENT_LIGHTNING -> PresentationElement.LIGHTNING;
            case ELEMENT_TIME -> PresentationElement.TIME;
            case ELEMENT_SPACE -> PresentationElement.SPACE;
            case ELEMENT_LIGHT -> PresentationElement.LIGHT;
            case ELEMENT_DARK -> PresentationElement.DARK;
            case ELEMENT_NATURE -> PresentationElement.NATURE;
            case ELEMENT_ICE -> PresentationElement.ICE;
            case ELEMENT_SOUND -> PresentationElement.SOUND;
            case ELEMENT_VOID -> PresentationElement.VOID;
            case ELEMENT_ARCANE -> PresentationElement.ARCANE;
            default -> throw new AssertionError(opcode);
        };
    }
}
