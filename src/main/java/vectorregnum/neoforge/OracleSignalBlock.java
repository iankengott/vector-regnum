package vectorregnum.neoforge;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import vectorregnum.neoforge.effect.PersistentEffectService;

/** A full-strength redstone signal whose persisted scheduled tick removes it safely. */
public final class OracleSignalBlock extends Block {
    public static final int LIFETIME_TICKS = 100;
    public static final MapCodec<OracleSignalBlock> CODEC = simpleCodec(OracleSignalBlock::new);

    public OracleSignalBlock(Properties properties) {
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

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(
            BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    protected int getDirectSignal(
            BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return 15;
    }
}
