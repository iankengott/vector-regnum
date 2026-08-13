package vectorregnum.fabric.presentation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import vectorregnum.core.presentation.PresentationAccessibility;
import vectorregnum.core.presentation.PresentationCueKind;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationInstruction;
import vectorregnum.core.presentation.PresentationLod;
import vectorregnum.core.presentation.PresentationProgram;
import vectorregnum.core.presentation.PresentationProgramCodec;
import vectorregnum.core.presentation.PresentationQuality;
import vectorregnum.core.presentation.PresentationSignal;
import vectorregnum.core.presentation.PresentationTrigger;

/**
 * Bounded client interpreter for compiler-generated spell scores. Essential geometry
 * uses vanilla-compatible particles, so truth cues survive without shader support.
 */
public final class ClientPresentationRuntime {
    private static final int MAX_INSTANCES = 32;
    private static final int MAX_ACTIVE_CUES = 96;
    private static final Map<Long, Instance> INSTANCES = new LinkedHashMap<>();
    private static final List<ActiveCue> ACTIVE_CUES = new ArrayList<>();
    private static PresentationAccessibility accessibility = PresentationAccessibility.DEFAULT;
    private static boolean initialized;

    private ClientPresentationRuntime() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(PresentationStartPayload.ID,
                (payload, context) -> context.client().execute(() -> start(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PresentationSignalPayload.ID,
                (payload, context) -> context.client().execute(() -> signal(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(ClientPresentationRuntime::tick);
        HudRenderCallback.EVENT.register(ClientPresentationRuntime::renderHud);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static PresentationAccessibility accessibility() { return accessibility; }

    public static void setAccessibility(PresentationAccessibility settings) {
        accessibility = settings == null ? PresentationAccessibility.DEFAULT : settings;
    }

    public static void cycleQuality(MinecraftClient client) {
        PresentationQuality[] values = PresentationQuality.values();
        PresentationQuality next = values[(accessibility.quality().ordinal() + 1) % values.length];
        accessibility = new PresentationAccessibility(next, accessibility.particleDensity(),
                accessibility.darknessAndFog(), accessibility.flashIntensity(),
                accessibility.chromaticIntensity(), accessibility.cameraMovement(),
                accessibility.audioIntensity(), accessibility.reducedMotion(),
                accessibility.photosensitive());
        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.translatable(
                    "message.vector_regnum.presentation_quality",
                    net.minecraft.text.Text.translatable("options.vector_regnum.presentation_quality."
                            + next.name().toLowerCase(java.util.Locale.ROOT))), true);
        }
    }

    private static void start(PresentationStartPayload payload) {
        try {
            PresentationProgram program = PresentationProgramCodec.decode(payload.encodedProgram());
            if (INSTANCES.size() >= MAX_INSTANCES) {
                Iterator<Long> iterator = INSTANCES.keySet().iterator();
                if (iterator.hasNext()) { iterator.next(); iterator.remove(); }
            }
            Vec3d origin = new Vec3d(payload.originX(), payload.originY(), payload.originZ());
            Vec3d direction = new Vec3d(payload.directionX(), payload.directionY(), payload.directionZ());
            Instance instance = new Instance(program, origin,
                    direction.lengthSquared() < 1.0e-8 ? new Vec3d(0, 0, 1) : direction.normalize());
            INSTANCES.put(payload.instanceId(), instance);
            enqueue(instance, new PresentationSignal(0, 0, PresentationTrigger.Kind.CAST,
                    java.util.Optional.empty(), java.util.Optional.empty(), -1,
                    origin.x, origin.y, origin.z));
        } catch (RuntimeException ignored) {
            // Invalid remote presentation data is discarded and cannot affect play.
        }
    }

    private static void signal(PresentationSignalPayload payload) {
        Instance instance = INSTANCES.get(payload.instanceId());
        if (instance == null || payload.signal().sequence() <= instance.lastSequence) return;
        instance.lastSequence = payload.signal().sequence();
        enqueue(instance, payload.signal());
        if (payload.signal().kind() == PresentationTrigger.Kind.HALT
                || payload.signal().kind() == PresentationTrigger.Kind.FAULT) {
            instance.terminalAge = 0;
        }
    }

    private static void enqueue(Instance instance, PresentationSignal signal) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Vec3d point = new Vec3d(signal.x(), signal.y(), signal.z());
        PresentationLod lod = PresentationLod.select(client.player.getPos().distanceTo(point),
                accessibility.quality());
        for (PresentationInstruction instruction : instance.program.instructions()) {
            if (!signal.matches(instruction) || !lod.renders(instruction)) continue;
            if (ACTIVE_CUES.size() >= MAX_ACTIVE_CUES) break;
            ACTIVE_CUES.add(new ActiveCue(instance, instruction, point, lod,
                    new SplittableRandom(instance.program.deterministicSeed()
                            ^ signal.sequence() * 0x9E3779B97F4A7C15L
                            ^ instruction.rendererId().hashCode())));
        }
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) { clear(); return; }
        Iterator<ActiveCue> iterator = ACTIVE_CUES.iterator();
        while (iterator.hasNext()) {
            ActiveCue cue = iterator.next();
            if (!cue.tick(client)) iterator.remove();
        }
        Iterator<Instance> instances = INSTANCES.values().iterator();
        while (instances.hasNext()) {
            Instance instance = instances.next();
            if (instance.terminalAge >= 0 && ++instance.terminalAge > 60) instances.remove();
        }
    }

    private static void clear() { INSTANCES.clear(); ACTIVE_CUES.clear(); }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) return;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        double darkness = 0, frost = 0, fault = 0;
        for (ActiveCue cue : ACTIVE_CUES) {
            if (!cue.lod.screenLayers() || cue.age <= cue.instruction.startOffsetTicks()) continue;
            double envelope = cue.envelope();
            switch (cue.instruction.cueKind()) {
                case DARKNESS -> darkness = Math.max(darkness, envelope);
                case FOG -> frost = Math.max(frost, envelope);
                case SCREEN -> fault = Math.max(fault, envelope);
                default -> { }
            }
        }
        darkness *= accessibility.darknessAndFog();
        frost *= accessibility.darknessAndFog();
        fault *= accessibility.flashIntensity();
        if (darkness > 0) context.fill(0, 0, width, height,
                alpha(darkness * .22) << 24 | 0x110b18);
        if (frost > 0) {
            int color = alpha(frost * .16) << 24 | 0xb9e8ef;
            context.fill(0, 0, width, Math.max(2, height / 12), color);
            context.fill(0, height - Math.max(2, height / 12), width, height, color);
        }
        if (fault > 0) {
            int red = accessibility.chromaticIntensity() > 0 ? 0x722448 : 0x5f5f5f;
            int color = alpha(fault * .24) << 24 | red;
            int border = Math.max(2, (int) (6 * accessibility.cameraMovement()));
            context.fill(0, 0, width, border, color);
            context.fill(0, height - border, width, height, color);
            context.fill(0, 0, border, height, color);
            context.fill(width - border, 0, width, height, color);
        }
    }

    private static int alpha(double value) {
        return Math.clamp((int) Math.round(value * 255), 0, 255);
    }

    private static final class Instance {
        private final PresentationProgram program;
        private final Vec3d origin;
        private final Vec3d direction;
        private long lastSequence = -1;
        private int terminalAge = -1;

        private Instance(PresentationProgram program, Vec3d origin, Vec3d direction) {
            this.program = program; this.origin = origin; this.direction = direction;
        }
    }

    private static final class ActiveCue {
        private final Instance instance;
        private final PresentationInstruction instruction;
        private final Vec3d point;
        private final PresentationLod lod;
        private final SplittableRandom random;
        private int age;
        private boolean soundPlayed;

        private ActiveCue(Instance instance, PresentationInstruction instruction,
                Vec3d point, PresentationLod lod, SplittableRandom random) {
            this.instance = instance; this.instruction = instruction;
            this.point = point; this.lod = lod; this.random = random;
        }

        private boolean tick(MinecraftClient client) {
            int offset = instruction.startOffsetTicks();
            int duration = instruction.durationTicks();
            if (age++ < offset) return true;
            int localAge = age - offset - 1;
            if (localAge >= duration) return false;
            if (instruction.cueKind() == PresentationCueKind.SPATIAL_SOUND) {
                if (!soundPlayed) { soundPlayed = true; playSound(client); }
                return true;
            }
            int interval = instruction.truthLayer() ? 2 : 3;
            if (localAge % interval != 0) return true;
            render(client, localAge, duration);
            return true;
        }

        private void render(MinecraftClient client, int localAge, int duration) {
            if (instruction.cueKind() == PresentationCueKind.SCREEN
                    || instruction.cueKind() == PresentationCueKind.DARKNESS
                    || instruction.cueKind() == PresentationCueKind.FOG
                    || instruction.cueKind() == PresentationCueKind.CAMERA) return;
            double density = accessibility.effectiveParticleDensity() * lod.density();
            if (client.options.getParticles().getValue() == ParticlesMode.MINIMAL) density *= .4;
            int count = instruction.truthLayer() ? Math.max(1, (int) Math.ceil(3 * density))
                    : (int) Math.floor(3 * density);
            if (count <= 0) return;
            PresentationElement element = PresentationElement.fromParameter(parameter("element", 0));
            ParticleEffect primary = particle(element);
            double radius = Math.clamp(parameter("radius", 2), .5, 32);
            double progress = (localAge + 1.0) / duration;
            String renderer = instruction.rendererId();
            if (renderer.contains("invocation_circle") || renderer.contains("selection_boundary")
                    || renderer.contains("barrier") || renderer.contains("aura")) {
                double shownRadius = renderer.contains("invocation") ? .75 : radius;
                ring(client, point.add(0, -.35, 0), shownRadius * Math.min(1, progress * 2),
                        primary, Math.max(8, count * 6));
            } else if (renderer.contains("raycast") || renderer.contains("projectile")
                    || renderer.contains("vector_motion") || renderer.contains("redstone_pulse")) {
                line(client, instance.origin, instance.origin.add(instance.direction.multiply(
                        Math.max(2, radius * 2))), primary, Math.max(4, count * 3));
            } else if (renderer.contains("fractured")) {
                for (int index = 0; index < count * 2; index++) {
                    Vec3d offset = randomOffset(.7 + progress);
                    add(client, ParticleTypes.LARGE_SMOKE, point.add(offset), 0, .01, 0);
                }
            } else {
                for (int index = 0; index < count; index++) {
                    Vec3d offset = randomOffset(Math.min(radius, .4 + radius * progress));
                    ParticleEffect effect = renderer.contains("dust")
                            || renderer.contains("material") ? ParticleTypes.CLOUD : primary;
                    add(client, effect, point.add(offset), -offset.x * .015,
                            .005 + random.nextDouble() * .015, -offset.z * .015);
                }
            }
            if (instruction.cueKind() == PresentationCueKind.LIGHT && localAge % 4 == 0) {
                add(client, ParticleTypes.END_ROD, point, 0, .01, 0);
            }
        }

        private void playSound(MinecraftClient client) {
            if (accessibility.audioIntensity() <= 0 || client.world == null) return;
            PresentationElement element = PresentationElement.fromParameter(parameter("element", 0));
            SoundEvent sound = instruction.rendererId().contains("fault")
                    ? SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK
                    : instruction.rendererId().contains("tail")
                            ? SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE
                            : switch (element) {
                                case FIRE -> SoundEvents.ENTITY_BLAZE_SHOOT;
                                case FROST -> SoundEvents.BLOCK_GLASS_HIT;
                                case VOID -> SoundEvents.ENTITY_ENDERMAN_TELEPORT;
                                case ARCANE -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
                            };
            client.world.playSound(null, point.x, point.y, point.z, sound,
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    (float) (instruction.intensity() * accessibility.audioIntensity()),
                    (float) (0.9 + random.nextDouble() * .2));
        }

        private double parameter(String key, double fallback) {
            return instruction.parameters().getOrDefault(key, fallback);
        }

        private double envelope() {
            int localAge = age - instruction.startOffsetTicks();
            if (localAge < 0 || localAge >= instruction.durationTicks()) return 0;
            double progress = localAge / (double) instruction.durationTicks();
            double fade = Math.min(1.0, Math.min(progress * 4.0, (1.0 - progress) * 4.0));
            return instruction.intensity() * Math.max(0, fade);
        }

        private Vec3d randomOffset(double scale) {
            return new Vec3d(random.nextDouble(-scale, scale),
                    random.nextDouble(-scale * .35, scale * .55),
                    random.nextDouble(-scale, scale));
        }

        private static ParticleEffect particle(PresentationElement element) {
            return switch (element) {
                case FIRE -> ParticleTypes.FLAME;
                case FROST -> ParticleTypes.SNOWFLAKE;
                case VOID -> ParticleTypes.PORTAL;
                case ARCANE -> ParticleTypes.ENCHANT;
            };
        }

        private static void ring(MinecraftClient client, Vec3d center, double radius,
                ParticleEffect particle, int points) {
            for (int index = 0; index < points; index++) {
                double angle = Math.PI * 2 * index / points;
                add(client, particle, center.add(Math.cos(angle) * radius, 0,
                        Math.sin(angle) * radius), 0, .004, 0);
            }
        }

        private static void line(MinecraftClient client, Vec3d start, Vec3d end,
                ParticleEffect particle, int points) {
            for (int index = 0; index <= points; index++) {
                add(client, particle, start.lerp(end, index / (double) points), 0, 0, 0);
            }
        }

        private static void add(MinecraftClient client, ParticleEffect particle, Vec3d point,
                double velocityX, double velocityY, double velocityZ) {
            if (client.world != null) client.world.addParticle(particle, point.x, point.y, point.z,
                    velocityX, velocityY, velocityZ);
        }
    }
}
