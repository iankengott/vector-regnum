package vectorregnum.neoforge.progression;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import vectorregnum.neoforge.world.ManaCrystalGeology;

/** An immovable, finite source node. Players construct and attune infrastructure around it. */
public final class ManaCrystalNodeBlock extends BaseEntityBlock {
    public static final MapCodec<ManaCrystalNodeBlock> CODEC = simpleCodec(ManaCrystalNodeBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 8);
    public static final IntegerProperty GROWTH_STAGE = IntegerProperty.create("growth_stage", 0,
            ManaSourceGrowthRules.MAX_GROWTH_STAGE);
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
    public static final EnumProperty<ManaAffinity> AFFINITY =
            EnumProperty.create("affinity", ManaAffinity.class);
    public static final int MANA_PER_CHARGE = 100;

    public ManaCrystalNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(CHARGE, 8)
                .setValue(GROWTH_STAGE, ManaSourceGrowthRules.MAX_GROWTH_STAGE)
                .setValue(NATURAL, false)
                .setValue(AFFINITY, ManaAffinity.ARCANE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaCrystalNodeBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE, GROWTH_STAGE, NATURAL, AFFINITY);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(NATURAL)) {
            return;
        }
        ManaSourceGrowthRules.Environment environment = new ManaSourceGrowthRules.Environment(
                true, geologicallySupported(level, pos), nearbyNaturalSources(level, pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ManaCrystalNodeBlockEntity crystal)) {
            return;
        }
        ManaSourceGrowthRules.SourceState current = crystal.sourceState(state);
        ManaSourceGrowthRules.SourceState next = ManaSourceGrowthRules.advance(current,
                environment, ManaSourceGrowthRules.TICKS_PER_DAY);
        if (!current.equals(next)) {
            level.setBlock(pos, state.setValue(GROWTH_STAGE, next.growthStage())
                    .setValue(CHARGE, next.charges()), Block.UPDATE_ALL);
            crystal.setProgress(next);
        } else {
            // The block entity is the persistence boundary for sub-day progress;
            // it must survive a temporarily unsupported or unloaded vein.
            crystal.setProgress(current);
        }
        level.scheduleTick(pos, this, ManaSourceGrowthRules.TICKS_PER_DAY);
    }

    private static boolean geologicallySupported(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            ManaCrystalGeology.HostRock host = ManaCrystalGeology.HostRock.from(
                    level.getBlockState(pos.relative(direction)).getBlock());
            if (host.conductivity() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Dense veins recharge more slowly: up to four days per 100-mana charge. */
    private static int nearbyNaturalSources(ServerLevel level, BlockPos origin) {
        int competing = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-4, -2, -4),
                origin.offset(4, 2, 4))) {
            if (candidate.equals(origin) || !level.hasChunkAt(candidate)) {
                continue;
            }
            BlockState nearby = level.getBlockState(candidate);
            if (nearby.is(ProgressionContent.MANA_CRYSTAL_NODE.get())
                    && nearby.getValue(NATURAL) && ++competing == 3) {
                break;
            }
        }
        return competing;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return stack.is(ProgressionContent.MANA_CRYSTAL_SHARD.get()) || tuningAffinity(stack) != null
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ManaAffinity tuning = tuningAffinity(stack);
        if (tuning != null) {
            level.setBlock(pos, state.setValue(AFFINITY, tuning), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            serverPlayer.sendSystemMessage(Component.literal("Crystal source tuned to "
                    + tuning.getSerializedName() + " resonance"), true);
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.is(ProgressionContent.MANA_CRYSTAL_SHARD.get())) {
            if (!ProgressionContent.manaBridge().consumeCapacityShard(
                    serverPlayer, ManaDrawRules.CAPACITY_PER_SHARD)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.vector_regnum.capacity_full"), true);
                return ItemInteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            ProgressionData.unlock(serverPlayer, ProgressionUnlock.CRYSTAL_HARVEST);
            serverPlayer.sendSystemMessage(Component.translatable("message.vector_regnum.capacity_grew",
                    ManaDrawRules.CAPACITY_PER_SHARD), true);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        int charges = state.getValue(CHARGE);
        if (charges == 0) {
            serverPlayer.sendSystemMessage(Component.translatable("message.vector_regnum.node_empty"), true);
            return InteractionResult.FAIL;
        }

        double distance = serverPlayer.position().distanceTo(Vec3.atCenterOf(pos));
        ManaAffinity sourceAffinity = state.getValue(AFFINITY);
        ManaAffinity requested = ProgressionContent.manaBridge().requestedAffinity(serverPlayer);
        int offered = ManaDrawRules.offeredMana(MANA_PER_CHARGE, distance, sourceAffinity, requested);
        if (offered <= 0 || !ProgressionContent.manaBridge().tryAcceptExact(
                serverPlayer, offered, sourceAffinity, pos)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.vector_regnum.no_mana_space"), true);
            return InteractionResult.FAIL;
        }

        ProgressionContent.manaBridge().attune(serverPlayer, pos, sourceAffinity);
        level.setBlock(pos, state.setValue(CHARGE, charges - 1), Block.UPDATE_ALL);
        level.updateNeighbourForOutputSignal(pos, this);
        ProgressionData.unlock(serverPlayer, ProgressionUnlock.MANA_STORAGE);
        serverPlayer.sendSystemMessage(Component.translatable("message.vector_regnum.drew_mana", offered), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return Math.round(state.getValue(CHARGE) * 15.0F / 8.0F);
    }

    private static ManaAffinity tuningAffinity(ItemStack stack) {
        if (stack.is(Items.BLAZE_POWDER)) return ManaAffinity.FIRE;
        if (stack.is(Items.SNOWBALL)) return ManaAffinity.FROST;
        if (stack.is(Items.ENDER_PEARL)) return ManaAffinity.VOID;
        if (stack.is(Items.AMETHYST_SHARD)) return ManaAffinity.ARCANE;
        return null;
    }
}
