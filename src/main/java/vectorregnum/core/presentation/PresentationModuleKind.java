package vectorregnum.core.presentation;

public enum PresentationModuleKind {
    PARTICLE,
    BEAM,
    RIBBON,
    TRAIL,
    RUNE,
    ANIMATED_MESH,
    SURFACE,
    VOLUME,
    DEFERRED_LIGHT,
    FRAMEBUFFER,
    POST_PROCESS,
    SPATIAL_AUDIO;

    public boolean isCosmeticOnly() {
        return this == FRAMEBUFFER || this == POST_PROCESS;
    }
}