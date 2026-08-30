package vectorregnum.core.ritual;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import vectorregnum.core.casting.CastingPolicy;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.casting.ReagentLoadout;

/** Stable allocation and aggregate-resource checks for cooperative ritual admission. */
public final class RitualCostAllocator {
    private static final double EPSILON = 1.0e-9;

    private RitualCostAllocator() {
    }

    public static Map<UUID, CooperativeRitual.Allocation> allocate(
            double mana, double upkeep, List<CooperativeRitual.Participant> participants) {
        finiteNonNegative(mana, "mana");
        finiteNonNegative(upkeep, "upkeep");
        Objects.requireNonNull(participants, "participants");
        double manaCapacity = participants.stream().mapToDouble(value -> value.terms().maxMana()).sum();
        double upkeepCapacity = participants.stream().mapToDouble(value -> value.terms().maxUpkeep()).sum();
        if (manaCapacity + EPSILON < mana || upkeepCapacity + EPSILON < upkeep) {
            throw new IllegalArgumentException("approved commitments do not fund the complete ritual quote");
        }
        double manaLeft = mana;
        double upkeepLeft = upkeep;
        Map<UUID, CooperativeRitual.Allocation> result = new LinkedHashMap<>();
        for (CooperativeRitual.Participant participant : participants) {
            double assignedMana = Math.min(manaLeft, participant.terms().maxMana());
            double assignedUpkeep = Math.min(upkeepLeft, participant.terms().maxUpkeep());
            manaLeft = positiveRemainder(manaLeft - assignedMana);
            upkeepLeft = positiveRemainder(upkeepLeft - assignedUpkeep);
            result.put(participant.playerId(),
                    new CooperativeRitual.Allocation(assignedMana, assignedUpkeep));
        }
        if (manaLeft > EPSILON || upkeepLeft > EPSILON) {
            throw new IllegalStateException("bounded allocation left an unfunded remainder");
        }
        return Map.copyOf(result);
    }

    public static ReagentLoadout aggregate(
            List<CooperativeRitual.Participant> participants, CastingPolicy policy) {
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(policy, "policy");
        EnumMap<ReagentKind, Integer> units = new EnumMap<>(ReagentKind.class);
        int offerings = 0;
        for (CooperativeRitual.Participant participant : participants) {
            if (participant.status() != CooperativeRitual.ParticipantStatus.RESERVED) {
                throw new IllegalStateException("cannot aggregate an unreserved participant");
            }
            for (ReagentKind kind : ReagentKind.values()) {
                units.merge(kind, participant.loadout().units(kind), Math::addExact);
            }
            offerings = Math.addExact(offerings, participant.loadout().offeringUnits());
        }
        return ReagentLoadout.of(units, offerings, policy);
    }

    private static double positiveRemainder(double value) {
        return Math.abs(value) <= EPSILON ? 0.0 : value;
    }

    private static void finiteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
