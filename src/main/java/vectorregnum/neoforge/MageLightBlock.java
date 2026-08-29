package vectorregnum.neoforge;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import vectorregnum.neoforge.effect.PersistentEffectService;

/** A scheduled-tick light: expiry persists with the chunk across server restarts. */
public final class MageLightBlock extends Block {
    public static final int LIFETIME_TICKS = 1_200;
    public static final MapCodec<MageLightBlock> CODEC = simpleCodec(MageLightBlock::new);

    public MageLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
            BlockState oldState, boolean notify) {
        super.onPlace(state, level, pos, oldState, notify);
        if (!level.isClientSide()) level.scheduleTick(pos, this, LIFETIME_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        PersistentEffectService.onScheduledBlockTick(world, pos, this);
    }
}
