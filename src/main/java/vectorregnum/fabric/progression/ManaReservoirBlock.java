package vectorregnum.fabric.progression;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One of three persistent storage tiers which owns all server-side network transfer. */
public final class ManaReservoirBlock extends BlockWithEntity {
    public static final MapCodec<ManaReservoirBlock> CODEC = createCodec(ManaReservoirBlock::new);
    public static final EnumProperty<ManaAffinity> AFFINITY =
            EnumProperty.of("affinity", ManaAffinity.class);
    private final ManaReservoir.Tier tier;
    private final ManaTransportRules.ConduitTier conduitTier;

    public ManaReservoirBlock(Settings settings) {
        this(settings, ManaReservoir.Tier.CRYSTAL_VIAL,
                ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    }

    public ManaReservoirBlock(Settings settings, ManaReservoir.Tier tier,
            ManaTransportRules.ConduitTier conduitTier) {
        super(settings);
        this.tier = tier;
        this.conduitTier = conduitTier;
        setDefaultState(getStateManager().getDefaultState().with(AFFINITY, ManaAffinity.ARCANE));
    }

    public ManaReservoir.Tier tier() {
        return tier;
    }

    public ManaTransportRules.ConduitTier conduitTier() {
        return conduitTier;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ManaReservoirBlockEntity(pos, state);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AFFINITY);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
            BlockEntityType<T> type) {
        return world.isClient() ? null : validateTicker(type,
                ProgressionContent.MANA_RESERVOIR_ENTITY, ManaReservoirBlockEntity::tick);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
            BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ManaAffinity affinity = tuningAffinity(stack);
        if (affinity == null) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient()) {
            return ItemActionResult.SUCCESS;
        }
        BlockEntity entity = world.getBlockEntity(pos);
        if (!(entity instanceof ManaReservoirBlockEntity reservoir) || !reservoir.canRetune()) {
            player.sendMessage(Text.translatable("message.vector_regnum.cell_tune_requires_empty"), true);
            return ItemActionResult.FAIL;
        }
        world.setBlockState(pos, state.with(AFFINITY, affinity), Block.NOTIFY_ALL);
        reservoir.setAffinity(affinity);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        player.sendMessage(Text.translatable("message.vector_regnum.cell_tuned", affinity.asString()), true);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world.getBlockEntity(pos) instanceof ManaReservoirBlockEntity reservoir)) {
            return ActionResult.FAIL;
        }
        if (!serverPlayer.isSneaking()) {
            reservoir.reportStatus(serverPlayer);
            return ActionResult.SUCCESS;
        }
        return reservoir.drawTo(serverPlayer) ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof ManaReservoirBlockEntity reservoir
                ? Math.round(reservoir.stored() * 15.0f / reservoir.capacity()) : 0;
    }

    private static ManaAffinity tuningAffinity(ItemStack stack) {
        if (stack.isOf(Items.BLAZE_POWDER)) return ManaAffinity.FIRE;
        if (stack.isOf(Items.SNOWBALL)) return ManaAffinity.FROST;
        if (stack.isOf(Items.ENDER_PEARL)) return ManaAffinity.VOID;
        if (stack.isOf(Items.AMETHYST_SHARD)) return ManaAffinity.ARCANE;
        return null;
    }
}
