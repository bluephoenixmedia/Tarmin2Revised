package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** The monster AI honours Pinned and lets Dazed and Pinned wear off (#242). */
public class PinnedMonsterAiTest {

    private Player player;
    private Maze maze;
    private MonsterAiManager ai;
    private CombatManager combat;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        player = new Player(5f, 5f);
        player.setFacing(Direction.EAST);
        maze = new Maze(1, new int[16][16]);
        ai = new MonsterAiManager();
        combat = new CombatManager(player, maze, null, new AnimationManager() {}, new GameEventManager(),
                new HeadlessSoundManager(), null, null, null, null, null);
    }

    private Monster huntingGhoulAt(float x) {
        Monster ghoul = new Monster(Monster.MonsterType.GHOUL, 100, 30, x, 5f);
        ghoul.setState(Monster.MonsterState.HUNTING);
        ghoul.setLastKnownTargetPos(new GridPoint2(5, 5));
        maze.addMonster(ghoul);
        return ghoul;
    }

    @Test
    public void aHuntingMonsterClosesInWhenFree() {
        Monster ghoul = huntingGhoulAt(9f);
        float x = ghoul.getPosition().x;
        ai.updateMonster(ghoul, maze, player, true, combat);
        assertTrue("moved from " + x + " to " + ghoul.getPosition().x, ghoul.getPosition().x < x);
    }

    @Test
    public void aPinnedMonsterHoldsItsTile() {
        Monster ghoul = huntingGhoulAt(9f);
        ghoul.applyPin();
        float x = ghoul.getPosition().x;
        ai.updateMonster(ghoul, maze, player, true, combat);
        assertEquals(x, ghoul.getPosition().x, 0f);
    }

    @Test
    public void aPinnedMonsterBesideThePlayerStillAttacks() {
        Monster ghoul = huntingGhoulAt(6f);
        int hp = player.getCurrentHP();
        for (int turn = 0; turn < 12 && player.getCurrentHP() == hp; turn++) {
            ghoul.applyPin();
            ai.updateMonster(ghoul, maze, player, true, combat);
        }
        assertTrue("a pin stops the feet, not the claws", player.getCurrentHP() < hp);
    }

    @Test
    public void aPinWearsOffAfterThreeOfTheMonstersTurns() {
        Monster ghoul = huntingGhoulAt(9f);
        ghoul.applyPin();
        ghoul.applyDaze();
        for (int i = 0; i < Monster.PIN_TURNS; i++) ai.updateMonster(ghoul, maze, player, true, combat);
        assertFalse(ghoul.isPinned());
        assertFalse(ghoul.isDazed());
    }
}
