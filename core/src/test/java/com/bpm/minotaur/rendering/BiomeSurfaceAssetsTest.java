package com.bpm.minotaur.rendering;

import com.bpm.minotaur.generation.Biome;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class BiomeSurfaceAssetsTest {

    private static File asset(String path) {
        File f = new File("../assets/" + path);
        return f.exists() ? f : new File("assets/" + path);
    }

    @Test
    public void everySurfaceABiomeNamesIsOnDisk() {
        for (Biome biome : Biome.values()) {
            for (String path : new String[]{biome.getWallTexturePath(), biome.getFloorTexturePath()}) {
                if (path != null) {
                    assertTrue(biome + " names " + path + ", which is missing", asset(path).exists());
                }
            }
        }
    }

    @Test
    public void theDesertHasItsOwnSandAndSandstoneButNoSkyOfItsOwn() {
        assertEquals("images/floor_desert.png", Biome.DESERT.getFloorTexturePath());
        assertEquals("images/desert_cliff.png", Biome.DESERT.getWallTexturePath());
        assertNull("the desert lies under the same burning sky as everywhere else",
                Biome.DESERT.getSkyboxTexturePath());
    }
}
