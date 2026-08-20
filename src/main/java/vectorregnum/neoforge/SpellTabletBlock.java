package vectorregnum.neoforge;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** An authored tablet becomes a permanent, reusable world-anchored spell. */
public final class SpellTabletBlock extends BlockWithEntity {
    public static final MapCodec<SpellTabletBlock> CODEC = createCodec(SpellTabletBlock::new);

    public SpellTabletBlock(Settings settings) {
        super(settings);
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
        return new SpellTabletBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (player instanceof ServerPlayerEntity serverPlayer
                && blockEntity instanceof SpellTabletBlockEntity tablet) {
            tablet.activate(serverPlayer);
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }
}
