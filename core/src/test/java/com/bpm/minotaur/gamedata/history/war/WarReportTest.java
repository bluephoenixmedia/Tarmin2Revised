package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.CasusBelli;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** Living War W27, W30: what the War Table reads, and the HUD's tug-of-war. */
public class WarReportTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void everyWarUnderWayIsOnTheTableWithItsBattlesTallied() {
        HistoryManager h = HistoryManager.create(51L, catalog);
        for (int i = 0; i < 12; i++) h.onSleep();
        HistoryWorld w = h.world();
        List<WarReport.WarLine> lines = WarReport.wars(w, null, null);
        assertEquals(w.activeWars().size(), lines.size());
        for (WarReport.WarLine line : lines) {
            War war = null;
            for (War x : w.activeWars()) if (x.id == line.warId) war = x;
            int a = 0, b = 0;
            for (HistoryEvent e : w.events()) {
                if (e.type != EventType.BATTLE || e.causeEventId != war.declaredEventId) continue;
                if (e.houseA == war.attackerId) a++;
                if (e.houseA == war.defenderId) b++;
            }
            assertEquals(a, line.winsA);
            assertEquals(b, line.winsB);
            assertFalse("never an enum name", line.cause.contains("_"));
            assertTrue(line.sinceYear > 0);
        }
    }

    @Test
    public void everyCauseHasWords() {
        for (CasusBelli cb : CasusBelli.values()) {
            String said = WarReport.cause(cb);
            assertFalse(cb + " reads as " + said, said.isEmpty() || said.contains("_") || said.equals(cb.name()));
        }
    }

    @Test
    public void aFrontIsGivenAsABearing() {
        assertEquals("here", WarReport.bearing(new GridPoint2(2, 2), new GridPoint2(2, 2)));
        assertEquals("3 chunks north", WarReport.bearing(new GridPoint2(0, 0), new GridPoint2(0, 3)));
        assertEquals("1 chunk west", WarReport.bearing(new GridPoint2(0, 0), new GridPoint2(-1, 0)));
        assertEquals("4 chunks south-east", WarReport.bearing(new GridPoint2(0, 0), new GridPoint2(4, -4)));
    }

    @Test
    public void aWeakGashHolderAtWarIsAtRisk() {
        HistoryManager h = HistoryManager.create(53L, catalog);
        HistoryWorld w = h.world();
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            if (w.activeWarCount(w.gashHolder(g).id) == 0) continue;
            w.gashHolder(g).strength = 12f;
            boolean listed = false;
            for (WarReport.SeatAtRisk s : WarReport.seatsAtRisk(w)) if (s.gash.equals(w.gashName(g))) listed = true;
            assertTrue(listed);
            return;
        }
    }

    @Test
    public void theTallySharesTheGround() {
        assertEquals(0.5f, new WarTally(1, 2, 0, 0).shareA(), 0f);
        assertEquals(0.75f, new WarTally(1, 2, 6, 2).shareA(), 0.001f);
        assertEquals(0f, new WarTally(1, 2, 0, 5).shareA(), 0f);
    }
}
