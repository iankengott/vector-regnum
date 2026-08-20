package vectorregnum.neoforge.presentation;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import vectorregnum.core.presentation.PresentationProgramCodec;

/** One compact immutable presentation score, broadcast once per accepted cast. */
public record PresentationStartPayload(long instanceId, UUID casterId, long serverTick,
        double originX, double originY, double originZ,
        double directionX, double directionY, double directionZ,
        String encodedProgram) implements CustomPayload {
    public static final Id<PresentationStartPayload> ID = new Id<>(
            Identifier.of("vector_regnum", "presentation_start"));
    public static final PacketCodec<RegistryByteBuf, PresentationStartPayload> CODEC =
            PacketCodec.of(PresentationStartPayload::write, PresentationStartPayload::new);

    public PresentationStartPayload {
        if (instanceId < 0 || casterId == null || serverTick < 0
                || !finite(originX, originY, originZ, directionX, directionY, directionZ)
                || encodedProgram == null || encodedProgram.isBlank()
                || encodedProgram.length() > PresentationProgramCodec.MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("invalid presentation start payload");
        }
    }

    private PresentationStartPayload(RegistryByteBuf buffer) {
        this(buffer.readVarLong(), buffer.readUuid(), buffer.readVarLong(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readString(PresentationProgramCodec.MAX_ENCODED_LENGTH));
    }

    private void write(RegistryByteBuf buffer) {
        buffer.writeVarLong(instanceId);
        buffer.writeUuid(casterId);
        buffer.writeVarLong(serverTick);
        buffer.writeDouble(originX); buffer.writeDouble(originY); buffer.writeDouble(originZ);
        buffer.writeDouble(directionX); buffer.writeDouble(directionY); buffer.writeDouble(directionZ);
        buffer.writeString(encodedProgram, PresentationProgramCodec.MAX_ENCODED_LENGTH);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
