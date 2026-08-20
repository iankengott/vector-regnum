package vectorregnum.neoforge.ponder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client request for its latest completed server-authored trace. */
public record PonderTraceRequestPayload(String source) implements CustomPayload {
    public static final Id<PonderTraceRequestPayload> ID = new Id<>(
            Identifier.of("vector_regnum", "ponder_trace_request"));
    public static final PacketCodec<RegistryByteBuf, PonderTraceRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32), PonderTraceRequestPayload::source,
            PonderTraceRequestPayload::new);

    public PonderTraceRequestPayload {
        if (source == null || source.isBlank() || source.length() > 32) {
            throw new IllegalArgumentException("invalid ponder request source");
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
