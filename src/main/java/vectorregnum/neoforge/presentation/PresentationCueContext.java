package vectorregnum.neoforge.presentation;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.presentation.PresentationInstruction;
import vectorregnum.core.presentation.PresentationLod;
import vectorregnum.core.presentation.PresentationModuleMapper;
import vectorregnum.core.presentation.PresentationModulePlan;

/** Immutable, bounded view passed to optional client presentation backends. */
record PresentationCueContext(long cueId, PresentationInstruction instruction,
        PresentationModulePlan modulePlan, Vec3 origin, Vec3 direction, Vec3 point,
        PresentationLod lod, long deterministicSeed) {
    PresentationCueContext {
        if (cueId < 1) throw new IllegalArgumentException("cueId must be positive");
        Objects.requireNonNull(instruction, "instruction");
        Objects.requireNonNull(modulePlan, "modulePlan");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(lod, "lod");
    }

    static PresentationCueContext create(long cueId, PresentationInstruction instruction,
            Vec3 origin, Vec3 direction, Vec3 point, PresentationLod lod, long seed) {
        return new PresentationCueContext(cueId, instruction,
                PresentationModuleMapper.map(instruction), origin, direction, point, lod, seed);
    }
}
