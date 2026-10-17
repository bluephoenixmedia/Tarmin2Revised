package com.bpm.minotaur.generation.theme;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Walls are edges, not tiles. A themed carve that wrote "1" for a solid tile was writing one west
 * wall, and left the old maze's doors standing on open floor -- the "doors floating in space" a
 * play-test found in a Bridge of Souls. Every theme's carve must leave walls two-sided, solid tiles
 * solid from every side, and no door without a doorway.
 */
public class CarveGeometryTest {

    private static final int CHUNK = 32;
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** A real maze's chunk: a solid border, every interior edge a wall, and doors in the middle. */
    static Maze mazeChunk() {
        int[][] wallData = new int[CHUNK][CHUNK];
        Maze maze = new Maze(3, wallData);
        for (int y = 0; y < CHUNK; y++) {
            for (int x = 0; x < CHUNK; x++) {
                boolean border = x == 0 || y == 0 || x == CHUNK - 1 || y == CHUNK - 1;
                // Interior tiles are cells of a maze: walled on north and east, open south and west,
                // mirrored -- a grid of corridors, as a generator leaves before carving its paths.
                if (border) ChunkThemeDecorator.makeSolid(maze, new java.util.HashSet<>(), x, y);
            }
        }
        for (int y = 2; y < CHUNK - 2; y += 4) {
            for (int x = 1; x < CHUNK - 1; x++) wall(maze, x, y, Direction.NORTH);
        }
        maze.setLiquidManager(new LiquidManager());
        // Doors where the middle of the chunk will be carved away.
        for (int i = 0; i < 4; i++) {
            int x = CHUNK / 2 - 2 + i;
            int y = 14;
            int door = Direction.NORTH.getWallMask() << 1;
            maze.setTile(x, y, maze.getWallDataAt(x, y) | door);
            maze.addGameObject(new Door(), x, y);
        }
        maze.addGate(new com.bpm.minotaur.gamedata.Gate(
                CHUNK / 2, CHUNK - 1, new GridPoint2(1, 0), new GridPoint2(CHUNK / 2, 1)));
        maze.addGate(new com.bpm.minotaur.gamedata.Gate(
                CHUNK / 2, 0, new GridPoint2(-1, 0), new GridPoint2(CHUNK / 2, CHUNK - 2)));
        return maze;
    }

    private static void wall(Maze maze, int x, int y, Direction d) {
        maze.setTile(x, y, maze.getWallDataAt(x, y) | d.getWallMask());
        int nx = x + (int) d.getVector().x;
        int ny = y + (int) d.getVector().y;
        maze.setTile(nx, ny, maze.getWallDataAt(nx, ny) | d.getOpposite().getWallMask());
    }

    private static Maze decorate(ChunkTheme theme, long seed) {
        Maze maze = mazeChunk();
        ChunkThemeDecorator.decorate(maze, theme, seed, null, null, null);
        return maze;
    }

    @Test
    public void everyWallIsTwoSidedAfterEveryCarve() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            Maze maze = decorate(theme, 17L);
            for (int y = 1; y < CHUNK - 1; y++) {
                for (int x = 1; x < CHUNK - 1; x++) {
                    for (Direction d : DIRS) {
                        int nx = x + (int) d.getVector().x;
                        int ny = y + (int) d.getVector().y;
                        boolean here = (maze.getWallDataAt(x, y) & d.getWallMask()) != 0;
                        boolean there = (maze.getWallDataAt(nx, ny) & d.getOpposite().getWallMask()) != 0;
                        assertEquals(theme + ": the wall between (" + x + "," + y + ") and (" + nx + "," + ny
                                + ") stands on one side only -- a floating slab", here, there);
                    }
                }
            }
        }
    }

    @Test
    public void noThemeLeavesADoorWithoutADoorway() {
        for (ChunkTheme theme : ChunkTheme.values()) {
            Maze maze = decorate(theme, 23L);
            for (java.util.Map.Entry<GridPoint2, Object> e : maze.getGameObjects().entrySet()) {
                if (!(e.getValue() instanceof Door)) continue;
                int data = maze.getWallDataAt(e.getKey().x, e.getKey().y);
                assertTrue(theme + ": a door stands at " + e.getKey() + " with no doorway edge",
                        (data & ChunkThemeDecorator.DOOR_BITS) != 0);
            }
        }
    }

    @Test
    public void aSolidTileIsWalledOnEverySide() {
        Maze maze = mazeChunk();
        ChunkThemeDecorator.makeSolid(maze, new java.util.HashSet<>(), 10, 10);
        assertTrue(maze.isWall(10, 10));
        for (Direction d : DIRS) {
            assertTrue("blocked going " + d, maze.isWallBlocking(10, 10, d));
            int nx = 10 + (int) d.getVector().x;
            int ny = 10 + (int) d.getVector().y;
            assertTrue("and blocked coming in from " + d, maze.isWallBlocking(nx, ny, d.getOpposite()));
        }
        ChunkThemeDecorator.makeOpen(maze, new java.util.HashSet<>(), 11, 10);
        assertTrue("an opened tile keeps the wall against its solid neighbour", maze.isWallBlocking(11, 10, Direction.WEST));
    }

    @Test
    public void colosseumPillarsArePillarsNotSlabs() {
        if (!new File("assets/data/props.json").isFile() && !new File("../assets/data/props.json").isFile()) return;
        Maze maze = decorate(ChunkTheme.BLOOD_COLOSSEUM, 41L);
        int pillars = 0;
        for (Scenery s : maze.getScenery().values()) {
            if ("ruined_pillar".equals(s.getPropId())) {
                pillars++;
                assertTrue(s.isImpassable());
            }
        }
        assertTrue("the arena raises its four pillars: " + pillars, pillars >= 4);
    }

    @Test
    public void theCastleKeepIsSolidWallWithADoorwayIntoTheThroneRoom() {
        Maze maze = decorate(ChunkTheme.RUINED_CASTLE, 43L);
        ChunkThemeDecorator.Rect outer = ChunkThemeDecorator.centralRect(maze, 0.22f);
        int inset = Math.max(2, (outer.maxX - outer.minX) / 4);
        int kMinX = outer.minX + inset, kMaxX = outer.maxX - inset, kMinY = outer.minY + inset, kMaxY = outer.maxY - inset;
        for (int x = kMinX; x <= kMaxX; x++) {
            if (x == (kMinX + kMaxX) / 2) continue;
            assertTrue("the keep's south wall is solid at " + x, maze.isWall(x, kMinY));
            assertTrue("the keep's north wall is solid at " + x, maze.isWall(x, kMaxY));
        }
        // Walk in from the courtyard by walls and props alone; monsters move, walls do not.
        java.util.Set<GridPoint2> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<GridPoint2> queue = new java.util.ArrayDeque<>();
        GridPoint2 start = new GridPoint2(outer.minX, outer.minY);
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            GridPoint2 at = queue.poll();
            for (Direction d : DIRS) {
                if (maze.isWallBlocking(at.x, at.y, d)) continue;
                GridPoint2 next = new GridPoint2(at.x + (int) d.getVector().x, at.y + (int) d.getVector().y);
                Scenery prop = maze.getScenery().get(next);
                if (prop != null && prop.isImpassable()) continue;
                if (seen.add(next)) queue.add(next);
            }
        }
        int inside = 0, reached = 0;
        for (int y = kMinY + 1; y < kMaxY; y++) {
            for (int x = kMinX + 1; x < kMaxX; x++) {
                inside++;
                if (seen.contains(new GridPoint2(x, y))) reached++;
            }
        }
        assertTrue("the throne room can be walked into: " + reached + " of " + inside, reached * 2 > inside);
    }
}
