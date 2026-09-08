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

    @Test
    public void testItemDataManagerMissingTemplateFallback() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }

        ItemDataManager idm = new ItemDataManager();
        // Even without loading full json, getTemplate for missing type should generate a fallback instead of throwing NPE
        com.bpm.minotaur.gamedata.item.ItemTemplate template = idm.getTemplate(ItemType.LOST_DIVINITIES);
        assertNotNull(template);
        assertEquals("LOST_DIVINITIES", template.friendlyName);
    }
}
