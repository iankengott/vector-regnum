package vectorregnum.neoforge;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import vectorregnum.core.circle.CircleDiagnostic;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.presentation.ExecutionEvent;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/** Turns deterministic core effect commands into authoritative world changes and particle traces. */
public final class SpellVisualManager {
    static final int DEV_SHOWCASE_DURATION_TICKS = 300;
    private static final List<ActiveVisual> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private SpellVisualManager() {
    }

    public static void initialize() {
        initialized = true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
        initialized = false;
    }

    public static void apply(ServerPlayer caster, EffectCommand command) {
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
        ACTIVE.add(new CircleVisual(player));
    }

    /** Resolves only bounded, mechanics-derived VM events into cosmetic particles. */
    public static void acceptVmEvent(ServerPlayer player, ExecutionEvent event) {
        if (player == null || player.isRemoved() || event == null) {
            return;
        }
        ServerLevel world = player.serverLevel();
        Vec3 point = player.getEyePosition();
        switch (event) {
            case ExecutionEvent.Started ignored -> world.sendParticles(ParticleTypes.ENCHANT,
                    point.x, point.y, point.z, 3, 0.12, 0.12, 0.12, 0.03);
            case ExecutionEvent.DelayStarted delay -> world.sendParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, Math.min(6, delay.delayTicks()),
                    0.16, 0.16, 0.16, 0.01);
            case ExecutionEvent.WorldEffectEmitted ignored -> world.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                    5, 0.2, 0.2, 0.2, 0.03);
            case ExecutionEvent.Halted ignored -> world.sendParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 4, 0.18, 0.18, 0.18, 0.02);
            case ExecutionEvent.Faulted ignored -> world.sendParticles(ParticleTypes.SMOKE,
                    point.x, point.y, point.z, 8, 0.22, 0.25, 0.22, 0.03);
            default -> { }
        }
    }

    public static void showAuthoredCircle(
            ServerPlayer player, MagicCircle circle, List<CircleDiagnostic> diagnostics) {
        ACTIVE.removeIf(visual -> visual instanceof AuthoredCircleVisual authored
                && authored.player.getUUID().equals(player.getUUID()));
        ACTIVE.add(new AuthoredCircleVisual(player, circle, diagnostics));
    }

    public static void showAuthoredCircleAt(ServerPlayer player, MagicCircle circle,
            List<CircleDiagnostic> diagnostics, Vec3 center) {
        ACTIVE.removeIf(visual -> visual instanceof AuthoredCircleVisual authored
                && authored.player.getUUID().equals(player.getUUID()));
        ACTIVE.add(new AuthoredCircleVisual(player, circle, diagnostics, center));
    }

    /** Draws a fixed editor preview parallel to a server-captured block face. */
    public static void showAuthoredCircleOnFace(ServerPlayer player, MagicCircle circle,
            List<CircleDiagnostic> diagnostics, Vec3 center, Direction face) {
        ACTIVE.removeIf(visual -> visual instanceof AuthoredCircleVisual authored
                && authored.player.getUUID().equals(player.getUUID()));
        ACTIVE.add(new AuthoredCircleVisual(player, circle, diagnostics, center, face));
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

    private interface ActiveVisual {
        boolean tick();
    }

    private static boolean casterAvailable(ServerPlayer caster, ServerLevel world) {
        return caster != null && !caster.isRemoved() && caster.isAlive()
                && caster.serverLevel() == world
                && caster.getServer().getPlayerList().getPlayer(caster.getUUID()) == caster
                && world.isLoaded(caster.blockPosition());
    }

    private static final class ProjectileVisual implements ActiveVisual {
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
                world.sendParticles(ParticleTypes.SMOKE,
                        hitPosition.x, hitPosition.y, hitPosition.z,
                        8, 0.2, 0.2, 0.2, 0.02);
                return false;
            }
            ParticleOptions primary = element == Element.FROST
                    ? ParticleTypes.SNOWFLAKE
                    : element == Element.VOID ? ParticleTypes.DRAGON_BREATH : ParticleTypes.FLAME;
            world.sendParticles(primary, position.x, position.y, position.z,
                    7, radius * 0.18, radius * 0.18, radius * 0.18, 0.02);
            world.sendParticles(ParticleTypes.END_ROD, position.x, position.y, position.z,
                    2, 0.08, 0.08, 0.08, 0.0);

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
                } else if (element == Element.FROST) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                }
                world.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z,
                        3, 0.2, 0.2, 0.2, 0.0);
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
        private int age;

        private AuraVisual(ServerPlayer caster, EffectCommand.Aura command) {
            this.caster = caster;
            this.world = caster.serverLevel();
            this.center = toMinecraft(command.origin()).add(0.0, -1.35, 0.0);
            this.element = command.element().orElse(Element.ARCANE);
            this.targetRadius = Math.max(1.0, Math.min(command.radius(), 12.0));
            this.magnitude = command.magnitude();
        }

        @Override
        public boolean tick() {
            if (age++ >= 80 || !casterAvailable(caster, world)) {
                return false;
            }
            double radius = Math.min(targetRadius, 0.35 + age * 0.16);
            ParticleOptions particle = element == Element.FROST
                    ? ParticleTypes.SNOWFLAKE
                    : element == Element.FIRE ? ParticleTypes.FLAME : ParticleTypes.ENCHANT;
            if (age % 2 != 0) {
                return true;
            }
            int points = Math.max(16, (int) Math.ceil(radius * 6.0));
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                world.sendParticles(particle, x, center.y + 0.15, z,
                        1, 0.02, 0.08, 0.02, 0.0);
            }
            if (age == 32) {
                List<LivingEntity> targets = world.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(center, center).inflate(targetRadius, 2.5, targetRadius),
                        entity -> entity != caster
                                && entity.isAlive()
                                && SpellSecurityPolicy.canAffectEntity(caster, entity)
                                && entity.distanceToSqr(center) <= targetRadius * targetRadius);
                for (LivingEntity target : targets) {
                    if (element == Element.FROST) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 2));
                    }
                    target.hurt(world.damageSources().magic(), (float) Math.min(20.0, 2.0 * magnitude));
                }
            }
            return true;
        }
    }

    private static final class WildMagicVisual implements ActiveVisual {
        private final ServerPlayer caster;
        private final ServerLevel world;
        private final EffectCommand.WildMagic command;
        private final SplittableRandom random;
        private final Vec3 origin;
        private final Vec3 violentDirection;
        private int age;

        private WildMagicVisual(ServerPlayer caster, EffectCommand.WildMagic command) {
            this.caster = caster;
            this.world = caster.serverLevel();
            this.command = command;
            this.random = new SplittableRandom(command.variationSeed());
            this.origin = command.origin().map(SpellVisualManager::toMinecraft).orElse(caster.getEyePosition());
            this.violentDirection = randomDirection(random);
        }

        @Override
        public boolean tick() {
            if (age++ >= 50 || !casterAvailable(caster, world)) {
                return false;
            }
            if (age == 1 && command.category() == WildMagicCategory.INTERNAL_MANA_DETONATION) {
                caster.hurt(world.damageSources().magic(), 4.0F);
                world.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        origin.x, origin.y, origin.z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            switch (command.category()) {
                case INTERNAL_MANA_DETONATION -> world.sendParticles(
                        ParticleTypes.TOTEM_OF_UNDYING,
                        caster.getX(), caster.getY() + caster.getBbHeight() * 0.6, caster.getZ(),
                        18, 0.5, 0.8, 0.5, 0.2);
                case UNSTRUCTURED_ELEMENT_BURST -> {
                    for (int i = 0; i < 16; i++) {
                        Vec3 offset = randomDirection(random).scale(0.4 + random.nextDouble() * 3.5);
                        Vec3 position = origin.add(offset);
                        world.sendParticles(ParticleTypes.WITCH,
                                position.x, position.y, position.z,
                                1, 0.04, 0.04, 0.04, 0.05);
                    }
                }
                case VIOLENT_MISCAST -> {
                    Vec3 position = origin.add(violentDirection.scale(age * 0.48));
                    world.sendParticles(ParticleTypes.LARGE_SMOKE,
                            position.x, position.y, position.z,
                            6, 0.25, 0.25, 0.25, 0.04);
                    world.sendParticles(ParticleTypes.FLAME,
                            position.x, position.y, position.z,
                            4, 0.15, 0.15, 0.15, 0.05);
                }
            }
            return true;
        }
    }

    private static final class CircleVisual implements ActiveVisual {
        private final ServerPlayer player;
        private final ServerLevel world;
        private final Vec3 center;
        private final Vec3 right;
        private final Vec3 up;
        private int age;

        private CircleVisual(ServerPlayer player) {
            this.player = player;
            this.world = player.serverLevel();
            Vec3 forward = player.getViewVector(1.0F).normalize();
            Vec3 horizontal = forward.cross(new Vec3(0.0, 1.0, 0.0));
            this.right = horizontal.lengthSqr() < 1.0e-6
                    ? new Vec3(1.0, 0.0, 0.0)
                    : horizontal.normalize();
            this.up = right.cross(forward).normalize();
            this.center = player.getEyePosition().add(forward.scale(5.0));
        }

        @Override
        public boolean tick() {
            if (age++ >= DEV_SHOWCASE_DURATION_TICKS || !casterAvailable(player, world)) {
                return false;
            }
            if (age % 4 == 0) {
                drawRing(world, center, right, up, 2.25, 32, ParticleTypes.END_ROD);
                drawRing(world, center, right, up, 1.45, 20, ParticleTypes.ENCHANT);
            }
            if (age % 8 == 0) {
                drawStar(world, center, right, up, 1.85);
            }
            return true;
        }
    }

    /** Draws the actual authored ring/slot topology and animates compiler order. */
    private static final class AuthoredCircleVisual implements ActiveVisual {
        private static final int DURATION_TICKS = DEV_SHOWCASE_DURATION_TICKS;
        private final ServerPlayer player;
        private final ServerLevel world;
        private final MagicCircle circle;
        private final Set<vectorregnum.core.circle.CircleCoordinate> errors;
        private final Vec3 center;
        private final Vec3 right;
        private final Vec3 up;
        private int age;

        private AuthoredCircleVisual(
                ServerPlayer player, MagicCircle circle, List<CircleDiagnostic> diagnostics) {
            this(player, circle, diagnostics, null, null);
        }

        private AuthoredCircleVisual(ServerPlayer player, MagicCircle circle,
                List<CircleDiagnostic> diagnostics, Vec3 anchoredCenter) {
            this(player, circle, diagnostics, anchoredCenter, null);
        }

        private AuthoredCircleVisual(ServerPlayer player, MagicCircle circle,
                List<CircleDiagnostic> diagnostics, Vec3 anchoredCenter, Direction anchorFace) {
            this.player = player;
            this.world = player.serverLevel();
            this.circle = circle;
            this.errors = diagnostics.stream()
                    .filter(diagnostic -> diagnostic.severity() == CircleDiagnostic.Severity.ERROR)
                    .flatMap(diagnostic -> diagnostic.location().stream())
                    .collect(Collectors.toUnmodifiableSet());
            Vec3 forward;
            if (anchorFace == null) {
                forward = player.getViewVector(1.0F).normalize();
                Vec3 horizontal = forward.cross(new Vec3(0.0, 1.0, 0.0));
                this.right = horizontal.lengthSqr() < 1.0e-6
                        ? new Vec3(1.0, 0.0, 0.0) : horizontal.normalize();
                this.up = right.cross(forward).normalize();
            } else {
                forward = new Vec3(anchorFace.getStepX(), anchorFace.getStepY(),
                        anchorFace.getStepZ());
                Vec3 referenceUp = Math.abs(forward.y) > 0.9
                        ? new Vec3(0.0, 0.0, 1.0) : new Vec3(0.0, 1.0, 0.0);
                this.right = referenceUp.cross(forward).normalize();
                this.up = forward.cross(right).normalize();
            }
            this.center = anchoredCenter == null
                    ? player.getEyePosition().add(forward.scale(5.0)) : anchoredCenter;
        }

        @Override
        public boolean tick() {
            if (age++ >= DURATION_TICKS || !casterAvailable(player, world)) {
                return false;
            }
            if (age % 4 == 0) {
                for (int ring = 0; ring < circle.ringCount(); ring++) {
                    double radius = radius(ring);
                    drawRing(world, center, right, up, radius,
                            Math.max(18, circle.slotsPerRing() * 3),
                            ring == 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT);
                }
            }
            List<PlacedSigil> ordered = circle.executionOrder().stream().limit(128).toList();
            for (int index = 0; index < ordered.size(); index++) {
                PlacedSigil sigil = ordered.get(index);
                Vec3 position = position(sigil);
                boolean active = index == Math.min(ordered.size() - 1, age / 12);
                ParticleOptions particle = errors.contains(sigil.coordinate())
                        ? ParticleTypes.LARGE_SMOKE : particleFor(sigil.type());
                world.sendParticles(particle, position.x, position.y, position.z,
                        active ? 8 : 1, active ? 0.13 : 0.02, active ? 0.13 : 0.02,
                        active ? 0.13 : 0.02, active ? 0.025 : 0.0);
                if (active) {
                    drawLine(world, center, position, ParticleTypes.WITCH);
                }
            }
            return true;
        }

        private double radius(int ring) {
            double step = 2.2 / Math.max(1, circle.ringCount());
            return 2.6 - ring * step;
        }

        private Vec3 position(PlacedSigil sigil) {
            double angle = Math.PI * 2.0 * sigil.coordinate().clockwiseSlot()
                    / circle.slotsPerRing();
            double radius = radius(sigil.coordinate().ring());
            return center.add(right.scale(Math.sin(angle) * radius))
                    .add(up.scale(Math.cos(angle) * radius));
        }

        private static ParticleOptions particleFor(String type) {
            if (type.contains("FIRE")) return ParticleTypes.FLAME;
            if (type.contains("FROST")) return ParticleTypes.SNOWFLAKE;
            if (type.contains("VOID")) return ParticleTypes.DRAGON_BREATH;
            if (type.equals("EXECUTE")) return ParticleTypes.TOTEM_OF_UNDYING;
            if (type.startsWith("SHAPE_")) return ParticleTypes.ELECTRIC_SPARK;
            return ParticleTypes.END_ROD;
        }
    }

    private static void drawRing(
            ServerLevel world,
            Vec3 center,
            Vec3 right,
            Vec3 up,
            double radius,
            int points,
            ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            Vec3 position = center
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
            world.sendParticles(particle, position.x, position.y, position.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawStar(ServerLevel world, Vec3 center, Vec3 right, Vec3 up, double radius) {
        Vec3[] points = new Vec3[5];
        for (int i = 0; i < points.length; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / points.length;
            points[i] = center
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
        }
        for (int i = 0; i < points.length; i++) {
            Vec3 start = points[i];
            Vec3 end = points[(i + 2) % points.length];
            for (int step = 0; step <= 6; step++) {
                double t = step / 6.0;
                Vec3 position = start.scale(1.0 - t).add(end.scale(t));
                world.sendParticles(ParticleTypes.FLAME,
                        position.x, position.y, position.z,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void drawLine(
            ServerLevel world, Vec3 start, Vec3 end, ParticleOptions particle) {
        for (int step = 0; step <= 10; step++) {
            double amount = step / 10.0;
            Vec3 position = start.scale(1.0 - amount).add(end.scale(amount));
            world.sendParticles(particle, position.x, position.y, position.z,
                    1, 0.0, 0.0, 0.0, 0.0);
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
