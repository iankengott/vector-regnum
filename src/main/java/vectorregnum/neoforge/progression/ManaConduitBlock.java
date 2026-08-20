package vectorregnum.neoforge.progression;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Passive raw-crystal link. Transfer is owned and ticked by a receiving cell. */
public final class ManaConduitBlock extends Block {
    public static final MapCodec<ManaConduitBlock> CODEC = simpleCodec(ManaConduitBlock::new);
    private final ManaTransportRules.ConduitTier tier;

    public ManaConduitBlock(Properties properties) {
        this(properties, ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    }

    public ManaConduitBlock(Properties properties, ManaTransportRules.ConduitTier tier) {
        super(properties);
        this.tier = tier;
    }

    public ManaTransportRules.ConduitTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            var source = ManaReservoirBlockEntity.findSource(serverLevel, pos, tier);
            serverPlayer.sendSystemMessage(source
                    .<Component>map(found -> Component.translatable("message.vector_regnum.conduit_linked",
                            found.conduitDistance()))
                    .orElseGet(() -> Component.translatable("message.vector_regnum.conduit_unlinked")), true);
        }
        return InteractionResult.SUCCESS;
    }
}
