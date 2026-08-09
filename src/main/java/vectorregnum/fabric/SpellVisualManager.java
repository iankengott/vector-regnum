package vectorregnum.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.Element;
import vectorregnum.core.Vec3;
import vectorregnum.core.WildMagicCategory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;

/** Turns deterministic core effect commands into authoritative world changes and particle traces. */
public final class SpellVisualManager {
    static final int DEV_SHOWCASE_DURATION_TICKS = 1200;
    private static final List<ActiveVisual> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private SpellVisualManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(SpellVisualManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
    }

    public static void apply(ServerPlayerEntity caster, EffectCommand command) {
        switch (command) {
            case EffectCommand.Projectile projectile -> {
                ACTIVE.add(new ProjectileVisual(caster, projectile));
                caster.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 0.8F, 1.15F);
            }
            case EffectCommand.Aura aura -> {
                ACTIVE.add(new AuraVisual(caster, aura));
                caster.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
            }
            case EffectCommand.WildMagic wildMagic -> {
                ACTIVE.add(new WildMagicVisual(caster, wildMagic));
                caster.playSound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.0F, 0.65F);
            }
        }
    }

    public static void startShowcase(ServerPlayerEntity player) {
        ACTIVE.add(new CircleVisual(player));
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

    private static final class ProjectileVisual implements ActiveVisual {
        private final ServerPlayerEntity caster;
        private final ServerWorld world;
        private final Vec3d origin;
        private final Vec3d direction;
        private final Element element;
        private final double radius;
        private final double magnitude;
        private int age;

        private ProjectileVisual(ServerPlayerEntity caster, EffectCommand.Projectile command) {
            this.caster = caster;
            this.world = caster.getServerWorld();
            this.origin = toMinecraft(command.origin());
            this.direction = toMinecraft(command.direction()).normalize();
            this.element = command.element().orElse(Element.ARCANE);
            this.radius = Math.max(0.4, Math.min(command.radius(), 2.5));
            this.magnitude = command.magnitude();
        }

        @Override
        public boolean tick() {
            if (age++ >= 45 || caster.isRemoved()) {
                return false;
            }
            Vec3d position = origin.add(direction.multiply(age * 0.55));
            Vec3d previous = origin.add(direction.multiply((age - 1) * 0.55));
            HitResult blockHit = world.raycast(new RaycastContext(
                    previous,
                    position,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    caster));
            if (blockHit.getType() != HitResult.Type.MISS) {
                Vec3d hitPosition = blockHit.getPos();
                world.spawnParticles(ParticleTypes.SMOKE,
                        hitPosition.x, hitPosition.y, hitPosition.z,
                        8, 0.2, 0.2, 0.2, 0.02);
                return false;
            }
            ParticleEffect primary = element == Element.FROST
                    ? ParticleTypes.SNOWFLAKE
                    : element == Element.VOID ? ParticleTypes.DRAGON_BREATH : ParticleTypes.FLAME;
            world.spawnParticles(primary, position.x, position.y, position.z,
                    7, radius * 0.18, radius * 0.18, radius * 0.18, 0.02);
            world.spawnParticles(ParticleTypes.END_ROD, position.x, position.y, position.z,
                    2, 0.08, 0.08, 0.08, 0.0);

            List<LivingEntity> hits = world.getEntitiesByClass(
                    LivingEntity.class,
                    new Box(position, position).expand(radius),
                    entity -> entity != caster && entity.isAlive());
            if (!hits.isEmpty()) {
                LivingEntity target = hits.getFirst();
                target.damage(world.getDamageSources().magic(), (float) Math.min(40.0, 4.0 * magnitude));
                if (element == Element.FIRE) {
                    target.setOnFireFor((float) Math.min(10.0, 3.0 * magnitude));
                } else if (element == Element.FROST) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
                }
                world.spawnParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z,
                        3, 0.2, 0.2, 0.2, 0.0);
                return false;
            }
            return true;
        }
    }

    private static final class AuraVisual implements ActiveVisual {
        private final ServerPlayerEntity caster;
        private final ServerWorld world;
        private final Vec3d center;
        private final Element element;
        private final double targetRadius;
        private final double magnitude;
        private int age;

        private AuraVisual(ServerPlayerEntity caster, EffectCommand.Aura command) {
            this.caster = caster;
            this.world = caster.getServerWorld();
            this.center = toMinecraft(command.origin()).add(0.0, -1.35, 0.0);
            this.element = command.element().orElse(Element.ARCANE);
            this.targetRadius = Math.max(1.0, Math.min(command.radius(), 12.0));
            this.magnitude = command.magnitude();
        }

        @Override
        public boolean tick() {
            if (age++ >= 80 || caster.isRemoved()) {
                return false;
            }
            double radius = Math.min(targetRadius, 0.35 + age * 0.16);
            ParticleEffect particle = element == Element.FROST
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
                world.spawnParticles(particle, x, center.y + 0.15, z,
                        1, 0.02, 0.08, 0.02, 0.0);
            }
            if (age == 32) {
                List<LivingEntity> targets = world.getEntitiesByClass(
                        LivingEntity.class,
                        new Box(center, center).expand(targetRadius, 2.5, targetRadius),
                        entity -> entity != caster
                                && entity.isAlive()
                                && entity.squaredDistanceTo(center) <= targetRadius * targetRadius);
                for (LivingEntity target : targets) {
                    if (element == Element.FROST) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 140, 2));
                    }
                    target.damage(world.getDamageSources().magic(), (float) Math.min(20.0, 2.0 * magnitude));
                }
            }
            return true;
        }
    }

    private static final class WildMagicVisual implements ActiveVisual {
        private final ServerPlayerEntity caster;
        private final ServerWorld world;
        private final EffectCommand.WildMagic command;
        private final SplittableRandom random;
        private final Vec3d origin;
        private final Vec3d violentDirection;
        private int age;

        private WildMagicVisual(ServerPlayerEntity caster, EffectCommand.WildMagic command) {
            this.caster = caster;
            this.world = caster.getServerWorld();
            this.command = command;
            this.random = new SplittableRandom(command.variationSeed());
            this.origin = command.origin().map(SpellVisualManager::toMinecraft).orElse(caster.getEyePos());
            this.violentDirection = randomDirection(random);
        }

        @Override
        public boolean tick() {
            if (age++ >= 50 || caster.isRemoved()) {
                return false;
            }
            if (age == 1 && command.category() == WildMagicCategory.INTERNAL_MANA_DETONATION) {
                caster.damage(world.getDamageSources().magic(), 4.0F);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        origin.x, origin.y, origin.z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            switch (command.category()) {
                case INTERNAL_MANA_DETONATION -> world.spawnParticles(
                        ParticleTypes.TOTEM_OF_UNDYING,
                        caster.getX(), caster.getBodyY(0.6), caster.getZ(),
                        18, 0.5, 0.8, 0.5, 0.2);
                case UNSTRUCTURED_ELEMENT_BURST -> {
                    for (int i = 0; i < 16; i++) {
                        Vec3d offset = randomDirection(random).multiply(0.4 + random.nextDouble() * 3.5);
                        Vec3d position = origin.add(offset);
                        world.spawnParticles(ParticleTypes.WITCH,
                                position.x, position.y, position.z,
                                1, 0.04, 0.04, 0.04, 0.05);
                    }
                }
                case VIOLENT_MISCAST -> {
                    Vec3d position = origin.add(violentDirection.multiply(age * 0.48));
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                            position.x, position.y, position.z,
                            6, 0.25, 0.25, 0.25, 0.04);
                    world.spawnParticles(ParticleTypes.FLAME,
                            position.x, position.y, position.z,
                            4, 0.15, 0.15, 0.15, 0.05);
                }
            }
            return true;
        }
    }

    private static final class CircleVisual implements ActiveVisual {
        private final ServerPlayerEntity player;
        private final ServerWorld world;
        private int age;

        private CircleVisual(ServerPlayerEntity player) {
            this.player = player;
            this.world = player.getServerWorld();
        }

        @Override
        public boolean tick() {
            if (age++ >= DEV_SHOWCASE_DURATION_TICKS || player.isRemoved()) {
                return false;
            }
            Vec3d forward = player.getRotationVec(1.0F).normalize();
            Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
            if (right.lengthSquared() < 1.0e-6) {
                right = new Vec3d(1.0, 0.0, 0.0);
            } else {
                right = right.normalize();
            }
            Vec3d up = right.crossProduct(forward).normalize();
            Vec3d center = player.getEyePos().add(forward.multiply(5.0));

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

    private static void drawRing(
            ServerWorld world,
            Vec3d center,
            Vec3d right,
            Vec3d up,
            double radius,
            int points,
            ParticleEffect particle) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            Vec3d position = center
                    .add(right.multiply(Math.cos(angle) * radius))
                    .add(up.multiply(Math.sin(angle) * radius));
            world.spawnParticles(particle, position.x, position.y, position.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void drawStar(ServerWorld world, Vec3d center, Vec3d right, Vec3d up, double radius) {
        Vec3d[] points = new Vec3d[5];
        for (int i = 0; i < points.length; i++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / points.length;
            points[i] = center
                    .add(right.multiply(Math.cos(angle) * radius))
                    .add(up.multiply(Math.sin(angle) * radius));
        }
        for (int i = 0; i < points.length; i++) {
            Vec3d start = points[i];
            Vec3d end = points[(i + 2) % points.length];
            for (int step = 0; step <= 6; step++) {
                double t = step / 6.0;
                Vec3d position = start.multiply(1.0 - t).add(end.multiply(t));
                world.spawnParticles(ParticleTypes.FLAME,
                        position.x, position.y, position.z,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static Vec3d randomDirection(SplittableRandom random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            Vec3d vector = new Vec3d(
                    random.nextDouble(-1.0, 1.0),
                    random.nextDouble(-0.6, 1.0),
                    random.nextDouble(-1.0, 1.0));
            if (vector.lengthSquared() > 1.0e-6) {
                return vector.normalize();
            }
        }
        return new Vec3d(0.0, 1.0, 0.0);
    }

    private static Vec3d toMinecraft(Vec3 vector) {
        return new Vec3d(vector.x(), vector.y(), vector.z());
    }
}
