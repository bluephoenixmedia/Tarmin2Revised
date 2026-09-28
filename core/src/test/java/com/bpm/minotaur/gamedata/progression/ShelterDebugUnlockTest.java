package com.bpm.minotaur.gamedata.progression;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ShelterDebugUnlockTest {

    private ShelterAltar altar;

    @Before
    public void setUp() {
        altar = ShelterAltar.getInstance();
        altar.setDebugAllUnlocked(false);
    }

    @After
    public void tearDown() {
        altar.setDebugAllUnlocked(false);
        BiomePortal.DEBUG_UNLOCK_ALL = false;
    }

    @Test
    public void testDebugAllUnlockedTogglesAllStations() {
        assertFalse(altar.isDebugAllUnlocked());

        altar.setDebugAllUnlocked(true);
        assertTrue(altar.isDebugAllUnlocked());

        for (ShelterAltar.Station station : ShelterAltar.Station.values()) {
            assertTrue("Station " + station.name() + " should be available when debug unlock is ON",
                    altar.hasStation(station));
        }

        assertTrue("Skill tree should be unlocked via debug", altar.isSkillTreeUnlocked());

        // Toggle back off
        altar.setDebugAllUnlocked(false);
        assertFalse(altar.isDebugAllUnlocked());
    }

    @Test
    public void testDebugAllUnlockedMaxesAltarTiersAndAscension() {
        altar.setDebugAllUnlocked(true);

        for (ShelterAltar.Tree tree : ShelterAltar.Tree.values()) {
            if (tree != ShelterAltar.Tree.ASCENSION) {
                assertEquals("Tree " + tree + " should be max tier",
                        ShelterAltar.MAX_TIER, altar.getTier(tree));
            }
        }

        for (ShelterAltar.StatType stat : ShelterAltar.StatType.values()) {
            assertEquals("Ascension stat " + stat + " should be max tier (5)",
                    ShelterAltar.MAX_ASCENSION_TIER, altar.getAscensionTier(stat));
        }

        altar.setDebugAllUnlocked(false);
    }
}
