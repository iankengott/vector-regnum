package vectorregnum.neoforge.api.v1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import vectorregnum.api.v1.ActionResult;
import vectorregnum.api.v1.CastContext;
import vectorregnum.api.v1.CastModifier;
import vectorregnum.api.v1.CastParameters;
import vectorregnum.api.v1.DisruptionRequest;
import vectorregnum.api.v1.DisruptionResult;
import vectorregnum.api.v1.IntegrationRegistry;
import vectorregnum.api.v1.ManaRegionSnapshot;
import vectorregnum.api.v1.PlayerMagicSnapshot;
import vectorregnum.api.v1.StoryEvent;
import vectorregnum.api.v1.VectorRegnumApiV1;
import vectorregnum.core.Element;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.security.MechanicDecision;
import vectorregnum.core.security.MechanicRequest;
import vectorregnum.core.security.SpellDisruptionPolicy;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.PlayerAttachmentContent;
import vectorregnum.neoforge.multiplayer.ClaimLedger;
import vectorregnum.neoforge.multiplayer.MultiplayerLifecycleService;
import vectorregnum.neoforge.multiplayer.SpellSecurityPolicy;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlock;
import vectorregnum.neoforge.progression.ManaReservoirBlockEntity;
import vectorregnum.neoforge.progression.ProgressionData;
import vectorregnum.neoforge.progression.ProgressionUnlock;

/**
 * The optional NeoForge server adapter for Vector-Regnum's loader-neutral v1
 * integration API.
 *
 * <p>This class deliberately exposes observations and bounded requests only.
 * It never returns a VM, attachment, block entity, or other mutable game
 * object to a companion. Calls which can inspect or mutate a player/world are
 * accepted only from the owning Minecraft server thread.</p>
 */
public final class VectorRegnumApi {
    public static final int VERSION = VectorRegnumApiV1.VERSION;
    public static final int MAX_QUERY_RADIUS = ManaRegionSnapshot.MAX_QUERY_RADIUS;
    public static final int MAX_QUERY_ENTRIES = ManaRegionSnapshot.MAX_QUERY_ENTRIES;
    private static final double DISRUPTION_RANGE = 4.0;

    private VectorRegnumApi() {
    }

    /** Returns the process-wide optional registry used by companion mods. */
    public static IntegrationRegistry registry() {
        return VectorRegnumApiV1.registry();
    }

    /**
     * Reads the immutable projection of one player.  Natural identity is
     * resolved through the optional Origins hook before the deterministic
     * Vector-Regnum fallback is committed.
     */
    public static PlayerMagicSnapshot playerSnapshot(ServerPlayer player) {
        requirePlayer(player);
        Element natural = resolveNaturalElement(player);
        return new PlayerMagicSnapshot(player.getUUID(), natural.id(),
                ManaData.channelAffinity(player).getSerializedName(),
                ProgressionData.get(player).ids());
    }

    /**
     * Resolves and, on the first authoritative call, persists the one permanent
     * natural element.  Invalid provider output is already isolated by the
     * loader-neutral registry; the UUID selector remains the deterministic
     * fail-closed fallback.
     */
    public static Element resolveNaturalElement(ServerPlayer player) {
        requirePlayer(player);
        String stored = player.getData(PlayerAttachmentContent.NATURAL_ELEMENT);
        Element existing = Element.fromId(stored).filter(Element::isNatural).orElse(null);
        if (existing != null) {
            if (!existing.id().equalsIgnoreCase(stored)) {
                player.setData(PlayerAttachmentContent.NATURAL_ELEMENT,
                        existing.id().toUpperCase(Locale.ROOT));
            }
            return existing;
        }

        Element selected = registry().naturalElement(player.getUUID())
                .flatMap(Element::fromId)
                .filter(Element::isNatural)
                .orElseGet(() -> vectorregnum.core.NaturalElementSelector.select(player.getUUID()));
        player.setData(PlayerAttachmentContent.NATURAL_ELEMENT,
                selected.id().toUpperCase(Locale.ROOT));
        return selected;
    }

    /**
     * Grants exactly one known Vector-Regnum research unlock.  Unknown IDs and
     * wrong-thread calls fail closed without touching player state.  Repeating
     * an already granted unlock is idempotently accepted.
     */
    public static ActionResult grantUnlock(ServerPlayer player, String unlockId) {
        if (player == null) {
            return ActionResult.UNAVAILABLE;
        }
        if (unlockId == null || unlockId.isBlank()) {
            return ActionResult.UNKNOWN_ID;
        }
        if (!isServerThread(player.serverLevel())) {
            return ActionResult.WRONG_THREAD;
        }
        ProgressionUnlock unlock;
        try {
            unlock = ProgressionUnlock.byId(unlockId.toLowerCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return ActionResult.UNKNOWN_ID;
        }
        boolean changed = ProgressionData.unlock(player, unlock);
        return changed ? ActionResult.APPLIED : ActionResult.ALREADY_PRESENT;
    }

    /**
     * Applies all registered modifiers to a cast cost, then restores the
     * server's central policy floors.  Reagent discount policy is intentionally
     * left to CastingResourceService.quote after this adapter returns.
     */
    public static CastCost applyCastModifiers(ServerPlayer player, CastContext context,
            CastCost baseline) {
        requirePlayer(player);
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(baseline, "baseline");
        if (!player.getUUID().equals(context.playerId())) {
            throw new IllegalArgumentException("cast context player does not match the caller");
        }
        CastModifier modifier = registry().castModifier(context);
        CastParameters adjusted = modifier.apply(new CastParameters(
                baseline.mana(), baseline.castingTime(), baseline.upkeep(), baseline.instability()));
        CastCost floors = vectorregnum.neoforge.CastingResourceService.policy().floors();
        return new CastCost(
                Math.max(floors.mana(), adjusted.mana()),
                Math.max(floors.castingTime(), adjusted.castingTime()),
                Math.max(floors.upkeep(), adjusted.upkeep()),
                Math.max(floors.instability(), adjusted.instability()));
    }

    /** Builds the loader-neutral context for a normal authoritative cast. */
    public static CastCost applyCastModifiers(ServerPlayer player, String spellId,
            Element element, CastingMethod method, CastCost baseline) {
        requirePlayer(player);
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(method, "method");
        CastContext context = new CastContext(player.getUUID(), spellId, element.id(),
                method.stableId(),
                new CastParameters(baseline.mana(), baseline.castingTime(), baseline.upkeep(),
                        baseline.instability()), player.serverLevel().getGameTime());
        return applyCastModifiers(player, context, baseline);
    }

    /**
     * Revalidates one Combat disruption request at the server boundary and
     * asks the authoritative VM service to cancel the target.  No VM object or
     * cancellation collection escapes this method.
     */
    public static DisruptionResult disrupt(ServerPlayer attacker, ServerPlayer target,
            DisruptionRequest request) {
        if (attacker == null) {
            return disruption(DisruptionResult.Code.ATTACKER_NOT_FOUND,
                    "attacker is not a server player");
        }
        if (target == null) {
            return disruption(DisruptionResult.Code.TARGET_NOT_FOUND,
                    "target is not a server player");
        }
        if (!isServerThread(attacker.serverLevel()) || !isServerThread(target.serverLevel())) {
            return disruption(DisruptionResult.Code.ENGINE_FAILURE,
                    "disruption must run on the authoritative server thread");
        }
        if (request == null || !attacker.getUUID().equals(request.attackerId())
                || !target.getUUID().equals(request.targetId()) || attacker == target) {
            return disruption(DisruptionResult.Code.INVALID_REQUEST,
                    "disruption identities do not match the supplied players");
        }
        if (attacker.getServer() != target.getServer()) {
            return disruption(DisruptionResult.Code.WRONG_DIMENSION,
                    "players do not belong to the same server");
        }
        if (attacker.serverLevel() != target.serverLevel()) {
            return disruption(DisruptionResult.Code.WRONG_DIMENSION,
                    "disruption requires one dimension");
        }
        if (!attacker.serverLevel().isLoaded(attacker.blockPosition())
                || !attacker.serverLevel().isLoaded(target.blockPosition())) {
            return disruption(DisruptionResult.Code.TARGET_NOT_LOADED,
                    "attacker and target chunks must remain loaded");
        }
        double range = attacker.distanceTo(target);
        if (!Double.isFinite(range) || range > DISRUPTION_RANGE) {
            return disruption(DisruptionResult.Code.OUT_OF_RANGE,
                    "disruption exceeds the four-block range");
        }
        if (!attacker.hasLineOfSight(target)) {
            return disruption(DisruptionResult.Code.LINE_OF_SIGHT_BLOCKED,
                    "line of sight is blocked");
        }

        DisruptionResult.Code policyFailure = policyFailure(attacker, target);
        if (policyFailure != null) {
            return disruption(policyFailure, "shared spell security policy rejected disruption");
        }

        boolean active = NeoForgeVmService.hasActiveSpell(target.getUUID());
        MechanicRequest common = new MechanicRequest(
                vectorregnum.core.security.MechanicCapability.SPELL_DISRUPTION,
                range, 1, 1, true, true, true, true,
                attacker.getServer().isPvpAllowed(), friendlyFireAllowed(attacker, target), true);
        MechanicDecision decision = SpellDisruptionPolicy.evaluate(common, active,
                request.stanceReady(), request.weaponReady(),
                Math.toIntExact(request.timingWindowTicks()));
        if (!decision.allowed()) {
            return disruption(mapDecision(decision.code()), decision.reason());
        }
        if (!NeoForgeVmService.disruptOwner(target.getUUID(),
                "priority27 source " + request.sourceId())) {
            return disruption(DisruptionResult.Code.NO_ACTIVE_SPELL,
                    "the target spell ended before disruption was committed");
        }
        publishDisruptionEvent(attacker, target, request.sourceId());
        return disruption(DisruptionResult.Code.ACCEPTED, "disruption accepted");
    }

    /**
     * Summarizes positive mana in the requested cube without loading chunks.
     * Chunks and positions are traversed in deterministic ascending order;
     * after 256 entries the result is explicitly truncated.
     */
    public static ManaRegionSnapshot queryManaRegion(ServerLevel level, BlockPos center,
            int radius) {
        requireLevel(level);
        Objects.requireNonNull(center, "center");
        if (radius < 0 || radius > MAX_QUERY_RADIUS) {
            throw new IllegalArgumentException("radius must be between 0 and " + MAX_QUERY_RADIUS);
        }

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        TreeMap<String, Double> summary = new TreeMap<>();
        int entries = 0;
        boolean unloaded = false;
        boolean truncated = false;

        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        outer:
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    unloaded = true;
                    continue;
                }
                ArrayList<BlockEntity> candidates = new ArrayList<>(chunk.getBlockEntities().values());
                candidates.sort(Comparator.comparingLong(entity -> entity.getBlockPos().asLong()));
                for (BlockEntity candidate : candidates) {
                    BlockPos position = candidate.getBlockPos();
                    if (position.getX() < minX || position.getX() > maxX
                            || position.getY() < minY || position.getY() > maxY
                            || position.getZ() < minZ || position.getZ() > maxZ) {
                        continue;
                    }
                    if (entries >= MAX_QUERY_ENTRIES) {
                        truncated = true;
                        break outer;
                    }
                    entries++;
                    Entry entry = readManaEntry(level, position, candidate);
                    if (entry == null) continue;
                    summary.merge(entry.elementId(), entry.amount(), Double::sum);
                }
            }
        }
        return new ManaRegionSnapshot(level.dimension().location().toString(), center.getX(),
                center.getY(), center.getZ(), radius, summary, entries, unloaded, truncated);
    }

    /** Publishes a stable natural-identity observation. */
    public static StoryEvent publishIdentityEvent(ServerPlayer actor) {
        requirePlayer(actor);
        Element natural = resolveNaturalElement(actor);
        return publish(actor, stableEventId("identity", actor.getUUID().toString()), 0L,
                "vector_regnum:identity", actor.getUUID().toString(), natural.id(), "assigned");
    }

    /** Publishes a stable progression observation for one unlock ID. */
    public static StoryEvent publishProgressionEvent(ServerPlayer actor, String unlockId) {
        requirePlayer(actor);
        Objects.requireNonNull(unlockId, "unlockId");
        String normalized = unlockId.toLowerCase(Locale.ROOT);
        return publish(actor, stableEventId("progression", actor.getUUID() + ":" + normalized), 0L,
                "vector_regnum:progression", normalized,
                resolveNaturalElement(actor).id(), "unlocked");
    }

    /** Publishes the first revision for a spell execution ID. */
    public static StoryEvent publishSpellStartEvent(ServerPlayer actor, UUID spellId,
            String spellSubject, Element element) {
        requirePlayer(actor);
        Objects.requireNonNull(spellId, "spellId");
        Objects.requireNonNull(element, "element");
        return publish(actor, spellId, 0L, "vector_regnum:spell_start",
                safeSubject(spellSubject, spellId.toString()), element.id(), "started");
    }

    /** Publishes the next revision for the same spell execution ID. */
    public static StoryEvent publishSpellTerminalEvent(ServerPlayer actor, UUID spellId,
            String spellSubject, Element element, String outcome) {
        requirePlayer(actor);
        Objects.requireNonNull(spellId, "spellId");
        Objects.requireNonNull(element, "element");
        return publish(actor, spellId, 1L, "vector_regnum:spell_terminal",
                safeSubject(spellSubject, spellId.toString()), element.id(),
                safeOutcome(outcome, "terminal"));
    }

    /** Publishes a stable disruption observation keyed by the Combat source. */
    public static StoryEvent publishDisruptionEvent(ServerPlayer actor, ServerPlayer target,
            String sourceId) {
        requirePlayer(actor);
        requirePlayer(target);
        Objects.requireNonNull(sourceId, "sourceId");
        UUID eventId = stableEventId("disruption", actor.getUUID() + ":"
                + target.getUUID() + ":" + sourceId + ":"
                + actor.serverLevel().getGameTime());
        return publish(actor, eventId, 0L, "vector_regnum:disruption", target.getUUID().toString(),
                ManaData.channelAffinity(target).getSerializedName(), "accepted");
    }

    /** Publishes an already constructed event after checking its authoritative actor. */
    public static void publishStoryEvent(ServerPlayer actor, StoryEvent event) {
        requirePlayer(actor);
        Objects.requireNonNull(event, "event");
        if (!actor.getUUID().equals(event.actorId())) {
            throw new IllegalArgumentException("story event actor does not match the caller");
        }
        registry().publishStoryEvent(event);
    }

    private static StoryEvent publish(ServerPlayer actor, UUID eventId, long revision, String kind,
            String subject, String element, String outcome) {
        BlockPos position = actor.blockPosition();
        StoryEvent event = new StoryEvent(eventId, revision, kind, actor.serverLevel().getGameTime(),
                actor.getUUID(), actor.serverLevel().dimension().location().toString(),
                position.getX(), position.getY(), position.getZ(),
                safeSubject(subject, eventId.toString()), safeElement(element),
                safeOutcome(outcome, "observed"));
        registry().publishStoryEvent(event);
        return event;
    }

    private static String safeSubject(String subject, String fallback) {
        String value = subject == null || subject.isBlank() ? fallback : subject;
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.:/-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = fallback;
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private static String safeElement(String element) {
        if (element == null || element.isBlank()) return "";
        return Element.fromId(element).map(Element::id).orElse("arcane");
    }

    private static String safeOutcome(String outcome, String fallback) {
        if (outcome == null || outcome.isBlank()) return fallback;
        String normalized = outcome.toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_.:/-]{1,128}") ? normalized : fallback;
    }

    private static UUID stableEventId(String kind, String subject) {
        return UUID.nameUUIDFromBytes((kind + ":" + subject).getBytes(StandardCharsets.UTF_8));
    }

    private static Entry readManaEntry(ServerLevel level, BlockPos position, BlockEntity blockEntity) {
        var state = level.getBlockState(position);
        if (state.is(vectorregnum.neoforge.progression.ProgressionContent.manaCrystalNode())) {
            int charges = state.getValue(ManaCrystalNodeBlock.CHARGE);
            if (charges <= 0) return null;
            return new Entry(ManaAffinity.fromId(
                    state.getValue(ManaCrystalNodeBlock.AFFINITY).getSerializedName())
                    .orElse(ManaAffinity.ARCANE).canonical().element().id(),
                    (double) charges * ManaCrystalNodeBlock.MANA_PER_CHARGE);
        }
        if (blockEntity instanceof ManaReservoirBlockEntity reservoir
                && reservoir.stored() > 0) {
            return new Entry(reservoir.affinity().canonical().element().id(), reservoir.stored());
        }
        return null;
    }

    private static DisruptionResult.Code policyFailure(ServerPlayer attacker, ServerPlayer target) {
        if (!attacker.getServer().isPvpAllowed()) return DisruptionResult.Code.PVP_DISABLED;
        if (!friendlyFireAllowed(attacker, target)) return DisruptionResult.Code.TEAM_BLOCKED;
        ServerLevel level = attacker.serverLevel();
        ClaimLedger.ClaimKey key = MultiplayerLifecycleService.key(level, target.blockPosition());
        if (!MultiplayerLifecycleService.claims(level).permits(key, attacker.getUUID(),
                SpellSecurityPolicy.teamName(attacker), attacker.hasPermissions(2))) {
            return DisruptionResult.Code.CLAIM_BLOCKED;
        }
        if (!SpellSecurityPolicy.canAffectEntity(attacker, target, DISRUPTION_RANGE)) {
            return DisruptionResult.Code.REJECTED_POLICY;
        }
        return null;
    }

    private static boolean friendlyFireAllowed(ServerPlayer attacker, Entity target) {
        if (!(target instanceof ServerPlayer other) || attacker == other) return true;
        var attackerTeam = attacker.getTeam();
        return attackerTeam == null || attackerTeam != other.getTeam()
                || attackerTeam.isAllowFriendlyFire();
    }

    private static DisruptionResult.Code mapDecision(MechanicDecision.Code code) {
        return switch (code) {
            case NO_ACTIVE_SPELL -> DisruptionResult.Code.NO_ACTIVE_SPELL;
            case WINDOW_CLOSED -> DisruptionResult.Code.TIMING_WINDOW_CLOSED;
            case PVP_DENIED -> DisruptionResult.Code.PVP_DISABLED;
            case FRIENDLY_FIRE_DENIED -> DisruptionResult.Code.TEAM_BLOCKED;
            case SOURCE_UNLOADED, TARGET_UNLOADED -> DisruptionResult.Code.TARGET_NOT_LOADED;
            case DIMENSION_MISMATCH -> DisruptionResult.Code.WRONG_DIMENSION;
            case RANGE_EXCEEDED -> DisruptionResult.Code.OUT_OF_RANGE;
            case STANCE_REQUIRED, WEAPON_REQUIRED -> DisruptionResult.Code.REJECTED_POLICY;
            default -> DisruptionResult.Code.REJECTED_POLICY;
        };
    }

    private static DisruptionResult disruption(DisruptionResult.Code code, String reason) {
        return new DisruptionResult(code, reason == null || reason.isBlank()
                ? "disruption rejected" : reason);
    }

    private static void requirePlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        requireLevel(player.serverLevel());
    }

    private static void requireLevel(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (!isServerThread(level)) {
            throw new IllegalStateException("Vector-Regnum API requires the authoritative server thread");
        }
    }

    private static boolean isServerThread(ServerLevel level) {
        return level != null && level.getServer() != null && level.getServer().isSameThread();
    }

    private record Entry(String elementId, double amount) {
    }
}
