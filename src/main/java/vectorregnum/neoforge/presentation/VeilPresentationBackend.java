package vectorregnum.neoforge.presentation;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.quasar.particle.ParticleEmitter;
import foundry.veil.api.quasar.particle.ParticleSystemManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import foundry.veil.api.quasar.data.EmitterShapeSettings;
import foundry.veil.api.quasar.emitters.shape.Cylinder;
import foundry.veil.api.quasar.emitters.shape.Sphere;
import foundry.veil.api.quasar.emitters.shape.Torus;
import vectorregnum.core.presentation.PresentationAccessibility;
import vectorregnum.core.presentation.PresentationCueKind;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationEnhancementPolicy;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.neoforge.VectorRegnumMod;

/**
 * Optional Veil 4.4.1 adapter. When active it owns every Vector-Regnum
 * particle-based animation through capped Quasar motifs (rings, beams, bursts,
 * motes, sparks, smoke), deferred lights, and optional bloom. The mandatory
 * built-in renderer remains the Veil-absent/failure fallback and still draws
 * the enchanting-table truth cue.
 */
final class VeilPresentationBackend implements ClientPresentationBackend {
    private static final int MAX_LIGHTS = 8;
    private static final int MAX_EMITTERS = 16;
    private static final ResourceLocation BLOOM = ResourceLocation.fromNamespaceAndPath(
            "veil", "core/bloom");
    private static final String RING_MARKERS = "invocation_circle|selection_boundary|barrier|aura";
    private static final String BEAM_MARKERS =
            "raycast|projectile|vector_motion|redstone_pulse|trace/beam";
    private final Map<Long, LightRenderHandle<PointLightData>> lights = new HashMap<>();
    private final Map<Long, ParticleEmitter> emitters = new HashMap<>();
    private final Set<Long> postCues = new HashSet<>();
    private final Set<ResourceLocation> missingEmitters = new HashSet<>();
    private final boolean irisCompatibilityMode;

    VeilPresentationBackend() {
        irisCompatibilityMode = ModList.get().isLoaded("iris");
        if (irisCompatibilityMode) {
            VectorRegnumMod.LOGGER.info(
                    "Veil renderer compatibility mode: Iris detected; deferred lights and bloom disabled");
        }
    }

    @Override
    public String id() {
        return "veil-4.4.1";
    }

    @Override
    public void cueStarted(PresentationCueContext cue,
            PresentationAccessibility accessibility) {
        PresentationEnhancementPolicy policy = PresentationEnhancementPolicy.select(
                cue.modulePlan(), cue.lod(), accessibility);
        if (!irisCompatibilityMode && policy.deferredLights() && lights.size() < MAX_LIGHTS) {
            addLight(cue, accessibility);
        }
        if (policy.particles() && emitters.size() < MAX_EMITTERS) {
            addEmitter(cue, accessibility);
        }
        if (!irisCompatibilityMode && policy.postProcessing()) {
            if (postCues.add(cue.cueId()) && postCues.size() == 1) {
                postManager().add(900, BLOOM);
            }
        }
    }

    @Override
    public void cueTick(PresentationCueContext cue, PresentationAccessibility accessibility,
            int localAge, int duration, double envelope) {
        LightRenderHandle<PointLightData> handle = lights.get(cue.cueId());
        if (handle == null || !handle.isValid()) return;
        double magnitude = parameter(cue, "magnitude", 1.0);
        double radius = parameter(cue, "radius", 2.0);
        float brightness = (float) Math.clamp(envelope * (0.7 + magnitude * 0.16), 0.0, 2.0);
        if (accessibility.photosensitive()) brightness = Math.min(brightness, 0.35F);
        PointLightData light = handle.getLightData();
        light.setPosition(cue.point().x, cue.point().y, cue.point().z)
                .setRadius((float) Math.clamp(radius * 1.4, 1.25, 12.0))
                .setBrightness(brightness);
        handle.markDirty();
    }

    @Override
    public void cueEnded(long cueId) {
        LightRenderHandle<PointLightData> light = lights.remove(cueId);
        if (light != null && light.isValid()) light.free();
        ParticleEmitter emitter = emitters.remove(cueId);
        if (emitter != null) emitter.remove();
        if (postCues.remove(cueId) && postCues.isEmpty()) postManager().remove(BLOOM);
    }

    @Override
    public void resourceReloaded() {
        clear();
        missingEmitters.clear();
    }

    @Override
    public void clear() {
        for (LightRenderHandle<PointLightData> light : lights.values()) {
            if (light.isValid()) light.free();
        }
        lights.clear();
        for (ParticleEmitter emitter : emitters.values()) emitter.remove();
        emitters.clear();
        if (!postCues.isEmpty()) postManager().remove(BLOOM);
        postCues.clear();
    }

    private void addLight(PresentationCueContext cue,
            PresentationAccessibility accessibility) {
        PresentationElement element = PresentationElement.fromParameter(
                parameter(cue, "element", 0.0));
        float[] color = color(element, accessibility.chromaticIntensity());
        PointLightData data = new PointLightData()
                .setPosition(cue.point().x, cue.point().y, cue.point().z)
                .setRadius((float) Math.clamp(parameter(cue, "radius", 2.0) * 1.4, 1.25, 12.0))
                .setColor(color[0], color[1], color[2])
                .setBrightness(0.0F)
                .setOcclusionEnabled(true);
        lights.put(cue.cueId(), VeilRenderSystem.renderer().getLightRenderer().addLight(data));
    }

    private void addEmitter(PresentationCueContext cue,
            PresentationAccessibility accessibility) {
        String rendererId = cue.instruction().rendererId();
        boolean ringFamily = rendererId.contains("trace/ring")
                || rendererId.contains("invocation_circle") || rendererId.contains("selection_boundary")
                || rendererId.contains("barrier") || rendererId.contains("aura");
        boolean beamFamily = cue.instruction().cueKind() == PresentationCueKind.BEAM
                || cue.instruction().cueKind() == PresentationCueKind.TRAIL
                || cue.instruction().cueKind() == PresentationCueKind.RIBBON
                || rendererId.contains("trace/beam") || rendererId.contains("raycast")
                || rendererId.contains("projectile") || rendererId.contains("vector_motion")
                || rendererId.contains("redstone_pulse");
        boolean burstFamily = rendererId.contains("trace/burst")
                || rendererId.contains("fractured");
        PresentationElement element = PresentationElement.fromParameter(
                parameter(cue, "element", 0.0));
        PresentationParticleStyle style = style(cue);
        ResourceLocation id = emitterId(ringFamily, beamFamily, burstFamily, style, element);
        ParticleSystemManager manager = VeilRenderSystem.renderer().getParticleManager();
        ParticleEmitter emitter = manager.createEmitter(id);
        if (emitter == null) {
            if (missingEmitters.add(id)) {
                VectorRegnumMod.LOGGER.warn("Veil Quasar emitter did not load: {}", id);
            }
            return;
        }
        double density = accessibility.effectiveParticleDensity() * cue.lod().density();
        emitter.setPosition(cue.point());
        applyShape(cue, emitter, ringFamily, beamFamily);
        int count = Math.clamp((int) Math.ceil(baseCount(style, burstFamily) * density), 1, 8);
        emitter.setCount(count);
        emitter.setMaxParticles(Math.clamp(
                (int) Math.ceil(maxParticles(style, burstFamily, ringFamily) * density), 4, 64));
        manager.addParticleSystem(emitter);
        emitters.put(cue.cueId(), emitter);
    }

    /** Orients rings into their authored plane and beams along their segment. */
    private static void applyShape(PresentationCueContext cue, ParticleEmitter emitter,
            boolean ringFamily, boolean beamFamily) {
        double radius = Math.clamp(parameter(cue, "radius", 2.0), 0.25, 32.0);
        if (ringFamily) {
            Vector3fc rotation = cue.modulePlan().parameters().containsKey("axis_rx")
                    ? eulerDegrees(axisVector(cue, "axis_r"), axisVector(cue, "axis_u"))
                    : new org.joml.Vector3f(-90.0F, 0.0F, 0.0F);
            emitter.setEmitterShapeSettings(List.of(new EmitterShapeSettings(new Torus(),
                    new org.joml.Vector3f((float) radius, (float) radius, 0.06F),
                    rotation, true)));
            return;
        }
        if (beamFamily) {
            float length = (float) Math.clamp(parameter(cue, "length",
                    Math.max(2.0, radius * 2.0)), 0.5, 96.0);            Vec3 direction = cue.direction();
            Vector3fc rotation = eulerDegrees(perpendicular(direction), direction);
            emitter.setEmitterShapeSettings(List.of(new EmitterShapeSettings(new Cylinder(),
                    new org.joml.Vector3f(0.05F, length, 0.05F), rotation, false)));
            return;
        }
        emitter.setEmitterShapeSettings(List.of(new EmitterShapeSettings(new Sphere(),
                new org.joml.Vector3f((float) Math.min(radius, 4.0),
                        (float) Math.min(radius, 4.0), (float) Math.min(radius, 4.0)),
                new org.joml.Vector3f(0.0F, 0.0F, 0.0F), true)));
    }

    /**
     * Euler XYZ degrees reproducing Quasar's rotateX→rotateY→rotateZ application
     * order for a basis whose local Y maps to {@code up} and local X to
     * {@code right}.
     */
    static org.joml.Vector3f eulerDegrees(Vec3 right, Vec3 up) {
        Vector3f r = toVector(right);
        Vector3f u = toVector(up);
        Vector3f n = r.cross(u, new Vector3f()).normalize();
        Matrix3f basis = new Matrix3f()
                .setColumn(0, r)
                .setColumn(1, u)
                .setColumn(2, n);
        Vector3f radians = basis.getEulerAnglesXYZ(new Vector3f());
        return new org.joml.Vector3f((float) Math.toDegrees(radians.x),
                (float) Math.toDegrees(radians.y), (float) Math.toDegrees(radians.z));
    }

    private static Vector3f toVector(Vec3 value) {
        Vec3 normalized = value.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : value.normalize();
        return new Vector3f((float) normalized.x, (float) normalized.y, (float) normalized.z);
    }

    private static Vec3 axisVector(PresentationCueContext cue, String prefix) {
        return new Vec3(parameter(cue, prefix + "x", 0.0),
                parameter(cue, prefix + "y", 1.0), parameter(cue, prefix + "z", 0.0));
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return direction.cross(reference);
    }

    private static ResourceLocation emitterId(boolean ringFamily, boolean beamFamily,
            boolean burstFamily, PresentationParticleStyle style, PresentationElement element) {
        String name;
        if (ringFamily) {
            name = "presentation/ring/" + element.name().toLowerCase(java.util.Locale.ROOT);
        } else if (beamFamily) {
            name = "presentation/beam/" + element.name().toLowerCase(java.util.Locale.ROOT);
        } else if (burstFamily || style == PresentationParticleStyle.TOTEM
                || style == PresentationParticleStyle.EXPLOSION
                || style == PresentationParticleStyle.EXPLOSION_EMITTER) {
            name = "presentation/burst/" + element.name().toLowerCase(java.util.Locale.ROOT);
        } else if (style == PresentationParticleStyle.SMOKE
                || style == PresentationParticleStyle.LARGE_SMOKE
                || style == PresentationParticleStyle.WITCH
                || style == PresentationParticleStyle.CLOUD) {
            name = "presentation/smoke";
        } else if (style == PresentationParticleStyle.SPARK) {
            name = "presentation/spark";
        } else if (style == PresentationParticleStyle.END_ROD) {
            name = "presentation/light";
        } else {
            name = "presentation/" + element.name().toLowerCase(java.util.Locale.ROOT);
        }
        return ResourceLocation.fromNamespaceAndPath("vector_regnum", name);
    }

    private static PresentationParticleStyle style(PresentationCueContext cue) {
        int ordinal = (int) Math.round(parameter(cue, "style",
                (double) PresentationParticleStyle.MOTES.ordinal()));
        return PresentationParticleStyle.isValidOrdinal(ordinal)
                ? PresentationParticleStyle.values()[ordinal] : PresentationParticleStyle.MOTES;
    }

    private static int baseCount(PresentationParticleStyle style, boolean burstFamily) {
        if (burstFamily) return 6;
        if (style == PresentationParticleStyle.SPARK) return 5;
        if (style == PresentationParticleStyle.END_ROD) return 2;
        return 3;
    }

    private static int maxParticles(PresentationParticleStyle style, boolean burstFamily,
            boolean ringFamily) {
        if (ringFamily) return 48;
        if (burstFamily) return 40;
        if (style == PresentationParticleStyle.EXPLOSION_EMITTER) return 64;
        return 24;
    }

    private static PostProcessingManager postManager() {
        return VeilRenderSystem.renderer().getPostProcessingManager();
    }

    private static double parameter(PresentationCueContext cue, String name, double fallback) {
        return cue.modulePlan().parameters().getOrDefault(name, fallback);
    }

    private static float[] color(PresentationElement element, double chromaticIntensity) {
        if (chromaticIntensity <= 0.0) return new float[]{0.72F, 0.72F, 0.72F};
        float mix = (float) Math.clamp(chromaticIntensity, 0.0, 1.0);
        float[] color = switch (element) {
            case FIRE -> new float[]{1.0F, 0.24F, 0.04F};
            case FROST -> new float[]{0.38F, 0.82F, 1.0F};
            case VOID -> new float[]{0.52F, 0.08F, 0.72F};
            case ARCANE -> new float[]{0.68F, 0.30F, 1.0F};
        };
        for (int index = 0; index < color.length; index++) {
            color[index] = 0.72F + (color[index] - 0.72F) * mix;
        }
        return color;
    }
}
