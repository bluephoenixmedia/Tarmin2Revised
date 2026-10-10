package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.managers.HistoryManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Living War W2-W8: the surface war is always somewhere near, and comes to the player on schedule. */
public class EncounterSchedulerTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static HistoryWorld atWar(long seed) {
        return HistoryManager.create(seed, catalog).world();
    }

    /** The player wanders the overland, a chunk every 30 turns, never far from home. */
    private static GridPoint2 walk(long turn) {
        long step = turn / 30;
        int x = (int) (Math.round(4 * Math.sin(step * 0.7)));
        int y = (int) (Math.round(4 * Math.cos(step * 0.45)));
        return new GridPoint2(x, y);
    }

    private static List<String> run(HistoryWorld world, EncounterLedger ledger, long from, long to) {
        List<String> seen = new ArrayList<>();
        for (long t = from; t < to; t++) {
            seen.add(t + ": " + EncounterScheduler.at(world, t, ledger, walk(t), true));
        }
        return seen;
    }

    @Test
    public void theSameSeedAndClockMakeTheSameWar() {
        HistoryWorld w = atWar(5L);
        assertEquals(run(w, new EncounterLedger(), 0, 1500), run(w, new EncounterLedger(), 0, 1500));
    }

    @Test
    public void aVisibleEncounterComesEveryEightyToOneHundredTwentyTurns() {
        HistoryWorld w = atWar(7L);
        EncounterLedger ledger = new EncounterLedger();
        Set<Long> starts = new LinkedHashSet<>();
        for (long t = 0; t < 2000; t++) {
            for (Encounter e : EncounterScheduler.at(w, t, ledger, walk(t), true)) {
                if (e.visible() && e.slot >= 0) starts.add(e.start);
            }
        }
        List<Long> s = new ArrayList<>(starts);
        assertTrue("about one per slot over 2000 turns: " + s.size(), s.size() >= 15);
        for (int i = 1; i < s.size(); i++) {
            long gap = s.get(i) - s.get(i - 1);
            assertTrue("a gap of " + gap + " turns", gap >= 80 && gap <= 120);
        }
    }

    @Test
    public void someWarIsAlwaysWithinEarshotWhileAWarIsOn() {
        HistoryWorld w = atWar(9L);
        EncounterLedger ledger = new EncounterLedger();
        for (long t = EncounterScheduler.SLOT + EncounterScheduler.JITTER; t < 2000; t++) {
            GridPoint2 me = walk(t);
            boolean heard = false;
            for (Encounter e : EncounterScheduler.at(w, t, ledger, me, true)) {
                if (e.fighting() && EncounterScheduler.distance(me, e.chunkAt(t)) <= EncounterScheduler.EARSHOT) heard = true;
            }
            assertTrue("silence at turn " + t, heard);
        }
    }

    @Test
    public void theFirstSkirmishComesNextToThePlayerWithinAHundredAndFiftyTurns() {
        for (long seed = 1; seed <= 20; seed++) {
            HistoryWorld w = atWar(seed);
            EncounterLedger ledger = new EncounterLedger();
            long clock = 1234;
            Encounter first = null;
            long at = -1;
            for (long t = clock; t < clock + 150 && first == null; t++) {
                for (Encounter e : EncounterScheduler.at(w, t, ledger, new GridPoint2(3, -2), true)) {
                    if (e.slot == -1) {
                        first = e;
                        at = t;
                    }
                }
            }
            assertNotNull("seed " + seed + ": no skirmish in the first 150 turns", first);
            assertEquals(Encounter.Kind.SKIRMISH, first.kind);
            assertEquals("next to the player", 1, EncounterScheduler.distance(new GridPoint2(3, -2), first.chunkAt(at)));
            for (long t = at; t < first.end + 5; t++) EncounterScheduler.at(w, t, ledger, new GridPoint2(3, -2), true);
            assertTrue(ledger.firstDone);
        }
    }

    @Test
    public void theFirstSkirmishWaitsForThePlayerToComeUp() {
        HistoryWorld w = atWar(11L);
        EncounterLedger ledger = new EncounterLedger();
        for (long t = 0; t < 500; t++) EncounterScheduler.at(w, t, ledger, new GridPoint2(0, 0), false);
        assertEquals("never scheduled underground", -1, ledger.firstAt);
    }

    @Test
    public void oneExpeditionBringsOnePitchedBattleAfterThreeEncounters() {
        HistoryWorld w = atWar(13L);
        EncounterLedger ledger = new EncounterLedger();
        int battles = 0;
        Set<Long> seen = new LinkedHashSet<>();
        for (long t = 0; t < 3000; t++) {
            for (Encounter e : EncounterScheduler.at(w, t, ledger, walk(t), true)) {
                if (e.kind == Encounter.Kind.BATTLE && seen.add(e.slot)) battles++;
            }
        }
        assertEquals(1, battles);
        ledger.newExpedition(true);
        for (long t = 3000; t < 6000; t++) {
            for (Encounter e : EncounterScheduler.at(w, t, ledger, walk(t), true)) {
                if (e.kind == Encounter.Kind.BATTLE && seen.add(e.slot)) battles++;
            }
        }
        assertEquals("one more after a sleep", 2, battles);
    }

    @Test
    public void aColumnMarchesAcrossChunksPastThePlayer() {
        HistoryWorld w = atWar(15L);
        EncounterLedger ledger = new EncounterLedger();
        for (long t = 0; t < 4000; t++) {
            for (Encounter e : EncounterScheduler.at(w, t, ledger, walk(t), true)) {
                if (e.kind != Encounter.Kind.COLUMN) continue;
                assertNotEquals(e.from, e.to);
                assertEquals(2 * EncounterScheduler.COLUMN_REACH, EncounterScheduler.distance(e.from, e.to));
                assertEquals(e.from, e.chunkAt(e.start));
                assertEquals(e.to, e.chunkAt(e.end));
                assertEquals(-1, e.houseB);
                return;
            }
        }
        fail("no column in 4000 turns");
    }

    @Test
    public void peaceIsNeverSilentWhereAGrudgeIsHeld() {
        for (long seed = 1; seed <= 200; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            if (!w.activeWars().isEmpty() || w.grudges().isEmpty()) continue;
            EncounterLedger ledger = new EncounterLedger();
            ledger.firstDone = true;
            int raids = 0;
            for (long t = 0; t < 3000; t++) {
                for (Encounter e : EncounterScheduler.at(w, t, ledger, walk(t), true)) {
                    assertEquals("only raids in peacetime", Encounter.Kind.RAID, e.kind);
                    assertEquals(-1, e.warId);
                    raids++;
                }
            }
            if (raids > 0) return;
        }
        fail("no peacetime world with a grudge ever raided");
    }

    @Test
    public void theSpentAreNotSeenAgain() {
        HistoryWorld w = atWar(17L);
        EncounterLedger ledger = new EncounterLedger();
        for (long t = 0; t < 2000; t++) {
            for (Encounter e : EncounterScheduler.at(w, t, ledger, walk(t), true)) {
                if (!e.visible()) continue;
                ledger.spend(e);
                for (Encounter again : EncounterScheduler.at(w, t, ledger, walk(t), true)) {
                    assertNotEquals(e.key(), again.key());
                }
                return;
            }
        }
        fail("nothing to spend");
    }

    @Test
    public void everyWarHasACampOnEachSide() {
        HistoryManager h = HistoryManager.create(19L, catalog);
        HistoryWorld w = h.world();
        SeatMap seats = SeatMap.of(w, new com.bpm.minotaur.generation.ShelterRoads(19L, new GridPoint2(0, 30)),
                new GridPoint2(0, 30), 19L);
        List<Encounter> camps = EncounterScheduler.camps(w, seats);
        assertFalse(camps.isEmpty());
        for (Encounter c : camps) {
            assertEquals(Encounter.Kind.CAMP, c.kind);
            assertTrue(c.activeAt(123456L));
        }
    }
}
