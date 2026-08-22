package vectorregnum.core.presentation;

import java.util.Set;

/** Pure policy gate for optional renderer features. Built-in truth cues bypass this gate. */
public record PresentationEnhancementPolicy(boolean particles, boolean deferredLights,
        boolean postProcessing) {
    public static final PresentationEnhancementPolicy DISABLED =
            new PresentationEnhancementPolicy(false, false, false);

    public static PresentationEnhancementPolicy select(PresentationModulePlan plan,
            PresentationLod lod, PresentationAccessibility accessibility) {
        if (plan == null) throw new NullPointerException("plan");
        if (lod == null) throw new NullPointerException("lod");
        if (accessibility == null) throw new NullPointerException("accessibility");
        if (accessibility.quality() == PresentationQuality.MINIMAL
                || lod == PresentationLod.TELEGRAPH_ONLY) {
            return DISABLED;
        }
        Set<PresentationModuleKind> modules = Set.copyOf(plan.modules());
        boolean moving = modules.contains(PresentationModuleKind.TRAIL)
                || modules.contains(PresentationModuleKind.RIBBON)
                || modules.contains(PresentationModuleKind.ANIMATED_MESH);
        boolean particles = accessibility.effectiveParticleDensity() > 0.0
                && !(accessibility.reducedMotion() && moving)
                && (modules.contains(PresentationModuleKind.PARTICLE)
                        || modules.contains(PresentationModuleKind.BEAM)
                        || modules.contains(PresentationModuleKind.TRAIL)
                        || modules.contains(PresentationModuleKind.RIBBON)
                        || modules.contains(PresentationModuleKind.RUNE)
                        || modules.contains(PresentationModuleKind.ANIMATED_MESH)
                        || modules.contains(PresentationModuleKind.SURFACE)
                        || modules.contains(PresentationModuleKind.VOLUME));
        boolean lights = modules.contains(PresentationModuleKind.DEFERRED_LIGHT);
        boolean post = lod.screenLayers()
                && !accessibility.photosensitive()
                && accessibility.flashIntensity() > 0.0
                && (modules.contains(PresentationModuleKind.POST_PROCESS)
                        || modules.contains(PresentationModuleKind.FRAMEBUFFER));
        return new PresentationEnhancementPolicy(particles, lights, post);
    }
}
