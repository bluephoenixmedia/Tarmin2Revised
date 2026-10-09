package com.bpm.minotaur.managers;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** Plan T2.7, D18: what a broken army leaves for a player who stayed. */
public class BattleSpoilsTest {

    private static Application mockApp;
    private static com.badlogic.gdx.Files mockFiles;
    private static ItemDataManager items;

    @BeforeClass
    public static void setUpGdx() {
        if (Gdx.app == null) {
            mockApp = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class},
                    (proxy, method, args) -> "getType".equals(method.getName()) ? Application.ApplicationType.HeadlessDesktop : null);
            Gdx.app = mockApp;
        }
        if (Gdx.files == null) {
            mockFiles = (com.badlogic.gdx.Files) Proxy.newProxyInstance(com.badlogic.gdx.Files.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Files.class},
                    (proxy, method, args) -> {
                        if ("local".equals(method.getName()) || "internal".equals(method.getName())) {
                            String path = (String) args[0];
                            java.io.File f = new java.io.File("assets/" + path);
                            if (!f.exists()) f = new java.io.File("../assets/" + path);
                            return new com.badlogic.gdx.files.FileHandle(f.exists() ? f : new java.io.File(path));
                        }
                        return null;
                    });
            Gdx.files = mockFiles;
        }
        items = new ItemDataManager();
        items.load();
        items.loadWeapons();
        items.loadArmor();
    }

    @AfterClass
    public static void tearDownGdx() {
        if (Gdx.app == mockApp) Gdx.app = null;
        if (Gdx.files == mockFiles) Gdx.files = null;
    }

    private static int count(Maze maze, java.util.function.Predicate<Item> p) {
        int n = 0;
        for (Item i : maze.getItems().values()) if (p.test(i)) n++;
        return n;
    }

    @Test
    public void theFieldTheTrophiesAndALordsBlade() {
        Maze maze = new Maze(1, new int[36][36]);
        new BattleSpoils(items, null, 7L).drop(maze, new GridPoint2(18, 18), 3, true);

        int gear = count(maze, i -> (i.isWeapon() || i.isArmor()) && i.getEnchantment() < BattleSpoils.LORD_ENCHANTMENT);
        assertTrue("arms of the fallen: " + gear, gear >= BattleSpoils.FIELD_MIN);
        assertEquals(1, count(maze, i -> i.getType() == Item.ItemType.SIGNET_RING));
        assertEquals(1, count(maze, i -> i.getType() == Item.ItemType.TORN_BANNER));
        assertEquals("the fallen lord's own blade", 1,
                count(maze, i -> i.isWeapon() && i.getEnchantment() >= BattleSpoils.LORD_ENCHANTMENT));
    }

    @Test
    public void noLordNoBlade() {
        Maze maze = new Maze(1, new int[36][36]);
        new BattleSpoils(items, null, 8L).drop(maze, new GridPoint2(18, 18), 3, false);
        assertEquals(0, count(maze, i -> i.isWeapon() && i.getEnchantment() >= BattleSpoils.LORD_ENCHANTMENT));
    }

    @Test
    public void aSignetIsATrophyThatTellsItsStoryOnce() {
        Item ring = items.createItem(Item.ItemType.SIGNET_RING, 0, 0, com.bpm.minotaur.gamedata.item.ItemColor.YELLOW, null);
        assertTrue(ring.isTrophy());
        assertFalse("kept, not consumed", ring.isChronicleFragment());
        assertNotNull(ring.fragmentKind());
        assertFalse(ring.isTrophyRead());
        ring.markTrophyRead();
        assertTrue(ring.isTrophyRead());
        assertTrue("worth coin", items.getTemplate(Item.ItemType.SIGNET_RING).baseValue >= 100);
    }
}
