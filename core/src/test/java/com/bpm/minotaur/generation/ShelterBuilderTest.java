package com.bpm.minotaur.generation;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Gate;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.progression.ShelterAltar;
import com.bpm.minotaur.rendering.RetroTheme;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * An outpost shelter stamped into a generated surface chunk is a roofed building
 * that stands cold, holds a slot for every station, and can always be walked into
 * from every gate of the chunk (docs/DEsign/Requirements_ Shelter Roads.md, section 3).
 */
public class ShelterBuilderTest {

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
    }

    private static Maze generate(IChunkGenerator gen, long seed) {
        return gen.generateChunk(new GridPoint2(25, 3), 1, 8, Difficulty.MEDIUM, GameMode.ADVANCED,
                RetroTheme.STANDARD_THEME, RetroTheme.STANDARD_THEME,
                null, null, null, null, null, seed, 0);
    }

    @Test
    public void theOutpostIsAColdRoofedBuildingWithEveryStationSlot() {
        Maze maze = generate(new TundraChunkGenerator(), 4L);
        ShelterBuilder.buildOutpost(maze, Biome.TUNDRA, null, null);

        assertEquals(ShelterBuilder.SIZE * ShelterBuilder.SIZE, maze.getHomeTiles().size());
        assertFalse("a new outpost stands cold", maze.isSanctuary());
        assertTrue(maze.getSanctuaryTiles().isEmpty());
        GridPoint2 inside = maze.getHearthTile();
        assertNotNull(inside);
        assertTrue("roofed", maze.isIndoors(inside.x, inside.y));
        assertFalse("not yet a sanctuary", maze.isSanctuaryTile(inside));
        assertNotNull(maze.getAltarTile());
        assertNotNull(maze.getShelterEntry());
        for (ShelterAltar.Station s : new ShelterAltar.Station[]{ShelterAltar.Station.BED, ShelterAltar.Station.CAMPFIRE,
                ShelterAltar.Station.STASH_CHEST, ShelterAltar.Station.CRAFTING_BENCH, ShelterAltar.Station.LANTERN,
                ShelterAltar.Station.TRAINING_DUMMY, ShelterAltar.Station.ARCHIVE_LECTERN}) {
            assertFalse(s + " has no slot", maze.getStationSlots(s).isEmpty());
        }
        assertTrue("outposts carry no portal", maze.getStationSlots(ShelterAltar.Station.PORTAL_FOREST).isEmpty());

        ShelterBuilder.light(maze, null, null);
        assertTrue(maze.isSanctuary());
        assertTrue(maze.isSanctuaryTile(inside));
    }

    @Test
    public void everyGateReachesTheShelterDoorInEverySurfaceBiome() {
        IChunkGenerator[] gens = {new ForestChunkGenerator(), new DesertChunkGenerator(),
                new LakelandsChunkGenerator(), new TundraChunkGenerator(), new BlightChunkGenerator()};
        Biome[] biomes = {Biome.FOREST, Biome.DESERT, Biome.LAKELANDS, Biome.TUNDRA, Biome.BLIGHT};
        for (int g = 0; g < gens.length; g++) {
            for (long seed = 1; seed <= 12; seed++) {
                Maze maze = generate(gens[g], seed);
                ShelterBuilder.buildOutpost(maze, biomes[g], null, null);
                Set<GridPoint2> reach = reachable(maze, maze.getShelterEntry());
                for (Gate gate : maze.getGates().values()) {
                    GridPoint2 gp = new GridPoint2((int) gate.getPosition().x, (int) gate.getPosition().y);
                    assertTrue(biomes[g] + " seed " + seed + ": gate " + gp + " cut off from the shelter",
                            touches(reach, gp));
                }
                GridPoint2 h = maze.getHearthTile();
                assertTrue(biomes[g] + " seed " + seed + ": hearth unreachable from the door",
                        touches(reach, h));
            }
        }
    }

    @Test
    public void theSealSiteStandsReachableInTheMiddleOfTheChunk() {
        for (long seed = 1; seed <= 8; seed++) {
            Maze maze = generate(new DesertChunkGenerator(), seed);
            ShelterBuilder.buildSealSite(maze, com.badlogic.gdx.graphics.Color.WHITE, null);
            GridPoint2 c = new GridPoint2(maze.getWidth() / 2, maze.getHeight() / 2);
            Set<GridPoint2> reach = reachable(maze, new GridPoint2(c.x, c.y - 1));
            for (Gate gate : maze.getGates().values()) {
                GridPoint2 gp = new GridPoint2((int) gate.getPosition().x, (int) gate.getPosition().y);
                assertTrue("seed " + seed + ": gate " + gp + " cut off from the seal site", touches(reach, gp));
            }
        }
    }

    private static boolean touches(Set<GridPoint2> reach, GridPoint2 p) {
        if (reach.contains(p)) return true;
        for (Direction d : Direction.values()) {
            if (reach.contains(new GridPoint2(p.x + (int) d.getVector().x, p.y + (int) d.getVector().y))) return true;
        }
        return false;
    }

    /** Tiles walkable from {@code start} with every door open. */
    private static Set<GridPoint2> reachable(Maze maze, GridPoint2 start) {
        for (Object o : maze.getGameObjects().values()) {
            if (o instanceof Door) ((Door) o).startOpening();
        }
        Set<GridPoint2> seen = new HashSet<>();
        ArrayDeque<GridPoint2> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            GridPoint2 c = queue.poll();
            for (Direction d : Direction.values()) {
                int nx = c.x + (int) d.getVector().x;
                int ny = c.y + (int) d.getVector().y;
                GridPoint2 n = new GridPoint2(nx, ny);
                if (seen.contains(n) || maze.getGates().containsKey(n)) continue;
                if (maze.isWallBlocking(c.x, c.y, d) || !maze.isPassable(nx, ny)) continue;
                seen.add(n);
                queue.add(n);
            }
        }
        return seen;
    }
}
