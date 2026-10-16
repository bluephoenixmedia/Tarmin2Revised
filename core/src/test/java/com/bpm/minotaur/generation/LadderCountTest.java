package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.rendering.RetroTheme;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/**
 * Every surface biome offers 2-3 ways down per chunk (#200), as the maze and
 * forest already did: one ladder per chunk made the descent a hunt.
 */
public class LadderCountTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
    }

    private static void assertTwoOrThreeDown(String biome, IChunkGenerator gen, RetroTheme.Theme theme) {
        for (long seed = 1; seed <= 12; seed++) {
            Maze maze = gen.generateChunk(new GridPoint2(6, 6), 1, 1, Difficulty.MEDIUM, GameMode.ADVANCED,
                    theme, RetroTheme.STANDARD_THEME, null, null, null, null, null, seed, 0);
            int down = 0;
            for (Ladder l : maze.getLadders().values()) {
                if (l.getType() == Ladder.LadderType.DOWN) down++;
            }
            assertTrue(biome + " seed " + seed + " should have 2-3 down ladders, had " + down,
                    down >= 2 && down <= 3);
        }
    }

    @Test
    public void desertChunksOfferTwoOrThreeWaysDown() {
        assertTwoOrThreeDown("desert", new DesertChunkGenerator(), RetroTheme.DESERT_THEME);
    }

    @Test
    public void lakelandsChunksOfferTwoOrThreeWaysDown() {
        assertTwoOrThreeDown("lakelands", new LakelandsChunkGenerator(), RetroTheme.LAKELANDS_THEME);
    }

    @Test
    public void tundraChunksOfferTwoOrThreeWaysDown() {
        assertTwoOrThreeDown("tundra", new TundraChunkGenerator(), RetroTheme.TUNDRA_THEME);
    }

    @Test
    public void blightChunksOfferTwoOrThreeWaysDown() {
        assertTwoOrThreeDown("blight", new BlightChunkGenerator(), RetroTheme.BLIGHT_THEME);
    }
}
