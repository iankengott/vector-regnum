package vectorregnum.neoforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManaCrystalGeologyTest {
    @Test
    void plansAreStableAndBounded() {
        for (int chunkX = -40; chunkX <= 40; chunkX++) {
            for (int chunkZ = -40; chunkZ <= 40; chunkZ++) {
                Optional<ManaCrystalGeology.VeinPlan> first =
                        ManaCrystalGeology.planForChunk(8_675_309L, chunkX, chunkZ);
                assertEquals(first, ManaCrystalGeology.planForChunk(8_675_309L, chunkX, chunkZ));
                first.ifPresent(plan -> {
                    assertTrue(plan.localX() >= 2 && plan.localX() <= 13);
                    assertTrue(plan.localZ() >= 2 && plan.localZ() <= 13);
                    assertTrue(plan.y() >= ManaCrystalGeology.DEFAULT.minimumY());
                    assertTrue(plan.y() <= ManaCrystalGeology.DEFAULT.maximumY());
                    assertTrue(plan.size() >= 2 && plan.size() <= 5);
                });
            }
        }
    }

    @Test
    void defaultRarityIsSparseRatherThanGuaranteedPerChunk() {
        long veins = 0;
        for (int chunkX = 0; chunkX < 64; chunkX++) {
            for (int chunkZ = 0; chunkZ < 64; chunkZ++) {
                if (ManaCrystalGeology.planForChunk(42L, chunkX, chunkZ).isPresent()) {
                    veins++;
                }
            }
        }
        assertTrue(veins >= 180 && veins <= 340, "expected roughly one vein per 16 chunks");
    }

    @Test
    void geologyRequiresBuriedConductiveRockAndBoundsDrops() {
        var plan = new ManaCrystalGeology.VeinPlan(8, -48, 8, 4,
                ManaCrystalGeology.CrystalGrade.PRIMAL);
        assertTrue(ManaCrystalGeology.canReplace(ManaCrystalGeology.HostRock.DEEPSLATE,
                -48, false, ManaCrystalGeology.DEFAULT));
        assertFalse(ManaCrystalGeology.canReplace(ManaCrystalGeology.HostRock.OTHER,
                -48, false, ManaCrystalGeology.DEFAULT));
        assertFalse(ManaCrystalGeology.canReplace(ManaCrystalGeology.HostRock.DEEPSLATE,
                -48, true, ManaCrystalGeology.DEFAULT));
        assertEquals(6, ManaCrystalGeology.shardYield(plan, 3));
    }

    @Test
    void defaultPlansKeepEveryCandidateAndBurialNeighborInsideTheirChunk() {
        for (int chunkX = -40; chunkX <= 40; chunkX++) {
            for (int chunkZ = -40; chunkZ <= 40; chunkZ++) {
                ManaCrystalGeology.planForChunk(8_675_309L, chunkX, chunkZ).ifPresent(plan -> {
                    var positions = ManaCrystalGeology.localVeinPositions(plan);
                    assertEquals(plan.size(), positions.size());
                    positions.forEach(position -> {
                        assertTrue(position.x() > 0 && position.x() < 15);
                        assertTrue(position.z() > 0 && position.z() < 15);
                    });
                });
            }
        }
    }

    @Test
    void nonDefaultEdgePlansAreClippedBeforeChunkAccess() {
        var leftEdge = new ManaCrystalGeology.VeinPlan(0, -20, 2, 5,
                ManaCrystalGeology.CrystalGrade.TRACE);
        assertEquals(1, ManaCrystalGeology.localVeinPositions(leftEdge).size());
        assertEquals(1, ManaCrystalGeology.localVeinPositions(leftEdge).getFirst().x());

        var corner = new ManaCrystalGeology.VeinPlan(0, -20, 0, 5,
                ManaCrystalGeology.CrystalGrade.TRACE);
        assertTrue(ManaCrystalGeology.localVeinPositions(corner).isEmpty());
    }
}
