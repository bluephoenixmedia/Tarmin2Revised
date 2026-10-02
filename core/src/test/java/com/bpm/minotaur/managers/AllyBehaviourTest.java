package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.ChunkData;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.CharmRules;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class AllyBehaviourTest {

    private Player player;
    private Maze maze;
    private CombatManager combat;
    private MonsterAiManager ai;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(
                    Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(5f, 9f);
        player.setFacing(Direction.EAST);
        maze = new Maze(1, new int[12][12]);
        SoundManager sound = new SoundManager() {
            @Override
            public void playWeaponSwing() {}
        };
        combat = new CombatManager(player, maze, null, new AnimationManager() {}, new GameEventManager(),
                sound, null, null, null, null, null);
        ai = new MonsterAiManager();
    }

    private Monster put(Monster.MonsterType type, int x, int y) {
        Monster m = new Monster(type, 30, 10, (float) x, (float) y);
        maze.addMonster(m);
        return m;
    }

    private GridPoint2 tileOf(Monster m) {
        return new GridPoint2((int) m.getPosition().x, (int) m.getPosition().y);
    }

    @Test
    public void anAllyWithNothingToFightClosesUpOnThePlayer() {
        Monster ally = put(Monster.MonsterType.ORC, 5, 2);
        ally.setAllyTurns(CharmRules.PERMANENT);
        for (int i = 0; i < 12; i++) {
            ai.updateMonster(ally, maze, player, true, combat);
        }
        GridPoint2 at = tileOf(ally);
        int dist = Math.abs(at.x - 5) + Math.abs(at.y - 9);
        assertTrue("ally should have followed, ended " + dist + " tiles away", dist <= 2);
        assertEquals("an ally never starts a fight with the player",
                CombatManager.CombatState.INACTIVE, combat.getCurrentState());
    }

    @Test
    public void anAllyNextToThePlayerNeverAttacksThem() {
        Monster ally = put(Monster.MonsterType.ORC, 5, 8);
        ally.setAllyTurns(CharmRules.PERMANENT);
        int hp = player.getStats().getCurrentHP();
        for (int i = 0; i < 20; i++) {
            ai.updateMonster(ally, maze, player, true, combat);
        }
        assertEquals(hp, player.getStats().getCurrentHP());
        assertEquals(CombatManager.CombatState.INACTIVE, combat.getCurrentState());
    }

    @Test
    public void anAllyFightsAnAdjacentEnemy() {
        Monster ally = put(Monster.MonsterType.ORC, 5, 6);
        ally.setAllyTurns(CharmRules.PERMANENT);
        Monster enemy = put(Monster.MonsterType.SKELETON, 5, 5);
        int before = enemy.getCurrentHP();
        for (int i = 0; i < 40 && enemy.getCurrentHP() == before; i++) {
            ai.updateMonster(ally, maze, player, true, combat);
        }
        assertTrue("the ally should have hurt the enemy", enemy.getCurrentHP() < before);
    }

    @Test
    public void anAllyWalksUpToADistantEnemy() {
        Monster ally = put(Monster.MonsterType.ORC, 2, 6);
        ally.setAllyTurns(CharmRules.PERMANENT);
        Monster enemy = put(Monster.MonsterType.SKELETON, 8, 6);
        int start = Math.abs(tileOf(ally).x - 8);
        for (int i = 0; i < 3; i++) {
            ai.updateMonster(ally, maze, player, true, combat);
        }
        assertTrue(Math.abs(tileOf(ally).x - 8) < start);
        assertSame(enemy, ally.getTargetMonster());
    }

    @Test
    public void aHostileMonsterGoesForAnAllyThatIsCloserThanThePlayer() {
        Monster hostile = put(Monster.MonsterType.SKELETON, 5, 5);
        Monster ally = put(Monster.MonsterType.ORC, 6, 5);
        ally.setAllyTurns(CharmRules.PERMANENT);
        ai.updateMonster(hostile, maze, player, true, combat);
        assertSame(ally, hostile.getTargetMonster());
    }

    @Test
    public void aTimedCharmRunsOutAndTheMonsterTurnsHostile() {
        Monster ally = put(Monster.MonsterType.ORC, 5, 7);
        ally.setAllyTurns(2);
        ai.updateMonster(ally, maze, player, true, combat);
        assertTrue(ally.isAlly());
        ai.updateMonster(ally, maze, player, true, combat);
        assertFalse(ally.isAlly());
        assertEquals(Monster.MonsterState.HUNTING, ally.getState());
        assertEquals(0, ally.getTameness());
    }

    @Test
    public void aPermanentAllyDoesNotTickDown() {
        Monster ally = put(Monster.MonsterType.ORC, 5, 7);
        ally.setAllyTurns(CharmRules.PERMANENT);
        for (int i = 0; i < 50; i++) {
            ai.updateMonster(ally, maze, player, true, combat);
        }
        assertTrue(ally.isAlly());
        assertEquals(CharmRules.PERMANENT, ally.getAllyTurns());
    }

    @Test
    public void walkingIntoAnAllySwapsPlacesInsteadOfAttacking() {
        player.getPosition().set(5.5f, 5.5f);
        Monster ally = put(Monster.MonsterType.ORC, 6, 5);
        ally.setAllyTurns(CharmRules.PERMANENT);
        player.moveForward(maze, new GameEventManager(), GameMode.ADVANCED, null);
        assertEquals(6, (int) player.getPosition().x);
        assertEquals(5, (int) ally.getPosition().x);
        assertSame(ally, maze.getMonsters().get(new GridPoint2(5, 5)));
        assertNull(maze.getMonsters().get(new GridPoint2(6, 5)));
    }

    @Test
    public void anAllyKeepsItsCharmThroughAChunkSave() {
        Monster ally = put(Monster.MonsterType.ORC, 4, 4);
        ally.setAllyTurns(37);
        assertEquals(37, new ChunkData.MonsterData(ally).allyTurns);
        Monster hostile = put(Monster.MonsterType.SKELETON, 7, 7);
        assertEquals(0, new ChunkData.MonsterData(hostile).allyTurns);
    }
}
