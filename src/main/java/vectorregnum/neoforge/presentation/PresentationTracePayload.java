package vectorregnum.neoforge.presentation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.core.presentation.PresentationTraceKind;

/**
 * One compact authoritative world trace. Servers emit this instead of particle
 * packets; each receiving client renders it through its own presentation
 * backend, so Veil-active clients never receive vanilla particle spawns.
 *
 * @param extraPoints up to {@link #MAX_EXTRA_POINTS} additional emission points
 * @param extraStyles per-point style override ordinal, or -1 to inherit
 */
public record PresentationTracePayload(long instanceId, PresentationTraceKind kind,
        PresentationParticleStyle style, PresentationElement element,
        double x, double y, double z, boolean hasTarget,
        double targetX, double targetY, double targetZ,
        float radius, int durationTicks, float intensity, long seed,
        List<double[]> extraPoints, List<Integer> extraStyles) implements CustomPacketPayload {
    public static final int MAX_EXTRA_POINTS = 16;
    public static final float MAX_RADIUS = 48.0f;
    public static final int MAX_DURATION_TICKS = 1_200;
    private static final int WIRE_VERSION = 1;
    public static final Type<PresentationTracePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("vector_regnum", "presentation_trace"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PresentationTracePayload> CODEC =
            StreamCodec.of((buffer, value) -> value.write(buffer), PresentationTracePayload::read);

    public PresentationTracePayload {
        extraPoints = extraPoints == null ? List.of() : List.copyOf(extraPoints);
        extraStyles = extraStyles == null ? List.of() : List.copyOf(extraStyles);
        if (instanceId < 0 || kind == null || style == null || element == null
                || !finite(x, y, z) || (hasTarget && !finite(targetX, targetY, targetZ))
                || !Float.isFinite(radius) || radius < 0.0f || radius > MAX_RADIUS
                || durationTicks < 1 || durationTicks > MAX_DURATION_TICKS
                || !Float.isFinite(intensity) || intensity < 0.0f || intensity > 1.0f
                || extraPoints.size() != extraStyles.size()
                || extraPoints.size() > MAX_EXTRA_POINTS) {
            throw new IllegalArgumentException("invalid presentation trace payload");
        }
        for (double[] point : extraPoints) {
            if (point == null || point.length != 3 || !finite(point[0], point[1], point[2])) {
                throw new IllegalArgumentException("invalid presentation trace extra point");
            }
        }
        for (Integer override : extraStyles) {
            if (override == null || (override >= 0 && !PresentationParticleStyle.isValidOrdinal(override))) {
                throw new IllegalArgumentException("invalid presentation trace style override");
            }
        }
    }

    /** Convenience factory for the common single-point trace. */
    public static PresentationTracePayload point(long instanceId, PresentationTraceKind kind,
            PresentationParticleStyle style, PresentationElement element, double x, double y,
            double z, float radius, int durationTicks, float intensity, long seed) {
        return new PresentationTracePayload(instanceId, kind, style, element, x, y, z,
                false, 0, 0, 0, radius, durationTicks, intensity, seed, List.of(), List.of());
    }

    private static PresentationTracePayload read(RegistryFriendlyByteBuf buffer) {
        long instanceId = buffer.readVarLong();
        int version = buffer.readUnsignedByte();
        if (version != WIRE_VERSION) {
            throw new IllegalArgumentException("unsupported presentation trace wire version");
        }
        PresentationTraceKind kind = bounded(buffer, PresentationTraceKind.values());
        PresentationParticleStyle style = bounded(buffer, PresentationParticleStyle.values());
        PresentationElement element = bounded(buffer, PresentationElement.values());
        double x = buffer.readDouble(); double y = buffer.readDouble(); double z = buffer.readDouble();
        boolean hasTarget = buffer.readBoolean();
        double targetX = hasTarget ? buffer.readDouble() : 0;
        double targetY = hasTarget ? buffer.readDouble() : 0;
        double targetZ = hasTarget ? buffer.readDouble() : 0;
        float radius = clamp(buffer.readFloat(), MAX_RADIUS);
        int durationTicks = Math.clamp(buffer.readShort() & 0xFFFF, 1, MAX_DURATION_TICKS);
        float intensity = clamp(buffer.readFloat(), 1.0f);
        long seed = buffer.readLong();
        int count = Math.min(MAX_EXTRA_POINTS, buffer.readUnsignedByte());
        List<double[]> points = new ArrayList<>(count);
        List<Integer> styles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(new double[]{buffer.readDouble(), buffer.readDouble(), buffer.readDouble()});
            int override = buffer.readByte();
            styles.add(PresentationParticleStyle.isValidOrdinal(override) ? override : -1);
        }
        return new PresentationTracePayload(instanceId, kind, style, element, x, y, z,
                hasTarget, targetX, targetY, targetZ, radius, durationTicks, intensity, seed,
                points, styles);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(instanceId);
        buffer.writeByte(WIRE_VERSION);
        buffer.writeByte(kind.ordinal());
        buffer.writeByte(style.ordinal());
        buffer.writeByte(element.ordinal());
        buffer.writeDouble(x); buffer.writeDouble(y); buffer.writeDouble(z);
        buffer.writeBoolean(hasTarget);
        if (hasTarget) {
            buffer.writeDouble(targetX); buffer.writeDouble(targetY); buffer.writeDouble(targetZ);
        }
        buffer.writeFloat(radius);
        buffer.writeShort(durationTicks);
        buffer.writeFloat(intensity);
        buffer.writeLong(seed);
        buffer.writeByte(extraPoints.size());
        for (int index = 0; index < extraPoints.size(); index++) {
            double[] point = extraPoints.get(index);
            buffer.writeDouble(point[0]); buffer.writeDouble(point[1]); buffer.writeDouble(point[2]);
            buffer.writeByte(extraStyles.get(index));
        }
    }

    private static <T extends Enum<T>> T bounded(RegistryFriendlyByteBuf buffer, T[] values) {
        int ordinal = buffer.readUnsignedByte();
        return values[ordinal < values.length ? ordinal : 0];
    }

    private static float clamp(float value, float maximum) {
        return Float.isFinite(value) ? Math.clamp(value, 0.0f, maximum) : 0.0f;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
