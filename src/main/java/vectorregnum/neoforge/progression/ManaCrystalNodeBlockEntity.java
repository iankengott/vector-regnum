package vectorregnum.neoforge.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent tick progress for a natural mana-crystal source. */
public final class ManaCrystalNodeBlockEntity extends BlockEntity {
    private static final String RECHARGE_PROGRESS = "recharge_progress_ticks";
    private static final String GROWTH_PROGRESS = "growth_progress_ticks";

    private int rechargeProgressTicks;
    private int growthProgressTicks;

    public ManaCrystalNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ProgressionContent.MANA_CRYSTAL_NODE_ENTITY.get(), pos, state);
    }

    public ManaSourceGrowthRules.SourceState sourceState(BlockState state) {
        return new ManaSourceGrowthRules.SourceState(
                state.getValue(ManaCrystalNodeBlock.NATURAL)
                        ? ManaSourceGrowthRules.SourceOrigin.NATURAL
                        : ManaSourceGrowthRules.SourceOrigin.CONSTRUCTED,
                state.getValue(ManaCrystalNodeBlock.GROWTH_STAGE),
                state.getValue(ManaCrystalNodeBlock.CHARGE),
                rechargeProgressTicks, growthProgressTicks);
    }

    public void setProgress(ManaSourceGrowthRules.SourceState state) {
        rechargeProgressTicks = state.rechargeProgressTicks();
        growthProgressTicks = state.growthProgressTicks();
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            ManaAffinity affinity = state.getValue(ManaCrystalNodeBlock.AFFINITY);
            if (affinity != affinity.canonical()) {
                level.setBlock(worldPosition,
                        state.setValue(ManaCrystalNodeBlock.AFFINITY, affinity.canonical()),
                        Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rechargeProgressTicks = clampProgress(tag.getInt(RECHARGE_PROGRESS));
        growthProgressTicks = clampProgress(tag.getInt(GROWTH_PROGRESS));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (rechargeProgressTicks > 0) {
            tag.putInt(RECHARGE_PROGRESS, rechargeProgressTicks);
        }
        if (growthProgressTicks > 0) {
            tag.putInt(GROWTH_PROGRESS, growthProgressTicks);
        }
    }

    private static int clampProgress(int progress) {
        return Math.max(0, Math.min(ManaSourceGrowthRules.MAX_CATCH_UP_TICKS, progress));
    }
}
