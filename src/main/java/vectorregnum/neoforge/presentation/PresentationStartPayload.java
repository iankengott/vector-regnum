package vectorregnum.neoforge.presentation;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import vectorregnum.core.presentation.PresentationProgramCodec;

/** One compact immutable presentation score, broadcast once per accepted cast. */
public record PresentationStartPayload(long instanceId, UUID casterId, long serverTick,
        double originX, double originY, double originZ,
        double directionX, double directionY, double directionZ,
        String encodedProgram) implements CustomPacketPayload {
    public static final Type<PresentationStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "presentation_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PresentationStartPayload> CODEC =
            StreamCodec.of((buffer, value) -> value.write(buffer), PresentationStartPayload::new);

    public PresentationStartPayload {
        if (instanceId < 0 || casterId == null || serverTick < 0
                || !finite(originX, originY, originZ, directionX, directionY, directionZ)
                || encodedProgram == null || encodedProgram.isBlank()
                || encodedProgram.length() > PresentationProgramCodec.MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("invalid presentation start payload");
        }
    }

    private PresentationStartPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarLong(), buffer.readUUID(), buffer.readVarLong(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readUtf(PresentationProgramCodec.MAX_ENCODED_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(instanceId);
        buffer.writeUUID(casterId);
        buffer.writeVarLong(serverTick);
        buffer.writeDouble(originX); buffer.writeDouble(originY); buffer.writeDouble(originZ);
        buffer.writeDouble(directionX); buffer.writeDouble(directionY); buffer.writeDouble(directionZ);
        buffer.writeUtf(encodedProgram, PresentationProgramCodec.MAX_ENCODED_LENGTH);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
