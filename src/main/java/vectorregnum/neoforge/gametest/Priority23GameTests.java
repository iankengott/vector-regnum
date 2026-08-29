package vectorregnum.neoforge.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.core.effect.PersistentEffectContract;
import vectorregnum.core.effect.PersistentEffectLedger;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.RuntimeValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.WorldEffect;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.TemporarySpellContent;
import vectorregnum.neoforge.effect.PersistentEffectSavedData;
import vectorregnum.neoforge.effect.PersistentEffectService;

/** Real SavedData, world-handle, reconciliation, and cleanup coverage for priority 23. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority23GameTests {
    private static final BlockPos EFFECT_POS = new BlockPos(2, 1, 2);

    @GameTest(template = "empty")
    public void savedDataRoundTripPreservesVersionedContract(GameTestHelper context) {
        long now = context.getLevel().getGameTime();
        PersistentEffectContract contract = contract(context.getLevel(), UUID.randomUUID(),
                UUID.randomUUID(), context.absolutePos(EFFECT_POS), now, now + 40L,
                4.0, 1.0, 23L);
        PersistentEffectSavedData original = new PersistentEffectSavedData(
                PersistentEffectLedger.EMPTY.register(contract).ledger());
        CompoundTag encoded = original.save(new CompoundTag(), context.getLevel().registryAccess());
        PersistentEffectSavedData decoded = PersistentEffectSavedData.load(
                encoded, context.getLevel().registryAccess());

        context.assertValueEqual(contract, decoded.ledger().get(contract.effectId()),
                "Minecraft NBT must preserve the complete versioned effect contract");
        context.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "priority23_effects_isolated")
    public void naturalConclusionCleansWorldHandleExactlyOnce(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos absolute = context.absolutePos(EFFECT_POS);
        context.setBlock(EFFECT_POS, TemporarySpellContent.mageLight());
        long now = level.getGameTime();
        UUID id = register(level, contract(level, UUID.randomUUID(), UUID.randomUUID(),
                absolute, now, now + 3L, 2.0, 1.0, 2301L));

        context.runAfterDelay(7, () -> {
            context.assertBlockNotPresent(TemporarySpellContent.mageLight(), EFFECT_POS);
            context.assertTrue(PersistentEffectSavedData.get(level).ledger().get(id) == null,
                    "natural cleanup should remove the terminal ledger entry exactly once");
            PersistentEffectService.tickLevelForTesting(level);
            context.assertTrue(PersistentEffectSavedData.get(level).ledger().get(id) == null,
                    "a duplicate cleanup tick must remain a no-op");
            context.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "priority23_effects_isolated")
    public void loadedReconciliationDebitsExactUpkeepAndHonorsPerHandleDeadlines(
            GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        ManaData.setForTesting(player, 100.0, 100.0);
        BlockPos absolute = context.absolutePos(EFFECT_POS);
        context.setBlock(EFFECT_POS, TemporarySpellContent.mageLight());
        var reservation = CastingResourceService.begin(player, CastingMethod.BARE,
                CastingResourceService.baseline(CastingMethod.BARE,
                        1.0, 1, 4.0, 0.0), true, false, ItemStack.EMPTY)
                .orElseThrow();
        Program program = new Program(List.of(Instruction.halt(
                new SourceLocation(0, 1, 1, "PRIORITY23_TEST"))));
        var batch = PersistentEffectService.begin(player, program, reservation, 2302L);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
        batch.trackStatus(player, MobEffects.MOVEMENT_SLOWDOWN.value(), 0, 40);
        batch.trackBlock(absolute, TemporarySpellContent.mageLight(), 20);
        Vec3 start = player.position();
        batch.trackForce(new WorldEffect.FollowPath(player.getStringUUID(), List.of(
                new Vector3(start.x, start.y, start.z),
                new Vector3(start.x + 2.0, start.y, start.z)), 0.2, 40));
        context.assertTrue(batch.commit(reservation),
                "the real batch encoder should commit a status-effect handle");
        CastingResourceService.settle(reservation, ResourceEscrow.Outcome.SUCCESS);
        UUID id = batch.effectId();
        long now = level.getGameTime();

        PersistentEffectService.tickLevelForTesting(level);
        PersistentEffectContract paid = PersistentEffectSavedData.get(level).ledger().get(id);
        context.assertValueEqual(2.0, paid.prepaidUpkeep(),
                "the first loaded interval must debit exactly one quoted installment");
        context.assertValueEqual(now + 20L, paid.nextUpkeepTick(),
                "the durable cadence must advance exactly once");
        context.assertTrue(paid.handles().stream().anyMatch(handle ->
                        handle.startsWith("f|follow_path|") && handle.contains("|1|0.2|")),
                "follow-path progress must advance and persist instead of returning to waypoint zero");
        context.runAfterDelay(25, () -> {
            context.assertBlockNotPresent(TemporarySpellContent.mageLight(), EFFECT_POS);
            PersistentEffectContract stillActive = PersistentEffectSavedData.get(level).ledger().get(id);
            context.assertTrue(stillActive != null
                            && stillActive.state() == PersistentEffectContract.State.ACTIVE,
                    "the shorter block handle must conclude without ending the longer status handle");
            context.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "the longer status handle must remain active after the block endpoint");
            removeForTest(level, id);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            level.getServer().getPlayerList().remove(player);
            context.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30, batch = "priority23_effects_isolated")
    public void continuingForceTicksBeforeDelayedVmHalts(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setDeltaMovement(Vec3.ZERO);
        AtomicReference<ResourceEscrow.Outcome> terminal = new AtomicReference<>();
        Program program = new Program(List.of(
                Instruction.duration(12, SourceLocation.at(0, "DURATION")),
                Instruction.push(new RuntimeValue.EntityValue(player.getStringUUID()),
                        SourceLocation.at(1, "TARGET")),
                Instruction.push(new RuntimeValue.VectorValue(new Vector3(0.25, 0.0, 0.0)),
                        SourceLocation.at(2, "VECTOR")),
                Instruction.acceleration(1.0, 0.0, SourceLocation.at(3, "ACCELERATION")),
                Instruction.delay(6, SourceLocation.at(4, "DELAY")),
                Instruction.halt(SourceLocation.at(5, "HALT"))));

        context.assertTrue(NeoForgeVmService.startAuthored(player, program, false,
                        "Pre-halt persistent force test", terminal::set),
                "the delayed continuing-force program should enter the real VM service");
        context.runAfterDelay(4, () -> {
            context.assertTrue(terminal.get() == null,
                    "the VM must still be delayed when the continuing force is inspected");
            context.assertTrue(player.getDeltaMovement().x > 0.4,
                    "the force must keep applying during VM delay instead of pausing after one tick");
        });
        context.runAfterDelay(10, () -> {
            context.assertValueEqual(ResourceEscrow.Outcome.SUCCESS, terminal.get(),
                    "the delayed force program must finish successfully");
            List<PersistentEffectContract> contracts = PersistentEffectService.ownedBy(player);
            context.assertValueEqual(1, contracts.size(),
                    "the unexpired remainder must transfer into one durable contract at halt");
            context.assertTrue(contracts.getFirst().naturalDeadlineTick()
                            - contracts.getFirst().startTick() < 12L,
                    "the durable endpoint must retain elapsed pre-halt time");
            removeForTest(level, contracts.getFirst().effectId());
            level.getServer().getPlayerList().remove(player);
            context.succeed();
        });
    }

    @GameTest(template = "empty", batch = "priority23_effects_isolated")
    public void unpaidEffectPersistsCollapseBeforeBoundedCleanup(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos absolute = context.absolutePos(EFFECT_POS);
        context.setBlock(EFFECT_POS, TemporarySpellContent.mageLight());
        long now = level.getGameTime();
        UUID id = register(level, contract(level, UUID.randomUUID(), UUID.randomUUID(),
                absolute, now, now + 40L, 1.0, 2.0, 2303L));

        PersistentEffectService.tickLevelForTesting(level);
        PersistentEffectContract collapsed = PersistentEffectSavedData.get(level).ledger().get(id);
        context.assertValueEqual(PersistentEffectContract.State.COLLAPSED, collapsed.state(),
                "underpayment must persist collapse before any world cleanup");
        context.assertValueEqual(2303L, collapsed.collapseSeed(),
                "the collapse seed must remain deterministic across reconciliation");
        context.assertBlockPresent(TemporarySpellContent.mageLight(), EFFECT_POS);

        PersistentEffectService.tickLevelForTesting(level);
        context.assertBlockPresent(TemporarySpellContent.mageLight(), EFFECT_POS);
        context.assertValueEqual(PersistentEffectContract.State.COLLAPSE_EMITTED,
                PersistentEffectSavedData.get(level).ledger().get(id).state(),
                "collapse emission must be recorded separately before cleanup");
        PersistentEffectService.tickLevelForTesting(level);
        context.assertBlockNotPresent(TemporarySpellContent.mageLight(), EFFECT_POS);
        context.assertValueEqual(PersistentEffectContract.State.CLEANED,
                PersistentEffectSavedData.get(level).ledger().get(id).state(),
                "collapsed cleanup must become durable before ledger removal");
        PersistentEffectService.tickLevelForTesting(level);
        context.assertTrue(PersistentEffectSavedData.get(level).ledger().get(id) == null,
                "collapsed magic must finish idempotent ledger removal");

        context.setBlock(EFFECT_POS, TemporarySpellContent.mageLight());
        long hardStart = Math.max(0L, level.getGameTime() - 1L);
        long hardDeadline = level.getGameTime();
        UUID hardId = UUID.randomUUID();
        PersistentEffectContract noConclusion = new PersistentEffectContract(
                PersistentEffectContract.CURRENT_SCHEMA, hardId, UUID.randomUUID(),
                "sha256:priority23-hard-cap", level.dimension().location().toString(),
                0L, hardStart, PersistentEffectContract.NO_NATURAL_DEADLINE,
                hardDeadline, 20, hardStart, 0.0, 0.0, 2306L,
                PersistentEffectContract.State.ACTIVE,
                List.of("b|" + absolute.getX() + '|' + absolute.getY() + '|'
                        + absolute.getZ() + "|vector_regnum:mage_light|" + hardDeadline));
        register(level, noConclusion);
        PersistentEffectService.tickLevelForTesting(level);
        context.assertValueEqual(PersistentEffectContract.State.COLLAPSED,
                PersistentEffectSavedData.get(level).ledger().get(hardId).state(),
                "a production no-conclusion contract must reach deterministic hard-cap collapse");
        PersistentEffectService.tickLevelForTesting(level);
        PersistentEffectService.tickLevelForTesting(level);
        PersistentEffectService.tickLevelForTesting(level);
        context.assertTrue(PersistentEffectSavedData.get(level).ledger().get(hardId) == null,
                "hard-cap collapse must clean and remove through the production service");
        context.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "priority23_effects_isolated")
    public void offlineOwnerStillPaysAndReachesNaturalCleanup(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        BlockPos absolute = context.absolutePos(EFFECT_POS);
        context.setBlock(EFFECT_POS, TemporarySpellContent.mageLight());
        long now = level.getGameTime();
        UUID id = register(level, contract(level, UUID.randomUUID(), player.getUUID(),
                absolute, now, now + 5L, 2.0, 1.0, 2304L));
        level.getServer().getPlayerList().remove(player);

        PersistentEffectService.tickLevelForTesting(level);
        context.assertValueEqual(1.0,
                PersistentEffectSavedData.get(level).ledger().get(id).prepaidUpkeep(),
                "offline ownership must not make prepaid upkeep free");
        context.runAfterDelay(9, () -> {
            context.assertBlockNotPresent(TemporarySpellContent.mageLight(), EFFECT_POS);
            context.assertTrue(PersistentEffectSavedData.get(level).ledger().get(id) == null,
                    "offline ownership must not orphan natural cleanup");
            context.succeed();
        });
    }

    @GameTest(template = "empty", batch = "priority23_effects_isolated")
    public void unloadedChunkPausesWithoutChargingOrMutating(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        BlockPos unloaded = new BlockPos(30_000_000, 64, 30_000_000);
        context.assertTrue(!level.hasChunkAt(unloaded), "test target must begin unloaded");
        long now = level.getGameTime();
        UUID id = register(level, contract(level, UUID.randomUUID(), UUID.randomUUID(),
                unloaded, now, now + 40L, 4.0, 2.0, 2305L));

        PersistentEffectService.tickLevelForTesting(level);
        PersistentEffectContract waiting = PersistentEffectSavedData.get(level).ledger().get(id);
        context.assertValueEqual(0L, waiting.revision(),
                "unloaded reconciliation must not advance durable state");
        context.assertValueEqual(4.0, waiting.prepaidUpkeep(),
                "unloaded reconciliation must not debit upkeep early");
        removeForTest(level, id);
        context.succeed();
    }

    private static PersistentEffectContract contract(ServerLevel level, UUID id, UUID owner,
            BlockPos pos, long start, long natural, double balance, double installment, long seed) {
        return new PersistentEffectContract(PersistentEffectContract.CURRENT_SCHEMA,
                id, owner, "sha256:priority23-gametest",
                level.dimension().location().toString(), 0L, start, natural, natural,
                20, start, installment, balance, seed,
                PersistentEffectContract.State.ACTIVE,
                List.of("b|" + pos.getX() + '|' + pos.getY() + '|' + pos.getZ()
                        + "|vector_regnum:mage_light|" + natural));
    }

    private static UUID register(ServerLevel level, PersistentEffectContract contract) {
        PersistentEffectSavedData data = PersistentEffectSavedData.get(level);
        data.replace(data.ledger().register(contract).ledger());
        return contract.effectId();
    }

    private static void removeForTest(ServerLevel level, UUID id) {
        PersistentEffectSavedData data = PersistentEffectSavedData.get(level);
        PersistentEffectLedger ledger = data.ledger();
        PersistentEffectContract contract = ledger.get(id);
        if (contract == null) return;
        if (contract.state() != PersistentEffectContract.State.CLEANED) {
            ledger = ledger.completeCleanup(id).ledger();
        }
        data.replace(ledger.removeCleaned(id).ledger());
    }
}
