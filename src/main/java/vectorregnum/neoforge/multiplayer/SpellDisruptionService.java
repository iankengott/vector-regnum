package vectorregnum.neoforge.multiplayer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import vectorregnum.neoforge.NeoForgeVmService;
import vectorregnum.core.security.MechanicCapability;
import vectorregnum.core.security.MechanicRequest;
import vectorregnum.core.security.SpellDisruptionPolicy;

/** Bounded reverse-unwriting adapter for a Combat companion or server event. */
public final class SpellDisruptionService {
    private static final double MAX_RANGE = 4.0;

    private SpellDisruptionService() { }

    /**
     * Attempts one disruption. Crouching is the built-in disruption stance and
     * an enchanted netherite sword is the intentionally narrow weapon proof.
     */
    public static boolean tryDisrupt(ServerPlayer attacker, ServerPlayer target,
            ItemStack weapon, long timingWindowTicks) {
        if (attacker == null || target == null || weapon == null || attacker == target) return false;
        if (!attacker.hasLineOfSight(target)
                || attacker.distanceTo(target) > MAX_RANGE
                || !SpellSecurityPolicy.canAffectEntity(attacker, target, MAX_RANGE)) return false;
        MechanicRequest request = new MechanicRequest(MechanicCapability.SPELL_DISRUPTION,
                attacker.distanceTo(target), 1, 1,
                attacker.level() == target.level(),
                attacker.serverLevel().isLoaded(attacker.blockPosition()),
                target.serverLevel().isLoaded(target.blockPosition()), true,
                attacker.getServer().isPvpAllowed(), true, true);
        boolean weaponReady = weapon.isEnchanted()
                && weapon.is(net.minecraft.world.item.Items.NETHERITE_SWORD);
        if (!SpellDisruptionPolicy.evaluate(request,
                NeoForgeVmService.hasActiveSpell(target.getUUID()),
                attacker.isCrouching(), weaponReady, (int) timingWindowTicks).allowed()) return false;
        return NeoForgeVmService.disruptOwner(target.getUUID(),
                "reverse-unwritten by " + attacker.getStringUUID());
    }
}
