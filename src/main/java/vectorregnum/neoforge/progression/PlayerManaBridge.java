package vectorregnum.neoforge.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Narrow integration boundary to the player mana attachment. Exact acceptance keeps
 * the block-state reservoir lossless: a source charge is spent only when all of it fits.
 */
public interface PlayerManaBridge {
    PlayerManaBridge DISCONNECTED = new PlayerManaBridge() {
        @Override
        public ManaAffinity requestedAffinity(ServerPlayer player) {
            return ManaAffinity.ARCANE;
        }

        @Override
        public boolean tryAcceptExact(ServerPlayer player, int mana,
                ManaAffinity sourceAffinity, BlockPos source) {
            return false;
        }

        @Override
        public boolean consumeCapacityShard(ServerPlayer player, int capacityIncrease) {
            return false;
        }

        @Override
        public boolean tryAcceptStoredExact(ServerPlayer player, int mana,
                ManaAffinity affinity, BlockPos storage) {
            return false;
        }

        @Override
        public void attune(ServerPlayer player, BlockPos source, ManaAffinity sourceAffinity) {
        }
    };

    ManaAffinity requestedAffinity(ServerPlayer player);

    boolean tryAcceptExact(ServerPlayer player, int mana,
            ManaAffinity sourceAffinity, BlockPos source);

    /** Credits finite block storage without replacing the player's attuned natural source. */
    default boolean tryAcceptStoredExact(ServerPlayer player, int mana,
            ManaAffinity affinity, BlockPos storage) {
        return tryAcceptExact(player, mana, affinity, storage);
    }

    boolean consumeCapacityShard(ServerPlayer player, int capacityIncrease);

    /** Called before a successful source draw so an implementation can persist the selected source. */
    default void attune(ServerPlayer player, BlockPos source, ManaAffinity sourceAffinity) {
    }
}
