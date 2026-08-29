package vectorregnum.core.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistentEffectLedgerTest {
    private static final UUID EFFECT = UUID.fromString("00000000-0000-0000-0000-000000000023");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void contractCarriesVersionedOwnershipEndpointUpkeepAndCleanupHandles() {
        PersistentEffectContract contract = contract(40L, 40L, 4.0);
        assertEquals(PersistentEffectContract.CURRENT_SCHEMA, contract.schema());
        assertEquals(EFFECT, contract.effectId());
        assertEquals(OWNER, contract.ownerId());
        assertEquals("sha256:test", contract.programHash());
        assertEquals("minecraft:overworld", contract.dimension());
        assertTrue(contract.hasNaturalDeadline());
        assertEquals(40L, contract.effectiveDeadlineTick());
        assertEquals(2.0, contract.upkeepPerInterval());
        assertEquals(List.of("block|1|2|3|vector_regnum:mage_light"), contract.handles());
    }

    @Test
    void exactCadenceDebitsOnceAndDuplicateTicksAreNoOps() {
        PersistentEffectLedger ledger = PersistentEffectLedger.EMPTY.register(
                contract(40L, 80L, 4.0)).ledger();
        var first = ledger.reconcile(EFFECT, 0L, true);
        assertEquals(PersistentEffectLedger.Decision.UPKEEP_PAID, first.decision());
        assertEquals(2.0, first.upkeepDebited());
        assertEquals(2.0, first.contract().prepaidUpkeep());

        var duplicate = first.ledger().reconcile(EFFECT, 0L, true);
        assertEquals(PersistentEffectLedger.Decision.ACTIVE, duplicate.decision());
        assertEquals(0.0, duplicate.upkeepDebited());
        assertSame(first.contract(), duplicate.contract());

        var second = duplicate.ledger().reconcile(EFFECT, 20L, true);
        assertEquals(PersistentEffectLedger.Decision.UPKEEP_PAID, second.decision());
        assertEquals(0.0, second.contract().prepaidUpkeep());
    }

    @Test
    void unloadedEffectsPauseThenReconcileElapsedDebt() {
        PersistentEffectLedger ledger = PersistentEffectLedger.EMPTY.register(
                contract(80L, 80L, 8.0)).ledger();
        var unloaded = ledger.reconcile(EFFECT, 40L, false);
        assertEquals(PersistentEffectLedger.Decision.WAITING_UNLOADED, unloaded.decision());
        assertSame(ledger, unloaded.ledger());
        assertEquals(8.0, unloaded.contract().prepaidUpkeep());

        var loaded = ledger.reconcile(EFFECT, 40L, true);
        assertEquals(PersistentEffectLedger.Decision.UPKEEP_PAID, loaded.decision());
        assertEquals(6.0, loaded.upkeepDebited());
        assertEquals(2.0, loaded.contract().prepaidUpkeep());
    }

    @Test
    void naturalDeadlineCleansWithoutWildMagicAndRemovalIsIdempotent() {
        PersistentEffectLedger ledger = PersistentEffectLedger.EMPTY.register(
                contract(40L, 80L, 4.0)).ledger();
        var ended = ledger.reconcile(EFFECT, 40L, true);
        assertEquals(PersistentEffectLedger.Decision.NATURAL_CONCLUSION, ended.decision());
        assertEquals(PersistentEffectContract.State.CONCLUDING, ended.contract().state());
        assertEquals(PersistentEffectLedger.Decision.CONCLUSION_PENDING_CLEANUP,
                ended.ledger().reconcile(EFFECT, 41L, true).decision());
        var cleaned = ended.ledger().completeCleanup(EFFECT);
        var removed = cleaned.ledger().removeCleaned(EFFECT);
        assertTrue(removed.changed());
        assertTrue(removed.ledger().entries().isEmpty());
        assertFalse(removed.ledger().removeCleaned(EFFECT).changed());
    }

    @Test
    void underpaymentAndHardCapCollapseOnlyOnceBeforeCleanup() {
        PersistentEffectContract unpaid = new PersistentEffectContract(
                PersistentEffectContract.CURRENT_SCHEMA, EFFECT, OWNER, "sha256:test",
                "minecraft:overworld", 0L, 0L, 60L, 60L, 20, 0L,
                2.0, 1.0, 23L, PersistentEffectContract.State.ACTIVE,
                List.of("status|target|minecraft:glowing|0"));
        PersistentEffectLedger ledger = PersistentEffectLedger.EMPTY.register(unpaid).ledger();
        var collapsed = ledger.reconcile(EFFECT, 0L, true);
        assertEquals(PersistentEffectLedger.Decision.COLLAPSE_UNPAID, collapsed.decision());
        assertEquals(23L, collapsed.contract().collapseSeed());
        assertEquals(PersistentEffectLedger.Decision.COLLAPSE_PENDING_EMISSION,
                collapsed.ledger().reconcile(EFFECT, 1L, true).decision());
        var emitted = collapsed.ledger().completeCollapseEmission(EFFECT);
        assertEquals(PersistentEffectContract.State.COLLAPSE_EMITTED,
                emitted.ledger().get(EFFECT).state());
        assertEquals(PersistentEffectLedger.Decision.COLLAPSE_PENDING_CLEANUP,
                emitted.ledger().reconcile(EFFECT, 2L, true).decision());
        var cleaned = emitted.ledger().completeCleanup(EFFECT);
        assertTrue(cleaned.changed());
        assertFalse(cleaned.ledger().completeCleanup(EFFECT).changed());

        UUID noEndpointId = UUID.fromString("00000000-0000-0000-0000-000000000024");
        PersistentEffectContract noEndpoint = new PersistentEffectContract(
                PersistentEffectContract.CURRENT_SCHEMA, noEndpointId, OWNER, "sha256:open",
                "minecraft:overworld", 0L, 0L,
                PersistentEffectContract.NO_NATURAL_DEADLINE, 30L, 20, 0L,
                0.0, 0.0, 24L, PersistentEffectContract.State.ACTIVE,
                List.of("force|target|damping|0.5"));
        var hardCap = PersistentEffectLedger.EMPTY.register(noEndpoint).ledger()
                .reconcile(noEndpointId, 30L, true);
        assertEquals(PersistentEffectLedger.Decision.COLLAPSE_HARD_CAP, hardCap.decision());
    }

    @Test
    void validationAndWorldBoundsRejectUnsafeData() {
        assertThrows(IllegalArgumentException.class, () -> new PersistentEffectContract(
                2, EFFECT, OWNER, "hash", "dimension", 0L, 0L, 1L, 1L,
                20, 0L, 0.0, 0.0, 0L,
                PersistentEffectContract.State.ACTIVE, List.of("handle")));
        assertThrows(IllegalArgumentException.class, () -> new PersistentEffectContract(
                1, EFFECT, OWNER, "hash", "dimension", 0L, 0L, 1L, 1L,
                20, 0L, 0.0, 0.0, 0L,
                PersistentEffectContract.State.ACTIVE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> contract(80_000L, 80_000L, 1.0));

        Map<UUID, PersistentEffectContract> tooMany = new java.util.LinkedHashMap<>();
        for (int index = 0; index <= PersistentEffectLedger.MAX_WORLD_EFFECTS; index++) {
            UUID id = new UUID(0L, index + 1L);
            tooMany.put(id, PersistentEffectContract.active(id, OWNER, "hash", "dimension",
                    0L, 20L, 20L, 20, 0.0, index, List.of("handle-" + index)));
        }
        assertThrows(IllegalArgumentException.class, () -> new PersistentEffectLedger(tooMany));
    }

    private static PersistentEffectContract contract(long natural, long hard, double upkeep) {
        return PersistentEffectContract.active(EFFECT, OWNER, "sha256:test",
                "minecraft:overworld", 0L, natural, hard, 20, upkeep, 23L,
                List.of("block|1|2|3|vector_regnum:mage_light"));
    }
}
