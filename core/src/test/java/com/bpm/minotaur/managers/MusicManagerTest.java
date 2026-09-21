package com.bpm.minotaur.managers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class MusicManagerTest {

    private MusicManager musicManager;

    @Before
    public void setUp() {
        musicManager = MusicManager.getInstance();
        musicManager.stop();
        musicManager.setMasterVolume(0.70f);
        musicManager.restoreMusicVolume();
        musicManager.setResting(false);
    }

    @After
    public void tearDown() {
        musicManager.stop();
    }

    @Test
    public void testSingletonInstance() {
        assertNotNull(musicManager);
        assertSame(musicManager, MusicManager.getInstance());
    }

    @Test
    public void testMusicStateTransitions() {
        // Menu music
        musicManager.playMenuMusic("sounds/music/tarmin_core.mp3");
        assertEquals(MusicManager.MusicState.MENU, musicManager.getCurrentState());
        assertEquals("sounds/music/tarmin_core.mp3", musicManager.getCurrentTrackPath());

        // Shelter music
        musicManager.playShelterMusic("sounds/music/tarmin_ambient.ogg");
        assertEquals(MusicManager.MusicState.SHELTER, musicManager.getCurrentState());
        assertEquals("sounds/music/tarmin_ambient.ogg", musicManager.getCurrentTrackPath());

        // Exploration music
        musicManager.playExplorationMusic("sounds/music/tarmin_maze.mp3");
        assertEquals(MusicManager.MusicState.EXPLORATION, musicManager.getCurrentState());
        assertEquals("sounds/music/tarmin_maze.mp3", musicManager.getCurrentTrackPath());

        // Combat music
        musicManager.playCombatMusic("sounds/music/tarmin_fuxx.ogg");
        assertEquals(MusicManager.MusicState.COMBAT, musicManager.getCurrentState());
        assertEquals("sounds/music/tarmin_fuxx.ogg", musicManager.getCurrentTrackPath());

        // Boss combat
        musicManager.playBossCombat("sounds/music/tarmin_boss_tension.wav");
        assertEquals(MusicManager.MusicState.BOSS, musicManager.getCurrentState());
        assertEquals("sounds/music/tarmin_boss_tension.wav", musicManager.getCurrentTrackPath());

        // Exit combat resumes exploration
        musicManager.exitCombat();
        assertEquals(MusicManager.MusicState.EXPLORATION, musicManager.getCurrentState());
        assertEquals("sounds/music/tarmin_maze.mp3", musicManager.getCurrentTrackPath());

        // Stop
        musicManager.stop();
        assertEquals(MusicManager.MusicState.PAUSED, musicManager.getCurrentState());
        assertNull(musicManager.getCurrentTrackPath());
    }

    @Test
    public void testVolumeClampingAndScaling() {
        musicManager.setMasterVolume(0.5f);
        assertEquals(0.5f, musicManager.getMasterVolume(), 0.001f);
        assertEquals(0.5f, musicManager.getVolume(), 0.001f);

        // Clamp upper bound
        musicManager.setMasterVolume(1.8f);
        assertEquals(1.0f, musicManager.getMasterVolume(), 0.001f);

        // Clamp lower bound
        musicManager.setMasterVolume(-0.4f);
        assertEquals(0.0f, musicManager.getMasterVolume(), 0.001f);
    }

    @Test
    public void testAtmosphericBreathingAndDucking() {
        assertFalse(musicManager.isResting());

        musicManager.setResting(true);
        assertTrue(musicManager.isResting());

        // Drive update ticks to simulate smooth lerp interpolation
        for (int i = 0; i < 20; i++) {
            musicManager.update(0.05f);
        }

        // Rest completed
        musicManager.setResting(false);
        assertFalse(musicManager.isResting());

        for (int i = 0; i < 20; i++) {
            musicManager.update(0.05f);
        }
    }

    @Test
    public void testCustomDuckAndRestore() {
        musicManager.duckMusic(0.25f);
        musicManager.update(0.1f);

        musicManager.restoreMusicVolume();
        musicManager.update(0.1f);
    }

    @Test
    public void testSettingsManagerVolumeIntegration() {
        SettingsManager settings = SettingsManager.getInstance();

        settings.setMusicVolume(0.45f);
        assertEquals(0.45f, settings.getMusicVolume(), 0.001f);
        assertEquals(0.45f, musicManager.getMasterVolume(), 0.001f);

        settings.setSfxVolume(0.65f);
        assertEquals(0.65f, settings.getSfxVolume(), 0.001f);

        // Clamping tests
        settings.setMusicVolume(1.5f);
        assertEquals(1.0f, settings.getMusicVolume(), 0.001f);

        settings.setMusicVolume(-0.2f);
        assertEquals(0.0f, settings.getMusicVolume(), 0.001f);

        // Restore defaults
        settings.setMusicVolume(0.70f);
        settings.setSfxVolume(0.80f);
    }
}
