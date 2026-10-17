package com.bpm.minotaur.managers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Tarmin's Hunger after #176 / #188: turns within an Expedition still escalate the stage and reset
 * each expedition, while deaths only raise a slow baseline across all 50 (stage 2 from 10, stage 3
 * from 25, stage 4 from 40). The stage is the higher of the two. Deaths no longer scale monster
 * damage -- with permanent death per Incarnation that was a death spiral.
 */
public class TarminsHungerStageTest {

    private DoomManager doom;

    @Before
    public void setUp() {
        SaveManager.getInstance().startNewGame(1, "ADVANCED", "Ariadne", "Rogue");
        doom = DoomManager.getInstance();
        doom.resetDeaths();
        doom.resetExpeditionTurns();
    }

    @After
    public void tearDown() {
        doom.resetDeaths();
        doom.resetExpeditionTurns();
        SaveManager.getInstance().deleteSlot(1);
    }

    private void dieUntil(int deaths) {
        while (doom.getDeathCount() < deaths) {
            doom.incrementDeaths();
        }
    }

    @Test
    public void earlyDeathsNoLongerJumpTheStage() {
        dieUntil(3);
        assertEquals("three deaths used to pin the game at stage 4", 1, doom.getDoomStage());
        dieUntil(9);
        assertEquals(1, doom.getDoomStage());
    }

    @Test
    public void deathsRaiseASlowBaselineAcrossAllFifty() {
        dieUntil(10);
        assertEquals(2, doom.getDoomStage());
        dieUntil(24);
        assertEquals(2, doom.getDoomStage());
        dieUntil(25);
        assertEquals(3, doom.getDoomStage());
        dieUntil(39);
        assertEquals(3, doom.getDoomStage());
        dieUntil(40);
        assertEquals(4, doom.getDoomStage());
        dieUntil(49);
        assertEquals(4, doom.getDoomStage());
    }

    @Test
    public void stageIsTheHigherOfTurnsAndDeaths() {
        dieUntil(10);
        for (int i = 0; i < 900; i++) {
            doom.advanceExpeditionTurn();
        }
        assertEquals("turns past 800 outrank a stage-2 death baseline", 3, doom.getDoomStage());

        doom.resetExpeditionTurns();
        assertEquals("a new expedition falls back to the death baseline, not to stage 1",
                2, doom.getDoomStage());

        dieUntil(40);
        for (int i = 0; i < 450; i++) {
            doom.advanceExpeditionTurn();
        }
        assertEquals("a stage-4 death baseline outranks stage-2 turns", 4, doom.getDoomStage());
    }

    @Test
    public void deathsNoLongerScaleMonsterDamage() {
        for (Method m : DoomManager.class.getDeclaredMethods()) {
            assertNotEquals("per-death monster scaling was removed by #188",
                    "getEnemyScalingMultiplier", m.getName());
        }
    }
}
