package vectorregnum.neoforge.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.core.automation.AutomationRule;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.automation.AutomationContent;
import vectorregnum.neoforge.automation.AutomationRelayBlockEntity;
import vectorregnum.neoforge.multiplayer.ClaimLedger;
import vectorregnum.neoforge.multiplayer.ClaimSavedData;
import vectorregnum.neoforge.multiplayer.MultiplayerLifecycleService;

/** Real NeoForge coverage for priority 18's command, relay, redstone, and NBT boundaries. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority18GameTests {
    private static final BlockPos RELAY = new BlockPos(2, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 40)
    public void remoteActivationUsesProgrammedRelayAndServerQueue(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        context.setBlock(RELAY, AutomationContent.automationRelay());
        programCompatibilityCircle(context, player);
        BlockPos absolute = context.absolutePos(RELAY);

        execute(context, player, "vectorregnum automation program "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);
        execute(context, player, "vectorregnum automation trigger "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);

        context.runAfterDelay(3, () -> {
            AutomationRelayBlockEntity relay = context.getBlockEntity(RELAY);
            context.assertValueEqual(1L, relay.acceptedActivations(),
                    "remote command should enqueue exactly one immutable invocation");
            context.assertValueEqual(1L, relay.successfulActivations(),
                    "the server tick should execute the programmed circle for its owner");
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public void risingRedstoneCapturesBridgeValueAndDrivesComparator(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        context.setBlock(RELAY, AutomationContent.automationRelay());
        programCompatibilityCircle(context, player);
        BlockPos absolute = context.absolutePos(RELAY);
        execute(context, player, "vectorregnum automation program "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);

        context.runAtTickTime(context.getTick() + 2, () -> context.setBlock(RELAY.west(), Blocks.REDSTONE_BLOCK));
        context.runAfterDelay(6, () -> {
            AutomationRelayBlockEntity relay = context.getBlockEntity(RELAY);
            context.assertValueEqual(1L, relay.acceptedActivations(),
                    "one rising edge should enqueue one invocation");
            context.assertValueEqual(15, relay.comparatorOutput(),
                    "captured redstone data should bridge back out as comparator strength");
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty")
    public void relayProgramRuleAndCountersSurviveMinecraftNbt(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        context.setBlock(RELAY, AutomationContent.automationRelay());
        programCompatibilityCircle(context, player);
        AutomationRelayBlockEntity original = context.getBlockEntity(RELAY);
        original.configure(player, vectorregnum.neoforge.CircleAuthoringService.session(player).current(),
                new AutomationRule(AutomationRule.TriggerMode.CHANGE, 7, 33));

        var registries = context.getLevel().registryAccess();
        CompoundTag saved = original.saveWithFullMetadata(registries);
        AutomationRelayBlockEntity reloaded = new AutomationRelayBlockEntity(
                context.absolutePos(RELAY), AutomationContent.automationRelay().defaultBlockState());
        reloaded.loadWithComponents(saved.copy(), registries);

        context.assertValueEqual(player.getUUID(), reloaded.owner().orElseThrow(),
                "relay owner should survive block-entity NBT");
        context.assertValueEqual(new AutomationRule(AutomationRule.TriggerMode.CHANGE, 7, 33),
                reloaded.rule(), "programmable redstone rule should survive block-entity NBT");
        context.assertTrue(reloaded.configured(), "checksummed circle program should survive NBT");
        completeAfterCleanup(context, player);
    }

    @GameTest(template = "empty")
    public void privateClaimAndRelayOwnershipRejectRemoteStranger(GameTestHelper context) {
        ServerPlayer owner = connectedCreativePlayer(context);
        ServerPlayer stranger = connectedCreativePlayer(context);
        context.setBlock(RELAY, AutomationContent.automationRelay());
        programCompatibilityCircle(context, owner);
        BlockPos absolute = context.absolutePos(RELAY);
        clearPersistedClaim(context, owner);
        execute(context, owner, "vectorregnum security claim private", 1);
        execute(context, owner, "vectorregnum automation program "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 1);
        execute(context, stranger, "vectorregnum automation trigger "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ(), 0);
        AutomationRelayBlockEntity relay = context.getBlockEntity(RELAY);
        context.assertValueEqual(0L, relay.acceptedActivations(),
                "a foreign remote request must not enter the server queue");
        execute(context, owner, "vectorregnum security release", 1);
        completeAfterCleanup(context, owner, stranger);
    }

    private static void programCompatibilityCircle(GameTestHelper context, ServerPlayer player) {
        execute(context, player, "vectorregnum circle new automation-gametest", 1);
        execute(context, player, "vectorregnum circle place 0 0 ORIGIN_SELF", 1);
        execute(context, player, "vectorregnum circle place 0 1 ELEMENT_FIRE", 1);
        execute(context, player, "vectorregnum circle place 0 2 SHAPE_AURA", 1);
        execute(context, player, "vectorregnum circle place 0 3 EXECUTE", 1);
    }

    private static ServerPlayer connectedCreativePlayer(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = true;
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    private static void clearPersistedClaim(GameTestHelper context, ServerPlayer player) {
        var key = MultiplayerLifecycleService.key(context.getLevel(), player.blockPosition());
        ClaimLedger ledger = MultiplayerLifecycleService.claims(context.getLevel());
        if (ledger.at(key).isPresent()) {
            ClaimSavedData.get(context.getLevel()).replace(
                    ledger.release(key, player.getUUID(), true).ledger());
        }
    }

    private static void completeAfterCleanup(
            GameTestHelper context, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (context.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
                context.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        context.succeed();
    }

    private static void execute(GameTestHelper context, ServerPlayer player,
            String command, int expectedResult) {
        CommandDispatcher<CommandSourceStack> dispatcher = context.getLevel().getServer()
                .getCommands().getDispatcher();
        try {
            int result = dispatcher.execute(dispatcher.parse(command,
                    player.createCommandSourceStack().withPermission(0)));
            context.assertValueEqual(expectedResult, result, "unexpected command result: /" + command);
        } catch (CommandSyntaxException exception) {
            context.fail("command failed: /" + command + " ("
                    + exception.getMessage() + ")");
        }
    }
}
