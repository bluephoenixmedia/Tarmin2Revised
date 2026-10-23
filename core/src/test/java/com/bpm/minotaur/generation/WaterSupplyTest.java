package com.bpm.minotaur.generation;

import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

/** Water is common on the surface and scarce deep down (#193). */
public class WaterSupplyTest {

    @Test
    public void strataWaterMostlyTurnsToBloodMuckAndSlush() {
        assertEquals(LiquidType.WATER, WaterSupply.strataWater(0f));
        assertEquals(LiquidType.BLOOD, WaterSupply.strataWater(0.3f));
        assertEquals(LiquidType.FREEZING_SLUSH, WaterSupply.strataWater(0.999f));

        LiquidManager liquids = new LiquidManager();
        int size = LiquidManager.CHUNK_SIZE;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                liquids.setLiquidAt(x, y, LiquidType.WATER);
        WaterSupply.foulStrataWater(liquids, size, size, new Random(7));

        Map<LiquidType, Integer> counts = new EnumMap<>(LiquidType.class);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                counts.merge(liquids.getLiquidAt(x, y), 1, Integer::sum);
        int total = size * size;
        float water = counts.getOrDefault(LiquidType.WATER, 0) / (float) total;
        assertEquals("about a quarter stays water", WaterSupply.STRATA_WATER_KEPT, water, 0.03f);
        assertTrue(counts.getOrDefault(LiquidType.BLOOD, 0) > counts.getOrDefault(LiquidType.WATER, 0));
        assertTrue(counts.getOrDefault(LiquidType.BLACK_MUCK, 0) > 0);
        assertTrue(counts.getOrDefault(LiquidType.FREEZING_SLUSH, 0) > 0);
    }
    private static com.bpm.minotaur.gamedata.Maze dryChunk() {
        // Open floor, no liquid at all: the worst case a tundra start or a fouled stratum leaves.
        return new com.bpm.minotaur.gamedata.Maze(1, new int[24][24]);
    }

    private static int cleanWater(com.bpm.minotaur.gamedata.Maze maze) {
        return WaterSupply.countWater(maze.getLiquidManager(), maze.getWidth(), maze.getHeight());
    }

    @Test
    public void nearHomeThereIsAlwaysCleanWaterOnTheSurfaceAndTheFirstStratum() {
        com.bpm.minotaur.gamedata.Maze tundra = dryChunk();
        WaterSupply.apply(tundra, Biome.TUNDRA, 1, 11L, true);
        assertTrue("a tundra chunk by the Shelter gets a spring", cleanWater(tundra) > 0);

        com.bpm.minotaur.gamedata.Maze stratum = dryChunk();
        WaterSupply.apply(stratum, Biome.MAZE, 2, 12L, true);
        assertTrue("so does the first stratum beneath it, after the fouling", cleanWater(stratum) > 0);
    }

    @Test
    public void farFromHomeOrDeeperTheWaterIsLeftAsItIs() {
        com.bpm.minotaur.gamedata.Maze far = dryChunk();
        WaterSupply.apply(far, Biome.TUNDRA, 1, 13L, false);
        assertEquals(0, cleanWater(far));

        com.bpm.minotaur.gamedata.Maze deep = dryChunk();
        WaterSupply.apply(deep, Biome.MAZE, 3, 14L, true);
        assertEquals("only the first stratum is promised water", 0, cleanWater(deep));
    }

    @Test
    public void onlyChunksWithinTwoOfAShelterAreNearHome() {
        com.badlogic.gdx.math.GridPoint2 home = new com.badlogic.gdx.math.GridPoint2(0, 0);
        assertTrue(WaterSupply.nearShelter(new com.badlogic.gdx.math.GridPoint2(2, -2), home, null));
        assertFalse(WaterSupply.nearShelter(new com.badlogic.gdx.math.GridPoint2(3, 0), home, null));
        assertTrue("the Shelter the soul now wakes in counts too",
                WaterSupply.nearShelter(new com.badlogic.gdx.math.GridPoint2(9, 1), home, new com.badlogic.gdx.math.GridPoint2(8, 0)));
    }
}
