package vectorregnum.neoforge.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.multiplayer.ClaimLedger;
import vectorregnum.neoforge.multiplayer.MultiplayerLifecycleService;

/** Real-server integration coverage for priority 17 lifecycle/security policy. */
public final class Priority17GameTests implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void claimCommandPersistsAndRejectsAnotherOwner(TestContext context) {
        ServerPlayerEntity owner = connectedCreativePlayer(context, "claim-owner");
        ServerPlayerEntity stranger = connectedCreativePlayer(context, "claim-stranger");
        removePlayersAfterTest(context, owner, stranger);
        execute(context, owner, "vectorregnum security claim private", 1);

        var key = MultiplayerLifecycleService.key(context.getWorld(), owner.getBlockPos());
        ClaimLedger ledger = MultiplayerLifecycleService.claims(context.getWorld());
        context.assertEquals(owner.getUuid(), ledger.at(key).orElseThrow().owner(),
                "claim attachment should retain its server-authoritative owner");
        context.assertTrue(!ledger.permits(key, stranger.getUuid(), "", false),
                "another player must not mutate the claimed chunk");
        execute(context, stranger, "vectorregnum security release", 0);
        execute(context, owner, "vectorregnum security release", 1);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void deathMigrationKeepsManaProgressAndCancelsRunningVm(TestContext context) {
        ServerPlayerEntity player = connectedCreativePlayer(context, "lifecycle-owner");
        removePlayersAfterTest(context, player);
        ManaData.setForTesting(player, 100, 80);
        ManaData.lockChannel(player, 200);
        ManaData.migrateAndSanitize(player, true, 1);
        context.assertEquals(100.0, ManaData.capacity(player),
                "death copy should preserve capacity progression");
        context.assertEquals(80.0, ManaData.available(player),
                "death copy should preserve held mana");
        context.assertTrue(!ManaData.isChannelLocked(player),
                "death copy should clear transient channel lock");
        NeoForgeVmService.cancelOwner(player.getUuid(), "gametest death fixture");
        context.complete();
    }

    private static ServerPlayerEntity connectedCreativePlayer(TestContext context, String name) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = true;
        player.changeGameMode(GameMode.CREATIVE);
        return player;
    }

    private static void removePlayersAfterTest(TestContext context, ServerPlayerEntity... players) {
        context.addInstantFinalTask(() -> {
            for (ServerPlayerEntity player : players) {
                if (context.getWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != null) {
                    context.getWorld().getServer().getPlayerManager().remove(player);
                }
            }
        });
    }

    private static void execute(TestContext context, ServerPlayerEntity player,
            String command, int expected) {
        CommandDispatcher<ServerCommandSource> dispatcher = context.getWorld().getServer()
                .getCommandManager().getDispatcher();
        try {
            int result = dispatcher.execute(dispatcher.parse(command,
                    player.getCommandSource().withLevel(0)));
            context.assertEquals(expected, result, "unexpected command result: /" + command);
        } catch (CommandSyntaxException exception) {
            context.throwGameTestException("command failed: /" + command + " ("
                    + exception.getMessage() + ")");
        }
    }
}
