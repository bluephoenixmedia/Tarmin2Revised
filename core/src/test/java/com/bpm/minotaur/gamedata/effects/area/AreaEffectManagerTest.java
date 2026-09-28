package com.bpm.minotaur.gamedata.effects.area;

import com.bpm.minotaur.gamedata.Maze;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The tile grid behind Fog Cloud: where a cloud spreads, how long it lasts, what disperses it,
 * and what it stops you seeing.
 *
 * <p>All of this is deliberately free of libGDX rendering so it can be tested headless. A
 * {@link Maze} built from a plain {@code int[][]} is all the world it needs.
 */
public class AreaEffectManagerTest {

    /** An open room with no interior walls. */
    private static Maze openMaze(int size) {
        return new Maze(1, new int[size][size]);
    }

    private static AreaEffectManager manager(int size) {
        return new AreaEffectManager(size, size);
    }

    // --- Spread ----------------------------------------------------------

    @Test
    public void aCloudFillsEveryTileWithinItsRadius() {
        Maze maze = openMaze(11);
        AreaEffectManager m = manager(11);

        m.apply(maze, 5, 5, AreaEffectType.OBSCURING, 2, 10);

        // Radius 2 by step count from the origin: a diamond, not a square.
        assertTrue(m.isActive(5, 5));
        assertTrue(m.isActive(5, 7));
        assertTrue(m.isActive(3, 5));
        assertTrue(m.isActive(6, 6));
        assertFalse("a tile three steps away is outside a radius of two", m.isActive(5, 8));
        assertFalse(m.isActive(7, 7));
    }

    @Test
    public void wallsStopTheSpreadRatherThanBeingFilled() {
        Maze maze = openMaze(11);
        // A solid wall down column 6, so a cloud at x=5 cannot reach x=7.
        for (int y = 0; y < 11; y++) {
            maze.setTile(6, y, 1);
        }
        AreaEffectManager m = manager(11);

        m.apply(maze, 5, 5, AreaEffectType.OBSCURING, 2, 10);

        assertTrue(m.isActive(5, 5));
        assertTrue("the cloud still spreads along the wall", m.isActive(5, 7));
        assertFalse("fog must not occupy solid stone", m.isActive(6, 5));
        assertFalse("fog must not pass through solid stone", m.isActive(7, 5));
    }

    @Test
    public void theCloudSpreadsAroundACorner() {
        // A cloud is not a circle stamped on the map: it flows. Cast into the mouth of an
        // L-shaped passage and it should turn the corner, which a radius check would not do.
        int[][] walls = new int[7][7];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                walls[y][x] = 1;
            }
        }
        // Carve an L: (1,1)-(3,1) then (3,1)-(3,3).
        walls[1][1] = 0;
        walls[1][2] = 0;
        walls[1][3] = 0;
        walls[2][3] = 0;
        walls[3][3] = 0;
        Maze maze = new Maze(1, walls);
        AreaEffectManager m = manager(7);

        m.apply(maze, 1, 1, AreaEffectType.OBSCURING, 4, 10);

        assertTrue(m.isActive(3, 1));
        assertTrue("the cloud turns the corner", m.isActive(3, 3));
        assertFalse(m.isActive(2, 2));
    }

    @Test
    public void aCloudCastInsideAWallGoesNowhere() {
        Maze maze = openMaze(9);
        maze.setTile(4, 4, 1);
        AreaEffectManager m = manager(9);

        int filled = m.apply(maze, 4, 4, AreaEffectType.OBSCURING, 2, 10);

        assertEquals(0, filled);
        assertEquals(0, m.activeTileCount());
    }

    // --- Duration --------------------------------------------------------

    @Test
    public void aCloudCountsDownOneTurnAtATimeAndThenIsGone() {
        Maze maze = openMaze(9);
        AreaEffectManager m = manager(9);
        m.apply(maze, 4, 4, AreaEffectType.OBSCURING, 1, 3);

        assertEquals(3, m.turnsRemainingAt(4, 4));
        m.tick(maze, 0f);
        assertEquals(2, m.turnsRemainingAt(4, 4));
        m.tick(maze, 0f);
        m.tick(maze, 0f);
        assertFalse(m.isActive(4, 4));
        assertEquals(0, m.activeTileCount());
    }

    @Test
    public void recastingRefreshesTheOverlapAndUnionsTheFootprint() {
        Maze maze = openMaze(15);
        AreaEffectManager m = manager(15);

        m.apply(maze, 5, 5, AreaEffectType.OBSCURING, 1, 10);
        m.tick(maze, 0f);
        m.tick(maze, 0f);
        assertEquals(8, m.turnsRemainingAt(5, 5));

        // A second cast one tile over: the shared tile goes back to full, and the new tiles
        // join the cloud rather than replacing it.
        m.apply(maze, 6, 5, AreaEffectType.OBSCURING, 1, 10);

        assertEquals("an overlapped tile is refreshed, not extended", 10, m.turnsRemainingAt(5, 5));
        assertEquals(10, m.turnsRemainingAt(7, 5));
        assertTrue("the first cloud's outlying tiles survive", m.isActive(4, 5));
    }

    @Test
    public void refreshingNeverShortensATileThatHadLonger() {
        Maze maze = openMaze(9);
        AreaEffectManager m = manager(9);
        m.apply(maze, 4, 4, AreaEffectType.OBSCURING, 1, 10);
        m.apply(maze, 4, 4, AreaEffectType.OBSCURING, 1, 4);
        assertEquals(10, m.turnsRemainingAt(4, 4));
    }

    // --- Wind ------------------------------------------------------------

    @Test
    public void stillAirLeavesTheCloudAlone() {
        Maze maze = openMaze(9);
        AreaEffectManager m = manager(9);
        m.apply(maze, 4, 4, AreaEffectType.OBSCURING, 1, 10);
        m.tick(maze, AreaEffectManager.DISPERSING_WIND_SPEED - 0.1f);
        assertEquals("below the threshold the wind does nothing extra", 9, m.turnsRemainingAt(4, 4));
    }

    @Test
    public void aStormTearsTheCloudApartInAFewTurns() {
        Maze maze = openMaze(9);
        AreaEffectManager m = manager(9);
        m.apply(maze, 4, 4, AreaEffectType.OBSCURING, 1, 10);

        // Halving on top of the ordinary decrement: 10 -> 4 -> 1 -> gone.
        m.tick(maze, AreaEffectManager.DISPERSING_WIND_SPEED);
        assertEquals(4, m.turnsRemainingAt(4, 4));
        m.tick(maze, AreaEffectManager.DISPERSING_WIND_SPEED);
        assertEquals(1, m.turnsRemainingAt(4, 4));
        m.tick(maze, AreaEffectManager.DISPERSING_WIND_SPEED);
        assertFalse(m.isActive(4, 4));
    }

    @Test
    public void shelteredTilesHoldTheirFogEvenInAGale() {
        // Wind is tested per tile, not against wherever the player happens to be standing:
        // a cloud indoors must not be torn apart because the caster walked outside, and an
        // outdoor cloud must keep dispersing after they step in.
        Maze maze = openMaze(9);
        maze.setHomeTiles(java.util.Collections.singletonList(new com.badlogic.gdx.math.GridPoint2(2, 2)));
        AreaEffectManager m = manager(9);
        m.apply(maze, 2, 2, AreaEffectType.OBSCURING, 0, 10);
        m.apply(maze, 6, 6, AreaEffectType.OBSCURING, 0, 10);

        m.tick(maze, AreaEffectManager.DISPERSING_WIND_SPEED);

        assertEquals("a sheltered tile only takes the ordinary decrement", 9, m.turnsRemainingAt(2, 2));
        assertEquals("an exposed tile is halved as well", 4, m.turnsRemainingAt(6, 6));
    }

    // --- Sight -----------------------------------------------------------

    @Test
    public void aClearLineIsNotObscured() {
        AreaEffectManager m = manager(15);
        assertFalse(m.blocksSight(2, 2, 12, 2));
    }

    @Test
    public void fogAcrossTheLineBlocksIt() {
        Maze maze = openMaze(15);
        AreaEffectManager m = manager(15);
        m.apply(maze, 7, 2, AreaEffectType.OBSCURING, 1, 10);

        assertTrue("the cloud sits between the two points", m.blocksSight(2, 2, 12, 2));
    }

    @Test
    public void fogBesideTheLineDoesNotBlockIt() {
        Maze maze = openMaze(15);
        AreaEffectManager m = manager(15);
        m.apply(maze, 7, 9, AreaEffectType.OBSCURING, 1, 10);

        assertFalse(m.blocksSight(2, 2, 12, 2));
    }

    @Test
    public void standingInFogBlindsYouEvenLookingIntoClearAir() {
        Maze maze = openMaze(15);
        AreaEffectManager m = manager(15);
        m.apply(maze, 2, 2, AreaEffectType.OBSCURING, 1, 10);

        // The viewer's own tile counts: fog you are inside is fog you cannot see out of.
        assertTrue(m.blocksSight(2, 2, 12, 2));
        // And symmetrically, from the far end looking in.
        assertTrue(m.blocksSight(12, 2, 2, 2));
    }

    @Test
    public void aLineToYourOwnTileIsNeverBlocked() {
        Maze maze = openMaze(15);
        AreaEffectManager m = manager(15);
        m.apply(maze, 5, 5, AreaEffectType.OBSCURING, 1, 10);
        assertFalse("a point can always see itself", m.blocksSight(5, 5, 5, 5));
    }

    @Test
    public void adjacentTilesSeeEachOtherThroughFog() {
        // Q19: a monster standing next to you in the fog has not lost you. Obscurement starts
        // at two tiles; otherwise fog would make you untouchable at point-blank range.
        Maze maze = openMaze(15);
        AreaEffectManager m = manager(15);
        m.apply(maze, 5, 5, AreaEffectType.OBSCURING, 2, 10);

        assertFalse(m.blocksSight(5, 5, 6, 5));
        assertFalse(m.blocksSight(5, 5, 6, 6));
        assertTrue("two tiles out, the fog counts again", m.blocksSight(5, 5, 7, 5));
    }

    // --- Housekeeping ----------------------------------------------------

    @Test
    public void outOfBoundsQueriesAreSafeAndFalse() {
        AreaEffectManager m = manager(9);
        assertFalse(m.isActive(-1, 4));
        assertFalse(m.isActive(4, 99));
        assertEquals(0, m.turnsRemainingAt(-5, -5));
        assertFalse(m.blocksSight(-3, -3, 40, 40));
    }

    @Test
    public void tickingAnEmptyGridIsFree() {
        AreaEffectManager m = manager(9);
        m.tick(openMaze(9), 0f);
        assertEquals(0, m.activeTileCount());
        // A null maze means nothing is treated as exposed, rather than an exception.
        m.tick(null, 99f);
        assertEquals(0, m.activeTileCount());
    }

    @Test
    public void whatApplyReportsIsWhatTheGridHolds() {
        // The renderer walks a window of tiles and asks each one, so the count apply() returns
        // has to agree with what a scan finds -- otherwise a cloud would draw at a different
        // size from the one it blocks sight over.
        Maze maze = openMaze(11);
        AreaEffectManager m = manager(11);
        int filled = m.apply(maze, 5, 5, AreaEffectType.OBSCURING, 1, 10);

        int found = 0;
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < 11; x++) {
                if (m.isActive(x, y)) {
                    found++;
                    assertEquals(AreaEffectType.OBSCURING, m.typeAt(x, y));
                    assertTrue(m.turnsRemainingAt(x, y) > 0);
                }
            }
        }
        assertEquals(filled, found);
        assertEquals(filled, m.activeTileCount());
    }
}
