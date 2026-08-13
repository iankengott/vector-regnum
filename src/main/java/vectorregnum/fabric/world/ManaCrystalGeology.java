package vectorregnum.fabric.world;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic, loader-independent geology rules for natural mana crystals.
 * A future Fabric placed-feature adapter can consume the plan without putting
 * random or balance decisions in the world-generation callback.
 */
public final class ManaCrystalGeology {
    public static final Config DEFAULT = new Config(16, -56, 20, 2, 5);

    private static final long CHUNK_X_SALT = 0x632BE59BD9B4E019L;
    private static final long CHUNK_Z_SALT = 0x9E3779B97F4A7C15L;

    private ManaCrystalGeology() {
    }

    public static Optional<VeinPlan> planForChunk(long worldSeed, int chunkX, int chunkZ) {
        return planForChunk(worldSeed, chunkX, chunkZ, DEFAULT);
    }

    public static Optional<VeinPlan> planForChunk(
            long worldSeed, int chunkX, int chunkZ, Config config) {
        long chunkSeed = mix64(worldSeed ^ (CHUNK_X_SALT * chunkX) ^ (CHUNK_Z_SALT * chunkZ));
        if (bounded(chunkSeed, config.oneInChunks()) != 0) {
            return Optional.empty();
        }

        int localX = 2 + bounded(mix64(chunkSeed + 1), 12);
        int localZ = 2 + bounded(mix64(chunkSeed + 2), 12);
        int depthSample = bounded(mix64(chunkSeed + 3), 65_536);
        long squaredSample = (long) depthSample * depthSample;
        int ySpan = config.maximumY() - config.minimumY() + 1;
        int y = config.minimumY() + (int) (squaredSample * ySpan / (65_536L * 65_536L));
        y = Math.min(y, config.maximumY());

        int gradeRoll = bounded(mix64(chunkSeed + 4), 100);
        CrystalGrade grade;
        if (y <= -40 && gradeRoll < 5) {
            grade = CrystalGrade.PRIMAL;
        } else if (y <= -16 && gradeRoll < 35) {
            grade = CrystalGrade.RESONANT;
        } else {
            grade = CrystalGrade.TRACE;
        }

        int sizeRange = config.maximumVeinSize() - config.minimumVeinSize() + 1;
        int size = config.minimumVeinSize() + bounded(mix64(chunkSeed + 5), sizeRange);
        return Optional.of(new VeinPlan(localX, y, localZ, size, grade));
    }

    /** Crystals must remain buried in a mana-conductive host rock. */
    public static boolean canReplace(HostRock host, int y, boolean exposedToAir, Config config) {
        return host.conductivity() > 0
                && y >= config.minimumY()
                && y <= config.maximumY()
                && !exposedToAir;
    }

    /** A bounded harvest quote shared by loot-table and interaction adapters. */
    public static int shardYield(VeinPlan plan, int fortuneLevel) {
        if (fortuneLevel < 0 || fortuneLevel > 3) {
            throw new IllegalArgumentException("Fortune level must be between zero and three");
        }
        int gradeBonus = switch (plan.grade()) {
            case TRACE -> 0;
            case RESONANT -> 1;
            case PRIMAL -> 2;
        };
        return Math.min(8, 1 + gradeBonus + fortuneLevel);
    }

    /**
     * Returns only candidates whose full six-neighbor burial check is inside their owner chunk.
     * This keeps generation callbacks from requesting a neighboring chunk at chunk edges.
     */
    public static List<LocalPosition> localVeinPositions(VeinPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Vein plan is required");
        }
        List<LocalPosition> positions = new ArrayList<>(plan.size());
        for (int index = 0; index < plan.size(); index++) {
            int x = plan.localX() + (index % 3) - 1;
            int z = plan.localZ() + (index / 3) - 1;
            if (isInteriorLocal(x, z)) {
                positions.add(new LocalPosition(x, plan.y(), z));
            }
        }
        return List.copyOf(positions);
    }

    private static boolean isInteriorLocal(int x, int z) {
        return x > 0 && x < 15 && z > 0 && z < 15;
    }

    private static int bounded(long value, int bound) {
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public enum HostRock {
        STONE(1),
        DEEPSLATE(3),
        TUFF(2),
        CALCITE(2),
        OTHER(0);

        private final int conductivity;

        HostRock(int conductivity) {
            this.conductivity = conductivity;
        }

        public int conductivity() {
            return conductivity;
        }

        public static HostRock from(Block block) {
            if (block == Blocks.STONE) return STONE;
            if (block == Blocks.DEEPSLATE) return DEEPSLATE;
            if (block == Blocks.TUFF) return TUFF;
            if (block == Blocks.CALCITE) return CALCITE;
            return OTHER;
        }
    }

    public enum CrystalGrade {
        TRACE,
        RESONANT,
        PRIMAL
    }

    public record Config(int oneInChunks, int minimumY, int maximumY,
                         int minimumVeinSize, int maximumVeinSize) {
        public Config {
            if (oneInChunks <= 0 || minimumY > maximumY
                    || minimumVeinSize <= 0 || minimumVeinSize > maximumVeinSize) {
                throw new IllegalArgumentException("Invalid crystal geology configuration");
            }
        }
    }

    public record VeinPlan(int localX, int y, int localZ, int size, CrystalGrade grade) {
        public VeinPlan {
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15 || size <= 0) {
                throw new IllegalArgumentException("Invalid chunk-local vein plan");
            }
            if (grade == null) {
                throw new IllegalArgumentException("Crystal grade is required");
            }
        }
    }

    public record LocalPosition(int x, int y, int z) {
        public LocalPosition {
            if (!isInteriorLocal(x, z)) {
                throw new IllegalArgumentException("Local crystal position requires a chunk-local halo");
            }
        }
    }
}
