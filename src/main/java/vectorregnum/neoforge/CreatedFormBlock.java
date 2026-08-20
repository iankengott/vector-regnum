package vectorregnum.neoforge;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Internal creation material whose scheduled removal survives normal server restarts. */
public final class CreatedFormBlock extends Block {
    public static final MapCodec<CreatedFormBlock> CODEC = simpleCodec(CreatedFormBlock::new);

    public CreatedFormBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (state.is(this)) world.removeBlock(pos, false);
    }
}
