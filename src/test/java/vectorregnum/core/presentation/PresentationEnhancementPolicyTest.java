package vectorregnum.core.presentation;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresentationEnhancementPolicyTest {
    @Test
    void minimalAndTelegraphOnlyDisableEveryOptionalLayer() {
        PresentationModulePlan plan = plan(List.of(PresentationModuleKind.PARTICLE,
                PresentationModuleKind.DEFERRED_LIGHT, PresentationModuleKind.POST_PROCESS));
        PresentationAccessibility minimal = settings(PresentationQuality.MINIMAL, false, false);
        assertEquals(PresentationEnhancementPolicy.DISABLED,
                PresentationEnhancementPolicy.select(plan, PresentationLod.NEAR, minimal));
        assertEquals(PresentationEnhancementPolicy.DISABLED,
                PresentationEnhancementPolicy.select(plan, PresentationLod.TELEGRAPH_ONLY,
                        PresentationAccessibility.DEFAULT));
    }

    @Test
    void photosensitiveModeBlocksPostButAllowsBoundedLightAndParticles() {
        PresentationModulePlan plan = plan(List.of(PresentationModuleKind.PARTICLE,
                PresentationModuleKind.DEFERRED_LIGHT, PresentationModuleKind.POST_PROCESS));
        PresentationEnhancementPolicy policy = PresentationEnhancementPolicy.select(plan,
                PresentationLod.NEAR, settings(PresentationQuality.FULL, false, true));
        assertTrue(policy.particles());
        assertTrue(policy.deferredLights());
        assertFalse(policy.postProcessing());
    }

    @Test
    void reducedMotionBlocksMovingEmitters() {
        PresentationEnhancementPolicy policy = PresentationEnhancementPolicy.select(
                plan(List.of(PresentationModuleKind.RIBBON)), PresentationLod.NEAR,
                settings(PresentationQuality.FULL, true, false));
        assertFalse(policy.particles());
    }

    @Test
    void everyMappedVisualGeometryFamilyReceivesABoundedEmitterMotif() {
        for (PresentationModuleKind module : List.of(PresentationModuleKind.PARTICLE,
                PresentationModuleKind.BEAM, PresentationModuleKind.RIBBON,
                PresentationModuleKind.TRAIL, PresentationModuleKind.RUNE,
                PresentationModuleKind.ANIMATED_MESH, PresentationModuleKind.SURFACE,
                PresentationModuleKind.VOLUME)) {
            assertTrue(PresentationEnhancementPolicy.select(plan(List.of(module)),
                    PresentationLod.NEAR, PresentationAccessibility.DEFAULT).particles(),
                    "optional motif for " + module);
        }
    }

    @Test
    void screenLayersRequireNearLodAndNonzeroFlashIntensity() {
        PresentationModulePlan plan = plan(List.of(PresentationModuleKind.POST_PROCESS));
        assertFalse(PresentationEnhancementPolicy.select(plan, PresentationLod.MID,
                PresentationAccessibility.DEFAULT).postProcessing());
        PresentationAccessibility noFlash = new PresentationAccessibility(
                PresentationQuality.FULL, 1.0, 1.0, 0.0, 0.35, 0.35, 1.0,
                false, false);
        assertFalse(PresentationEnhancementPolicy.select(plan, PresentationLod.NEAR,
                noFlash).postProcessing());
    }

    private static PresentationModulePlan plan(List<PresentationModuleKind> modules) {
        return new PresentationModulePlan("vector_regnum:test", PresentationCueKind.PARTICLES,
                false, 0.8, Map.of(), modules);
    }

    private static PresentationAccessibility settings(PresentationQuality quality,
            boolean reducedMotion, boolean photosensitive) {
        return new PresentationAccessibility(quality, 1.0, 1.0, 0.65, 0.35, 0.35,
                1.0, reducedMotion, photosensitive);
    }
}
