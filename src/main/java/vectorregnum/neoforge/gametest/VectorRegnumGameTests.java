package vectorregnum.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import vectorregnum.core.circle.CircleCoordinate;
import vectorregnum.core.circle.MagicCircle;
import vectorregnum.core.circle.PlacedSigil;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.neoforge.CastingResourceService;
import vectorregnum.neoforge.CircleAuthoringService;
import vectorregnum.neoforge.LibrarySpellService;
import vectorregnum.neoforge.MageLightBlock;
import vectorregnum.neoforge.ManaData;
import vectorregnum.neoforge.OracleSignalBlock;
import vectorregnum.neoforge.SpellMediaContent;
import vectorregnum.neoforge.SpellTabletBlockEntity;
import vectorregnum.neoforge.TemporarySpellContent;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlock;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlockEntity;
import vectorregnum.neoforge.progression.ManaDrawRules;
import vectorregnum.neoforge.progression.ManaSourceGrowthRules;
import vectorregnum.neoforge.progression.ProgressionContent;
import vectorregnum.neoforge.progression.ProgressionData;
import vectorregnum.neoforge.progression.ProgressionUnlock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production NeoForge GameTests for server-authoritative integration boundaries.
 *
 * <p>The reload scenarios below exercise Minecraft's actual NBT codecs and tick
 * scheduler serialization. A complete process stop/start remains an external server
 * integration test because vanilla GameTest deliberately cannot reboot its host.</p>
 */
@GameTestHolder("vector_regnum")
@PrefixGameTestTemplate(false)
public final class VectorRegnumGameTests {
    private static final BlockPos CRYSTAL_POS = new BlockPos(2, 1, 2);
    private static final BlockPos TABLET_SUPPORT_POS = new BlockPos(2, 1, 2);
    private static final BlockPos TABLET_POS = TABLET_SUPPORT_POS.above();

    @GameTest(template = "empty")
    public void crystalNodeCanBePlaced(GameTestHelper context) {
        context.setBlock(CRYSTAL_POS, ProgressionContent.manaCrystalNode().defaultBlockState());
        context.assertBlockPresent(ProgressionContent.manaCrystalNode(), CRYSTAL_POS);
        context.assertBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.AFFINITY, ManaAffinity.ARCANE);
        context.assertBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.CHARGE, 8);
        context.succeed();
    }

    @GameTest(template = "empty")
    public void naturalSourceAdvancesOnlyWhenSupported(GameTestHelper context) {
        var natural = ManaSourceGrowthRules.SourceState.youngNatural();
        var blocked = ManaSourceGrowthRules.advance(natural,
                new ManaSourceGrowthRules.Environment(true, false, 0),
                ManaSourceGrowthRules.TICKS_PER_DAY * 7L);
        context.assertValueEqual(natural, blocked, "unsupported geology must not advance");
        var advanced = ManaSourceGrowthRules.advance(natural,
                new ManaSourceGrowthRules.Environment(true, true, 0),
                ManaSourceGrowthRules.TICKS_PER_DAY * 3L);
        context.assertTrue(advanced.growthStage() > 0,
                "supported natural source should mature over three days");
        context.succeed();
    }

    @GameTest(template = "empty")
    public void commandsUseRealParserAndKeepPlayersIsolated(GameTestHelper context) {
        ServerPlayer first = connectedCreativePlayer(context);
        ServerPlayer second = connectedCreativePlayer(context);

        execute(context, first, "vectorregnum mana attune fire", 1);
        execute(context, second, "vectorregnum mana attune ice", 1);
        execute(context, first, "vectorregnum circle new multiplayer-alpha", 1);
        execute(context, second, "vectorregnum circle new multiplayer-beta", 1);
        execute(context, first, "vectorregnum circle place 0 0 VM_PUSH_SELF", 1);
        var firstNatural = ManaData.naturalElement(first);
        var secondNatural = ManaData.naturalElement(second);

        context.assertValueEqual(ManaAffinity.FIRE, ManaData.channelAffinity(first),
                "the first command source should retain its own affinity");
        context.assertValueEqual(ManaAffinity.ICE, ManaData.channelAffinity(second),
                "the second command source should retain its own affinity");
        context.assertValueEqual(firstNatural, ManaData.naturalElement(first),
                "the first player's natural identity should be stable");
        context.assertValueEqual(secondNatural, ManaData.naturalElement(second),
                "the second player's natural identity should be stable");
        context.assertValueEqual("multiplayer-alpha", CircleAuthoringService.session(first).current().id(),
                "the first command source should edit only its own attachment/session");
        context.assertValueEqual("multiplayer-beta", CircleAuthoringService.session(second).current().id(),
                "the second command source should edit only its own attachment/session");
        context.assertValueEqual(1, CircleAuthoringService.session(first).current().sigils().size(),
                "the first player's edit should be retained");
        context.assertTrue(CircleAuthoringService.session(second).current().sigils().isEmpty(),
                "the first player's edit must not leak to the second player");
        completeAfterCleanup(context, first, second);
    }

    @GameTest(template = "empty")
    public void playerAttachmentsSurviveMinecraftNbtRoundTrip(GameTestHelper context) {
        ServerPlayer original = connectedCreativePlayer(context);
        ManaData.setForTesting(original, 600.0, 425.0);
        ManaData.setChannelAffinity(original, ManaAffinity.VOID);
        ManaData.recordAttunedSource(original, context.absolutePos(CRYSTAL_POS));
        ManaData.lockChannel(original, 77L);
        execute(context, original, "vectorregnum circle new persisted-circle", 1);
        execute(context, original, "vectorregnum circle place 0 0 VM_PUSH_SELF", 1);
        original.getInventory().placeItemBackInInventory(new ItemStack(Items.SUGAR, 2));
        context.assertTrue(CastingResourceService.stage(original, ReagentKind.CASTING_TIME, 2),
                "reagent fixture should stage before player NBT save");

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        ServerPlayer reloaded = detachedPlayer(context);
        reloaded.load(saved.copy());

        context.assertValueEqual(600.0, ManaData.capacity(reloaded),
                "capacity attachment should decode from player NBT");
        context.assertValueEqual(425.0, ManaData.available(reloaded),
                "mana attachment should decode from player NBT");
        context.assertValueEqual(ManaAffinity.VOID, ManaData.channelAffinity(reloaded),
                "affinity attachment should decode from player NBT");
        context.assertValueEqual(ManaData.naturalElement(original), ManaData.naturalElement(reloaded),
                "natural element attachment should decode from player NBT");
        context.assertValueEqual(ManaData.attunedSource(original), ManaData.attunedSource(reloaded),
                "source position attachment should decode from player NBT");
        context.assertValueEqual(ManaData.attunedDimension(original), ManaData.attunedDimension(reloaded),
                "source dimension attachment should decode from player NBT");
        context.assertValueEqual(ManaData.remainingLockTicks(original), ManaData.remainingLockTicks(reloaded),
                "channel lock attachment should decode from player NBT");
        context.assertValueEqual("persisted-circle", CircleAuthoringService.session(reloaded).current().id(),
                "authored-circle attachment should decode before a session is materialized");
        context.assertValueEqual(1, CircleAuthoringService.session(reloaded).current().sigils().size(),
                "authored-circle contents should survive player NBT");
        context.assertValueEqual(2,
                CastingResourceService.staged(reloaded).units(ReagentKind.CASTING_TIME),
                "checksummed staged reagents should survive player NBT");
        CastingResourceService.clearStaged(original);
        completeAfterCleanup(context, original);
    }

    @GameTest(template = "empty")
    public void spellMediaSurviveItemStackCodecRoundTrips(GameTestHelper context) {
        MagicCircle circle = artifactCircle();
        List<SpellArtifact> artifacts = List.of(
                SpellArtifact.scroll("gametest-scroll", circle),
                SpellArtifact.book("gametest-book", circle),
                SpellArtifact.engraving("gametest-engraving", circle),
                SpellArtifact.tablet("gametest-tablet", circle));

        for (SpellArtifact artifact : artifacts) {
            ItemStack stack = CircleAuthoringService.createArtifactStack(artifact);
            Tag encoded = stack.save(context.getLevel().registryAccess());
            ItemStack decoded = ItemStack.parse(
                    context.getLevel().registryAccess(), encoded).orElse(ItemStack.EMPTY);
            context.assertTrue(!decoded.isEmpty(), artifact.medium() + " should decode as an item stack");
            context.assertTrue(ItemStack.matches(stack, decoded),
                    artifact.medium() + " item and data components should round-trip exactly");
            context.assertValueEqual(Optional.of(artifact), CircleAuthoringService.readArtifact(decoded),
                    artifact.medium() + " checksum payload should remain valid after item decoding");
        }
        context.succeed();
    }

    @GameTest(template = "empty")
    public void tabletPlacementAndBlockEntityNbtRoundTripPreserveAnchor(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        context.setBlock(TABLET_SUPPORT_POS, Blocks.STONE);
        ItemStack tabletStack = CircleAuthoringService.createArtifactStack(
                SpellArtifact.tablet("placed-tablet", artifactCircle()));
        player.setItemInHand(InteractionHand.MAIN_HAND, tabletStack);
        context.placeAt(player, tabletStack, TABLET_SUPPORT_POS, Direction.UP);
        context.assertBlockPresent(SpellMediaContent.carvedTablet(), TABLET_POS);

        SpellTabletBlockEntity placed = context.getBlockEntity(TABLET_POS);
        var registries = context.getLevel().registryAccess();
        CompoundTag beforeInstall = placed.saveWithFullMetadata(registries);
        SpellTabletBlockEntity recreated = new SpellTabletBlockEntity(
                context.absolutePos(TABLET_POS), SpellMediaContent.carvedTablet().defaultBlockState());
        recreated.loadWithComponents(beforeInstall.copy(), registries);
        context.assertValueEqual(beforeInstall, recreated.saveWithFullMetadata(registries),
                "tablet payload should survive block-entity reconstruction");

        context.getLevel().setBlockEntity(recreated);
        recreated.activate(player);
        CompoundTag installed = recreated.saveWithFullMetadata(registries);
        String payload = installed.getString("vector_regnum_artifact");
        SpellArtifact decoded = vectorregnum.core.circle.SpellArtifactPersistence.decode(payload);
        context.assertValueEqual(SpellArtifact.State.INSTALLED, decoded.state(),
                "first use should persist the installed lifecycle state");
        BlockPos absoluteTablet = context.absolutePos(TABLET_POS);
        context.assertValueEqual(new SpellArtifact.WorldAnchor(
                        context.getLevel().dimension().location().toString(),
                        absoluteTablet.getX(), absoluteTablet.getY(), absoluteTablet.getZ()),
                decoded.installedAt().orElseThrow(),
                "tablet anchor should be the verified placed dimension and position");

        SpellTabletBlockEntity reloaded = new SpellTabletBlockEntity(
                absoluteTablet, SpellMediaContent.carvedTablet().defaultBlockState());
        reloaded.loadWithComponents(installed.copy(), registries);
        context.assertValueEqual(installed, reloaded.saveWithFullMetadata(registries),
                "installed tablet lifecycle and anchor should survive another NBT round-trip");
        completeAfterCleanup(context, player);
    }

    @GameTest(template = "empty")
    public void crystalInteractionsAreFiniteAndPlayerScoped(GameTestHelper context) {
        ServerPlayer consumer = connectedCreativePlayer(context);
        ServerPlayer observer = connectedCreativePlayer(context);
        context.setBlock(CRYSTAL_POS, ProgressionContent.manaCrystalNode().defaultBlockState());

        consumer.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ProgressionContent.manaCrystalShard()));
        context.useBlock(CRYSTAL_POS, consumer);
        context.assertValueEqual((double) ManaDrawRules.CAPACITY_PER_SHARD, ManaData.capacity(consumer),
                "a shard interaction should grow only the acting player's capacity");
        context.assertValueEqual(0.0, ManaData.capacity(observer),
                "another player's capacity attachment must remain unchanged");

        consumer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_POWDER));
        context.useBlock(CRYSTAL_POS, consumer);
        context.assertBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.AFFINITY, ManaAffinity.FIRE);
        ManaData.setChannelAffinity(consumer, ManaAffinity.FIRE);
        ManaData.setForTesting(consumer, 500.0, 0.0);
        consumer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        consumer.moveTo(context.absolutePos(CRYSTAL_POS).east(), 90.0F, 0.0F);
        context.useBlock(CRYSTAL_POS, consumer);

        context.assertTrue(ManaData.available(consumer) > 0.0,
                "a compatible nearby player should receive finite mana");
        context.assertValueEqual(0.0, ManaData.available(observer),
                "crystal draw must not credit a non-acting player");
        context.assertBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.CHARGE, 7);
        context.assertValueEqual(context.absolutePos(CRYSTAL_POS), ManaData.attunedSource(consumer),
                "successful draw should attach the exact source position");
        completeAfterCleanup(context, consumer, observer);
    }

    @GameTest(template = "empty")
    public void insufficientRemoteSourceDrawIsAtomic(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        BlockState source = ProgressionContent.manaCrystalNode().defaultBlockState()
                .setValue(ManaCrystalNodeBlock.CHARGE, 1)
                .setValue(ManaCrystalNodeBlock.AFFINITY, ManaAffinity.ARCANE);
        context.setBlock(CRYSTAL_POS, source);
        ManaData.setForTesting(player, 500.0, 0.0);
        ManaData.setChannelAffinity(player, ManaAffinity.ARCANE);
        ManaData.recordAttunedSource(player, context.absolutePos(CRYSTAL_POS));
        player.moveTo(context.absolutePos(CRYSTAL_POS).east(), 90.0F, 0.0F);

        context.assertTrue(!ManaData.ensureAvailable(player, 150.0),
                "a one-charge source must reject a request larger than its total offer");
        context.assertValueEqual(0.0, ManaData.available(player),
                "a rejected remote draw must not partially credit player mana");
        context.assertBlockProperty(CRYSTAL_POS, ManaCrystalNodeBlock.CHARGE, 1);
        completeAfterCleanup(context, player);
    }

    @GameTest(template = "empty")
    public void naturalCrystalProgressSurvivesBlockEntityNbtRoundTrip(GameTestHelper context) {
        BlockState natural = ProgressionContent.manaCrystalNode().defaultBlockState()
                .setValue(ManaCrystalNodeBlock.NATURAL, true)
                .setValue(ManaCrystalNodeBlock.GROWTH_STAGE, 1)
                .setValue(ManaCrystalNodeBlock.CHARGE, 2);
        context.setBlock(CRYSTAL_POS, natural);
        ManaCrystalNodeBlockEntity original = context.getBlockEntity(CRYSTAL_POS);
        var expected = new ManaSourceGrowthRules.SourceState(
                ManaSourceGrowthRules.SourceOrigin.NATURAL, 1, 2, 12_345, 54_321);
        original.setProgress(expected);

        var registries = context.getLevel().registryAccess();
        CompoundTag saved = original.saveWithFullMetadata(registries);
        ManaCrystalNodeBlockEntity reloaded = new ManaCrystalNodeBlockEntity(
                context.absolutePos(CRYSTAL_POS), natural);
        reloaded.loadWithComponents(saved.copy(), registries);
        context.assertValueEqual(expected, reloaded.sourceState(natural),
                "natural source tick progress should survive block-entity reconstruction");
        context.assertValueEqual(saved, reloaded.saveWithFullMetadata(registries),
                "natural source NBT should round-trip without losing persisted progress");
        context.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1_240)
    public void temporaryBlocksQueueAndExpireOnScheduledWorldTicks(GameTestHelper context) {
        BlockPos light = new BlockPos(1, 1, 1);
        BlockPos oracle = new BlockPos(3, 1, 1);
        context.setBlock(light, TemporarySpellContent.mageLight());
        context.setBlock(oracle, TemporarySpellContent.oracleSignal());
        BlockPos absoluteLight = context.absolutePos(light);
        BlockPos absoluteOracle = context.absolutePos(oracle);
        context.assertTrue(context.getLevel().getBlockTicks()
                        .hasScheduledTick(absoluteLight, TemporarySpellContent.mageLight()),
                "mage light placement should enqueue a world block tick");
        context.assertTrue(context.getLevel().getBlockTicks()
                        .hasScheduledTick(absoluteOracle, TemporarySpellContent.oracleSignal()),
                "oracle placement should enqueue a world block tick");

        context.runAfterDelay(OracleSignalBlock.LIFETIME_TICKS + 1L, () -> {
            context.assertBlockPresent(TemporarySpellContent.mageLight(), light);
            context.assertBlockPresent(Blocks.AIR, oracle);
        });
        context.runAfterDelay(MageLightBlock.LIFETIME_TICKS + 1L, () -> {
            context.assertBlockPresent(Blocks.AIR, light);
            context.assertBlockPresent(Blocks.AIR, oracle);
            context.succeed();
        });
    }

    @GameTest(template = "empty")
    public void scheduledEffectQueueSurvivesChunkTickNbtRoundTrip(GameTestHelper context) {
        BlockPos light = new BlockPos(1, 1, 1);
        BlockPos oracle = new BlockPos(3, 1, 1);
        context.setBlock(light, TemporarySpellContent.mageLight());
        context.setBlock(oracle, TemporarySpellContent.oracleSignal());
        BlockPos absoluteLight = context.absolutePos(light);
        BlockPos absoluteOracle = context.absolutePos(oracle);
        long worldTime = context.getLevel().getGameTime();
        LevelChunkTicks<Block> reloadedLight = reloadScheduler(context, absoluteLight, worldTime);
        LevelChunkTicks<Block> reloadedOracle = reloadScheduler(context, absoluteOracle, worldTime);
        context.assertTrue(reloadedLight.hasScheduledTick(absoluteLight, TemporarySpellContent.mageLight()),
                "serialized mage-light expiry should remain queued after scheduler reload");
        context.assertTrue(reloadedOracle.hasScheduledTick(absoluteOracle, TemporarySpellContent.oracleSignal()),
                "serialized oracle expiry should remain queued after scheduler reload");
        context.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public void semanticImpulseQueuesFollowupVmWithoutMutatingActiveIteration(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        ProgressionData.unlock(player, ProgressionUnlock.MOVEMENT_WEAVING);
        context.assertTrue(LibrarySpellService.cast(player, "vector_step"),
                "Vector Step should queue its semantic VM");

        context.runAfterDelay(40, () -> {
            context.assertTrue(player.getDeltaMovement().lengthSqr() > 0.0,
                    "the follow-up impulse VM should execute on a later server tick");
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public void fireballUsesTheLibraryVmAndDamagesAVisibleBoundedImpact(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        player.moveTo(context.absolutePos(new BlockPos(1, 2, 2)).getCenter().x,
                context.absolutePos(new BlockPos(1, 2, 2)).getY(),
                context.absolutePos(new BlockPos(1, 2, 2)).getCenter().z,
                -90.0F, 0.0F);
        var target = context.spawn(EntityType.HUSK, new BlockPos(7, 2, 2));
        target.setNoAi(true);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        float health = target.getHealth();
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        ProgressionData.unlock(player, ProgressionUnlock.COMBAT_WEAVING);

        context.assertTrue(LibrarySpellService.cast(player, "fireball"),
                "Fireball should queue through the real library VM");
        context.runAfterDelay(45, () -> {
            context.assertTrue(target.getHealth() < health,
                    "Fireball should damage its visible impact target after the cast wind-up");
            context.assertTrue(target.getHealth() >= health - 20.0F,
                    "Fireball damage must remain inside the semantic damage cap");
            target.discard();
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public void tidalPrisonSlowsAtMostSixNearbyHostiles(GameTestHelper context) {
        BlockPos start = context.absolutePos(new BlockPos(5, 2, 5));
        ServerPlayer player = connectedCreativePlayer(context);
        player.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5,
                0.0F, 0.0F);
        var targets = List.of(
                context.spawn(EntityType.HUSK, new BlockPos(3, 2, 5)),
                context.spawn(EntityType.HUSK, new BlockPos(4, 2, 5)),
                context.spawn(EntityType.HUSK, new BlockPos(5, 2, 3)),
                context.spawn(EntityType.HUSK, new BlockPos(5, 2, 4)),
                context.spawn(EntityType.HUSK, new BlockPos(6, 2, 5)),
                context.spawn(EntityType.HUSK, new BlockPos(5, 2, 6)),
                context.spawn(EntityType.HUSK, new BlockPos(9, 2, 5)));
        targets.forEach(target -> target.setNoAi(true));
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        ProgressionData.unlock(player, ProgressionUnlock.COMBAT_WEAVING);

        context.assertTrue(LibrarySpellService.cast(player, "tidal_prison"),
                "Tidal Prison should queue through the real library VM");
        context.runAfterDelay(45, () -> {
            long slowed = targets.stream()
                    .filter(target -> target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)).count();
            context.assertValueEqual(6L, slowed,
                    "Tidal Prison should enforce its six-target semantic limit");
            int longestRemaining = targets.stream()
                    .map(target -> target.getEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(net.minecraft.world.effect.MobEffectInstance::getDuration)
                    .max().orElse(0);
            context.assertTrue(longestRemaining > 0 && longestRemaining <= 120,
                    "Tidal Prison should honor its explicit 120-tick duration");
            targets.forEach(net.minecraft.world.entity.Entity::discard);
            completeAfterCleanup(context, player);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public void tidalPrisonRejectsAnEmptyHostileSelectionBeforeCharging(GameTestHelper context) {
        ServerPlayer player = connectedCreativePlayer(context);
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        ProgressionData.unlock(player, ProgressionUnlock.COMBAT_WEAVING);
        double before = ManaData.available(player);

        context.assertTrue(!LibrarySpellService.cast(player, "tidal_prison"),
                "Tidal Prison should reject an empty hostile selection during preflight");
        context.assertTrue(ManaData.available(player) == before,
                "Rejected Tidal Prison preflight must not consume mana");
        completeAfterCleanup(context, player);
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public void teleportMovesTheCasterOnlyToAClearLoadedBlockFace(GameTestHelper context) {
        BlockPos start = new BlockPos(1, 2, 2);
        BlockPos target = new BlockPos(7, 3, 2);
        BlockPos destination = new BlockPos(6, 3, 2);
        context.setBlock(target, Blocks.STONE);
        context.setBlock(destination.below(), Blocks.STONE);
        ServerPlayer player = connectedCreativePlayer(context);
        BlockPos absoluteStart = context.absolutePos(start);
        player.moveTo(absoluteStart.getX() + 0.5, absoluteStart.getY(),
                absoluteStart.getZ() + 0.5, -90.0F, 0.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                net.minecraft.world.phys.Vec3.atCenterOf(context.absolutePos(target)));
        ManaData.setForTesting(player, 1_000.0, 1_000.0);
        ProgressionData.unlock(player, ProgressionUnlock.MOVEMENT_WEAVING);

        context.assertTrue(LibrarySpellService.cast(player, "teleport"),
                "Teleport should queue through the real library VM");
        context.runAfterDelay(45, () -> {
            context.assertValueEqual(context.absolutePos(destination), player.blockPosition(),
                    "Teleport should place the caster at the visible block face");
            context.assertTrue(player.getDeltaMovement().lengthSqr() == 0.0,
                    "Teleport should clear stale movement at arrival");
            completeAfterCleanup(context, player);
        });
    }

    private static LevelChunkTicks<Block> reloadScheduler(
            GameTestHelper context, BlockPos position, long worldTime) {
        LevelChunk chunk = context.getLevel().getChunkAt(position);
        ListTag persisted = (ListTag) chunk.getTicksForSerialization().blocks().save(
                worldTime, block -> BuiltInRegistries.BLOCK.getKey(block).toString());
        context.assertTrue(!persisted.isEmpty(),
                "owning chunk should serialize its scheduled spell effect");
        return LevelChunkTicks.load(persisted.copy(),
                id -> Optional.ofNullable(ResourceLocation.tryParse(id))
                        .flatMap(BuiltInRegistries.BLOCK::getOptional), chunk.getPos());
    }

    private static ServerPlayer connectedCreativePlayer(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = true;
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    private static ServerPlayer detachedPlayer(GameTestHelper context) {
        return new ServerPlayer(context.getLevel().getServer(), context.getLevel(),
                new GameProfile(UUID.randomUUID(), "vr-reload-fixture"), ClientInformation.createDefault());
    }

    private static void completeAfterCleanup(GameTestHelper context, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (context.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
                context.getLevel().getServer().getPlayerList().remove(player);
            }
        }
        context.succeed();
    }

    private static void execute(
            GameTestHelper context, ServerPlayer player, String command, int expectedResult) {
        CommandDispatcher<CommandSourceStack> dispatcher = context.getLevel().getServer()
                .getCommands().getDispatcher();
        try {
            int result = dispatcher.execute(dispatcher.parse(command,
                    player.createCommandSourceStack().withPermission(4)));
            context.assertValueEqual(expectedResult, result,
                    "unexpected result for parsed command: /" + command);
        } catch (CommandSyntaxException exception) {
            context.fail("command failed to parse/execute: /" + command
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
