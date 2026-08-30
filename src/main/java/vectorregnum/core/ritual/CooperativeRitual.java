package vectorregnum.core.ritual;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import vectorregnum.core.casting.ReagentLoadout;
import vectorregnum.core.casting.ResourceEscrow;

/** Immutable, loader-neutral contract for one explicitly approved cooperative ritual. */
public record CooperativeRitual(
        int schema,
        UUID ritualId,
        UUID leaderId,
        String leaderName,
        String title,
        String circlePayload,
        Mode mode,
        String dimension,
        long createdTick,
        long expiresTick,
        long revision,
        State state,
        String terminalReason,
        List<Participant> participants) {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_PARTICIPANTS = 8;
    public static final int MAX_ACTIVE_PER_LEADER = 4;
    public static final int MAX_TITLE_LENGTH = 64;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_PAYLOAD_LENGTH = 65_536;
    public static final long MAX_APPROVAL_TICKS = 72_000L;
    public static final double MAX_COMMITMENT_MANA = 5_000.0;
    public static final int MAX_COMMITMENT_REAGENTS = 64;

    public CooperativeRitual {
        if (schema < 1 || schema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("unsupported cooperative ritual schema " + schema);
        }
        Objects.requireNonNull(ritualId, "ritualId");
        Objects.requireNonNull(leaderId, "leaderId");
        leaderName = boundedText(leaderName, "leaderName", MAX_NAME_LENGTH);
        title = boundedText(title, "title", MAX_TITLE_LENGTH);
        circlePayload = boundedPayload(circlePayload);
        Objects.requireNonNull(mode, "mode");
        dimension = boundedText(dimension, "dimension", 128);
        Objects.requireNonNull(state, "state");
        terminalReason = terminalReason == null ? "" : terminalReason.strip();
        if (terminalReason.length() > 96) throw new IllegalArgumentException("terminal reason is too long");
        if (createdTick < 0L || expiresTick <= createdTick
                || expiresTick - createdTick > MAX_APPROVAL_TICKS) {
            throw new IllegalArgumentException("approval window is outside the bounded range");
        }
        if (revision < 0L) throw new IllegalArgumentException("revision cannot be negative");
        Objects.requireNonNull(participants, "participants");
        participants = List.copyOf(participants);
        if (participants.isEmpty() || participants.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("ritual participant count is outside 1.." + MAX_PARTICIPANTS);
        }
        Set<UUID> identities = new HashSet<>();
        for (Participant participant : participants) {
            if (!identities.add(participant.playerId())) {
                throw new IllegalArgumentException("duplicate ritual participant");
            }
        }
        if (!identities.contains(leaderId)) throw new IllegalArgumentException("ritual leader is not a participant");
        if (state == State.READY && !allReserved(participants)) {
            throw new IllegalArgumentException("ready ritual has an unreserved participant");
        }
        if (state == State.STARTED && participants.stream().anyMatch(value ->
                value.status() != ParticipantStatus.RESERVED || value.allocatedMana() < 0.0
                        || value.allocatedUpkeep() < 0.0)) {
            throw new IllegalArgumentException("started ritual has incomplete allocation state");
        }
        if (state.terminal() && terminalReason.isBlank()) {
            throw new IllegalArgumentException("terminal ritual requires an audit reason");
        }
    }

    public static CooperativeRitual create(UUID ritualId, UUID leaderId, String leaderName,
            String title, String circlePayload, Mode mode, String dimension, long createdTick,
            long expiresTick, Terms leaderTerms) {
        return new CooperativeRitual(CURRENT_SCHEMA, ritualId, leaderId, leaderName, title,
                circlePayload, mode, dimension, createdTick, expiresTick, 0L, State.OPEN, "",
                List.of(Participant.invited(leaderId, leaderName, leaderTerms)));
    }

    public CooperativeRitual invite(UUID playerId, String name, Terms terms) {
        requirePreStart();
        if (participant(playerId) != null) throw new IllegalArgumentException("player is already in this ritual");
        if (participants.size() >= MAX_PARTICIPANTS) throw new IllegalStateException("ritual participant cap reached");
        List<Participant> updated = new ArrayList<>(participants);
        updated.add(Participant.invited(playerId, name, terms));
        return copy(State.OPEN, "", updated);
    }

    public CooperativeRitual reserve(UUID playerId, ReagentLoadout exactLoadout) {
        requirePreStart();
        Participant current = requireParticipant(playerId);
        if (current.status() == ParticipantStatus.RESERVED) {
            if (!current.loadout().equals(exactLoadout)) {
                throw new IllegalStateException("reservation retry changed the exact reagent loadout");
            }
            return this;
        }
        Participant reserved = current.reserve(exactLoadout);
        List<Participant> updated = replaceParticipant(reserved);
        State next = updated.size() >= 2 && allReserved(updated) ? State.READY : State.OPEN;
        return copy(next, "", updated);
    }

    public CooperativeRitual cancel(String reason) {
        if (state.terminal()) return this;
        return copy(State.CANCELLED, boundedText(reason, "reason", 96), participants);
    }

    public CooperativeRitual start(Map<UUID, Allocation> allocations) {
        if (state != State.READY) throw new IllegalStateException("ritual is not ready");
        Objects.requireNonNull(allocations, "allocations");
        List<Participant> updated = new ArrayList<>(participants.size());
        for (Participant participant : participants) {
            Allocation allocation = allocations.get(participant.playerId());
            if (allocation == null) throw new IllegalArgumentException("missing ritual allocation");
            updated.add(participant.allocate(allocation));
        }
        if (allocations.size() != participants.size()) {
            throw new IllegalArgumentException("allocation contains a non-participant");
        }
        return copy(State.STARTED, "", updated);
    }

    /** Records the upkeep actually transferred by one split execution. */
    public CooperativeRitual commitSplitUpkeep(double committedUpkeep) {
        requireStarted();
        finiteCommittedUpkeep(committedUpkeep);
        double remaining = committedUpkeep;
        List<Participant> updated = new ArrayList<>(participants.size());
        for (Participant participant : participants) {
            double committed = Math.min(remaining, participant.allocatedUpkeep());
            remaining = Math.max(0.0, remaining - committed);
            updated.add(participant.withCommittedUpkeep(committed));
        }
        if (remaining > 1.0e-9) {
            throw new IllegalArgumentException("committed split upkeep exceeds its approved allocation");
        }
        return copy(State.STARTED, "", updated);
    }

    /** Records the upkeep actually transferred by each stable replicate copy. */
    public CooperativeRitual commitReplicateUpkeep(Map<UUID, Double> committedUpkeep) {
        requireStarted();
        Objects.requireNonNull(committedUpkeep, "committedUpkeep");
        if (committedUpkeep.size() != participants.size()) {
            throw new IllegalArgumentException("replicate upkeep must name every contributor exactly once");
        }
        List<Participant> updated = new ArrayList<>(participants.size());
        for (Participant participant : participants) {
            Double committed = committedUpkeep.get(participant.playerId());
            if (committed == null) throw new IllegalArgumentException("missing replicate upkeep contributor");
            finiteCommittedUpkeep(committed);
            updated.add(participant.withCommittedUpkeep(committed));
        }
        return copy(State.STARTED, "", updated);
    }

    public CooperativeRitual settle(ResourceEscrow.Outcome outcome) {
        return settle(outcome, outcome.name().toLowerCase(Locale.ROOT));
    }

    public CooperativeRitual settle(ResourceEscrow.Outcome outcome, String reason) {
        Objects.requireNonNull(outcome, "outcome");
        if (state.terminal()) return this;
        if (state != State.STARTED && outcome.consumesResources()) {
            throw new IllegalStateException("pre-start ritual cannot consume resources");
        }
        ParticipantStatus terminal = outcome.consumesResources()
                ? ParticipantStatus.CONSUMED : ParticipantStatus.REFUNDED;
        List<Participant> updated = participants.stream()
                .map(value -> value.status() == ParticipantStatus.RESERVED
                        ? value.withStatus(terminal) : value)
                .toList();
        State next = outcome == ResourceEscrow.Outcome.SUCCESS ? State.SUCCEEDED
                : outcome.consumesResources() ? State.FAULTED : State.CANCELLED;
        return copy(next, boundedText(reason, "reason", 96), updated);
    }

    public Participant participant(UUID playerId) {
        return participants.stream().filter(value -> value.playerId().equals(playerId))
                .findFirst().orElse(null);
    }

    public boolean includes(UUID playerId) {
        return participant(playerId) != null;
    }

    public boolean expired(long tick) {
        return !state.startedOrTerminal() && tick >= expiresTick;
    }

    public int reservedCount() {
        return (int) participants.stream().filter(value -> value.status() == ParticipantStatus.RESERVED).count();
    }

    /** Aggregates copy settlement without allowing one success to hide a refundable failure. */
    public static ResourceEscrow.Outcome aggregateOutcomes(List<ResourceEscrow.Outcome> outcomes) {
        Objects.requireNonNull(outcomes, "outcomes");
        if (outcomes.isEmpty() || outcomes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("cooperative outcomes must be non-empty and non-null");
        }
        if (outcomes.stream().allMatch(value -> value == ResourceEscrow.Outcome.SUCCESS)) {
            return ResourceEscrow.Outcome.SUCCESS;
        }
        if (outcomes.stream().anyMatch(value ->
                value == ResourceEscrow.Outcome.GENUINE_SPELL_FAULT)) {
            return ResourceEscrow.Outcome.GENUINE_SPELL_FAULT;
        }
        return outcomes.stream().filter(value -> value != ResourceEscrow.Outcome.SUCCESS)
                .findFirst().orElse(ResourceEscrow.Outcome.ENGINE_FAILURE);
    }

    private CooperativeRitual copy(State newState, String reason, List<Participant> updated) {
        return new CooperativeRitual(schema, ritualId, leaderId, leaderName, title, circlePayload,
                mode, dimension, createdTick, expiresTick, Math.addExact(revision, 1L),
                newState, reason, updated);
    }

    private void requirePreStart() {
        if (state.startedOrTerminal()) throw new IllegalStateException("ritual already started or ended");
    }

    private void requireStarted() {
        if (state != State.STARTED) throw new IllegalStateException("ritual has not started");
    }

    private static void finiteCommittedUpkeep(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("committed ritual upkeep must be finite and non-negative");
        }
    }

    private Participant requireParticipant(UUID playerId) {
        Participant participant = participant(playerId);
        if (participant == null) throw new IllegalArgumentException("player is not invited to this ritual");
        return participant;
    }

    private List<Participant> replaceParticipant(Participant replacement) {
        return participants.stream().map(value -> value.playerId().equals(replacement.playerId())
                ? replacement : value).toList();
    }

    private static boolean allReserved(List<Participant> participants) {
        return participants.stream().allMatch(value -> value.status() == ParticipantStatus.RESERVED);
    }

    private static String boundedText(String value, String field, int maximum) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " length is outside 1.." + maximum);
        }
        return normalized;
    }

    private static String boundedPayload(String value) {
        Objects.requireNonNull(value, "circlePayload");
        if (value.isEmpty() || value.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("circlePayload length is outside 1.."
                    + MAX_PAYLOAD_LENGTH);
        }
        return value;
    }

    public enum Mode {
        SPLIT,
        REPLICATE;

        public String stableId() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum State {
        OPEN,
        READY,
        STARTED,
        SUCCEEDED,
        FAULTED,
        CANCELLED;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAULTED || this == CANCELLED;
        }

        public boolean startedOrTerminal() {
            return this == STARTED || terminal();
        }
    }

    public enum ParticipantStatus {
        INVITED,
        RESERVED,
        CONSUMED,
        REFUNDED
    }

    public record Terms(double maxMana, int maxReagentUnits, double maxUpkeep) {
        public Terms {
            finiteBounded(maxMana, "maximum mana");
            finiteBounded(maxUpkeep, "maximum upkeep");
            if (maxReagentUnits < 0 || maxReagentUnits > MAX_COMMITMENT_REAGENTS) {
                throw new IllegalArgumentException("maximum reagent units are outside 0.."
                        + MAX_COMMITMENT_REAGENTS);
            }
            if (maxMana + maxUpkeep <= 0.0 && maxReagentUnits == 0) {
                throw new IllegalArgumentException("ritual commitment cannot be empty");
            }
        }

        private static void finiteBounded(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0 || value > MAX_COMMITMENT_MANA) {
                throw new IllegalArgumentException(name + " is outside 0.." + MAX_COMMITMENT_MANA);
            }
        }
    }

    public record Allocation(double mana, double upkeep) {
        public Allocation {
            if (!Double.isFinite(mana) || mana < 0.0 || !Double.isFinite(upkeep) || upkeep < 0.0) {
                throw new IllegalArgumentException("ritual allocation must be finite and non-negative");
            }
        }
    }

    public record Participant(UUID playerId, String name, Terms terms,
            ParticipantStatus status, ReagentLoadout loadout,
            double allocatedMana, double allocatedUpkeep) {
        public Participant {
            Objects.requireNonNull(playerId, "playerId");
            name = boundedText(name, "participant name", MAX_NAME_LENGTH);
            Objects.requireNonNull(terms, "terms");
            Objects.requireNonNull(status, "status");
            loadout = loadout == null ? ReagentLoadout.empty() : loadout;
            if (loadout.totalUnits() + loadout.offeringUnits() > terms.maxReagentUnits()) {
                throw new IllegalArgumentException("exact reagent loadout exceeds approved maximum");
            }
            if (!Double.isFinite(allocatedMana) || allocatedMana < 0.0
                    || allocatedMana > terms.maxMana() + 1.0e-9
                    || !Double.isFinite(allocatedUpkeep) || allocatedUpkeep < 0.0
                    || allocatedUpkeep > terms.maxUpkeep() + 1.0e-9) {
                throw new IllegalArgumentException("ritual allocation exceeds approved maximum");
            }
            if (status == ParticipantStatus.INVITED && !loadout.isEmpty()) {
                throw new IllegalArgumentException("unapproved participant cannot hold reagents");
            }
        }

        public static Participant invited(UUID id, String name, Terms terms) {
            return new Participant(id, name, terms, ParticipantStatus.INVITED,
                    ReagentLoadout.empty(), 0.0, 0.0);
        }

        public Participant reserve(ReagentLoadout exactLoadout) {
            Objects.requireNonNull(exactLoadout, "exactLoadout");
            return new Participant(playerId, name, terms, ParticipantStatus.RESERVED,
                    exactLoadout, 0.0, 0.0);
        }

        public Participant allocate(Allocation allocation) {
            if (status != ParticipantStatus.RESERVED) {
                throw new IllegalStateException("only a reserved participant can receive an allocation");
            }
            return new Participant(playerId, name, terms, status, loadout,
                    allocation.mana(), allocation.upkeep());
        }

        private Participant withStatus(ParticipantStatus replacement) {
            return new Participant(playerId, name, terms, replacement, loadout,
                    allocatedMana, allocatedUpkeep);
        }

        private Participant withCommittedUpkeep(double committedUpkeep) {
            if (committedUpkeep > allocatedUpkeep + 1.0e-9) {
                throw new IllegalArgumentException("committed upkeep exceeds approved allocation");
            }
            return new Participant(playerId, name, terms, status, loadout,
                    allocatedMana, committedUpkeep);
        }
    }
}
