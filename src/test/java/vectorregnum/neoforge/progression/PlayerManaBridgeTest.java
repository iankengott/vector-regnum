package vectorregnum.neoforge.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

class PlayerManaBridgeTest {
    @Test
    void storageCreditCanAvoidNaturalSourceAttunement() {
        AtomicInteger sourceCredits = new AtomicInteger();
        AtomicInteger storageCredits = new AtomicInteger();
        PlayerManaBridge bridge = new PlayerManaBridge() {
            @Override
            public ManaAffinity requestedAffinity(ServerPlayer player) {
                return ManaAffinity.ARCANE;
            }

            @Override
            public boolean tryAcceptExact(ServerPlayer player, int mana,
                    ManaAffinity affinity, BlockPos source) {
                sourceCredits.incrementAndGet();
                return true;
            }

            @Override
            public boolean tryAcceptStoredExact(ServerPlayer player, int mana,
                    ManaAffinity affinity, BlockPos storage) {
                storageCredits.incrementAndGet();
                return true;
            }

            @Override
            public boolean consumeCapacityShard(ServerPlayer player, int increase) {
                return false;
            }
        };

        assertTrue(bridge.tryAcceptStoredExact(null, 80, ManaAffinity.ARCANE, BlockPos.ZERO));
        assertEquals(0, sourceCredits.get());
        assertEquals(1, storageCredits.get());
    }
}
