package vectorregnum.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Server-authoritative, persistent mana. It intentionally has no regeneration. */
public final class ManaData {
    public static final double STARTING_MANA = 500.0;

    private static final AttachmentType<Double> MANA = AttachmentRegistry.<Double>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "mana"),
            builder -> builder
                    .initializer(() -> STARTING_MANA)
                    .persistent(Codec.DOUBLE)
                    .copyOnDeath());

    private static final AttachmentType<Long> CHANNEL_LOCK_UNTIL = AttachmentRegistry.<Long>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "channel_lock_until"),
            builder -> builder
                    .initializer(() -> 0L)
                    .persistent(Codec.LONG)
                    .copyOnDeath());

    private ManaData() {
    }

    public static void initialize() {
        // Class initialization registers both attachment types.
    }

    public static double available(ServerPlayerEntity player) {
        double value = player.getAttachedOrCreate(MANA);
        if (!Double.isFinite(value) || value < 0.0) {
            player.setAttached(MANA, 0.0);
            return 0.0;
        }
        return value;
    }

    public static boolean trySpend(ServerPlayerEntity player, double cost) {
        if (!Double.isFinite(cost) || cost < 0.0) {
            throw new IllegalArgumentException("Mana cost must be finite and non-negative");
        }
        double current = available(player);
        if (current + 1.0e-9 < cost) {
            return false;
        }
        player.setAttached(MANA, Math.max(0.0, current - cost));
        return true;
    }

    public static void drain(ServerPlayerEntity player) {
        player.setAttached(MANA, 0.0);
    }

    public static void refill(ServerPlayerEntity player) {
        player.setAttached(MANA, STARTING_MANA);
        player.setAttached(CHANNEL_LOCK_UNTIL, 0L);
    }

    public static boolean isChannelLocked(ServerPlayerEntity player) {
        return player.getServerWorld().getTime() < player.getAttachedOrCreate(CHANNEL_LOCK_UNTIL);
    }

    public static long remainingLockTicks(ServerPlayerEntity player) {
        return Math.max(0L,
                player.getAttachedOrCreate(CHANNEL_LOCK_UNTIL) - player.getServerWorld().getTime());
    }

    public static void lockChannel(ServerPlayerEntity player, long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("Lock duration cannot be negative");
        }
        long target = player.getServerWorld().getTime() + ticks;
        long current = player.getAttachedOrCreate(CHANNEL_LOCK_UNTIL);
        player.setAttached(CHANNEL_LOCK_UNTIL, Math.max(current, target));
    }
}
