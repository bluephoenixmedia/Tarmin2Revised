package com.bpm.minotaur.screens.map;

import org.junit.Test;

import static org.junit.Assert.*;

public class MapKnowledgeMaskTest {

    @Test
    public void testDimensions() {
        assertEquals(48, MapKnowledgeMask.N);
        assertEquals(8, MapKnowledgeMask.R);
        assertEquals(24, MapKnowledgeMask.PAD);
        assertEquals(384, MapKnowledgeMask.M);
    }

    @Test
    public void testFrontierFormula() {
        assertEquals(0f, MapKnowledgeMask.calcFrontier(0f), 0.0001f);
        assertEquals(0f, MapKnowledgeMask.calcFrontier(1f), 0.0001f);

        // Peaks near 0.5
        float peak = MapKnowledgeMask.calcFrontier(0.5f);
        assertTrue("Frontier formula should peak near middle", peak >= 1.0f);

        // Glimpsed chunks (v = 0.42) should have strong wisps
        float glimpsed = MapKnowledgeMask.calcFrontier(0.42f);
        assertTrue("Glimpsed chunks should have wisps", glimpsed >= 1.0f);
    }

    @Test
    public void testBoxBlurPreservesRangeAndSmooths() {
        int M = 32;
        float[] grid = new float[M * M];
        // Center 4x4 block set to 1.0
        for (int y = 14; y < 18; y++) {
            for (int x = 14; x < 18; x++) {
                grid[y * M + x] = 1.0f;
            }
        }

        MapKnowledgeMask.boxBlur(grid, M, 2);

        // Center should still be high
        assertTrue("Center should remain high after blur", grid[15 * M + 15] > 0.3f);
        // Adjacent tiles should have smoothed values > 0
        assertTrue("Neighbor should receive blur spread", grid[12 * M + 12] > 0.0f);
        // Far corners should remain 0
        assertEquals(0.0f, grid[0], 0.0001f);

        for (float v : grid) {
            assertTrue("Values must be within [0, 1]", v >= 0f && v <= 1f);
        }
    }

    @Test
    public void testMaskGridCoordinates() {
        // Chunk (0, 0) starts at tx=0, ty=0 => px = PAD * R = 24 * 8 = 192, py = 192
        int px = MapKnowledgeMask.PAD * MapKnowledgeMask.R;
        int py = MapKnowledgeMask.PAD * MapKnowledgeMask.R;

        int cx = MapKnowledgeMask.chunkX(px);
        int cy = MapKnowledgeMask.chunkY(py);
        assertEquals(0, cx);
        assertEquals(0, cy);

        int lx = MapKnowledgeMask.localTileX(px);
        int ly = MapKnowledgeMask.localTileY(py);
        assertEquals(0, lx);
        assertEquals(0, ly);

        // Negative chunk coordinates should work naturally
        assertEquals(-1, MapKnowledgeMask.chunkX(px - MapKnowledgeMask.R));
        assertEquals(-2, MapKnowledgeMask.chunkY(py - 2 * MapKnowledgeMask.R));
        assertEquals(-MapKnowledgeMask.HALF_CHUNKS, MapKnowledgeMask.chunkX(0));
    }
}
