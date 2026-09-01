package vectorregnum.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiContractTest {
    @Test
    void exposesStableOptionalVersionAndDomainOrder() {
        assertEquals(1, VectorRegnumApiV1.VERSION);
        assertTrue(VectorRegnumApiV1.OPTIONAL);
        assertTrue(VectorRegnumApiV1.supports(1));
        assertFalse(VectorRegnumApiV1.supports(0));
        assertFalse(VectorRegnumApiV1.supports(2));
        assertEquals(List.of("origins", "combat", "progression", "world_story",
                "administration", "modpack"), VectorRegnumApiV1.domains());
        assertEquals(VectorRegnumApiV1.REGISTRY, VectorRegnumApiV1.registry());
    }

    @Test
    void playerSnapshotSortsAndCopiesUnlocks() {
        UUID player = UUID.randomUUID();
        List<String> supplied = new ArrayList<>(List.of("vector_regnum:zeta", "vector_regnum:alpha"));
        PlayerMagicSnapshot snapshot = new PlayerMagicSnapshot(player, "frost", "arcane", supplied);

        supplied.add("vector_regnum:later");
        assertEquals(List.of("vector_regnum:alpha", "vector_regnum:zeta"), snapshot.unlockIds());
        assertNotSame(supplied, snapshot.unlockIds());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.unlockIds().add("vector_regnum:blocked"));
        assertEquals("ice", snapshot.naturalElement());
        assertEquals("arcane", snapshot.channel());
    }

    @Test
    void immutableRecordsRejectInvalidValues() {
        CastParameters parameters = new CastParameters(4.0, 8.0, 2.0, 1.0);
        assertThrows(IllegalArgumentException.class,
                () -> new CastParameters(Double.NaN, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new CastModifier(0.49, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new CastContext(UUID.randomUUID(), "vector_regnum:test", "arcane", "bare",
                        parameters, -1L));
        CastContext context = new CastContext(UUID.randomUUID(), "vector_regnum:test", "fire",
                "spellbook", parameters, 7L);
        assertEquals("spellbook", context.castingMethod());
        assertThrows(IllegalArgumentException.class,
                () -> new DisruptionRequest(UUID.randomUUID(), UUID.randomUUID(), "combat:parry",
                        true, true, DisruptionRequest.MAX_TIMING_WINDOW_TICKS + 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new StoryEvent(UUID.randomUUID(), 0L, "vector_regnum:cast", 0L,
                        UUID.randomUUID(), "minecraft:overworld", 0, 64, 0,
                        "vector_regnum:test", "not-an-element", "started"));
    }

    @Test
    void actionResultCodesAreStable() {
        assertEquals("applied", ActionResult.APPLIED.code());
        assertEquals("already_present", ActionResult.ALREADY_PRESENT.code());
        assertEquals("unknown_id", ActionResult.UNKNOWN_ID.code());
        assertEquals("wrong_thread", ActionResult.WRONG_THREAD.code());
        assertEquals("unavailable", ActionResult.UNAVAILABLE.code());
        assertEquals("rejected", ActionResult.REJECTED.code());
    }

    @Test
    void storyEventExposesStableAtLeastOnceDeliveryKey() {
        UUID eventId = UUID.randomUUID();
        StoryEvent event = new StoryEvent(eventId, 3L, "vector_regnum:cast", 90L,
                UUID.randomUUID(), "minecraft:overworld", 1, 2, 3,
                "vector_regnum:test", "fire", "started");

        assertEquals(new StoryEvent.DeliveryKey(eventId, 3L), event.deliveryKey());
    }
}
