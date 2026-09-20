package com.bpm.minotaur.gamedata.firearm;

import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.monster.Monster;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Firing wakes the level.
 *
 * <p>This is what a shot costs beyond ammunition: you trade one dead enemy for several
 * awake ones. Without it the drawback of a black-powder weapon is entirely imaginary,
 * and a 2d8 shot at bow range is simply better than a bow.
 *
 * <p>Noise is not sight -- it passes through walls, because sound does.
 */
public class GunshotNoiseTest {

    private static final Vector2 SHOOTER = new Vector2(5.5f, 5.5f);

    private static Maze openRoom() {
        return new Maze(1, new int[24][24]);
    }

    /** Note the constructor order: (type, hp, ac, x, y). */
    private static Monster sleeperAt(int x, int y) {
        Monster m = new Monster(Monster.MonsterType.GOBLIN, 6, 5, x, y);
        m.setState(Monster.MonsterState.IDLE);
        return m;
    }

    @Test
    public void testMonstersInsideTheRadiusWakeAndHunt() {
        Maze maze = openRoom();
        Monster near = sleeperAt(8, 5);
        maze.addMonster(near);

        int woken = GunshotNoise.wake(maze, SHOOTER, Item.ItemType.MUSKET);

        assertEquals(1, woken);
        assertEquals(Monster.MonsterState.HUNTING, near.getState());
    }

    @Test
    public void testMonstersBeyondTheRadiusSleepOn() {
        Maze maze = openRoom();
        Monster far = sleeperAt(22, 22); // ~23 tiles away, past even the blunderbuss
        maze.addMonster(far);

        int woken = GunshotNoise.wake(maze, SHOOTER, Item.ItemType.MUSKET);

        assertEquals(0, woken);
        assertEquals(Monster.MonsterState.IDLE, far.getState());
    }

    @Test
    public void testALouderGunWakesMoreThanAQuieterOne() {
        // A goblin at 14 tiles: inside the blunderbuss's 16, outside the pistol's 9.
        Maze pistolRoom = openRoom();
        Monster byPistol = sleeperAt(19, 5);
        pistolRoom.addMonster(byPistol);

        Maze blunderRoom = openRoom();
        Monster byBlunderbuss = sleeperAt(19, 5);
        blunderRoom.addMonster(byBlunderbuss);

        assertEquals(0, GunshotNoise.wake(pistolRoom, SHOOTER, Item.ItemType.PISTOL_STARWHEEL));
        assertEquals(1, GunshotNoise.wake(blunderRoom, SHOOTER, Item.ItemType.BLUNDERBUS));
    }

    @Test
    public void testNoiseTravelsThroughWalls() {
        Maze maze = openRoom();
        for (int y = 0; y < 24; y++) {
            maze.setTile(7, y, 1); // a solid wall between shooter and sleeper
        }
        Monster behindWall = sleeperAt(9, 5);
        maze.addMonster(behindWall);

        assertEquals("sound is not sight", 1,
                GunshotNoise.wake(maze, SHOOTER, Item.ItemType.MUSKET));
        assertEquals(Monster.MonsterState.HUNTING, behindWall.getState());
    }

    @Test
    public void testAWokenMonsterIsPointedAtTheShot() {
        Maze maze = openRoom();
        Monster near = sleeperAt(8, 5);
        maze.addMonster(near);

        GunshotNoise.wake(maze, SHOOTER, Item.ItemType.MUSKET);

        assertNotNull("it should know where the bang came from", near.getLastKnownTargetPos());
        assertEquals(5, near.getLastKnownTargetPos().x);
        assertEquals(5, near.getLastKnownTargetPos().y);
    }

    @Test
    public void testAlreadyHuntingMonstersAreNotCountedAgain() {
        Maze maze = openRoom();
        Monster alert = sleeperAt(8, 5);
        alert.setState(Monster.MonsterState.HUNTING);
        maze.addMonster(alert);

        assertEquals("only newly-woken monsters are news", 0,
                GunshotNoise.wake(maze, SHOOTER, Item.ItemType.MUSKET));
    }

    @Test
    public void testSilentWeaponsWakeNobody() {
        Maze maze = openRoom();
        maze.addMonster(sleeperAt(6, 5));

        assertEquals(0, GunshotNoise.wake(maze, SHOOTER, Item.ItemType.BOW));
        assertEquals(0, GunshotNoise.wake(maze, SHOOTER, null));
    }

    @Test
    public void testNullsAreTolerated() {
        assertEquals(0, GunshotNoise.wake(null, SHOOTER, Item.ItemType.MUSKET));
        assertEquals(0, GunshotNoise.wake(openRoom(), null, Item.ItemType.MUSKET));
    }
}
