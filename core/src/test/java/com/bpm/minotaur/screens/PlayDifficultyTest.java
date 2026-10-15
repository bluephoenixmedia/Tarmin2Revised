package com.bpm.minotaur.screens;

import com.bpm.minotaur.gamedata.Difficulty;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** New and loaded games are played on MEDIUM, the reference difficulty for balance work. */
public class PlayDifficultyTest {

    @Test
    public void gamesArePlayedOnMedium() {
        assertEquals(Difficulty.MEDIUM, SaveSlotSelectScreen.PLAY_DIFFICULTY);
    }
}
