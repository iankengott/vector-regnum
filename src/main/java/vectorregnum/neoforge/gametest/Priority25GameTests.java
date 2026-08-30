package vectorregnum.neoforge.gametest;

import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.core.casting.CastingPolicy;
import vectorregnum.core.casting.ReagentLoadout;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.CircleValue;
import vectorregnum.core.ritual.CooperativeRitual;
import vectorregnum.core.ritual.CooperativeRitualLedger;
import vectorregnum.core.ritual.RitualCostAllocator;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.CircleAuthoringService;
import vectorregnum.neoforge.ManaData;
import vectorregnum.core.effect.PersistentEffectContract;
import vectorregnum.neoforge.effect.PersistentEffectService;
import vectorregnum.neoforge.ritual.CooperativeRitualSavedData;
import vectorregnum.neoforge.ritual.CooperativeRitualService;

/** Real player attachment, SavedData, consent, refund, and multicasting coverage. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority25GameTests {
    private static final CastingPolicy POLICY = CastingPolicy.canonical();

    @GameTest(template = "empty")
    public void savedDataRoundTripPreservesConsentAllocationsAndAuditState(GameTestHelper context) {
        UUID leader = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CooperativeRitual ready = CooperativeRitual.create(UUID.randomUUID(), leader, "leader",
                        "restart ritual", "frozen-circle", CooperativeRitual.Mode.SPLIT,
                        context.getLevel().dimension().location().toString(), 10L, 1_000L,
                        new CooperativeRitual.Terms(40.0, 1, 8.0))
                .invite(second, "second", new CooperativeRitual.Terms(30.0, 0, 6.0))
                .reserve(leader, ReagentLoadout.empty().withOfferingUnits(1, POLICY))
                .reserve(second, ReagentLoadout.empty());
        CooperativeRitual started = ready.start(RitualCostAllocator.allocate(
                55.0, 10.0, ready.participants()));
        CooperativeRitualSavedData original = new CooperativeRitualSavedData(
                new CooperativeRitualLedger(Map.of(started.ritualId(), started)));
        CompoundTag encoded = original.save(new CompoundTag(), context.getLevel().registryAccess());
        CooperativeRitualSavedData decoded = CooperativeRitualSavedData.load(
                encoded, context.getLevel().registryAccess());

        context.assertValueEqual(started, decoded.ledger().get(started.ritualId()),
                "restart NBT must preserve every consent term and exact allocation");
        context.succeed();
    }

    @GameTest(template = "empty", batch = "priority25_ritual_isolated")
    public void declinedPreStartRitualRefundsEveryReservationExactlyOnce(GameTestHelper context) {
        ServerPlayer leader = player(context);
        ServerPlayer second = player(context);
        prepare(leader);
        prepare(second);
        leader.getInventory().placeItemBackInInventory(new ItemStack(Items.QUARTZ));
        context.assertTrue(CastingResourceService.stageOffering(leader, 1),
                "leader should stage the exact ritual offering before approval");
        CooperativeRitual ritual = createAndInvite(leader, second, CooperativeRitual.Mode.SPLIT);
        context.assertTrue(CooperativeRitualService.approve(leader, shortId(ritual)),
                "leader should explicitly approve their own maximum");
        context.assertTrue(CooperativeRitualService.approve(second, shortId(ritual)),
                "second contributor should explicitly approve their own maximum");
        context.assertValueEqual(400.0, ManaData.available(leader),
                "leader maximum mana and upkeep must move into durable escrow");
        context.assertValueEqual(400.0, ManaData.available(second),
                "second maximum mana and upkeep must move into durable escrow");
        context.assertTrue(!ManaData.tryCreditExact(leader, 1.0),
                "durable ritual escrow must preserve enough capacity for an exact refund");

        context.assertTrue(CooperativeRitualService.decline(second, shortId(ritual)),
                "a pre-start decline must atomically cancel the whole ritual");
        context.assertValueEqual(1_000.0, ManaData.available(leader),
                "leader reservation must refund exactly");
        context.assertValueEqual(1_000.0, ManaData.available(second),
                "second reservation must refund exactly");
        context.assertValueEqual(1, leader.getInventory().countItem(Items.QUARTZ),
                "the exact ritual offering must return on pre-start failure");
        context.assertValueEqual(0, CastingResourceService.ritualEscrows(leader).escrows().size(),
                "leader escrow retry state must be cleared once");
        context.assertTrue(!CooperativeRitualService.decline(second, shortId(ritual)),
                "a duplicate terminal decline must not settle resources again");
        cleanup(context, leader, second);
    }

    @GameTest(template = "empty", batch = "priority25_ritual_isolated")
    public void splitCircleCombinesManaAndConsumesOneApprovedOffering(GameTestHelper context) {
        ServerPlayer leader = player(context);
        ServerPlayer second = player(context);
        prepare(leader);
        prepare(second);
        leader.getInventory().placeItemBackInInventory(new ItemStack(Items.QUARTZ));
        CastingResourceService.stageOffering(leader, 1);
        CooperativeRitual ritual = createAndInvite(leader, second, CooperativeRitual.Mode.SPLIT);
        CooperativeRitualService.approve(leader, shortId(ritual));
        CooperativeRitualService.approve(second, shortId(ritual));

        context.assertTrue(CooperativeRitualService.start(leader, shortId(ritual)),
                "a fully approved split ritual should execute the frozen circle once");
        CooperativeRitual terminal = CooperativeRitualSavedData.get(context.getLevel().getServer())
                .ledger().get(ritual.ritualId());
        context.assertValueEqual(CooperativeRitual.State.SUCCEEDED, terminal.state(),
                "the compatibility circle should settle synchronously as one combined cast");
        double spent = 2_000.0 - ManaData.available(leader) - ManaData.available(second);
        double committedMana = terminal.participants().stream()
                .mapToDouble(CooperativeRitual.Participant::allocatedMana).sum();
        double committedUpkeep = terminal.participants().stream()
                .mapToDouble(CooperativeRitual.Participant::allocatedUpkeep).sum();
        context.assertTrue(committedMana > 0.0 && committedMana <= 1_000.0,
                "the combined quote must consume a positive amount below approved maxima");
        context.assertValueEqual(0.0, committedUpkeep,
                "a successful circle with no continuing handles must refund all approved upkeep");
        context.assertTrue(Math.abs(spent - committedMana) <= 1.0e-9,
                "only committed mana may remain charged after unused upkeep refunds");
        context.assertValueEqual(0, leader.getInventory().countItem(Items.QUARTZ),
                "successful execution must consume the exact approved offering");
        cleanup(context, leader, second);
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = "priority25_ritual_isolated")
    public void replicatedVmCircleRunsOneCopyPerContributor(GameTestHelper context) {
        ServerPlayer leader = player(context);
        ServerPlayer second = player(context);
        prepare(leader);
        prepare(second);
        loadPersistentVmCircle(leader);
        leader.getInventory().placeItemBackInInventory(new ItemStack(Items.QUARTZ));
        CastingResourceService.stageOffering(leader, 1);
        CooperativeRitual ritual = createAndInvite(leader, second, CooperativeRitual.Mode.REPLICATE);
        CooperativeRitualService.approve(leader, shortId(ritual));
        CooperativeRitualService.approve(second, shortId(ritual));
        double secondReservedMana = ManaData.available(second);
        context.assertTrue(CooperativeRitualService.start(leader, shortId(ritual)),
                "replicate mode should queue one bounded VM copy per contributor");
        context.assertTrue(!CooperativeRitualService.approve(second, shortId(ritual)),
                "approval retries must be rejected after execution starts");
        context.assertValueEqual(secondReservedMana, ManaData.available(second),
                "a post-start approval retry must not refund a live contributor escrow");
        context.assertValueEqual(1, CastingResourceService.ritualEscrows(second).escrows().size(),
                "the live contributor escrow must remain durable until every copy settles");

        context.runAfterDelay(90, () -> {
            CooperativeRitual terminal = CooperativeRitualSavedData.get(context.getLevel().getServer())
                    .ledger().get(ritual.ritualId());
            context.assertValueEqual(CooperativeRitual.State.SUCCEEDED, terminal.state(),
                    "both deterministic VM copies should settle the shared ritual once");
            context.assertValueEqual(0, CastingResourceService.ritualEscrows(leader).escrows().size(),
                    "leader player-NBT escrow must settle once after both copies finish");
            context.assertValueEqual(0, CastingResourceService.ritualEscrows(second).escrows().size(),
                    "contributor player-NBT escrow must settle once after both copies finish");
            assertApprovedPersistentUpkeep(context, leader, terminal);
            assertApprovedPersistentUpkeep(context, second, terminal);
            cleanup(context, leader, second);
        });
    }

    @GameTest(template = "empty", batch = "priority25_ritual_isolated")
    public void approvalRetryUsesOnePlayerEscrowRecord(GameTestHelper context) {
        ServerPlayer leader = player(context);
        ServerPlayer second = player(context);
        prepare(leader);
        prepare(second);
        leader.getInventory().placeItemBackInInventory(new ItemStack(Items.QUARTZ));
        CastingResourceService.stageOffering(leader, 1);
        CooperativeRitual ritual = createAndInvite(leader, second, CooperativeRitual.Mode.SPLIT);
        context.assertTrue(CooperativeRitualService.approve(leader, shortId(ritual)),
                "first approval should reserve once");
        context.assertTrue(CooperativeRitualService.approve(leader, shortId(ritual)),
                "identical retry should converge without a second debit");
        context.assertValueEqual(400.0, ManaData.available(leader),
                "identical approval retry must not debit a second maximum");
        context.assertValueEqual(1, CastingResourceService.ritualEscrows(leader).escrows().size(),
                "identical approval retry must keep one checksummed escrow record");
        CooperativeRitualService.decline(second, shortId(ritual));
        cleanup(context, leader, second);
    }

    private static CooperativeRitual createAndInvite(ServerPlayer leader, ServerPlayer second,
            CooperativeRitual.Mode mode) {
        CooperativeRitual ritual = CooperativeRitualService.create(leader, mode,
                new CooperativeRitual.Terms(500.0, 1, 100.0)).orElseThrow();
        if (!CooperativeRitualService.invite(leader, shortId(ritual), second,
                new CooperativeRitual.Terms(500.0, 0, 100.0))) {
            throw new IllegalStateException("priority 25 fixture invitation was rejected");
        }
        return ritual;
    }

    private static void prepare(ServerPlayer player) {
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        CastingResourceService.clearStaged(player);
    }

    private static void loadPersistentVmCircle(ServerPlayer player) {
        CircleAuthoringService.newCircle(player, "cooperative-persistent-force");
        requireEdit(CircleAuthoringService.place(player, 0, 0, "VM_DURATION"));
        requireEdit(CircleAuthoringService.parameterize(player, 0, 0, "40"));
        requireEdit(CircleAuthoringService.place(player, 0, 1, "VM_PUSH_SELF"));
        requireEdit(CircleAuthoringService.place(player, 0, 2, "VM_PUSH_LOOK"));
        requireEdit(CircleAuthoringService.place(player, 0, 3, "VM_PUSH_NUMBER"));
        requireEdit(CircleAuthoringService.parameterize(player, 0, 3, "0.1"));
        requireEdit(CircleAuthoringService.place(player, 0, 4, "VM_MULTIPLY"));
        requireEdit(CircleAuthoringService.place(player, 1, 0, "VM_ACCELERATION"));
        requireEdit(CircleAuthoringService.session(player).parameterize(
                new CircleCoordinate(1, 0), java.util.List.of(
                        new CircleValue.NumberValue("1"),
                        new CircleValue.NumberValue("0"))).changed());
        requireEdit(CircleAuthoringService.place(player, 2, 0, "EXECUTE"));
    }

    private static void assertApprovedPersistentUpkeep(GameTestHelper context,
            ServerPlayer player, CooperativeRitual terminal) {
        java.util.List<PersistentEffectContract> contracts = PersistentEffectService.ownedBy(player);
        context.assertValueEqual(1, contracts.size(),
                "each replicate copy must transfer one continuing handle into the durable ledger");
        PersistentEffectContract contract = contracts.getFirst();
        context.assertValueEqual(PersistentEffectService.cooperativeEffectId(
                        terminal.ritualId(), player.getUUID()), contract.effectId(),
                "the persistent copy ID must be recoverable from ritual and contributor after restart");
        double approved = terminal.participant(player.getUUID()).allocatedUpkeep();
        long paymentSpan = Math.max(1L, contract.effectiveDeadlineTick() - contract.startTick());
        long installments = Math.max(1L,
                (paymentSpan + contract.upkeepIntervalTicks() - 1L)
                        / contract.upkeepIntervalTicks());
        double committed = contract.upkeepPerInterval() * installments;
        context.assertTrue(Math.abs(approved - committed) <= 1.0e-9,
                "persistent upkeep for " + player.getUUID() + " must equal approved "
                        + approved + ", found " + committed);
        PersistentEffectService.cancelCommitted(context.getLevel().getServer(), contract.effectId());
        PersistentEffectService.cancelCommitted(context.getLevel().getServer(), contract.effectId());
        context.assertValueEqual(0, PersistentEffectService.ownedBy(player).size(),
                "committed cooperative cleanup must be idempotent");
    }

    private static void requireEdit(boolean changed) {
        if (!changed) throw new IllegalStateException("priority 25 persistent circle edit was rejected");
    }

    private static ServerPlayer player(GameTestHelper context) {
        return context.makeMockServerPlayerInLevel();
    }

    private static String shortId(CooperativeRitual ritual) {
        return ritual.ritualId().toString().substring(0, 8);
    }

    private static void cleanup(GameTestHelper context, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (context.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
                context.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        context.succeed();
    }
}
