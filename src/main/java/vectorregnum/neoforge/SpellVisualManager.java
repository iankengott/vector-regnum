package vectorregnum.neoforge;

import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.Element;
import vectorregnum.core.WildMagicCategory;
import vectorregnum.core.security.WildMagicEnvelope;
import vectorregnum.core.circle.CircleDiagnostic;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;
import vectorregnum.neoforge.presentation.CirclePreviewPayload;
import vectorregnum.neoforge.presentation.ServerTraces;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/**
 * Turns deterministic core effect commands into authoritative world changes and
 * compact trace events. This manager never spawns vanilla particles: every
 * visual is a bounded payload that each receiving client renders through its
 * own presentation backend (Quasar motifs under Veil, guarded built-in
 * fallback otherwise).
 */
public final class SpellVisualManager {
    static final int DEV_SHOWCASE_DURATION_TICKS = 300;
    static final int MAX_ACTIVE_VISUALS = 96;
    private static final int MAX_PREVIEW_SIGILS = CirclePreviewPayload.MAX_SIGILS;
    private static final List<ActiveVisual> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private SpellVisualManager() {
    }

    public static void initialize() {
        initialized = true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerTraces.tickBudget(server.overworld().getGameTime());
        tick(server);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
        initialized = false;
    }

    public static void apply(ServerPlayer caster, EffectCommand command) {
        if (caster == null || command == null || ACTIVE.size() >= MAX_ACTIVE_VISUALS) return;
        switch (command) {
            case EffectCommand.Projectile projectile -> {
                ACTIVE.add(new ProjectileVisual(caster, projectile));
                caster.playSound(SoundEvents.BLAZE_SHOOT, 0.8F, 1.15F);
            }
            case EffectCommand.Aura aura -> {
                ACTIVE.add(new AuraVisual(caster, aura));
                caster.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
            }
            case EffectCommand.WildMagic wildMagic -> {
                ACTIVE.add(new WildMagicVisual(caster, wildMagic));
                caster.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.65F);
            }
        }
    }

    public static void startShowcase(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 forward = player.getViewVector(1.0F).normalize();
        Vec3 horizontal = forward.cross(new Vec3(0.0, 1.0, 0.0));
        Vec3 right = horizontal.lengthSqr() < 1.0e-6
                ? new Vec3(1.0, 0.0, 0.0) : horizontal.normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 center = player.getEyePosition().add(forward.scale(5.0));
        ServerTraces.circlePreview(world, new CirclePreviewPayload(
                ServerTraces.nextInstanceId(), true,
                center.x, center.y, center.z, right.x, right.y, right.z,
                up.x, up.y, up.z,
                List.of(2.25F, 1.45F), 32, List.of(),
                DEV_SHOWCASE_DURATION_TICKS, world.getGameTime()));
    }

    public static void showAuthoredCircle(
            ServerPlayer player, MagicCircle circle, List<CircleDiagnostic> diagnostics) {
        broadcastPreview(player, circle, diagnostics, null, null);
    }

    public static void showAuthoredCircleAt(ServerPlayer player, MagicCircle circle,
            List<CircleDiagnostic> diagnostics, Vec3 center) {
        broadcastPreview(player, circle, diagnostics, center, null);
    }

    /** Draws a fixed editor preview parallel to a server-captured block face. */
    public static void showAuthoredCircleOnFace(ServerPlayer player, MagicCircle circle,
            List<CircleDiagnostic> diagnostics, Vec3 center, Direction face) {
        broadcastPreview(player, circle, diagnostics, center, face);
    }

    private static void tick(MinecraftServer server) {
        Iterator<ActiveVisual> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActiveVisual visual = iterator.next();
            try {
                if (!visual.tick()) {
                    iterator.remove();
                }
            } catch (RuntimeException exception) {
                VectorRegnumMod.LOGGER.error("Visual effect failed and was removed", exception);
                iterator.remove();
            }
        }
    }

    /** Broadcasts one verified circle descriptor; the client animates it locally. */
    private static void broadcastPreview(ServerPlayer player, MagicCircle circle,
            List<CircleDiagnostic> diagnostics, Vec3 anchoredCenter, Direction anchorFace) {
        ServerLevel world = player.serverLevel();
        Vec3 forward;
        Vec3 right;
        Vec3 up;
        if (anchorFace == null) {
            forward = player.getViewVector(1.0F).normalize();
            Vec3 horizontal = forward.cross(new Vec3(0.0, 1.0, 0.0));
            right = horizontal.lengthSqr() < 1.0e-6
                    ? new Vec3(1.0, 0.0, 0.0) : horizontal.normalize();
            up = right.cross(forward).normalize();
        } else {
            forward = new Vec3(anchorFace.getStepX(), anchorFace.getStepY(),
                    anchorFace.getStepZ());
            Vec3 referenceUp = Math.abs(forward.y) > 0.9
                    ? new Vec3(0.0, 0.0, 1.0) : new Vec3(0.0, 1.0, 0.0);
            right = referenceUp.cross(forward).normalize();
            up = forward.cross(right).normalize();
        }
        Vec3 center = anchoredCenter == null
                ? player.getEyePosition().add(forward.scale(5.0)) : anchoredCenter;
        Set<vectorregnum.core.circle.CircleCoordinate> errors = diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == CircleDiagnostic.Severity.ERROR)
                .flatMap(diagnostic -> diagnostic.location().stream())
                .collect(Collectors.toUnmodifiableSet());
        int ringCount = Math.min(circle.ringCount(), CirclePreviewPayload.MAX_RINGS);
        List<Float> radii = new ArrayList<>(ringCount);
        for (int ring = 0; ring < ringCount; ring++) {
            radii.add((float) radius(ring, circle));
        }
        List<CirclePreviewPayload.SigilDot> sigils = new ArrayList<>(MAX_PREVIEW_SIGILS);
        for (PlacedSigil placed : circle.executionOrder()) {
            if (sigils.size() >= MAX_PREVIEW_SIGILS) break;
            int coordinateRing = placed.coordinate().ring();
            if (coordinateRing >= ringCount) continue;
            int visual = errors.contains(placed.coordinate())
                    ? CirclePreviewPayload.VISUAL_FAULT : visualFor(placed.type());
            sigils.add(new CirclePreviewPayload.SigilDot(coordinateRing,
                    placed.coordinate().clockwiseSlot(), visual));
        }
        ServerTraces.circlePreview(world, new CirclePreviewPayload(
                ServerTraces.nextInstanceId(), false,
                center.x, center.y, center.z, right.x, right.y, right.z, up.x, up.y, up.z,
                radii, Math.min(circle.slotsPerRing(), CirclePreviewPayload.MAX_SLOTS_PER_RING),
                sigils, DEV_SHOWCASE_DURATION_TICKS, world.getGameTime()));
    }

    private static int visualFor(String type) {
        if (type.startsWith("ELEMENT_")) {
            return Element.fromId(type.substring("ELEMENT_".length()))
                    .map(SpellVisualManager::visualForElement)
                    .orElse(CirclePreviewPayload.VISUAL_DEFAULT);
        }
        if (type.equals("EXECUTE")) return CirclePreviewPayload.VISUAL_EXECUTE;
        if (type.startsWith("SHAPE_")) return CirclePreviewPayload.VISUAL_SHAPE;
        return CirclePreviewPayload.VISUAL_DEFAULT;
    }

    private static int visualForElement(Element element) {
        return switch (element) {
            case FIRE -> CirclePreviewPayload.VISUAL_FIRE;
            case ICE -> CirclePreviewPayload.VISUAL_ICE;
            case VOID -> CirclePreviewPayload.VISUAL_VOID;
            case WATER -> CirclePreviewPayload.VISUAL_WATER;
            case AIR -> CirclePreviewPayload.VISUAL_AIR;
            case EARTH -> CirclePreviewPayload.VISUAL_EARTH;
            case LIGHTNING -> CirclePreviewPayload.VISUAL_LIGHTNING;
            case TIME -> CirclePreviewPayload.VISUAL_TIME;
            case SPACE -> CirclePreviewPayload.VISUAL_SPACE;
            case LIGHT -> CirclePreviewPayload.VISUAL_LIGHT;
            case DARK -> CirclePreviewPayload.VISUAL_DARK;
            case NATURE -> CirclePreviewPayload.VISUAL_NATURE;
            case SOUND -> CirclePreviewPayload.VISUAL_SOUND;
            case ARCANE -> CirclePreviewPayload.VISUAL_DEFAULT;
        };
    }

    private static double radius(int ring, MagicCircle circle) {
        double step = 2.2 / Math.max(1, circle.ringCount());
        return 2.6 - ring * step;
    }

    private interface ActiveVisual {
        boolean tick();
    }

    private static boolean casterAvailable(ServerPlayer caster, ServerLevel world) {
        return caster != null && !caster.isRemoved() && caster.isAlive()
                && caster.serverLevel() == world
                && caster.getServer().getPlayerList().getPlayer(caster.getUUID()) == caster
                && world.isLoaded(caster.blockPosition());
    }

    private static PresentationElement trailElement(Element element) {
        return switch (element) {
            case FIRE -> PresentationElement.FIRE;
            case ICE -> PresentationElement.ICE;
            case VOID -> PresentationElement.VOID;
            case ARCANE -> PresentationElement.ARCANE;
            case WATER -> PresentationElement.WATER;
            case AIR -> PresentationElement.AIR;
            case EARTH -> PresentationElement.EARTH;
            case LIGHTNING -> PresentationElement.LIGHTNING;
            case TIME -> PresentationElement.TIME;
            case SPACE -> PresentationElement.SPACE;
            case LIGHT -> PresentationElement.LIGHT;
            case DARK -> PresentationElement.DARK;
            case NATURE -> PresentationElement.NATURE;
            case SOUND -> PresentationElement.SOUND;
        };
    }

    private static final class ProjectileVisual implements ActiveVisual {
        private static final int CADENCE_TICKS = 2;
        private final ServerPlayer caster;
        private final ServerLevel world;
        private final Vec3 origin;
        private final Vec3 direction;
        private final Element element;
        private final double radius;
        private final double magnitude;
        private int age;

        private ProjectileVisual(ServerPlayer caster, EffectCommand.Projectile command) {
            this.caster = caster;
            this.world = caster.serverLevel();
            this.origin = toMinecraft(command.origin());
            this.direction = toMinecraft(command.direction()).normalize();
            this.element = command.element().orElse(Element.ARCANE);
            this.radius = Math.max(0.4, Math.min(command.radius(), 2.5));
            this.magnitude = command.magnitude();
        }

        @Override
        public boolean tick() {
            if (age++ >= 45 || !casterAvailable(caster, world)) {
                return false;
            }
            Vec3 position = origin.add(direction.scale(age * 0.55));
            Vec3 previous = origin.add(direction.scale((age - 1) * 0.55));
            HitResult blockHit = world.clip(new ClipContext(
                    previous,
                    position,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    caster));
            if (blockHit.getType() != HitResult.Type.MISS) {
                Vec3 hitPosition = blockHit.getLocation();
                ServerTraces.burst(world, hitPosition, PresentationParticleStyle.SMOKE,
                        trailElement(element), 0.5F, 0.7F, 12);
                return false;
            }
            if (age % CADENCE_TICKS == 0) {
                ServerTraces.motes(world, position, PresentationParticleStyle.MOTES,
                        trailElement(element), (float) (radius * 0.5), 0.85F, 10);
                ServerTraces.motes(world, position, PresentationParticleStyle.END_ROD,
                        PresentationElement.ARCANE, 0.08F, 0.4F, 8);
            }
            List<LivingEntity> hits = world.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(position, position).inflate(radius),
                    entity -> entity != caster && entity.isAlive()
                            && SpellSecurityPolicy.canAffectEntity(caster, entity));
            if (!hits.isEmpty()) {
                LivingEntity target = hits.getFirst();
                target.hurt(world.damageSources().magic(), (float) Math.min(40.0, 4.0 * magnitude));
                if (element == Element.FIRE) {
                    target.igniteForSeconds((float) Math.min(10.0, 3.0 * magnitude));
                } else if (element == Element.ICE) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                }
                ServerTraces.burst(world, position, PresentationParticleStyle.EXPLOSION,
                        trailElement(element), 0.8F, 1.0F, 10);
                return false;
            }
            return true;
        }
    }

    private static final class AuraVisual implements ActiveVisual {
        private final ServerPlayer caster;
        private final ServerLevel world;
        private final Vec3 center;
        private final Element element;
        private final double targetRadius;
        private final double magnitude;

        private AuraVisual(ServerPlayer caster, EffectCommand.Aura command) {
            this.caster = caster;
            this.world = caster.serverLevel();
            this.center = toMinecraft(command.origin()).add(0.0, -1.35, 0.0);
            this.element = command.element().orElse(Element.ARCANE);
            this.targetRadius = Math.max(1.0, Math.min(command.radius(), 12.0));
            this.magnitude = command.magnitude();
            // One authoritative ring descriptor replaces per-tick particle streaming;
            // clients grow the ring across the aura window themselves.
            ServerTraces.ring(this.world, this.center, (float) this.targetRadius,
                    trailElement(this.element), 80, 0.8F);
        }

        @Override
        public boolean tick() {
            if (!casterAvailable(caster, world)) {
                return false;
            }
            if (++age > 80) {
                return false;
            }
            if (age == 32) {
                applyBurst();
            }
            return true;
        }

        private int age;

        private void applyBurst() {
            List<LivingEntity> targets = world.getEntitiesOfClass(
                    LivingEntity.class,
                    new AABB(center, center).inflate(targetRadius, 2.5, targetRadius),
                    entity -> entity != caster
                            && entity.isAlive()
                            && SpellSecurityPolicy.canAffectEntity(caster, entity)
                            && entity.distanceToSqr(center) <= targetRadius * targetRadius);
            for (LivingEntity target : targets) {
                if (element == Element.ICE) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 2));
                }
                target.hurt(world.damageSources().magic(), (float) Math.min(20.0, 2.0 * magnitude));
            }
        }
    }

    private static final class WildMagicVisual implements ActiveVisual {
        private static final int CADENCE_TICKS = 4;
        private final ServerPlayer caster;
        private final ServerLevel world;
        private final EffectCommand.WildMagic command;
        private final SplittableRandom random;
        private final Vec3 origin;
        private final Vec3 violentDirection;
        private final WildMagicEnvelope envelope;
        private int age;

        private WildMagicVisual(ServerPlayer caster, EffectCommand.WildMagic command) {
            this.caster = caster;
            this.world = caster.serverLevel();
            this.command = command;
            this.envelope = command.envelope();
            this.random = new SplittableRandom(command.variationSeed());
            this.origin = command.origin().map(SpellVisualManager::toMinecraft)
                    .orElse(caster.getEyePosition());
            this.violentDirection = randomDirection(random);
        }

        @Override
        public boolean tick() {
            if (age++ >= envelope.durationTicks() || !casterAvailable(caster, world)) {
                return false;
            }
            if (age == 1 && command.category() == WildMagicCategory.INTERNAL_MANA_DETONATION) {
                caster.hurt(world.damageSources().magic(), (float) envelope.magnitude());
                ServerTraces.burst(world, origin, PresentationParticleStyle.EXPLOSION_EMITTER,
                        PresentationElement.ARCANE, (float) envelope.radius(), 1.0F, 15);
            }
            if (age % CADENCE_TICKS != 0) {
                return true;
            }
            switch (command.category()) {
                case INTERNAL_MANA_DETONATION -> ServerTraces.burst(world,
                        new Vec3(caster.getX(), caster.getY() + caster.getBbHeight() * 0.6,
                                caster.getZ()),
                        PresentationParticleStyle.TOTEM, PresentationElement.ARCANE,
                        1.2F, 0.9F, 8);
                case UNSTRUCTURED_ELEMENT_BURST -> {
                    List<Vec3> points = new ArrayList<>(envelope.targetLimit());
                    for (int i = 0; i < envelope.targetLimit(); i++) {
                        points.add(origin.add(randomDirection(random)
                                .scale(0.4 + random.nextDouble() * envelope.radius())));
                    }
                    ServerTraces.burstAll(world, points, PresentationParticleStyle.WITCH,
                            PresentationElement.VOID, 0.25F, 0.7F, 10);
                }
                case VIOLENT_MISCAST -> {
                    Vec3 tip = origin.add(violentDirection.scale(
                            Math.min(envelope.radius(), age * 0.48)));
                    ServerTraces.beam(world, origin, tip,
                            PresentationParticleStyle.LARGE_SMOKE, PresentationElement.VOID,
                            0.3F, 10, 0.8F);
                    ServerTraces.beam(world, origin, tip,
                            PresentationParticleStyle.MOTES, PresentationElement.FIRE,
                            0.15F, 10, 0.6F);
                }
                case COERCIVE_ATTENTION -> ServerTraces.ring(world,
                        origin.add(0, -.8, 0), (float) envelope.radius(),
                        PresentationElement.ARCANE, 10, .8F);
            }
            return true;
        }
    }

    private static Vec3 randomDirection(SplittableRandom random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            Vec3 vector = new Vec3(
                    random.nextDouble(-1.0, 1.0),
                    random.nextDouble(-0.6, 1.0),
                    random.nextDouble(-1.0, 1.0));
            if (vector.lengthSqr() > 1.0e-6) {
                return vector.normalize();
            }
        }
        return new Vec3(0.0, 1.0, 0.0);
    }

    private static Vec3 toMinecraft(vectorregnum.core.Vec3 vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }
}
