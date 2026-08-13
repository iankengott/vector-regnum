package vectorregnum.fabric.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

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
        super(ProgressionContent.MANA_RESERVOIR_ENTITY, pos, state);
        reservoir = new ManaReservoir(blockTier(state),
                state.get(ManaReservoirBlock.AFFINITY), 0);
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
        markDirty();
    }

    public static void tick(World world, BlockPos pos, BlockState state,
            ManaReservoirBlockEntity cell) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        ManaTransportRules.ConduitTier conduit = cell.conduitTier(state);
        if (cell.pendingInput == 0) {
            Optional<ManaNetworkSearch.Found<BlockPos>> found = findSource(serverWorld, pos, conduit);
            if (found.isEmpty()) {
                return;
            }
            BlockPos sourcePos = found.get().position();
            BlockState sourceState = serverWorld.getBlockState(sourcePos);
            if (!sourceState.isOf(ProgressionContent.MANA_CRYSTAL_NODE)
                    || sourceState.get(ManaCrystalNodeBlock.CHARGE) <= 0) {
                return;
            }
            ManaAffinity sourceAffinity = sourceState.get(ManaCrystalNodeBlock.AFFINITY);
            int eventualDelivery = expectedDelivery(INPUT_PER_PULL, sourceAffinity,
                    cell.affinity(), found.get().conduitDistance(), conduit);
            if (eventualDelivery <= 0 || cell.reservoir.space() < eventualDelivery) {
                return;
            }
            cell.pendingInput = INPUT_PER_PULL;
            cell.pendingAffinity = sourceAffinity;
            cell.pendingDistance = found.get().conduitDistance();
            serverWorld.setBlockState(sourcePos, sourceState.with(ManaCrystalNodeBlock.CHARGE,
                    sourceState.get(ManaCrystalNodeBlock.CHARGE) - 1), Block.NOTIFY_ALL);
            serverWorld.updateComparators(sourcePos, ProgressionContent.MANA_CRYSTAL_NODE);
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
        serverWorld.updateComparators(pos, state.getBlock());
        cell.markDirty();
    }

    static Optional<ManaNetworkSearch.Found<BlockPos>> findSource(ServerWorld world,
            BlockPos origin, ManaTransportRules.ConduitTier conduitTier) {
        return ManaNetworkSearch.find(origin,
                conduitTier.maximumDistance(), MAX_VISITED_BLOCKS,
                current -> loadedNeighbors(world, current),
                candidate -> isConduit(world.getBlockState(candidate), conduitTier),
                candidate -> world.getBlockState(candidate).isOf(ProgressionContent.MANA_CRYSTAL_NODE));
    }

    private static boolean isConduit(BlockState state, ManaTransportRules.ConduitTier tier) {
        return state.getBlock() instanceof ManaConduitBlock conduit && conduit.tier() == tier;
    }

    private static Iterable<BlockPos> loadedNeighbors(ServerWorld world, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            BlockPos candidate = pos.offset(direction);
            if (world.isChunkLoaded(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    public void reportStatus(ServerPlayerEntity player) {
        Optional<ManaNetworkSearch.Found<BlockPos>> source = findSource(player.getServerWorld(), pos,
                conduitTier(getCachedState()));
        player.sendMessage(Text.translatable("message.vector_regnum.cell_status", stored(), capacity(),
                affinity().asString(), source.isPresent()
                        ? Integer.toString(source.get().conduitDistance())
                        : Text.translatable("message.vector_regnum.cell_no_source")), true);
    }

    public boolean drawTo(ServerPlayerEntity player) {
        int offered = Math.min(stored(), ManaDrawRules.offeredMana(INPUT_PER_PULL, 1.0,
                affinity(), ProgressionContent.manaBridge().requestedAffinity(player)));
        if (offered <= 0 || !ProgressionContent.manaBridge().tryAcceptStoredExact(
                player, offered, affinity(), pos)) {
            player.sendMessage(Text.translatable("message.vector_regnum.no_mana_space"), true);
            return false;
        }
        reservoir = reservoir.withStored(stored() - offered);
        markDirty();
        if (world != null) {
            world.updateComparators(pos, getCachedState().getBlock());
        }
        ProgressionData.unlock(player, ProgressionUnlock.MANA_STORAGE);
        player.sendMessage(Text.translatable("message.vector_regnum.cell_drew", offered), true);
        return true;
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        ManaAffinity affinity = parseAffinity(nbt.getString(AFFINITY_KEY));
        int stored = Math.max(0, Math.min(ManaReservoir.Tier.RESONANT_VAULT.capacity(),
                nbt.getInt(STORED_KEY)));
        ManaReservoir.Tier tier = blockTier(getCachedState());
        stored = Math.min(tier.capacity(), stored);
        reservoir = new ManaReservoir(tier, affinity, stored);
        PendingTransfer pending = restorePending(nbt.getInt(PENDING_KEY),
                nbt.getString(PENDING_AFFINITY_KEY), nbt.getInt(PENDING_DISTANCE_KEY),
                conduitTier(getCachedState()));
        pendingInput = pending.input();
        pendingAffinity = pending.affinity();
        pendingDistance = pending.distance();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt(STORED_KEY, reservoir.stored());
        nbt.putString(AFFINITY_KEY, reservoir.affinity().asString());
        if (pendingInput > 0) {
            nbt.putInt(PENDING_KEY, pendingInput);
            nbt.putString(PENDING_AFFINITY_KEY, pendingAffinity.asString());
            nbt.putInt(PENDING_DISTANCE_KEY, pendingDistance);
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
