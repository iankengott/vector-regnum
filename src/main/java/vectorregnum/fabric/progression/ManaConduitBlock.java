package vectorregnum.fabric.progression;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Passive raw-crystal link. Transfer is owned and ticked by a receiving cell. */
public final class ManaConduitBlock extends Block {
    public static final MapCodec<ManaConduitBlock> CODEC = createCodec(ManaConduitBlock::new);
    private final ManaTransportRules.ConduitTier tier;

    public ManaConduitBlock(Settings settings) {
        this(settings, ManaTransportRules.ConduitTier.RAW_CRYSTAL);
    }

    public ManaConduitBlock(Settings settings, ManaTransportRules.ConduitTier tier) {
        super(settings);
        this.tier = tier;
    }

    public ManaTransportRules.ConduitTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            var source = ManaReservoirBlockEntity.findSource(serverPlayer.getServerWorld(), pos, tier);
            serverPlayer.sendMessage(source
                    .<Text>map(found -> Text.translatable("message.vector_regnum.conduit_linked",
                            found.conduitDistance()))
                    .orElseGet(() -> Text.translatable("message.vector_regnum.conduit_unlinked")), true);
        }
        return ActionResult.SUCCESS;
    }
}
