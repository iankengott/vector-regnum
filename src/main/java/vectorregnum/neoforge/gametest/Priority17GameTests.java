package vectorregnum.neoforge.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.multiplayer.ClaimLedger;
import vectorregnum.neoforge.multiplayer.ClaimSavedData;
import vectorregnum.neoforge.multiplayer.MultiplayerLifecycleService;

/** Real-server integration coverage for priority 17 lifecycle/security policy. */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class Priority17GameTests {
    @GameTest(template = "empty")
    public void claimCommandPersistsAndRejectsAnotherOwner(GameTestHelper context) {
        ServerPlayer owner = connectedCreativePlayer(context, "claim-owner");
        ServerPlayer stranger = connectedCreativePlayer(context, "claim-stranger");
        clearPersistedClaim(context, owner);
        execute(context, owner, "vectorregnum security claim private", 1);

        var key = MultiplayerLifecycleService.key(context.getLevel(), owner.blockPosition());
        ClaimLedger ledger = MultiplayerLifecycleService.claims(context.getLevel());
        context.assertValueEqual(owner.getUUID(), ledger.at(key).orElseThrow().owner(),
                "claim attachment should retain its server-authoritative owner");
        context.assertTrue(!ledger.permits(key, stranger.getUUID(), "", false),
                "another player must not mutate the claimed chunk");
        execute(context, stranger, "vectorregnum security release", 0);
        execute(context, owner, "vectorregnum security release", 1);
        completeAfterCleanup(context, owner, stranger);
    }

    @GameTest(template = "empty")
    public void deathMigrationKeepsManaProgressAndCancelsRunningVm(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context, "lifecycle-owner");
        var natural = ManaData.naturalElement(player);
        ManaData.setForTesting(player, 100, 80);
        ManaData.lockChannel(player, 200);
        ManaData.migrateAndSanitize(player, true, 1);
        context.assertValueEqual(100.0, ManaData.capacity(player),
                "death copy should preserve capacity progression");
        context.assertValueEqual(80.0, ManaData.available(player),
                "death copy should preserve held mana");
        context.assertTrue(!ManaData.isChannelLocked(player),
                "death copy should clear transient channel lock");
        context.assertValueEqual(natural, ManaData.naturalElement(player),
                "death migration should preserve permanent natural identity");
        NeoForgeVmService.cancelOwner(player.getUUID(), "gametest death fixture");
        completeAfterCleanup(context, player);
    }

    private static ServerPlayer connectedCreativePlayer(GameTestHelper context, String name) {
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

    private static void completeAfterCleanup(GameTestHelper context, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (context.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
                context.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        context.succeed();
    }

    private static void execute(GameTestHelper context, ServerPlayer player,
            String command, int expected) {
        CommandDispatcher<CommandSourceStack> dispatcher = context.getLevel().getServer()
                .getCommands().getDispatcher();
        try {
            int result = dispatcher.execute(dispatcher.parse(command,
                    player.createCommandSourceStack().withPermission(0)));
            context.assertValueEqual(expected, result, "unexpected command result: /" + command);
        } catch (CommandSyntaxException exception) {
            context.fail("command failed: /" + command + " ("
                    + exception.getMessage() + ")");
        }
    }
}
