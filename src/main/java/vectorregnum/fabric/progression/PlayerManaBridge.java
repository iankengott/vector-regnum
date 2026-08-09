package vectorregnum.fabric.progression;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Narrow integration boundary to the player mana attachment. Exact acceptance keeps
 * the block-state reservoir lossless: a source charge is spent only when all of it fits.
 */
public interface PlayerManaBridge {
    PlayerManaBridge DISCONNECTED = new PlayerManaBridge() {
        @Override
        public ManaAffinity requestedAffinity(ServerPlayerEntity player) {
            return ManaAffinity.ARCANE;
        }

        @Override
        public boolean tryAcceptExact(ServerPlayerEntity player, int mana,
                ManaAffinity sourceAffinity, BlockPos source) {
            return false;
        }

        @Override
        public boolean consumeCapacityShard(ServerPlayerEntity player, int capacityIncrease) {
            return false;
        }

        @Override
        public void attune(ServerPlayerEntity player, BlockPos source, ManaAffinity sourceAffinity) {
        }
    };

    ManaAffinity requestedAffinity(ServerPlayerEntity player);

    boolean tryAcceptExact(ServerPlayerEntity player, int mana,
            ManaAffinity sourceAffinity, BlockPos source);

    boolean consumeCapacityShard(ServerPlayerEntity player, int capacityIncrease);

    /** Called before a successful source draw so an implementation can persist the selected source. */
    default void attune(ServerPlayerEntity player, BlockPos source, ManaAffinity sourceAffinity) {
    }
}
