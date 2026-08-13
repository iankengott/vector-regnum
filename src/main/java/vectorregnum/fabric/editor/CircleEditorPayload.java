package vectorregnum.fabric.editor;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bounded client-to-server editor action. The server owns the circle mutation. */
public record CircleEditorPayload(String action, String data) implements CustomPayload {
    public static final Id<CircleEditorPayload> ID = new Id<>(
            Identifier.of("vector_regnum", "circle_editor_request"));
    public static final PacketCodec<RegistryByteBuf, CircleEditorPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), CircleEditorPayload::action,
            PacketCodecs.string(8_192), CircleEditorPayload::data,
            CircleEditorPayload::new);

    public CircleEditorPayload {
        if (action == null || action.isBlank() || action.length() > 64
                || data == null || data.length() > 8_192) {
            throw new IllegalArgumentException("invalid circle editor payload");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
