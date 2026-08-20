package vectorregnum.neoforge.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Persistent cell state plus its bounded, server-authoritative pull tick. */
public final class ManaReservoirBlockEntity extends BlockEntity {
    // A straight 64-link resonant run encounters about five rejected side-neighbors per link.
    // 512 preserves that advertised range while still bounding adversarial branching.
    public static final int MAX_VISITED_BLOCKS = 512;
    public static final int INPUT_PER_PULL = ManaCrystalNodeBlock.MANA_PER_CHARGE;
    private static final String STORED_KEY = "stored_mana";
    private static final String AFFINITY_KEY = "affinity";
    private static final String PENDING_KEY = "pending_input";
    private static final String PENDING_AFFINITY_KEY = "pending_affinity";
    private static final String PENDING_DISTANCE_KEY = "pending_distance";

    private ManaReservoir reservoir = new ManaReservoir(
            ManaReservoir.Tier.CRYSTAL_VIAL, ManaAffinity.ARCANE, 0);
    private int pendingInput;
    private ManaAffinity pendingAffinity = ManaAffinity.ARCANE;
    private int pendingDistance;

    public ManaReservoirBlockEntity(BlockPos pos, BlockState state) {
        super(ProgressionContent.MANA_RESERVOIR_ENTITY.get(), pos, state);
        reservoir = new ManaReservoir(blockTier(state),
                state.getValue(ManaReservoirBlock.AFFINITY), 0);
    }

    public int stored() {
        return reservoir.stored();
    }

    public int capacity() {
        return reservoir.capacity();
    }

    public boolean canRetune() {
        return canRetune(reservoir.stored(), pendingInput);
    }

    static boolean canRetune(int stored, int pending) {
        return stored == 0 && pending == 0;
    }

    public ManaAffinity affinity() {
        return reservoir.affinity();
    }

    public void setAffinity(ManaAffinity affinity) {
        reservoir = new ManaReservoir(reservoir.tier(), affinity, reservoir.stored());
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
            ManaReservoirBlockEntity cell) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ManaTransportRules.ConduitTier conduit = cell.conduitTier(state);
        if (cell.pendingInput == 0) {
            Optional<ManaNetworkSearch.Found<BlockPos>> found = findSource(serverLevel, pos, conduit);
            if (found.isEmpty()) {
                return;
            }
            BlockPos sourcePos = found.get().position();
            BlockState sourceState = serverLevel.getBlockState(sourcePos);
            if (!sourceState.is(ProgressionContent.MANA_CRYSTAL_NODE.get())
                    || sourceState.getValue(ManaCrystalNodeBlock.CHARGE) <= 0) {
                return;
            }
            ManaAffinity sourceAffinity = sourceState.getValue(ManaCrystalNodeBlock.AFFINITY);
            int eventualDelivery = expectedDelivery(INPUT_PER_PULL, sourceAffinity,
                    cell.affinity(), found.get().conduitDistance(), conduit);
            if (eventualDelivery <= 0 || cell.reservoir.space() < eventualDelivery) {
                return;
            }
            cell.pendingInput = INPUT_PER_PULL;
            cell.pendingAffinity = sourceAffinity;
            cell.pendingDistance = found.get().conduitDistance();
            serverLevel.setBlock(sourcePos, sourceState.setValue(ManaCrystalNodeBlock.CHARGE,
                    sourceState.getValue(ManaCrystalNodeBlock.CHARGE) - 1), Block.UPDATE_ALL);
            serverLevel.updateNeighbourForOutputSignal(sourcePos,
                    ProgressionContent.MANA_CRYSTAL_NODE.get());
        }
        ManaReservoir source = new ManaReservoir(ManaReservoir.Tier.CRYSTAL_VIAL,
                cell.pendingAffinity, cell.pendingInput);
        ManaTransportRules.TransferResult result = ManaTransportRules.transfer(source,
                cell.reservoir, cell.pendingInput, cell.pendingDistance, conduit);
        if (result.extracted() <= 0) {
            return;
        }
        cell.reservoir = result.destination();
        cell.pendingInput = result.source().stored();
        serverLevel.updateNeighbourForOutputSignal(pos, state.getBlock());
        cell.setChanged();
    }

    static Optional<ManaNetworkSearch.Found<BlockPos>> findSource(ServerLevel level,
            BlockPos origin, ManaTransportRules.ConduitTier conduitTier) {
        return ManaNetworkSearch.find(origin,
                conduitTier.maximumDistance(), MAX_VISITED_BLOCKS,
                current -> loadedNeighbors(level, current),
                candidate -> isConduit(level.getBlockState(candidate), conduitTier),
                candidate -> level.getBlockState(candidate).is(ProgressionContent.MANA_CRYSTAL_NODE.get()));
    }

    private static boolean isConduit(BlockState state, ManaTransportRules.ConduitTier tier) {
        return state.getBlock() instanceof ManaConduitBlock conduit && conduit.tier() == tier;
    }

    private static Iterable<BlockPos> loadedNeighbors(ServerLevel level, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            BlockPos candidate = pos.relative(direction);
            if (level.hasChunkAt(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    public void reportStatus(ServerPlayer player) {
        Optional<ManaNetworkSearch.Found<BlockPos>> source = findSource(
                player.serverLevel(), worldPosition, conduitTier(getBlockState()));
        player.sendSystemMessage(Component.translatable("message.vector_regnum.cell_status", stored(), capacity(),
                affinity().getSerializedName(), source.isPresent()
                        ? Integer.toString(source.get().conduitDistance())
                        : Component.translatable("message.vector_regnum.cell_no_source")), true);
    }

    public boolean drawTo(ServerPlayer player) {
        int offered = Math.min(stored(), ManaDrawRules.offeredMana(INPUT_PER_PULL, 1.0,
                affinity(), ProgressionContent.manaBridge().requestedAffinity(player)));
        if (offered <= 0 || !ProgressionContent.manaBridge().tryAcceptStoredExact(
                player, offered, affinity(), worldPosition)) {
            player.sendSystemMessage(Component.translatable("message.vector_regnum.no_mana_space"), true);
            return false;
        }
        reservoir = reservoir.withStored(stored() - offered);
        setChanged();
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        ProgressionData.unlock(player, ProgressionUnlock.MANA_STORAGE);
        player.sendSystemMessage(Component.translatable("message.vector_regnum.cell_drew", offered), true);
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ManaAffinity affinity = parseAffinity(tag.getString(AFFINITY_KEY));
        int stored = Math.max(0, Math.min(ManaReservoir.Tier.RESONANT_VAULT.capacity(),
                tag.getInt(STORED_KEY)));
        ManaReservoir.Tier tier = blockTier(getBlockState());
        stored = Math.min(tier.capacity(), stored);
        reservoir = new ManaReservoir(tier, affinity, stored);
        PendingTransfer pending = restorePending(tag.getInt(PENDING_KEY),
                tag.getString(PENDING_AFFINITY_KEY), tag.getInt(PENDING_DISTANCE_KEY),
                conduitTier(getBlockState()));
        pendingInput = pending.input();
        pendingAffinity = pending.affinity();
        pendingDistance = pending.distance();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(STORED_KEY, reservoir.stored());
        tag.putString(AFFINITY_KEY, reservoir.affinity().getSerializedName());
        if (pendingInput > 0) {
            tag.putInt(PENDING_KEY, pendingInput);
            tag.putString(PENDING_AFFINITY_KEY, pendingAffinity.getSerializedName());
            tag.putInt(PENDING_DISTANCE_KEY, pendingDistance);
        }
    }

    private static ManaAffinity parseAffinity(String value) {
        try {
            return ManaAffinity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ManaAffinity.ARCANE;
        }
    }

    static PendingTransfer restorePending(int input, String affinity, int distance,
            ManaTransportRules.ConduitTier conduit) {
        return new PendingTransfer(Math.max(0, Math.min(INPUT_PER_PULL, input)),
                parseAffinity(affinity), Math.max(0, Math.min(conduit.maximumDistance(), distance)));
    }

    record PendingTransfer(int input, ManaAffinity affinity, int distance) {
    }

    static int expectedDelivery(int input, ManaAffinity source, ManaAffinity destination,
            int distance, ManaTransportRules.ConduitTier conduit) {
        ManaReservoir pending = new ManaReservoir(ManaReservoir.Tier.CRYSTAL_VIAL,
                source, input);
        ManaReservoir target = new ManaReservoir(ManaReservoir.Tier.RESONANT_VAULT,
                destination, 0);
        while (pending.stored() > 0) {
            ManaTransportRules.TransferResult result = ManaTransportRules.transfer(pending, target,
                    pending.stored(), distance, conduit);
            if (result.extracted() == 0) {
                break;
            }
            pending = result.source();
            target = result.destination();
        }
        return target.stored();
    }

    private static ManaReservoir.Tier blockTier(BlockState state) {
        return state.getBlock() instanceof ManaReservoirBlock block
                ? block.tier() : ManaReservoir.Tier.CRYSTAL_VIAL;
    }

    private ManaTransportRules.ConduitTier conduitTier(BlockState state) {
        return state.getBlock() instanceof ManaReservoirBlock block
                ? block.conduitTier() : ManaTransportRules.ConduitTier.RAW_CRYSTAL;
    }
}
