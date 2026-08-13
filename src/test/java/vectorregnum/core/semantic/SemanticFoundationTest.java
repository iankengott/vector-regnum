package vectorregnum.core.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vectorregnum.core.vm2.SourceLocation;

class SemanticFoundationTest {
    private static SourceLocation at(int index) { return SourceLocation.at(index, "SEM_" + index); }

    @Test
    void creationSpecsEnforceMaterialFormVolumeDurationAndPermanence() {
        CreationSpec stoneWall = new CreationSpec(CreationMaterial.STONE,
                CreationForm.BARRIER, 8, 200, true);
        assertEquals(1.0, stoneWall.material().rarity());
        assertTrue(stoneWall.declaredCost().rarity() > 0);

        assertThrows(IllegalArgumentException.class, () -> new CreationSpec(
                CreationMaterial.FIRE, CreationForm.BARRIER, 2, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new CreationSpec(
                CreationMaterial.ICE, CreationForm.VOLUME, 49, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new CreationSpec(
                CreationMaterial.LIGHT, CreationForm.FIELD, 4, 20, true));
    }

    @Test
    void genericRegistryLowersInOrderAndReportsMissingOpcodeAtSource() {
        CreationSpec spec = new CreationSpec(CreationMaterial.LIGHT,
                CreationForm.FIELD, 4, 40, false);
        SemanticProgram program = new SemanticProgram(List.of(
                SemanticInstruction.simple(SemanticOpcode.ORIGIN_SELF, at(0)),
                SemanticInstruction.creation(spec, at(1)),
                SemanticInstruction.simple(SemanticOpcode.EXECUTE, at(2))));
        OpcodeLoweringRegistry<String> registry = OpcodeLoweringRegistry.<String>builder()
                .registerSingle(SemanticOpcode.ORIGIN_SELF, (instruction, context) -> "origin:" + context.spellId())
                .register(SemanticOpcode.CREATE_FORM, (instruction, context) -> List.of(
                        instruction.creationSpec().material().id(),
                        instruction.creationSpec().form().name().toLowerCase()))
                .build();

        LoweringResult<String> result = registry.lower(program,
                new LoweringContext("light_test", 7, Map.of()));
        assertEquals(List.of("origin:light_test", "light", "field"), result.output());
        assertFalse(result.successful());
        assertEquals(LoweringDiagnostic.Code.MISSING_OPCODE_LOWERER,
                result.diagnostics().getFirst().code());
        assertEquals(at(2), result.diagnostics().getFirst().source());
        assertTrue(registry.missingOpcodes().contains(SemanticOpcode.APPLY_DAMAGE));
    }

    @Test
    void registryTurnsOperandFailuresIntoPreciseDiagnostics() {
        SemanticProgram program = new SemanticProgram(List.of(
                SemanticInstruction.simple(SemanticOpcode.EXECUTE, at(0))));
        OpcodeLoweringRegistry<String> registry = OpcodeLoweringRegistry.<String>builder()
                .registerSingle(SemanticOpcode.EXECUTE, (instruction, context) -> {
                    throw new IllegalArgumentException("missing target");
                }).build();
        LoweringResult<String> result = registry.lower(program,
                new LoweringContext("bad", 0, Map.of()));
        assertEquals(LoweringDiagnostic.Code.INVALID_OPERAND, result.diagnostics().getFirst().code());
        assertEquals("missing target", result.diagnostics().getFirst().message());
    }
}
