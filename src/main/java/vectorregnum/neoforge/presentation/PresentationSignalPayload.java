package vectorregnum.neoforge.presentation;

import java.util.Optional;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import vectorregnum.core.presentation.PresentationSignal;
import vectorregnum.core.presentation.PresentationTrigger;
import vectorregnum.core.semantic.SemanticOpcode;
import vectorregnum.core.vm2.Opcode;

/** Bounded authoritative execution hook; it carries resolved geometry, never gameplay decisions. */
public record PresentationSignalPayload(long instanceId, PresentationSignal signal)
        implements CustomPayload {
    public static final Id<PresentationSignalPayload> ID = new Id<>(
            Identifier.of("vector_regnum", "presentation_signal"));
    public static final PacketCodec<RegistryByteBuf, PresentationSignalPayload> CODEC =
            PacketCodec.of(PresentationSignalPayload::write, PresentationSignalPayload::new);

    public PresentationSignalPayload {
        if (instanceId < 0 || signal == null) {
            throw new IllegalArgumentException("invalid presentation signal payload");
        }
    }

    private PresentationSignalPayload(RegistryByteBuf buffer) {
        this(buffer.readVarLong(), readSignal(buffer));
    }

    private void write(RegistryByteBuf buffer) {
        buffer.writeVarLong(instanceId);
        buffer.writeVarLong(signal.sequence());
        buffer.writeVarLong(signal.tick());
        buffer.writeByte(signal.kind().ordinal());
        buffer.writeByte(signal.opcode().map(Enum::ordinal).orElse(-1));
        buffer.writeByte(signal.semanticOpcode().map(Enum::ordinal).orElse(-1));
        buffer.writeVarInt(signal.sourceIndex() + 1);
        buffer.writeDouble(signal.x()); buffer.writeDouble(signal.y()); buffer.writeDouble(signal.z());
    }

    private static PresentationSignal readSignal(RegistryByteBuf buffer) {
        long sequence = buffer.readVarLong();
        long tick = buffer.readVarLong();
        PresentationTrigger.Kind kind = enumValue(PresentationTrigger.Kind.values(),
                buffer.readUnsignedByte());
        int vmIndex = buffer.readByte();
        int semanticIndex = buffer.readByte();
        Optional<Opcode> opcode = vmIndex < 0 ? Optional.empty()
                : Optional.of(enumValue(Opcode.values(), vmIndex));
        Optional<SemanticOpcode> semantic = semanticIndex < 0 ? Optional.empty()
                : Optional.of(enumValue(SemanticOpcode.values(), semanticIndex));
        return new PresentationSignal(sequence, tick, kind, opcode, semantic,
                buffer.readVarInt() - 1, buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static <T> T enumValue(T[] values, int index) {
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("invalid enum ordinal");
        return values[index];
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
