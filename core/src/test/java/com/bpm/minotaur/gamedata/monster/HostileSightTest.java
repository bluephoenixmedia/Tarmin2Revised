package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Maze;
import org.junit.Test;

import static org.junit.Assert.*;

public class HostileSightTest {

    private static final Vector2 PLAYER = new Vector2(1.5f, 1.5f);

    private static Maze openRoom() {
        return new Maze(1, new int[12][12]);
    }

    @Test
    public void emptyRoomHasNoHostileInView() {
        assertFalse(HostileSight.anyInView(openRoom(), PLAYER));
    }

    @Test
    public void monsterWithClearSightIsInView() {
        Maze maze = openRoom();
        maze.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 5, 1));
        assertTrue(HostileSight.anyInView(maze, PLAYER));
    }

    @Test
    public void monsterBehindTheFacingDirectionStillCounts() {
        Maze maze = openRoom();
        maze.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 1, 0));
        assertTrue("Sight is all-round, not the first-person view cone", HostileSight.anyInView(maze, PLAYER));
    }

    @Test
    public void monsterBehindAWallIsNotInView() {
        Maze maze = openRoom();
        for (int y = 0; y < 12; y++) {
            maze.setTile(3, y, 1);
        }
        maze.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 5, 1));
        assertFalse(HostileSight.anyInView(maze, PLAYER));
    }

    @Test
    public void monsterBeyondSightRangeIsNotInView() {
        Maze maze = new Maze(1, new int[1][30]);
        maze.addMonster(new Monster(Monster.MonsterType.GOBLIN, 10, 10, 1 + HostileSight.SIGHT_RANGE_TILES + 1, 0));
        assertFalse(HostileSight.anyInView(maze, new Vector2(1.5f, 0.5f)));
    }

    @Test
    public void deadTameOrHiddenMonstersDoNotCount() {
        Maze maze = openRoom();
        Monster dead = new Monster(Monster.MonsterType.GOBLIN, 10, 10, 4, 1);
        dead.setCurrentHP(0);
        Monster tame = new Monster(Monster.MonsterType.GOBLIN, 10, 10, 5, 1);
        tame.setTameness(5);
        Monster hidden = new Monster(Monster.MonsterType.GOBLIN, 10, 10, 6, 1);
        hidden.setHidden(true);
        Monster invisible = new Monster(Monster.MonsterType.GOBLIN, 10, 10, 7, 1);
        invisible.setInvisible(true);
        maze.addMonster(dead);
        maze.addMonster(tame);
        maze.addMonster(hidden);
        maze.addMonster(invisible);

        assertFalse(HostileSight.anyInView(maze, PLAYER));
    }
}
