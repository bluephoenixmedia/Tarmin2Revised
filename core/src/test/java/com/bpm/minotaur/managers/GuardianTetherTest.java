package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.bpm.minotaur.gamedata.Direction;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.monster.MonsterTemplate;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.headless.HeadlessSoundManager;
import com.bpm.minotaur.rendering.Animation;
import com.bpm.minotaur.rendering.AnimationManager;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** A tethered Guardian attacks only what stands on its own ground (#302). */
public class GuardianTetherTest {

    private Player player;
    private Maze maze;
    private CombatManager combat;
    private Monster guardian;

    @Before
    public void setUp() {
        if (Gdx.app == null) {
            Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
        }
        player = new Player(3f, 3f);
        player.setFacing(Direction.EAST);
        player.getStats().setMaxHP(999);
        player.getStats().setCurrentHP(999);
        maze = new Maze(1, new int[10][10]);
        combat = new CombatManager(player, maze, null, new AnimationManager() {
            @Override
            public void addAnimation(Animation animation) {
            }
        }, new GameEventManager(), new HeadlessSoundManager(), new com.bpm.minotaur.gamedata.item.ItemDataManager(),
                null, null, null, null);
        MonsterTemplate t = new MonsterTemplate();
        t.baseLevel = 1;
        t.maxHP = 50;
        t.hasRangedAttack = true;
        t.attackRange = 8;
        t.damageDice = "1d6";
        guardian = new Monster(Monster.MonsterType.BRINGER_OF_DEATH, 50, 0, 6, 3);
        guardian.setTemplate(t);
        guardian.setCurrentHP(50);
        guardian.setTether(new int[]{5, 1, 8, 8}); // his isle: x 5-8
        maze.addMonster(guardian);
    }

    /** Lets the guardian act {@code tries} times; true if the player was ever hurt. */
    private boolean everHurt(Runnable attack, int tries) {
        for (int i = 0; i < tries; i++) {
            attack.run();
            if (player.getCurrentHP() < 999) return true;
        }
        return false;
    }

    @Test
    public void offHisGroundHeNeitherShootsNorStrikes() {
        assertFalse("no shot at a player off the isle", combat.performMonsterRangedAttack(guardian));
        assertFalse(everHurt(() -> combat.monsterMeleeStrike(guardian), 40));
        assertFalse(combat.reachesPlayer(guardian));
    }

    @Test
    public void onHisGroundHeDoesBoth() {
        player.getPosition().set(5.5f, 3.5f); // stepped onto the isle, beside him
        assertTrue(combat.reachesPlayer(guardian));
        assertTrue("struck at least once on his ground", everHurt(() -> combat.monsterMeleeStrike(guardian), 40));
    }

    @Test
    public void aCutGuardiansAimIsCutInTheSameProportion() {
        MonsterTemplate t = guardian.getTemplate();
        t.maxHP = 140; // the Bringer's template: +10 to hit from his size alone
        t.baseLevel = 18;
        guardian.setLevel(18);
        int whole = CombatManager.calculateMonsterAttackBonus(guardian);
        guardian.setThemeChampion(true);
        guardian.setLevel(4); // cut to a depth-2 stratum
        int cut = CombatManager.calculateMonsterAttackBonus(guardian);
        assertEquals("proficiency 2 and size 10 x 4/18, rounded: 2", 4, cut);
        assertTrue(cut < whole);
    }

    @Test
    public void aDuelWithAGuardianOffItsGroundNeverStallsOnItsTurn() {
        // Found in review: its turn returned without ending, and the duel froze on MONSTER_TURN.
        combat.startCombat(guardian);
        for (int i = 0; i < 30; i++) {
            combat.setCurrentState(CombatManager.CombatState.MONSTER_TURN);
            combat.monsterAttack();
            assertNotEquals("the turn came back (" + i + ")", CombatManager.CombatState.MONSTER_TURN, combat.getCurrentState());
        }
        assertEquals("and nothing reached the player off the isle", 999, player.getCurrentHP());
    }

    @Test
    public void aCutGuardiansShotIsCutAsItsBlowIs() {
        guardian.getTemplate().rangedDamageDice = "10";
        guardian.setDamageScale(0.3f);
        player.getPosition().set(5.5f, 3.5f); // on the isle, in his line
        player.getStats().setMaxHP(5000);
        player.getStats().setCurrentHP(5000);
        maze.getMonsters().values().remove(guardian);
        guardian.getPosition().set(8.5f, 3.5f); // the isle's far side, three tiles off
        maze.addMonster(guardian);
        int hits = 0, taken = 0;
        for (int i = 0; i < 200 && hits < 20; i++) {
            int before = player.getCurrentHP();
            if (combat.performMonsterRangedAttack(guardian) && player.getCurrentHP() < before) {
                hits++;
                taken += before - player.getCurrentHP();
            }
        }
        assertTrue("some shots landed", hits > 0);
        assertTrue("about 3 a shot, not 10: " + taken / (float) hits, taken / (float) hits < 7f);
    }

    @Test
    public void anUntetheredMonsterReachesAnywhere() {
        guardian.setTether(null);
        assertTrue(combat.reachesPlayer(guardian));
    }
}
