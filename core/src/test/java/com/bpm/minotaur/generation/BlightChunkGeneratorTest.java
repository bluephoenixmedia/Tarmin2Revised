package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.blight.CastleGate;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.RetroTheme;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * The Blight chunk keeps the wilderness contract (every gate reaches every other),
 * and the castle chunk puts a reachable, sealed gate on the side facing the maze.
 */
public class BlightChunkGeneratorTest {

    private static final int SIZE = BlightChunkGenerator.CHUNK_SIZE;
    private static final GridPoint2 CASTLE = new GridPoint2(-31, 38);

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
    }

    private static Maze generate(BlightChunkGenerator gen, GridPoint2 chunk, long seed) {
        return gen.generateChunk(chunk, 1, 12, Difficulty.MEDIUM, GameMode.ADVANCED,
                RetroTheme.BLIGHT_THEME, RetroTheme.STANDARD_THEME,
                null, null, null, null, null, seed, 0);
    }

    private static BlightChunkGenerator generator() {
        BlightChunkGenerator gen = new BlightChunkGenerator();
        gen.setCastleSite(CASTLE);
        return gen;
    }

    @Test
    public void everyGateReachesEveryOtherInOrdinaryChunks() {
        for (long seed = 1; seed <= 25; seed++) {
            BlightChunkGenerator gen = generator();
            Maze maze = generate(gen, new GridPoint2(40, 3), seed);
            assertSame(Biome.BLIGHT, maze.getBiome());
            assertGatesConnected(gen, maze, "seed " + seed);
        }
    }

    @Test
    public void theCastleChunkHasOneReachableSealedGateFacingTheMaze() {
        for (long seed = 1; seed <= 10; seed++) {
            BlightChunkGenerator gen = generator();
            Maze maze = generate(gen, CASTLE, seed);
            assertGatesConnected(gen, maze, "castle seed " + seed);

            Scenery billboard = maze.getScenery().get(new GridPoint2(SIZE / 2, SIZE / 2));
            assertNotNull("the castle billboard stands at the centre", billboard);
            assertEquals(BlightChunkGenerator.CASTLE_BILLBOARD_PROP, billboard.getPropId());

            List<GridPoint2> gates = new ArrayList<>();
            for (Map.Entry<GridPoint2, Scenery> e : maze.getScenery().entrySet()) {
                if (CastleGate.isCastleGate(e.getValue())) gates.add(e.getKey());
            }
            assertEquals("exactly one castle gate", 1, gates.size());
            assertFalse("sealed until Phase 5", CastleGate.isOpen());

            GridPoint2 doorstep = gen.getCastleDoorstep();
            assertTrue("the doorstep is walkable from the edge gates",
                    gen.computeReachableTiles(maze).contains(doorstep));
            assertEquals("the gate is next to its doorstep", 1,
                    Math.abs(doorstep.x - gates.get(0).x) + Math.abs(doorstep.y - gates.get(0).y));

            // (-31, 38): the maze lies south-east, and |y| > |x|, so the gate faces south.
            assertTrue("the gate faces the maze (south)", gates.get(0).y < SIZE / 2);
            assertEquals(doorstep, gen.getInitialPlayerStartPos());
        }
    }

    @Test
    public void rotPoolsAreNecroticSludgeAndStayOffTheAvenues() {
        int pools = 0;
        for (long seed = 1; seed <= 10; seed++) {
            Maze maze = generate(generator(), new GridPoint2(40, 3), seed);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    LiquidType l = maze.getLiquidManager().getLiquidAt(x, y);
                    if (l == null || l == LiquidType.NONE) continue;
                    assertSame(LiquidType.BLACK_MUCK, l);
                    assertFalse("avenues stay dry", Math.abs(x - SIZE / 2) <= 1 || Math.abs(y - SIZE / 2) <= 1);
                    pools++;
                }
            }
        }
        assertTrue("the Marches have rot pools", pools > 0);
    }

    @Test
    public void theFrontFacesTheMazeAlongTheDominantAxis() {
        assertEquals(new GridPoint2(-1, 0), BlightChunkGenerator.frontFacingMaze(new GridPoint2(50, 10)));
        assertEquals(new GridPoint2(1, 0), BlightChunkGenerator.frontFacingMaze(new GridPoint2(-50, 10)));
        assertEquals(new GridPoint2(0, -1), BlightChunkGenerator.frontFacingMaze(new GridPoint2(5, 45)));
        assertEquals(new GridPoint2(0, 1), BlightChunkGenerator.frontFacingMaze(new GridPoint2(5, -45)));
    }

    @Test
    public void theBlightedFlagAndFactionSurviveAChunkSave() {
        Monster m = new Monster(Monster.MonsterType.ZOMBIE, 20, 12);
        m.setBlighted(true);
        m.setFaction(com.bpm.minotaur.gamedata.monster.Faction.TARMIN_LEGION);

        ChunkData.MonsterData data = new ChunkData.MonsterData(m);
        assertTrue(data.blighted);
        assertEquals("TARMIN_LEGION", data.faction);
    }

    @Test
    public void theRosterKeepsBringerOfDeathForTheCastle() {
        List<Monster.MonsterType> all = new ArrayList<>();
        all.addAll(Arrays.asList(BlightChunkGenerator.LEGION));
        all.addAll(Arrays.asList(BlightChunkGenerator.DENIZENS));
        all.addAll(Arrays.asList(BlightChunkGenerator.BLIGHTABLE_FAUNA));
        all.add(BlightChunkGenerator.RARE_ELITE);
        assertFalse(all.contains(Monster.MonsterType.BRINGER_OF_DEATH));
        assertFalse("the Legion patrol leaves the golem to the castle", all.contains(Monster.MonsterType.IRON_GOLEM));
        assertTrue(all.containsAll(Arrays.asList(Monster.MonsterType.SPECTER,
                Monster.MonsterType.SKELETAL_WIZARD, Monster.MonsterType.DEMON_SLIME, Monster.MonsterType.FALL_ANGEL)));
    }

    private static void assertGatesConnected(BlightChunkGenerator gen, Maze maze, String label) {
        Set<GridPoint2> reach = gen.computeReachableTiles(maze);
        int mid = SIZE / 2;
        GridPoint2[] approaches = {
                new GridPoint2(mid, 1), new GridPoint2(mid, SIZE - 2),
                new GridPoint2(1, mid), new GridPoint2(SIZE - 2, mid)
        };
        for (GridPoint2 a : approaches) {
            assertTrue(label + ": gate approach " + a + " unreachable", reach.contains(a));
        }
    }
}
