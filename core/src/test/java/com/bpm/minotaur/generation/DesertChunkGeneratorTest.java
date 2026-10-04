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

    @Test
    public void mesasStandTwoAndAHalfWallsTall() {
        Maze desert = generate(3L);
        assertEquals(2.5f, com.bpm.minotaur.rendering.mesh.ChunkMeshBuilder.ceilingHeightFor(desert, 5, 5), 0.001f);
    }

    @Test
    public void anOasisLiesInHalfTheChunksAndAlwaysInASideBasin() {
        int withOasis = 0;
        int chunks = 40;
        for (long seed = 1; seed <= chunks; seed++) {
            Maze maze = generate(seed);
            int water = 0;
            for (int y = 0; y < maze.getHeight(); y++) {
                for (int x = 0; x < maze.getWidth(); x++) {
                    if (!maze.getLiquidManager().hasLiquidAt(x, y)) continue;
                    water++;
                    assertTrue("seed " + seed + ": oasis water at (" + x + "," + y + ") must be on open sand",
                            com.bpm.minotaur.rendering.OpenGround.isOpen(maze, x, y)
                                    || maze.getScenery().containsKey(new GridPoint2(x, y)));
                    assertTrue("seed " + seed + ": the oasis lies away from the central bowl",
                            Math.abs(x - 18) + Math.abs(y - 18) >= 8);
                }
            }
            if (water > 0) withOasis++;
        }
        assertTrue("about half the chunks have an oasis, found " + withOasis + "/" + chunks,
                withOasis >= chunks / 4 && withOasis <= chunks * 3 / 4);
    }

    @Test
    public void desertFaunaRespectsTheSpawnTablesDepthWindows() {
        com.bpm.minotaur.gamedata.spawntables.SpawnTableData table = new com.bpm.minotaur.gamedata.spawntables.SpawnTableData();
        table.monsterSpawnTable = new com.badlogic.gdx.utils.Array<>();
        table.monsterSpawnTable.add(entry("GIANT_SCORPION", 1, 99, 8));
        table.monsterSpawnTable.add(entry("BASILISK", 12, 70, 3));
        table.monsterSpawnTable.add(entry("KOBOLD", 1, 5, 12));

        java.util.List<com.bpm.minotaur.gamedata.monster.Monster.MonsterType> atOne =
                DesertChunkGenerator.faunaFor(table, 1);
        assertEquals(java.util.List.of(com.bpm.minotaur.gamedata.monster.Monster.MonsterType.GIANT_SCORPION), atOne);
        assertTrue("a basilisk turns up deeper",
                DesertChunkGenerator.faunaFor(table, 20).contains(com.bpm.minotaur.gamedata.monster.Monster.MonsterType.BASILISK));
        assertTrue("no table, no fauna", DesertChunkGenerator.faunaFor(null, 1).isEmpty());
    }

    private static com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry entry(String type, int min, int max, int weight) {
        com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry e = new com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry();
        e.type = type;
        e.minLevel = min;
        e.maxLevel = max;
        e.weight = weight;
        return e;
    }
}
