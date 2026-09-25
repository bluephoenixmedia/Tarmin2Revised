package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.gamedata.save.SlotMetadata;
import com.bpm.minotaur.screens.GameScreen;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AutoSaveTest {

    private SaveManager saveManager;

    @Before
    public void setUp() {
        saveManager = SaveManager.getInstance();
        saveManager.setActiveSlotIndex(3);
    }

    @After
    public void tearDown() {
        saveManager.deleteSlot(3);
    }

    @Test
    public void testAutoSaveIntervalIsSixtySeconds() {
        assertEquals(60.0f, GameScreen.AUTOSAVE_INTERVAL_SECONDS, 0.001f);
    }

    @Test
    public void testAutoSaveExecutesCleanlyAndPersistsPlayer() {
        Player player = new Player(5, 5);
        player.getStats().setCurrentHP(18);
        player.getStats().setMaxHP(20);
        player.getStats().setLevel(2);

        // Perform save as autosave does
        saveManager.saveActiveSlot(player, null);

        PlayerSaveData loaded = saveManager.loadActivePlayerData();
        assertNotNull("Autosaved player data must not be null", loaded);
        assertEquals(18, loaded.currentHP);
        assertEquals(20, loaded.maxHP);
        assertEquals(2, loaded.level);

        SlotMetadata meta = saveManager.getSlotMetadata(3);
        assertNotNull(meta);
        assertTrue(meta.isOccupied);
        assertEquals(18, meta.currentHP);
        assertTrue(meta.lastPlayedTimestamp > 0);
    }
}
