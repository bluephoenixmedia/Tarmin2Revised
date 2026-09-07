package com.bpm.minotaur.startup;

import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.managers.SettingsManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class StartupOptimizationTest {

    @Test
    public void testSafeItemTypeLookupDoesNotThrow() {
        // Known types return enum
        assertEquals(ItemType.RUSTY_SWORD, ItemDataManager.getSafeItemType("RUSTY_SWORD"));
        assertEquals(ItemType.HOME_CHEST, ItemDataManager.getSafeItemType("HOME_CHEST"));
        assertEquals(ItemType.HOME_SLEEPING_BAG, ItemDataManager.getSafeItemType("HOME_SLEEPING_BAG"));

        // Unknown types return null safely without throwing IllegalArgumentException
        assertNull(ItemDataManager.getSafeItemType("NON_EXISTENT_WEAPON_9999"));
        assertNull(ItemDataManager.getSafeItemType(""));
        assertNull(ItemDataManager.getSafeItemType(null));
    }

    @Test
    public void testSkipIntroVideoSystemPropertyOverride() {
        // Without system property
        System.clearProperty("minotaur.skipIntro");
        SettingsManager settings = SettingsManager.getInstance();
        settings.setSkipIntroVideo(false);
        assertFalse(settings.isSkipIntroVideo());

        // With system property
        System.setProperty("minotaur.skipIntro", "true");
        assertTrue(settings.isSkipIntroVideo());

        // Cleanup
        System.clearProperty("minotaur.skipIntro");
    }
}
