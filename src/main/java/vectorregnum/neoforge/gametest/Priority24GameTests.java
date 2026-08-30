package vectorregnum.neoforge.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.core.vm2.Instruction;
import vectorregnum.core.vm2.Priority24ProgramFactory;
import vectorregnum.core.vm2.Program;
import vectorregnum.core.vm2.RuntimeValue;
import vectorregnum.core.vm2.SourceLocation;
import vectorregnum.core.vm2.Vector3;
import vectorregnum.core.vm2.WorldAccess.CollisionTarget;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.NeoForgeVmService;

/** Real server-thread coverage for priority-24 world and message adapters. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority24GameTests {
    @GameTest(template = "empty", timeoutTicks = 20, batch = "priority24_adapter_isolated")
    public void collisionAdapterUsesAabbsAndRejectsUnsafeOperands(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        var first = context.spawn(EntityType.HUSK, new BlockPos(2, 2, 2));
        var second = context.spawn(EntityType.HUSK, new BlockPos(2, 2, 2));
        first.setNoAi(true);
        second.setNoAi(true);

        Vec3 entityOrigin = player.position().add(2.0, 0.0, 0.0);
        first.moveTo(entityOrigin.x, entityOrigin.y, entityOrigin.z, 0.0F, 0.0F);
        second.moveTo(entityOrigin.x, entityOrigin.y, entityOrigin.z, 0.0F, 0.0F);
        Vec3 velocity = first.getDeltaMovement();
        context.assertTrue(NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.EntityTarget(first.getStringUUID()),
                        new CollisionTarget.EntityTarget(second.getStringUUID()), 16.0),
                "overlapping entity AABBs should collide");
        context.assertTrue(NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.EntityTarget(first.getStringUUID()),
                        new CollisionTarget.PointTarget(toCore(first.getBoundingBox().getCenter())), 16.0),
                "a point inside an entity AABB should collide");
        context.assertTrue(!NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.EntityTarget(first.getStringUUID()),
                        new CollisionTarget.PointTarget(toCore(first.getBoundingBox().getCenter()
                                .add(2.0, 0.0, 0.0))), 16.0),
                "a point outside an entity AABB should not collide");
        context.assertTrue(NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.PointTarget(toCore(player.position())),
                        new CollisionTarget.PointTarget(toCore(player.position().add(5.0e-4, 0.0, 0.0))),
                        16.0),
                "near-identical points should collide within the epsilon");
        context.assertTrue(!NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.PointTarget(toCore(player.position())),
                        new CollisionTarget.PointTarget(toCore(player.position().add(0.01, 0.0, 0.0))),
                        16.0),
                "separated points should not collide");
        context.assertTrue(!NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.EntityTarget(first.getStringUUID()),
                        new CollisionTarget.EntityTarget(second.getStringUUID()), 1.0),
                "out-of-range entities must be rejected without a world mutation");
        context.assertTrue(!NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.EntityTarget(UUID.randomUUID().toString()),
                        new CollisionTarget.PointTarget(toCore(player.position())), 16.0),
                "missing entities must fail closed");
        context.assertTrue(!NeoForgeVmService.collisionProbeForTesting(player,
                        new CollisionTarget.PointTarget(toCore(player.position().add(10_000.0, 0.0, 0.0))),
                        new CollisionTarget.PointTarget(toCore(player.position())), 16.0),
                "far or unloaded points must fail closed");
        context.assertValueEqual(velocity, first.getDeltaMovement(),
                "collision inspection must not mutate entity motion");

        first.discard();
        second.discard();
        completeAfterCleanup(context, player);
    }

    @GameTest(template = "empty", timeoutTicks = 50, batch = "priority24_adapter_isolated")
    public void outOfRangeMessageIsRejectedBeforeAnyDelivery(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        AtomicReference<ResourceEscrow.Outcome> terminal = new AtomicReference<>();
        Vector3 farPoint = toCore(player.position().add(32.0, 0.0, 0.0));

        context.assertTrue(NeoForgeVmService.startAuthored(player,
                        outputProgram(farPoint, "must-not-deliver", 4.0), false,
                        "Priority 24 out-of-range message", terminal::set),
                "the message program should queue before its runtime range check");
        context.assertTrue(terminal.get() == null,
                "queueing must not synchronously execute or deliver the output");
        context.runAfterDelay(5, () -> {
            context.assertValueEqual(ResourceEscrow.Outcome.POLICY_REJECTED, terminal.get(),
                    "an output outside its declared owner range must be rejected");
            context.assertValueEqual(1_000.0, ManaData.available(player),
                    "policy rejection must refund the no-charge reservation exactly");
            context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                    "range rejection must close its reservation");
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50, batch = "priority24_adapter_isolated")
    public void inRangeOutputUsesTheAuthoritativeTickDeliveryPath(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        AtomicReference<ResourceEscrow.Outcome> terminal = new AtomicReference<>();
        Vector3 point = toCore(player.position().add(1.0, 0.0, 0.0));

        context.assertTrue(NeoForgeVmService.startAuthored(player,
                        outputProgram(point, "accepted-output", 8.0), false,
                        "Priority 24 in-range output", terminal::set),
                "the in-range message program should queue");
        context.runAfterDelay(5, () -> {
            context.assertValueEqual(ResourceEscrow.Outcome.SUCCESS, terminal.get(),
                    "a validated output should settle successfully after the server tick");
            context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                    "successful output delivery must close its reservation");
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50, batch = "priority24_vm_isolated")
    public void factoryDemoRunsBranchesCollisionAndMessagesOnServerTicks(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        AtomicReference<ResourceEscrow.Outcome> terminal = new AtomicReference<>();
        Program demo = Priority24ProgramFactory.create(player.getStringUUID(), toCore(player.position()));

        context.assertTrue(NeoForgeVmService.startAuthored(player, demo, false,
                        "Priority 24 factory demo", terminal::set),
                "the shared bounded demo should queue");
        context.assertTrue(terminal.get() == null,
                "the factory demo must wait for a later authoritative tick");
        context.runAfterDelay(20, () -> {
            context.assertValueEqual(ResourceEscrow.Outcome.SUCCESS, terminal.get(),
                    "the factory demo should complete its iterator, collision, signal, and child branch");
            context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                    "the completed demo must not leak its reservation");
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "priority24_vm_isolated")
    public void cancelledFactoryDemoSettlesOnceAndDoesNotLeak(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<ResourceEscrow.Outcome> terminal = new AtomicReference<>();
        Program demo = Priority24ProgramFactory.create(player.getStringUUID(), toCore(player.position()));

        context.assertTrue(NeoForgeVmService.startAuthored(player, demo, false,
                        "Priority 24 cancellation demo", outcome -> {
                            callbacks.incrementAndGet();
                            terminal.set(outcome);
                        }), "the demo should queue before cancellation");
        NeoForgeVmService.cancelOwner(player.getUUID(), "priority24 lifecycle GameTest");
        NeoForgeVmService.cancelOwner(player.getUUID(), "priority24 duplicate lifecycle GameTest");
        context.runAfterDelay(3, () -> {
            context.assertValueEqual(ResourceEscrow.Outcome.OWNER_LIFECYCLE, terminal.get(),
                    "owner cancellation must settle the queued VM through the lifecycle path");
            context.assertValueEqual(1, callbacks.get(),
                    "duplicate cancellation must notify the terminal callback once");
            context.assertValueEqual(1_000.0, ManaData.available(player),
                    "lifecycle cancellation must not consume mana");
            context.assertValueEqual(0, CastingResourceService.activeCount(player.getUUID()),
                    "lifecycle cancellation must close its reservation");
            completeAfterCleanup(context, player);
        });
    }

    private static Program outputProgram(Vector3 point, String text, double range) {
        return new Program(List.of(
                Instruction.push(new RuntimeValue.TextValue(text), source(0)),
                Instruction.push(new RuntimeValue.PointValue(point), source(1)),
                Instruction.output(range, source(2)),
                Instruction.halt(source(3))));
    }

    private static SourceLocation source(int index) {
        return SourceLocation.at(index, "PRIORITY24_" + index);
    }

    private static Vector3 toCore(Vec3 point) {
        return new Vector3(point.x, point.y, point.z);
    }

    private static ServerPlayer connectedCreativePlayer(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = true;
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    private static void completeAfterCleanup(GameTestHelper context, ServerPlayer player) {
        if (context.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
            context.getLevel().getServer().getPlayerList().remove(player);
        }
        context.succeed();
    }
}
