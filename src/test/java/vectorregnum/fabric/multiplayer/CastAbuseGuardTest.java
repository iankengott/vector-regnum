package vectorregnum.fabric.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CastAbuseGuardTest {
    @Test
    void concurrentLimitIsPerPlayerAndReleasesExactly() {
        CastAbuseGuard guard = new CastAbuseGuard();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        for (int index = 0; index < CastAbuseGuard.MAX_ACTIVE_PER_PLAYER; index++) {
            assertTrue(guard.acquire(first, 10).accepted());
        }
        assertFalse(guard.acquire(first, 10).accepted());
        assertTrue(guard.acquire(second, 10).accepted());
        guard.release(first);
        assertTrue(guard.acquire(first, 10).accepted());
        assertEquals(CastAbuseGuard.MAX_ACTIVE_PER_PLAYER, guard.active(first));
    }

    @Test
    void burstLimitResetsAfterBoundedWindow() {
        CastAbuseGuard guard = new CastAbuseGuard();
        UUID actor = UUID.randomUUID();
        for (int index = 0; index < CastAbuseGuard.MAX_STARTS_PER_WINDOW; index++) {
            assertTrue(guard.acquire(actor, 100).accepted());
            guard.release(actor);
        }
        assertFalse(guard.acquire(actor, 100).accepted());
        assertTrue(guard.acquire(actor, 100 + CastAbuseGuard.WINDOW_TICKS).accepted());
    }
}
