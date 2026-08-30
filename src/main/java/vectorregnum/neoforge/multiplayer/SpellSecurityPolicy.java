package vectorregnum.neoforge.multiplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import vectorregnum.core.security.MechanicLimits;

/** One server-authoritative permission boundary for every spell world mutation. */
public final class SpellSecurityPolicy {
    private SpellSecurityPolicy() { }

    public static boolean canAffectEntity(ServerPlayer caster, Entity target) {
        return canAffectEntity(caster, target, MechanicLimits.MAX_RANGE);
    }

    /** Entity policy with an explicit bounded cast envelope. */
    public static boolean canAffectEntity(ServerPlayer caster, Entity target, double maxRange) {
        if (caster == null || target == null || target.isRemoved() || target.level() != caster.level()
                || !caster.serverLevel().isLoaded(target.blockPosition())
                || !Double.isFinite(maxRange) || maxRange < 0.0
                || caster.distanceToSqr(target) > maxRange * maxRange) return false;
        ClaimLedger.ClaimKey key = MultiplayerLifecycleService.key(caster.serverLevel(),
                target.blockPosition());
        if (!MultiplayerLifecycleService.claims(caster.serverLevel()).permits(key,
                caster.getUUID(), teamName(caster), caster.hasPermissions(2))) return false;
        if (!(target instanceof ServerPlayer other) || other == caster) return true;
        if (!caster.getServer().isPvpAllowed() || other.isSpectator()) return false;
        var casterTeam = caster.getTeam();
        var targetTeam = other.getTeam();
        return casterTeam == null || casterTeam != targetTeam || casterTeam.isAllowFriendlyFire();
    }

    public static boolean canModifyBlock(ServerPlayer caster, BlockPos pos, BlockState state) {
        if (caster == null || pos == null || state == null) return false;
        ServerLevel world = caster.serverLevel();
        if (!world.isLoaded(pos) || !world.mayInteract(caster, pos)) return false;
        ClaimLedger.ClaimKey key = MultiplayerLifecycleService.key(world, pos);
        if (!MultiplayerLifecycleService.claims(world).permits(key, caster.getUUID(),
                teamName(caster), caster.hasPermissions(2))) return false;
        return true;
    }

    /**
     * Applies the policy at the actual server break boundary. Calling
     * {@link #canModifyBlock(ServerPlayer, BlockPos, BlockState)} here is pure
     * validation; it never re-posts or re-enters this cancellable event.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel world)
                || player.serverLevel() != world
                || !canModifyBlock(player, event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    public static String teamName(ServerPlayer player) {
        return player.getTeam() == null ? "" : player.getTeam().getName();
    }
}
