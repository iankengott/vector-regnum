package vectorregnum.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.neoforge.progression.ProgressionContent;
import vectorregnum.neoforge.progression.ProgressionData;
import vectorregnum.neoforge.progression.ProgressionSpellLibrary;
import vectorregnum.core.semantic.CreationForm;
import vectorregnum.core.semantic.CreationMaterial;
import vectorregnum.core.semantic.CreationSpec;
import vectorregnum.neoforge.automation.AutomationContent;
import vectorregnum.neoforge.automation.AutomationRelayBlockEntity;
import vectorregnum.neoforge.multiplayer.ClaimLedger;
import vectorregnum.neoforge.multiplayer.ClaimSavedData;
import vectorregnum.neoforge.multiplayer.MultiplayerLifecycleService;
import vectorregnum.neoforge.multiplayer.PlayerDataMigration;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Makes NeoForge visual checks reproducible without enabling them in normal play. */
public final class DevShowcaseController {
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private static final java.util.List<StagedBlock> STAGED_BLOCKS = new java.util.ArrayList<>();
    private static boolean registered;

    private DevShowcaseController() {
    }

    public static void initialize() {
        if (!visualCheckRequested() || registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.register(DevShowcaseController.class);
    }

    /** Queues one showcase run after the player's login has completed. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING.put(player.getUUID(), 100);
        }
    }

    /** Drops a queued run when its player leaves before the one-shot delay expires. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    /** Restores only blocks that this controller placed, before levels close. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (StagedBlock staged : STAGED_BLOCKS) {
            if (staged.world.getBlockState(staged.pos).is(staged.expected)) {
                staged.world.setBlock(staged.pos, staged.previous, Block.UPDATE_ALL);
            }
        }
        STAGED_BLOCKS.clear();
        PENDING.clear();
        registered = false;
    }

    /** Advances delayed post-join work on the authoritative server thread. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks > 0) {
                entry.setValue(ticks);
                continue;
            }
            UUID playerId = entry.getKey();
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                runShowcase(player);
            }
        }
    }

    private static boolean visualCheckRequested() {
        return Boolean.getBoolean("vectorregnum.visualCheck")
                || "1".equals(System.getenv("VECTOR_REGNUM_VISUAL_CHECK"));
    }

    private static void runShowcase(ServerPlayer player) {
        ManaData.setForTesting(player, 2_000.0, 2_000.0);
        int unlocksAdded = ProgressionData.unlockAll(player);
        player.serverLevel().setDayTime(6000L);
        CircleAuthoringService.loadStarter(player);
        var mediaCircle = CircleAuthoringService.session(player).current();
        var compilation = CircleAuthoringService.compile(player);
        if (compilation.hasErrors()) {
            throw new IllegalStateException("development starter circle failed compilation");
        }

        BlockPos sourcePos = player.blockPosition().relative(player.getDirection(), 3);
        ClaimLedger.ClaimKey showcaseClaim = MultiplayerLifecycleService.key(
                player.serverLevel(), sourcePos);
        var priorClaim = MultiplayerLifecycleService.claims(player.serverLevel()).at(showcaseClaim);
        String persistenceClaim;
        if (priorClaim.isPresent()) {
            if (!priorClaim.orElseThrow().owner().equals(player.getUUID())) {
                throw new IllegalStateException("visual-check chunk belongs to another claim owner");
            }
            persistenceClaim = "restored";
        } else {
            ClaimLedger.Change change = MultiplayerLifecycleService.claims(player.serverLevel())
                    .claim(showcaseClaim, player.getUUID(), "", ClaimLedger.Access.OWNER_ONLY);
            if (!change.accepted()) {
                throw new IllegalStateException("visual-check persistence claim was rejected");
            }
            ClaimSavedData.get(player.serverLevel()).replace(change.ledger());
            persistenceClaim = "created";
        }
        if (player.serverLevel().isEmptyBlock(sourcePos)) {
            BlockState previous = player.serverLevel().getBlockState(sourcePos);
            player.serverLevel().setBlock(sourcePos,
                    ProgressionContent.manaCrystalNode().defaultBlockState(), Block.UPDATE_ALL);
            STAGED_BLOCKS.add(new StagedBlock(player.serverLevel(), sourcePos.immutable(),
                    previous, ProgressionContent.manaCrystalNode()));
        }
        BlockPos conduitPos = sourcePos.relative(player.getDirection().getClockWise());
        BlockPos vialPos = conduitPos.relative(player.getDirection().getClockWise());
        BlockPos relayPos = sourcePos.relative(player.getDirection().getCounterClockWise());
        stageIfAir(player.serverLevel(), conduitPos, ProgressionContent.rawCrystalConduit());
        stageIfAir(player.serverLevel(), vialPos, ProgressionContent.crystalVial());
        stageIfAir(player.serverLevel(), relayPos, AutomationContent.automationRelay());
        if (player.serverLevel().getBlockEntity(relayPos)
                instanceof AutomationRelayBlockEntity relay) {
            relay.configure(player, mediaCircle,
                    vectorregnum.core.automation.AutomationRule.risingEdge());
            relay.requestRemote(player);
        }
        SemanticCreationExecutor.create(player, new CreationSpec(CreationMaterial.ARCANE_FORCE,
                CreationForm.BARRIER, 8, 600, false));
        BlockPos createProbePos = sourcePos
                .relative(player.getDirection().getClockWise(), 4);
        boolean createRendererProbe = stageOptionalBlock(player.serverLevel(), createProbePos,
                ResourceLocation.fromNamespaceAndPath("create", "mechanical_press"));
        createRendererProbe &= stageOptionalBlock(player.serverLevel(),
                createProbePos.relative(player.getDirection().getClockWise()),
                ResourceLocation.fromNamespaceAndPath("create", "large_cogwheel"));
        giveIfMissing(player, ProgressionContent.manaCrystalShard(),
                new ItemStack(ProgressionContent.manaCrystalShard(), 8));
        giveIfMissing(player, ProgressionContent.crystalVialItem(),
                new ItemStack(ProgressionContent.crystalVialItem()));
        giveIfMissing(player, ProgressionContent.rawCrystalConduitItem(),
                new ItemStack(ProgressionContent.rawCrystalConduitItem(), 8));
        giveIfMissing(player, AutomationContent.automationRelayItem(),
                new ItemStack(AutomationContent.automationRelayItem()));
        giveIfMissing(player, SpellMediaContent.spellScroll(),
                CircleAuthoringService.createArtifactStack(SpellArtifact.scroll("showcase-scroll", mediaCircle)));
        giveIfMissing(player, SpellMediaContent.spellBook(),
                CircleAuthoringService.createArtifactStack(SpellArtifact.book("showcase-book", mediaCircle)));
        giveIfMissing(player, SpellMediaContent.carvedTabletItem(),
                CircleAuthoringService.createArtifactStack(SpellArtifact.tablet("showcase-tablet", mediaCircle)));

        CircleAuthoringService.loadVmStarter(player);
        var circle = CircleAuthoringService.session(player).current();
        var typedCompilation = CircleAuthoringService.compile(player);
        if (typedCompilation.hasErrors()
                || !NeoForgeVmService.launchVectorStep(player, false, 250, 0.25)) {
            throw new IllegalStateException("typed authored circle visual preflight failed");
        }

        var probe = NeoForgeVmService.perceptionProbe(player, 16.0);
        if (probe.status() != vectorregnum.core.vm2.TickResult.Status.HALTED
                || ProgressionSpellLibrary.ALL.size() != 15) {
            throw new IllegalStateException("vm2/library automated visual preflight failed");
        }

        CastService.cast(player, SpellPresets.FIREBOLT, false);
        CastService.cast(player, SpellPresets.FROST_NOVA, false);
        LibrarySpellService.castForShowcase(player, "aegis_shell");
        LibrarySpellService.castForShowcase(player, "featherfall");
        LibrarySpellService.castForShowcase(player, "redstone_oracle");
        player.sendSystemMessage(Component.literal("VECTOR-REGNUM • PRIORITIES 1–19 VISUAL CHECKPOINT")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        int playerSchema = player.getData(PlayerAttachmentContent.PLAYER_DATA_SCHEMA);
        if (playerSchema != PlayerDataMigration.CURRENT_SCHEMA) {
            throw new IllegalStateException("player attachment schema did not persist migration");
        }
        VectorRegnumMod.LOGGER.info(
                "VISUAL_CHECKPOINT_READY milestone=priorities_1_19 player={} circle_sigils={} "
                        + "library_spells={} automation_relay={} vm_status={} vm_cost={} duration_ticks={} "
                        + "persistence_claim={} player_schema={} unlocks_added={} create_renderer_probe={}",
                player.getGameProfile().getName(),
                circle.sigils().size(),
                ProgressionSpellLibrary.ALL.size(),
                player.serverLevel().getBlockState(relayPos).is(AutomationContent.automationRelay()),
                probe.status(),
                probe.cost().total(),
                SpellVisualManager.DEV_SHOWCASE_DURATION_TICKS,
                persistenceClaim,
                playerSchema,
                unlocksAdded,
                createRendererProbe);
    }

    private static void giveIfMissing(ServerPlayer player, Item item, ItemStack stack) {
        if (!player.getInventory().contains(candidate -> candidate.is(item))) {
            player.getInventory().add(stack);
        }
    }

    private static void stageIfAir(ServerLevel world, BlockPos pos, Block block) {
        if (!world.isEmptyBlock(pos)) return;
        BlockState previous = world.getBlockState(pos);
        world.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        STAGED_BLOCKS.add(new StagedBlock(world, pos.immutable(), previous, block));
    }

    private static boolean stageOptionalBlock(ServerLevel world, BlockPos pos,
            ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) return false;
        stageIfAir(world, pos, block);
        return world.getBlockState(pos).is(block);
    }

    private record StagedBlock(ServerLevel world, BlockPos pos, BlockState previous,
            Block expected) {
    }
}
