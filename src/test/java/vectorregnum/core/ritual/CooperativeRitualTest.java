package vectorregnum.core.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vectorregnum.core.casting.CastingPolicy;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.casting.ReagentLoadout;
import vectorregnum.core.casting.ResourceEscrow;

class CooperativeRitualTest {
    private static final CastingPolicy POLICY = CastingPolicy.canonical();

    @Test
    void everyContributorApprovesOneExactBoundedCommitment() {
        UUID leader = UUID.randomUUID();
        UUID contributor = UUID.randomUUID();
        CooperativeRitual ritual = ritual(leader)
                .invite(contributor, "second", new CooperativeRitual.Terms(18.0, 2, 4.0));
        ReagentLoadout leaderLoadout = ReagentLoadout.of(ReagentKind.MANA, 1, 1, POLICY);
        ReagentLoadout secondLoadout = ReagentLoadout.of(ReagentKind.UPKEEP, 1, POLICY);

        CooperativeRitual firstApproval = ritual.reserve(leader, leaderLoadout);
        assertEquals(CooperativeRitual.State.OPEN, firstApproval.state());
        assertEquals(1, firstApproval.reservedCount());
        CooperativeRitual ready = firstApproval.reserve(contributor, secondLoadout);
        assertEquals(CooperativeRitual.State.READY, ready.state());
        assertEquals(2, ready.reservedCount());
        assertSame(ready, ready.reserve(contributor, secondLoadout),
                "an identical approval retry must be a no-op");
        assertThrows(IllegalStateException.class, () -> ready.reserve(contributor,
                ReagentLoadout.of(ReagentKind.MANA, 1, POLICY)));
    }

    @Test
    void deterministicAllocationNeverExceedsAnApprovedMaximum() {
        UUID leader = UUID.randomUUID();
        UUID contributor = UUID.randomUUID();
        CooperativeRitual ready = ritual(leader)
                .invite(contributor, "second", new CooperativeRitual.Terms(12.0, 1, 3.0))
                .reserve(leader, ReagentLoadout.empty().withOfferingUnits(1, POLICY))
                .reserve(contributor, ReagentLoadout.empty());

        Map<UUID, CooperativeRitual.Allocation> allocation = RitualCostAllocator.allocate(
                22.0, 5.0, ready.participants());
        assertEquals(new CooperativeRitual.Allocation(20.0, 4.0), allocation.get(leader));
        assertEquals(new CooperativeRitual.Allocation(2.0, 1.0), allocation.get(contributor));
        CooperativeRitual started = ready.start(allocation);
        assertEquals(CooperativeRitual.State.STARTED, started.state());
        CooperativeRitual splitCommitted = started.commitSplitUpkeep(2.5);
        assertEquals(2.5, splitCommitted.participants().stream()
                .mapToDouble(CooperativeRitual.Participant::allocatedUpkeep).sum());
        CooperativeRitual replicated = started.commitReplicateUpkeep(Map.of(
                leader, 1.5, contributor, 0.5));
        assertEquals(1.5, replicated.participant(leader).allocatedUpkeep());
        assertEquals(0.5, replicated.participant(contributor).allocatedUpkeep());
        assertThrows(IllegalArgumentException.class, () -> started.commitReplicateUpkeep(Map.of(
                leader, 1.5, contributor, 1.5)));
        assertThrows(IllegalArgumentException.class, () -> RitualCostAllocator.allocate(
                33.0, 5.0, ready.participants()));
    }

    @Test
    void preStartFailuresRefundWhileStartedFaultsConsume() {
        UUID leader = UUID.randomUUID();
        UUID contributor = UUID.randomUUID();
        CooperativeRitual ready = ritual(leader)
                .invite(contributor, "second", new CooperativeRitual.Terms(12.0, 0, 3.0))
                .reserve(leader, ReagentLoadout.empty().withOfferingUnits(1, POLICY))
                .reserve(contributor, ReagentLoadout.empty());
        CooperativeRitual cancelled = ready.settle(ResourceEscrow.Outcome.OWNER_LIFECYCLE,
                "second disconnected before start");
        assertEquals(CooperativeRitual.State.CANCELLED, cancelled.state());
        assertTrue(cancelled.participants().stream().allMatch(value ->
                value.status() == CooperativeRitual.ParticipantStatus.REFUNDED));

        CooperativeRitual started = ready.start(RitualCostAllocator.allocate(
                22.0, 5.0, ready.participants()));
        CooperativeRitual faulted = started.settle(ResourceEscrow.Outcome.GENUINE_SPELL_FAULT,
                "bounded VM fault");
        assertEquals(CooperativeRitual.State.FAULTED, faulted.state());
        assertTrue(faulted.participants().stream().allMatch(value ->
                value.status() == CooperativeRitual.ParticipantStatus.CONSUMED));
        assertEquals(ResourceEscrow.Outcome.OWNER_LIFECYCLE,
                CooperativeRitual.aggregateOutcomes(List.of(
                        ResourceEscrow.Outcome.SUCCESS,
                        ResourceEscrow.Outcome.OWNER_LIFECYCLE)),
                "one successful copy must not consume a different copy's refundable failure");
        assertEquals(ResourceEscrow.Outcome.GENUINE_SPELL_FAULT,
                CooperativeRitual.aggregateOutcomes(List.of(
                        ResourceEscrow.Outcome.ENGINE_FAILURE,
                        ResourceEscrow.Outcome.GENUINE_SPELL_FAULT)));
    }

    @Test
    void aggregateReagentsRetainTypesAndRequireGlobalCaps() {
        UUID leader = UUID.randomUUID();
        UUID contributor = UUID.randomUUID();
        CooperativeRitual ready = ritual(leader)
                .invite(contributor, "second", new CooperativeRitual.Terms(12.0, 2, 3.0))
                .reserve(leader, ReagentLoadout.of(ReagentKind.MANA, 1, 1, POLICY))
                .reserve(contributor, ReagentLoadout.of(ReagentKind.UPKEEP, 2, POLICY));
        ReagentLoadout aggregate = RitualCostAllocator.aggregate(ready.participants(), POLICY);
        assertEquals(1, aggregate.units(ReagentKind.MANA));
        assertEquals(2, aggregate.units(ReagentKind.UPKEEP));
        assertEquals(1, aggregate.offeringUnits());
    }

    private static CooperativeRitual ritual(UUID leader) {
        return CooperativeRitual.create(UUID.randomUUID(), leader, "leader", "test circle",
                "encoded-circle", CooperativeRitual.Mode.SPLIT, "minecraft:overworld",
                100L, 1_000L, new CooperativeRitual.Terms(20.0, 2, 4.0));
    }
}
