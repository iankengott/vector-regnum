package vectorregnum.neoforge.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded client-to-server editor action. The server owns the circle mutation. */
public record CircleEditorPayload(String action, String data) implements CustomPacketPayload {
    public static final Type<CircleEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "circle_editor_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CircleEditorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), CircleEditorPayload::action,
            ByteBufCodecs.stringUtf8(8_192), CircleEditorPayload::data,
            CircleEditorPayload::new);

    public CircleEditorPayload {
        if (action == null || action.isBlank() || action.length() > 64
                || data == null || data.length() > 8_192) {
            throw new IllegalArgumentException("invalid circle editor payload");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
