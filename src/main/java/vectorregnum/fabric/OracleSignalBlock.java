package vectorregnum.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A full-strength redstone signal whose persisted scheduled tick removes it safely. */
public final class OracleSignalBlock extends Block {
    public static final int LIFETIME_TICKS = 100;
    public static final MapCodec<OracleSignalBlock> CODEC = createCodec(OracleSignalBlock::new);

    public OracleSignalBlock(Settings settings) {
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

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getWeakRedstonePower(
            BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    protected int getStrongRedstonePower(
            BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return 15;
    }
}
