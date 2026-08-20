package vectorregnum.neoforge;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlock;
import vectorregnum.neoforge.progression.ManaDrawRules;
import vectorregnum.neoforge.progression.ProgressionContent;
import vectorregnum.neoforge.multiplayer.PlayerDataMigration;

/** Server-authoritative, persistent mana. It intentionally has no regeneration. */
public final class ManaData {
    public static final double STARTING_MANA = 0.0;
    public static final double STARTING_CAPACITY = 0.0;
    public static final double MAX_CAPACITY = 5_000.0;

    private static final AttachmentType<Double> MANA = AttachmentRegistry.<Double>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "mana"),
            builder -> builder
                    .initializer(() -> STARTING_MANA)
                    .persistent(Codec.DOUBLE)
                    .copyOnDeath());

    private static final AttachmentType<Double> CAPACITY = AttachmentRegistry.<Double>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "mana_capacity"),
            builder -> builder
                    .initializer(() -> STARTING_CAPACITY)
                    .persistent(Codec.DOUBLE)
                    .copyOnDeath());

    private static final AttachmentType<String> AFFINITY = AttachmentRegistry.<String>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "mana_affinity"),
            builder -> builder
                    .initializer(() -> ManaAffinity.ARCANE.name())
                    .persistent(Codec.STRING)
                    .copyOnDeath());

    private static final AttachmentType<Long> ATTUNED_SOURCE = AttachmentRegistry.<Long>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "attuned_source"),
            builder -> builder
                    .initializer(() -> Long.MIN_VALUE)
                    .persistent(Codec.LONG)
                    .copyOnDeath());

    private static final AttachmentType<String> ATTUNED_DIMENSION = AttachmentRegistry.<String>create(
            Identifier.of(VectorRegnumMod.MOD_ID, "attuned_dimension"),
            builder -> builder
                    .initializer(() -> "")
                    .persistent(Codec.STRING)
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
        // Class initialization registers the persistent channel attachments.
    }

    public static double available(ServerPlayerEntity player) {
        double value = player.getAttachedOrCreate(MANA);
        double capacity = capacity(player);
        if (!Double.isFinite(value) || value < 0.0 || value > capacity) {
            value = Math.clamp(Double.isFinite(value) ? value : 0.0, 0.0, capacity);
            player.setAttached(MANA, value);
        }
        return value;
    }

    public static double capacity(ServerPlayerEntity player) {
        double value = player.getAttachedOrCreate(CAPACITY);
        if (!Double.isFinite(value) || value < 0.0 || value > MAX_CAPACITY) {
            value = Math.clamp(Double.isFinite(value) ? value : 0.0, 0.0, MAX_CAPACITY);
            player.setAttached(CAPACITY, value);
        }
        return value;
    }

    public static boolean tryCreditExact(ServerPlayerEntity player, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Mana credit must be finite and non-negative");
        }
        double current = available(player);
        double capacity = capacity(player);
        if (current + amount > capacity + 1.0e-9) {
            return false;
        }
        player.setAttached(MANA, Math.min(capacity, current + amount));
        return true;
    }

    public static boolean growCapacity(ServerPlayerEntity player, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("Capacity growth must be finite and positive");
        }
        double current = capacity(player);
        if (current >= MAX_CAPACITY) {
            return false;
        }
        player.setAttached(CAPACITY, Math.min(MAX_CAPACITY, current + amount));
        return true;
    }

    public static ManaAffinity affinity(ServerPlayerEntity player) {
        String value = player.getAttachedOrCreate(AFFINITY);
        try {
            return ManaAffinity.valueOf(value);
        } catch (IllegalArgumentException exception) {
            player.setAttached(AFFINITY, ManaAffinity.ARCANE.name());
            return ManaAffinity.ARCANE;
        }
    }

    public static void setAffinity(ServerPlayerEntity player, ManaAffinity affinity) {
        player.setAttached(AFFINITY, affinity.name());
    }

    public static void recordAttunedSource(ServerPlayerEntity player, BlockPos source) {
        player.setAttached(ATTUNED_SOURCE, source.asLong());
        player.setAttached(ATTUNED_DIMENSION,
                player.getServerWorld().getRegistryKey().getValue().toString());
    }

    public static BlockPos attunedSource(ServerPlayerEntity player) {
        long encoded = player.getAttachedOrCreate(ATTUNED_SOURCE);
        return encoded == Long.MIN_VALUE ? null : BlockPos.fromLong(encoded);
    }

    public static String attunedDimension(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(ATTUNED_DIMENSION);
    }

    /** Pulls finite charges from the loaded, same-dimension attuned node when needed. */
    public static boolean ensureAvailable(ServerPlayerEntity player, double required) {
        if (!Double.isFinite(required) || required < 0.0) {
            throw new IllegalArgumentException("Required mana must be finite and non-negative");
        }
        if (available(player) + 1.0e-9 >= required) return true;
        BlockPos source = attunedSource(player);
        String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
        if (source == null || !dimension.equals(attunedDimension(player))) return false;
        var world = player.getServerWorld();
        if (!world.getChunkManager().isChunkLoaded(source.getX() >> 4, source.getZ() >> 4)) return false;

        while (available(player) + 1.0e-9 < required) {
            var state = world.getBlockState(source);
            if (!state.isOf(ProgressionContent.MANA_CRYSTAL_NODE)) return false;
            int charges = state.get(ManaCrystalNodeBlock.CHARGE);
            if (charges <= 0) return false;
            double distance = player.getPos().distanceTo(net.minecraft.util.math.Vec3d.ofCenter(source));
            int offered = ManaDrawRules.offeredMana(ManaCrystalNodeBlock.MANA_PER_CHARGE,
                    distance, state.get(ManaCrystalNodeBlock.AFFINITY), affinity(player));
            double accepted = Math.min(offered, capacity(player) - available(player));
            if (accepted <= 0 || !tryCreditExact(player, accepted)) return false;
            world.setBlockState(source,
                    state.with(ManaCrystalNodeBlock.CHARGE, charges - 1),
                    net.minecraft.block.Block.NOTIFY_ALL);
        }
        return true;
    }

    public static void setForTesting(ServerPlayerEntity player, double capacity, double mana) {
        if (!Double.isFinite(capacity) || !Double.isFinite(mana)
                || capacity < 0.0 || capacity > MAX_CAPACITY || mana < 0.0 || mana > capacity) {
            throw new IllegalArgumentException("Invalid test mana state");
        }
        player.setAttached(CAPACITY, capacity);
        player.setAttached(MANA, mana);
        player.setAttached(CHANNEL_LOCK_UNTIL, 0L);
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
        double devCapacity = Math.max(500.0, capacity(player));
        setForTesting(player, devCapacity, devCapacity);
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

    /** Repairs legacy/corrupt fields and clears death-only transient channel state. */
    public static void migrateAndSanitize(ServerPlayerEntity player, boolean deathCopy, int schema) {
        PlayerDataMigration.Snapshot migrated = PlayerDataMigration.migrate(
                new PlayerDataMigration.Snapshot(schema,
                        player.getAttachedOrCreate(MANA), player.getAttachedOrCreate(CAPACITY),
                        player.getAttachedOrCreate(AFFINITY),
                        player.getAttachedOrCreate(ATTUNED_SOURCE),
                        player.getAttachedOrCreate(ATTUNED_DIMENSION),
                        player.getAttachedOrCreate(CHANNEL_LOCK_UNTIL)),
                deathCopy, MAX_CAPACITY);
        player.setAttached(CAPACITY, migrated.capacity());
        player.setAttached(MANA, migrated.mana());
        player.setAttached(AFFINITY, migrated.affinity());
        player.setAttached(ATTUNED_SOURCE, migrated.sourcePosition());
        player.setAttached(ATTUNED_DIMENSION, migrated.sourceDimension());
        player.setAttached(CHANNEL_LOCK_UNTIL, migrated.channelLockUntil());
    }
}
