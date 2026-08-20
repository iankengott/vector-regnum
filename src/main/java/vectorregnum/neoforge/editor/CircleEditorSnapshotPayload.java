package vectorregnum.neoforge.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Authoritative circle snapshot returned after each graphical-editor action. */
public record CircleEditorSnapshotPayload(String encodedCircle, String status, boolean bindable,
        String anchorDescription)
        implements CustomPacketPayload {
    public static final Type<CircleEditorSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "circle_editor_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CircleEditorSnapshotPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(65_536), CircleEditorSnapshotPayload::encodedCircle,
                    ByteBufCodecs.stringUtf8(1_024), CircleEditorSnapshotPayload::status,
                    ByteBufCodecs.BOOL, CircleEditorSnapshotPayload::bindable,
                    ByteBufCodecs.stringUtf8(256), CircleEditorSnapshotPayload::anchorDescription,
                    CircleEditorSnapshotPayload::new);

    public CircleEditorSnapshotPayload {
        if (encodedCircle == null || encodedCircle.length() > 65_536
                || status == null || status.length() > 1_024
                || anchorDescription == null || anchorDescription.length() > 256) {
            throw new IllegalArgumentException("invalid circle editor snapshot");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
