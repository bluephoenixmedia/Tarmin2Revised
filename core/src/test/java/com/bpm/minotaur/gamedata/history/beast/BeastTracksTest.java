package com.bpm.minotaur.gamedata.history.beast;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.Megabeast;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** Plan T3.3, D34: where a megabeast is, and how it follows the player. */
public class BeastTracksTest {

    private static DoctrineCatalog catalog;
    private static final long WORLD = 99L;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static Megabeast awake(HistoryWorld w) {
        for (Megabeast b : w.megabeasts()) if (b.isAlive() && b.isAwake(w.season())) return b;
        throw new AssertionError("no awake beast");
    }

    @Test
    public void aBeastRoamsNearItsLairAtItsLairsDepth() {
        HistoryWorld w = HistoryManager.create(1L, catalog).world();
        Megabeast b = awake(w);
        GridPoint2 lair = BeastTracks.lairChunk(WORLD, b);
        boolean moved = false;
        GridPoint2 first = BeastTracks.roamChunk(WORLD, b, 0);
        for (long clock = 0; clock < BeastTracks.ROAM_PERIOD; clock += 30) {
            GridPoint2 at = BeastTracks.roamChunk(WORLD, b, clock);
            assertTrue(Math.abs(at.x - lair.x) <= BeastTracks.ROAM_RADIUS && Math.abs(at.y - lair.y) <= BeastTracks.ROAM_RADIUS);
            if (!at.equals(first)) moved = true;
            assertSame(b, BeastTracks.presentAt(w, WORLD, clock, at, b.lairLevel, null));
            assertNotSame("not on another level", b, BeastTracks.presentAt(w, WORLD, clock, at, b.lairLevel + 1, null));
        }
        assertTrue(moved);
    }

    @Test
    public void aHuntingBeastFollowsDownALadderAfterADelay() {
        HistoryManager h = HistoryManager.create(2L, catalog);
        Megabeast b = awake(h.world());
        GridPoint2 chunk = BeastTracks.roamChunk(WORLD, b, 0);
        h.startHunt(b.id, chunk, b.lairLevel + 1);

        assertNull("not yet", BeastTracks.presentAt(h.world(), WORLD, h.warClock(), chunk, b.lairLevel + 1, h.hunt()));
        for (int i = 0; i < BeastTracks.HUNT_DELAY; i++) h.tickWarClock();
        assertSame("down the ladder after the player", b,
                BeastTracks.presentAt(h.world(), WORLD, h.warClock(), chunk, b.lairLevel + 1, h.hunt()));
        assertNull("and gone from where it was",
                BeastTracks.presentAt(h.world(), WORLD, h.warClock(), chunk, b.lairLevel, h.hunt()));

        for (int i = 0; i < BeastTracks.HUNT_LENGTH; i++) h.tickWarClock();
        assertNull("the hunt lapses", BeastTracks.presentAt(h.world(), WORLD, h.warClock(), chunk, b.lairLevel + 1, h.hunt()));
    }

    @Test
    public void aDeadBeastNeverComesBackEvenAfterALoad() {
        HistoryManager h = HistoryManager.create(3L, catalog);
        Megabeast b = awake(h.world());
        h.recordMegabeastSlain(b.id);
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        Megabeast again = loaded.world().megabeast(b.id);
        assertFalse(again.isAlive());
        for (long clock = 0; clock < BeastTracks.ROAM_PERIOD; clock += 25) {
            GridPoint2 at = BeastTracks.roamChunk(WORLD, again, clock);
            assertNull(BeastTracks.presentAt(loaded.world(), WORLD, clock, at, again.lairLevel, null));
        }
    }

    @Test
    public void woundsAndTheHuntSurviveALoad() {
        HistoryManager h = HistoryManager.create(4L, catalog);
        Megabeast b = awake(h.world());
        h.setBeastHp(b.id, 123);
        h.startHunt(b.id, new GridPoint2(3, -2), 4);
        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertEquals(123, loaded.beastHp(b.id, 999));
        assertEquals(999, loaded.beastHp(b.id + 100, 999));
        assertNotNull(loaded.hunt());
        assertEquals(b.id, loaded.hunt().beastId);
        assertEquals(4, loaded.hunt().level);
    }

    @Test
    public void aSleepingBeastIsNowhere() {
        HistoryManager h = HistoryManager.create(5L, catalog);
        for (Megabeast b : h.world().megabeasts()) {
            if (b.isAwake(h.world().season())) continue;
            GridPoint2 at = BeastTracks.roamChunk(WORLD, b, 0);
            assertNull(BeastTracks.presentAt(h.world(), WORLD, 0, at, b.lairLevel, null));
        }
    }
}
