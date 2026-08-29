package vectorregnum.neoforge.effect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import vectorregnum.core.EffectCommand;
import vectorregnum.core.WildMagicCategory;
import vectorregnum.core.effect.PersistentEffectContract;
import vectorregnum.core.effect.PersistentEffectLedger;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.WorldEffect;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.SpellVisualManager;
import vectorregnum.neoforge.VectorRegnumMod;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;

/** Server-thread adapter for durable upkeep, restart reconciliation, and cleanup. */
public final class PersistentEffectService {
    public static final int UPKEEP_INTERVAL_TICKS = 20;
    private static final int MAX_STATUS_DURATION_TICKS = 1_200;

    private PersistentEffectService() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) tickLevel(level);
    }

    public static Batch begin(ServerPlayer owner, Program program,
            CastingResourceService.Reservation reservation, long seed) {
        Objects.requireNonNull(program, "program");
        return new Batch(owner, reservation.funded() ? reservation.id() : UUID.randomUUID(),
                programHash(program), seed);
    }

    public static List<PersistentEffectContract> ownedBy(ServerPlayer player) {
        return PersistentEffectSavedData.get(player.serverLevel()).ledger()
                .forOwner(player.getUUID());
    }

    public static int activeCount(ServerLevel level) {
        return PersistentEffectSavedData.get(level).ledger().entries().size();
    }

    /** Exposed for production GameTests; normal runtime reaches this through the tick event. */
    public static void tickLevelForTesting(ServerLevel level) {
        tickLevel(level);
    }

    /**
     * Scheduled ticks remain a restart-safe fallback. A managed block waits for
     * its ownership ledger; an unmanaged legacy block follows the old removal path.
     */
    public static boolean onScheduledBlockTick(ServerLevel level, BlockPos pos, Block expected) {
        String prefix = BlockHandle.prefix(pos, expected);
        boolean managed = PersistentEffectSavedData.get(level).ledger().entries().values().stream()
                .filter(entry -> entry.state() != PersistentEffectContract.State.CLEANED)
                .flatMap(entry -> entry.handles().stream())
                .anyMatch(handle -> handle.startsWith(prefix));
        if (managed) return false;
        if (level.getBlockState(pos).is(expected)) return level.removeBlock(pos, false);
        return false;
    }

    private static void tickLevel(ServerLevel level) {
        PersistentEffectSavedData data = PersistentEffectSavedData.get(level);
        PersistentEffectLedger ledger = data.ledger();
        long now = level.getGameTime();
        for (UUID effectId : List.copyOf(ledger.entries().keySet())) {
            PersistentEffectContract before = ledger.get(effectId);
            if (before == null) continue;
            List<Handle> handles;
            try {
                if (!before.dimension().equals(level.dimension().location().toString())) {
                    throw new IllegalArgumentException(
                            "persistent-effect contract is stored in the wrong dimension");
                }
                handles = before.handles().stream()
                        .map(encoded -> decodeHandle(encoded, before.effectiveDeadlineTick()))
                        .toList();
                if (handles.stream().anyMatch(handle -> handle.deadlineTick() <= before.startTick()
                        || handle.deadlineTick() > before.effectiveDeadlineTick())) {
                    throw new IllegalArgumentException("persistent-effect handle deadline is outside its contract");
                }
            } catch (RuntimeException exception) {
                // Encoded handles are validated before normal persistence. If a
                // save is nevertheless corrupted, quarantine the record instead
                // of retrying an undecodable cleanup forever. Block scheduled
                // ticks and finite vanilla status durations remain the fallback.
                VectorRegnumMod.LOGGER.error("Quarantining malformed persistent effect {}", effectId, exception);
                PersistentEffectLedger.Change cleaned = ledger.completeCleanup(effectId);
                ledger = cleaned.ledger();
                PersistentEffectLedger.Change removed = ledger.removeCleaned(effectId);
                ledger = removed.ledger();
                data.replace(ledger);
                continue;
            }
            boolean loaded = handles.stream().allMatch(handle -> handle.loaded(level));
            PersistentEffectLedger.Reconciliation reconciliation = ledger.reconcile(effectId, now, loaded);
            ledger = reconciliation.ledger();
            data.replace(ledger);
            PersistentEffectContract current = reconciliation.contract();
            switch (reconciliation.decision()) {
                case MISSING, WAITING_UNLOADED -> { }
                case ACTIVE, UPKEEP_PAID -> {
                    if (loaded && current != null) {
                        PersistentEffectContract ticked = tickHandles(level, current, handles);
                        if (ticked != current) {
                            PersistentEffectLedger.Change changed = ledger.replace(
                                    current.revision(), ticked);
                            ledger = changed.ledger();
                            data.replace(ledger);
                            current = ledger.get(effectId);
                        }
                    }
                    if (reconciliation.decision() == PersistentEffectLedger.Decision.UPKEEP_PAID) {
                        logPayment(level, current, reconciliation.upkeepDebited());
                    }
                }
                case NATURAL_CONCLUSION, CONCLUSION_PENDING_CLEANUP -> {
                    if (cleanup(level, handles)) {
                        PersistentEffectLedger.Change cleaned = ledger.completeCleanup(effectId);
                        ledger = cleaned.ledger();
                        data.replace(ledger);
                    }
                }
                case COLLAPSE_UNPAID, COLLAPSE_HARD_CAP -> {
                    // The collapsed state is persisted first. Emission and cleanup
                    // happen on the following tick, so a crash cannot orphan active state.
                }
                case COLLAPSE_PENDING_EMISSION -> {
                    // Emit from the already-durable collapsed state, then record
                    // delivery separately. A crash may replay the same bounded,
                    // deterministic event, but can never silently skip it.
                    emitCollapse(level, current, handles);
                    PersistentEffectLedger.Change emitted = ledger.completeCollapseEmission(effectId);
                    ledger = emitted.ledger();
                    data.replace(ledger);
                }
                case COLLAPSE_PENDING_CLEANUP -> {
                    if (cleanup(level, handles)
                            || current != null && now >= current.hardDeadlineTick()) {
                        PersistentEffectLedger.Change cleaned = ledger.completeCleanup(effectId);
                        ledger = cleaned.ledger();
                        data.replace(ledger);
                    }
                }
                case CLEANUP_PENDING_REMOVAL -> {
                    if (cleanup(level, handles)
                            || current != null && now >= current.hardDeadlineTick()) {
                        PersistentEffectLedger.Change removed = ledger.removeCleaned(effectId);
                        ledger = removed.ledger();
                        data.replace(ledger);
                    }
                }
            }
        }
    }

    private static PersistentEffectContract tickHandles(ServerLevel level,
            PersistentEffectContract contract,
            List<Handle> handles) {
        MinecraftServer server = level.getServer();
        ServerPlayer owner = server.getPlayerList().getPlayer(contract.ownerId());
        // Offline owners are never used as a gameplay authority. Prepaid upkeep
        // and deadline cleanup still reconcile, but active mutations pause until login.
        boolean ownerCanMutate = owner != null && owner.serverLevel() == level && owner.isAlive();
        long now = level.getGameTime();
        List<String> updated = new ArrayList<>(handles.size());
        for (Handle handle : handles) {
            Handle replacement = handle;
            if (now >= handle.deadlineTick()) handle.cleanup(level);
            else if (ownerCanMutate) replacement = handle.tick(level, owner);
            updated.add(replacement.encode());
        }
        return updated.equals(contract.handles()) ? contract : contract.withHandles(updated);
    }

    private static boolean cleanup(ServerLevel level, List<Handle> handles) {
        boolean complete = true;
        for (Handle handle : handles) {
            try {
                complete &= handle.cleanup(level);
            } catch (RuntimeException exception) {
                complete = false;
                VectorRegnumMod.LOGGER.error("Persistent-effect cleanup failed safely for {}",
                        handle.encode(), exception);
            }
        }
        return complete;
    }

    private static void emitCollapse(ServerLevel level, PersistentEffectContract contract,
            List<Handle> handles) {
        if (contract == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(contract.ownerId());
        Vec3 origin = handles.stream().map(handle -> handle.origin(level)).flatMap(Optional::stream)
                .findFirst().orElse(owner == null ? Vec3.ZERO : owner.position());
        if (owner == null || owner.serverLevel() != level) {
            // Offline policy: resolve once as a bounded world trace and durable
            // audit event. Never mutate a player from another dimension.
            vectorregnum.neoforge.presentation.ServerTraces.burstAll(level, List.of(origin),
                    vectorregnum.core.presentation.PresentationParticleStyle.EXPLOSION,
                    vectorregnum.core.presentation.PresentationElement.ARCANE,
                    1.0F, 1.0F, 16);
            VectorRegnumMod.LOGGER.warn(
                    "PERSISTENT_EFFECT_COLLAPSE id={} owner={} seed={} reason=unpaid_or_hard_cap "
                            + "handles={} delivery=offline_world_trace",
                    contract.effectId(), contract.ownerId(), contract.collapseSeed(), handles.size());
            return;
        }
        VectorRegnumMod.LOGGER.warn(
                "PERSISTENT_EFFECT_COLLAPSE id={} owner={} seed={} reason=unpaid_or_hard_cap "
                        + "handles={} delivery=owner",
                contract.effectId(), contract.ownerId(), contract.collapseSeed(), handles.size());
        EffectCommand.WildMagic command = new EffectCommand.WildMagic(
                owner.getStringUUID(), WildMagicCategory.VIOLENT_MISCAST,
                Optional.of(new vectorregnum.core.Vec3(origin.x, origin.y, origin.z)),
                Optional.empty(), Optional.empty(), 0,
                "Persistent magic failed its upkeep or conclusion contract",
                contract.collapseSeed());
        SpellVisualManager.apply(owner, command);
        owner.sendSystemMessage(Component.literal("WILD MAGIC: persistent effect "
                        + shortId(contract.effectId()) + " collapsed and cleaned")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    private static void logPayment(ServerLevel level, PersistentEffectContract contract,
            double debit) {
        if (contract == null || debit <= 0.0) return;
        VectorRegnumMod.LOGGER.info(
                "PERSISTENT_UPKEEP_PAID id={} owner={} amount={} remaining={} next={}",
                contract.effectId(), contract.ownerId(), debit,
                contract.prepaidUpkeep(), contract.nextUpkeepTick());
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(contract.ownerId());
        if (owner != null && owner.serverLevel() == level) {
            owner.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                            "Effect %s upkeep %.2f μ • %.2f μ escrow left",
                            shortId(contract.effectId()), debit, contract.prepaidUpkeep()))
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    public static String programHash(Program program) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (var instruction : program.instructions()) {
                digest.update(instruction.toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static final class Batch {
        private final ServerPlayer owner;
        private final ServerLevel level;
        private final UUID effectId;
        private final String programHash;
        private final long seed;
        private final Map<String, Handle> handles = new LinkedHashMap<>();
        private boolean terminal;

        private Batch(ServerPlayer owner, UUID effectId, String programHash, long seed) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.level = owner.serverLevel();
            this.effectId = Objects.requireNonNull(effectId, "effectId");
            this.programHash = Objects.requireNonNull(programHash, "programHash");
            this.seed = seed;
        }

        public UUID effectId() {
            return effectId;
        }

        public boolean hasHandles() {
            return !handles.isEmpty();
        }

        public void trackBlock(BlockPos pos, Block expected, int durationTicks) {
            track(blockHandle(pos, expected, durationTicks), durationTicks);
        }

        public void prepareBlock(BlockPos pos, Block expected, int durationTicks) {
            ensureTrackable(blockHandle(pos, expected, durationTicks), durationTicks);
        }

        public void trackStatus(LivingEntity target, MobEffect effect,
                int amplifier, int durationTicks) {
            int bounded = Math.clamp(durationTicks, 1, MAX_STATUS_DURATION_TICKS);
            track(statusHandle(target, effect, amplifier, bounded), bounded);
        }

        public void prepareStatus(LivingEntity target, MobEffect effect,
                int amplifier, int durationTicks) {
            int bounded = Math.clamp(durationTicks, 1, MAX_STATUS_DURATION_TICKS);
            ensureTrackable(statusHandle(target, effect, amplifier, bounded), bounded);
        }

        public void trackForce(WorldEffect effect) {
            if (effect.durationTicks() <= 1 || effect instanceof WorldEffect.Impulse
                    || effect instanceof WorldEffect.SemanticStep) return;
            track(forceHandle(effect), effect.durationTicks());
        }

        public void prepareForce(WorldEffect effect) {
            if (effect.durationTicks() <= 1 || effect instanceof WorldEffect.Impulse
                    || effect instanceof WorldEffect.SemanticStep) return;
            ensureTrackable(forceHandle(effect), effect.durationTicks());
        }

        /** Preflights, applies, and retains the same continuing-force handle. */
        public void applyForce(WorldEffect effect) {
            if (effect.durationTicks() <= 1 || effect instanceof WorldEffect.Impulse
                    || effect instanceof WorldEffect.SemanticStep) return;
            ForceHandle handle = forceHandle(effect);
            ensureTrackable(handle, effect.durationTicks());
            Handle applied = handle.tick(level, owner);
            handles.putIfAbsent(applied.encode(), applied);
        }

        /** Keeps continuing effects live while their owning VM is still running. */
        public void tickPending() {
            if (terminal) return;
            if (owner.serverLevel() != level) {
                throw new IllegalStateException("persistent-effect owner changed dimension while casting");
            }
            long now = level.getGameTime();
            Map<String, Handle> updated = new LinkedHashMap<>();
            for (Handle handle : handles.values()) {
                if (now >= handle.deadlineTick()) {
                    handle.cleanup(level);
                    continue;
                }
                Handle replacement = handle.tick(level, owner);
                if (replacement.deadlineTick() > now) {
                    updated.putIfAbsent(replacement.encode(), replacement);
                }
            }
            handles.clear();
            handles.putAll(updated);
        }

        private long deadline(int durationTicks) {
            return Math.addExact(level.getGameTime(), durationTicks);
        }

        private BlockHandle blockHandle(BlockPos pos, Block expected, int durationTicks) {
            return new BlockHandle(pos.immutable(), BuiltInRegistries.BLOCK.getKey(expected),
                    deadline(durationTicks));
        }

        private StatusHandle statusHandle(LivingEntity target, MobEffect effect,
                int amplifier, int durationTicks) {
            return new StatusHandle(target.getUUID(), BuiltInRegistries.MOB_EFFECT.getKey(effect),
                    amplifier, target instanceof ServerPlayer,
                    target.blockPosition().immutable(), deadline(durationTicks));
        }

        private ForceHandle forceHandle(WorldEffect effect) {
            Entity entity = find(level, effect.entityId());
            if (entity == null) throw new IllegalArgumentException("persistent force target disappeared");
            if (effect instanceof WorldEffect.KeepDistance keep
                    && find(level, keep.targetId()) == null) {
                throw new IllegalArgumentException("persistent force comparison target disappeared");
            }
            return ForceHandle.from(effect, entity.blockPosition(), entity instanceof ServerPlayer,
                    deadline(effect.durationTicks()));
        }

        private void track(Handle handle, int durationTicks) {
            ensureTrackable(handle, durationTicks);
            handles.putIfAbsent(handle.encode(), handle);
        }

        private void ensureTrackable(Handle handle, int durationTicks) {
            if (terminal) throw new IllegalStateException("persistent-effect batch is terminal");
            if (durationTicks < 1 || durationTicks > MAX_STATUS_DURATION_TICKS) {
                throw new IllegalArgumentException("persistent-effect duration is outside 1..1200 ticks");
            }
            if (!handles.containsKey(handle.encode())
                    && handles.size() >= PersistentEffectContract.MAX_HANDLES) {
                throw new IllegalStateException("persistent-effect handle cap reached");
            }
        }

        /** Commits only after semantic execution succeeds. */
        public boolean commit(CastingResourceService.Reservation reservation) {
            if (terminal) return false;
            long commitTick = level.getGameTime();
            List<Handle> activeHandles = handles.values().stream()
                    .filter(handle -> handle.deadlineTick() > commitTick).toList();
            handles.values().stream().filter(handle -> handle.deadlineTick() <= commitTick)
                    .forEach(handle -> handle.cleanup(level));
            if (activeHandles.isEmpty()) {
                terminal = true;
                return false;
            }
            if (owner.serverLevel() != level) {
                rollbackHandles();
                terminal = true;
                throw new IllegalStateException("persistent-effect owner changed dimension before commit");
            }
            PersistentEffectSavedData data = PersistentEffectSavedData.get(level);
            if (data.ledger().entries().size() >= PersistentEffectLedger.MAX_WORLD_EFFECTS
                    || data.ledger().get(effectId) != null) {
                throw new IllegalStateException("persistent-effect ledger cannot accept this cast");
            }
            double upkeep = CastingResourceService.claimUpkeep(reservation);
            long natural = activeHandles.stream().mapToLong(Handle::deadlineTick).max().orElseThrow();
            long hard = Math.addExact(commitTick, PersistentEffectContract.MAX_LIFETIME_TICKS);
            PersistentEffectContract contract = PersistentEffectContract.active(
                    effectId, owner.getUUID(), programHash,
                    level.dimension().location().toString(), commitTick,
                    natural, hard, UPKEEP_INTERVAL_TICKS, upkeep, seed,
                    activeHandles.stream().map(Handle::encode).toList());
            try {
                PersistentEffectLedger.Change registered = data.ledger().register(contract);
                if (!registered.changed()) {
                    throw new IllegalStateException("persistent-effect registration did not change the ledger");
                }
                persistRegistration(data, registered.ledger(), level.getDataStorage()::save);
            } catch (RuntimeException exception) {
                CastingResourceService.releaseUpkeepClaim(reservation);
                throw exception;
            }
            terminal = true;
            owner.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                            "Persistent effect %s • endpoint %dt • prepaid upkeep %.2f μ",
                            shortId(effectId), natural - commitTick, upkeep))
                    .withStyle(ChatFormatting.GOLD), false);
            VectorRegnumMod.LOGGER.info(
                    "PERSISTENT_EFFECT_REGISTERED id={} owner={} handles={} deadline={} upkeep={} program={}",
                    effectId, owner.getUUID(), activeHandles.size(), natural, upkeep, programHash);
            return true;
        }

        public void rollback() {
            if (terminal) return;
            terminal = true;
            rollbackHandles();
        }

        private void rollbackHandles() {
            cleanup(level, List.copyOf(handles.values()));
        }
    }

    static void persistRegistration(PersistentEffectSavedData data,
            PersistentEffectLedger replacement, Runnable save) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(save, "save");
        PersistentEffectLedger previous = data.ledger();
        if (!data.replace(replacement)) {
            throw new IllegalStateException("persistent-effect registration did not replace SavedData");
        }
        try {
            save.run();
        } catch (RuntimeException exception) {
            data.replace(previous);
            try {
                save.run();
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    private sealed interface Handle permits BlockHandle, StatusHandle, ForceHandle {
        String encode();
        long deadlineTick();
        boolean loaded(ServerLevel level);
        Handle tick(ServerLevel level, ServerPlayer owner);
        boolean cleanup(ServerLevel level);
        Optional<Vec3> origin(ServerLevel level);
    }

    private record BlockHandle(BlockPos pos, ResourceLocation expected,
            long deadlineTick) implements Handle {
        private static String prefix(BlockPos pos, Block block) {
            return "b|" + pos.getX() + '|' + pos.getY() + '|' + pos.getZ() + '|'
                    + BuiltInRegistries.BLOCK.getKey(block) + '|';
        }

        @Override public String encode() {
            return "b|" + pos.getX() + '|' + pos.getY() + '|' + pos.getZ() + '|'
                    + expected + '|' + deadlineTick;
        }
        @Override public boolean loaded(ServerLevel level) { return level.hasChunkAt(pos); }
        @Override public Handle tick(ServerLevel level, ServerPlayer owner) { return this; }
        @Override public boolean cleanup(ServerLevel level) {
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(expected)) {
                level.removeBlock(pos, false);
            }
            return true;
        }
        @Override public Optional<Vec3> origin(ServerLevel level) { return Optional.of(Vec3.atCenterOf(pos)); }
    }

    private record StatusHandle(UUID entityId, ResourceLocation effectId,
            int amplifier, boolean playerTarget, BlockPos lastPos,
            long deadlineTick) implements Handle {
        @Override public String encode() {
            return "s|" + entityId + '|' + effectId + '|' + amplifier + '|'
                    + (playerTarget ? "1" : "0") + '|'
                    + lastPos.getX() + '|' + lastPos.getY() + '|' + lastPos.getZ()
                    + '|' + deadlineTick;
        }
        @Override public boolean loaded(ServerLevel level) {
            if (playerTarget) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entityId);
                if (player == null || player.serverLevel() != level) return true;
                return level.hasChunkAt(player.blockPosition());
            }
            Entity current = level.getEntity(entityId);
            return current == null ? level.hasChunkAt(lastPos)
                    : level.hasChunkAt(current.blockPosition());
        }
        @Override public Handle tick(ServerLevel level, ServerPlayer owner) { return this; }
        @Override public boolean cleanup(ServerLevel level) {
            Entity entity;
            if (playerTarget) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entityId);
                if (player == null || player.serverLevel() != level) return false;
                entity = player;
            } else {
                entity = level.getEntity(entityId);
            }
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(effectId).orElse(null);
            if (effect == null) return true;
            if (!(entity instanceof LivingEntity living)) return true;
            var current = living.getEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
            long remaining = Math.max(0L, deadlineTick - living.level().getGameTime());
            if (current != null && current.getAmplifier() == amplifier
                    && current.getDuration() <= remaining + 2L) {
                living.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
            }
            return true;
        }
        @Override public Optional<Vec3> origin(ServerLevel level) {
            Entity entity = playerTarget
                    ? level.getServer().getPlayerList().getPlayer(entityId)
                    : level.getEntity(entityId);
            if (entity != null && entity.level() != level) entity = null;
            return Optional.of(entity == null ? Vec3.atCenterOf(lastPos) : entity.position());
        }
    }

    private record ForceHandle(String kind, UUID entityId, UUID otherId,
            boolean playerTarget, BlockPos lastPos, List<Double> values,
            long deadlineTick, int pathIndex) implements Handle {
        private static ForceHandle from(WorldEffect effect, BlockPos lastPos,
                boolean playerTarget, long deadlineTick) {
            return switch (effect) {
                case WorldEffect.Acceleration value -> new ForceHandle("acceleration",
                        UUID.fromString(value.entityId()), null, playerTarget, lastPos,
                        vector(value.acceleration()), deadlineTick, 0);
                case WorldEffect.Damping value -> new ForceHandle("damping",
                        UUID.fromString(value.entityId()), null, playerTarget, lastPos,
                        List.of(value.factor()), deadlineTick, 0);
                case WorldEffect.MoveToward value -> new ForceHandle("move_toward",
                        UUID.fromString(value.entityId()), null, playerTarget, lastPos,
                        List.of(value.point().x(), value.point().y(), value.point().z(), value.speed()),
                        deadlineTick, 0);
                case WorldEffect.KeepDistance value -> new ForceHandle("keep_distance",
                        UUID.fromString(value.entityId()), UUID.fromString(value.targetId()),
                        playerTarget, lastPos, List.of(value.distance()), deadlineTick, 0);
                case WorldEffect.FollowPath value -> {
                    List<Double> values = new ArrayList<>();
                    values.add(value.speed());
                    for (Vector3 point : value.points()) {
                        values.add(point.x()); values.add(point.y()); values.add(point.z());
                    }
                    yield new ForceHandle("follow_path", UUID.fromString(value.entityId()),
                            null, playerTarget, lastPos, List.copyOf(values), deadlineTick, 0);
                }
                default -> throw new IllegalArgumentException("world effect does not continue");
            };
        }

        private static List<Double> vector(Vector3 value) {
            return List.of(value.x(), value.y(), value.z());
        }

        @Override public String encode() {
            StringBuilder encoded = new StringBuilder("f|").append(kind).append('|')
                    .append(entityId).append('|').append(otherId == null ? "-" : otherId)
                    .append('|').append(playerTarget ? "1" : "0")
                    .append('|').append(lastPos.getX()).append('|').append(lastPos.getY())
                    .append('|').append(lastPos.getZ()).append('|').append(deadlineTick)
                    .append('|').append(pathIndex);
            for (double value : values) encoded.append('|').append(Double.toString(value));
            return encoded.toString();
        }

        @Override public boolean loaded(ServerLevel level) {
            Entity current = level.getEntity(entityId);
            boolean primaryLoaded = current == null
                    ? level.hasChunkAt(lastPos) : level.hasChunkAt(current.blockPosition());
            return primaryLoaded;
        }

        @Override public Handle tick(ServerLevel level, ServerPlayer owner) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || !SpellSecurityPolicy.canAffectEntity(owner, entity)) {
                return concludeSoon(level);
            }
            ForceHandle replacement = this;
            switch (kind) {
                case "acceleration" -> entity.setDeltaMovement(entity.getDeltaMovement()
                        .add(clamped(new Vec3(values.get(0), values.get(1), values.get(2)), 1.0)));
                case "damping" -> entity.setDeltaMovement(entity.getDeltaMovement().scale(values.getFirst()));
                case "move_toward" -> moveToward(entity,
                        new Vec3(values.get(0), values.get(1), values.get(2)), values.get(3));
                case "keep_distance" -> {
                    Entity target = otherId == null ? null : level.getEntity(otherId);
                    if (target == null || !SpellSecurityPolicy.canAffectEntity(owner, target)) {
                        return concludeSoon(level);
                    }
                    Vec3 delta = entity.position().subtract(target.position());
                    double current = delta.length();
                    if (current > 1.0e-6) {
                        double correction = Math.clamp((values.getFirst() - current) * 0.08, -0.5, 0.5);
                        entity.setDeltaMovement(entity.getDeltaMovement().add(delta.normalize().scale(correction)));
                    }
                }
                case "follow_path" -> replacement = followPath(entity);
                default -> throw new IllegalStateException("unknown persistent force " + kind);
            }
            entity.hasImpulse = true;
            return replacement;
        }

        private ForceHandle concludeSoon(ServerLevel level) {
            long replacement = Math.min(deadlineTick, level.getGameTime() + 1L);
            return replacement == deadlineTick ? this : new ForceHandle(kind, entityId, otherId,
                    playerTarget, lastPos, values, replacement, pathIndex);
        }

        private ForceHandle followPath(Entity entity) {
            double speed = values.getFirst();
            Vec3 current = entity.position();
            int pointCount = (values.size() - 1) / 3;
            int index = Math.clamp(pathIndex, 0, pointCount - 1);
            Vec3 target = point(index);
            while (index < pointCount - 1 && target.distanceToSqr(current) < 0.16) {
                index++;
                target = point(index);
            }
            if (target.distanceToSqr(current) >= 0.16) moveToward(entity, target, speed);
            else entity.setDeltaMovement(Vec3.ZERO);
            return index == pathIndex ? this : new ForceHandle(kind, entityId, otherId,
                    playerTarget, lastPos, values, deadlineTick, index);
        }

        private Vec3 point(int index) {
            int offset = 1 + index * 3;
            return new Vec3(values.get(offset), values.get(offset + 1), values.get(offset + 2));
        }

        @Override public boolean cleanup(ServerLevel level) { return true; }
        @Override public Optional<Vec3> origin(ServerLevel level) {
            Entity entity = level.getEntity(entityId);
            return Optional.of(entity == null ? Vec3.atCenterOf(lastPos) : entity.position());
        }
    }

    private static Handle decodeHandle(String encoded, long legacyDeadline) {
        String[] part = encoded.split("\\|", -1);
        try {
            return switch (part[0]) {
                case "b" -> {
                    if (part.length != 5 && part.length != 6) {
                        throw new IllegalArgumentException("handle field count mismatch");
                    }
                    yield new BlockHandle(pos(part, 1), ResourceLocation.parse(part[4]),
                            part.length == 6 ? Long.parseLong(part[5]) : legacyDeadline);
                }
                case "s" -> {
                    if (part.length != 8 && part.length != 9) {
                        throw new IllegalArgumentException("handle field count mismatch");
                    }
                    yield new StatusHandle(UUID.fromString(part[1]), ResourceLocation.parse(part[2]),
                            Integer.parseInt(part[3]), parseFlag(part[4]), pos(part, 5),
                            part.length == 9 ? Long.parseLong(part[8]) : legacyDeadline);
                }
                case "f" -> decodeForce(part, legacyDeadline);
                default -> throw new IllegalArgumentException("unknown persistent-effect handle type");
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed persistent-effect handle", exception);
        }
    }

    private static ForceHandle decodeForce(String[] part, long legacyDeadline) {
        if (part.length < 9) throw new IllegalArgumentException("short persistent force handle");
        String kind = part[1];
        UUID entity = UUID.fromString(part[2]);
        UUID other = part[3].equals("-") ? null : UUID.fromString(part[3]);
        boolean playerTarget = parseFlag(part[4]);
        BlockPos pos = pos(part, 5);
        long deadline = legacyDeadline;
        int valuesStart = 8;
        try {
            deadline = Long.parseLong(part[8]);
            valuesStart = 9;
        } catch (NumberFormatException legacyEncoding) {
            // Priority 23's pre-checkpoint development codec stored no
            // per-handle deadline. Its contract endpoint is the safe fallback.
        }
        int pathIndex = 0;
        try {
            pathIndex = Integer.parseInt(part[valuesStart]);
            valuesStart++;
        } catch (NumberFormatException legacyEncoding) {
            // Older force handles restart from the first ordered waypoint.
        }
        List<Double> values = new ArrayList<>();
        for (int index = valuesStart; index < part.length; index++) {
            values.add(Double.parseDouble(part[index]));
        }
        int expected = switch (kind) {
            case "acceleration", "move_toward" -> kind.equals("acceleration") ? 3 : 4;
            case "damping", "keep_distance" -> 1;
            case "follow_path" -> values.size() >= 4 && (values.size() - 1) % 3 == 0 ? values.size() : -1;
            default -> -1;
        };
        if (expected < 0 || values.size() != expected) {
            throw new IllegalArgumentException("persistent force payload length mismatch");
        }
        int pointCount = kind.equals("follow_path") ? (values.size() - 1) / 3 : 0;
        if ((kind.equals("follow_path") && (pathIndex < 0 || pathIndex >= pointCount))
                || (!kind.equals("follow_path") && pathIndex != 0)) {
            throw new IllegalArgumentException("persistent force path index is outside its bounds");
        }
        values.forEach(value -> {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite force value");
        });
        return new ForceHandle(kind, entity, other, playerTarget, pos,
                List.copyOf(values), deadline, pathIndex);
    }

    private static BlockPos pos(String[] parts, int offset) {
        return new BlockPos(Integer.parseInt(parts[offset]), Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private static boolean parseFlag(String encoded) {
        if (encoded.equals("1")) return true;
        if (encoded.equals("0")) return false;
        throw new IllegalArgumentException("handle boolean is not 0 or 1");
    }

    private static Entity find(ServerLevel level, String id) {
        try {
            return level.getEntity(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Vec3 clamped(Vec3 vector, double maximum) {
        double length = vector.length();
        return length > maximum ? vector.scale(maximum / length) : vector;
    }

    private static void moveToward(Entity entity, Vec3 target, double speed) {
        Vec3 delta = target.subtract(entity.position());
        if (delta.lengthSqr() > 1.0e-8) {
            entity.setDeltaMovement(delta.normalize().scale(Math.min(4.0, speed)));
        }
    }
}
