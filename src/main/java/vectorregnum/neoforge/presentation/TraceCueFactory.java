package vectorregnum.neoforge.presentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.presentation.PresentationBinding;
import vectorregnum.core.presentation.PresentationBudget;
import vectorregnum.core.presentation.PresentationCost;
import vectorregnum.core.presentation.PresentationCueKind;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationInstruction;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.core.presentation.PresentationPhase;
import vectorregnum.core.presentation.PresentationProgram;
import vectorregnum.core.presentation.PresentationTrigger;
import vectorregnum.core.presentation.PresentationTraceKind;

/**
 * Pure factory that turns compact authoritative trace payloads into bounded
 * single-cue presentation programs. Loader-neutral and free of client runtime
 * state so it can be verified by ordinary unit tests.
 */
public final class TraceCueFactory {
    /** One synthesized cue ready for the shared client cue pipeline. */
    public record SynthesizedCue(PresentationProgram program,
            PresentationInstruction instruction, Vec3 origin, Vec3 direction, Vec3 point) { }

    private static final PresentationCost TRACE_COST =
            new PresentationCost(1, 64, 128, 1, 0, 0, 1);

    private TraceCueFactory() { }

    /** Expands one payload into at most {@code 1 + extras} synthesized cues. */
    public static List<SynthesizedCue> synthesize(PresentationTracePayload payload) {
        List<SynthesizedCue> cues = new ArrayList<>(payload.extraPoints().size() + 1);
        cues.add(single(payload.instanceId(), payload.kind(), payload.style(), payload.element(),
                new Vec3(payload.x(), payload.y(), payload.z()),
                payload.hasTarget() ? new Vec3(payload.targetX(), payload.targetY(),
                        payload.targetZ()) : null,
                null, null, payload.radius(), payload.durationTicks(), payload.intensity(),
                payload.seed()));
        for (int index = 0; index < payload.extraPoints().size(); index++) {
            double[] point = payload.extraPoints().get(index);
            int override = payload.extraStyles().get(index);
            cues.add(single(payload.instanceId(), payload.kind(),
                    override >= 0 ? PresentationParticleStyle.values()[override] : payload.style(),
                    payload.element(), new Vec3(point[0], point[1], point[2]), null, null, null,
                    payload.radius(), payload.durationTicks(), payload.intensity(),
                    payload.seed() + index));
        }
        return cues;
    }

    /** Builds the single-cue program for one trace emission point. */
    public static SynthesizedCue single(long instanceId, PresentationTraceKind kind,
            PresentationParticleStyle style, PresentationElement element, Vec3 point,
            Vec3 target, Vec3 ringRight, Vec3 ringUp, float radius, int durationTicks,
            float intensity, long seed) {
        String rendererId;
        PresentationCueKind cueKind;
        PresentationPhase phase;
        PresentationBinding binding;
        Map<String, Double> parameters = new LinkedHashMap<>();
        Vec3 origin = point;
        Vec3 direction = new Vec3(0, 0, 1);
        switch (kind) {
            case RING -> {
                rendererId = "vector_regnum:trace/ring";
                cueKind = PresentationCueKind.PARTICLES;
                phase = PresentationPhase.GATHERING;
                binding = PresentationBinding.AFFECTED_AREA;
                parameters.put("radius", (double) Math.clamp(radius, 0.5, 32.0));
                parameters.put("element", element.parameter());
                parameters.put("style", (double) style.ordinal());
                putAxes(parameters, ringRight, ringUp);
            }
            case BEAM -> {
                rendererId = "vector_regnum:trace/beam";
                cueKind = PresentationCueKind.BEAM;
                phase = PresentationPhase.TRAVEL;
                binding = PresentationBinding.PATH;
                Vec3 end = target == null ? point.add(0, 0, 2) : target;
                Vec3 segment = end.subtract(point);
                direction = segment.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1)
                        : segment.normalize();
                parameters.put("radius", (double) Math.clamp(radius, 0.05, 2.0));
                parameters.put("length", Math.clamp(segment.length(), 0.5, 96.0));
                parameters.put("element", element.parameter());
                parameters.put("style", (double) style.ordinal());
            }
            case BURST -> {
                rendererId = "vector_regnum:trace/burst";
                cueKind = PresentationCueKind.PARTICLES;
                phase = PresentationPhase.RELEASE;
                binding = PresentationBinding.IMPACT_POINT;
                parameters.put("radius", (double) Math.clamp(radius, 0.2, 32.0));
                parameters.put("element", element.parameter());
                parameters.put("style", (double) style.ordinal());
                parameters.put("count", (double) Math.clamp(
                        (int) Math.ceil(6.0 * intensity * (0.5 + radius * 0.25)), 1, 24));
            }
            default -> {
                rendererId = "vector_regnum:trace/motes";
                cueKind = PresentationCueKind.PARTICLES;
                phase = PresentationPhase.SUSTAIN;
                binding = PresentationBinding.AFFECTED_AREA;
                parameters.put("radius", (double) Math.clamp(radius, 0.2, 32.0));
                parameters.put("element", element.parameter());
                parameters.put("style", (double) style.ordinal());
            }
        }
        PresentationInstruction instruction = new PresentationInstruction(
                PresentationTrigger.worldEffect(), phase, cueKind, rendererId, binding,
                0, Math.clamp(durationTicks, 1, 1_200), Math.clamp(intensity, 0.0, 1.0),
                true, parameters, TRACE_COST);
        PresentationProgram program = new PresentationProgram("vector_regnum:trace", seed,
                List.of(instruction), PresentationBudget.DEFAULT);
        return new SynthesizedCue(program, instruction, origin, direction, point);
    }

    private static void putAxes(Map<String, Double> parameters, Vec3 right, Vec3 up) {
        if (right == null || up == null) return;
        Vec3 safeRight = right.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 safeUp = up.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : up.normalize();
        parameters.put("axis_rx", safeRight.x);
        parameters.put("axis_ry", safeRight.y);
        parameters.put("axis_rz", safeRight.z);
        parameters.put("axis_ux", safeUp.x);
        parameters.put("axis_uy", safeUp.y);
        parameters.put("axis_uz", safeUp.z);
    }
}
