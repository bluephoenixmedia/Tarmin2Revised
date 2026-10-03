package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.Difficulty;
import org.junit.Test;

import static org.junit.Assert.*;

public class StartingFoodTest {

    @Test
    public void everyDifficultyStartsNotHungry() {
        for (Difficulty d : Difficulty.values()) {
            PlayerStats stats = new PlayerStats(d);
            assertEquals(d + " should start Normal, not Hungry",
                    PlayerStats.SatiationState.NORMAL, stats.getSatiationState());
            assertEquals(PlayerStats.STARTING_SATIETY, stats.getSatietyFloat(), 0f);
        }
    }

    @Test
    public void aNewGameCarriesThirtyRationsInOneSlot() {
        assertEquals(30, Player.STARTING_RATIONS);
        assertTrue(Player.STARTING_RATIONS < new com.bpm.minotaur.gamedata.Inventory().getMaxBackpackSize());
    }
}
