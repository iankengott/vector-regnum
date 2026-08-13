package vectorregnum.fabric.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerDataMigrationTest {
    @Test
    void legacyAndCorruptStateIsClampedAndNormalized() {
        PlayerDataMigration.Snapshot migrated = PlayerDataMigration.migrate(
                new PlayerDataMigration.Snapshot(0, Double.NaN, 9_000, "storm",
                        42L, "NOT A DIMENSION", -5L), false, 5_000);
        assertEquals(PlayerDataMigration.CURRENT_SCHEMA, migrated.schema());
        assertEquals(5_000, migrated.capacity());
        assertEquals(0, migrated.mana());
        assertEquals("ARCANE", migrated.affinity());
        assertEquals("", migrated.sourceDimension());
        assertEquals(Long.MIN_VALUE, migrated.sourcePosition());
        assertEquals(0, migrated.channelLockUntil());
    }

    @Test
    void deathPreservesProgressButClearsTransientChannelLock() {
        PlayerDataMigration.Snapshot migrated = PlayerDataMigration.migrate(
                new PlayerDataMigration.Snapshot(1, 80, 100, "fire", 123L,
                        "minecraft:overworld", 9_000L), true, 5_000);
        assertEquals(80, migrated.mana());
        assertEquals(100, migrated.capacity());
        assertEquals("FIRE", migrated.affinity());
        assertEquals(123L, migrated.sourcePosition());
        assertEquals(0L, migrated.channelLockUntil());
    }
}
