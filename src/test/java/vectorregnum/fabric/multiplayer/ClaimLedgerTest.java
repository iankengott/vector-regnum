package vectorregnum.fabric.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
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
}
