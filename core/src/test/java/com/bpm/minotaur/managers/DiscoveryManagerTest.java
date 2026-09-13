package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies scroll identification is bound to a single source of truth for both
 * scroll systems in the game: the randomized SCROLL_A-H appearance pool, and
 * dedicated spell scrolls (SCROLL_FIREBALL, SCROLL_MAGIC_MISSILE, etc.) which
 * previously always revealed their real name regardless of identification.
 */
public class DiscoveryManagerTest {

    private DiscoveryManager discoveryManager;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
        discoveryManager = new DiscoveryManager(new GameEventManager());
        discoveryManager.initializeNewGame(java.util.List.of(ItemType.POTION_BLUE, ItemType.POTION_PINK));
    }

    @Test
    public void testDedicatedSpellScrollHidesItsNameUntilIdentified() {
        Item scroll = dedicatedSpellScroll(ItemType.SCROLL_MAGIC_MISSILE, "Spell Scroll (Magic Missile)");

        String unidentifiedName = discoveryManager.getDisplayName(scroll);
        assertFalse("An unidentified dedicated spell scroll must not reveal its spell name",
                unidentifiedName.toLowerCase().contains("magic missile"));
        assertTrue("Unidentified scrolls should show a cryptic rune label",
                unidentifiedName.startsWith("Scroll labeled "));

        discoveryManager.identifyDedicatedScroll(ItemType.SCROLL_MAGIC_MISSILE, "Magic Missile");

        String identifiedName = discoveryManager.getDisplayName(scroll);
        assertEquals("Spell Scroll (Magic Missile)", identifiedName);
    }

    @Test
    public void testDedicatedScrollRuneLabelIsDeterministicAcrossCalls() {
        String first = discoveryManager.getScrollRuneLabel(ItemType.SCROLL_FIREBALL);
        String second = discoveryManager.getScrollRuneLabel(ItemType.SCROLL_FIREBALL);
        assertEquals("The same unidentified scroll type must always show the same rune label",
                first, second);

        String otherType = discoveryManager.getScrollRuneLabel(ItemType.SCROLL_LIGHTNING_BOLT);
        assertNotEquals("Different scroll types should not collide on the same rune label",
                first, otherType);
    }

    @Test
    public void testAppearanceScrollStillUsesItsOwnRandomizedRune() {
        // SCROLL_A is part of the randomized appearance pool set up by initializeNewGame.
        Item scroll = dedicatedSpellScroll(ItemType.SCROLL_A, "Labeled Scroll");
        String name = discoveryManager.getDisplayName(scroll);
        assertTrue(name.startsWith("Scroll labeled "));
    }

    private Item dedicatedSpellScroll(ItemType type, String friendlyName) {
        ItemTemplate template = new ItemTemplate();
        template.friendlyName = friendlyName;
        template.isUsable = true;
        return Item.fromTemplate(type, template);
    }
}
