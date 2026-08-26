package vectorregnum.neoforge.progression;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/** One of three persistent storage tiers which owns all server-side network transfer. */
public final class ManaReservoirBlock extends BaseEntityBlock {
    public static final MapCodec<ManaReservoirBlock> CODEC = simpleCodec(ManaReservoirBlock::new);
    public static final EnumProperty<ManaAffinity> AFFINITY =
            EnumProperty.create("affinity", ManaAffinity.class);
    private final ManaReservoir.Tier tier;
    private final ManaTransportRules.ConduitTier conduitTier;

    public ManaReservoirBlock(Properties properties) {
        this(properties, ManaReservoir.Tier.CRYSTAL_VIAL,
                ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    }

    public ManaReservoirBlock(Properties properties, ManaReservoir.Tier tier,
            ManaTransportRules.ConduitTier conduitTier) {
        super(properties);
        this.tier = tier;
        this.conduitTier = conduitTier;
        registerDefaultState(stateDefinition.any().setValue(AFFINITY, ManaAffinity.ARCANE));
    }

    public ManaReservoir.Tier tier() {
        return tier;
    }

    public ManaTransportRules.ConduitTier conduitTier() {
        return conduitTier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaReservoirBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AFFINITY);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,
                ProgressionContent.MANA_RESERVOIR_ENTITY.get(), ManaReservoirBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ManaAffinity affinity = tuningAffinity(stack);
        if (affinity == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof ManaReservoirBlockEntity reservoir) || !reservoir.canRetune()) {
            player.displayClientMessage(Component.translatable("message.vector_regnum.cell_tune_requires_empty"), true);
            return ItemInteractionResult.FAIL;
        }
        level.setBlock(pos, state.setValue(AFFINITY, affinity), Block.UPDATE_ALL);
        reservoir.setAffinity(affinity);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.vector_regnum.cell_tuned",
                affinity.getSerializedName()), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof ManaReservoirBlockEntity reservoir)) {
            return InteractionResult.FAIL;
        }
        if (!serverPlayer.isShiftKeyDown()) {
            reservoir.reportStatus(serverPlayer);
            return InteractionResult.SUCCESS;
        }
        return reservoir.drawTo(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ManaReservoirBlockEntity reservoir
                ? Math.round(reservoir.stored() * 15.0F / reservoir.capacity()) : 0;
    }

    private static ManaAffinity tuningAffinity(ItemStack stack) {
        return ManaTuningItems.affinity(stack).orElse(null);
    }
}
