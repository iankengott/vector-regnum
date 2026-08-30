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
import net.minecraft.world.phys.Vec3;
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
import vectorregnum.neoforge.effect.PersistentEffectService;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.neoforge.presentation.ServerTraces;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Makes NeoForge visual checks reproducible without enabling them in normal play. */
public final class DevShowcaseController {
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_PERSISTENT = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_PALETTE = new HashMap<>();
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
        PENDING_PERSISTENT.remove(event.getEntity().getUUID());
        PENDING_PALETTE.remove(event.getEntity().getUUID());
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
        PENDING_PERSISTENT.clear();
        PENDING_PALETTE.clear();
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
        tickPersistentCheckpoint(server);
        tickPalette(server);
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
        giveIfMissing(player, SpellMediaContent.engravedSpellCircleItem(),
                CircleAuthoringService.createArtifactStack(
                        SpellArtifact.engraving("showcase-engraving", mediaCircle)));
        giveIfMissing(player, SpellMediaContent.carvedTabletItem(),
                CircleAuthoringService.createArtifactStack(SpellArtifact.tablet("showcase-tablet", mediaCircle)));
        for (vectorregnum.core.casting.ReagentKind kind
                : vectorregnum.core.casting.ReagentKind.values()) {
            giveIfMissing(player, CastingResourceService.reagentItem(kind),
                    new ItemStack(CastingResourceService.reagentItem(kind), 8));
        }
        giveIfMissing(player, CastingResourceService.offeringItem(),
                new ItemStack(CastingResourceService.offeringItem(), 8));
        if (!CastingResourceService.stageOffering(player, 1)
                || !CastingResourceService.stage(player,
                        vectorregnum.core.casting.ReagentKind.MANA, 1)) {
            throw new IllegalStateException("priority-22 ritual resource staging failed");
        }
        CircleAuthoringService.quote(player, vectorregnum.core.casting.CastingMethod.RITUAL);
        CastingResourceService.clearStaged(player);

        CircleAuthoringService.loadVmStarter(player);
        var circle = CircleAuthoringService.session(player).current();
        var typedCompilation = CircleAuthoringService.compile(player);
        if (typedCompilation.hasErrors()) {
            throw new IllegalStateException("typed authored circle visual preflight failed");
        }

        var probe = NeoForgeVmService.perceptionProbe(player, 16.0);
        if (probe.status() != vectorregnum.core.vm2.TickResult.Status.HALTED
                || ProgressionSpellLibrary.ALL.size() != 20) {
            throw new IllegalStateException("vm2/library automated visual preflight failed");
        }

        CastService.cast(player, SpellPresets.FIREBOLT, false);
        CastService.cast(player, SpellPresets.ICE_NOVA, false);
        if (!LibrarySpellService.castForShowcase(player, "stone_aegis")
                || !LibrarySpellService.castForShowcase(player, "featherfall")
                || !LibrarySpellService.castForShowcase(player, "redstone_oracle")) {
            throw new IllegalStateException("priority-23 persistent-effect visual preflight failed");
        }
        // The priority-24 proof launches from the delayed persistent checkpoint,
        // after the earlier invocation preludes have expired. Staging every cue
        // on one tick makes the test obscure its own truth telegraphs.
        PENDING_PERSISTENT.put(player.getUUID(), 60);
        // Let the bounded spell/circle cues finish before presenting the
        // canonical grid. This makes the automated visual proof repeatable.
        PENDING_PALETTE.put(player.getUUID(),
                SpellVisualManager.DEV_SHOWCASE_DURATION_TICKS + 40);
        player.sendSystemMessage(Component.literal("VECTOR-REGNUM • PRIORITY 23 PERSISTENT MAGIC CHECKPOINT")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        int playerSchema = player.getData(PlayerAttachmentContent.PLAYER_DATA_SCHEMA);
        if (playerSchema != PlayerDataMigration.CURRENT_SCHEMA) {
            throw new IllegalStateException("player attachment schema did not persist migration");
        }
        VectorRegnumMod.LOGGER.info(
                "VISUAL_CHECKPOINT_READY milestone=priority_23 player={} circle_sigils={} "
                        + "library_spells={} automation_relay={} vm_status={} vm_cost={} duration_ticks={} "
                        + "persistence_claim={} player_schema={} unlocks_added={} create_renderer_probe={} "
                        + "element_palette_count=14 affinity_matrix=100,75,50,25 opposed_floor=25 "
                        + "natural_element=server_authoritative channel_attunement={} "
                        + "casting_methods=6 reagent_kinds=4 ritual_offering=quartz field_manual=12 "
                        + "persistent_contracts=queued five_spell_expansion=5",
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
                createRendererProbe,
                ManaData.channelAffinity(player).getSerializedName());
    }

    private static void launchPriority24Showcase(ServerPlayer player) {
        if (!NeoForgeVmService.launchPriority24Demo(player, outcome -> {
            if (outcome != vectorregnum.core.casting.ResourceEscrow.Outcome.SUCCESS) {
                throw new IllegalStateException(
                        "priority-24 shared-control visual demo settled as " + outcome);
            }
            player.sendSystemMessage(Component.literal(
                            "VECTOR-REGNUM • PRIORITY 24 SHARED CONTROL CHECKPOINT")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
            VectorRegnumMod.LOGGER.info(
                    "VISUAL_CHECKPOINT_READY milestone=priority_24 player={} "
                            + "shared_stack=atomic branch_order=stable_server_tick "
                            + "variables=64 iterators=16 iterator_steps=1024 "
                            + "active_branches=8 total_branches=32 signals=128 "
                            + "outputs=64 output_chars=256 field_manual=12 "
                            + "command=\"/vectorregnum vm control_demo\"",
                    player.getGameProfile().getName());
        })) {
            throw new IllegalStateException("priority-24 shared-control visual preflight failed");
        }
    }

    private static void tickPersistentCheckpoint(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_PERSISTENT.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks > 0) {
                entry.setValue(ticks);
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            var contracts = PersistentEffectService.ownedBy(player);
            if (contracts.isEmpty()) {
                throw new IllegalStateException(
                        "priority-23 showcase produced no durable persistent-effect contract");
            }
            double upkeep = contracts.stream()
                    .mapToDouble(vectorregnum.core.effect.PersistentEffectContract::prepaidUpkeep)
                    .sum();
            player.sendSystemMessage(Component.literal(String.format(java.util.Locale.ROOT,
                            "PERSISTENT MAGIC • %d active contract(s) • %.2f μ escrow left",
                            contracts.size(), upkeep))
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
            VectorRegnumMod.LOGGER.info(
                    "PERSISTENT_EFFECT_CHECKPOINT_READY player={} contracts={} upkeep_remaining={} "
                            + "saved_data={} field_manual=12 command=\"/vectorregnum effect status\"",
                    player.getGameProfile().getName(), contracts.size(), upkeep,
                    vectorregnum.neoforge.effect.PersistentEffectSavedData.FILE_ID);
            launchPriority24Showcase(player);
        }
    }

    private static void tickPalette(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING_PALETTE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks > 0) {
                entry.setValue(ticks);
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                stageElementPalette(player);
                VectorRegnumMod.LOGGER.info(
                        "ELEMENT_PALETTE_READY player={} count=14 duration_ticks={}",
                        player.getGameProfile().getName(),
                        SpellVisualManager.DEV_SHOWCASE_DURATION_TICKS);
            }
        }
    }

    /** Stages every canonical element through the authoritative trace choke point. */
    private static void stageElementPalette(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 forward = player.getViewVector(1.0F).normalize();
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1.0, 0.0, 0.0);
        else right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        // Keep the proof grid in front of the fixed showcase wall and inside a
        // normal field of view so all fourteen entries are inspectable at once.
        Vec3 center = player.getEyePosition().add(forward.scale(1.5)).add(up.scale(0.25));
        PresentationElement[] elements = {
                PresentationElement.ARCANE, PresentationElement.FIRE, PresentationElement.ICE,
                PresentationElement.VOID, PresentationElement.WATER, PresentationElement.AIR,
                PresentationElement.EARTH, PresentationElement.LIGHTNING, PresentationElement.TIME,
                PresentationElement.SPACE, PresentationElement.LIGHT, PresentationElement.DARK,
                PresentationElement.NATURE, PresentationElement.SOUND};
        for (int index = 0; index < elements.length; index++) {
            double horizontal = (index % 7 - 3) * 0.28;
            double vertical = index < 7 ? 0.22 : -0.22;
            Vec3 point = center.add(right.scale(horizontal)).add(up.scale(vertical));
            ServerTraces.motes(world, point, PresentationParticleStyle.MOTES,
                    elements[index], 0.12F, 0.70F,
                    SpellVisualManager.DEV_SHOWCASE_DURATION_TICKS);
        }
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
