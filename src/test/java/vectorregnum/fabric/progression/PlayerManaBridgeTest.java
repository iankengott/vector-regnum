package vectorregnum.fabric.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

class PlayerManaBridgeTest {
    @Test
    void storageCreditCanAvoidNaturalSourceAttunement() {
        AtomicInteger sourceCredits = new AtomicInteger();
        AtomicInteger storageCredits = new AtomicInteger();
        PlayerManaBridge bridge = new PlayerManaBridge() {
            @Override
            public ManaAffinity requestedAffinity(ServerPlayerEntity player) {
                return ManaAffinity.ARCANE;
            }

            @Override
            public boolean tryAcceptExact(ServerPlayerEntity player, int mana,
                    ManaAffinity affinity, BlockPos source) {
                sourceCredits.incrementAndGet();
                return true;
            }

            @Override
            public boolean tryAcceptStoredExact(ServerPlayerEntity player, int mana,
                    ManaAffinity affinity, BlockPos storage) {
                storageCredits.incrementAndGet();
                return true;
            }

            @Override
            public boolean consumeCapacityShard(ServerPlayerEntity player, int increase) {
                return false;
            }
        };

        assertTrue(bridge.tryAcceptStoredExact(null, 80, ManaAffinity.ARCANE, BlockPos.ORIGIN));
        assertEquals(0, sourceCredits.get());
        assertEquals(1, storageCredits.get());
    }
}
