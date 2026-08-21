package vectorregnum.neoforge.presentation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import vectorregnum.core.presentation.PresentationElement;
import vectorregnum.core.presentation.PresentationParticleStyle;
import vectorregnum.core.presentation.PresentationTraceKind;

/**
 * Client-side animator for authoritative circle-preview descriptors. The server
 * sends the verified topology once; this animator replays the same bounded
 * choreography the previous server-side particle streaming produced, through
 * the shared cue pipeline so the active backend owns every particle.
 */
final class ClientCirclePreviews {
    private static final int MAX_PREVIEWS = 8;
    private static final int RING_CADENCE_TICKS = 4;
    private static final int STAR_CADENCE_TICKS = 8;
    private static final int SIGIL_ADVANCE_TICKS = 12;
    private static final List<ActivePreview> ACTIVE = new ArrayList<>();

    private ClientCirclePreviews() { }

    static void start(CirclePreviewPayload payload) {
        ACTIVE.removeIf(preview -> preview.payload().instanceId() == payload.instanceId());
        if (ACTIVE.size() >= MAX_PREVIEWS) ACTIVE.removeFirst();
        ACTIVE.add(new ActivePreview(payload));
    }

    static void tick(Minecraft client) {
        Iterator<ActivePreview> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActivePreview preview = iterator.next();
            if (preview.age++ >= preview.payload().durationTicks()
                    || client.level == null) {
                iterator.remove();
                continue;
            }
            animate(client, preview);
        }
    }

    static void clear() {
        ACTIVE.clear();
    }

    private static void animate(Minecraft client, ActivePreview preview) {
        CirclePreviewPayload payload = preview.payload();
        Vec3 center = center(payload);
        Vec3 right = axis(payload.rightX(), payload.rightY(), payload.rightZ());
        Vec3 up = axis(payload.upX(), payload.upY(), payload.upZ());
        if (payload.showcase()) {
            if (preview.age % STAR_CADENCE_TICKS == 0) drawStar(client, center, right, up);
        } else if (preview.age % RING_CADENCE_TICKS == 0) {
            drawIdleSigils(client, payload, center, right, up);
        }
        if (preview.age % RING_CADENCE_TICKS == 0) drawRings(client, payload, center, right, up);
        drawActiveSigil(client, payload, center, right, up, preview.age / SIGIL_ADVANCE_TICKS);
    }

    private static void drawRings(Minecraft client, CirclePreviewPayload payload,
            Vec3 center, Vec3 right, Vec3 up) {
        List<Float> radii = payload.ringRadii();
        for (int ring = 0; ring < radii.size(); ring++) {
            // Ring 0 uses the rod motif; deeper rings use the enchant motif,
            // matching the original server-side preview exactly.
            PresentationParticleStyle style = ring == 0
                    ? PresentationParticleStyle.END_ROD
                    : PresentationParticleStyle.MOTES;
            spawn(client, TraceCueFactory.single(payload.instanceId(),
                    PresentationTraceKind.RING, style, PresentationElement.ARCANE,
                    center, null, right, up, radii.get(ring), RING_CADENCE_TICKS + 2,
                    0.55f, payload.seed() + ring));
        }
    }

    private static void drawStar(Minecraft client, Vec3 center, Vec3 right, Vec3 up) {
        double radius = 1.85;
        Vec3[] points = new Vec3[5];
        for (int index = 0; index < points.length; index++) {
            double angle = -Math.PI / 2.0 + Math.PI * 2.0 * index / points.length;
            points[index] = center
                    .add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
        }
        for (int index = 0; index < points.length; index++) {
            spawn(client, TraceCueFactory.single(0, PresentationTraceKind.BEAM,
                    PresentationParticleStyle.MOTES, PresentationElement.FIRE,
                    points[index], points[(index + 2) % points.length], null, null,
                    0.05f, STAR_CADENCE_TICKS, 0.6f, index));
        }
    }

    private static void drawIdleSigils(Minecraft client, CirclePreviewPayload payload,
            Vec3 center, Vec3 right, Vec3 up) {
        for (CirclePreviewPayload.SigilDot sigil : payload.sigils()) {
            PresentationParticleStyle style = styleFor(sigil.visual());
            spawn(client, TraceCueFactory.single(payload.instanceId(),
                    PresentationTraceKind.MOTES, style, elementFor(sigil.visual()),
                    position(center, right, up, payload, sigil), null, null, null,
                    0.06f, RING_CADENCE_TICKS, 0.25f,
                    payload.seed() + sigil.ring() * 131L + sigil.slot()));
        }
    }

    private static void drawActiveSigil(Minecraft client, CirclePreviewPayload payload,
            Vec3 center, Vec3 right, Vec3 up, int activeIndex) {
        if (payload.sigils().isEmpty()) return;
        int index = Math.min(payload.sigils().size() - 1, activeIndex);
        CirclePreviewPayload.SigilDot sigil = payload.sigils().get(index);
        Vec3 position = position(center, right, up, payload, sigil);
        boolean fault = sigil.visual() == CirclePreviewPayload.VISUAL_FAULT;
        spawn(client, TraceCueFactory.single(payload.instanceId(),
                PresentationTraceKind.BURST,
                fault ? PresentationParticleStyle.LARGE_SMOKE : styleFor(sigil.visual()),
                elementFor(sigil.visual()), position, null, null, null,
                fault ? 0.3f : 0.16f, SIGIL_ADVANCE_TICKS, fault ? 0.7f : 0.9f,
                payload.seed() + index));
        spawn(client, TraceCueFactory.single(payload.instanceId(),
                PresentationTraceKind.BEAM, PresentationParticleStyle.WITCH,
                PresentationElement.VOID, center, position, null, null, 0.06f,
                SIGIL_ADVANCE_TICKS, 0.6f, payload.seed() + 97L + index));
    }

    private static PresentationParticleStyle styleFor(int visual) {
        return switch (visual) {
            case CirclePreviewPayload.VISUAL_FIRE -> PresentationParticleStyle.MOTES;
            case CirclePreviewPayload.VISUAL_FROST -> PresentationParticleStyle.MOTES;
            case CirclePreviewPayload.VISUAL_VOID -> PresentationParticleStyle.MOTES;
            case CirclePreviewPayload.VISUAL_EXECUTE -> PresentationParticleStyle.TOTEM;
            case CirclePreviewPayload.VISUAL_SHAPE -> PresentationParticleStyle.SPARK;
            case CirclePreviewPayload.VISUAL_FAULT -> PresentationParticleStyle.LARGE_SMOKE;
            default -> PresentationParticleStyle.END_ROD;
        };
    }

    private static PresentationElement elementFor(int visual) {
        return switch (visual) {
            case CirclePreviewPayload.VISUAL_FIRE -> PresentationElement.FIRE;
            case CirclePreviewPayload.VISUAL_FROST -> PresentationElement.FROST;
            case CirclePreviewPayload.VISUAL_VOID -> PresentationElement.VOID;
            default -> PresentationElement.ARCANE;
        };
    }

    private static Vec3 position(Vec3 center, Vec3 right, Vec3 up, CirclePreviewPayload payload,
            CirclePreviewPayload.SigilDot sigil) {
        float radius = payload.ringRadii()
                .get(Math.min(sigil.ring(), payload.ringRadii().size() - 1));
        double angle = Math.PI * 2.0 * sigil.slot() / payload.slotsPerRing();
        return center.add(right.scale(Math.sin(angle) * radius))
                .add(up.scale(Math.cos(angle) * radius));
    }

    private static Vec3 center(CirclePreviewPayload payload) {
        return new Vec3(payload.centerX(), payload.centerY(), payload.centerZ());
    }

    private static Vec3 axis(double x, double y, double z) {
        Vec3 axis = new Vec3(x, y, z);
        return axis.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : axis.normalize();
    }

    private static void spawn(Minecraft client, TraceCueFactory.SynthesizedCue cue) {
        ClientPresentationRuntime.spawnSynthesizedCue(cue);
    }

    private static final class ActivePreview {
        private final CirclePreviewPayload payload;
        private int age;

        private ActivePreview(CirclePreviewPayload payload) {
            this.payload = payload;
        }

        private CirclePreviewPayload payload() {
            return payload;
        }
    }
}
