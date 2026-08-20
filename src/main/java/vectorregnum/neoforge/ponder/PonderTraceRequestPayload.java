package vectorregnum.neoforge.ponder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request for its latest completed server-authored trace. */
public record PonderTraceRequestPayload(String source) implements CustomPacketPayload {
    public static final Type<PonderTraceRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "ponder_trace_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PonderTraceRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PonderTraceRequestPayload::source,
            PonderTraceRequestPayload::new);

    public PonderTraceRequestPayload {
        if (source == null || source.isBlank() || source.length() > 32) {
            throw new IllegalArgumentException("invalid ponder request source");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
