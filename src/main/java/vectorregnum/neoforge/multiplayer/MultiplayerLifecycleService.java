package vectorregnum.neoforge.multiplayer;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.PlayerAttachmentContent;
import vectorregnum.neoforge.VectorRegnumMod;
import vectorregnum.neoforge.ponder.PonderTraceNetworking;

/** Death/copy, disconnect, dimension, claim, team, and migration integration. */
public final class MultiplayerLifecycleService {
    private MultiplayerLifecycleService() { }

    public static void initialize() {
        // NeoForge discovers the static event subscribers on the game bus.
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) migrate(player, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CastingResourceService.refundOwner(player, ResourceEscrow.Outcome.OWNER_LIFECYCLE);
        NeoForgeVmService.cancelOwner(player.getUUID(), "owner disconnected");
        PonderTraceNetworking.onDisconnect(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)
                || !(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        CastingResourceService.refundOwner(newPlayer, ResourceEscrow.Outcome.OWNER_LIFECYCLE);
        NeoForgeVmService.cancelOwner(oldPlayer.getUUID(), event.isWasDeath()
                ? "owner died" : "player instance changed");
        String originalNatural = oldPlayer.getData(PlayerAttachmentContent.NATURAL_ELEMENT);
        String cloneNatural = newPlayer.getData(PlayerAttachmentContent.NATURAL_ELEMENT);
        if ((cloneNatural == null || cloneNatural.isBlank())
                && originalNatural != null && !originalNatural.isBlank()) {
            newPlayer.setData(PlayerAttachmentContent.NATURAL_ELEMENT, originalNatural);
        }
        migrate(newPlayer, event.isWasDeath());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CastingResourceService.refundOwner(player, ResourceEscrow.Outcome.OWNER_LIFECYCLE);
            NeoForgeVmService.cancelOwner(player.getUUID(), "owner changed dimension");
        }
    }

    public static ClaimLedger claims(ServerLevel world) {
        ClaimSavedData storedData = ClaimSavedData.get(world);
        ClaimLedger stored = storedData.ledger();
        ClaimLedger migrated = stored.migrated();
        if (migrated != stored) storedData.replace(migrated);
        return migrated;
    }

    public static ClaimLedger.ClaimKey key(ServerLevel world, BlockPos pos) {
        return new ClaimLedger.ClaimKey(world.dimension().location().toString(),
                pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void migrate(ServerPlayer player, boolean deathCopy) {
        int oldSchema = player.getData(PlayerAttachmentContent.PLAYER_DATA_SCHEMA);
        ManaData.migrateAndSanitize(player, deathCopy, oldSchema);
        player.setData(PlayerAttachmentContent.PLAYER_DATA_SCHEMA,
                PlayerDataMigration.CURRENT_SCHEMA);
        VectorRegnumMod.LOGGER.info("priority21_identity player={} natural={} channel={} schema={}",
                player.getUUID(), ManaData.naturalElement(player).id(),
                ManaData.channelAffinity(player).getSerializedName(),
                PlayerDataMigration.CURRENT_SCHEMA);
        if (oldSchema != PlayerDataMigration.CURRENT_SCHEMA) {
            VectorRegnumMod.LOGGER.info("Migrated Vector-Regnum player {} from schema {} to {}",
                    player.getUUID(), oldSchema, PlayerDataMigration.CURRENT_SCHEMA);
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vectorregnum")
                        .then(Commands.literal("security")
                                .executes(context -> status(context.getSource()))
                                .then(Commands.literal("claim")
                                        .executes(context -> claim(context.getSource(),
                                                ClaimLedger.Access.OWNER_ONLY))
                                        .then(Commands.literal("private")
                                                .executes(context -> claim(context.getSource(),
                                                        ClaimLedger.Access.OWNER_ONLY)))
                                        .then(Commands.literal("team")
                                                .executes(context -> claim(context.getSource(),
                                                        ClaimLedger.Access.TEAM))))
                                .then(Commands.literal("release")
                                        .executes(context -> release(context.getSource())))));
    }

    private static int claim(CommandSourceStack source, ClaimLedger.Access access) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return reject(source, "A player must create a spell claim");
        String team = SpellSecurityPolicy.teamName(player);
        if (access == ClaimLedger.Access.TEAM && team.isEmpty()) {
            return reject(source, "Join a scoreboard team before creating a team claim");
        }
        ServerLevel world = player.serverLevel();
        ClaimLedger.Change change = claims(world).claim(key(world, player.blockPosition()),
                player.getUUID(), team, access);
        if (change.accepted()) ClaimSavedData.get(world).replace(change.ledger());
        player.sendSystemMessage(Component.literal(change.message()).withStyle(change.accepted()
                ? ChatFormatting.GREEN : ChatFormatting.RED));
        return change.accepted() ? 1 : 0;
    }

    private static int release(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return reject(source, "A player must release a spell claim");
        ServerLevel world = player.serverLevel();
        ClaimLedger.Change change = claims(world).release(key(world, player.blockPosition()),
                player.getUUID(), source.hasPermission(2));
        if (change.accepted()) ClaimSavedData.get(world).replace(change.ledger());
        player.sendSystemMessage(Component.literal(change.message()).withStyle(change.accepted()
                ? ChatFormatting.GREEN : ChatFormatting.RED));
        return change.accepted() ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return reject(source, "A player is required");
        ServerLevel world = player.serverLevel();
        var claim = claims(world).at(key(world, player.blockPosition()));
        String description = claim.map(value -> "Owner " + value.owner() + " • "
                + value.access().name().toLowerCase() + (value.team().isEmpty()
                ? "" : " • team " + value.team())).orElse("This chunk is unclaimed");
        player.sendSystemMessage(Component.literal(description).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private static int reject(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
