package vectorregnum.fabric.multiplayer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpellLeasePolicyTest {
    @Test
    void runningSpellRequiresLiveConnectedOwnerInItsLoadedOriginWorld() {
        assertTrue(SpellLeasePolicy.shouldContinue(true, true, true, true));
        assertFalse(SpellLeasePolicy.shouldContinue(false, true, true, true));
        assertFalse(SpellLeasePolicy.shouldContinue(true, false, true, true));
        assertFalse(SpellLeasePolicy.shouldContinue(true, true, false, true));
        assertFalse(SpellLeasePolicy.shouldContinue(true, true, true, false));
    }
}
