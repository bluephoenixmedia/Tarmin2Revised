package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T4.5, D39: a megabeast can be slain, or its peace bought with a trophy laid before it. */
public class MegabeastOfferingTest {

    private static DoctrineCatalog catalog;
    private static ItemDataManager items;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
        BattleSpoilsTest.setUpGdx();
        items = new ItemDataManager();
        items.load();
    }

    @org.junit.AfterClass
    public static void tearDown() {
        BattleSpoilsTest.tearDownGdx();
    }

    private static Megabeast living(HistoryManager h) {
        for (Megabeast b : h.world().megabeasts()) if (b.isAlive()) return b;
        throw new AssertionError("no living beast");
    }

    @Test
    public void aPacifiedBeastIsChronicledAndStaysSoAfterALoad() {
        HistoryManager h = HistoryManager.create(4L, catalog);
        Megabeast b = living(h);
        assertFalse(b.isPacified());
        h.recordMegabeastPacified(b.id);
        assertTrue(b.isPacified());
        assertTrue("pacified, not dead", b.isAlive());
        HistoryEvent last = h.world().events().get(h.world().events().size() - 1);
        assertEquals(EventType.MEGABEAST_PACIFIED, last.type);
        assertEquals(b.id, last.beastId);

        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertTrue(loaded.world().megabeast(b.id).isPacified());
    }

    @Test
    public void strikingABoughtBeastVoidsTheBargainForGood() {
        HistoryManager h = HistoryManager.create(4L, catalog);
        Megabeast b = living(h);
        h.recordMegabeastPacified(b.id);
        h.breakMegabeastPeace(b.id);
        assertFalse(b.isPacified());
        assertFalse("after a load too", HistoryManager.fromSave(0L, h.toSave(), catalog).world().megabeast(b.id).isPacified());
    }

    @Test
    public void aBeastIsBoughtOffOnceAndTheDeadCannotBe() {
        HistoryManager h = HistoryManager.create(4L, catalog);
        Megabeast b = living(h);
        h.recordMegabeastPacified(b.id);
        int events = h.world().events().size();
        h.recordMegabeastPacified(b.id);
        assertEquals("only once", events, h.world().events().size());

        Megabeast other = null;
        for (Megabeast m : h.world().megabeasts()) if (m.isAlive() && m.id != b.id) other = m;
        if (other == null) return;
        h.recordMegabeastSlain(other.id);
        h.recordMegabeastPacified(other.id);
        assertFalse(other.isPacified());
    }

    @Test
    public void aTrophyLaidBeforeTheBeastIsTakenAndNothingElseIs() {
        Maze maze = new Maze(3, new int[36][36]);
        GridPoint2 beast = new GridPoint2(18, 18);
        Item bread = items.createItem(Item.ItemType.SMALL_RING, 19, 18, ItemColor.YELLOW, null);
        maze.addItem(bread);
        assertNull("only a trophy of the houses will do", BeastForge.takeOffering(maze, beast));

        Item far = items.createItem(Item.ItemType.TORN_BANNER, 18 + BeastForge.OFFERING_REACH + 1, 18, ItemColor.TAN, null);
        maze.addItem(far);
        assertNull("laid before it, not across the hall", BeastForge.takeOffering(maze, beast));

        Item ring = items.createItem(Item.ItemType.SIGNET_RING, 18, 20, ItemColor.YELLOW, null);
        maze.addItem(ring);
        assertSame(ring, BeastForge.takeOffering(maze, beast));
        assertFalse("it is taken", maze.getItems().containsValue(ring));
        assertTrue(maze.getItems().containsValue(bread));
    }
}
