package vectorregnum.neoforge.presentation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import vectorregnum.core.presentation.PresentationAccessibility;
import vectorregnum.core.presentation.PresentationAccessibilityCodec;
import vectorregnum.core.presentation.PresentationCueKind;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationInstruction;
import vectorregnum.core.presentation.PresentationLod;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.core.presentation.PresentationProgram;
import vectorregnum.core.presentation.PresentationProgramCodec;
import vectorregnum.core.presentation.PresentationQuality;
import vectorregnum.core.presentation.PresentationSignal;
import vectorregnum.core.presentation.PresentationTrigger;

/**
 * Bounded client interpreter for compiler-generated spell scores and compact
 * authoritative world traces. With Veil active, Quasar motifs own every
 * particle-based animation except the enchanting-table truth cue; the guarded
 * built-in renderer is the mandatory Veil-absent/failure fallback.
 */
public final class ClientPresentationRuntime {
    private static final int MAX_INSTANCES = 32;
    private static final int MAX_ACTIVE_CUES = 96;
    private static final Map<Long, Instance> INSTANCES = new LinkedHashMap<>();
    private static final List<ActiveCue> ACTIVE_CUES = new ArrayList<>();
    private static PresentationAccessibility accessibility = PresentationAccessibility.DEFAULT;
    private static long nextCueId = 1;
    private static boolean initialized;

    private ClientPresentationRuntime() { }

    /** Registers every client-bound presentation payload. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(PresentationStartPayload.TYPE, PresentationStartPayload.CODEC,
                ClientPresentationRuntime::handleStartPayload)
                .playToClient(PresentationSignalPayload.TYPE, PresentationSignalPayload.CODEC,
                        ClientPresentationRuntime::handleSignalPayload)
                .playToClient(PresentationTracePayload.TYPE, PresentationTracePayload.CODEC,
                        ClientPresentationRuntime::handleTracePayload)
                .playToClient(CirclePreviewPayload.TYPE, CirclePreviewPayload.CODEC,
                        ClientPresentationRuntime::handleCirclePreviewPayload);
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        loadAccessibility();
        OptionalPresentationBackend.initialize();
        NeoForge.EVENT_BUS.addListener(ClientPresentationRuntime::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientPresentationRuntime::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ClientPresentationRuntime::onClientLoggingOut);
    }

    private static void handleStartPayload(PresentationStartPayload payload, IPayloadContext ignored) {
        start(payload);
    }

    private static void handleSignalPayload(PresentationSignalPayload payload, IPayloadContext ignored) {
        signal(payload);
    }

    private static void handleTracePayload(PresentationTracePayload payload,
            IPayloadContext ignored) {
        try {
            for (TraceCueFactory.SynthesizedCue cue : TraceCueFactory.synthesize(payload)) {
                spawnSynthesizedCue(cue);
            }
        } catch (RuntimeException ignored2) {
            // Invalid remote trace data is discarded and cannot affect play.
        }
    }

    private static void handleCirclePreviewPayload(CirclePreviewPayload payload,
            IPayloadContext ignored) {
        try {
            ClientCirclePreviews.start(payload);
        } catch (RuntimeException ignored2) {
            // Invalid remote preview data is discarded and cannot affect play.
        }
    }

    /** Spawns one factory-built cue through the shared bounded pipeline. */
    static void spawnSynthesizedCue(TraceCueFactory.SynthesizedCue cue) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (ACTIVE_CUES.size() >= MAX_ACTIVE_CUES) return;
        PresentationLod lod = PresentationLod.select(
                client.player.position().distanceTo(cue.point()), accessibility.quality());
        if (!lod.renders(cue.instruction())) return;
        Instance instance = new Instance(cue.program(), cue.origin(), cue.direction());
        ACTIVE_CUES.add(new ActiveCue(nextCueId++, instance, cue.instruction(), cue.point(),
                lod, new SplittableRandom(cue.program().deterministicSeed()
                        ^ cue.instruction().rendererId().hashCode())));
    }

    private static void onClientTick(ClientTickEvent.Post ignored) {
        tick(Minecraft.getInstance());
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        renderHud(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut ignored) {
        clear();
    }

    public static PresentationAccessibility accessibility() { return accessibility; }

    public static String backendId() { return OptionalPresentationBackend.id(); }

    public static boolean veilActive() { return OptionalPresentationBackend.veilActive(); }

    public static void onResourceReload() {
        clear();
        OptionalPresentationBackend.resourceReloaded();
    }

    public static void setAccessibility(PresentationAccessibility settings) {
        accessibility = settings == null ? PresentationAccessibility.DEFAULT : settings;
        saveAccessibility();
    }

    public static void cycleQuality(Minecraft client) {
        PresentationQuality[] values = PresentationQuality.values();
        PresentationQuality next = values[(accessibility.quality().ordinal() + 1) % values.length];
        accessibility = new PresentationAccessibility(next, accessibility.particleDensity(),
                accessibility.darknessAndFog(), accessibility.flashIntensity(),
                accessibility.chromaticIntensity(), accessibility.cameraMovement(),
                accessibility.audioIntensity(), accessibility.reducedMotion(),
                accessibility.photosensitive());
        if (client.player != null) {
            client.player.displayClientMessage(Component.translatable(
                    "message.vector_regnum.presentation_quality",
                    Component.translatable("options.vector_regnum.presentation_quality."
                            + next.name().toLowerCase(java.util.Locale.ROOT))), true);
        }
    }

    private static void start(PresentationStartPayload payload) {
        try {
            PresentationProgram program = PresentationProgramCodec.decode(payload.encodedProgram());
            if (INSTANCES.size() >= MAX_INSTANCES) {
                Iterator<Long> iterator = INSTANCES.keySet().iterator();
                if (iterator.hasNext()) { removeInstance(iterator.next()); }
            }
            Vec3 origin = new Vec3(payload.originX(), payload.originY(), payload.originZ());
            Vec3 direction = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
            Instance instance = new Instance(program, origin,
                    direction.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : direction.normalize());
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
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Vec3 point = new Vec3(signal.x(), signal.y(), signal.z());
        PresentationLod lod = PresentationLod.select(client.player.position().distanceTo(point),
                accessibility.quality());
        for (PresentationInstruction instruction : instance.program.instructions()) {
            if (!signal.matches(instruction) || !lod.renders(instruction)) continue;
            if (ACTIVE_CUES.size() >= MAX_ACTIVE_CUES) break;
            ACTIVE_CUES.add(new ActiveCue(nextCueId++, instance, instruction, point, lod,
                    new SplittableRandom(instance.program.deterministicSeed()
                            ^ signal.sequence() * 0x9E3779B97F4A7C15L
                            ^ instruction.rendererId().hashCode())));
        }
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null) { clear(); return; }
        ClientCirclePreviews.tick(client);
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

    private static void clear() {
        INSTANCES.clear();
        ACTIVE_CUES.forEach(ActiveCue::endBackend);
        ACTIVE_CUES.clear();
        ClientCirclePreviews.clear();
        OptionalPresentationBackend.clear();
    }

    private static void removeInstance(long instanceId) {
        Instance removed = INSTANCES.remove(instanceId);
        if (removed == null) return;
        Iterator<ActiveCue> cues = ACTIVE_CUES.iterator();
        while (cues.hasNext()) {
            ActiveCue cue = cues.next();
            if (cue.instance == removed) {
                cue.endBackend();
                cues.remove();
            }
        }
    }

    private static Path accessibilityPath() {
        Minecraft client = Minecraft.getInstance();
        return client.gameDirectory.toPath().resolve("vector-regnum-accessibility.txt");
    }

    private static void loadAccessibility() {
        try {
            Path path = accessibilityPath();
            if (Files.isRegularFile(path)) {
                accessibility = PresentationAccessibilityCodec.decode(
                        Files.readString(path, StandardCharsets.UTF_8));
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            accessibility = PresentationAccessibility.DEFAULT;
        }
    }

    private static void saveAccessibility() {
        try {
            Files.writeString(accessibilityPath(),
                    PresentationAccessibilityCodec.encode(accessibility),
                    StandardCharsets.UTF_8);
        } catch (RuntimeException | java.io.IOException ignored) {
            // Preferences are optional; a failed write cannot affect gameplay.
        }
    }

    private static void renderHud(GuiGraphics context, DeltaTracker ignored) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null) return;
        int width = context.guiWidth();
        int height = context.guiHeight();
        double darkness = 0, ice = 0, fault = 0;
        for (ActiveCue cue : ACTIVE_CUES) {
            if (!cue.lod.screenLayers() || cue.age <= cue.instruction.startOffsetTicks()) continue;
            double envelope = cue.envelope();
            switch (cue.instruction.cueKind()) {
                case DARKNESS -> darkness = Math.max(darkness, envelope);
                case FOG -> ice = Math.max(ice, envelope);
                case SCREEN -> fault = Math.max(fault, envelope);
                default -> { }
            }
        }
        darkness *= accessibility.darknessAndFog();
        ice *= accessibility.darknessAndFog();
        fault *= accessibility.flashIntensity();
        if (darkness > 0) context.fill(0, 0, width, height,
                alpha(darkness * .22) << 24 | 0x110b18);
        if (ice > 0) {
            int color = alpha(ice * .16) << 24 | 0xb9e8ef;
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
        private final Vec3 origin;
        private final Vec3 direction;
        private long lastSequence = -1;
        private int terminalAge = -1;

        private Instance(PresentationProgram program, Vec3 origin, Vec3 direction) {
            this.program = program; this.origin = origin; this.direction = direction;
        }
    }

    private static final class ActiveCue {
        private final long cueId;
        private final Instance instance;
        private final PresentationInstruction instruction;
        private final Vec3 point;
        private final PresentationLod lod;
        private final SplittableRandom random;
        private final PresentationCueContext context;
        private int age;
        private boolean soundPlayed;
        private boolean backendStarted;

        private ActiveCue(long cueId, Instance instance, PresentationInstruction instruction,
                Vec3 point, PresentationLod lod, SplittableRandom random) {
            this.cueId = cueId;
            this.instance = instance; this.instruction = instruction;
            this.point = point; this.lod = lod; this.random = random;
            this.context = PresentationCueContext.create(cueId, instruction,
                    instance.origin, instance.direction, point, lod,
                    instance.program.deterministicSeed());
        }

        private boolean tick(Minecraft client) {
            int offset = instruction.startOffsetTicks();
            int duration = instruction.durationTicks();
            int currentAge = age++;
            if (currentAge < offset) return true;
            int localAge = currentAge - offset;
            if (localAge >= duration) {
                endBackend();
                return false;
            }
            if (!backendStarted) {
                backendStarted = true;
                OptionalPresentationBackend.cueStarted(context, accessibility);
            }
            OptionalPresentationBackend.cueTick(context, accessibility,
                    localAge, duration, envelope(localAge));
            if (instruction.cueKind() == PresentationCueKind.SPATIAL_SOUND) {
                if (!soundPlayed) { soundPlayed = true; playSound(client); }
                return true;
            }
            int interval = instruction.truthLayer() ? 2 : 3;
            if (localAge % interval != 0) return true;
            render(client, localAge, duration);
            return true;
        }

        private void render(Minecraft client, int localAge, int duration) {
            if (instruction.cueKind() == PresentationCueKind.SCREEN
                    || instruction.cueKind() == PresentationCueKind.DARKNESS
                    || instruction.cueKind() == PresentationCueKind.FOG
                    || instruction.cueKind() == PresentationCueKind.CAMERA) return;
            double density = accessibility.effectiveParticleDensity() * lod.density();
            if (client.options.particles().get() == ParticleStatus.MINIMAL) density *= .4;
            int count = instruction.truthLayer() ? Math.max(1, (int) Math.ceil(3 * density))
                    : (int) Math.floor(3 * density);
            if (count <= 0) return;
            PresentationElement element = PresentationElement.fromParameter(parameter("element", 0));
            PresentationParticleStyle style = style();
            ParticleOptions primary = particle(style, element);
            if (instruction.truthLayer() && OptionalPresentationBackend.veilActive()
                    && !VanillaParticleAllowlist.mayEmit(primary)) {
                // Active Veil owns expressive particles, but truth must remain visible at
                // minimal LOD and in photosensitive/reduced-motion modes.
                primary = ParticleTypes.ENCHANT;
            }
            if (accessibility.reducedMotion() && !instruction.truthLayer()) return;
            double radius = Math.clamp(parameter("radius", 2), .5, 32);
            double progress = (localAge + 1.0) / duration;
            String renderer = instruction.rendererId();
            if (renderer.contains("invocation_circle") || renderer.contains("selection_boundary")
                    || renderer.contains("barrier") || renderer.contains("aura")
                    || renderer.contains("trace/ring")) {
                double shownRadius = renderer.contains("invocation") ? .75 : radius;
                Vec3 right = axisParameter("axis_r", null);
                Vec3 up = axisParameter("axis_u", right == null ? new Vec3(0, 0, 1) : null);
                ring(client, point.add(0, -.35, 0), shownRadius * Math.min(1, progress * 2),
                        primary, Math.max(8, count * 6), right, up);
            } else if (renderer.contains("raycast") || renderer.contains("projectile")
                    || renderer.contains("vector_motion") || renderer.contains("redstone_pulse")
                    || renderer.contains("trace/beam")) {
                double length = parameter("length", Math.max(2, radius * 2));
                line(client, instance.origin, instance.origin.add(instance.direction.scale(length)),
                        primary, Math.max(4, count * 3));
            } else if (renderer.contains("fractured")) {
                for (int index = 0; index < count * 2; index++) {
                    Vec3 offset = randomOffset(.7 + progress);
                    add(client, ParticleTypes.LARGE_SMOKE, point.add(offset), 0, .01, 0);
                }
            } else if (renderer.contains("trace/burst")) {
                int burstCount = Math.clamp((int) parameter("count", count), 1, 24);
                for (int index = 0; index < burstCount; index++) {
                    Vec3 offset = randomOffset(Math.min(radius, .4 + radius * progress));
                    add(client, primary, point.add(offset),
                            -offset.x * .06, .02 + random.nextDouble() * .04,
                            -offset.z * .06);
                }
            } else {
                for (int index = 0; index < count; index++) {
                    Vec3 offset = randomOffset(Math.min(radius, .4 + radius * progress));
                    ParticleOptions effect = renderer.contains("dust")
                            || renderer.contains("material") ? ParticleTypes.CLOUD : primary;
                    add(client, effect, point.add(offset), -offset.x * .015,
                            .005 + random.nextDouble() * .015, -offset.z * .015);
                }
            }
            if (instruction.cueKind() == PresentationCueKind.LIGHT && localAge % 4 == 0) {
                add(client, ParticleTypes.END_ROD, point, 0, .01, 0);
            }
        }

        private void playSound(Minecraft client) {
            if (accessibility.audioIntensity() <= 0 || client.level == null) return;
            PresentationElement element = PresentationElement.fromParameter(parameter("element", 0));
            SoundEvent sound = instruction.rendererId().contains("fault")
                    ? SoundEvents.AMETHYST_BLOCK_BREAK
                    : instruction.rendererId().contains("tail")
                            ? SoundEvents.AMETHYST_BLOCK_RESONATE
                            : switch (element) {
                                case FIRE -> SoundEvents.BLAZE_SHOOT;
                                case ICE -> SoundEvents.GLASS_HIT;
                                case VOID -> SoundEvents.ENDERMAN_TELEPORT;
                                case ARCANE -> SoundEvents.AMETHYST_BLOCK_CHIME;
                                case WATER -> SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT;
                                case AIR -> SoundEvents.ELYTRA_FLYING;
                                case EARTH -> SoundEvents.STONE_PLACE;
                                case LIGHTNING -> SoundEvents.LIGHTNING_BOLT_THUNDER;
                                case TIME -> SoundEvents.AMETHYST_BLOCK_RESONATE;
                                case SPACE -> SoundEvents.ENDERMAN_TELEPORT;
                                case LIGHT -> SoundEvents.BEACON_AMBIENT;
                                case DARK -> SoundEvents.SCULK_SENSOR_HIT;
                                case NATURE -> SoundEvents.AZALEA_LEAVES_BREAK;
                                case SOUND -> SoundEvents.NOTE_BLOCK_CHIME.value();
                            };
            client.level.playSound(null, point.x, point.y, point.z, sound,
                    SoundSource.PLAYERS,
                    (float) (instruction.intensity() * accessibility.audioIntensity()),
                    (float) (0.9 + random.nextDouble() * .2));
        }

        private double parameter(String key, double fallback) {
            return instruction.parameters().getOrDefault(key, fallback);
        }

        private double envelope() {
            return envelope(age - instruction.startOffsetTicks() - 1);
        }

        private double envelope(int localAge) {
            if (localAge < 0 || localAge >= instruction.durationTicks()) return 0;
            double progress = localAge / (double) instruction.durationTicks();
            double fade = Math.min(1.0, Math.min(progress * 4.0, (1.0 - progress) * 4.0));
            return instruction.intensity() * Math.max(0, fade);
        }

        private void endBackend() {
            if (!backendStarted) return;
            backendStarted = false;
            OptionalPresentationBackend.cueEnded(cueId);
        }

        private Vec3 randomOffset(double scale) {
            return new Vec3(random.nextDouble(-scale, scale),
                    random.nextDouble(-scale * .35, scale * .55),
                    random.nextDouble(-scale, scale));
        }

        private PresentationParticleStyle style() {
            int ordinal = (int) Math.round(parameter("style",
                    PresentationParticleStyle.MOTES.ordinal()));
            if (!PresentationParticleStyle.isValidOrdinal(ordinal)) {
                return PresentationParticleStyle.MOTES;
            }
            return PresentationParticleStyle.values()[ordinal];
        }

        private Vec3 axisParameter(String prefix, Vec3 fallback) {
            Double x = instruction.parameters().get(prefix + "x");
            Double y = instruction.parameters().get(prefix + "y");
            Double z = instruction.parameters().get(prefix + "z");
            if (x == null || y == null || z == null) return fallback;
            Vec3 axis = new Vec3(x, y, z);
            return axis.lengthSqr() < 1.0e-8 ? fallback : axis.normalize();
        }

        private static ParticleOptions particle(PresentationParticleStyle style,
                PresentationElement element) {
            return switch (style) {
                case MOTES -> switch (element) {
                    case FIRE -> ParticleTypes.FLAME;
                    case ICE -> ParticleTypes.SNOWFLAKE;
                    case VOID -> ParticleTypes.PORTAL;
                    case ARCANE -> ParticleTypes.ENCHANT;
                    case WATER -> ParticleTypes.FALLING_WATER;
                    case AIR -> ParticleTypes.CLOUD;
                    case EARTH -> ParticleTypes.POOF;
                    case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
                    case TIME -> ParticleTypes.END_ROD;
                    case SPACE -> ParticleTypes.PORTAL;
                    case LIGHT -> ParticleTypes.END_ROD;
                    case DARK -> ParticleTypes.SMOKE;
                    case NATURE -> ParticleTypes.COMPOSTER;
                    case SOUND -> ParticleTypes.NOTE;
                };
                case CLOUD -> ParticleTypes.CLOUD;
                case SMOKE -> ParticleTypes.SMOKE;
                case LARGE_SMOKE -> ParticleTypes.LARGE_SMOKE;
                case SPARK -> ParticleTypes.ELECTRIC_SPARK;
                case END_ROD -> ParticleTypes.END_ROD;
                case TOTEM -> ParticleTypes.TOTEM_OF_UNDYING;
                case WITCH -> ParticleTypes.WITCH;
                case EXPLOSION -> ParticleTypes.EXPLOSION;
                case EXPLOSION_EMITTER -> ParticleTypes.EXPLOSION_EMITTER;
            };
        }

        private static void ring(Minecraft client, Vec3 center, double radius,
                ParticleOptions particle, int points, Vec3 right, Vec3 up) {
            Vec3 planeRight = right == null ? new Vec3(1, 0, 0) : right;
            Vec3 planeUp = up == null ? new Vec3(0, 0, 1) : up;
            for (int index = 0; index < points; index++) {
                double angle = Math.PI * 2 * index / points;
                add(client, particle, center.add(planeRight.scale(Math.cos(angle) * radius))
                        .add(planeUp.scale(Math.sin(angle) * radius)), 0, .004, 0);
            }
        }

        private static void line(Minecraft client, Vec3 start, Vec3 end,
                ParticleOptions particle, int points) {
            for (int index = 0; index <= points; index++) {
                add(client, particle, start.lerp(end, index / (double) points), 0, 0, 0);
            }
        }

        private static void add(Minecraft client, ParticleOptions particle, Vec3 point,
                double velocityX, double velocityY, double velocityZ) {
            // Sole sanctioned built-in emission choke point. Under an active Veil
            // backend only the enchanting-table truth cue may pass; every other
            // family is owned by Quasar motifs in the backend.
            if (!VanillaParticleAllowlist.mayEmit(particle)) return;
            if (client.level != null) client.level.addParticle(particle, point.x, point.y, point.z,
                    velocityX, velocityY, velocityZ);
        }
    }
}
