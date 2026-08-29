package vectorregnum.neoforge.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import vectorregnum.core.effect.PersistentEffectContract;
import vectorregnum.core.effect.PersistentEffectLedger;

class PersistentEffectSavedDataTest {
    @Test
    void emptyAndCollapseEmittedLedgersRoundTripAcrossRestart() {
        PersistentEffectSavedData original = new PersistentEffectSavedData();
        CompoundTag encoded = original.save(new CompoundTag(), null);
        PersistentEffectSavedData decoded = PersistentEffectSavedData.load(encoded, null);
        assertTrue(decoded.ledger().entries().isEmpty());

        PersistentEffectContract emitted = PersistentEffectContract.active(
                        UUID.randomUUID(), UUID.randomUUID(), "restart-test",
                        "minecraft:overworld", 10L, 50L, 50L, 20, 4.0, 23L,
                        List.of("b|1|2|3|vector_regnum:mage_light|50"))
                .withState(PersistentEffectContract.State.COLLAPSED)
                .withState(PersistentEffectContract.State.COLLAPSE_EMITTED);
        PersistentEffectSavedData durable = new PersistentEffectSavedData(
                PersistentEffectLedger.EMPTY.register(emitted).ledger());
        CompoundTag durableTag = durable.save(new CompoundTag(), null);
        PersistentEffectSavedData restarted = PersistentEffectSavedData.load(durableTag, null);
        assertEquals(emitted, restarted.ledger().get(emitted.effectId()));
    }

    @Test
    void failedRegistrationSaveRestoresAndPersistsThePreviousLedger() {
        PersistentEffectSavedData data = new PersistentEffectSavedData();
        PersistentEffectContract contract = PersistentEffectContract.active(
                UUID.randomUUID(), UUID.randomUUID(), "save-failure-test",
                "minecraft:overworld", 10L, 50L, 50L, 20, 4.0, 23L,
                List.of("b|1|2|3|vector_regnum:mage_light|50"));
        PersistentEffectLedger candidate = data.ledger().register(contract).ledger();
        AtomicInteger saves = new AtomicInteger();

        assertThrows(IllegalStateException.class, () ->
                PersistentEffectService.persistRegistration(data, candidate, () -> {
                    if (saves.getAndIncrement() == 0) {
                        throw new IllegalStateException("injected first-save failure");
                    }
                }));

        assertTrue(data.ledger().entries().isEmpty(),
                "a failed handoff must not leave a free in-memory contract");
        assertEquals(2, saves.get(),
                "the compensating save must durably restore the prior ledger");
    }
}
