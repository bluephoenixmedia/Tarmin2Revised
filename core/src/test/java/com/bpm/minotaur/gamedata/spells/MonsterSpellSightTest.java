package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.spells.MonsterSpellSight.Reach;
import org.junit.Test;

import static org.junit.Assert.*;

public class MonsterSpellSightTest {

    private static Vector2 centre(int x, int y) {
        return new Vector2(x + 0.5f, y + 0.5f);
    }

    private static Maze open() {
        return new Maze(1, new int[5][9]);
    }

    @Test
    public void anOpenCorridorIsClear() {
        assertEquals(Reach.CLEAR, MonsterSpellSight.assess(open(), centre(1, 2), centre(6, 2), false, false));
    }

    @Test
    public void aWallOnTheCastersOwnEdgeBlocksTheCast() {
        // The old test never looked at the start tile, so this wall did nothing.
        Maze maze = open();
        maze.setTile(1, 2, Direction.EAST.getWallMask());
        assertEquals(Reach.BLOCKED, MonsterSpellSight.assess(maze, centre(1, 2), centre(6, 2), false, false));
    }

    @Test
    public void aWallWrittenOnlyOnTheFarSideStillBlocks() {
        // Wall data is not always symmetric; reading both sides of every edge is the point.
        Maze maze = open();
        maze.setTile(4, 2, Direction.WEST.getWallMask());
        assertEquals(Reach.BLOCKED, MonsterSpellSight.assess(maze, centre(1, 2), centre(6, 2), false, false));
    }

    @Test
    public void aClosedDoorBlocksAndAnOpenOneDoesNot() {
        Maze maze = open();
        maze.setTile(3, 2, Direction.EAST.getWallMask() << 1);
        Door door = new Door();
        maze.addGameObject(door, 3, 2);

        assertEquals(Reach.BLOCKED, MonsterSpellSight.assess(maze, centre(1, 2), centre(6, 2), false, false));

        door.setState(Door.DoorState.OPEN, 1f);
        assertEquals(Reach.CLEAR, MonsterSpellSight.assess(maze, centre(1, 2), centre(6, 2), false, false));
    }

    @Test
    public void aDiagonalCannotSqueezeThroughACorner() {
        Maze maze = open();
        // Walls on both ways round the corner between (1,1) and (2,2).
        maze.setTile(1, 1, Direction.EAST.getWallMask() | Direction.NORTH.getWallMask());
        assertEquals(Reach.BLOCKED, MonsterSpellSight.assess(maze, centre(1, 1), centre(2, 2), false, false));
    }

    @Test
    public void aPiercingSpellNeedsAPiercingCasterToo() {
        Maze maze = open();
        maze.setTile(1, 2, Direction.EAST.getWallMask());
        Vector2 from = centre(1, 2);
        Vector2 to = centre(6, 2);

        assertEquals("both keys", Reach.THROUGH_WALLS, MonsterSpellSight.assess(maze, from, to, true, true));
        assertEquals("spell alone", Reach.BLOCKED, MonsterSpellSight.assess(maze, from, to, true, false));
        assertEquals("caster alone", Reach.BLOCKED, MonsterSpellSight.assess(maze, from, to, false, true));
    }

    @Test
    public void psychicSpellsPierceAndOrdinaryOnesDoNot() {
        SpellTemplate psychic = new SpellTemplate();
        psychic.damageType = "PSYCHIC";
        SpellTemplate fire = new SpellTemplate();
        fire.damageType = "FIRE";
        SpellTemplate flagged = new SpellTemplate();
        flagged.damageType = "FIRE";
        flagged.ignoresLineOfSight = true;

        assertTrue(psychic.pierces());
        assertFalse(fire.pierces());
        assertTrue(flagged.pierces());
    }

    @Test
    public void aPiercedCastIsReportedAsCastable() {
        assertTrue(Reach.THROUGH_WALLS.canCast());
        assertTrue(Reach.CLEAR.canCast());
        assertFalse(Reach.BLOCKED.canCast());
    }
}
