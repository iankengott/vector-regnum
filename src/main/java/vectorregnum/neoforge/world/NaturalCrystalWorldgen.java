package vectorregnum.neoforge.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.tick.OrderedTick;
import net.minecraft.world.tick.TickPriority;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlock;
import vectorregnum.neoforge.progression.ManaSourceGrowthRules;
import vectorregnum.neoforge.progression.ProgressionContent;

/** Places sparse buried crystal veins during chunk generation. */
public final class NaturalCrystalWorldgen {
    private static boolean initialized;

    private NaturalCrystalWorldgen() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerChunkEvents.CHUNK_GENERATE.register(NaturalCrystalWorldgen::generate);
    }

    private static void generate(ServerWorld world, WorldChunk chunk) {
        ManaCrystalGeology.planForChunk(world.getSeed(), chunk.getPos().x, chunk.getPos().z)
                .ifPresent(plan -> placeVein(world, chunk, plan));
    }

    private static void placeVein(ServerWorld world, WorldChunk chunk,
            ManaCrystalGeology.VeinPlan plan) {
        int initialCharge = switch (plan.grade()) {
            case TRACE -> 1;
            case RESONANT -> 3;
            case PRIMAL -> 5;
        };
        int growthStage = switch (plan.grade()) {
            case TRACE -> 0;
            case RESONANT -> 1;
            case PRIMAL -> 2;
        };
        for (ManaCrystalGeology.LocalPosition local
                : ManaCrystalGeology.localVeinPositions(plan)) {
            BlockPos pos = new BlockPos(chunk.getPos().getStartX() + local.x(), local.y(),
                    chunk.getPos().getStartZ() + local.z());
            BlockState hostState = chunk.getBlockState(pos);
            ManaCrystalGeology.HostRock host = ManaCrystalGeology.HostRock.from(hostState.getBlock());
            if (!ManaCrystalGeology.canReplace(host, pos.getY(), exposedToAir(chunk, pos),
                    ManaCrystalGeology.DEFAULT)) {
                continue;
            }
            BlockState crystal = ProgressionContent.MANA_CRYSTAL_NODE.getDefaultState()
                    .with(ManaCrystalNodeBlock.CHARGE, initialCharge)
                    .with(ManaCrystalNodeBlock.GROWTH_STAGE, growthStage)
                    .with(ManaCrystalNodeBlock.NATURAL, true)
                    .with(ManaCrystalNodeBlock.AFFINITY, ManaAffinity.ARCANE);
            chunk.setBlockState(pos, crystal, false);
            chunk.getBlockTickScheduler().scheduleTick(new OrderedTick<>(
                    ProgressionContent.MANA_CRYSTAL_NODE, pos,
                    world.getTime() + ManaSourceGrowthRules.TICKS_PER_DAY,
                    TickPriority.NORMAL, 0));
        }
    }

    private static boolean exposedToAir(WorldChunk chunk, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            if (!chunk.getPos().equals(new net.minecraft.util.math.ChunkPos(adjacentPos))) {
                // Conservatively clip rather than asking the server chunk manager for a neighbor.
                return true;
            }
            BlockState adjacent = chunk.getBlockState(adjacentPos);
            if (adjacent.isAir() || !adjacent.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
