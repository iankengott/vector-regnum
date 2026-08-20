package vectorregnum.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vectorregnum.core.presentation.ExecutionEvent;
import vectorregnum.core.presentation.ExecutionEventSink;
import vectorregnum.core.presentation.PresentationProgram;
import vectorregnum.core.presentation.PresentationProgramCodec;
import vectorregnum.core.presentation.PresentationSignal;
import vectorregnum.core.presentation.PresentationTrigger;
import vectorregnum.core.vm2.Opcode;
import vectorregnum.core.vm2.WorldEffect;
import vectorregnum.neoforge.presentation.PresentationSignalPayload;
import vectorregnum.neoforge.presentation.PresentationStartPayload;

/** Bounded authoritative-event tap; presentation failures never affect VM execution. */
public final class VmPresentationBridge implements ExecutionEventSink {
    public static final int MAX_EVENTS = 128;
    private static final double BROADCAST_DISTANCE_SQUARED = 96.0 * 96.0;
    private static final AtomicLong NEXT_INSTANCE = new AtomicLong();

    private final ServerPlayerEntity player;
    private final PresentationProgram program;
    private final long instanceId;
    private final List<ExecutionEvent> events = new ArrayList<>();

    public VmPresentationBridge(ServerPlayerEntity player, PresentationProgram program) {
        this.player = player;
        this.program = program;
        this.instanceId = NEXT_INSTANCE.getAndIncrement() & Long.MAX_VALUE;
    }

    @Override
    public void accept(ExecutionEvent event) {
        if (events.size() >= MAX_EVENTS) {
            return;
        }
        events.add(event);
        try {
            if (event instanceof ExecutionEvent.Started) {
                broadcastStart();
            } else {
                signal(event).ifPresent(this::broadcastSignal);
            }
        } catch (RuntimeException ignored) {
            // Visual adapters are deliberately fail-open.
        }
    }

    public List<ExecutionEvent> events() {
        return List.copyOf(events);
    }

    private void broadcastStart() {
        Vec3d origin = player.getEyePos();
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        PresentationStartPayload payload = new PresentationStartPayload(instanceId,
                player.getUuid(), player.getServerWorld().getTime(), origin.x, origin.y,
                origin.z, direction.x, direction.y, direction.z,
                PresentationProgramCodec.encode(program));
        broadcast(origin, payload);
    }

    private void broadcastSignal(PresentationSignal signal) {
        broadcast(new Vec3d(signal.x(), signal.y(), signal.z()),
                new PresentationSignalPayload(instanceId, signal));
    }

    private void broadcast(Vec3d origin, net.minecraft.network.packet.CustomPayload payload) {
        for (ServerPlayerEntity observer : player.getServerWorld().getPlayers(candidate ->
                candidate.squaredDistanceTo(origin) <= BROADCAST_DISTANCE_SQUARED)) {
            if (ServerPlayNetworking.canSend(observer, payload.getId())) {
                ServerPlayNetworking.send(observer, payload);
            }
        }
    }

    private Optional<PresentationSignal> signal(ExecutionEvent event) {
        Vec3d point = player.getEyePos();
        int source = -1;
        PresentationTrigger.Kind kind;
        Optional<Opcode> opcode = Optional.empty();
        Optional<vectorregnum.core.semantic.SemanticOpcode> semantic = Optional.empty();
        switch (event) {
            case ExecutionEvent.StepExecuted step -> {
                if (step.opcode() == Opcode.SEMANTIC || step.opcode() == Opcode.DELAY
                        || step.opcode() == Opcode.HALT) return Optional.empty();
                kind = PresentationTrigger.Kind.OPCODE;
                opcode = Optional.of(step.opcode());
                source = step.source().sourceIndex();
            }
            case ExecutionEvent.DelayStarted delay -> {
                kind = PresentationTrigger.Kind.OPCODE;
                opcode = Optional.of(Opcode.DELAY);
                source = delay.source().sourceIndex();
            }
            case ExecutionEvent.WorldEffectEmitted emitted -> {
                source = emitted.source().sourceIndex();
                if (emitted.effect() instanceof WorldEffect.SemanticStep step) {
                    kind = PresentationTrigger.Kind.OPCODE;
                    semantic = Optional.of(step.instruction().opcode());
                } else {
                    kind = PresentationTrigger.Kind.WORLD_EFFECT;
                    point = resolvedPoint(emitted.effect());
                }
            }
            case ExecutionEvent.Halted halted -> {
                kind = PresentationTrigger.Kind.HALT;
                source = halted.source().sourceIndex();
            }
            case ExecutionEvent.Faulted faulted -> {
                kind = PresentationTrigger.Kind.FAULT;
                source = faulted.fault().source().sourceIndex();
            }
            default -> { return Optional.empty(); }
        }
        return Optional.of(new PresentationSignal(event.sequence(), event.tick(), kind,
                opcode, semantic, source, point.x, point.y, point.z));
    }

    private Vec3d resolvedPoint(WorldEffect effect) {
        if (effect instanceof WorldEffect.MoveToward move) {
            return new Vec3d(move.point().x(), move.point().y(), move.point().z());
        }
        if (effect instanceof WorldEffect.FollowPath path) {
            var last = path.points().getLast();
            return new Vec3d(last.x(), last.y(), last.z());
        }
        try {
            Entity entity = player.getServerWorld().getEntity(java.util.UUID.fromString(effect.entityId()));
            if (entity != null) return entity.getPos().add(0.0, entity.getHeight() * .5, 0.0);
        } catch (IllegalArgumentException ignored) {
            // Missing entities use the truthful cast origin; presentation remains cosmetic.
        }
        return player.getEyePos();
    }
}
