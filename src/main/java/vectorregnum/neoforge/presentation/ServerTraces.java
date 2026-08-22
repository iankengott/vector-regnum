package vectorregnum.neoforge.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.core.presentation.PresentationTraceKind;
import vectorregnum.neoforge.VectorRegnumMod;

/**
 * Single authoritative choke point for world-space spell traces. Servers emit
 * compact bounded payloads instead of vanilla particles, so every receiving
 * client renders through its own presentation backend and Veil-active clients
 * never receive built-in particle spawns. Failures here are cosmetic by
 * contract and can never alter gameplay state.
 */
public final class ServerTraces {
    /** Bounded per-tick emission guard against automation-driven spam. */
    static final int MAX_TRACES_PER_TICK = 256;
    private static final double BROADCAST_DISTANCE = 96.0;
    private static final AtomicLong NEXT_INSTANCE = new AtomicLong();
    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;
    private static volatile boolean warnedUnsupported;

    private ServerTraces() { }

    public static long nextInstanceId() {
        return NEXT_INSTANCE.getAndIncrement() & Long.MAX_VALUE;
    }

    /** Expanding puff at one point. */
    public static void burst(ServerLevel level, Vec3 point, PresentationParticleStyle style,
            PresentationElement element, float radius, float intensity, int durationTicks) {
        emit(level, PresentationTracePayload.point(nextInstanceId(), PresentationTraceKind.BURST,
                style, element, point.x, point.y, point.z, radius, durationTicks, intensity,
                level.getGameTime()));
    }

    /** Expanding puffs at several points, batched into bounded payloads. */
    public static void burstAll(ServerLevel level, List<Vec3> points,
            PresentationParticleStyle style, PresentationElement element, float radius,
            float intensity, int durationTicks) {
        if (points == null || points.isEmpty()) return;
        long seed = level.getGameTime();
        for (int start = 0; start < points.size(); start += PresentationTracePayload.MAX_EXTRA_POINTS) {
            List<double[]> extraPoints = new ArrayList<>();
            List<Integer> extraStyles = new ArrayList<>();
            Vec3 primary = points.get(start);
            for (int index = start + 1; index < Math.min(points.size(),
                    start + PresentationTracePayload.MAX_EXTRA_POINTS); index++) {
                Vec3 point = points.get(index);
                extraPoints.add(new double[]{point.x, point.y, point.z});
                extraStyles.add(-1);
            }
            emit(level, new PresentationTracePayload(nextInstanceId(),
                    PresentationTraceKind.BURST, style, element, primary.x, primary.y,
                    primary.z, false, 0, 0, 0, radius, durationTicks, intensity, seed,
                    extraPoints, extraStyles));
        }
    }

    /** Gentle drifting motes around one point. */
    public static void motes(ServerLevel level, Vec3 point, PresentationParticleStyle style,
            PresentationElement element, float radius, float intensity, int durationTicks) {
        emit(level, PresentationTracePayload.point(nextInstanceId(), PresentationTraceKind.MOTES,
                style, element, point.x, point.y, point.z, radius, durationTicks, intensity,
                level.getGameTime()));
    }

    /**
     * Horizontal ring that grows into {@code radius} on the client. One payload
     * replaces the previous per-tick ring streaming.
     */
    public static void ring(ServerLevel level, Vec3 center, float radius,
            PresentationElement element, int durationTicks, float intensity) {
        emit(level, PresentationTracePayload.point(nextInstanceId(), PresentationTraceKind.RING,
                PresentationParticleStyle.MOTES, element, center.x, center.y, center.z,
                radius, durationTicks, intensity, level.getGameTime()));
    }

    /** Straight styled segment between two points. */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to,
            PresentationParticleStyle style, PresentationElement element, float thickness,
            int durationTicks, float intensity) {
        emit(level, new PresentationTracePayload(nextInstanceId(), PresentationTraceKind.BEAM,
                style, element, from.x, from.y, from.z, true, to.x, to.y, to.z,
                thickness, durationTicks, intensity, level.getGameTime(), List.of(), List.of()));
    }

    /** Broadcasts the verified topology of an animated circle preview once. */
    public static void circlePreview(ServerLevel level, CirclePreviewPayload payload) {
        if (level == null || payload == null) return;
        if (!spendBudget()) return;
        try {
            PacketDistributor.sendToPlayersNear(level, null,
                    payload.centerX(), payload.centerY(), payload.centerZ(),
                    BROADCAST_DISTANCE, payload);
        } catch (RuntimeException | LinkageError failure) {
            // Traces are strictly cosmetic and deliberately fail-open: embedded
            // test players and un-negotiated connections simply see nothing.
            logOnce(failure);
        }
    }

    private static void emit(ServerLevel level, PresentationTracePayload payload) {
        if (level == null || !spendBudget()) return;
        try {
            PacketDistributor.sendToPlayersNear(level, null,
                    payload.x(), payload.y(), payload.z(), BROADCAST_DISTANCE, payload);
        } catch (RuntimeException | LinkageError failure) {
            logOnce(failure);
        }
    }

    private static void logOnce(Throwable failure) {
        if (!warnedUnsupported) {
            warnedUnsupported = true;
            VectorRegnumMod.LOGGER.info(
                    "Presentation trace unavailable for a connection; trace skipped ({})",
                    failure.toString());
        }
    }

    private static boolean spendBudget() {
        // Budget resets are driven by {@link #tickBudget(long)} from the server tick.
        if (budgetUsed >= MAX_TRACES_PER_TICK) return false;
        budgetUsed++;
        return true;
    }

    /** Resets the per-tick emission budget; called from the server tick event. */
    public static void tickBudget(long gameTick) {
        if (gameTick != budgetTick) {
            budgetTick = gameTick;
            budgetUsed = 0;
        }
    }
}
