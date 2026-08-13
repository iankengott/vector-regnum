package vectorregnum.fabric.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import vectorregnum.core.automation.AutomationRule;
import vectorregnum.fabric.ManaData;
import vectorregnum.fabric.automation.AutomationContent;
import vectorregnum.fabric.automation.AutomationRelayBlockEntity;

/** Real Fabric coverage for priority 18's command, relay, redstone, and NBT boundaries. */
public final class Priority18GameTests implements FabricGameTest {
    private static final BlockPos RELAY = new BlockPos(2, 1, 2);

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
    public void remoteActivationUsesProgrammedRelayAndServerQueue(TestContext context) {
        ServerPlayerEntity player = connectedCreativePlayer(context);
        removePlayerAfterTest(context, player);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        context.setBlockState(RELAY, AutomationContent.AUTOMATION_RELAY);
        programCompatibilityCircle(context, player);
        BlockPos absolute = context.getAbsolutePos(RELAY);

        execute(context, player, "vectorregnum automation program "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);
        execute(context, player, "vectorregnum automation trigger "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);

        context.waitAndRun(3, () -> {
            AutomationRelayBlockEntity relay = context.getBlockEntity(RELAY);
            context.assertEquals(1L, relay.acceptedActivations(),
                    "remote command should enqueue exactly one immutable invocation");
            context.assertEquals(1L, relay.successfulActivations(),
                    "the server tick should execute the programmed circle for its owner");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
    public void risingRedstoneCapturesBridgeValueAndDrivesComparator(TestContext context) {
        ServerPlayerEntity player = connectedCreativePlayer(context);
        removePlayerAfterTest(context, player);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        context.setBlockState(RELAY, AutomationContent.AUTOMATION_RELAY);
        programCompatibilityCircle(context, player);
        BlockPos absolute = context.getAbsolutePos(RELAY);
        execute(context, player, "vectorregnum automation program "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);

        context.runAtTick(2, () -> context.setBlockState(RELAY.west(), Blocks.REDSTONE_BLOCK));
        context.waitAndRun(6, () -> {
            AutomationRelayBlockEntity relay = context.getBlockEntity(RELAY);
            context.assertEquals(1L, relay.acceptedActivations(),
                    "one rising edge should enqueue one invocation");
            context.assertEquals(15, relay.comparatorOutput(),
                    "captured redstone data should bridge back out as comparator strength");
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void relayProgramRuleAndCountersSurviveMinecraftNbt(TestContext context) {
        ServerPlayerEntity player = connectedCreativePlayer(context);
        removePlayerAfterTest(context, player);
        context.setBlockState(RELAY, AutomationContent.AUTOMATION_RELAY);
        programCompatibilityCircle(context, player);
        AutomationRelayBlockEntity original = context.getBlockEntity(RELAY);
        original.configure(player, vectorregnum.fabric.CircleAuthoringService.session(player).current(),
                new AutomationRule(AutomationRule.TriggerMode.CHANGE, 7, 33));

        var registries = context.getWorld().getRegistryManager();
        NbtCompound saved = original.createNbtWithIdentifyingData(registries);
        AutomationRelayBlockEntity reloaded = new AutomationRelayBlockEntity(
                context.getAbsolutePos(RELAY), AutomationContent.AUTOMATION_RELAY.getDefaultState());
        reloaded.read(saved.copy(), registries);

        context.assertEquals(player.getUuid(), reloaded.owner().orElseThrow(),
                "relay owner should survive block-entity NBT");
        context.assertEquals(new AutomationRule(AutomationRule.TriggerMode.CHANGE, 7, 33),
                reloaded.rule(), "programmable redstone rule should survive block-entity NBT");
        context.assertTrue(reloaded.configured(), "checksummed circle program should survive NBT");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void privateClaimAndRelayOwnershipRejectRemoteStranger(TestContext context) {
        ServerPlayerEntity owner = connectedCreativePlayer(context);
        ServerPlayerEntity stranger = connectedCreativePlayer(context);
        removePlayersAfterTest(context, owner, stranger);
        context.setBlockState(RELAY, AutomationContent.AUTOMATION_RELAY);
        programCompatibilityCircle(context, owner);
        BlockPos absolute = context.getAbsolutePos(RELAY);
        execute(context, owner, "vectorregnum security claim private", 1);
        execute(context, owner, "vectorregnum automation program "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);
        execute(context, stranger, "vectorregnum automation trigger "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 0);
        AutomationRelayBlockEntity relay = context.getBlockEntity(RELAY);
        context.assertEquals(0L, relay.acceptedActivations(),
                "a foreign remote request must not enter the server queue");
        context.complete();
    }

    private static void programCompatibilityCircle(TestContext context, ServerPlayerEntity player) {
        execute(context, player, "vectorregnum circle new automation-gametest", 1);
        execute(context, player, "vectorregnum circle place 0 0 ORIGIN_SELF", 1);
        execute(context, player, "vectorregnum circle place 0 1 ELEMENT_FIRE", 1);
        execute(context, player, "vectorregnum circle place 0 2 SHAPE_AURA", 1);
        execute(context, player, "vectorregnum circle place 0 3 EXECUTE", 1);
    }

    private static ServerPlayerEntity connectedCreativePlayer(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = true;
        player.changeGameMode(GameMode.CREATIVE);
        return player;
    }

    private static void removePlayerAfterTest(TestContext context, ServerPlayerEntity player) {
        removePlayersAfterTest(context, player);
    }

    private static void removePlayersAfterTest(
            TestContext context, ServerPlayerEntity... players) {
        context.addInstantFinalTask(() -> {
            for (ServerPlayerEntity player : players) {
                if (context.getWorld().getServer().getPlayerManager()
                        .getPlayer(player.getUuid()) != null) {
                    context.getWorld().getServer().getPlayerManager().remove(player);
                }
            }
        });
    }

    private static void execute(TestContext context, ServerPlayerEntity player,
            String command, int expectedResult) {
        CommandDispatcher<ServerCommandSource> dispatcher = context.getWorld().getServer()
                .getCommandManager().getDispatcher();
        try {
            int result = dispatcher.execute(dispatcher.parse(command,
                    player.getCommandSource().withLevel(0)));
            context.assertEquals(expectedResult, result, "unexpected command result: /" + command);
        } catch (CommandSyntaxException exception) {
            context.throwGameTestException("command failed: /" + command + " ("
                    + exception.getMessage() + ")");
        }
    }
}
