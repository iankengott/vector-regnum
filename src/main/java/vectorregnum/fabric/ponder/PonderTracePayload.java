package vectorregnum.fabric.ponder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** A complete bounded teaching trace built from authoritative server results. */
public record PonderTracePayload(String encodedTimeline) implements CustomPayload {
    public static final Id<PonderTracePayload> ID = new Id<>(
            Identifier.of("vector_regnum", "ponder_trace"));
    public static final PacketCodec<RegistryByteBuf, PonderTracePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(PonderTimelineCodec.MAX_ENCODED_LENGTH),
            PonderTracePayload::encodedTimeline, PonderTracePayload::new);

    public PonderTracePayload {
        if (encodedTimeline == null || encodedTimeline.isBlank()
                || encodedTimeline.length() > PonderTimelineCodec.MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("invalid ponder trace payload");
        }
    }

    public static PonderTracePayload of(PonderTimeline timeline) {
        return new PonderTracePayload(PonderTimelineCodec.encode(timeline));
    }

    public PonderTimeline timeline() {
        return PonderTimelineCodec.decode(encodedTimeline);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
