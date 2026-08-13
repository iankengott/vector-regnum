package vectorregnum.fabric.multiplayer;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import vectorregnum.fabric.FabricVmService;
import vectorregnum.fabric.ManaData;
import vectorregnum.fabric.VectorRegnumMod;

/** Death/copy, disconnect, dimension, claim, team, and migration integration. */
public final class MultiplayerLifecycleService {
    private static final AttachmentType<Integer> PLAYER_SCHEMA = AttachmentRegistry.create(
            Identifier.of(VectorRegnumMod.MOD_ID, "player_data_schema"),
            builder -> builder.initializer(() -> 0).persistent(Codec.INT).copyOnDeath());
    private static final AttachmentType<ClaimLedger> WORLD_CLAIMS = AttachmentRegistry.create(
            Identifier.of(VectorRegnumMod.MOD_ID, "spell_claims"),
            builder -> builder.initializer(() -> ClaimLedger.EMPTY).persistent(ClaimLedger.CODEC));
    private static boolean initialized;

    private MultiplayerLifecycleService() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerPlayerEvents.JOIN.register(player -> migrate(player, false));
        ServerPlayerEvents.LEAVE.register(player -> FabricVmService.cancelOwner(
                player.getUuid(), "owner disconnected"));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            FabricVmService.cancelOwner(oldPlayer.getUuid(), alive
                    ? "player instance changed" : "owner died");
            migrate(newPlayer, !alive);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) ->
                FabricVmService.cancelOwner(player.getUuid(), "owner changed dimension"));
        registerCommands();
    }

    public static ClaimLedger claims(ServerWorld world) {
        ClaimLedger stored = world.getAttachedOrCreate(WORLD_CLAIMS);
        ClaimLedger migrated = stored.migrated();
        if (migrated != stored) world.setAttached(WORLD_CLAIMS, migrated);
        return migrated;
    }

    public static ClaimLedger.ClaimKey key(ServerWorld world, BlockPos pos) {
        return new ClaimLedger.ClaimKey(world.getRegistryKey().getValue().toString(),
                pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void migrate(ServerPlayerEntity player, boolean deathCopy) {
        int oldSchema = player.getAttachedOrCreate(PLAYER_SCHEMA);
        ManaData.migrateAndSanitize(player, deathCopy, oldSchema);
        player.setAttached(PLAYER_SCHEMA, PlayerDataMigration.CURRENT_SCHEMA);
        if (oldSchema != PlayerDataMigration.CURRENT_SCHEMA) {
            VectorRegnumMod.LOGGER.info("Migrated Vector-Regnum player {} from schema {} to {}",
                    player.getUuid(), oldSchema, PlayerDataMigration.CURRENT_SCHEMA);
        }
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("vectorregnum")
                        .then(CommandManager.literal("security")
                                .executes(context -> status(context.getSource()))
                                .then(CommandManager.literal("claim")
                                        .executes(context -> claim(context.getSource(),
                                                ClaimLedger.Access.OWNER_ONLY))
                                        .then(CommandManager.literal("private")
                                                .executes(context -> claim(context.getSource(),
                                                        ClaimLedger.Access.OWNER_ONLY)))
                                        .then(CommandManager.literal("team")
                                                .executes(context -> claim(context.getSource(),
                                                        ClaimLedger.Access.TEAM))))
                                .then(CommandManager.literal("release")
                                        .executes(context -> release(context.getSource()))))));
    }

    private static int claim(ServerCommandSource source, ClaimLedger.Access access) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return reject(source, "A player must create a spell claim");
        String team = SpellSecurityPolicy.teamName(player);
        if (access == ClaimLedger.Access.TEAM && team.isEmpty()) {
            return reject(source, "Join a scoreboard team before creating a team claim");
        }
        ServerWorld world = player.getServerWorld();
        ClaimLedger.Change change = claims(world).claim(key(world, player.getBlockPos()),
                player.getUuid(), team, access);
        if (change.accepted()) world.setAttached(WORLD_CLAIMS, change.ledger());
        player.sendMessage(Text.literal(change.message()).formatted(change.accepted()
                ? Formatting.GREEN : Formatting.RED), false);
        return change.accepted() ? 1 : 0;
    }

    private static int release(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return reject(source, "A player must release a spell claim");
        ServerWorld world = player.getServerWorld();
        ClaimLedger.Change change = claims(world).release(key(world, player.getBlockPos()),
                player.getUuid(), player.hasPermissionLevel(2));
        if (change.accepted()) world.setAttached(WORLD_CLAIMS, change.ledger());
        player.sendMessage(Text.literal(change.message()).formatted(change.accepted()
                ? Formatting.GREEN : Formatting.RED), false);
        return change.accepted() ? 1 : 0;
    }

    private static int status(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return reject(source, "A player is required");
        ServerWorld world = player.getServerWorld();
        var claim = claims(world).at(key(world, player.getBlockPos()));
        String description = claim.map(value -> "Owner " + value.owner() + " • "
                + value.access().name().toLowerCase() + (value.team().isEmpty()
                ? "" : " • team " + value.team())).orElse("This chunk is unclaimed");
        player.sendMessage(Text.literal(description).formatted(Formatting.AQUA), false);
        return 1;
    }

    private static int reject(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
    }
}
