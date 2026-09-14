package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression coverage for the "near invincible" telemetry bugs found alongside
 * the AC-stacking issue: the real per-tick monster attack path,
 * CombatManager#monsterMeleeStrike, applied damage to the player but never fed
 * the combat bookkeeping (damageTakenInCombat / logCombatRound), and a killing
 * blow through it bypassed CombatState.DEFEAT entirely -- which is why every
 * historical death session was missing a COMBAT_END line.
 */
public class CombatLoopInstrumentationTest {

    private CombatManager combatManager;
    private Player player;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        player = new Player(5f, 5f);
        Maze maze = new Maze(1, new int[10][10]);
        com.bpm.minotaur.rendering.AnimationManager animationManager =
                new com.bpm.minotaur.rendering.AnimationManager() {};
        combatManager = new CombatManager(player, maze, null, animationManager, new GameEventManager(),
                silentSoundManager(), null, null, null, null, null);
    }

    // monsterMeleeStrike's very first call is soundManager.playMonsterAttackSound(),
    // which dereferences DebugManager -- headless tests have no audio backend, so
    // the sound side-effects are stubbed out rather than exercised here.
    private SoundManager silentSoundManager() {
        return new SoundManager() {
            @Override
            public void playMonsterAttackSound(Monster monster) {
            }
        };
    }

    @Test
    public void monsterMeleeStrikeDamageIsTalliedIntoCombatCounters() {
        Monster attacker = new Monster(Monster.MonsterType.GOBLIN, 20, 10, 6f, 5f);

        // Player AC is 10 (base, unarmored); attackBonus is 2 (no template).
        // d20 + 2 >= 10 hits on a roll of 8+ (65% per swing) -- loop generously so
        // the assertion isn't flaky, but bounded so a real regression still fails fast.
        for (int i = 0; i < 100 && combatManager.getDamageTakenInCombat() == 0; i++) {
            combatManager.monsterMeleeStrike(attacker);
        }

        assertTrue("At least one of 100 monster swings at 65% hit chance should have landed",
                combatManager.getDamageTakenInCombat() > 0);
    }

    @Test
    public void killingBlowFromMonsterMeleeStrikeRoutesThroughDefeatState() {
        Monster attacker = new Monster(Monster.MonsterType.GOBLIN, 20, 10, 6f, 5f);
        player.getStats().setCurrentHP(1);

        for (int i = 0; i < 100 && combatManager.getCurrentState() != CombatManager.CombatState.DEFEAT; i++) {
            combatManager.monsterMeleeStrike(attacker);
        }

        assertEquals(CombatManager.CombatState.DEFEAT, combatManager.getCurrentState());

        // Drive one update() tick so the DEFEAT branch actually runs (logCombatEnd,
        // the death-inversion check, PLAYER_DIED, endCombat()) instead of only
        // checking that the state flag was set. A GOBLIN never triggers death
        // inversion, so this settles back to INACTIVE via the plain PLAYER_DIED path.
        combatManager.update(0f);
        assertEquals(CombatManager.CombatState.INACTIVE, combatManager.getCurrentState());
    }

    @Test
    public void repeatedSwingsAtSameSurvivingMonsterAccumulateTurnsInsteadOfResetting() {
        // High HP so it's guaranteed to survive every swing regardless of damage rolls --
        // isolates the counter-reset bug from attack-roll RNG.
        Monster tankyTarget = new Monster(Monster.MonsterType.GOBLIN, 1000, 10, 6f, 5f);

        combatManager.playerMeleeStrike(tankyTarget);
        combatManager.playerMeleeStrike(tankyTarget);
        combatManager.playerMeleeStrike(tankyTarget);

        // Previously playerMeleeStrike() reset currentCombatTurns to 0 on every call
        // while stateless combat kept currentState == INACTIVE, so this would have
        // stuck at 1 forever instead of accumulating.
        assertEquals(3, combatManager.getCurrentCombatTurns());
    }
}
