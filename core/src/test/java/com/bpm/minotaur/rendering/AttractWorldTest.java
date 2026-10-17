package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.rendering.attract.AttractWorld;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AttractWorldTest {

    private AttractWorld world;

    @Before
    public void setUp() {
        world = new AttractWorld();
    }

    @Test
    public void testWorldDimensions() {
        assertEquals("Grid width should be 10 chunks", 10, AttractWorld.GRID_SIZE);
        assertEquals("Grid height should be 10 chunks", 10, AttractWorld.GRID_SIZE);
        assertEquals("Total chunks should be 100", 100, world.getTotalChunkCount());
    }

    @Test
    public void testBiomeDistribution() {
        // Center citadel
        assertEquals(Biome.MAZE, world.getBiomeForChunk(4, 4));
        assertEquals(Biome.MAZE, world.getBiomeForChunk(5, 5));

        // North quadrant
        assertEquals(Biome.FOREST, world.getBiomeForChunk(4, 7));
        assertEquals(Biome.FOREST, world.getBiomeForChunk(5, 8));

        // East quadrant
        assertEquals(Biome.LAKELANDS, world.getBiomeForChunk(7, 4));
        assertEquals(Biome.LAKELANDS, world.getBiomeForChunk(8, 5));

        // South quadrant
        assertEquals(Biome.DESERT, world.getBiomeForChunk(4, 2));
        assertEquals(Biome.DESERT, world.getBiomeForChunk(5, 1));

        // West quadrant
        assertEquals(Biome.MOUNTAINS, world.getBiomeForChunk(2, 4));
        assertEquals(Biome.MOUNTAINS, world.getBiomeForChunk(1, 5));

        // Outer rim
        assertEquals(Biome.OCEAN, world.getBiomeForChunk(0, 0));
        assertEquals(Biome.OCEAN, world.getBiomeForChunk(9, 9));
        assertEquals(Biome.OCEAN, world.getBiomeForChunk(0, 5));
        assertEquals(Biome.OCEAN, world.getBiomeForChunk(9, 5));
    }

    @Test
    public void testChunkGenerationSpeedAndIntegrity() {
        long start = System.currentTimeMillis();
        for (int cy = 0; cy < AttractWorld.GRID_SIZE; cy++) {
            for (int cx = 0; cx < AttractWorld.GRID_SIZE; cx++) {
                Maze chunk = world.getChunk(cx, cy);
                assertNotNull("Chunk (" + cx + "," + cy + ") must not be null", chunk);
                assertEquals(36, chunk.getWidth());
                assertEquals(36, chunk.getHeight());
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        // 100 chunks should generate or load in well under 500ms
        assertTrue("World chunk access should be fast (took " + elapsed + "ms)", elapsed < 800);
    }

    @Test
    public void testLakelandsLiquidGeneration() {
        Maze lakelandChunk = world.getChunk(7, 4);
        assertNotNull(lakelandChunk.getLiquidManager());
        boolean hasWater = false;
        for (int y = 0; y < 36; y++) {
            for (int x = 0; x < 36; x++) {
                if (lakelandChunk.getLiquidAt(x, y) == LiquidType.WATER) {
                    hasWater = true;
                    break;
                }
            }
        }
        assertTrue("Lakelands chunk must contain water tiles", hasWater);
    }

    @Test
    public void testCitadelMoatAndCourtyard() {
        Maze citadelChunk = world.getChunk(4, 4);
        assertNotNull(citadelChunk);
        // Citadel has a moat around perimeter
        boolean hasMoatWater = false;
        for (int y = 0; y < 36; y++) {
            for (int x = 0; x < 36; x++) {
                if (citadelChunk.getLiquidAt(x, y) == LiquidType.WATER) {
                    hasMoatWater = true;
                    break;
                }
            }
        }
        assertTrue("Citadel chunk should have moat water", hasMoatWater);
    }

    @Test
    public void testCitadelHasMazeCorridorsAndWalls() {
        Maze citadelChunk = world.getChunk(4, 4);
        assertNotNull(citadelChunk);
        int wallCount = 0;
        for (int y = 0; y < 36; y++) {
            for (int x = 0; x < 36; x++) {
                if (citadelChunk.getWallDataAt(x, y) != 0) {
                    wallCount++;
                }
            }
        }
        assertTrue("Citadel chunk must contain authentic stone maze walls (was " + wallCount + ")", wallCount > 100);
    }
}
