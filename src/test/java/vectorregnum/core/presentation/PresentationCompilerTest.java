package vectorregnum.core.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vectorregnum.core.semantic.LoweringContext;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.semantic.SemanticVmLowerer;
import vectorregnum.neoforge.LibrarySemanticAdapter;
import vectorregnum.neoforge.progression.ProgressionSpellLibrary;

class PresentationCompilerTest {
    @Test
    void everyLibrarySpellCompilesToBoundedLayeredRoundTrippablePresentation() {
        EnumSet<PresentationCueKind> layers = EnumSet.noneOf(PresentationCueKind.class);
        for (var spell : ProgressionSpellLibrary.ALL) {
            var semantic = LibrarySemanticAdapter.adapt(spell);
            var vm = SemanticVmLowerer.lowerChecked(semantic,
                    new LoweringContext(spell.id(), 91L, Map.of()));
            PresentationProgram program = PresentationCompiler.compile(spell.id(), 91L, vm);
            assertFalse(program.instructions().isEmpty(), spell.id());
            assertTrue(program.instructions().stream().anyMatch(PresentationInstruction::truthLayer),
                    spell.id());
            assertTrue(program.instructions().stream().anyMatch(instruction ->
                    instruction.cueKind() == PresentationCueKind.SPATIAL_SOUND), spell.id());
            assertTrue(program.instructions().stream().anyMatch(instruction ->
                    instruction.cueKind() == PresentationCueKind.AFTERMATH), spell.id());
            assertEquals(program, PresentationProgramCodec.decode(
                    PresentationProgramCodec.encode(program)), spell.id());
            layers.addAll(program.instructions().stream().map(PresentationInstruction::cueKind).toList());
        }
        assertTrue(layers.containsAll(EnumSet.of(PresentationCueKind.PARTICLES,
                PresentationCueKind.BEAM, PresentationCueKind.RIBBON,
                PresentationCueKind.RUNES, PresentationCueKind.SURFACE,
                PresentationCueKind.VOLUME, PresentationCueKind.LIGHT,
                PresentationCueKind.DARKNESS, PresentationCueKind.AIR,
                PresentationCueKind.MATERIAL_RESPONSE, PresentationCueKind.SPATIAL_SOUND,
                PresentationCueKind.SCREEN, PresentationCueKind.AFTERMATH)));
    }

    @Test
    void runtimeSignalsMatchOnlyTheirExecutedSemanticHook() {
        PresentationInstruction damage = new PresentationInstruction(
                PresentationTrigger.semantic(SemanticOpcode.APPLY_DAMAGE),
                PresentationPhase.IMPACT, PresentationCueKind.PARTICLES,
                "vector_regnum:truth/impact", PresentationBinding.IMPACT_POINT,
                0, 8, .7, true, Map.of(), PresentationCost.ZERO);
        PresentationSignal damageSignal = new PresentationSignal(3, 1,
                PresentationTrigger.Kind.OPCODE, Optional.empty(),
                Optional.of(SemanticOpcode.APPLY_DAMAGE), 5, 1, 2, 3);
        PresentationSignal slowSignal = new PresentationSignal(4, 1,
                PresentationTrigger.Kind.OPCODE, Optional.empty(),
                Optional.of(SemanticOpcode.APPLY_SLOW), 6, 1, 2, 3);
        assertTrue(damageSignal.matches(damage));
        assertFalse(slowSignal.matches(damage));
    }

    @Test
    void lodAndAccessibilityReduceDetailWithoutDroppingTruth() {
        PresentationInstruction truth = instruction(true, PresentationCueKind.SURFACE);
        PresentationInstruction haze = instruction(false, PresentationCueKind.FOG);
        assertTrue(PresentationLod.TELEGRAPH_ONLY.renders(truth));
        assertFalse(PresentationLod.TELEGRAPH_ONLY.renders(haze));
        assertEquals(PresentationLod.MID,
                PresentationLod.select(5, PresentationQuality.BALANCED));

        PresentationAccessibility safe = new PresentationAccessibility(
                PresentationQuality.MINIMAL, .8, .7, 1, 1, 1, .6, true, true);
        assertEquals(0.0, safe.cameraMovement());
        assertEquals(0.0, safe.chromaticIntensity());
        assertTrue(safe.flashIntensity() <= .15);
        assertTrue(safe.effectiveParticleDensity() < .2);
    }

    private static PresentationInstruction instruction(boolean truth, PresentationCueKind kind) {
        return new PresentationInstruction(PresentationTrigger.cast(), PresentationPhase.GATHERING,
                kind, "vector_regnum:test/layer", PresentationBinding.CAST_ORIGIN,
                0, 10, .5, truth, Map.of(), PresentationCost.ZERO);
    }
}
