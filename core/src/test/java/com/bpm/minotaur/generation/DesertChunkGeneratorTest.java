package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.rendering.OpenGround;
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
                    assertTrue("baked rock, was " + path, path.startsWith("images/desert/rock_0"));
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

    @Test
    public void everyDesertSpriteIsBakedPixelArt() {
        for (long seed = 1; seed <= 15; seed++) {
            for (Scenery s : generate(seed).getScenery().values()) {
                if (s.getType() == Scenery.SceneryType.STATUE) continue; // encounter art
                assertTrue("seed " + seed + ": " + s.getType() + " " + s.getPropId() + " draws " + s.getTexturePath(),
                        s.getTexturePath() != null && s.getTexturePath().startsWith("images/desert/"));
            }
        }
    }

    @Test
    public void anOasisIsRingedByPalms() {
        int oases = 0;
        for (long seed = 1; seed <= 30; seed++) {
            Maze maze = generate(seed);
            GridPoint2 water = null;
            for (int y = 0; y < maze.getHeight() && water == null; y++) {
                for (int x = 0; x < maze.getWidth(); x++) {
                    if (maze.getLiquidManager().hasLiquidAt(x, y)) { water = new GridPoint2(x, y); break; }
                }
            }
            if (water == null) continue;
            oases++;
            int palms = 0;
            for (Scenery s : maze.getScenery().values()) {
                if (s.getTexturePath() != null && s.getTexturePath().contains("palm")
                        && s.getPosition().dst(water.x + 1.5f, water.y + 1.5f) < 4f) palms++;
            }
            assertTrue("seed " + seed + ": palms around the oasis, found " + palms, palms >= 2);
        }
        assertTrue(oases > 0);
    }

    @Test
    public void sideBasinsCarryALandmark() {
        int withLandmark = 0;
        for (long seed = 1; seed <= 20; seed++) {
            for (Scenery s : generate(seed).getScenery().values()) {
                String path = s.getTexturePath();
                if (path != null && (path.endsWith("hoodoo.png") || path.endsWith("beast_skull.png") || path.endsWith("arch.png"))) {
                    withLandmark++;
                    break;
                }
            }
        }
        assertTrue("most chunks raise a hoodoo, beast skull or arch in a side basin, " + withLandmark + "/20",
                withLandmark >= 19);
    }

    @Test
    public void theBowlsCentrepieceIsAlwaysPlaced() {
        for (long seed = 0; seed < 30; seed++) {
            int archetype = (int) (Math.abs(seed) % 3);
            if (archetype == 0) continue; // the nomad camp's fire is a catalogue prop
            String wanted = (archetype == 1) ? "ruin_arch.png" : "titan_skull.png";
            boolean found = false;
            for (Scenery s : generate(seed).getScenery().values()) {
                if (s.getTexturePath() != null && s.getTexturePath().endsWith(wanted)) found = true;
            }
            assertTrue("seed " + seed + ": the bowl lost its " + wanted, found);
        }
    }

    @Test
    public void nothingPlacedCutsOffOpenGround() {
        for (long seed = 1; seed <= 20; seed++) {
            Maze maze = generate(seed);
            java.util.Set<GridPoint2> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<GridPoint2> queue = new java.util.ArrayDeque<>();
            GridPoint2 start = new GridPoint2(18, 18);
            seen.add(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                GridPoint2 c = queue.poll();
                int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : steps) {
                    GridPoint2 n = new GridPoint2(c.x + d[0], c.y + d[1]);
                    if (OpenGround.isOpen(maze, n.x, n.y) && seen.add(n)) queue.add(n);
                }
            }
            for (int y = 0; y < maze.getHeight(); y++) {
                for (int x = 0; x < maze.getWidth(); x++) {
                    if (OpenGround.isOpen(maze, x, y) && maze.getGateAt(x, y) == null) {
                        assertTrue("seed " + seed + ": open ground at (" + x + "," + y + ") is cut off",
                                seen.contains(new GridPoint2(x, y)));
                    }
                }
            }
        }
    }

    @Test
    public void groundCoverDecoratesOnlyClearOpenSand() {
        int scatter = 0;
        for (long seed = 1; seed <= 15; seed++) {
            Maze maze = generate(seed);
            for (Scenery s : maze.getBackdropScenery()) {
                scatter++;
                int x = (int) Math.floor(s.getPosition().x);
                int y = (int) Math.floor(s.getPosition().y);
                GridPoint2 tile = new GridPoint2(x, y);
                assertTrue("ground cover at " + tile + " stands on open sand", OpenGround.isOpen(maze, x, y));
                assertFalse("not on a solid prop's tile", maze.getScenery().containsKey(tile));
                assertFalse("not in the water", maze.getLiquidManager().hasLiquidAt(x, y));
                assertFalse("not hiding an item", maze.getItems().containsKey(tile));
                assertFalse("not hiding a ladder", maze.getLadders().containsKey(tile));
                assertNull("not hiding an event", maze.getEventAt(x, y));
                assertTrue("below item height", s.getScale().y <= 0.5f);
                assertTrue("baked art", s.getTexturePath().startsWith("images/desert/"));
            }
        }
        assertTrue("the sand is scattered with ground cover, found " + scatter, scatter > 15 * 20);
    }
}
