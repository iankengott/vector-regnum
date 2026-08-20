package vectorregnum.neoforge.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import vectorregnum.neoforge.progression.ManaAffinity;
import vectorregnum.neoforge.progression.ManaCrystalNodeBlock;
import vectorregnum.neoforge.progression.ManaSourceGrowthRules;
import vectorregnum.neoforge.progression.ProgressionContent;

/**
 * Registers and places sparse buried crystal veins during NeoForge world generation.
 *
 * <p>The feature deliberately delegates all placement decisions to
 * {@link ManaCrystalGeology}. The placed-feature origin only identifies the
 * owner chunk; its random X/Z/Y values are not used for geology, so a seed
 * produces the same plan as the legacy chunk-generation adapter.</p>
 */
public final class NaturalCrystalWorldgen {
    public static final String MOD_ID = "vector_regnum";

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(BuiltInRegistries.FEATURE, MOD_ID);

    /** The registered configured-feature type used by the data-driven feature. */
    public static final DeferredHolder<Feature<?>, NaturalCrystalFeature>
            NATURAL_CRYSTAL_FEATURE = FEATURES.register("natural_crystal",
                    () -> new NaturalCrystalFeature(NoneFeatureConfiguration.CODEC));

    private NaturalCrystalWorldgen() {
    }

    /** Registers the custom feature on the NeoForge mod event bus. */
    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
    }

    /**
     * The custom feature implementation. NeoForge supplies a world-generation
     * region containing the owner chunk and a safe surrounding halo; this
     * implementation intentionally writes only the interior positions selected
     * by {@link ManaCrystalGeology#localVeinPositions}.
     */
    static final class NaturalCrystalFeature extends Feature<NoneFeatureConfiguration> {
        private NaturalCrystalFeature(Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            WorldGenLevel level = context.level();
            ChunkPos ownerChunk = new ChunkPos(context.origin());
            return ManaCrystalGeology.planForChunk(level.getSeed(), ownerChunk.x, ownerChunk.z)
                    .map(plan -> placeVein(level, ownerChunk, plan))
                    .orElse(false);
        }
    }

    private static boolean placeVein(WorldGenLevel level, ChunkPos ownerChunk,
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
        Block crystalBlock = ProgressionContent.manaCrystalNode();
        boolean placed = false;
        for (ManaCrystalGeology.LocalPosition local
                : ManaCrystalGeology.localVeinPositions(plan)) {
            BlockPos pos = new BlockPos(ownerChunk.getMinBlockX() + local.x(), local.y(),
                    ownerChunk.getMinBlockZ() + local.z());
            BlockState hostState = level.getBlockState(pos);
            ManaCrystalGeology.HostRock host = ManaCrystalGeology.HostRock.from(hostState.getBlock());
            if (!ManaCrystalGeology.canReplace(host, pos.getY(), exposedToAir(level, ownerChunk, pos),
                    ManaCrystalGeology.DEFAULT)) {
                continue;
            }
            BlockState crystal = crystalBlock.defaultBlockState()
                    .setValue(ManaCrystalNodeBlock.CHARGE, initialCharge)
                    .setValue(ManaCrystalNodeBlock.GROWTH_STAGE, growthStage)
                    .setValue(ManaCrystalNodeBlock.NATURAL, true)
                    .setValue(ManaCrystalNodeBlock.AFFINITY, ManaAffinity.ARCANE);
            if (!level.setBlock(pos, crystal, 2)) {
                continue;
            }
            level.scheduleTick(pos, crystalBlock, ManaSourceGrowthRules.TICKS_PER_DAY);
            placed = true;
        }
        return placed;
    }

    private static boolean exposedToAir(WorldGenLevel level, ChunkPos ownerChunk, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            if (!ownerChunk.equals(new ChunkPos(adjacentPos))) {
                // Clip at the owner-chunk edge rather than requesting a neighbor.
                return true;
            }
            BlockState adjacent = level.getBlockState(adjacentPos);
            if (adjacent.isAir() || !adjacent.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
