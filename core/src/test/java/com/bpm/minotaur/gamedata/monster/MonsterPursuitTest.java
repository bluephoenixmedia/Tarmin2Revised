package com.bpm.minotaur.gamedata.monster;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Door;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.Pathfinder;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.MonsterPursuitManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class MonsterPursuitTest {

    @Before
    public void setUp() {
        MonsterPursuitManager.getInstance().clear();
    }

    @Test
    public void testMonsterEligibility() {
        // Minotaur: INT 20, MYTHICAL -> should open doors and climb ladders
        Monster minotaur = new Monster(Monster.MonsterType.MINOTAUR, 5, 5, 0.5f, 0.5f);
        minotaur.setIntelligence(20);
        minotaur.setFamily(MonsterFamily.MYTHICAL);
        assertTrue("Minotaur should operate doors", minotaur.canOperateDoors());
        assertTrue("Minotaur should climb ladders", minotaur.canClimbLadders());

        // Lich: INT 25, UNDEAD -> should operate doors and climb ladders
        Monster lich = new Monster(Monster.MonsterType.LICH, 5, 5, 0.5f, 0.5f);
        lich.setIntelligence(25);
        lich.setFamily(MonsterFamily.UNDEAD);
        assertTrue("Lich should operate doors", lich.canOperateDoors());
        assertTrue("Lich should climb ladders", lich.canClimbLadders());

        // Dragon: INT 10, MYTHICAL, type DRAGON -> explicitly ineligible
        Monster dragon = new Monster(Monster.MonsterType.DRAGON, 5, 5, 0.5f, 0.5f);
        dragon.setIntelligence(10);
        dragon.setFamily(MonsterFamily.MYTHICAL);
        assertFalse("Dragon cannot operate doors", dragon.canOperateDoors());
        assertFalse("Dragon cannot climb ladders", dragon.canClimbLadders());

        // Giant Ant / Beast: family BEAST, INT 4 -> ineligible
        Monster ant = new Monster(Monster.MonsterType.GIANT_ANT, 5, 5, 0.5f, 0.5f);
        ant.setIntelligence(4);
        ant.setFamily(MonsterFamily.BEAST);
        assertFalse("Beasts cannot operate doors", ant.canOperateDoors());
        assertFalse("Beasts cannot climb ladders", ant.canClimbLadders());
    }

    @Test
    public void testPathfindingWithDoorsAndSanctuary() {
        // Create 5x5 maze with empty interior
        int[][] walls = new int[5][5];
        // Enclose with boundary walls
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                if (x == 0 || x == 4 || y == 0 || y == 4) {
                    walls[y][x] = 1;
                } else {
                    walls[y][x] = 0;
                }
            }
        }

        // Place an interior wall dividing column 1 and column 2
        // Solid walls at rows 1 and 3, closed door at row 2
        Direction east = Direction.EAST;
        walls[1][1] = east.getWallMask();
        walls[3][1] = east.getWallMask();
        int doorMask = east.getWallMask() << 1;
        walls[2][1] = doorMask;

        Maze maze = new Maze(1, walls);
        Door door = new Door();
        door.close(); // CLOSED
        maze.addGameObject(door, 2, 2);

        Player player = new Player(3f, 2f);
        GridPoint2 start = new GridPoint2(1, 2);
        GridPoint2 goal = new GridPoint2(3, 2);

        // 1. Non-door opener should fail to path directly through closed door
        List<GridPoint2> pathNoDoor = Pathfinder.findPath(maze, player, start, goal, false);
        // Path should either be empty or route around (not through (2,2))
        boolean passedThroughDoor = false;
        for (GridPoint2 pt : pathNoDoor) {
            if (pt.x == 2 && pt.y == 2) {
                passedThroughDoor = true;
                break;
            }
        }
        assertFalse("Non-door opener must not step through closed door", passedThroughDoor);

        // 2. Door opener should route through closed door
        List<GridPoint2> pathWithDoor = Pathfinder.findPath(maze, player, start, goal, true);
        assertFalse("Door opener should find path", pathWithDoor.isEmpty());
        assertEquals("Next step should be door tile", new GridPoint2(2, 2), pathWithDoor.get(0));

        // 3. Sanctuary Ward Check: Add goal tile to home tiles
        maze.getHomeTiles().add(new GridPoint2(3, 2));
        List<GridPoint2> pathSanctuary = Pathfinder.findPath(maze, player, start, goal, true);
        assertTrue("Shelter sanctuary home tiles must be impassable to monsters", pathSanctuary.isEmpty());
    }

    @Test
    public void testGatePursuitQueueAndStaggering() {
        MonsterPursuitManager manager = MonsterPursuitManager.getInstance();
        manager.clear();

        Monster m1 = new Monster(Monster.MonsterType.LICH, 1, 1, 0.5f, 0.5f);
        Monster m2 = new Monster(Monster.MonsterType.MIND_FLAYER, 2, 1, 0.5f, 0.5f);
        Monster m3 = new Monster(Monster.MonsterType.VAMPIRE, 3, 1, 0.5f, 0.5f);

        // Register 3 pursuers (should cap at 2)
        List<Monster> candidates = Arrays.asList(m1, m2, m3);
        GridPoint2 originChunk = new GridPoint2(0, 0);
        GridPoint2 targetChunk = new GridPoint2(1, 0);
        GridPoint2 arrivalTile = new GridPoint2(1, 1);

        manager.registerGatePursuit(candidates, originChunk, targetChunk, arrivalTile, 1);

        assertEquals("Should cap at 2 simultaneous pursuers", 2, manager.getPendingPursuers().size());
        assertEquals("First pursuer stagger turns", 2, manager.getPendingPursuers().get(0).turnsRemaining);
        assertEquals("Second pursuer stagger turns", 4, manager.getPendingPursuers().get(1).turnsRemaining);

        // Setup test maze
        int[][] walls = new int[5][5];
        Maze maze = new Maze(1, walls);
        Player player = new Player(3f, 3f);

        // Turn 1: Decrement countdown, neither arrives
        manager.onPlayerTurn(maze, player, null, null, null, null);
        assertEquals("Still 2 pending after 1 turn", 2, manager.getPendingPursuers().size());
        assertTrue("Maze should have 0 monsters", maze.getMonsters().isEmpty());

        // Turn 2: First pursuer arrives
        manager.onPlayerTurn(maze, player, null, null, null, null);
        assertEquals("1 pursuer remaining pending", 1, manager.getPendingPursuers().size());
        assertEquals("First pursuer should spawn in maze", 1, maze.getMonsters().size());

        Monster spawned = maze.getMonsters().values().iterator().next();
        assertEquals(Monster.MonsterType.LICH, spawned.getType());
        assertEquals(Monster.MonsterState.HUNTING, spawned.getState());
        assertEquals(8, spawned.getSearchTurnsRemaining());
    }

    @Test
    public void testLadderPursuitDreadWarningAndAntiCheeseShove() {
        MonsterPursuitManager manager = MonsterPursuitManager.getInstance();
        manager.clear();

        Monster minotaur = new Monster(Monster.MonsterType.MINOTAUR, 5, 5, 0.5f, 0.5f);
        GridPoint2 arrivalTile = new GridPoint2(2, 2);

        manager.registerLadderPursuit(Arrays.asList(minotaur), 1, 2, arrivalTile, true);

        assertEquals(1, manager.getPendingPursuers().size());
        MonsterPursuitManager.PendingPursuer pursuer = manager.getPendingPursuers().get(0);
        assertEquals(3, pursuer.turnsRemaining);
        assertFalse(pursuer.warned);

        // Setup maze on level 2
        int[][] walls = new int[5][5];
        Maze maze = new Maze(2, walls);

        // Player blocks arrival tile (2,2), with walls surrounding it except (2, 3)
        Player player = new Player(2f, 2f);
        walls[2][1] = 1; // west wall
        walls[2][3] = 1; // east wall
        walls[1][2] = 1; // south wall
        // Only (2, 3) open to shove into (or north (2,1) depending on coord convention)

        // Turn 1: emits dread warning
        manager.onPlayerTurn(maze, player, null, null, null, null);
        assertTrue("Dread warning should trigger on first tick", pursuer.warned);
        assertEquals(2, pursuer.turnsRemaining);

        // Turn 2: tick
        manager.onPlayerTurn(maze, player, null, null, null, null);
        assertEquals(1, pursuer.turnsRemaining);

        // Turn 3: Minotaur emerges! Since player blocks (2,2), pursuer shoves player
        manager.onPlayerTurn(maze, player, null, null, null, null);
        assertTrue("Pursuit queue should be empty", manager.getPendingPursuers().isEmpty());
        assertEquals("Minotaur should be placed in maze", 1, maze.getMonsters().size());
    }
}
