package vectorregnum.neoforge.progression;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Compact server-authoritative discovery snapshot sent to the client manual. */
public record ProgressionPayload(String serializedUnlocks) implements CustomPayload {
    public static final Id<ProgressionPayload> ID = new Id<>(
            Identifier.of("vector_regnum", "progression_sync"));
    public static final PacketCodec<RegistryByteBuf, ProgressionPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, ProgressionPayload::serializedUnlocks,
                    ProgressionPayload::new);

    public ProgressionPayload {
        if (serializedUnlocks == null || serializedUnlocks.length() > 512) {
            throw new IllegalArgumentException("progression snapshot is too large");
        }
    }

    public static ProgressionPayload of(ProgressionState state) {
        return new ProgressionPayload(String.join(",", state.ids()));
    }

    public Set<String> unlocks() {
        if (serializedUnlocks.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(serializedUnlocks.split(","))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
