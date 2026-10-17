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
}
