package com.bpm.minotaur.gamedata.progression;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** What stands beside the Altar or the Lectern never closes a way through the Shelter. */
public class ShelterPlacementTest {

    /**
     * A 3x3 room (x 1..3, y 1..3) whose only way out is the tile below its bottom middle, (2,0).
     * The Altar stands in the bottom-left corner, (1,1): its first free neighbour, (2,1), is the
     * one tile between the room and the way out.
     */
    private static Maze roomWithOneExit() {
        Maze maze = new Maze(1, new int[8][8]);
        List<GridPoint2> home = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) home.add(new GridPoint2(x, y));
        }
        home.add(new GridPoint2(2, 0));
        maze.setHomeTiles(home);
        return maze;
    }

    @Test
    public void nothingIsStoodWhereItWouldCloseTheWayOut() {
        Maze maze = roomWithOneExit();
        GridPoint2 altar = new GridPoint2(1, 1);
        GridPoint2 spot = ShelterAltar.safeSpotBeside(maze, altar);
        assertNotNull(spot);
        assertNotEquals("the doorway stays open", new GridPoint2(2, 1), spot);
        assertEquals(new GridPoint2(1, 2), spot);
    }

    @Test
    public void nothingStandsBesideTheWayOutEvenWhereTheFloorStaysWhole() {
        Maze maze = new Maze(1, new int[8][8]);
        List<GridPoint2> home = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) home.add(new GridPoint2(x, y));
        }
        maze.setHomeTiles(home);
        // The way out is outside the home tiles, below the room's bottom middle.
        maze.setShelterEntry(new GridPoint2(2, 0));
        GridPoint2 spot = ShelterAltar.safeSpotBeside(maze, new GridPoint2(1, 1));
        assertNotEquals("the tile before the way out stays clear", new GridPoint2(2, 1), spot);
        assertTrue(ShelterAltar.byTheWayOut(maze, new GridPoint2(2, 1)));
    }

    @Test
    public void aTileThatHoldsTheShelterTogetherIsKnownForOne() {
        Maze maze = roomWithOneExit();
        GridPoint2 altar = new GridPoint2(1, 1);
        assertTrue(ShelterAltar.wouldCut(maze, new GridPoint2(2, 1), altar));
        assertFalse(ShelterAltar.wouldCut(maze, new GridPoint2(1, 2), altar));
    }
}
