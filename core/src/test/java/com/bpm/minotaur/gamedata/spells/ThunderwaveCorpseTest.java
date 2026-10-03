package com.bpm.minotaur.gamedata.spells;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * S1, 2026-10-03: Thunderwave killed a bat and pushed the corpse a tile back.
 * The push moved the bat's map key but not its position, and every kill path
 * removed by position -- so the dead bat stayed on the map, frozen, and every
 * hit on it "defeated" it again for experience and divinities, forever.
 */
public class ThunderwaveCorpseTest {

    private Player player;
    private Maze maze;
    private GameEventManager eventManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        SpellDataManager.getInstance().load();
        maze = new Maze(1, new int[10][10]);
        player = new Player(2, 2);
        player.setFacing(Direction.NORTH);
        player.getStats().setMaxMP(100);
        player.getStats().setCurrentMP(100);
        eventManager = new GameEventManager();
    }

    private Monster batInFront(int hp) {
        Monster bat = new Monster(Monster.MonsterType.GOBLIN, hp, 10, 2, 3);
        maze.addMonster(bat);
        return bat;
    }

    private static void assertKeyMatchesPosition(Maze maze) {
        for (Map.Entry<GridPoint2, Monster> e : maze.getMonsters().entrySet()) {
            Monster m = e.getValue();
            assertEquals("map key and position drifted apart",
                    e.getKey(), new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y));
        }
    }

    @Test
    public void aMonsterThunderwaveKillsLeavesTheMap() {
        Monster bat = batInFront(1);

        assertTrue(SpellExecutionEngine.castSpell("THUNDERWAVE", player, maze, eventManager, null));

        assertTrue("the bat must be dead", bat.getCurrentHP() <= 0);
        assertFalse("a corpse left on the map can be killed again for free",
                maze.getMonsters().containsValue(bat));
    }

    @Test
    public void aMonsterThunderwavePushesStaysWhereItsMapKeySaysItIs() {
        Monster ogre = batInFront(500);

        assertTrue(SpellExecutionEngine.castSpell("THUNDERWAVE", player, maze, eventManager, null));

        assertTrue("survives the blast", ogre.getCurrentHP() > 0);
        assertTrue(maze.getMonsters().containsValue(ogre));
        assertKeyMatchesPosition(maze);
        assertEquals("pushed one tile back", 4, (int) ogre.getPosition().y);
    }

    @Test
    public void removeMonsterFindsAMonsterWhosePositionIsStale() {
        Monster bat = batInFront(5);
        bat.getPosition().set(7.5f, 7.5f); // the drift the push bug produced

        assertTrue(maze.removeMonster(bat));
        assertTrue(maze.getMonsters().isEmpty());
    }

    @Test
    public void moveMonsterMovesKeyAndPositionTogether() {
        Monster bat = batInFront(5);

        maze.moveMonster(bat, 6, 1);

        assertSame(bat, maze.getMonsters().get(new GridPoint2(6, 1)));
        assertEquals(1, maze.getMonsters().size());
        assertKeyMatchesPosition(maze);
    }

    @Test
    public void aMonsterDiesOnce() {
        Monster bat = batInFront(5);
        assertTrue("the first kill pays out", bat.claimDeath());
        assertFalse("no kill path may pay out the same death twice", bat.claimDeath());
        assertTrue(bat.isDeathClaimed());
    }
}
