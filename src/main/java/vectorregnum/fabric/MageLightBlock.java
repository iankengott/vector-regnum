package vectorregnum.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** A scheduled-tick light: expiry persists with the chunk across server restarts. */
public final class MageLightBlock extends Block {
    public static final int LIFETIME_TICKS = 1_200;
    public static final MapCodec<MageLightBlock> CODEC = createCodec(MageLightBlock::new);

    public MageLightBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos,
            BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient()) world.scheduleBlockTick(pos, this, LIFETIME_TICKS);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.isOf(this)) world.removeBlock(pos, false);
    }
}
