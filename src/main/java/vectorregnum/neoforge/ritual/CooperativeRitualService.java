package vectorregnum.neoforge.ritual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import vectorregnum.core.casting.CastCost;
import vectorregnum.core.casting.CastQuote;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.ReagentLoadout;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.core.circle.CirclePersistence;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.ritual.CooperativeRitual;
import vectorregnum.core.ritual.CooperativeRitualLedger;
import vectorregnum.core.ritual.RitualCostAllocator;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.CircleAuthoringService;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.VectorRegnumMod;
import vectorregnum.neoforge.effect.PersistentEffectService;

/** Server-thread cooperative consent, reservation, execution, and restart reconciliation. */
public final class CooperativeRitualService {
    public static final long APPROVAL_WINDOW_TICKS = 6_000L;
    private static final Map<UUID, ExecutionBatch> RUNNING = new LinkedHashMap<>();
    private static long lastReconcileTick = Long.MIN_VALUE;

    private CooperativeRitualService() {
    }

    public static Optional<CooperativeRitual> create(ServerPlayer leader,
            CooperativeRitual.Mode mode, CooperativeRitual.Terms leaderTerms) {
        Objects.requireNonNull(leader, "leader");
        MagicCircle circle = CircleAuthoringService.session(leader).current();
        if (CircleAuthoringService.cooperativeBaseline(leader, circle).isEmpty()) {
            reject(leader, "The current circle does not compile for a cooperative ritual");
            return Optional.empty();
        }
        MinecraftServer server = server(leader);
        CooperativeRitualSavedData data = data(server);
        CooperativeRitualLedger ledger = pruneTerminalForCapacity(data.ledger());
        if (ledger.activeForLeader(leader.getUUID()) >= CooperativeRitual.MAX_ACTIVE_PER_LEADER) {
            reject(leader, "You already own the maximum number of active ritual proposals");
            return Optional.empty();
        }
        long now = leader.serverLevel().getGameTime();
        CooperativeRitual ritual = CooperativeRitual.create(UUID.randomUUID(), leader.getUUID(),
                leader.getGameProfile().getName(), circle.name(), CirclePersistence.encode(circle),
                mode, leader.serverLevel().dimension().location().toString(), now,
                now + APPROVAL_WINDOW_TICKS, leaderTerms);
        persist(data, ledger.put(ritual).ledger(), server);
        sendTerms(leader, ritual, ritual.participant(leader.getUUID()),
                "Created cooperative ritual " + shortId(ritual.ritualId()));
        leader.sendSystemMessage(Component.literal("Approve your own exact commitment with /vectorregnum ritual approve "
                        + shortId(ritual.ritualId()))
                .withStyle(ChatFormatting.GOLD), false);
        audit("RITUAL_CREATED", ritual, "mode=" + mode.stableId());
        return Optional.of(ritual);
    }

    public static boolean invite(ServerPlayer leader, String idPrefix, ServerPlayer contributor,
            CooperativeRitual.Terms terms) {
        MinecraftServer server = server(leader);
        CooperativeRitual ritual = resolve(server, idPrefix).orElse(null);
        if (ritual == null) return reject(leader, "No unique ritual matches " + idPrefix);
        if (!ritual.leaderId().equals(leader.getUUID())) return reject(leader, "Only the ritual leader may invite");
        if (contributor.serverLevel() != leader.serverLevel()) {
            return reject(leader, "A contributor must be online in the ritual dimension before invitation");
        }
        try {
            CooperativeRitual updated = ritual.invite(contributor.getUUID(),
                    contributor.getGameProfile().getName(), terms);
            update(server, updated);
            sendTerms(contributor, updated, updated.participant(contributor.getUUID()),
                    leader.getGameProfile().getName() + " invited you to ritual "
                            + shortId(updated.ritualId()));
            contributor.sendSystemMessage(Component.literal("Approval is for this ritual only. Use /vectorregnum ritual approve "
                            + shortId(updated.ritualId()) + " or decline " + shortId(updated.ritualId()))
                    .withStyle(ChatFormatting.GOLD), false);
            leader.sendSystemMessage(Component.literal("Invited "
                            + contributor.getGameProfile().getName() + " to " + shortId(updated.ritualId()))
                    .withStyle(ChatFormatting.GREEN), false);
            audit("RITUAL_INVITED", updated, "contributor=" + contributor.getUUID());
            return true;
        } catch (RuntimeException exception) {
            return reject(leader, exception.getMessage());
        }
    }

    public static boolean approve(ServerPlayer contributor, String idPrefix) {
        MinecraftServer server = server(contributor);
        CooperativeRitual ritual = resolve(server, idPrefix).orElse(null);
        if (ritual == null) return reject(contributor, "No unique ritual matches " + idPrefix);
        CooperativeRitual.Participant participant = ritual.participant(contributor.getUUID());
        if (participant == null) return reject(contributor, "You are not invited to this ritual");
        if (ritual.state().startedOrTerminal()) {
            return reject(contributor, "That ritual already started or reached a terminal state");
        }
        if (ritual.expired(contributor.serverLevel().getGameTime())) {
            finish(server, ritual, ResourceEscrow.Outcome.ENGINE_FAILURE,
                    "approval window expired");
            return reject(contributor, "The ritual approval window expired and every reservation was refunded");
        }
        if (!dimension(contributor).equals(ritual.dimension())) {
            return reject(contributor, "Return to the ritual dimension before approving");
        }
        sendTerms(contributor, ritual, participant, "Approving exact ritual terms");
        Optional<RitualEscrowStore.Escrow> reserved;
        try {
            reserved = CastingResourceService.reserveRitual(contributor, ritual, participant);
        } catch (RuntimeException exception) {
            return reject(contributor, exception.getMessage());
        }
        if (reserved.isEmpty()) return false;
        CooperativeRitual updated;
        try {
            updated = ritual.reserve(contributor.getUUID(), reserved.orElseThrow().loadout());
            update(server, updated);
        } catch (RuntimeException exception) {
            CastingResourceService.settleRitual(contributor, ritual.ritualId(),
                    ResourceEscrow.Outcome.ENGINE_FAILURE, 0.0, 0.0);
            return reject(contributor, "Approval could not be persisted; your reservation was refunded");
        }
        notifyParticipants(server, updated,
                contributor.getGameProfile().getName() + " approved ritual "
                        + shortId(updated.ritualId()) + " • " + updated.reservedCount() + "/"
                        + updated.participants().size());
        if (updated.state() == CooperativeRitual.State.READY) {
            notifyParticipants(server, updated,
                    "All contributors approved. The leader may start ritual "
                            + shortId(updated.ritualId()));
        }
        audit("RITUAL_APPROVED", updated, "contributor=" + contributor.getUUID()
                + " loadout=" + reserved.orElseThrow().loadout());
        return true;
    }

    public static boolean decline(ServerPlayer contributor, String idPrefix) {
        MinecraftServer server = server(contributor);
        CooperativeRitual ritual = resolve(server, idPrefix).orElse(null);
        if (ritual == null || !ritual.includes(contributor.getUUID())) {
            return reject(contributor, "You are not invited to a unique matching ritual");
        }
        if (ritual.state().terminal()) {
            return reject(contributor, "That ritual already reached a terminal state");
        }
        finish(server, ritual, ResourceEscrow.Outcome.POLICY_REJECTED,
                contributor.getGameProfile().getName() + " declined before start");
        return true;
    }

    public static boolean cancel(ServerPlayer leader, String idPrefix) {
        MinecraftServer server = server(leader);
        CooperativeRitual ritual = resolve(server, idPrefix).orElse(null);
        if (ritual == null) return reject(leader, "No unique ritual matches " + idPrefix);
        if (!ritual.leaderId().equals(leader.getUUID())) return reject(leader, "Only the ritual leader may cancel");
        if (ritual.state().terminal()) return reject(leader, "That ritual already reached a terminal state");
        if (ritual.state() == CooperativeRitual.State.STARTED) {
            return reject(leader, "Execution already began; individual cast settlement now controls the escrow");
        }
        finish(server, ritual, ResourceEscrow.Outcome.POLICY_REJECTED,
                "leader cancelled before start");
        return true;
    }

    public static boolean start(ServerPlayer leader, String idPrefix) {
        MinecraftServer server = server(leader);
        CooperativeRitual ritual = resolve(server, idPrefix).orElse(null);
        if (ritual == null) return reject(leader, "No unique ritual matches " + idPrefix);
        if (!ritual.leaderId().equals(leader.getUUID())) return reject(leader, "Only the ritual leader may start");
        if (ritual.state() != CooperativeRitual.State.READY) {
            return reject(leader, "Every invited contributor must approve before execution");
        }
        if (ritual.expired(leader.serverLevel().getGameTime())) {
            finish(server, ritual, ResourceEscrow.Outcome.ENGINE_FAILURE,
                    "approval window expired");
            return false;
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (CooperativeRitual.Participant participant : ritual.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player == null || !dimension(player).equals(ritual.dimension())
                    || !NeoForgeVmService.canStartCooperative(player)) {
                finish(server, ritual, ResourceEscrow.Outcome.OWNER_LIFECYCLE,
                        participant.name() + " was offline, elsewhere, or unable to start");
                return reject(leader, "Pre-start contributor validation failed; all reservations were refunded");
            }
            RitualEscrowStore.Escrow escrow = CastingResourceService.ritualEscrows(player)
                    .get(ritual.ritualId());
            if (escrow == null || !escrow.loadout().equals(participant.loadout())) {
                finish(server, ritual, ResourceEscrow.Outcome.ENGINE_FAILURE,
                        participant.name() + " had no matching durable reservation");
                return reject(leader, "A durable reservation was missing; all valid reservations were refunded");
            }
            players.add(player);
        }

        MagicCircle circle;
        CastQuote quote;
        try {
            circle = CirclePersistence.decode(ritual.circlePayload());
            CastCost baseline = ritual.mode() == CooperativeRitual.Mode.SPLIT
                    ? baseline(players.getFirst(), circle)
                    : players.stream().map(player -> baseline(player, circle))
                            .reduce(CastCost.ZERO, CastCost::plus);
            ReagentLoadout combined = RitualCostAllocator.aggregate(
                    ritual.participants(), CastingResourceService.policy());
            quote = CastingResourceService.policy().quote(CastingMethod.RITUAL, baseline, combined);
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.error("Ritual {} quote or frozen-circle validation failed",
                    ritual.ritualId(), exception);
            finish(server, ritual, ResourceEscrow.Outcome.ENGINE_FAILURE,
                    "quote or frozen circle failed validation");
            return reject(leader, "Ritual quote failed safely: " + exception.getMessage());
        }

        Map<UUID, CooperativeRitual.Allocation> allocations;
        try {
            allocations = RitualCostAllocator.allocate(quote.finalCost().mana(),
                    quote.finalCost().upkeep(), ritual.participants());
        } catch (RuntimeException exception) {
            finish(server, ritual, ResourceEscrow.Outcome.ENGINE_FAILURE,
                    "approved maxima did not fund the final quote");
            return reject(leader, exception.getMessage() + "; all reservations were refunded");
        }
        CooperativeRitual started = ritual.start(allocations);
        try {
            update(server, started);
        } catch (RuntimeException exception) {
            finish(server, ritual, ResourceEscrow.Outcome.ENGINE_FAILURE,
                    "start record could not be persisted");
            return reject(leader, "Could not persist the atomic start; reservations were refunded");
        }

        List<ServerPlayer> casters = started.mode() == CooperativeRitual.Mode.SPLIT
                ? List.of(leader) : List.copyOf(players);
        List<CastCost> approvedCopyCosts = new ArrayList<>(casters.size());
        if (started.mode() == CooperativeRitual.Mode.SPLIT) {
            approvedCopyCosts.add(quote.finalCost());
        } else {
            double castingTimePerCopy = quote.finalCost().castingTime() / casters.size();
            double instabilityPerCopy = quote.finalCost().instability() / casters.size();
            for (ServerPlayer caster : casters) {
                CooperativeRitual.Participant participant = started.participant(caster.getUUID());
                approvedCopyCosts.add(new CastCost(participant.allocatedMana(),
                        castingTimePerCopy, participant.allocatedUpkeep(), instabilityPerCopy));
            }
        }
        String label = "Cooperative Ritual " + shortId(started.ritualId());
        ExecutionBatch batch = new ExecutionBatch(server, started.ritualId(),
                label, casters.size());
        RUNNING.put(started.ritualId(), batch);
        notifyParticipants(server, started, String.format(Locale.ROOT,
                "Ritual %s started • %s • %.2f μ + %.2f μ upkeep • %d exact reagent/offering units",
                shortId(started.ritualId()), started.mode().stableId(),
                quote.finalCost().mana(), quote.finalCost().upkeep(),
                quote.loadout().totalUnits() + quote.loadout().offeringUnits()));
        audit("RITUAL_STARTED", started, "mana=" + quote.finalCost().mana()
                + " upkeep=" + quote.finalCost().upkeep() + " copies=" + casters.size()
                + " allocations=" + started.participants().stream().map(participant ->
                        participant.playerId() + ":" + participant.allocatedMana()
                                + "/" + participant.allocatedUpkeep()).toList());

        int launched = 0;
        for (int index = 0; index < casters.size(); index++) {
            ServerPlayer caster = casters.get(index);
            boolean accepted;
            try {
                accepted = CircleAuthoringService.activateCooperativeCopy(caster, circle,
                        caster.getEyePosition(), label, approvedCopyCosts.get(index),
                        PersistentEffectService.cooperativeEffectId(
                                started.ritualId(), caster.getUUID()), batch::complete);
            } catch (RuntimeException exception) {
                VectorRegnumMod.LOGGER.error("Cooperative ritual {} copy launch failed safely for {}",
                        started.ritualId(), caster.getUUID(), exception);
                accepted = false;
            }
            if (!accepted) {
                batch.complete(failedCopy(caster.getUUID()));
                NeoForgeVmService.cancelLabel(label, "cooperative start did not accept every copy");
                for (int remaining = launched + 1; remaining < casters.size(); remaining++) {
                    batch.complete(failedCopy(casters.get(remaining).getUUID()));
                }
                return false;
            }
            launched++;
        }
        return true;
    }

    public static int status(ServerPlayer player) {
        List<CooperativeRitual> rituals = data(server(player)).ledger().entries().values().stream()
                .filter(value -> value.includes(player.getUUID()))
                .sorted(java.util.Comparator.comparingLong(CooperativeRitual::createdTick).reversed())
                .limit(8).toList();
        if (rituals.isEmpty()) {
            player.sendSystemMessage(Component.literal("No cooperative ritual records involve you")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        player.sendSystemMessage(Component.literal("Cooperative rituals • " + rituals.size())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (CooperativeRitual ritual : rituals) {
            player.sendSystemMessage(Component.literal(shortId(ritual.ritualId()) + " • "
                            + ritual.mode().stableId() + " • "
                            + ritual.state().name().toLowerCase(Locale.ROOT) + " • approvals "
                            + ritual.reservedCount() + "/" + ritual.participants().size()
                            + (ritual.terminalReason().isBlank() ? "" : " • " + ritual.terminalReason()))
                    .withStyle(ritual.state().terminal()
                            ? ChatFormatting.GRAY : ChatFormatting.AQUA), false);
        }
        return rituals.size();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcilePlayer(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = server(player);
        data(server).ledger().entries().values().stream()
                .filter(value -> value.includes(player.getUUID()))
                .filter(value -> !value.state().startedOrTerminal())
                .toList().forEach(ritual -> finish(server, ritual,
                        ResourceEscrow.Outcome.OWNER_LIFECYCLE,
                        player.getGameProfile().getName() + " disconnected before start"));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().overworld().getGameTime();
        if (tick == lastReconcileTick || tick % 20L != 0L) return;
        lastReconcileTick = tick;
        for (ExecutionBatch batch : List.copyOf(RUNNING.values())) batch.retry();
        for (CooperativeRitual ritual : List.copyOf(
                data(event.getServer()).ledger().entries().values())) {
            if (ritual.expired(tick)) {
                finish(event.getServer(), ritual, ResourceEscrow.Outcome.ENGINE_FAILURE,
                        "approval window expired");
            } else if (ritual.state() == CooperativeRitual.State.STARTED
                    && !RUNNING.containsKey(ritual.ritualId())) {
                finish(event.getServer(), ritual, ResourceEscrow.Outcome.SHUTDOWN,
                        "started VM was absent after restart");
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RUNNING.clear();
        data(event.getServer()).ledger().entries().values().stream()
                .filter(value -> !value.state().terminal()).toList()
                .forEach(ritual -> finish(event.getServer(), ritual,
                        ResourceEscrow.Outcome.SHUTDOWN, "server stopped"));
        lastReconcileTick = Long.MIN_VALUE;
    }

    private static void reconcilePlayer(ServerPlayer player) {
        RitualEscrowStore store;
        try {
            store = CastingResourceService.ritualEscrows(player);
        } catch (RuntimeException exception) {
            reject(player, exception.getMessage());
            return;
        }
        for (RitualEscrowStore.Escrow escrow : List.copyOf(store.escrows().values())) {
            MinecraftServer server = server(player);
            CooperativeRitual ritual = data(server).ledger().get(escrow.ritualId());
            if (ritual == null) {
                CastingResourceService.settleRitual(player, escrow.ritualId(),
                        ResourceEscrow.Outcome.ENGINE_FAILURE, 0.0, 0.0);
                continue;
            }
            CooperativeRitual.Participant participant = ritual.participant(player.getUUID());
            if (participant == null) {
                CastingResourceService.settleRitual(player, escrow.ritualId(),
                        ResourceEscrow.Outcome.ENGINE_FAILURE, 0.0, 0.0);
                continue;
            }
            if (ritual.state().terminal()) {
                CastingResourceService.settleRitual(player, escrow.ritualId(),
                        terminalOutcome(ritual), participant.allocatedMana(),
                        participant.allocatedUpkeep());
            } else if (participant.status() == CooperativeRitual.ParticipantStatus.INVITED
                    && approximately(escrow.reservedMana(), participant.terms().maxMana())
                    && approximately(escrow.reservedUpkeep(), participant.terms().maxUpkeep())) {
                update(server, ritual.reserve(player.getUUID(), escrow.loadout()));
                audit("RITUAL_RECONCILED", ritual, "adopted player escrow after restart");
            }
        }
    }

    private static CastCost baseline(ServerPlayer player, MagicCircle circle) {
        return CircleAuthoringService.cooperativeBaseline(player, circle)
                .orElseThrow(() -> new IllegalArgumentException(
                        "frozen circle no longer compiles for " + player.getGameProfile().getName()));
    }

    private static void finish(MinecraftServer server, CooperativeRitual ritual,
            ResourceEscrow.Outcome outcome, String reason) {
        boolean newlyTerminal = !ritual.state().terminal();
        ResourceEscrow.Outcome effectiveOutcome = newlyTerminal ? outcome : terminalOutcome(ritual);
        if (newlyTerminal && ritual.state() == CooperativeRitual.State.STARTED
                && effectiveOutcome != ResourceEscrow.Outcome.SUCCESS) {
            cancelCommittedCopies(server, ritual);
        }
        CooperativeRitual terminal = newlyTerminal ? ritual.settle(effectiveOutcome, reason) : ritual;
        if (newlyTerminal) update(server, terminal);
        for (CooperativeRitual.Participant participant : terminal.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) CastingResourceService.settleRitual(player, ritual.ritualId(),
                    effectiveOutcome, participant.allocatedMana(), participant.allocatedUpkeep());
        }
        RUNNING.remove(ritual.ritualId());
        if (newlyTerminal) {
            notifyParticipants(server, terminal, "Ritual " + shortId(ritual.ritualId()) + " • "
                    + terminal.state().name().toLowerCase(Locale.ROOT) + " • " + reason);
            audit("RITUAL_SETTLED", terminal, "outcome=" + effectiveOutcome + " reason=" + reason);
        }
    }

    private static void cancelCommittedCopies(MinecraftServer server, CooperativeRitual ritual) {
        List<UUID> owners = ritual.mode() == CooperativeRitual.Mode.SPLIT
                ? List.of(ritual.leaderId())
                : ritual.participants().stream().map(CooperativeRitual.Participant::playerId).toList();
        for (UUID ownerId : owners) {
            PersistentEffectService.cancelCommitted(server,
                    PersistentEffectService.cooperativeEffectId(ritual.ritualId(), ownerId));
        }
    }

    private static ResourceEscrow.Outcome terminalOutcome(CooperativeRitual ritual) {
        return switch (ritual.state()) {
            case SUCCEEDED -> ResourceEscrow.Outcome.SUCCESS;
            case FAULTED -> ResourceEscrow.Outcome.GENUINE_SPELL_FAULT;
            default -> ResourceEscrow.Outcome.ENGINE_FAILURE;
        };
    }

    private static CooperativeRitualLedger pruneTerminalForCapacity(CooperativeRitualLedger ledger) {
        if (ledger.entries().size() < CooperativeRitualLedger.MAX_RECORDS) return ledger;
        Optional<CooperativeRitual> oldest = ledger.entries().values().stream()
                .filter(value -> value.state().terminal())
                .min(java.util.Comparator.comparingLong(CooperativeRitual::createdTick));
        if (oldest.isEmpty()) throw new IllegalStateException("cooperative ritual ledger is full");
        return ledger.remove(oldest.orElseThrow().ritualId());
    }

    private static Optional<CooperativeRitual> resolve(MinecraftServer server, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        if (normalized.length() < 4 || normalized.length() > 36
                || !normalized.matches("[0-9a-f-]+")) return Optional.empty();
        List<CooperativeRitual> matches = data(server).ledger().entries().values().stream()
                .filter(value -> value.ritualId().toString().startsWith(normalized)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static void update(MinecraftServer server, CooperativeRitual ritual) {
        CooperativeRitualSavedData data = data(server);
        persist(data, data.ledger().put(ritual).ledger(), server);
    }

    private static void persist(CooperativeRitualSavedData data,
            CooperativeRitualLedger replacement, MinecraftServer server) {
        CooperativeRitualLedger previous = data.ledger();
        if (!data.replace(replacement)) return;
        try {
            server.overworld().getDataStorage().save();
        } catch (RuntimeException exception) {
            data.replace(previous);
            try {
                server.overworld().getDataStorage().save();
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    private static CooperativeRitualSavedData data(MinecraftServer server) {
        return CooperativeRitualSavedData.get(server);
    }

    private static void sendTerms(ServerPlayer player, CooperativeRitual ritual,
            CooperativeRitual.Participant participant, String heading) {
        player.sendSystemMessage(Component.literal(heading + " • " + ritual.title() + " • "
                        + ritual.mode().stableId()).withStyle(ChatFormatting.GOLD), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "Exact maximum • %.2f μ mana • %d staged reagent/offering units • %.2f μ upkeep",
                        participant.terms().maxMana(), participant.terms().maxReagentUnits(),
                        participant.terms().maxUpkeep()))
                .withStyle(ChatFormatting.AQUA), false);
    }

    private static void notifyParticipants(MinecraftServer server,
            CooperativeRitual ritual, String message) {
        for (CooperativeRitual.Participant participant : ritual.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) player.sendSystemMessage(Component.literal(message)
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    private static boolean reject(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message == null ? "Ritual request was rejected" : message)
                .withStyle(ChatFormatting.RED), false);
        return false;
    }

    private static String dimension(ServerPlayer player) {
        return player.serverLevel().dimension().location().toString();
    }

    private static MinecraftServer server(ServerPlayer player) {
        return player.serverLevel().getServer();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static boolean approximately(double left, double right) {
        return Math.abs(left - right) <= 1.0e-9;
    }

    private static void audit(String event, CooperativeRitual ritual, String detail) {
        VectorRegnumMod.LOGGER.info("{} id={} revision={} state={} participants={} {}", event,
                ritual.ritualId(), ritual.revision(), ritual.state(), ritual.participants().size(), detail);
    }

    private static NeoForgeVmService.CooperativeCopyResult failedCopy(UUID ownerId) {
        return new NeoForgeVmService.CooperativeCopyResult(ownerId,
                ResourceEscrow.Outcome.ENGINE_FAILURE, null, 0.0);
    }

    private static final class ExecutionBatch {
        private final MinecraftServer server;
        private final UUID ritualId;
        private final String label;
        private final Map<UUID, NeoForgeVmService.CooperativeCopyResult> results =
                new LinkedHashMap<>();
        private int pending;
        private boolean terminal;

        private ExecutionBatch(MinecraftServer server, UUID ritualId, String label, int pending) {
            this.server = server;
            this.ritualId = ritualId;
            this.label = label;
            this.pending = pending;
        }

        private void complete(NeoForgeVmService.CooperativeCopyResult result) {
            if (terminal) return;
            Objects.requireNonNull(result, "result");
            if (results.putIfAbsent(result.ownerId(), result) != null) {
                VectorRegnumMod.LOGGER.error(
                        "Cooperative ritual {} received duplicate terminal result for {}",
                        ritualId, result.ownerId());
                return;
            }
            pending--;
            retry();
        }

        private void retry() {
            if (terminal || pending > 0) return;
            try {
                settleCompleteBatch();
            } catch (RuntimeException exception) {
                VectorRegnumMod.LOGGER.error(
                        "Cooperative ritual {} finalization failed safely and will retry",
                        ritualId, exception);
            }
        }

        private void settleCompleteBatch() {
            CooperativeRitual ritual = data(server).ledger().get(ritualId);
            if (ritual == null) {
                terminal = true;
                RUNNING.remove(ritualId);
                return;
            }
            if (ritual.state().terminal()) {
                finish(server, ritual, terminalOutcome(ritual), ritual.terminalReason());
                terminal = true;
                return;
            }
            ResourceEscrow.Outcome aggregate = CooperativeRitual.aggregateOutcomes(
                    results.values().stream().map(NeoForgeVmService.CooperativeCopyResult::outcome)
                            .toList());
            if (aggregate == ResourceEscrow.Outcome.SUCCESS) {
                if (ritual.mode() == CooperativeRitual.Mode.SPLIT) {
                    ritual = ritual.commitSplitUpkeep(results.values().iterator().next()
                            .committedUpkeep());
                } else {
                    Map<UUID, Double> upkeep = new LinkedHashMap<>();
                    ritual.participants().forEach(participant -> upkeep.put(participant.playerId(),
                            results.get(participant.playerId()).committedUpkeep()));
                    ritual = ritual.commitReplicateUpkeep(upkeep);
                }
                update(server, ritual);
            }
            finish(server, ritual, aggregate, aggregate == ResourceEscrow.Outcome.SUCCESS
                    ? "every cooperative copy completed" : "cooperative execution settled as "
                            + aggregate.name().toLowerCase(Locale.ROOT));
            terminal = true;
        }

    }
}
