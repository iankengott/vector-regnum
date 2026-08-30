package vectorregnum.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlock;
import vectorregnum.neoforge.progression.ManaDrawRules;
import vectorregnum.neoforge.progression.ProgressionContent;
import vectorregnum.neoforge.multiplayer.PlayerDataMigration;
import vectorregnum.core.Element;
import vectorregnum.core.ElementalAffinityMatrix;
import vectorregnum.core.NaturalElementSelector;

/** Server-authoritative, persistent mana. It intentionally has no regeneration. */
public final class ManaData {
    public static final double STARTING_MANA = 0.0;
    public static final double STARTING_CAPACITY = 0.0;
    public static final double MAX_CAPACITY = 5_000.0;

    private ManaData() {
    }

    public static void initialize() {
        // Registration is owned by PlayerAttachmentContent and the mod bus.
    }

    public static double available(ServerPlayer player) {
        double value = player.getData(PlayerAttachmentContent.MANA);
        double capacity = capacity(player);
        if (!Double.isFinite(value) || value < 0.0 || value > capacity) {
            value = Math.clamp(Double.isFinite(value) ? value : 0.0, 0.0, capacity);
            player.setData(PlayerAttachmentContent.MANA, value);
        }
        return value;
    }

    public static double capacity(ServerPlayer player) {
        double value = player.getData(PlayerAttachmentContent.MANA_CAPACITY);
        if (!Double.isFinite(value) || value < 0.0 || value > MAX_CAPACITY) {
            value = Math.clamp(Double.isFinite(value) ? value : 0.0, 0.0, MAX_CAPACITY);
            player.setData(PlayerAttachmentContent.MANA_CAPACITY, value);
        }
        return value;
    }

    public static boolean tryCreditExact(ServerPlayer player, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Mana credit must be finite and non-negative");
        }
        double current = available(player);
        double capacity = capacity(player);
        double heldForRefund = CastingResourceService.reservedMana(player.getUUID())
                + CastingResourceService.reservedRitualMana(player);
        if (current + amount + heldForRefund > capacity + 1.0e-9) {
            return false;
        }
        player.setData(PlayerAttachmentContent.MANA, Math.min(capacity, current + amount));
        return true;
    }

    public static boolean growCapacity(ServerPlayer player, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("Capacity growth must be finite and positive");
        }
        double current = capacity(player);
        if (current >= MAX_CAPACITY) {
            return false;
        }
        player.setData(PlayerAttachmentContent.MANA_CAPACITY, Math.min(MAX_CAPACITY, current + amount));
        return true;
    }

    public static ManaAffinity channelAffinity(ServerPlayer player) {
        String value = player.getData(PlayerAttachmentContent.MANA_AFFINITY);
        ManaAffinity parsed = ManaAffinity.fromId(value).orElse(null);
        if (parsed == null) {
            player.setData(PlayerAttachmentContent.MANA_AFFINITY, ManaAffinity.ARCANE.name());
            return ManaAffinity.ARCANE;
        }
        if (!parsed.name().equals(value)) {
            player.setData(PlayerAttachmentContent.MANA_AFFINITY, parsed.name());
        }
        return parsed;
    }

    public static void setChannelAffinity(ServerPlayer player, ManaAffinity affinity) {
        player.setData(PlayerAttachmentContent.MANA_AFFINITY, affinity.name());
    }

    /** Applies the permanent natural-to-spell affinity cost adjustment. */
    public static double adjustedCost(ServerPlayer player, double base, Element spellElement) {
        Element spell = spellElement == null ? Element.ARCANE : spellElement;
        return ElementalAffinityMatrix.canonical().adjustedCost(
                base, naturalElement(player), spell);
    }

    public static double adjustedUpkeep(ServerPlayer player, double base, Element spellElement) {
        Element spell = spellElement == null ? Element.ARCANE : spellElement;
        return ElementalAffinityMatrix.canonical().upkeepCost(base, naturalElement(player), spell);
    }

    /** Affinity-derived instability units. Aligned casts are 1.0 and opposed casts are 4.0. */
    public static double instability(ServerPlayer player, Element spellElement) {
        Element spell = spellElement == null ? Element.ARCANE : spellElement;
        return 1.0 / ElementalAffinityMatrix.canonical().stabilityEfficiency(
                naturalElement(player), spell);
    }

    /** Scales a genuine fault's channel lock using the same deterministic stability band. */
    public static long stabilityLockTicks(ServerPlayer player, long baseTicks, Element spellElement) {
        if (baseTicks < 0L) throw new IllegalArgumentException("Lock duration cannot be negative");
        Element spell = spellElement == null ? Element.ARCANE : spellElement;
        double efficiency = ElementalAffinityMatrix.canonical().stabilityEfficiency(
                naturalElement(player), spell);
        return (long) Math.ceil(baseTicks / efficiency);
    }

    /** Applies the bounded final instability value already shown in the priority-22 quote. */
    public static long stabilityLockTicks(long baseTicks, double quotedInstability) {
        if (baseTicks < 0L || !Double.isFinite(quotedInstability) || quotedInstability < 0.0) {
            throw new IllegalArgumentException("Invalid quoted stability lock input");
        }
        return (long) Math.ceil(baseTicks * quotedInstability);
    }

    /** Compatibility aliases for the pre-priority-21 channel API. */
    @Deprecated(forRemoval = false)
    public static ManaAffinity affinity(ServerPlayer player) {
        return channelAffinity(player);
    }

    @Deprecated(forRemoval = false)
    public static void setAffinity(ServerPlayer player, ManaAffinity affinity) {
        setChannelAffinity(player, affinity);
    }

    /** Returns the one permanent natural element, repairing legacy/corrupt state once. */
    public static Element naturalElement(ServerPlayer player) {
        String stored = player.getData(PlayerAttachmentContent.NATURAL_ELEMENT);
        Element parsed = Element.fromId(stored).filter(Element::isNatural).orElse(null);
        if (parsed != null) return parsed;
        Element selected = NaturalElementSelector.select(player.getUUID());
        player.setData(PlayerAttachmentContent.NATURAL_ELEMENT,
                selected.id().toUpperCase(java.util.Locale.ROOT));
        return selected;
    }

    public static void recordAttunedSource(ServerPlayer player, BlockPos source) {
        player.setData(PlayerAttachmentContent.ATTUNED_SOURCE, source.asLong());
        player.setData(PlayerAttachmentContent.ATTUNED_DIMENSION,
                player.serverLevel().dimension().location().toString());
    }

    public static BlockPos attunedSource(ServerPlayer player) {
        long encoded = player.getData(PlayerAttachmentContent.ATTUNED_SOURCE);
        return encoded == Long.MIN_VALUE ? null : BlockPos.of(encoded);
    }

    public static String attunedDimension(ServerPlayer player) {
        return player.getData(PlayerAttachmentContent.ATTUNED_DIMENSION);
    }

    /** Pulls finite charges from the loaded, same-dimension attuned node when needed. */
    public static boolean ensureAvailable(ServerPlayer player, double required) {
        if (!Double.isFinite(required) || required < 0.0) {
            throw new IllegalArgumentException("Required mana must be finite and non-negative");
        }
        double current = available(player);
        if (current + 1.0e-9 >= required) return true;
        double capacity = capacity(player);
        if (capacity + 1.0e-9 < required) return false;
        BlockPos source = attunedSource(player);
        String dimension = player.serverLevel().dimension().location().toString();
        if (source == null || !dimension.equals(attunedDimension(player))) return false;
        var world = player.serverLevel();
        if (!world.hasChunkAt(source)) return false;

        BlockState state = world.getBlockState(source);
        if (!state.is(ProgressionContent.manaCrystalNode())) return false;
        int charges = state.getValue(ManaCrystalNodeBlock.CHARGE);
        if (charges <= 0) return false;
        double distance = player.position().distanceTo(Vec3.atCenterOf(source));
        int offered = ManaDrawRules.offeredMana(ManaCrystalNodeBlock.MANA_PER_CHARGE,
                distance, state.getValue(ManaCrystalNodeBlock.AFFINITY), channelAffinity(player));
        if (offered <= 0) return false;
        // Validate durable refund accounting before mutating the source block. The
        // subsequent credit runs on the same authoritative tick thread.
        CastingResourceService.reservedRitualMana(player);
        int chargesNeeded = (int) Math.ceil((required - current - 1.0e-9) / offered);
        if (chargesNeeded <= 0 || chargesNeeded > charges) return false;
        double accepted = Math.min((double) offered * chargesNeeded, capacity - current);
        if (current + accepted + 1.0e-9 < required) return false;

        BlockState depleted = state.setValue(ManaCrystalNodeBlock.CHARGE, charges - chargesNeeded);
        if (!world.setBlock(source, depleted, Block.UPDATE_ALL)) return false;
        if (!tryCreditExact(player, accepted)) {
            world.setBlock(source, state, Block.UPDATE_ALL);
            return false;
        }
        return true;
    }

    public static void setForTesting(ServerPlayer player, double capacity, double mana) {
        if (!Double.isFinite(capacity) || !Double.isFinite(mana)
                || capacity < 0.0 || capacity > MAX_CAPACITY || mana < 0.0 || mana > capacity) {
            throw new IllegalArgumentException("Invalid test mana state");
        }
        player.setData(PlayerAttachmentContent.MANA_CAPACITY, capacity);
        player.setData(PlayerAttachmentContent.MANA, mana);
        player.setData(PlayerAttachmentContent.CHANNEL_LOCK_UNTIL, 0L);
    }

    public static boolean trySpend(ServerPlayer player, double cost) {
        if (!Double.isFinite(cost) || cost < 0.0) {
            throw new IllegalArgumentException("Mana cost must be finite and non-negative");
        }
        double current = available(player);
        if (current + 1.0e-9 < cost) {
            return false;
        }
        player.setData(PlayerAttachmentContent.MANA, Math.max(0.0, current - cost));
        return true;
    }

    public static void drain(ServerPlayer player) {
        player.setData(PlayerAttachmentContent.MANA, 0.0);
    }

    public static void refill(ServerPlayer player) {
        double devCapacity = Math.max(500.0, capacity(player));
        setForTesting(player, devCapacity, devCapacity);
    }

    public static boolean isChannelLocked(ServerPlayer player) {
        return player.serverLevel().getGameTime()
                < player.getData(PlayerAttachmentContent.CHANNEL_LOCK_UNTIL);
    }

    public static long remainingLockTicks(ServerPlayer player) {
        return Math.max(0L,
                player.getData(PlayerAttachmentContent.CHANNEL_LOCK_UNTIL)
                        - player.serverLevel().getGameTime());
    }

    public static void lockChannel(ServerPlayer player, long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("Lock duration cannot be negative");
        }
        long target = player.serverLevel().getGameTime() + ticks;
        long current = player.getData(PlayerAttachmentContent.CHANNEL_LOCK_UNTIL);
        player.setData(PlayerAttachmentContent.CHANNEL_LOCK_UNTIL, Math.max(current, target));
    }

    /** Repairs legacy/corrupt fields and clears death-only transient channel state. */
    public static void migrateAndSanitize(ServerPlayer player, boolean deathCopy, int schema) {
        PlayerDataMigration.Snapshot migrated = PlayerDataMigration.migrate(
                new PlayerDataMigration.Snapshot(schema,
                        player.getData(PlayerAttachmentContent.MANA),
                        player.getData(PlayerAttachmentContent.MANA_CAPACITY),
                        player.getData(PlayerAttachmentContent.MANA_AFFINITY),
                        player.getData(PlayerAttachmentContent.NATURAL_ELEMENT),
                        player.getData(PlayerAttachmentContent.ATTUNED_SOURCE),
                        player.getData(PlayerAttachmentContent.ATTUNED_DIMENSION),
                        player.getData(PlayerAttachmentContent.CHANNEL_LOCK_UNTIL)),
                deathCopy, MAX_CAPACITY, player.getUUID());
        player.setData(PlayerAttachmentContent.MANA_CAPACITY, migrated.capacity());
        player.setData(PlayerAttachmentContent.MANA, migrated.mana());
        player.setData(PlayerAttachmentContent.MANA_AFFINITY, migrated.affinity());
        player.setData(PlayerAttachmentContent.NATURAL_ELEMENT, migrated.naturalElement());
        player.setData(PlayerAttachmentContent.ATTUNED_SOURCE, migrated.sourcePosition());
        player.setData(PlayerAttachmentContent.ATTUNED_DIMENSION, migrated.sourceDimension());
        player.setData(PlayerAttachmentContent.CHANNEL_LOCK_UNTIL, migrated.channelLockUntil());
    }
}
