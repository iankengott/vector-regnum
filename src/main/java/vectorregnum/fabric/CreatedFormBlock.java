package vectorregnum.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Internal creation material whose scheduled removal survives normal server restarts. */
public final class CreatedFormBlock extends Block {
    public static final MapCodec<CreatedFormBlock> CODEC = createCodec(CreatedFormBlock::new);

    public CreatedFormBlock(Settings settings) { super(settings); }

    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.isOf(this)) world.removeBlock(pos, false);
    }
}
