package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.managers.DivinityManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ShelterCartographerDeskTest {

    private ShelterAltar altar;
    private DivinityManager divinityManager;

    @Before
    public void setUp() {
        altar = ShelterAltar.getInstance();
        altar.reset();
        altar.setDebugAllUnlocked(false);
        divinityManager = DivinityManager.getInstance();
        divinityManager.resetForNewGame();
    }

    @After
    public void tearDown() {
        altar.reset();
        altar.setDebugAllUnlocked(false);
        divinityManager.resetForNewGame();
    }

    @Test
    public void testStationDefinition() {
        ShelterAltar.Station desk = ShelterAltar.Station.CARTOGRAPHER_DESK;
        assertNotNull(desk);
        assertEquals(20, desk.getCost());
        assertEquals(Item.ItemType.HOME_CARTOGRAPHER_DESK, desk.getItemType());
    }

    @Test
    public void testInitialAndUnlockState() {
        assertFalse(altar.hasStation(ShelterAltar.Station.CARTOGRAPHER_DESK));
        assertEquals(0, altar.getCartographerSurveyRadius());

        altar.setStationUnlocked(ShelterAltar.Station.CARTOGRAPHER_DESK, true);
        assertTrue(altar.hasStation(ShelterAltar.Station.CARTOGRAPHER_DESK));
        assertEquals(1, altar.getCartographerDeskTier());
        assertEquals(1, altar.getCartographerSurveyRadius());
    }

    @Test
    public void testUpgradeProgressionTier2AndTier3() {
        altar.setStationUnlocked(ShelterAltar.Station.CARTOGRAPHER_DESK, true);
        assertEquals(4, altar.getCartographerUpgradeCrestCost());
        assertEquals(10, altar.getCartographerUpgradeDivinityCost());

        // Insufficient funds
        assertFalse(altar.canUpgradeCartographerDesk());
        assertFalse(altar.upgradeCartographerDesk());

        // Add enough for Tier 2
        altar.addCrestsOfValor(4);
        divinityManager.addDivinities(10);
        assertTrue(altar.canUpgradeCartographerDesk());
        assertTrue(altar.upgradeCartographerDesk());

        assertEquals(2, altar.getCartographerDeskTier());
        assertEquals(2, altar.getCartographerSurveyRadius());
        assertEquals(0, altar.getCrestsOfValor());
        assertEquals(0, divinityManager.getDivinities());

        // Tier 3 costs
        assertEquals(8, altar.getCartographerUpgradeCrestCost());
        assertEquals(20, altar.getCartographerUpgradeDivinityCost());
        assertFalse(altar.canUpgradeCartographerDesk());

        altar.addCrestsOfValor(10);
        divinityManager.addDivinities(25);
        assertTrue(altar.canUpgradeCartographerDesk());
        assertTrue(altar.upgradeCartographerDesk());

        assertEquals(3, altar.getCartographerDeskTier());
        assertEquals(3, altar.getCartographerSurveyRadius());
        assertEquals(2, altar.getCrestsOfValor());
        assertEquals(5, divinityManager.getDivinities());

        // Max tier
        assertEquals(-1, altar.getCartographerUpgradeCrestCost());
        assertEquals(-1, altar.getCartographerUpgradeDivinityCost());
        assertFalse(altar.canUpgradeCartographerDesk());
        assertFalse(altar.upgradeCartographerDesk());
    }

    @Test
    public void testDebugAllUnlockedMaxesCartographerDesk() {
        altar.setDebugAllUnlocked(true);
        assertTrue(altar.hasStation(ShelterAltar.Station.CARTOGRAPHER_DESK));
        assertEquals(3, altar.getCartographerDeskTier());
        assertEquals(3, altar.getCartographerSurveyRadius());
    }
}
