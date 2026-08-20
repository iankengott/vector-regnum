package vectorregnum.neoforge;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.ManaCostModel;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.RuntimeValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.SpellVm;
import vectorregnum.core.vm2.TickResult;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.WorldAccess;
import vectorregnum.core.vm2.WorldEffect;
import vectorregnum.core.presentation.PresentationCompiler;
import vectorregnum.core.semantic.SemanticInstruction;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.Vm2CircleCompilation;
import vectorregnum.neoforge.ponder.PonderTimeline;
import vectorregnum.neoforge.ponder.PonderTimelineBuilder;
import vectorregnum.neoforge.ponder.PonderTraceNetworking;
import vectorregnum.neoforge.multiplayer.CastAbuseGuard;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;
import vectorregnum.neoforge.multiplayer.SpellLeasePolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Executes vm2 once per server tick and is the only adapter allowed to mutate entities. */
public final class NeoForgeVmService {
    private static final int PONDER_LIVE_INTERVAL_TICKS = 10;
    private static final List<ActiveVm> ACTIVE_VMS = new ArrayList<>();
    private static final List<ActiveForce> ACTIVE_FORCES = new ArrayList<>();
    private static final CastAbuseGuard ABUSE_GUARD = new CastAbuseGuard();
    private static final Set<UUID> CANCELLED_OWNERS = new HashSet<>();
    private static boolean initialized;

    private NeoForgeVmService() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(NeoForgeVmService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ACTIVE_VMS.clear();
            ACTIVE_FORCES.clear();
            ABUSE_GUARD.clear();
            CANCELLED_OWNERS.clear();
        });
    }

    /** A real delayed, tick-resumed VM cast used by Vector Step and visual checks. */
    public static boolean launchVectorStep(
            ServerPlayerEntity player, boolean chargeMana, int delayTicks, double strength) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vector3 impulse = new Vector3(look.x * strength, Math.max(0.18, look.y * strength + 0.22),
                look.z * strength);
        Program program = impulseProgram(player.getUuidAsString(), impulse, delayTicks, 1);
        return start(player, syntheticCompilation(program), chargeMana, "Vector Step");
    }

    public static boolean launchKineticWard(
            ServerPlayerEntity player, Entity target, Vec3d impulse, boolean chargeMana) {
        Program program = impulseProgram(target.getUuidAsString(),
                new Vector3(impulse.x, impulse.y, impulse.z), 0, 1);
        return start(player, syntheticCompilation(program), chargeMana, "Kinetic Ward");
    }

    public static boolean startAuthored(
            ServerPlayerEntity player, Program program, boolean chargeMana, String label) {
        return start(player, syntheticCompilation(program), chargeMana, label,
                (owner, steps) -> SemanticSpellExecutor.execute(owner, steps, false));
    }

    public static boolean startAuthored(ServerPlayerEntity player,
            Vm2CircleCompilation compilation, boolean chargeMana, String label) {
        if (compilation.hasErrors() || compilation.compiledProgram().isEmpty()) {
            return false;
        }
        return start(player, compilation, chargeMana, label,
                (owner, steps) -> SemanticSpellExecutor.execute(owner, steps, false));
    }

    /** Queues a lowered semantic program and applies its ordered plan only at EXECUTE. */
    public static boolean startSemantic(ServerPlayerEntity player, Program program,
            boolean chargeMana, String label,
            BiConsumer<ServerPlayerEntity, List<SemanticInstruction>> executor) {
        return start(player, syntheticCompilation(program), chargeMana, label, executor);
    }

    public static PerceptionReport perceptionProbe(ServerPlayerEntity player, double radius) {
        SourceLocation source = SourceLocation.at(0, "PERCEPTION_PROBE");
        Program program = new Program(List.of(
                Instruction.push(new RuntimeValue.PointValue(toCore(player.getPos())), source),
                Instruction.push(new RuntimeValue.NumberValue(radius), SourceLocation.at(1, "RADIUS")),
                Instruction.select(WorldAccess.SelectionFilter.ANY, radius, 1,
                        SourceLocation.at(2, "SELECT_RADIUS")),
                Instruction.halt(SourceLocation.at(3, "HALT"))));
        SpellVm vm = new SpellVm(program, new MinecraftWorldAccess(player));
        TickResult result = vm.tick();
        int count = vm.stackTopFirst().stream()
                .filter(RuntimeValue.ListValue.class::isInstance)
                .map(RuntimeValue.ListValue.class::cast)
                .findFirst().map(value -> value.values().size()).orElse(0);
        return new PerceptionReport(result.status(), count, program.manaCost());
    }

    /** Shares the burst limiter with compatibility casts that complete immediately. */
    public static boolean admitImmediateCast(ServerPlayerEntity player) {
        CastAbuseGuard.Admission admission = ABUSE_GUARD.acquire(
                player.getUuid(), player.getServerWorld().getTime());
        if (!admission.accepted()) {
            player.sendMessage(Text.literal(admission.message()).formatted(Formatting.RED), true);
            return false;
        }
        ABUSE_GUARD.release(player.getUuid());
        return true;
    }

    public static Program impulseProgram(
            String entityId, Vector3 impulse, int delayTicks, int durationTicks) {
        List<Instruction> instructions = new ArrayList<>();
        int index = 0;
        instructions.add(Instruction.duration(durationTicks, SourceLocation.at(index++, "DURATION")));
        if (delayTicks > 0) {
            instructions.add(Instruction.delay(delayTicks, SourceLocation.at(index++, "DELAY")));
        }
        instructions.add(Instruction.push(new RuntimeValue.EntityValue(entityId),
                SourceLocation.at(index++, "TARGET")));
        instructions.add(Instruction.push(new RuntimeValue.VectorValue(impulse),
                SourceLocation.at(index++, "VECTOR")));
        instructions.add(Instruction.impulse(Math.max(1.0, impulse.length() * impulse.length() * 10.0),
                0.0, SourceLocation.at(index++, "IMPULSE")));
        instructions.add(Instruction.halt(SourceLocation.at(index, "HALT")));
        return new Program(instructions);
    }

    private static boolean start(ServerPlayerEntity player, Vm2CircleCompilation compilation,
            boolean chargeMana, String label) {
        return start(player, compilation, chargeMana, label, null);
    }

    private static boolean start(ServerPlayerEntity player, Vm2CircleCompilation compilation,
            boolean chargeMana, String label,
            BiConsumer<ServerPlayerEntity, List<SemanticInstruction>> semanticExecutor) {
        Program program = compilation.compiledProgram().orElseThrow();
        CastAbuseGuard.Admission admission = ABUSE_GUARD.acquire(
                player.getUuid(), player.getServerWorld().getTime());
        if (!admission.accepted()) {
            player.sendMessage(Text.literal(admission.message()).formatted(Formatting.RED), true);
            return false;
        }
        if (chargeMana && ManaData.isChannelLocked(player)) {
            player.sendMessage(Text.literal("Mana channel locked for "
                            + ManaData.remainingLockTicks(player) + " more ticks")
                    .formatted(Formatting.RED), true);
            ABUSE_GUARD.release(player.getUuid());
            return false;
        }
        double cost = program.manaCost().total();
        if (chargeMana && (!ManaData.ensureAvailable(player, cost)
                || !ManaData.trySpend(player, cost))) {
            player.sendMessage(Text.literal(String.format(
                            "%s needs %.2f μ; only %.2f μ is available",
                            label, cost, ManaData.available(player)))
                    .formatted(Formatting.RED), true);
            ABUSE_GUARD.release(player.getUuid());
            return false;
        }
        long presentationSeed = player.getUuid().getMostSignificantBits()
                ^ player.getServerWorld().getTime() ^ program.instructions().hashCode();
        VmPresentationBridge presentation = new VmPresentationBridge(player,
                PresentationCompiler.compile(label, presentationSeed, program));
        ActiveVm active = new ActiveVm(player.getUuid(), player.getServerWorld(),
                new SpellVm(program, new MinecraftWorldAccess(player), presentation), label,
                presentation, compilation, new ArrayList<>(), semanticExecutor, new ArrayList<>());
        ACTIVE_VMS.add(active);
        publishLiveTrace(player, active);
        player.sendMessage(Text.literal(String.format(
                        "%s queued in vm2 • %.2f μ • tick-resumable",
                        label, cost)).formatted(Formatting.AQUA), true);
        return true;
    }

    private static void tick(MinecraftServer server) {
        Iterator<ActiveVm> vmIterator = ACTIVE_VMS.iterator();
        while (vmIterator.hasNext()) {
            ActiveVm active = vmIterator.next();
            ServerPlayerEntity currentOwner = server.getPlayerManager().getPlayer(active.owner);
            if (CANCELLED_OWNERS.contains(active.owner) || !validLifecycle(currentOwner, active)) {
                ABUSE_GUARD.release(active.owner);
                vmIterator.remove();
                continue;
            }
            TickResult result = active.vm.tick();
            active.trace.add(result);
            for (WorldEffect effect : result.effects()) {
                if (effect instanceof WorldEffect.SemanticStep semantic) {
                    active.semanticSteps.add(semantic.instruction());
                } else {
                    apply(currentOwner, active.world, effect);
                }
            }
            if (result.status() == TickResult.Status.FAULTED) {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(active.owner);
                result.fault().ifPresent(fault -> {
                    VectorRegnumMod.LOGGER.warn("vm2 {} fault {} at {}:{}: {}", active.label,
                            fault.code(), fault.source().line(), fault.source().column(), fault.message());
                    if (owner != null) {
                        owner.sendMessage(Text.literal("VM fault " + fault.code() + " at sigil "
                                        + fault.source().sourceIndex() + ": " + fault.message())
                                .formatted(Formatting.RED), false);
                    }
                });
                publishTrace(owner, active);
                ABUSE_GUARD.release(active.owner);
                vmIterator.remove();
            } else if (result.status() == TickResult.Status.HALTED) {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(active.owner);
                if (owner != null && active.semanticExecutor != null && !active.semanticSteps.isEmpty()) {
                    try {
                        active.semanticExecutor.accept(owner, List.copyOf(active.semanticSteps));
                    } catch (RuntimeException exception) {
                        VectorRegnumMod.LOGGER.error("Semantic adapter rejected {} after validated VM execution",
                                active.label, exception);
                        owner.sendMessage(Text.literal("Semantic execution failed safely for " + active.label)
                                .formatted(Formatting.RED), false);
                    }
                }
                publishTrace(owner, active);
                ABUSE_GUARD.release(active.owner);
                vmIterator.remove();
            } else if (active.trace.size() == 1
                    || active.trace.size() % PONDER_LIVE_INTERVAL_TICKS == 0) {
                publishLiveTrace(server.getPlayerManager().getPlayer(active.owner), active);
            }
        }

        Iterator<ActiveForce> forceIterator = ACTIVE_FORCES.iterator();
        while (forceIterator.hasNext()) {
            ActiveForce active = forceIterator.next();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(active.owner);
            if (CANCELLED_OWNERS.contains(active.owner) || owner == null
                    || !applyTimed(owner, active) || --active.remaining <= 0) {
                forceIterator.remove();
            }
        }
        CANCELLED_OWNERS.clear();
    }

    private static boolean validLifecycle(ServerPlayerEntity owner, ActiveVm active) {
        return SpellLeasePolicy.shouldContinue(owner != null && !owner.isRemoved(),
                owner != null && owner.isAlive(),
                owner != null && owner.getServerWorld() == active.world,
                owner != null && active.world.isChunkLoaded(owner.getBlockPos()));
    }

    public static void cancelOwner(UUID owner, String reason) {
        long pending = ACTIVE_VMS.stream().filter(active -> active.owner.equals(owner)).count();
        CANCELLED_OWNERS.add(owner);
        ABUSE_GUARD.clear(owner);
        if (pending > 0) VectorRegnumMod.LOGGER.info("Cancelling {} spell VM(s) for {}: {}",
                pending, owner, reason);
    }

    private static void apply(ServerPlayerEntity owner, ServerWorld world, WorldEffect effect) {
        if (effect instanceof WorldEffect.SemanticStep) return;
        Entity entity = find(world, effect.entityId());
        if (entity == null || !SpellSecurityPolicy.canAffectEntity(owner, entity)) return;
        switch (effect) {
            case WorldEffect.Impulse impulse -> {
                Vec3d change = clamped(toMinecraft(impulse.impulse()), 4.0);
                entity.addVelocity(change);
                entity.velocityModified = true;
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                        24, 0.35, 0.45, 0.35, 0.12);
            }
            default -> {
                ActiveForce active = new ActiveForce(owner.getUuid(), world, effect, effect.durationTicks());
                if (applyTimed(owner, active) && effect.durationTicks() > 1) {
                    active.remaining--;
                    ACTIVE_FORCES.add(active);
                }
            }
        }
    }

    private static boolean applyTimed(ServerPlayerEntity owner, ActiveForce active) {
        ServerWorld world = active.world;
        WorldEffect effect = active.effect;
        Entity entity = find(world, effect.entityId());
        if (entity == null || !SpellSecurityPolicy.canAffectEntity(owner, entity)) return false;
        switch (effect) {
            case WorldEffect.Impulse ignored -> { return false; }
            case WorldEffect.SemanticStep ignored -> { return false; }
            case WorldEffect.Acceleration acceleration ->
                    entity.addVelocity(clamped(toMinecraft(acceleration.acceleration()), 1.0));
            case WorldEffect.Damping damping ->
                    entity.setVelocity(entity.getVelocity().multiply(damping.factor()));
            case WorldEffect.FollowPath path -> {
                Vec3d current = entity.getPos();
                while (active.pathIndex < path.points().size() - 1
                        && toMinecraft(path.points().get(active.pathIndex))
                                .squaredDistanceTo(current) < 0.16) {
                    active.pathIndex++;
                }
                Vec3d target = toMinecraft(path.points().get(active.pathIndex));
                if (active.pathIndex == path.points().size() - 1
                        && target.squaredDistanceTo(current) < 0.16) {
                    entity.setVelocity(Vec3d.ZERO);
                    return false;
                }
                moveToward(entity, target, path.speed());
            }
            case WorldEffect.MoveToward move ->
                    moveToward(entity, toMinecraft(move.point()), move.speed());
            case WorldEffect.KeepDistance keep -> {
                Entity target = find(world, keep.targetId());
                if (target == null || !SpellSecurityPolicy.canAffectEntity(owner, target)) return false;
                Vec3d delta = entity.getPos().subtract(target.getPos());
                double current = delta.length();
                if (current > 1.0e-6) {
                    double correction = Math.clamp((keep.distance() - current) * 0.08, -0.5, 0.5);
                    entity.addVelocity(delta.normalize().multiply(correction));
                }
            }
        }
        entity.velocityModified = true;
        return true;
    }

    private static void moveToward(Entity entity, Vec3d target, double speed) {
        Vec3d delta = target.subtract(entity.getPos());
        if (delta.lengthSquared() > 1.0e-8) {
            entity.setVelocity(delta.normalize().multiply(Math.min(4.0, speed)));
        }
    }

    private static Entity find(ServerWorld world, String entityId) {
        try {
            return world.getEntity(UUID.fromString(entityId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Vec3d clamped(Vec3d vector, double maximum) {
        double length = vector.length();
        return length > maximum ? vector.multiply(maximum / length) : vector;
    }

    private static Vector3 toCore(Vec3d vector) {
        return new Vector3(vector.x, vector.y, vector.z);
    }

    private static Vec3d toMinecraft(Vector3 vector) {
        return new Vec3d(vector.x(), vector.y(), vector.z());
    }

    private static void publishTrace(ServerPlayerEntity owner, ActiveVm active) {
        if (owner == null) return;
        try {
            PonderTimeline timeline = PonderTimelineBuilder.fromVm2("server-vm-trace",
                    active.label + " — authoritative VM trace", active.compilation, active.trace);
            PonderTraceNetworking.publish(owner, timeline);
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Could not retain Ponder trace for {}", active.label,
                    exception);
        }
    }

    private static void publishLiveTrace(ServerPlayerEntity owner, ActiveVm active) {
        if (owner == null) return;
        try {
            PonderTimeline timeline = PonderTimelineBuilder.fromVm2("server-vm-trace",
                    active.label + " — live authoritative VM trace", active.compilation,
                    active.trace);
            PonderTraceNetworking.publishLive(owner, timeline);
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.warn("Could not stream Ponder trace for {}", active.label,
                    exception);
        }
    }

    private static Vm2CircleCompilation syntheticCompilation(Program program) {
        int maximumSource = program.instructions().stream()
                .mapToInt(instruction -> instruction.source().sourceIndex()).max().orElse(0);
        int size = Math.min(maximumSource + 1, PonderTimeline.MAX_STEPS / 2);
        List<PlacedSigil> order = new ArrayList<>(size);
        for (int sourceIndex = 0; sourceIndex < size; sourceIndex++) {
            final int wanted = sourceIndex;
            SourceLocation source = program.instructions().stream()
                    .map(Instruction::source)
                    .filter(candidate -> candidate.sourceIndex() == wanted)
                    .findFirst().orElse(SourceLocation.at(sourceIndex, "UNKNOWN_SOURCE"));
            String sigil = source.sigilId().matches("[A-Z][A-Z0-9_]{0,63}")
                    ? source.sigilId() : "UNKNOWN_SOURCE";
            order.add(new PlacedSigil(new CircleCoordinate(Math.max(0, source.line() - 1),
                    Math.max(0, source.column() - 1)), sigil));
        }
        return new Vm2CircleCompilation(order, program, List.of());
    }

    public record PerceptionReport(
            TickResult.Status status, int entityCount, ManaCostModel.Breakdown cost) {
    }

    private record ActiveVm(UUID owner, ServerWorld world, SpellVm vm, String label,
            VmPresentationBridge presentation, Vm2CircleCompilation compilation,
            List<TickResult> trace,
            BiConsumer<ServerPlayerEntity, List<SemanticInstruction>> semanticExecutor,
            List<SemanticInstruction> semanticSteps) {
    }

    private static final class ActiveForce {
        private final UUID owner;
        private final ServerWorld world;
        private final WorldEffect effect;
        private int remaining;
        private int pathIndex;

        private ActiveForce(UUID owner, ServerWorld world, WorldEffect effect, int remaining) {
            this.owner = owner;
            this.world = world;
            this.effect = effect;
            this.remaining = remaining;
        }
    }

    private static final class MinecraftWorldAccess implements WorldAccess {
        private final ServerPlayerEntity caster;
        private final ServerWorld world;

        private MinecraftWorldAccess(ServerPlayerEntity caster) {
            this.caster = caster;
            this.world = caster.getServerWorld();
        }

        @Override
        public Optional<EntitySnapshot> entity(String id) {
            return Optional.ofNullable(find(world, id)).map(this::snapshot);
        }

        @Override
        public Optional<RaycastHit> raycast(
                Vector3 origin, Vector3 normalizedDirection, double maxDistance, SelectionFilter filter) {
            Vec3d start = toMinecraft(origin);
            Vec3d end = start.add(toMinecraft(normalizedDirection).multiply(maxDistance));
            EntityHitResult hit = ProjectileUtil.raycast(caster, start, end,
                    new Box(start, end).expand(1.0), entity -> matches(entity, filter),
                    maxDistance * maxDistance);
            if (hit == null) return Optional.empty();
            HitResult blockHit = world.raycast(new RaycastContext(start, end,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
            if (blockHit.getType() != HitResult.Type.MISS
                    && start.squaredDistanceTo(blockHit.getPos())
                            + 1.0e-9 < start.squaredDistanceTo(hit.getPos())) {
                return Optional.empty();
            }
            return Optional.of(new RaycastHit(toCore(hit.getPos()),
                    Optional.of(snapshot(hit.getEntity())), start.distanceTo(hit.getPos())));
        }

        @Override
        public List<EntitySnapshot> select(
                Vector3 center, double radius, SelectionFilter filter) {
            Vec3d point = toMinecraft(center);
            return world.getOtherEntities(filter.includeCaster() ? null : caster,
                            new Box(point, point).expand(radius), entity ->
                                    entity.squaredDistanceTo(point) <= radius * radius
                                            && matches(entity, filter))
                    .stream().map(this::snapshot).toList();
        }

        private boolean matches(Entity entity, SelectionFilter filter) {
            if (!filter.includeCaster() && entity == caster) return false;
            Set<String> tags = tags(entity);
            if (!tags.containsAll(filter.requiredTags())) return false;
            return filter.kind().map(kind -> tags.contains(kind)
                    || Registries.ENTITY_TYPE.getId(entity.getType()).toString().equals(kind)).orElse(true);
        }

        private EntitySnapshot snapshot(Entity entity) {
            double mass = Math.max(0.1, entity.getWidth() * entity.getHeight());
            return new EntitySnapshot(entity.getUuidAsString(), toCore(entity.getPos()), mass,
                    Registries.ENTITY_TYPE.getId(entity.getType()).toString(), tags(entity));
        }

        private Set<String> tags(Entity entity) {
            Set<String> tags = new HashSet<>();
            tags.add("entity");
            if (entity instanceof LivingEntity) tags.add("living");
            if (entity instanceof HostileEntity) tags.add("hostile");
            if (entity instanceof ServerPlayerEntity) tags.add("player");
            return Set.copyOf(tags);
        }
    }
}
