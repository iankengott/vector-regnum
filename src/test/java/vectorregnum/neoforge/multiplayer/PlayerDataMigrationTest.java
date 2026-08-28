package vectorregnum.neoforge.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import vectorregnum.core.Element;

class PlayerDataMigrationTest {
    @Test
    void legacyAndCorruptStateIsClampedAndNormalized() {
        PlayerDataMigration.Snapshot migrated = PlayerDataMigration.migrate(
                new PlayerDataMigration.Snapshot(0, Double.NaN, 9_000, "storm",
                        "", 42L, "NOT A DIMENSION", -5L), false, 5_000,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertEquals(PlayerDataMigration.CURRENT_SCHEMA, migrated.schema());
        assertEquals(5_000, migrated.capacity());
        assertEquals(0, migrated.mana());
        assertEquals("ARCANE", migrated.affinity());
        assertTrue(Element.fromId(migrated.naturalElement()).orElseThrow().isNatural());
        assertEquals("", migrated.sourceDimension());
        assertEquals(Long.MIN_VALUE, migrated.sourcePosition());
        assertEquals(0, migrated.channelLockUntil());
    }

    @Test
    void deathPreservesProgressButClearsTransientChannelLock() {
        PlayerDataMigration.Snapshot migrated = PlayerDataMigration.migrate(
                new PlayerDataMigration.Snapshot(1, 80, 100, "frost", "frost", 123L,
                        "minecraft:overworld", 9_000L), true, 5_000,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertEquals(80, migrated.mana());
        assertEquals(100, migrated.capacity());
        assertEquals("ICE", migrated.affinity());
        assertEquals("ICE", migrated.naturalElement());
        assertEquals(123L, migrated.sourcePosition());
        assertEquals(0L, migrated.channelLockUntil());
    }

    @Test
    void missingNaturalIdentityIsDeterministicAndIdempotent() {
        UUID playerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        PlayerDataMigration.Snapshot initial = new PlayerDataMigration.Snapshot(
                2, 12, 100, "fire", "", Long.MIN_VALUE, "", 44L);
        PlayerDataMigration.Snapshot once = PlayerDataMigration.migrate(initial, false, 5_000, playerId);
        PlayerDataMigration.Snapshot twice = PlayerDataMigration.migrate(once, false, 5_000, playerId);

        assertEquals(once, twice);
        assertNotEquals("ARCANE", once.naturalElement());
        assertTrue(Element.fromId(once.naturalElement()).orElseThrow().isNatural());
    }

    @Test
    void validVoidIdentityIsPreservedAcrossDeathMigration() {
        UUID playerId = UUID.randomUUID();
        PlayerDataMigration.Snapshot stored = new PlayerDataMigration.Snapshot(
                2, 1, 2, "arcane", "void", Long.MIN_VALUE, "", 99L);
        PlayerDataMigration.Snapshot migrated = PlayerDataMigration.migrate(stored, true, 5_000, playerId);

        assertEquals("VOID", migrated.naturalElement());
        assertEquals("ARCANE", migrated.affinity());
        assertEquals(0L, migrated.channelLockUntil());
    }
}
