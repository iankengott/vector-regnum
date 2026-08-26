package vectorregnum.neoforge.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class CirclePreviewPayloadTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void currentPayloadRoundTripsEveryCanonicalVisualSlot() {
        List<CirclePreviewPayload.SigilDot> sigils = java.util.stream.IntStream
                .rangeClosed(CirclePreviewPayload.VISUAL_ARCANE,
                        CirclePreviewPayload.VISUAL_SOUND)
                .mapToObj(visual -> new CirclePreviewPayload.SigilDot(0, visual, visual))
                .toList();
        CirclePreviewPayload payload = new CirclePreviewPayload(7, false,
                1, 2, 3, 1, 0, 0, 0, 1, 0,
                List.of(1.5F), 32, sigils, 40, 99);
        RegistryFriendlyByteBuf buffer = buffer();
        CirclePreviewPayload.CODEC.encode(buffer, payload);

        assertEquals(sigils, CirclePreviewPayload.CODEC.decode(buffer).sigils());
    }

    @Test
    void versionOneDecodesHistoricalArcaneFireFrostAndVoidSlots() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeVarLong(8);
        buffer.writeByte(1);
        buffer.writeBoolean(false);
        for (double value : new double[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) {
            buffer.writeDouble(value);
        }
        buffer.writeByte(1);
        buffer.writeFloat(1.0F);
        buffer.writeByte(8);
        buffer.writeByte(4);
        for (int visual = 0; visual <= 3; visual++) {
            buffer.writeByte(0);
            buffer.writeByte(visual);
            buffer.writeByte(visual);
        }
        buffer.writeShort(20);
        buffer.writeLong(123);

        CirclePreviewPayload decoded = CirclePreviewPayload.CODEC.decode(buffer);
        assertEquals(List.of(
                new CirclePreviewPayload.SigilDot(0, 0, CirclePreviewPayload.VISUAL_ARCANE),
                new CirclePreviewPayload.SigilDot(0, 1, CirclePreviewPayload.VISUAL_FIRE),
                new CirclePreviewPayload.SigilDot(0, 2, CirclePreviewPayload.VISUAL_ICE),
                new CirclePreviewPayload.SigilDot(0, 3, CirclePreviewPayload.VISUAL_VOID)),
                decoded.sigils());
    }
}
