package vectorregnum.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.Element;
import vectorregnum.core.vm2.ManaCostModel;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.RuntimeValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.SpellVm;
import vectorregnum.core.vm2.TickResult;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.WorldAccess;
import vectorregnum.core.vm2.WorldEffect;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.core.presentation.PresentationCompiler;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.neoforge.presentation.ServerTraces;
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
import java.util.function.Consumer;

/** Executes vm2 once per server tick and is the only adapter allowed to mutate entities. */
public final class NeoForgeVmService {
    private static final int PONDER_LIVE_INTERVAL_TICKS = 10;
    private static final List<ActiveVm> ACTIVE_VMS = new ArrayList<>();
    private static final List<ActiveVm> PENDING_VMS = new ArrayList<>();
    private static final List<ActiveForce> ACTIVE_FORCES = new ArrayList<>();
    private static final CastAbuseGuard ABUSE_GUARD = new CastAbuseGuard();
    private static final Set<UUID> CANCELLED_OWNERS = new HashSet<>();
    private static boolean tickingVms;
    private static boolean initialized;

    private NeoForgeVmService() {
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
        settleAll(ResourceEscrow.Outcome.SHUTDOWN);
        CastingResourceService.refundAll(event.getServer(), ResourceEscrow.Outcome.SHUTDOWN);
        ACTIVE_VMS.clear();
        PENDING_VMS.clear();
        ACTIVE_FORCES.clear();
        ABUSE_GUARD.clear();
        CANCELLED_OWNERS.clear();
        tickingVms = false;
        initialized = false;
    }

    /** A real delayed, tick-resumed VM cast used by Vector Step and visual checks. */
    public static boolean launchVectorStep(
            ServerPlayer player, boolean chargeMana, int delayTicks, double strength) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vector3 impulse = new Vector3(look.x * strength, Math.max(0.18, look.y * strength + 0.22),
                look.z * strength);
        Program program = impulseProgram(player.getStringUUID(), impulse, delayTicks, 1);
        return start(player, syntheticCompilation(program), chargeMana, "Vector Step");
    }

    public static boolean launchKineticWard(
            ServerPlayer player, Entity target, Vec3 impulse, boolean chargeMana) {
        Program program = impulseProgram(target.getStringUUID(),
                new Vector3(impulse.x, impulse.y, impulse.z), 0, 1);
        return start(player, syntheticCompilation(program), chargeMana, "Kinetic Ward");
    }

    public static boolean startAuthored(
            ServerPlayer player, Program program, boolean chargeMana, String label) {
        return startAuthored(player, program, chargeMana, label, ignored -> { });
    }

    public static boolean startAuthored(ServerPlayer player, Program program,
            boolean chargeMana, String label, Consumer<ResourceEscrow.Outcome> terminal) {
        return start(player, syntheticCompilation(program), chargeMana, label,
                (owner, steps) -> SemanticSpellExecutor.execute(owner, steps, false),
                CastingMethod.BARE, true, ItemStack.EMPTY, terminal);
    }

    public static boolean startAuthored(ServerPlayer player,
            Vm2CircleCompilation compilation, boolean chargeMana, String label) {
        return startAuthored(player, compilation, chargeMana, label,
                CastingMethod.BARE, true, ItemStack.EMPTY, ignored -> { });
    }

    public static boolean startAuthored(ServerPlayer player,
            Vm2CircleCompilation compilation, boolean chargeMana, String label,
            CastingMethod method, boolean useStaged, ItemStack mediumStack,
            Consumer<ResourceEscrow.Outcome> terminal) {
        if (compilation.hasErrors() || compilation.compiledProgram().isEmpty()) {
            return false;
        }
        return start(player, compilation, chargeMana, label,
                (owner, steps) -> SemanticSpellExecutor.execute(owner, steps, false),
                method, useStaged, mediumStack, terminal);
    }

    /** Queues a lowered semantic program and applies its ordered plan only at EXECUTE. */
    public static boolean startSemantic(ServerPlayer player, Program program,
            boolean chargeMana, String label,
            BiConsumer<ServerPlayer, List<SemanticInstruction>> executor) {
        return startSemantic(player, program, chargeMana, label, executor,
                CastingMethod.BARE, true, ItemStack.EMPTY, ignored -> { });
    }

    public static boolean startSemantic(ServerPlayer player, Program program,
            boolean chargeMana, String label,
            BiConsumer<ServerPlayer, List<SemanticInstruction>> executor,
            CastingMethod method, boolean useStaged, ItemStack mediumStack,
            Consumer<ResourceEscrow.Outcome> terminal) {
        return start(player, syntheticCompilation(program), chargeMana, label, executor,
                method, useStaged, mediumStack, terminal);
    }

    public static PerceptionReport perceptionProbe(ServerPlayer player, double radius) {
        SourceLocation source = SourceLocation.at(0, "PERCEPTION_PROBE");
        Program program = new Program(List.of(
                Instruction.push(new RuntimeValue.PointValue(toCore(player.position())), source),
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
    public static boolean admitImmediateCast(ServerPlayer player) {
        CastAbuseGuard.Admission admission = ABUSE_GUARD.acquire(
                player.getUUID(), player.serverLevel().getGameTime());
        if (!admission.accepted()) {
            player.displayClientMessage(Component.literal(admission.message()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        ABUSE_GUARD.release(player.getUUID());
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

    private static boolean start(ServerPlayer player, Vm2CircleCompilation compilation,
            boolean chargeMana, String label) {
        return start(player, compilation, chargeMana, label, null,
                CastingMethod.BARE, true, ItemStack.EMPTY, ignored -> { });
    }

    private static boolean start(ServerPlayer player, Vm2CircleCompilation compilation,
            boolean chargeMana, String label,
            BiConsumer<ServerPlayer, List<SemanticInstruction>> semanticExecutor) {
        return start(player, compilation, chargeMana, label, semanticExecutor,
                CastingMethod.BARE, true, ItemStack.EMPTY, ignored -> { });
    }

    private static boolean start(ServerPlayer player, Vm2CircleCompilation compilation,
            boolean chargeMana, String label,
            BiConsumer<ServerPlayer, List<SemanticInstruction>> semanticExecutor,
            CastingMethod method, boolean useStaged, ItemStack mediumStack,
            Consumer<ResourceEscrow.Outcome> terminal) {
        Program program = compilation.compiledProgram().orElseThrow();
        CastAbuseGuard.Admission admission = ABUSE_GUARD.acquire(
                player.getUUID(), player.serverLevel().getGameTime());
        if (!admission.accepted()) {
            player.displayClientMessage(Component.literal(admission.message()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (chargeMana && ManaData.isChannelLocked(player)) {
            player.displayClientMessage(Component.literal("Mana channel locked for "
                            + ManaData.remainingLockTicks(player) + " more ticks")
                    .withStyle(ChatFormatting.RED), true);
            ABUSE_GUARD.release(player.getUUID());
            return false;
        }
        Element spellElement = spellElement(compilation);
        double adjustedMana = ManaData.adjustedCost(player, program.manaCost().total(), spellElement);
        double adjustedUpkeep = ManaData.adjustedUpkeep(player,
                program.manaCost().duration(), spellElement);
        CastCost baseline = CastingResourceService.baseline(method, adjustedMana,
                program.instructions().size(), adjustedUpkeep,
                ManaData.instability(player, spellElement));
        Optional<CastingResourceService.Reservation> reserved = CastingResourceService.begin(
                player, method, baseline, chargeMana, useStaged, mediumStack);
        if (reserved.isEmpty()) {
            ABUSE_GUARD.release(player.getUUID());
            return false;
        }
        CastingResourceService.Reservation reservation = reserved.orElseThrow();
        long presentationSeed = player.getUUID().getMostSignificantBits()
                ^ player.serverLevel().getGameTime() ^ program.instructions().hashCode();
        VmPresentationBridge presentation = new VmPresentationBridge(player,
                PresentationCompiler.compile(label, presentationSeed, program));
        ActiveVm active = new ActiveVm(player.getUUID(), player.serverLevel(),
                new SpellVm(program, new MinecraftWorldAccess(player), presentation), label,
                chargeMana,
                presentation, compilation, new ArrayList<>(), semanticExecutor, new ArrayList<>());
        active.reservation = reservation;
        active.remainingCastTicks = reservation.castingTicks();
        active.terminal = terminal;
        (tickingVms ? PENDING_VMS : ACTIVE_VMS).add(active);
        publishLiveTrace(player, active);
        player.displayClientMessage(Component.literal(String.format(
                        "%s queued in vm2 • %.2f μ • tick-resumable",
                        label, reservation.quote().finalCost().mana())).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private static void tick(MinecraftServer server) {
        tickingVms = true;
        try {
            Iterator<ActiveVm> vmIterator = ACTIVE_VMS.iterator();
            vmLoop:
            while (vmIterator.hasNext()) {
                ActiveVm active = vmIterator.next();
                ServerPlayer currentOwner = server.getPlayerList().getPlayer(active.owner);
                if (CANCELLED_OWNERS.contains(active.owner) || !validLifecycle(currentOwner, active)) {
                    settle(active, ResourceEscrow.Outcome.OWNER_LIFECYCLE);
                    ABUSE_GUARD.release(active.owner);
                    vmIterator.remove();
                    continue;
                }
                if (active.remainingCastTicks-- > 1) {
                    if (active.remainingCastTicks == 1
                            || active.remainingCastTicks % PONDER_LIVE_INTERVAL_TICKS == 0) {
                        publishLiveTrace(currentOwner, active);
                    }
                    continue;
                }
                TickResult result = active.vm.tick();
                active.trace.add(result);
                for (WorldEffect effect : result.effects()) {
                    if (effect instanceof WorldEffect.SemanticStep) continue;
                    Optional<ResourceEscrow.Outcome> rejection = effectRejection(
                            currentOwner, active.world, effect);
                    if (rejection.isPresent()) {
                        ResourceEscrow.Outcome outcome = rejection.orElseThrow();
                        currentOwner.sendSystemMessage(Component.literal("Cast stopped safely: "
                                        + outcome.name().toLowerCase(java.util.Locale.ROOT))
                                .withStyle(ChatFormatting.YELLOW), true);
                        settle(active, outcome);
                        publishTrace(currentOwner, active);
                        ABUSE_GUARD.release(active.owner);
                        vmIterator.remove();
                        continue vmLoop;
                    }
                }
                for (WorldEffect effect : result.effects()) {
                    if (effect instanceof WorldEffect.SemanticStep semantic) {
                        active.semanticSteps.add(semantic.instruction());
                    } else {
                        apply(currentOwner, active.world, effect);
                    }
                }
                if (result.status() == TickResult.Status.FAULTED) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(active.owner);
                    ResourceEscrow.Outcome faultOutcome = result.fault()
                            .filter(fault -> fault.code() == vectorregnum.core.vm2.VmFault.Code.ENTITY_NOT_FOUND)
                            .map(ignored -> ResourceEscrow.Outcome.UNLOADED_TARGET)
                            .orElse(ResourceEscrow.Outcome.GENUINE_SPELL_FAULT);
                    result.fault().ifPresent(fault -> {
                        VectorRegnumMod.LOGGER.warn("vm2 {} fault {} at {}:{}: {}", active.label,
                                fault.code(), fault.source().line(), fault.source().column(), fault.message());
                        if (owner != null) {
                            owner.sendSystemMessage(Component.literal("VM fault " + fault.code() + " at sigil "
                                            + fault.source().sourceIndex() + ": " + fault.message())
                                    .withStyle(ChatFormatting.RED));
                        }
                    });
                    if (owner != null && active.chargeMana && faultOutcome.consumesResources()) {
                        ManaData.lockChannel(owner,
                                ManaData.stabilityLockTicks(100L,
                                        active.reservation.quote().finalCost().instability()));
                    }
                    settle(active, faultOutcome);
                    publishTrace(owner, active);
                    ABUSE_GUARD.release(active.owner);
                    vmIterator.remove();
                } else if (result.status() == TickResult.Status.HALTED) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(active.owner);
                    ResourceEscrow.Outcome outcome = ResourceEscrow.Outcome.SUCCESS;
                    if (owner != null && active.semanticExecutor != null && !active.semanticSteps.isEmpty()) {
                        try {
                            active.semanticExecutor.accept(owner, List.copyOf(active.semanticSteps));
                        } catch (SemanticSpellExecutor.ExecutionRejection rejection) {
                            outcome = rejection.outcome();
                            owner.sendSystemMessage(Component.literal("Semantic execution stopped safely: "
                                            + outcome.name().toLowerCase(java.util.Locale.ROOT))
                                    .withStyle(ChatFormatting.YELLOW));
                        } catch (RuntimeException exception) {
                            outcome = ResourceEscrow.Outcome.ENGINE_FAILURE;
                            VectorRegnumMod.LOGGER.error("Semantic adapter rejected {} after validated VM execution",
                                    active.label, exception);
                            owner.sendSystemMessage(Component.literal("Semantic execution failed safely for " + active.label)
                                    .withStyle(ChatFormatting.RED));
                        }
                    }
                    settle(active, outcome);
                    publishTrace(owner, active);
                    ABUSE_GUARD.release(active.owner);
                    vmIterator.remove();
                } else if (active.trace.size() == 1
                        || active.trace.size() % PONDER_LIVE_INTERVAL_TICKS == 0) {
                    publishLiveTrace(server.getPlayerList().getPlayer(active.owner), active);
                }
            }
        } finally {
            tickingVms = false;
            ACTIVE_VMS.addAll(PENDING_VMS);
            PENDING_VMS.clear();
        }

        Iterator<ActiveForce> forceIterator = ACTIVE_FORCES.iterator();
        while (forceIterator.hasNext()) {
            ActiveForce active = forceIterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(active.owner);
            if (CANCELLED_OWNERS.contains(active.owner) || owner == null
                    || !applyTimed(owner, active) || --active.remaining <= 0) {
                forceIterator.remove();
            }
        }
        CANCELLED_OWNERS.clear();
    }

    private static boolean validLifecycle(ServerPlayer owner, ActiveVm active) {
        return SpellLeasePolicy.shouldContinue(owner != null && !owner.isRemoved(),
                owner != null && owner.isAlive(),
                owner != null && owner.serverLevel() == active.world,
                owner != null && active.world.isLoaded(owner.blockPosition()));
    }

    public static void cancelOwner(UUID owner, String reason) {
        long pending = java.util.stream.Stream.concat(ACTIVE_VMS.stream(), PENDING_VMS.stream())
                .filter(active -> active.owner.equals(owner)).count();
        CANCELLED_OWNERS.add(owner);
        ABUSE_GUARD.clear(owner);
        if (pending > 0) VectorRegnumMod.LOGGER.info("Cancelling {} spell VM(s) for {}: {}",
                pending, owner, reason);
    }

    private static void settle(ActiveVm active, ResourceEscrow.Outcome outcome) {
        CastingResourceService.settle(active.reservation, outcome);
        if (active.terminalNotified) return;
        active.terminalNotified = true;
        try {
            active.terminal.accept(outcome);
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.error("Casting terminal callback failed for {}", active.label,
                    exception);
        }
    }

    private static void settleAll(ResourceEscrow.Outcome outcome) {
        List<ActiveVm> all = new ArrayList<>(ACTIVE_VMS.size() + PENDING_VMS.size());
        all.addAll(ACTIVE_VMS);
        all.addAll(PENDING_VMS);
        for (ActiveVm active : all) {
            settle(active, outcome);
            ABUSE_GUARD.release(active.owner);
        }
    }

    private static Optional<ResourceEscrow.Outcome> effectRejection(
            ServerPlayer owner, ServerLevel world, WorldEffect effect) {
        Entity entity = find(world, effect.entityId());
        if (entity == null) return Optional.of(ResourceEscrow.Outcome.UNLOADED_TARGET);
        if (!SpellSecurityPolicy.canAffectEntity(owner, entity)) {
            return Optional.of(ResourceEscrow.Outcome.POLICY_REJECTED);
        }
        if (effect instanceof WorldEffect.KeepDistance keep) {
            Entity target = find(world, keep.targetId());
            if (target == null) return Optional.of(ResourceEscrow.Outcome.UNLOADED_TARGET);
            if (!SpellSecurityPolicy.canAffectEntity(owner, target)) {
                return Optional.of(ResourceEscrow.Outcome.POLICY_REJECTED);
            }
        }
        return Optional.empty();
    }

    private static void apply(ServerPlayer owner, ServerLevel world, WorldEffect effect) {
        if (effect instanceof WorldEffect.SemanticStep) return;
        Entity entity = find(world, effect.entityId());
        if (entity == null || !SpellSecurityPolicy.canAffectEntity(owner, entity)) return;
        switch (effect) {
            case WorldEffect.Impulse impulse -> {
                Vec3 change = clamped(toMinecraft(impulse.impulse()), 4.0);
                entity.setDeltaMovement(entity.getDeltaMovement().add(change));
                entity.hasImpulse = true;
                ServerTraces.burst(world,
                        new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                                entity.getZ()),
                        PresentationParticleStyle.SPARK, PresentationElement.ARCANE,
                        0.9F, 1.0F, 12);
            }
            default -> {
                ActiveForce active = new ActiveForce(owner.getUUID(), world, effect, effect.durationTicks());
                if (applyTimed(owner, active) && effect.durationTicks() > 1) {
                    active.remaining--;
                    ACTIVE_FORCES.add(active);
                }
            }
        }
    }

    private static boolean applyTimed(ServerPlayer owner, ActiveForce active) {
        ServerLevel world = active.world;
        WorldEffect effect = active.effect;
        Entity entity = find(world, effect.entityId());
        if (entity == null || !SpellSecurityPolicy.canAffectEntity(owner, entity)) return false;
        switch (effect) {
            case WorldEffect.Impulse ignored -> { return false; }
            case WorldEffect.SemanticStep ignored -> { return false; }
            case WorldEffect.Acceleration acceleration ->
                    entity.setDeltaMovement(entity.getDeltaMovement()
                            .add(clamped(toMinecraft(acceleration.acceleration()), 1.0)));
            case WorldEffect.Damping damping ->
                    entity.setDeltaMovement(entity.getDeltaMovement().scale(damping.factor()));
            case WorldEffect.FollowPath path -> {
                Vec3 current = entity.position();
                while (active.pathIndex < path.points().size() - 1
                        && toMinecraft(path.points().get(active.pathIndex))
                                .distanceToSqr(current) < 0.16) {
                    active.pathIndex++;
                }
                Vec3 target = toMinecraft(path.points().get(active.pathIndex));
                if (active.pathIndex == path.points().size() - 1
                        && target.distanceToSqr(current) < 0.16) {
                    entity.setDeltaMovement(Vec3.ZERO);
                    return false;
                }
                moveToward(entity, target, path.speed());
            }
            case WorldEffect.MoveToward move ->
                    moveToward(entity, toMinecraft(move.point()), move.speed());
            case WorldEffect.KeepDistance keep -> {
                Entity target = find(world, keep.targetId());
                if (target == null || !SpellSecurityPolicy.canAffectEntity(owner, target)) return false;
                Vec3 delta = entity.position().subtract(target.position());
                double current = delta.length();
                if (current > 1.0e-6) {
                    double correction = Math.clamp((keep.distance() - current) * 0.08, -0.5, 0.5);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(delta.normalize().scale(correction)));
                }
            }
        }
        entity.hasImpulse = true;
        return true;
    }

    private static void moveToward(Entity entity, Vec3 target, double speed) {
        Vec3 delta = target.subtract(entity.position());
        if (delta.lengthSqr() > 1.0e-8) {
            entity.setDeltaMovement(delta.normalize().scale(Math.min(4.0, speed)));
        }
    }

    private static Entity find(ServerLevel world, String entityId) {
        try {
            return world.getEntity(UUID.fromString(entityId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Vec3 clamped(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum ? vector.scale(maximum / length) : vector;
    }

    private static Vector3 toCore(Vec3 vector) {
        return new Vector3(vector.x, vector.y, vector.z);
    }

    private static Vec3 toMinecraft(Vector3 vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static void publishTrace(ServerPlayer owner, ActiveVm active) {
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

    private static void publishLiveTrace(ServerPlayer owner, ActiveVm active) {
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

    private static Element spellElement(Vm2CircleCompilation compilation) {
        return compilation.executionOrder().stream()
                .map(PlacedSigil::type)
                .filter(type -> type.startsWith("ELEMENT_"))
                .map(type -> type.substring("ELEMENT_".length()))
                .map(Element::fromId)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(Element.ARCANE);
    }

    public record PerceptionReport(
            TickResult.Status status, int entityCount, ManaCostModel.Breakdown cost) {
    }

    private static final class ActiveVm {
        private final UUID owner;
        private final ServerLevel world;
        private final SpellVm vm;
        private final String label;
        private final boolean chargeMana;
        private final VmPresentationBridge presentation;
        private final Vm2CircleCompilation compilation;
        private final List<TickResult> trace;
        private final BiConsumer<ServerPlayer, List<SemanticInstruction>> semanticExecutor;
        private final List<SemanticInstruction> semanticSteps;
        private CastingResourceService.Reservation reservation;
        private Consumer<ResourceEscrow.Outcome> terminal;
        private boolean terminalNotified;
        private int remainingCastTicks;

        private ActiveVm(UUID owner, ServerLevel world, SpellVm vm, String label,
                boolean chargeMana, VmPresentationBridge presentation,
                Vm2CircleCompilation compilation, List<TickResult> trace,
                BiConsumer<ServerPlayer, List<SemanticInstruction>> semanticExecutor,
                List<SemanticInstruction> semanticSteps) {
            this.owner = owner;
            this.world = world;
            this.vm = vm;
            this.label = label;
            this.chargeMana = chargeMana;
            this.presentation = presentation;
            this.compilation = compilation;
            this.trace = trace;
            this.semanticExecutor = semanticExecutor;
            this.semanticSteps = semanticSteps;
        }
    }

    private static final class ActiveForce {
        private final UUID owner;
        private final ServerLevel world;
        private final WorldEffect effect;
        private int remaining;
        private int pathIndex;

        private ActiveForce(UUID owner, ServerLevel world, WorldEffect effect, int remaining) {
            this.owner = owner;
            this.world = world;
            this.effect = effect;
            this.remaining = remaining;
        }
    }

    private static final class MinecraftWorldAccess implements WorldAccess {
        private final ServerPlayer caster;
        private final ServerLevel world;

        private MinecraftWorldAccess(ServerPlayer caster) {
            this.caster = caster;
            this.world = caster.serverLevel();
        }

        @Override
        public Optional<EntitySnapshot> entity(String id) {
            return Optional.ofNullable(find(world, id)).map(this::snapshot);
        }

        @Override
        public Optional<RaycastHit> raycast(
                Vector3 origin, Vector3 normalizedDirection, double maxDistance, SelectionFilter filter) {
            Vec3 start = toMinecraft(origin);
            Vec3 end = start.add(toMinecraft(normalizedDirection).scale(maxDistance));
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, start, end,
                    new AABB(start, end).inflate(1.0), entity -> matches(entity, filter),
                    maxDistance * maxDistance);
            if (hit == null) return Optional.empty();
            HitResult blockHit = world.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (blockHit.getType() != HitResult.Type.MISS
                    && start.distanceToSqr(blockHit.getLocation())
                            + 1.0e-9 < start.distanceToSqr(hit.getLocation())) {
                return Optional.empty();
            }
            return Optional.of(new RaycastHit(toCore(hit.getLocation()),
                    Optional.of(snapshot(hit.getEntity())), start.distanceTo(hit.getLocation())));
        }

        @Override
        public List<EntitySnapshot> select(
                Vector3 center, double radius, SelectionFilter filter) {
            Vec3 point = toMinecraft(center);
            return world.getEntities(filter.includeCaster() ? null : caster,
                            new AABB(point, point).inflate(radius), entity ->
                                    entity.distanceToSqr(point) <= radius * radius
                                            && matches(entity, filter))
                    .stream().map(this::snapshot).toList();
        }

        private boolean matches(Entity entity, SelectionFilter filter) {
            if (!filter.includeCaster() && entity == caster) return false;
            Set<String> tags = tags(entity);
            if (!tags.containsAll(filter.requiredTags())) return false;
            return filter.kind().map(kind -> tags.contains(kind)
                    || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(kind)).orElse(true);
        }

        private EntitySnapshot snapshot(Entity entity) {
            double mass = Math.max(0.1, entity.getBbWidth() * entity.getBbHeight());
            return new EntitySnapshot(entity.getStringUUID(), toCore(entity.position()), mass,
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), tags(entity));
        }

        private Set<String> tags(Entity entity) {
            Set<String> tags = new HashSet<>();
            tags.add("entity");
            if (entity instanceof LivingEntity) tags.add("living");
            if (entity instanceof Monster) tags.add("hostile");
            if (entity instanceof ServerPlayer) tags.add("player");
            return Set.copyOf(tags);
        }
    }
}
