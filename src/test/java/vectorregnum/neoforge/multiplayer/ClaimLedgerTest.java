package vectorregnum.neoforge.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

class ClaimLedgerTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEAMMATE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void privateAndTeamClaimsHaveExplicitPermissionSemantics() {
        ClaimLedger.ClaimKey privateKey = new ClaimLedger.ClaimKey("minecraft:overworld", 1, 2);
        ClaimLedger.Change privateChange = ClaimLedger.EMPTY.claim(privateKey, OWNER, "red",
                ClaimLedger.Access.OWNER_ONLY);
        assertTrue(privateChange.accepted());
        assertTrue(privateChange.ledger().permits(privateKey, OWNER, "", false));
        assertFalse(privateChange.ledger().permits(privateKey, TEAMMATE, "red", false));
        assertTrue(privateChange.ledger().permits(privateKey, STRANGER, "", true));

        ClaimLedger.ClaimKey teamKey = new ClaimLedger.ClaimKey("minecraft:overworld", 2, 2);
        ClaimLedger ledger = privateChange.ledger().claim(teamKey, OWNER, "red",
                ClaimLedger.Access.TEAM).ledger();
        assertTrue(ledger.permits(teamKey, TEAMMATE, "red", false));
        assertFalse(ledger.permits(teamKey, STRANGER, "blue", false));
    }

    @Test
    void releaseAndCapacityLimitsCannotBeUsedToStealOrFloodClaims() {
        ClaimLedger.ClaimKey key = new ClaimLedger.ClaimKey("minecraft:overworld", 0, 0);
        ClaimLedger ledger = ClaimLedger.EMPTY.claim(key, OWNER, "", ClaimLedger.Access.OWNER_ONLY).ledger();
        assertFalse(ledger.release(key, STRANGER, false).accepted());
        assertTrue(ledger.release(key, STRANGER, true).accepted());

        for (int index = 1; index <= ClaimLedger.MAX_CLAIMS_PER_OWNER; index++) {
            ledger = ledger.claim(new ClaimLedger.ClaimKey("minecraft:overworld", index, 0),
                    OWNER, "", ClaimLedger.Access.OWNER_ONLY).ledger();
        }
        assertFalse(ledger.claim(new ClaimLedger.ClaimKey("minecraft:overworld", 99, 0),
                OWNER, "", ClaimLedger.Access.OWNER_ONLY).accepted());
    }

    @Test
    void schemaOneClaimsMigrateToPrivateWithoutChangingOwnership() {
        ClaimLedger migrated = ClaimLedger.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("[\"minecraft:overworld|-3|8|" + OWNER + "\"]"))
                .getOrThrow();
        assertEquals(ClaimLedger.CURRENT_SCHEMA, migrated.schemaVersion());
        assertEquals(OWNER, migrated.claims().getFirst().owner());
        assertEquals(ClaimLedger.Access.OWNER_ONLY, migrated.claims().getFirst().access());
    }

    @Test
    void savedDataRoundTripsClaimsAndOwnsDirtyState() {
        ClaimLedger ledger = ClaimLedger.EMPTY.claim(
                new ClaimLedger.ClaimKey("minecraft:overworld", 4, -2), OWNER, "red",
                ClaimLedger.Access.TEAM).ledger();
        ClaimSavedData saved = new ClaimSavedData(ledger);
        assertFalse(saved.isDirty());

        CompoundTag encoded = saved.save(new CompoundTag(), null);
        ClaimSavedData loaded = ClaimSavedData.load(encoded, null);
        assertEquals(ledger, loaded.ledger());
        assertFalse(loaded.isDirty());

        assertFalse(loaded.replace(ledger), "equal retries must not dirty the save");
        ClaimLedger released = ledger.release(
                new ClaimLedger.ClaimKey("minecraft:overworld", 4, -2), OWNER, false).ledger();
        assertTrue(loaded.replace(released));
        assertTrue(loaded.isDirty());
    }

    @Test
    void savedDataMigratesSchemaOneAndResetsCorruptPayload() {
        CompoundTag schemaOne = new CompoundTag();
        ListTag claims = new ListTag();
        claims.add(StringTag.valueOf("minecraft:overworld|-3|8|" + OWNER));
        schemaOne.put("claims", claims);

        ClaimSavedData migrated = ClaimSavedData.load(schemaOne, null);
        assertEquals(ClaimLedger.CURRENT_SCHEMA, migrated.ledger().schemaVersion());
        assertEquals(ClaimLedger.Access.OWNER_ONLY,
                migrated.ledger().claims().getFirst().access());
        assertTrue(migrated.isDirty(), "schema migration must be persisted");

        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("claims", "not a list");
        ClaimSavedData fallback = ClaimSavedData.load(corrupt, null);
        assertEquals(ClaimLedger.EMPTY, fallback.ledger());
        assertTrue(fallback.isDirty(), "corrupt data should converge to the fallback");
    }
}
