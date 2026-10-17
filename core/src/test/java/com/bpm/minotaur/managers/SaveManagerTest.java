package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.save.ProfileData;
import com.bpm.minotaur.gamedata.save.SlotMetadata;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class SaveManagerTest {

    private SaveManager saveManager;

    @Before
    public void setUp() {
        saveManager = SaveManager.getInstance();
    }

    @After
    public void tearDown() {
        // Clean up test slots
        saveManager.deleteSlot(1);
        saveManager.deleteSlot(2);
        saveManager.deleteSlot(3);
    }

    @Test
    public void testSlotDirectoryPathsAndClamping() {
        saveManager.setActiveSlotIndex(1);
        assertEquals(1, saveManager.getActiveSlotIndex());
        assertEquals("saves/slot_1/", saveManager.getActiveSlotDirectoryPath());
        assertEquals("saves/slot_1/chunks/", saveManager.getActiveChunkSaveDirectory());

        saveManager.setActiveSlotIndex(2);
        assertEquals(2, saveManager.getActiveSlotIndex());
        assertEquals("saves/slot_2/", saveManager.getActiveSlotDirectoryPath());

        saveManager.setActiveSlotIndex(99); // Clamped to 3
        assertEquals(3, saveManager.getActiveSlotIndex());

        saveManager.setActiveSlotIndex(-5); // Clamped to 1
        assertEquals(1, saveManager.getActiveSlotIndex());
    }

    @Test
    public void testRespawnPendingSurvivesSaveAndClearsOnSlotChange() {
        saveManager.startNewGame(1, "ADVANCED", "Ariadne", "Rogue");
        com.bpm.minotaur.gamedata.player.Player player = new com.bpm.minotaur.gamedata.player.Player(0, 0);

        saveManager.setRespawnPending(true);
        saveManager.saveActiveSlot(player, null);
        assertTrue("a save written after death must say the respawn is unfinished",
                saveManager.loadActiveWorldData().respawnPending);

        saveManager.setRespawnPending(false);
        saveManager.saveActiveSlot(player, null);
        assertFalse(saveManager.loadActiveWorldData().respawnPending);

        saveManager.setRespawnPending(true);
        saveManager.setActiveSlotIndex(2);
        assertFalse("another slot's character is not the dead one", saveManager.isRespawnPending());
    }

    @Test
    public void testStartNewGameAndMetadata() {
        saveManager.startNewGame(2, "MODERN", "Ariadne", "Rogue");
        assertEquals(2, saveManager.getActiveSlotIndex());

        SlotMetadata meta = saveManager.getSlotMetadata(2);
        assertNotNull(meta);
        assertTrue(meta.isOccupied);
        assertEquals(2, meta.slotIndex);
        assertEquals("Ariadne", meta.characterName);
        assertEquals("Rogue", meta.characterClass);
        assertEquals("MODERN", meta.gameMode);
        assertEquals(1, meta.level);
        assertEquals(20, meta.currentHP);
        assertEquals(20, meta.maxHP);
        assertEquals(0, meta.deathCount);
    }

    @Test
    public void testDeleteSlotResetsOccupiedState() {
        saveManager.startNewGame(3, "MODERN", "Theseus", "Warrior");
        assertTrue(saveManager.getSlotMetadata(3).isOccupied);

        saveManager.deleteSlot(3);
        SlotMetadata meta = saveManager.getSlotMetadata(3);
        assertFalse(meta.isOccupied);
    }

    @Test
    public void testProfileAndClassicModeUnlock() {
        ProfileData profile = saveManager.getProfile();
        assertNotNull(profile);

        // classicModeUnlocked is slot-scoped, so we need an active slot first.
        saveManager.startNewGame(1, "MODERN", "Theseus", "Warrior");
        saveManager.unlockClassicMode();
        assertTrue(saveManager.isClassicModeUnlocked());
        // Verify the flag lives on the slot metadata (not global profile) --
        // this is intentional: a new character must not inherit another slot's victory.
        assertTrue(saveManager.getSlotMetadata(1).classicModeUnlocked);
        saveManager.deleteSlot(1);
    }

    @Test
    public void testShelterBackupCreation() {
        saveManager.startNewGame(1, "MODERN", "Perseus", "Fighter");
        saveManager.backupActiveSlot();

        File backupDir = new File("saves/slot_1/backup");
        assertTrue(backupDir.exists());
        assertTrue(backupDir.isDirectory());

        File metaBackup = new File("saves/slot_1/backup/meta.json");
        assertTrue(metaBackup.exists());
    }
}
