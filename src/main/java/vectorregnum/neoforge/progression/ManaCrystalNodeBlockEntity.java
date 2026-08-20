package vectorregnum.neoforge.progression;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Persistent tick progress for a natural mana-crystal source. */
public final class ManaCrystalNodeBlockEntity extends BlockEntity {
    private static final String RECHARGE_PROGRESS = "recharge_progress_ticks";
    private static final String GROWTH_PROGRESS = "growth_progress_ticks";

    private int rechargeProgressTicks;
    private int growthProgressTicks;

    public ManaCrystalNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ProgressionContent.MANA_CRYSTAL_NODE_ENTITY, pos, state);
    }

    public ManaSourceGrowthRules.SourceState sourceState(BlockState state) {
        return new ManaSourceGrowthRules.SourceState(
                state.get(ManaCrystalNodeBlock.NATURAL)
                        ? ManaSourceGrowthRules.SourceOrigin.NATURAL
                        : ManaSourceGrowthRules.SourceOrigin.CONSTRUCTED,
                state.get(ManaCrystalNodeBlock.GROWTH_STAGE),
                state.get(ManaCrystalNodeBlock.CHARGE),
                rechargeProgressTicks, growthProgressTicks);
    }

    public void setProgress(ManaSourceGrowthRules.SourceState state) {
        rechargeProgressTicks = state.rechargeProgressTicks();
        growthProgressTicks = state.growthProgressTicks();
        markDirty();
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        rechargeProgressTicks = clampProgress(nbt.getInt(RECHARGE_PROGRESS));
        growthProgressTicks = clampProgress(nbt.getInt(GROWTH_PROGRESS));
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (rechargeProgressTicks > 0) {
            nbt.putInt(RECHARGE_PROGRESS, rechargeProgressTicks);
        }
        if (growthProgressTicks > 0) {
            nbt.putInt(GROWTH_PROGRESS, growthProgressTicks);
        }
    }

    private static int clampProgress(int progress) {
        return Math.max(0, Math.min(ManaSourceGrowthRules.MAX_CATCH_UP_TICKS, progress));
    }
}
