package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.math.GridPoint2;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class MapKnowledgeMaskTest {

    /** Every listed chunk explored and fully seen; everything else unknown. */
    private static MapKnowledgeMask.KnowledgeProvider explored(List<GridPoint2> chunks) {
        return new MapKnowledgeMask.KnowledgeProvider() {
            @Override
            public State getChunkState(int cx, int cy) {
                return chunks.contains(new GridPoint2(cx, cy)) ? State.VISITED : State.UNKNOWN;
            }

            @Override
            public boolean isTileSeen(int cx, int cy, int lx, int ly) {
                return true;
            }
        };
    }

    @Test
    public void testFrontierFormula() {
        assertEquals(0f, MapKnowledgeMask.calcFrontier(0f), 0.0001f);
        assertEquals(0f, MapKnowledgeMask.calcFrontier(1f), 0.0001f);
        assertTrue("Frontier formula should peak near middle", MapKnowledgeMask.calcFrontier(0.5f) >= 1.0f);
        assertTrue("Glimpsed chunks should have wisps", MapKnowledgeMask.calcFrontier(0.42f) >= 1.0f);
    }

    @Test
    public void testBoxBlurPreservesRangeAndSmooths() {
        int M = 32;
        float[] grid = new float[M * M];
        for (int y = 14; y < 18; y++) {
            for (int x = 14; x < 18; x++) {
                grid[y * M + x] = 1.0f;
            }
        }
        MapKnowledgeMask.boxBlur(grid, M, 2);
        assertTrue("Center should remain high after blur", grid[15 * M + 15] > 0.3f);
        assertTrue("Neighbor should receive blur spread", grid[12 * M + 12] > 0.0f);
        assertEquals(0.0f, grid[0], 0.0001f);
        for (float v : grid) {
            assertTrue("Values must be within [0, 1]", v >= 0f && v <= 1f);
        }
    }

    @Test
    public void testLayoutPadsAndCentresTheKnownArea() {
        int[] l = MapKnowledgeMask.layout(-2, -1, 3, 1);
        int side = l[2];
        assertEquals(6 + 2 * MapKnowledgeMask.PAD, side);
        assertTrue(l[0] <= -2 - MapKnowledgeMask.PAD + 1 && l[0] + side >= 3 + MapKnowledgeMask.PAD);
        assertTrue(l[1] <= -1 && l[1] + side > 1);
    }

    @Test
    public void testMaskFollowsTheKnownAreaFarFromHome() {
        // Seal sites lie 20-40 chunks out and the castle 40-60: the mask must reach them.
        List<GridPoint2> known = Arrays.asList(new GridPoint2(41, -37), new GridPoint2(42, -37));
        MapKnowledgeMask mask = new MapKnowledgeMask(false);
        mask.rebuild(explored(known), known, 41, -37, 42, -37);

        int R = MapKnowledgeMask.R;
        int px = (41 - mask.getOriginX()) * R + R / 2;
        int py = (-37 - mask.getOriginY()) * R + R / 2;
        assertTrue("explored chunk is clear of fog", mask.valueAt(px, py) > 0.8f);
        int farX = (41 - MapKnowledgeMask.PAD + 1 - mask.getOriginX()) * R;
        assertEquals("ground far from it stays fogged", 0f, mask.valueAt(Math.max(0, farX), py), 0.0001f);
    }

    @Test
    public void testUnseenGroundOfAnExploredChunkIsHazeNotFog() {
        MapKnowledgeMask.KnowledgeProvider p = new MapKnowledgeMask.KnowledgeProvider() {
            @Override
            public State getChunkState(int cx, int cy) {
                return cx == 0 && cy == 0 ? State.VISITED : State.UNKNOWN;
            }

            @Override
            public boolean isTileSeen(int cx, int cy, int lx, int ly) {
                return lx < 4;
            }
        };
        assertEquals(1f, MapKnowledgeMask.blockValue(p, false, 0, 0, 1, 3), 0.0001f);
        assertEquals(MapKnowledgeMask.UNSEEN_EXPLORED, MapKnowledgeMask.blockValue(p, false, 0, 0, 6, 3), 0.0001f);
        assertEquals(0f, MapKnowledgeMask.blockValue(p, false, 5, 5, 1, 1), 0.0001f);
    }
}
