package vectorregnum.neoforge.progression;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact server-authoritative discovery snapshot sent to the client manual. */
public record ProgressionPayload(String serializedUnlocks) implements CustomPacketPayload {
    public static final Type<ProgressionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "progression_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ProgressionPayload::serializedUnlocks,
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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
