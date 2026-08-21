package vectorregnum.neoforge.presentation;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.core.presentation.PresentationTraceKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wire bounds and pure synthesis contracts for compact authoritative traces. */
class PresentationTracePayloadTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void pointTraceRoundTripsThroughTheWireCodec() {
        PresentationTracePayload payload = PresentationTracePayload.point(7,
                PresentationTraceKind.BURST, PresentationParticleStyle.SPARK,
                PresentationElement.FIRE, 1.5, 64.25, -3.0, 0.9F, 40, 0.8F, 123L);
        RegistryFriendlyByteBuf buffer = buffer();
        PresentationTracePayload.CODEC.encode(buffer, payload);
        PresentationTracePayload decoded = PresentationTracePayload.CODEC.decode(buffer);

        assertEquals(payload.instanceId(), decoded.instanceId());
        assertEquals(payload.kind(), decoded.kind());
        assertEquals(payload.style(), decoded.style());
        assertEquals(payload.element(), decoded.element());
        assertEquals(payload.x(), decoded.x());
        assertEquals(payload.y(), decoded.y());
        assertEquals(payload.z(), decoded.z());
        assertEquals(payload.radius(), decoded.radius());
        assertEquals(payload.durationTicks(), decoded.durationTicks());
        assertEquals(payload.intensity(), decoded.intensity());
        assertTrue(decoded.extraPoints().isEmpty());
    }

    @Test
    void extrasAndBeamTargetsRoundTrip() {
        PresentationTracePayload payload = new PresentationTracePayload(3,
                PresentationTraceKind.BEAM, PresentationParticleStyle.LARGE_SMOKE,
                PresentationElement.VOID, 0, 0, 0, true, 4, 5, 6,
                0.3F, 10, 0.5F, 99L,
                List.of(new double[]{1, 2, 3}), List.of(PresentationParticleStyle.WITCH.ordinal()));
        RegistryFriendlyByteBuf buffer = buffer();
        PresentationTracePayload.CODEC.encode(buffer, payload);
        PresentationTracePayload decoded = PresentationTracePayload.CODEC.decode(buffer);

        assertTrue(decoded.hasTarget());
        assertEquals(4, decoded.targetX());
        assertEquals(1, decoded.extraPoints().size());
        assertEquals(PresentationParticleStyle.WITCH.ordinal(),
                decoded.extraStyles().getFirst());
    }

    @Test
    void invalidTracesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PresentationTracePayload.point(-1,
                PresentationTraceKind.BURST, PresentationParticleStyle.MOTES,
                PresentationElement.ARCANE, 0, 0, 0, 1, 10, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> PresentationTracePayload.point(0,
                PresentationTraceKind.BURST, PresentationParticleStyle.MOTES,
                PresentationElement.ARCANE, 0, 0, 0, 49, 10, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> PresentationTracePayload.point(0,
                PresentationTraceKind.BURST, PresentationParticleStyle.MOTES,
                PresentationElement.ARCANE, Double.NaN, 0, 0, 1, 10, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PresentationTracePayload(0,
                PresentationTraceKind.MOTES, PresentationParticleStyle.MOTES,
                PresentationElement.ARCANE, 0, 0, 0, false, 0, 0, 0, 1, 10, 1, 0,
                List.of(new double[]{Double.NEGATIVE_INFINITY, 0, 0}), List.of(-1)));
        assertThrows(IllegalArgumentException.class, () -> new PresentationTracePayload(0,
                PresentationTraceKind.MOTES, PresentationParticleStyle.MOTES,
                PresentationElement.ARCANE, 0, 0, 0, false, 0, 0, 0, 1, 10, 1, 0,
                List.of(new double[]{0, 0, 0}), List.of(99)));
    }

    @Test
    void synthesisProducesBoundedCuesPerEmissionPoint() {
        List<double[]> extras = new java.util.ArrayList<>();
        for (int index = 0; index < PresentationTracePayload.MAX_EXTRA_POINTS; index++) {
            extras.add(new double[]{index, 0, 0});
        }
        PresentationTracePayload payload = new PresentationTracePayload(11,
                PresentationTraceKind.BURST, PresentationParticleStyle.TOTEM,
                PresentationElement.ARCANE, 5, 5, 5, false, 0, 0, 0,
                1.2F, 8, 0.9F, 42L, extras,
                java.util.Collections.nCopies(extras.size(), -1));

        var cues = TraceCueFactory.synthesize(payload);

        assertEquals(PresentationTracePayload.MAX_EXTRA_POINTS + 1, cues.size());
        for (var cue : cues) {
            assertEquals("vector_regnum:trace", cue.program().id());
            assertEquals(1, cue.program().instructions().size());
            assertTrue(cue.instruction().truthLayer(),
                    "authoritative world traces are mechanics-derived truth cues");
        }
        assertEquals("vector_regnum:trace/burst",
                cues.getFirst().instruction().rendererId());
    }

    @Test
    void beamSynthesisCarriesSegmentGeometry() {
        var cue = TraceCueFactory.single(1, PresentationTraceKind.BEAM,
                PresentationParticleStyle.END_ROD, PresentationElement.ARCANE,
                new Vec3(0, 0, 0), new Vec3(3, 0, 0), null, null, 0.05F, 8, 0.5F, 7L);

        assertEquals("vector_regnum:trace/beam", cue.instruction().rendererId());
        assertEquals(3.0, cue.instruction().parameters().get("length"));
        assertEquals(1.0, cue.direction().x);
    }

    @Test
    void ringSynthesisCanCarryAnAuthoredPlane() {
        var cue = TraceCueFactory.single(1, PresentationTraceKind.RING,
                PresentationParticleStyle.END_ROD, PresentationElement.ARCANE,
                new Vec3(1, 2, 3), null, new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                2.25F, 6, 0.55F, 9L);

        assertEquals("vector_regnum:trace/ring", cue.instruction().rendererId());
        assertEquals(2.25, cue.instruction().parameters().get("radius"));
        assertEquals(1.0, cue.instruction().parameters().get("axis_rx"));
        assertEquals(1.0, cue.instruction().parameters().get("axis_uz"));
    }

    @Test
    void friendlyByteBufRemainsTheTransport() {
        // Guards against accidental drift to registry-dependent serialization.
        assertTrue(FriendlyByteBuf.class.isAssignableFrom(RegistryFriendlyByteBuf.class));
    }
}
