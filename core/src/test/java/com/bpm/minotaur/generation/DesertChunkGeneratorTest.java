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

public class DesertChunkGeneratorTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
    }

    private Maze generate(long seed) {
        return new DesertChunkGenerator().generateChunk(new GridPoint2(6, 6), 1, 1, Difficulty.MEDIUM,
                GameMode.ADVANCED, RetroTheme.DESERT_THEME, RetroTheme.STANDARD_THEME,
                null, null, null, null, null, seed, 0);
    }

    @Test
    public void cactiAndRocksUseTheBakedPixelArtAtTheirCanvasAspect() {
        int cacti = 0;
        int rocks = 0;
        for (long seed = 1; seed <= 10; seed++) {
            for (Scenery s : generate(seed).getScenery().values()) {
                String path = s.getTexturePath();
                float aspect = s.getScale().x / s.getScale().y;
                if (s.getType() == Scenery.SceneryType.CACTUS) {
                    cacti++;
                    assertTrue("baked cactus, was " + path, path.startsWith("images/desert/cactus_"));
                    float expected = path.endsWith("tall.png") ? 0.9f / 2.4f
                            : path.endsWith("large.png") ? 1.4f / 2.2f : 0.9f / 1.2f;
                    assertEquals(path + " keeps its canvas aspect", expected, aspect, 0.01f);
                } else if (s.getType() == Scenery.SceneryType.SANDSTONE_ROCK) {
                    rocks++;
                    assertEquals("images/desert/rock_01.png", path);
                    assertEquals(1.2f, aspect, 0.01f);
                }
            }
        }
        assertTrue("the desert has cacti and rocks", cacti > 0 && rocks > 0);
    }
}
