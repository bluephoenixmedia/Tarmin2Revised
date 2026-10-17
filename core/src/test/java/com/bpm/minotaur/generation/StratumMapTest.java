package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.Assert.*;

/** Plan T4.1, D36: the strata by depth band, laid out in regions. */
public class StratumMapTest {

    private static Map<Stratum, Integer> census(long seed, int level) {
        Map<Stratum, Integer> count = new EnumMap<>(Stratum.class);
        for (int x = -30; x <= 30; x++) {
            for (int y = -30; y <= 30; y++) {
                count.merge(StratumMap.of(seed, new GridPoint2(x, y), level), 1, Integer::sum);
            }
        }
        return count;
    }

    @Test
    public void eachStratumAppearsInItsBandAndTheMazeStillDoes() {
        assertEquals(EnumSet.of(Stratum.MAZE, Stratum.FUNGAL_FOREST, Stratum.FLOODED_HALLS), census(4L, 2).keySet());
        assertEquals(EnumSet.of(Stratum.MAZE, Stratum.FUNGAL_FOREST, Stratum.FLOODED_HALLS, Stratum.OSSUARY), census(4L, 4).keySet());
        assertEquals(EnumSet.of(Stratum.MAZE, Stratum.OSSUARY, Stratum.MAGMA_DEEPS), census(4L, 7).keySet());
        assertEquals(EnumSet.of(Stratum.MAZE), census(4L, 1).keySet());
    }

    @Test
    public void sameWorldSameStrata() {
        for (int i = 0; i < 50; i++) {
            GridPoint2 c = new GridPoint2(i * 3 - 70, 40 - i * 2);
            assertEquals(StratumMap.of(11L, c, 3), StratumMap.of(11L, c, 3));
        }
    }

    @Test
    public void strataComeInRegionsNotACheckerboard() {
        int same = 0, total = 0;
        for (int x = -20; x < 20; x++) {
            for (int y = -20; y < 20; y++) {
                Stratum here = StratumMap.of(12L, new GridPoint2(x, y), 4);
                if (here == StratumMap.of(12L, new GridPoint2(x + 1, y), 4)) same++;
                total++;
            }
        }
        assertTrue("neighbours agree " + same + "/" + total, same > total * 0.7);
    }

    @Test
    public void townsStandOnlyWhereTheyMay() {
        assertTrue(Stratum.FUNGAL_FOREST.allowsTowns);
        assertFalse(Stratum.MAGMA_DEEPS.allowsTowns);
        assertFalse(Stratum.MAZE.allowsTowns);
    }
}
