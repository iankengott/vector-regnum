package vectorregnum.neoforge.ponder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A complete bounded teaching trace built from authoritative server results. */
public record PonderTracePayload(String encodedTimeline) implements CustomPacketPayload {
    public static final Type<PonderTracePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "ponder_trace"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PonderTracePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(PonderTimelineCodec.MAX_ENCODED_LENGTH),
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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
