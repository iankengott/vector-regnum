package vectorregnum.neoforge.multiplayer;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** One server-authoritative permission boundary for every spell world mutation. */
public final class SpellSecurityPolicy {
    private SpellSecurityPolicy() { }

    public static boolean canAffectEntity(ServerPlayerEntity caster, Entity target) {
        if (target.isRemoved() || target.getWorld() != caster.getWorld()
                || !caster.getServerWorld().isChunkLoaded(target.getBlockPos())) return false;
        if (!(target instanceof ServerPlayerEntity other) || other == caster) return true;
        if (!caster.getServer().isPvpEnabled() || other.isSpectator()) return false;
        var casterTeam = caster.getScoreboardTeam();
        var targetTeam = other.getScoreboardTeam();
        return casterTeam == null || casterTeam != targetTeam || casterTeam.isFriendlyFireAllowed();
    }

    public static boolean canModifyBlock(ServerPlayerEntity caster, BlockPos pos, BlockState state) {
        ServerWorld world = caster.getServerWorld();
        if (!world.isChunkLoaded(pos) || !world.canPlayerModifyAt(caster, pos)) return false;
        ClaimLedger.ClaimKey key = MultiplayerLifecycleService.key(world, pos);
        if (!MultiplayerLifecycleService.claims(world).permits(key, caster.getUuid(),
                teamName(caster), caster.hasPermissionLevel(2))) return false;
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                world, caster, pos, state, world.getBlockEntity(pos));
    }

    public static String teamName(ServerPlayerEntity player) {
        return player.getScoreboardTeam() == null ? "" : player.getScoreboardTeam().getName();
    }
}
