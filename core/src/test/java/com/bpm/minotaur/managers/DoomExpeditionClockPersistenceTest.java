package com.bpm.minotaur.managers;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The expedition turn count drives the Doom stage, so losing it on reload would let a quit reset
 * Tarmin's Hunger escalation.
 */
public class DoomExpeditionClockPersistenceTest {

    @After
    public void tearDown() {
        SaveManager.getInstance().deleteSlot(1);
    }

    @Test
    public void expeditionTurnsSurviveSaveAndReload() {
        SaveManager.getInstance().startNewGame(1, "ADVANCED", "Ariadne", "Rogue");
        DoomManager doom = DoomManager.getInstance();
        doom.resetExpeditionTurns();
        for (int i = 0; i < 450; i++) {
            doom.advanceExpeditionTurn();
        }
        assertEquals(2, doom.getDoomStage());

        doom.save();
        doom.reloadForActiveSlot();

        assertEquals(450, doom.getExpeditionTurns());
        assertEquals(2, doom.getDoomStage());
    }
}
