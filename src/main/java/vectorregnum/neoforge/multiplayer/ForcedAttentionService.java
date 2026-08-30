package vectorregnum.neoforge.multiplayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import vectorregnum.core.security.ForcedAttentionPolicy;
import vectorregnum.core.security.MechanicCapability;
import vectorregnum.core.security.MechanicRequest;
import vectorregnum.core.security.MechanicLimits;
import vectorregnum.neoforge.presentation.ServerTraces;
import vectorregnum.core.presentation.PresentationElement;

/** Server-authoritative, gradually applied attention effect with a safe visual fallback. */
public final class ForcedAttentionService {
    private static final List<AttentionEffect> ACTIVE = new ArrayList<>();

    private ForcedAttentionService() { }

    public static boolean apply(ServerPlayer caster, ServerPlayer target, double range,
            double angle, double strength, int durationTicks) {
        if (caster == null || target == null || caster == target) return false;
        if (!SpellSecurityPolicy.canAffectEntity(caster, target, range)) return false;
        MechanicRequest request = new MechanicRequest(MechanicCapability.FORCED_ATTENTION,
                range, durationTicks, 1, caster.level() == target.level(),
                caster.serverLevel().isLoaded(caster.blockPosition()),
                target.serverLevel().isLoaded(target.blockPosition()), true,
                caster.getServer().isPvpAllowed(), true, true);
        if (!ForcedAttentionPolicy.evaluate(request, angle, strength).allowed()) return false;
        long endTick = caster.serverLevel().getGameTime() + durationTicks;
        ACTIVE.removeIf(effect -> effect.caster().getUUID().equals(caster.getUUID())
                && effect.target().getUUID().equals(target.getUUID()));
        ACTIVE.add(new AttentionEffect(caster, target, range, angle, strength, endTick));
        // This world-space cue is the accessible alternative to camera movement.
        ServerTraces.ring(caster.serverLevel(), target.position().add(0, .05, 0),
                0.8F, PresentationElement.ARCANE, 10, .9F);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        Iterator<AttentionEffect> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            AttentionEffect effect = iterator.next();
            ServerPlayer caster = player(server, effect.caster().getUUID());
            ServerPlayer target = player(server, effect.target().getUUID());
            if (now >= effect.endTick() || caster == null || target == null
                    || !caster.isAlive() || !target.isAlive()
                    || caster.serverLevel() != target.serverLevel()
                    || !SpellSecurityPolicy.canAffectEntity(caster, target, effect.range())) {
                iterator.remove();
                continue;
            }
            tickOne(caster, target, effect);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent ignored) {
        ACTIVE.clear();
    }

    static int activeCount() { return ACTIVE.size(); }

    private static void tickOne(ServerPlayer caster, ServerPlayer target, AttentionEffect effect) {
        Vec3 toCaster = caster.getEyePosition().subtract(target.getEyePosition());
        if (toCaster.lengthSqr() < 1.0e-8) return;
        Vec3 direction = target.getViewVector(1.0F);
        double cosine = direction.normalize().dot(toCaster.normalize());
        if (cosine < Math.cos(Math.toRadians(effect.angle()))) return;
        double desiredYaw = Math.toDegrees(Math.atan2(-toCaster.x, toCaster.z));
        double delta = wrapDegrees(desiredYaw - target.getYRot());
        double step = Math.clamp(delta * effect.strength(),
                -MechanicLimits.MAX_ATTENTION_STEP_DEGREES,
                MechanicLimits.MAX_ATTENTION_STEP_DEGREES);
        target.setYRot((float) (target.getYRot() + step));
        target.setYHeadRot(target.getYRot());
        target.yRotO = target.getYRot();
        target.yHeadRotO = target.getYHeadRot();
        ServerTraces.ring(caster.serverLevel(), target.position().add(0, .05, 0),
                0.55F, PresentationElement.ARCANE, 4, .35F);
    }

    private static ServerPlayer player(MinecraftServer server, java.util.UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    private static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    private record AttentionEffect(ServerPlayer caster, ServerPlayer target, double range,
            double angle, double strength, long endTick) { }
}
