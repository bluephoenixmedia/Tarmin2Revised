package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.ForcedMovement.Result;
import org.junit.Test;

import static org.junit.Assert.*;

/** Knockback and pull, for Battering Ram and Reel In (#242). */
public class ForcedMovementTest {

    private static final GridPoint2 PLAYER = new GridPoint2(3, 3);

    private static Monster at(Maze maze, int x, int y, MonsterRole role, float xpMultiplier) {
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.role = role;
        t.xpMultiplier = xpMultiplier;
        Monster m = new Monster(Monster.MonsterType.KOBOLD, 10, 10, x, y);
        m.setTemplate(t);
        maze.addMonster(m);
        return m;
    }

    private static Monster at(Maze maze, int x, int y) {
        return at(maze, x, y, MonsterRole.SOLDIER, 1f);
    }

    private static GridPoint2 tile(Monster m) {
        return new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y);
    }

    @Test
    public void aKnockbackMovesTheMonsterOneTileAndRefilesIt() {
        Maze maze = new Maze(1, new int[8][8]);
        Monster m = at(maze, 4, 3);

        assertEquals(Result.MOVED, ForcedMovement.knockBack(maze, m, Direction.EAST, PLAYER));

        assertEquals(new GridPoint2(5, 3), tile(m));
        assertSame(m, maze.getMonsters().get(new GridPoint2(5, 3)));
        assertNull(maze.getMonsters().get(new GridPoint2(4, 3)));
    }

    @Test
    public void aWallStopsTheKnockbackAndCountsAsAnImpact() {
        Maze maze = new Maze(1, new int[8][8]);
        maze.setTile(4, 3, Direction.EAST.getWallMask());
        Monster m = at(maze, 4, 3);

        assertEquals(Result.HIT_WALL, ForcedMovement.knockBack(maze, m, Direction.EAST, PLAYER));
        assertEquals(new GridPoint2(4, 3), tile(m));
    }

    @Test
    public void theEdgeOfTheMapIsAWall() {
        Maze maze = new Maze(1, new int[8][8]);
        Monster m = at(maze, 7, 3);
        assertEquals(Result.HIT_WALL, ForcedMovement.knockBack(maze, m, Direction.EAST, PLAYER));
    }

    @Test
    public void anotherCreatureBlocksTheKnockback() {
        Maze maze = new Maze(1, new int[8][8]);
        Monster m = at(maze, 4, 3);
        at(maze, 5, 3);

        assertEquals(Result.BLOCKED, ForcedMovement.knockBack(maze, m, Direction.EAST, PLAYER));
        assertEquals(new GridPoint2(4, 3), tile(m));
    }

    @Test
    public void aPullDrawsAMonsterFromTwoTilesIntoReach() {
        Maze maze = new Maze(1, new int[8][8]);
        Monster m = at(maze, 5, 3);

        assertEquals(Result.MOVED, ForcedMovement.pull(maze, m, PLAYER));
        assertEquals("next to the player, not onto them", new GridPoint2(4, 3), tile(m));
    }

    @Test
    public void aWallBetweenStopsThePull() {
        Maze maze = new Maze(1, new int[8][8]);
        maze.setTile(5, 3, Direction.WEST.getWallMask());
        Monster m = at(maze, 5, 3);

        assertEquals(Result.HIT_WALL, ForcedMovement.pull(maze, m, PLAYER));
        assertEquals(new GridPoint2(5, 3), tile(m));
    }

    @Test
    public void heaviesAndElitesCannotBeMoved() {
        Maze maze = new Maze(1, new int[8][8]);
        Monster heavy = at(maze, 4, 3, MonsterRole.HEAVY, 1f);
        Monster elite = at(maze, 5, 4, MonsterRole.SOLDIER, 2f);

        assertEquals(Result.IMMOVABLE, ForcedMovement.knockBack(maze, heavy, Direction.EAST, PLAYER));
        assertEquals(Result.IMMOVABLE, ForcedMovement.pull(maze, elite, PLAYER));
        assertEquals(new GridPoint2(4, 3), tile(heavy));
    }

    @Test
    public void aBossCannotBeMovedEither() {
        Maze maze = new Maze(1, new int[8][8]);
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 5;
        Monster minotaur = new Monster(Monster.MonsterType.MINOTAUR, 40, 14, 4, 3);
        minotaur.setTemplate(t);
        maze.addMonster(minotaur);

        assertEquals(Result.IMMOVABLE, ForcedMovement.knockBack(maze, minotaur, Direction.EAST, PLAYER));
    }
}
