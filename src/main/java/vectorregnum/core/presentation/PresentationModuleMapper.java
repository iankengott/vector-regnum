package vectorregnum.core.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PresentationModuleMapper {
    private PresentationModuleMapper() {
    }

    public static PresentationModulePlan map(PresentationInstruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        PresentationCueKind cue = Objects.requireNonNull(instruction.cueKind(), "cueKind");
        List<PresentationModuleKind> modules = baseModules(cue);
        if (instruction.truthLayer()) {
            modules = truthHardened(modules, cue);
        }
        return new PresentationModulePlan(instruction.rendererId(), cue, instruction.truthLayer(),
                instruction.intensity(), instruction.parameters(), modules);
    }

    private static List<PresentationModuleKind> baseModules(PresentationCueKind cue) {
        return switch (cue) {
            case PARTICLES -> List.of(PresentationModuleKind.PARTICLE);
            case BEAM -> List.of(PresentationModuleKind.BEAM);
            case RIBBON -> List.of(PresentationModuleKind.RIBBON);
            case TRAIL -> List.of(PresentationModuleKind.TRAIL);
            case RUNES -> List.of(PresentationModuleKind.RUNE);
            case SURFACE -> List.of(PresentationModuleKind.SURFACE);
            case VOLUME -> List.of(PresentationModuleKind.VOLUME);
            case LIGHT -> List.of(PresentationModuleKind.DEFERRED_LIGHT, PresentationModuleKind.FRAMEBUFFER);
            case DARKNESS -> List.of(PresentationModuleKind.VOLUME, PresentationModuleKind.POST_PROCESS);
            case FOG -> List.of(PresentationModuleKind.VOLUME, PresentationModuleKind.FRAMEBUFFER);
            case AIR -> List.of(PresentationModuleKind.PARTICLE);
            case MATERIAL_RESPONSE -> List.of(PresentationModuleKind.ANIMATED_MESH);
            case SPATIAL_SOUND -> List.of(PresentationModuleKind.SPATIAL_AUDIO);
            case CAMERA -> List.of(PresentationModuleKind.FRAMEBUFFER, PresentationModuleKind.POST_PROCESS);
            case SCREEN -> List.of(PresentationModuleKind.FRAMEBUFFER, PresentationModuleKind.POST_PROCESS);
            case AFTERMATH -> List.of(PresentationModuleKind.PARTICLE, PresentationModuleKind.ANIMATED_MESH);
        };
    }

    private static List<PresentationModuleKind> truthHardened(List<PresentationModuleKind> base,
            PresentationCueKind cue) {
        if (base.stream().anyMatch(module -> !module.isCosmeticOnly())) {
            return base;
        }
        List<PresentationModuleKind> hardened = new ArrayList<>(base);
        hardened.add(concreteFallback(cue));
        return List.copyOf(hardened);
    }

    private static PresentationModuleKind concreteFallback(PresentationCueKind cue) {
        return switch (cue) {
            case PARTICLES -> PresentationModuleKind.PARTICLE;
            case BEAM -> PresentationModuleKind.BEAM;
            case RIBBON -> PresentationModuleKind.RIBBON;
            case TRAIL -> PresentationModuleKind.TRAIL;
            case RUNES -> PresentationModuleKind.RUNE;
            case SURFACE -> PresentationModuleKind.SURFACE;
            case VOLUME -> PresentationModuleKind.VOLUME;
            case LIGHT -> PresentationModuleKind.DEFERRED_LIGHT;
            case DARKNESS -> PresentationModuleKind.DEFERRED_LIGHT;
            case FOG -> PresentationModuleKind.VOLUME;
            case AIR -> PresentationModuleKind.PARTICLE;
            case MATERIAL_RESPONSE -> PresentationModuleKind.ANIMATED_MESH;
            case SPATIAL_SOUND -> PresentationModuleKind.SPATIAL_AUDIO;
            case CAMERA -> PresentationModuleKind.PARTICLE;
            case SCREEN -> PresentationModuleKind.PARTICLE;
            case AFTERMATH -> PresentationModuleKind.PARTICLE;
        };
    }
}
