package vectorregnum.neoforge.editor;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Authoritative circle snapshot returned after each graphical-editor action. */
public record CircleEditorSnapshotPayload(String encodedCircle, String status, boolean bindable,
        String anchorDescription)
        implements CustomPayload {
    public static final Id<CircleEditorSnapshotPayload> ID = new Id<>(
            Identifier.of("vector_regnum", "circle_editor_snapshot"));
    public static final PacketCodec<RegistryByteBuf, CircleEditorSnapshotPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.string(65_536), CircleEditorSnapshotPayload::encodedCircle,
                    PacketCodecs.string(1_024), CircleEditorSnapshotPayload::status,
                    PacketCodecs.BOOL, CircleEditorSnapshotPayload::bindable,
                    PacketCodecs.string(256), CircleEditorSnapshotPayload::anchorDescription,
                    CircleEditorSnapshotPayload::new);

    public CircleEditorSnapshotPayload {
        if (encodedCircle == null || encodedCircle.length() > 65_536
                || status == null || status.length() > 1_024
                || anchorDescription == null || anchorDescription.length() > 256) {
            throw new IllegalArgumentException("invalid circle editor snapshot");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
