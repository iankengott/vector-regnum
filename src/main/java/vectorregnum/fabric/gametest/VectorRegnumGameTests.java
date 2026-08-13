package vectorregnum.fabric.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.tick.ChunkTickScheduler;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.fabric.CircleAuthoringService;
import vectorregnum.fabric.MageLightBlock;
import vectorregnum.fabric.ManaData;
import vectorregnum.fabric.OracleSignalBlock;
import vectorregnum.fabric.SpellMediaContent;
import vectorregnum.fabric.SpellTabletBlockEntity;
import vectorregnum.fabric.TemporarySpellContent;
import vectorregnum.fabric.progression.ManaAffinity;
import vectorregnum.fabric.progression.ManaCrystalNodeBlock;
import vectorregnum.fabric.progression.ManaCrystalNodeBlockEntity;
import vectorregnum.fabric.progression.ManaDrawRules;
import vectorregnum.fabric.progression.ManaSourceGrowthRules;
import vectorregnum.fabric.progression.ProgressionContent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production Fabric GameTests for server-authoritative integration boundaries.
 *
 * <p>The reload scenarios below exercise Minecraft's actual NBT codecs and tick
 * scheduler serialization. A complete process stop/start remains an external server
 * integration test because vanilla GameTest deliberately cannot reboot its host.</p>
 */
public final class VectorRegnumGameTests implements FabricGameTest {
    private static final BlockPos CRYSTAL_POS = new BlockPos(2, 1, 2);
    private static final BlockPos TABLET_SUPPORT_POS = new BlockPos(2, 1, 2);
    private static final BlockPos TABLET_POS = TABLET_SUPPORT_POS.up();

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void crystalNodeCanBePlaced(TestContext context) {
        context.setBlockState(CRYSTAL_POS, ProgressionContent.MANA_CRYSTAL_NODE.getDefaultState());
        context.expectBlock(ProgressionContent.MANA_CRYSTAL_NODE, CRYSTAL_POS);
        context.expectBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.AFFINITY, ManaAffinity.ARCANE);
        context.expectBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.CHARGE, 8);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void naturalSourceAdvancesOnlyWhenSupported(TestContext context) {
        var natural = ManaSourceGrowthRules.SourceState.youngNatural();
        var blocked = ManaSourceGrowthRules.advance(natural,
                new ManaSourceGrowthRules.Environment(true, false, 0),
                ManaSourceGrowthRules.TICKS_PER_DAY * 7L);
        context.assertEquals(natural, blocked, "unsupported geology must not advance");
        var advanced = ManaSourceGrowthRules.advance(natural,
                new ManaSourceGrowthRules.Environment(true, true, 0),
                ManaSourceGrowthRules.TICKS_PER_DAY * 3L);
        context.assertTrue(advanced.growthStage() > 0,
                "supported natural source should mature over three days");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void commandsUseRealParserAndKeepPlayersIsolated(TestContext context) {
        ServerPlayerEntity first = connectedCreativePlayer(context);
        ServerPlayerEntity second = connectedCreativePlayer(context);
        removePlayersAfterTest(context, first, second);

        execute(context, first, "vectorregnum mana attune fire", 1);
        execute(context, second, "vectorregnum mana attune frost", 1);
        execute(context, first, "vectorregnum circle new multiplayer-alpha", 1);
        execute(context, second, "vectorregnum circle new multiplayer-beta", 1);
        execute(context, first, "vectorregnum circle place 0 0 VM_PUSH_SELF", 1);

        context.assertEquals(ManaAffinity.FIRE, ManaData.affinity(first),
                "the first command source should retain its own affinity");
        context.assertEquals(ManaAffinity.FROST, ManaData.affinity(second),
                "the second command source should retain its own affinity");
        context.assertEquals("multiplayer-alpha", CircleAuthoringService.session(first).current().id(),
                "the first command source should edit only its own attachment/session");
        context.assertEquals("multiplayer-beta", CircleAuthoringService.session(second).current().id(),
                "the second command source should edit only its own attachment/session");
        context.assertEquals(1, CircleAuthoringService.session(first).current().sigils().size(),
                "the first player's edit should be retained");
        context.assertTrue(CircleAuthoringService.session(second).current().sigils().isEmpty(),
                "the first player's edit must not leak to the second player");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void playerAttachmentsSurviveMinecraftNbtRoundTrip(TestContext context) {
        ServerPlayerEntity original = connectedCreativePlayer(context);
        removePlayersAfterTest(context, original);
        ManaData.setForTesting(original, 600.0, 425.0);
        ManaData.setAffinity(original, ManaAffinity.VOID);
        ManaData.recordAttunedSource(original, context.getAbsolutePos(CRYSTAL_POS));
        ManaData.lockChannel(original, 77L);
        execute(context, original, "vectorregnum circle new persisted-circle", 1);
        execute(context, original, "vectorregnum circle place 0 0 VM_PUSH_SELF", 1);

        NbtCompound saved = original.writeNbt(new NbtCompound());
        ServerPlayerEntity reloaded = detachedPlayer(context);
        reloaded.readNbt(saved.copy());

        context.assertEquals(600.0, ManaData.capacity(reloaded),
                "capacity attachment should decode from player NBT");
        context.assertEquals(425.0, ManaData.available(reloaded),
                "mana attachment should decode from player NBT");
        context.assertEquals(ManaAffinity.VOID, ManaData.affinity(reloaded),
                "affinity attachment should decode from player NBT");
        context.assertEquals(ManaData.attunedSource(original), ManaData.attunedSource(reloaded),
                "source position attachment should decode from player NBT");
        context.assertEquals(ManaData.attunedDimension(original), ManaData.attunedDimension(reloaded),
                "source dimension attachment should decode from player NBT");
        context.assertEquals(ManaData.remainingLockTicks(original), ManaData.remainingLockTicks(reloaded),
                "channel lock attachment should decode from player NBT");
        context.assertEquals("persisted-circle", CircleAuthoringService.session(reloaded).current().id(),
                "authored-circle attachment should decode before a session is materialized");
        context.assertEquals(1, CircleAuthoringService.session(reloaded).current().sigils().size(),
                "authored-circle contents should survive player NBT");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void spellMediaSurviveItemStackCodecRoundTrips(TestContext context) {
        MagicCircle circle = artifactCircle();
        List<SpellArtifact> artifacts = List.of(
                SpellArtifact.scroll("gametest-scroll", circle),
                SpellArtifact.book("gametest-book", circle),
                SpellArtifact.tablet("gametest-tablet", circle));

        for (SpellArtifact artifact : artifacts) {
            ItemStack stack = CircleAuthoringService.createArtifactStack(artifact);
            NbtElement encoded = stack.encode(context.getWorld().getRegistryManager());
            ItemStack decoded = ItemStack.fromNbtOrEmpty(
                    context.getWorld().getRegistryManager(), (NbtCompound) encoded);
            context.assertTrue(!decoded.isEmpty(), artifact.medium() + " should decode as an item stack");
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(stack, decoded),
                    artifact.medium() + " item and data components should round-trip exactly");
            context.assertEquals(Optional.of(artifact), CircleAuthoringService.readArtifact(decoded),
                    artifact.medium() + " checksum payload should remain valid after item decoding");
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void tabletPlacementAndBlockEntityNbtRoundTripPreserveAnchor(TestContext context) {
        ServerPlayerEntity player = connectedCreativePlayer(context);
        removePlayersAfterTest(context, player);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        context.setBlockState(TABLET_SUPPORT_POS, Blocks.STONE);
        ItemStack tabletStack = CircleAuthoringService.createArtifactStack(
                SpellArtifact.tablet("placed-tablet", artifactCircle()));
        player.setStackInHand(Hand.MAIN_HAND, tabletStack);
        context.useStackOnBlock(player, tabletStack, TABLET_SUPPORT_POS, Direction.UP);
        context.expectBlock(SpellMediaContent.CARVED_TABLET, TABLET_POS);

        SpellTabletBlockEntity placed = context.getBlockEntity(TABLET_POS);
        var registries = context.getWorld().getRegistryManager();
        NbtCompound beforeInstall = placed.createNbtWithIdentifyingData(registries);
        SpellTabletBlockEntity recreated = new SpellTabletBlockEntity(
                context.getAbsolutePos(TABLET_POS), SpellMediaContent.CARVED_TABLET.getDefaultState());
        recreated.read(beforeInstall.copy(), registries);
        context.assertEquals(beforeInstall, recreated.createNbtWithIdentifyingData(registries),
                "tablet payload should survive block-entity reconstruction");

        context.getWorld().getChunk(context.getAbsolutePos(TABLET_POS)).setBlockEntity(recreated);
        recreated.activate(player);
        NbtCompound installed = recreated.createNbtWithIdentifyingData(registries);
        String payload = installed.getString("vector_regnum_artifact");
        SpellArtifact decoded = vectorregnum.core.circle.SpellArtifactPersistence.decode(payload);
        context.assertEquals(SpellArtifact.State.INSTALLED, decoded.state(),
                "first use should persist the installed lifecycle state");
        BlockPos absoluteTablet = context.getAbsolutePos(TABLET_POS);
        context.assertEquals(new SpellArtifact.WorldAnchor(
                        context.getWorld().getRegistryKey().getValue().toString(),
                        absoluteTablet.getX(), absoluteTablet.getY(), absoluteTablet.getZ()),
                decoded.installedAt().orElseThrow(),
                "tablet anchor should be the verified placed dimension and position");

        SpellTabletBlockEntity reloaded = new SpellTabletBlockEntity(
                absoluteTablet, SpellMediaContent.CARVED_TABLET.getDefaultState());
        reloaded.read(installed.copy(), registries);
        context.assertEquals(installed, reloaded.createNbtWithIdentifyingData(registries),
                "installed tablet lifecycle and anchor should survive another NBT round-trip");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void crystalInteractionsAreFiniteAndPlayerScoped(TestContext context) {
        ServerPlayerEntity consumer = connectedCreativePlayer(context);
        ServerPlayerEntity observer = connectedCreativePlayer(context);
        removePlayersAfterTest(context, consumer, observer);
        context.setBlockState(CRYSTAL_POS, ProgressionContent.MANA_CRYSTAL_NODE.getDefaultState());

        consumer.setStackInHand(Hand.MAIN_HAND,
                new ItemStack(ProgressionContent.MANA_CRYSTAL_SHARD));
        context.useBlock(CRYSTAL_POS, consumer);
        context.assertEquals((double) ManaDrawRules.CAPACITY_PER_SHARD, ManaData.capacity(consumer),
                "a shard interaction should grow only the acting player's capacity");
        context.assertEquals(0.0, ManaData.capacity(observer),
                "another player's capacity attachment must remain unchanged");

        consumer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BLAZE_POWDER));
        context.useBlock(CRYSTAL_POS, consumer);
        context.expectBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.AFFINITY, ManaAffinity.FIRE);
        ManaData.setAffinity(consumer, ManaAffinity.FIRE);
        ManaData.setForTesting(consumer, 500.0, 0.0);
        consumer.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        consumer.refreshPositionAndAngles(context.getAbsolutePos(CRYSTAL_POS).east(), 90.0F, 0.0F);
        context.useBlock(CRYSTAL_POS, consumer);

        context.assertTrue(ManaData.available(consumer) > 0.0,
                "a compatible nearby player should receive finite mana");
        context.assertEquals(0.0, ManaData.available(observer),
                "crystal draw must not credit a non-acting player");
        context.expectBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.CHARGE, 7);
        context.assertEquals(context.getAbsolutePos(CRYSTAL_POS), ManaData.attunedSource(consumer),
                "successful draw should attach the exact source position");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void naturalCrystalProgressSurvivesBlockEntityNbtRoundTrip(TestContext context) {
        BlockState natural = ProgressionContent.MANA_CRYSTAL_NODE.getDefaultState()
                .with(ManaCrystalNodeBlock.NATURAL, true)
                .with(ManaCrystalNodeBlock.GROWTH_STAGE, 1)
                .with(ManaCrystalNodeBlock.CHARGE, 2);
        context.setBlockState(CRYSTAL_POS, natural);
        ManaCrystalNodeBlockEntity original = context.getBlockEntity(CRYSTAL_POS);
        var expected = new ManaSourceGrowthRules.SourceState(
                ManaSourceGrowthRules.SourceOrigin.NATURAL, 1, 2, 12_345, 54_321);
        original.setProgress(expected);

        var registries = context.getWorld().getRegistryManager();
        NbtCompound saved = original.createNbtWithIdentifyingData(registries);
        ManaCrystalNodeBlockEntity reloaded = new ManaCrystalNodeBlockEntity(
                context.getAbsolutePos(CRYSTAL_POS), natural);
        reloaded.read(saved.copy(), registries);
        context.assertEquals(expected, reloaded.sourceState(natural),
                "natural source tick progress should survive block-entity reconstruction");
        context.assertEquals(saved, reloaded.createNbtWithIdentifyingData(registries),
                "natural source NBT should round-trip without losing persisted progress");
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 1_240)
    public void temporaryBlocksQueueAndExpireOnScheduledWorldTicks(TestContext context) {
        BlockPos light = new BlockPos(1, 1, 1);
        BlockPos oracle = new BlockPos(3, 1, 1);
        context.setBlockState(light, TemporarySpellContent.MAGE_LIGHT);
        context.setBlockState(oracle, TemporarySpellContent.ORACLE_SIGNAL);
        BlockPos absoluteLight = context.getAbsolutePos(light);
        BlockPos absoluteOracle = context.getAbsolutePos(oracle);
        context.assertTrue(context.getWorld().getBlockTickScheduler()
                        .isQueued(absoluteLight, TemporarySpellContent.MAGE_LIGHT),
                "mage light placement should enqueue a world block tick");
        context.assertTrue(context.getWorld().getBlockTickScheduler()
                        .isQueued(absoluteOracle, TemporarySpellContent.ORACLE_SIGNAL),
                "oracle placement should enqueue a world block tick");

        context.runAtTick(OracleSignalBlock.LIFETIME_TICKS + 1L, () -> {
            context.expectBlock(TemporarySpellContent.MAGE_LIGHT, light);
            context.expectBlock(Blocks.AIR, oracle);
        });
        context.waitAndRun(MageLightBlock.LIFETIME_TICKS + 1L, () -> {
            context.expectBlock(Blocks.AIR, light);
            context.expectBlock(Blocks.AIR, oracle);
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void scheduledEffectQueueSurvivesChunkTickNbtRoundTrip(TestContext context) {
        BlockPos light = new BlockPos(1, 1, 1);
        BlockPos oracle = new BlockPos(3, 1, 1);
        context.setBlockState(light, TemporarySpellContent.MAGE_LIGHT);
        context.setBlockState(oracle, TemporarySpellContent.ORACLE_SIGNAL);
        BlockPos absoluteLight = context.getAbsolutePos(light);
        BlockPos absoluteOracle = context.getAbsolutePos(oracle);
        long worldTime = context.getWorld().getTime();
        ChunkTickScheduler<Block> reloadedLight = reloadScheduler(context, absoluteLight, worldTime);
        ChunkTickScheduler<Block> reloadedOracle = reloadScheduler(context, absoluteOracle, worldTime);
        context.assertTrue(reloadedLight.isQueued(absoluteLight, TemporarySpellContent.MAGE_LIGHT),
                "serialized mage-light expiry should remain queued after scheduler reload");
        context.assertTrue(reloadedOracle.isQueued(absoluteOracle, TemporarySpellContent.ORACLE_SIGNAL),
                "serialized oracle expiry should remain queued after scheduler reload");
        context.complete();
    }

    private static ChunkTickScheduler<Block> reloadScheduler(
            TestContext context, BlockPos position, long worldTime) {
        var chunk = context.getWorld().getChunk(position);
        NbtList persisted = (NbtList) chunk.getTickSchedulers().blocks().toNbt(
                worldTime, block -> Registries.BLOCK.getId(block).toString());
        context.assertTrue(!persisted.isEmpty(),
                "owning chunk should serialize its scheduled spell effect");
        return ChunkTickScheduler.create(persisted.copy(),
                id -> Optional.ofNullable(Identifier.tryParse(id))
                        .flatMap(Registries.BLOCK::getOrEmpty), chunk.getPos());
    }

    private static ServerPlayerEntity connectedCreativePlayer(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = true;
        player.changeGameMode(GameMode.CREATIVE);
        return player;
    }

    private static ServerPlayerEntity detachedPlayer(TestContext context) {
        return new ServerPlayerEntity(context.getWorld().getServer(), context.getWorld(),
                new GameProfile(UUID.randomUUID(), "vr-reload-fixture"),
                SyncedClientOptions.createDefault());
    }

    private static void removePlayersAfterTest(
            TestContext context, ServerPlayerEntity... players) {
        context.addInstantFinalTask(() -> {
            for (ServerPlayerEntity player : players) {
                if (context.getWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != null) {
                    context.getWorld().getServer().getPlayerManager().remove(player);
                }
            }
        });
    }

    private static void execute(
            TestContext context, ServerPlayerEntity player, String command, int expectedResult) {
        CommandDispatcher<ServerCommandSource> dispatcher = context.getWorld().getServer()
                .getCommandManager().getDispatcher();
        try {
            int result = dispatcher.execute(dispatcher.parse(command,
                    player.getCommandSource().withLevel(4)));
            context.assertEquals(expectedResult, result,
                    "unexpected result for parsed command: /" + command);
        } catch (CommandSyntaxException exception) {
            context.throwGameTestException("command failed to parse/execute: /" + command
                    + " (" + exception.getMessage() + ")");
        }
    }

    private static MagicCircle artifactCircle() {
        return new MagicCircle(MagicCircle.CURRENT_SCHEMA_VERSION,
                "gametest-artifact", "GameTest Artifact", 1, 4, List.of(
                new PlacedSigil(new CircleCoordinate(0, 0), "ORIGIN_SELF"),
                new PlacedSigil(new CircleCoordinate(0, 1), "ELEMENT_FIRE"),
                new PlacedSigil(new CircleCoordinate(0, 2), "SHAPE_AURA"),
                new PlacedSigil(new CircleCoordinate(0, 3), "EXECUTE")));
    }
}
