package vectorregnum.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.core.circle.SpellArtifactPersistence;

/** Server-persisted payload for a placed carved tablet. */
public final class SpellTabletBlockEntity extends BlockEntity {
    static final String PAYLOAD_KEY = "vector_regnum_artifact";
    private String payload = "";

    public SpellTabletBlockEntity(BlockPos pos, BlockState state) {
        super(SpellMediaContent.tabletBlockEntity(), pos, state);
    }

    public void activate(ServerPlayer player) {
        if (payload.isBlank()) {
            player.sendSystemMessage(Component.literal("This tablet has no carved spell")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        try {
            SpellArtifact artifact = SpellArtifactPersistence.decode(payload);
            if (artifact.state() != SpellArtifact.State.INSTALLED) {
                String dimension = player.serverLevel().dimension().location().toString();
                SpellArtifact.Transition installation = artifact.install(new SpellArtifact.WorldAnchor(
                        dimension, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
                if (!installation.accepted()) {
                    player.sendSystemMessage(Component.literal(installation.message()).withStyle(ChatFormatting.RED), true);
                    return;
                }
                artifact = installation.artifact();
                payload = SpellArtifactPersistence.encode(artifact);
                setChanged();
            }
            SpellArtifact.WorldAnchor anchor = artifact.installedAt().orElseThrow();
            String dimension = player.serverLevel().dimension().location().toString();
            if (!anchor.dimension().equals(dimension) || anchor.x() != worldPosition.getX()
                    || anchor.y() != worldPosition.getY() || anchor.z() != worldPosition.getZ()) {
                player.sendSystemMessage(Component.literal("Tablet anchor mismatch; activation refused")
                        .withStyle(ChatFormatting.DARK_RED), true);
                return;
            }
            Vec3 origin = Vec3.atCenterOf(worldPosition).add(0.0, 0.45, 0.0);
            if (CircleAuthoringService.activateCircleAt(player, artifact.circle(), true, origin)) {
                artifact = artifact.recordSuccessfulActivation().artifact();
                payload = SpellArtifactPersistence.encode(artifact);
                setChanged();
                player.sendSystemMessage(Component.literal("Permanent tablet activated • "
                                + artifact.successfulActivations() + " successful casts")
                        .withStyle(ChatFormatting.GOLD), false);
            }
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.error("Rejected malformed carved tablet at {}", worldPosition, exception);
            player.sendSystemMessage(Component.literal("The tablet's carving is corrupt")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        payload = nbt.getString(PAYLOAD_KEY);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        if (!payload.isBlank()) {
            nbt.putString(PAYLOAD_KEY, payload);
        }
    }
}
