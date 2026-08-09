package vectorregnum.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vectorregnum.core.circle.SpellArtifact;
import vectorregnum.core.circle.SpellArtifactPersistence;

/** Server-persisted payload for a placed carved tablet. */
public final class SpellTabletBlockEntity extends BlockEntity {
    static final String PAYLOAD_KEY = "vector_regnum_artifact";
    private String payload = "";

    public SpellTabletBlockEntity(BlockPos pos, BlockState state) {
        super(SpellMediaContent.TABLET_BLOCK_ENTITY, pos, state);
    }

    public void activate(ServerPlayerEntity player) {
        if (payload.isBlank()) {
            player.sendMessage(Text.literal("This tablet has no carved spell")
                    .formatted(Formatting.RED), true);
            return;
        }
        try {
            SpellArtifact artifact = SpellArtifactPersistence.decode(payload);
            if (artifact.state() != SpellArtifact.State.INSTALLED) {
                String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
                SpellArtifact.Transition installation = artifact.install(new SpellArtifact.WorldAnchor(
                        dimension, pos.getX(), pos.getY(), pos.getZ()));
                if (!installation.accepted()) {
                    player.sendMessage(Text.literal(installation.message()).formatted(Formatting.RED), true);
                    return;
                }
                artifact = installation.artifact();
                payload = SpellArtifactPersistence.encode(artifact);
                markDirty();
            }
            SpellArtifact.WorldAnchor anchor = artifact.installedAt().orElseThrow();
            String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
            if (!anchor.dimension().equals(dimension) || anchor.x() != pos.getX()
                    || anchor.y() != pos.getY() || anchor.z() != pos.getZ()) {
                player.sendMessage(Text.literal("Tablet anchor mismatch; activation refused")
                        .formatted(Formatting.DARK_RED), true);
                return;
            }
            Vec3d origin = Vec3d.ofCenter(pos).add(0.0, 0.45, 0.0);
            if (CircleAuthoringService.activateCircleAt(player, artifact.circle(), true, origin)) {
                artifact = artifact.recordSuccessfulActivation().artifact();
                payload = SpellArtifactPersistence.encode(artifact);
                markDirty();
                player.sendMessage(Text.literal("Permanent tablet activated • "
                                + artifact.successfulActivations() + " successful casts")
                        .formatted(Formatting.GOLD), false);
            }
        } catch (RuntimeException exception) {
            VectorRegnumMod.LOGGER.error("Rejected malformed carved tablet at {}", pos, exception);
            player.sendMessage(Text.literal("The tablet's carving is corrupt")
                    .formatted(Formatting.DARK_RED), true);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        payload = nbt.getString(PAYLOAD_KEY);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!payload.isBlank()) {
            nbt.putString(PAYLOAD_KEY, payload);
        }
    }
}
