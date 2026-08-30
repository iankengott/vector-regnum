package vectorregnum.neoforge.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vectorregnum.core.casting.CastingPolicy;
import vectorregnum.core.casting.ReagentKind;
import vectorregnum.core.casting.ReagentLoadout;

class RitualEscrowStoreTest {
    private static final CastingPolicy POLICY = CastingPolicy.canonical();

    @Test
    void checksummedPlayerEscrowRoundTripsAndRetriesConverge() {
        UUID id = UUID.randomUUID();
        RitualEscrowStore.Escrow escrow = new RitualEscrowStore.Escrow(id, 24.0, 5.0,
                ReagentLoadout.of(Map.of(ReagentKind.MANA, 2, ReagentKind.UPKEEP, 1),
                        1, POLICY));
        RitualEscrowStore store = RitualEscrowStore.empty().put(escrow);
        assertSame(store, store.put(escrow));
        assertEquals(escrow, RitualEscrowStore.decode(store.encode(), POLICY).get(id));
        assertEquals(0, RitualEscrowStore.decode(store.remove(id).encode(), POLICY)
                .escrows().size());
    }

    @Test
    void corruptionAndChangedRetriesFailClosed() {
        UUID id = UUID.randomUUID();
        RitualEscrowStore.Escrow escrow = new RitualEscrowStore.Escrow(id, 24.0, 5.0,
                ReagentLoadout.empty());
        RitualEscrowStore store = RitualEscrowStore.empty().put(escrow);
        assertThrows(IllegalArgumentException.class,
                () -> RitualEscrowStore.decode(store.encode() + "x", POLICY));
        assertThrows(IllegalStateException.class, () -> store.put(
                new RitualEscrowStore.Escrow(id, 25.0, 5.0, ReagentLoadout.empty())));
    }
}
