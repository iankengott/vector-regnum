package vectorregnum.fabric.progression;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** An immovable, finite source node. Players construct and attune infrastructure around it. */
public final class ManaCrystalNodeBlock extends Block {
    public static final MapCodec<ManaCrystalNodeBlock> CODEC = createCodec(ManaCrystalNodeBlock::new);
    public static final IntProperty CHARGE = IntProperty.of("charge", 0, 8);
    public static final EnumProperty<ManaAffinity> AFFINITY =
            EnumProperty.of("affinity", ManaAffinity.class);
    public static final int MANA_PER_CHARGE = 100;

    public ManaCrystalNodeBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(CHARGE, 8).with(AFFINITY, ManaAffinity.ARCANE));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(CHARGE, AFFINITY);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
            BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient()) {
            return stack.isOf(ProgressionContent.MANA_CRYSTAL_SHARD)
                    ? ItemActionResult.SUCCESS
                    : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        ManaAffinity tuning = tuningAffinity(stack);
        if (tuning != null) {
            world.setBlockState(pos, state.with(AFFINITY, tuning), Block.NOTIFY_ALL);
            serverPlayer.sendMessage(Text.literal("Crystal source tuned to "
                    + tuning.asString() + " resonance"), true);
            return ItemActionResult.SUCCESS;
        }

        if (stack.isOf(ProgressionContent.MANA_CRYSTAL_SHARD)) {
            if (!ProgressionContent.manaBridge().consumeCapacityShard(
                    serverPlayer, ManaDrawRules.CAPACITY_PER_SHARD)) {
                serverPlayer.sendMessage(Text.translatable("message.vector_regnum.capacity_full"), true);
                return ItemActionResult.FAIL;
            }
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            ProgressionData.unlock(serverPlayer, ProgressionUnlock.CRYSTAL_HARVEST);
            serverPlayer.sendMessage(Text.translatable("message.vector_regnum.capacity_grew",
                    ManaDrawRules.CAPACITY_PER_SHARD), true);
            return ItemActionResult.SUCCESS;
        }

        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected net.minecraft.util.ActionResult onUse(BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return net.minecraft.util.ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return net.minecraft.util.ActionResult.PASS;
        }
        int charges = state.get(CHARGE);
        if (charges == 0) {
            serverPlayer.sendMessage(Text.translatable("message.vector_regnum.node_empty"), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        double distance = serverPlayer.getPos().distanceTo(Vec3d.ofCenter(pos));
        ManaAffinity sourceAffinity = state.get(AFFINITY);
        ManaAffinity requested = ProgressionContent.manaBridge().requestedAffinity(serverPlayer);
        int offered = ManaDrawRules.offeredMana(MANA_PER_CHARGE, distance, sourceAffinity, requested);
        if (offered <= 0 || !ProgressionContent.manaBridge().tryAcceptExact(
                serverPlayer, offered, sourceAffinity, pos)) {
            serverPlayer.sendMessage(Text.translatable("message.vector_regnum.no_mana_space"), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        ProgressionContent.manaBridge().attune(serverPlayer, pos, ManaAffinity.ARCANE);
        world.setBlockState(pos, state.with(CHARGE, charges - 1), Block.NOTIFY_ALL);
        world.updateComparators(pos, this);
        ProgressionData.unlock(serverPlayer, ProgressionUnlock.MANA_STORAGE);
        serverPlayer.sendMessage(Text.translatable("message.vector_regnum.drew_mana", offered), true);
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return Math.round(state.get(CHARGE) * 15.0f / 8.0f);
    }

    private static ManaAffinity tuningAffinity(ItemStack stack) {
        if (stack.isOf(Items.BLAZE_POWDER)) return ManaAffinity.FIRE;
        if (stack.isOf(Items.SNOWBALL)) return ManaAffinity.FROST;
        if (stack.isOf(Items.ENDER_PEARL)) return ManaAffinity.VOID;
        if (stack.isOf(Items.AMETHYST_SHARD)) return ManaAffinity.ARCANE;
        return null;
    }
}
