package vectorregnum.neoforge.presentation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Compact authoritative descriptor for an animated magic-circle preview. The
 * server sends the verified topology once; the receiving client animates it
 * locally through its own presentation backend instead of the server streaming
 * vanilla particles every tick.
 *
 * @param ringRadii bounded per-ring radii in world units
 * @param sigils ring/slot coordinates with a visual ordinal (-1 marks a fault)
 */
public record CirclePreviewPayload(long instanceId, boolean showcase,
        double centerX, double centerY, double centerZ,
        double rightX, double rightY, double rightZ,
        double upX, double upY, double upZ,
        List<Float> ringRadii, int slotsPerRing, List<SigilDot> sigils,
        int durationTicks, long seed) implements CustomPacketPayload {
    public static final int MAX_RINGS = 16;
    public static final int MAX_SIGILS = 64;
    public static final int MAX_SLOTS_PER_RING = 64;
    public static final int MAX_DURATION_TICKS = 1_200;
    /** Visual ordinals shared by the encoder and the client animator. */
    public static final int VISUAL_FAULT = -1;
    public static final int VISUAL_DEFAULT = 0;
    public static final int VISUAL_FIRE = 1;
    public static final int VISUAL_FROST = 2;
    public static final int VISUAL_VOID = 3;
    public static final int VISUAL_EXECUTE = 4;
    public static final int VISUAL_SHAPE = 5;
    private static final int WIRE_VERSION = 1;
    public static final Type<CirclePreviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "circle_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CirclePreviewPayload> CODEC =
            StreamCodec.of((buffer, value) -> value.write(buffer), CirclePreviewPayload::read);

    /** One bounded sigil marker. */
    public record SigilDot(int ring, int slot, int visual) {
        public SigilDot {
            if (ring < 0 || ring >= MAX_RINGS || slot < 0 || slot >= MAX_SLOTS_PER_RING
                    || visual < VISUAL_FAULT || visual > VISUAL_SHAPE) {
                throw new IllegalArgumentException("invalid circle preview sigil");
            }
        }
    }

    public CirclePreviewPayload {
        ringRadii = ringRadii == null ? List.of() : List.copyOf(ringRadii);
        sigils = sigils == null ? List.of() : List.copyOf(sigils);
        if (instanceId < 0
                || !finite(centerX, centerY, centerZ, rightX, rightY, rightZ, upX, upY, upZ)
                || ringRadii.isEmpty() || ringRadii.size() > MAX_RINGS
                || slotsPerRing < 1 || slotsPerRing > MAX_SLOTS_PER_RING
                || sigils.size() > MAX_SIGILS
                || durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("invalid circle preview payload");
        }
        for (Float radius : ringRadii) {
            if (radius == null || !Float.isFinite(radius) || radius <= 0.0f || radius > 48.0f) {
                throw new IllegalArgumentException("invalid circle preview ring radius");
            }
        }
    }

    private static CirclePreviewPayload read(RegistryFriendlyByteBuf buffer) {
        long instanceId = buffer.readVarLong();
        int version = buffer.readUnsignedByte();
        if (version != WIRE_VERSION) {
            throw new IllegalArgumentException("unsupported circle preview wire version");
        }
        boolean showcase = buffer.readBoolean();
        double centerX = buffer.readDouble(); double centerY = buffer.readDouble();
        double centerZ = buffer.readDouble();
        double rightX = buffer.readDouble(); double rightY = buffer.readDouble();
        double rightZ = buffer.readDouble();
        double upX = buffer.readDouble(); double upY = buffer.readDouble();
        double upZ = buffer.readDouble();
        int ringCount = Math.min(MAX_RINGS, buffer.readUnsignedByte());
        List<Float> ringRadii = new ArrayList<>(ringCount);
        for (int index = 0; index < ringCount; index++) {
            float radius = buffer.readFloat();
            ringRadii.add(Float.isFinite(radius) && radius > 0.0f && radius <= 48.0f ? radius : 1.0f);
        }
        int slotsPerRing = Math.clamp(buffer.readUnsignedByte(), 1, MAX_SLOTS_PER_RING);
        int sigilCount = Math.min(MAX_SIGILS, buffer.readUnsignedByte());
        List<SigilDot> sigils = new ArrayList<>(sigilCount);
        for (int index = 0; index < sigilCount; index++) {
            int ring = buffer.readUnsignedByte();
            int slot = buffer.readUnsignedByte();
            int visual = buffer.readByte();
            if (ring < MAX_RINGS && slot < MAX_SLOTS_PER_RING
                    && visual >= VISUAL_FAULT && visual <= VISUAL_SHAPE) {
                sigils.add(new SigilDot(ring, slot, visual));
            }
        }
        int durationTicks = Math.clamp(buffer.readShort() & 0xFFFF, 1, MAX_DURATION_TICKS);
        long seed = buffer.readLong();
        return new CirclePreviewPayload(instanceId, showcase, centerX, centerY, centerZ,
                rightX, rightY, rightZ, upX, upY, upZ, ringRadii, slotsPerRing, sigils,
                durationTicks, seed);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(instanceId);
        buffer.writeByte(WIRE_VERSION);
        buffer.writeBoolean(showcase);
        buffer.writeDouble(centerX); buffer.writeDouble(centerY); buffer.writeDouble(centerZ);
        buffer.writeDouble(rightX); buffer.writeDouble(rightY); buffer.writeDouble(rightZ);
        buffer.writeDouble(upX); buffer.writeDouble(upY); buffer.writeDouble(upZ);
        buffer.writeByte(ringRadii.size());
        for (Float radius : ringRadii) buffer.writeFloat(radius);
        buffer.writeByte(slotsPerRing);
        buffer.writeByte(sigils.size());
        for (SigilDot sigil : sigils) {
            buffer.writeByte(sigil.ring());
            buffer.writeByte(sigil.slot());
            buffer.writeByte(sigil.visual());
        }
        buffer.writeShort(durationTicks);
        buffer.writeLong(seed);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
