package vectorregnum.core.presentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PresentationModuleMapperTest {
    private static final String NAMESPACE = "vector_regnum";
    private static final String RENDERER = NAMESPACE + ":test_renderer";

    @Test
    void mapsEveryCueKindToAValidTruthPlan() {
        for (PresentationCueKind cue : PresentationCueKind.values()) {
            PresentationModulePlan plan = PresentationModuleMapper.map(instruction(cue, true, Map.of()));
            assertEquals(cue, plan.cueKind());
            assertEquals(NAMESPACE, namespaceOf(plan.rendererId()));
            assertTrue(plan.truthLayer());
            assertFalse(plan.modules().isEmpty());
            assertTrue(plan.modules().stream().anyMatch(module -> !module.isCosmeticOnly()),
                    "truth plan for " + cue + " must carry a concrete module");
        }
    }

    @Test
    void mapsEachCueToItsCanonicalModuleFamily() {
        Map<PresentationCueKind, List<PresentationModuleKind>> expected = Map.ofEntries(
                Map.entry(PresentationCueKind.PARTICLES, List.of(PresentationModuleKind.PARTICLE)),
                Map.entry(PresentationCueKind.BEAM, List.of(PresentationModuleKind.BEAM)),
                Map.entry(PresentationCueKind.RIBBON, List.of(PresentationModuleKind.RIBBON)),
                Map.entry(PresentationCueKind.TRAIL, List.of(PresentationModuleKind.TRAIL)),
                Map.entry(PresentationCueKind.RUNES, List.of(PresentationModuleKind.RUNE)),
                Map.entry(PresentationCueKind.SURFACE, List.of(PresentationModuleKind.SURFACE)),
                Map.entry(PresentationCueKind.VOLUME, List.of(PresentationModuleKind.VOLUME)),
                Map.entry(PresentationCueKind.LIGHT, List.of(PresentationModuleKind.DEFERRED_LIGHT,
                        PresentationModuleKind.FRAMEBUFFER)),
                Map.entry(PresentationCueKind.DARKNESS, List.of(PresentationModuleKind.VOLUME,
                        PresentationModuleKind.POST_PROCESS)),
                Map.entry(PresentationCueKind.FOG, List.of(PresentationModuleKind.VOLUME,
                        PresentationModuleKind.FRAMEBUFFER)),
                Map.entry(PresentationCueKind.AIR, List.of(PresentationModuleKind.PARTICLE)),
                Map.entry(PresentationCueKind.MATERIAL_RESPONSE, List.of(PresentationModuleKind.ANIMATED_MESH)),
                Map.entry(PresentationCueKind.SPATIAL_SOUND, List.of(PresentationModuleKind.SPATIAL_AUDIO)),
                Map.entry(PresentationCueKind.CAMERA, List.of(PresentationModuleKind.FRAMEBUFFER,
                        PresentationModuleKind.POST_PROCESS, PresentationModuleKind.PARTICLE)),
                Map.entry(PresentationCueKind.SCREEN, List.of(PresentationModuleKind.FRAMEBUFFER,
                        PresentationModuleKind.POST_PROCESS, PresentationModuleKind.PARTICLE)),
                Map.entry(PresentationCueKind.AFTERMATH, List.of(PresentationModuleKind.PARTICLE,
                        PresentationModuleKind.ANIMATED_MESH)));
        for (PresentationCueKind cue : PresentationCueKind.values()) {
            PresentationModulePlan plan = PresentationModuleMapper.map(instruction(cue, true, Map.of()));
            assertEquals(expected.get(cue), plan.modules(), "module mapping for " + cue);
        }
    }

    @Test
    void mapsEveryCueKindDeterministically() {
        for (PresentationCueKind cue : PresentationCueKind.values()) {
            PresentationInstruction instruction = instruction(cue, true, Map.of("scale", 1.5));
            PresentationModulePlan first = PresentationModuleMapper.map(instruction);
            PresentationModulePlan second = PresentationModuleMapper.map(instruction);
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }
    }

    @Test
    void moduleSelectionIgnoresExtraneousParameters() {
        PresentationModulePlan plain = PresentationModuleMapper.map(
                instruction(PresentationCueKind.BEAM, true, Map.of("width", 0.25)));
        PresentationModulePlan decorated = PresentationModuleMapper.map(
                instruction(PresentationCueKind.BEAM, true, Map.of("width", 0.9, "color", 3.0)));
        assertEquals(plain.modules(), decorated.modules());
    }

    @Test
    void truthPlansForCosmeticCuesGainAConcreteFallback() {
        for (PresentationCueKind cue : List.of(PresentationCueKind.CAMERA, PresentationCueKind.SCREEN)) {
            PresentationModulePlan plan = PresentationModuleMapper.map(instruction(cue, true, Map.of()));
            assertTrue(plan.modules().contains(PresentationModuleKind.PARTICLE),
                    "truth " + cue + " must carry the particle truth telegraph");
            assertTrue(plan.modules().stream().anyMatch(module -> !module.isCosmeticOnly()));
            assertTrue(plan.modules().containsAll(List.of(PresentationModuleKind.FRAMEBUFFER,
                    PresentationModuleKind.POST_PROCESS)));
        }
    }

    @Test
    void nonTruthCosmeticCuesRemainCosmetic() {
        for (PresentationCueKind cue : List.of(PresentationCueKind.CAMERA, PresentationCueKind.SCREEN)) {
            PresentationModulePlan plan = PresentationModuleMapper.map(instruction(cue, false, Map.of()));
            assertFalse(plan.truthLayer());
            assertEquals(List.of(PresentationModuleKind.FRAMEBUFFER, PresentationModuleKind.POST_PROCESS),
                    plan.modules());
        }
    }

    @Test
    void everyTruthPlanNeverCarriesOnlyCosmeticModules() {
        for (PresentationCueKind cue : PresentationCueKind.values()) {
            PresentationModulePlan plan = PresentationModuleMapper.map(instruction(cue, true, Map.of()));
            assertTrue(plan.modules().stream().anyMatch(module -> !module.isCosmeticOnly()),
                    "truth plan for " + cue + " must not be cosmetic-only");
        }
    }

    @Test
    void rejectsRendererIdsOutsideVectorRegnumNamespace() {
        PresentationInstruction instruction = instruction(PresentationCueKind.PARTICLES, true, Map.of(),
                "minecraft:spell_fx");
        assertThrows(IllegalArgumentException.class, () -> PresentationModuleMapper.map(instruction));
    }

    @Test
    void rejectsMalformedRendererIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan("no-namespace", PresentationCueKind.BEAM, false, 0.8, Map.of(),
                        List.of(PresentationModuleKind.BEAM)));
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan("vector_regnum:", PresentationCueKind.BEAM, false, 0.8, Map.of(),
                        List.of(PresentationModuleKind.BEAM)));
    }

    @Test
    void acceptsSixteenParametersAndRejectsSeventeen() {
        new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, true, 0.8, boundedParameters(16),
                List.of(PresentationModuleKind.BEAM));
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, true, 0.8,
                        boundedParameters(17), List.of(PresentationModuleKind.BEAM)));
    }

    @Test
    void rejectsNonFiniteParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, false, 0.8,
                        Map.of("width", Double.NaN), List.of(PresentationModuleKind.BEAM)));
    }

    @Test
    void rejectsEmptyModuleList() {
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, false, 0.8, Map.of(),
                        List.of()));
    }

    @Test
    void rejectsTruthPlanWithOnlyCosmeticModules() {
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.CAMERA, true, 0.8, Map.of(),
                        List.of(PresentationModuleKind.FRAMEBUFFER, PresentationModuleKind.POST_PROCESS)));
    }

    @Test
    void acceptsNonTruthPlanWithOnlyCosmeticModules() {
        new PresentationModulePlan(RENDERER, PresentationCueKind.CAMERA, false, 0.8, Map.of(),
                List.of(PresentationModuleKind.FRAMEBUFFER, PresentationModuleKind.POST_PROCESS));
    }

    @Test
    void rejectsInvalidIntensity() {
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, false, 1.5, Map.of(),
                        List.of(PresentationModuleKind.BEAM)));
        assertThrows(IllegalArgumentException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, false, Double.NaN, Map.of(),
                        List.of(PresentationModuleKind.BEAM)));
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> PresentationModuleMapper.map(null));
        assertThrows(NullPointerException.class,
                () -> new PresentationModulePlan(null, PresentationCueKind.BEAM, false, 0.8, Map.of(),
                        List.of(PresentationModuleKind.BEAM)));
        assertThrows(NullPointerException.class,
                () -> new PresentationModulePlan(RENDERER, null, false, 0.8, Map.of(),
                        List.of(PresentationModuleKind.BEAM)));
        assertThrows(NullPointerException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, false, 0.8, null,
                        List.of(PresentationModuleKind.BEAM)));
        assertThrows(NullPointerException.class,
                () -> new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, false, 0.8, Map.of(), null));
    }

    @Test
    void exposesImmutableModules() {
        PresentationModulePlan plan = PresentationModuleMapper.map(
                instruction(PresentationCueKind.BEAM, true, Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> plan.modules().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.modules().add(PresentationModuleKind.PARTICLE));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.modules().remove(0));
    }

    @Test
    void exposesImmutableParameters() {
        PresentationModulePlan plan = PresentationModuleMapper.map(
                instruction(PresentationCueKind.BEAM, true, Map.of("width", 0.5)));
        assertThrows(UnsupportedOperationException.class, () -> plan.parameters().put("width", 1.0));
        assertThrows(UnsupportedOperationException.class, () -> plan.parameters().clear());
    }

    @Test
    void copiesMutableInputCollections() {
        Map<String, Double> params = new HashMap<>(Map.of("width", 0.5));
        List<PresentationModuleKind> modules = new ArrayList<>(List.of(PresentationModuleKind.BEAM));
        PresentationModulePlan plan = new PresentationModulePlan(RENDERER, PresentationCueKind.BEAM, true, 0.8,
                params, modules);
        params.put("extra", 1.0);
        modules.add(PresentationModuleKind.FRAMEBUFFER);
        assertEquals(Map.of("width", 0.5), plan.parameters());
        assertEquals(List.of(PresentationModuleKind.BEAM), plan.modules());
    }

    private static PresentationInstruction instruction(PresentationCueKind cue, boolean truthLayer,
            Map<String, Double> parameters) {
        return instruction(cue, truthLayer, parameters, RENDERER);
    }

    private static PresentationInstruction instruction(PresentationCueKind cue, boolean truthLayer,
            Map<String, Double> parameters, String rendererId) {
        return new PresentationInstruction(PresentationTrigger.cast(), PresentationPhase.RELEASE, cue,
                rendererId, PresentationBinding.AFFECTED_AREA, 0, 20, 0.8, truthLayer, parameters,
                PresentationCost.ZERO);
    }

    private static Map<String, Double> boundedParameters(int count) {
        Map<String, Double> params = new HashMap<>();
        for (int i = 0; i < count; i++) {
            params.put("p" + i, (double) i);
        }
        return params;
    }

    private static String namespaceOf(String rendererId) {
        return rendererId.substring(0, rendererId.indexOf(':'));
    }
}
