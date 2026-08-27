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
import net.minecraft.world.item.ItemStack;
import vectorregnum.core.casting.CastingMethod;
import vectorregnum.core.casting.ResourceEscrow;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.core.circle.SpellArtifactPersistence;

/** Server-persisted payload for an engraving or permanent carved tablet. */
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
            boolean expectedBlock = artifact.medium() == vectorregnum.core.circle.SpellMedium.ENGRAVING
                    ? getBlockState().is(SpellMediaContent.engravedSpellCircle())
                    : artifact.medium() == vectorregnum.core.circle.SpellMedium.TABLET
                            && getBlockState().is(SpellMediaContent.carvedTablet());
            if (!expectedBlock) {
                player.sendSystemMessage(Component.literal("Installed medium and carving do not match")
                        .withStyle(ChatFormatting.DARK_RED), true);
                return;
            }
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
            CastingMethod method = artifact.medium().permanentInstallation()
                    ? CastingMethod.INSTALLED_CIRCLE : CastingMethod.ENGRAVING;
            CircleAuthoringService.activateCircleAt(player, artifact.circle(), true, origin,
                    method, true, ItemStack.EMPTY,
                    outcome -> completeActivation(player, outcome));
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.error("Rejected malformed carved tablet at {}", worldPosition, exception);
            player.sendSystemMessage(Component.literal("The tablet's carving is corrupt")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    private void completeActivation(ServerPlayer player, ResourceEscrow.Outcome outcome) {
        if (outcome != ResourceEscrow.Outcome.SUCCESS) return;
        try {
            SpellArtifact updated = SpellArtifactPersistence.decode(payload)
                    .recordSuccessfulActivation().artifact();
            payload = SpellArtifactPersistence.encode(updated);
            setChanged();
            player.sendSystemMessage(Component.literal((updated.medium().permanentInstallation()
                            ? "Permanent circle" : "Engraving") + " activated • "
                            + updated.successfulActivations() + " successful casts")
                    .withStyle(ChatFormatting.GOLD), false);
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.error("Could not persist completed installed cast at {}",
                    worldPosition, exception);
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
