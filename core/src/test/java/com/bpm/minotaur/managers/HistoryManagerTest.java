package com.bpm.minotaur.managers;

import com.badlogic.gdx.utils.Json;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.save.WorldSaveData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** Plan T1.2 (replay save), T1.6 (live seasons), T1.7 (player deeds). */
public class HistoryManagerTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void sleepingAdvancesExactlyOneSeasonAndReportsItsNews() {
        HistoryManager m = HistoryManager.create(11L, catalog);
        int seasonBefore = m.world().season();
        int eventsBefore = m.world().events().size();

        List<HistoryEvent> news = m.onSleep();

        assertEquals(seasonBefore + 1, m.world().season());
        assertEquals(1, m.world().liveSeasons());
        assertEquals(m.world().events().subList(eventsBefore, m.world().events().size()), news);
    }

    @Test
    public void sameSeedAndSameDeedsMakeTheSameHistory() {
        HistoryManager a = HistoryManager.create(5L, catalog);
        HistoryManager b = HistoryManager.create(5L, catalog);
        for (int i = 0; i < 6; i++) {
            if (i == 2) {
                a.recordSeekerFell(-1);
                b.recordSeekerFell(-1);
            }
            a.onSleep();
            b.onSleep();
        }
        assertEquals(a.world().fingerprint(), b.world().fingerprint());
    }

    @Test
    public void savingAndLoadingReproducesTheWorldAndItsFuture() {
        HistoryManager live = HistoryManager.create(77L, catalog);
        live.onSleep();
        live.recordKill(live.world().lordOf(live.world().gashHolder(1)).id);
        live.onSleep();
        live.recordSeekerFell(live.world().gashHolder(0).id);
        live.onSleep();
        live.unlock(3);

        WorldSaveData save = new WorldSaveData();
        save.history = live.toSave();
        Json json = new Json();
        WorldSaveData reloaded = json.fromJson(WorldSaveData.class, json.toJson(save));

        // The world seed has been re-rolled by a death since; the history keeps its own.
        HistoryManager restored = HistoryManager.fromSave(-1L, reloaded.history, catalog);
        assertEquals(live.world().fingerprint(), restored.world().fingerprint());
        assertTrue(restored.isUnlocked(3));

        live.onSleep();
        restored.onSleep();
        assertEquals(live.world().fingerprint(), restored.world().fingerprint());
    }

    @Test
    public void slayingAGashLordIsRecordedAndHisHouseSucceedsHimNextSeason() {
        HistoryManager m = HistoryManager.create(3L, catalog);
        House holder = m.world().gashHolder(0);
        Figure lord = m.world().lordOf(holder);

        m.recordKill(lord.id);

        assertFalse(lord.isAlive());
        assertEquals(Figure.Fate.PLAYER, lord.fate);
        HistoryEvent last = m.world().events().get(m.world().events().size() - 1);
        assertEquals(EventType.SLAIN_BY_PLAYER, last.type);
        assertEquals(lord.id, last.figureB);

        List<HistoryEvent> news = m.onSleep();
        boolean resolved = false;
        for (HistoryEvent e : news) {
            boolean succession = e.type == EventType.SUCCESSION || e.type == EventType.DISPUTED_SUCCESSION
                    || e.type == EventType.USURPATION || e.type == EventType.HOUSE_EXTINGUISHED;
            if (succession && e.houseA == holder.id) resolved = true;
        }
        assertTrue("the house answered its lord's death", resolved);
        Figure heir = m.world().lordOf(m.world().gashHolder(0));
        assertTrue(heir.isAlive());
        assertNotEquals(lord.id, heir.id);
    }

    @Test
    public void aFallenSeekerIsChronicledAndHonoursTheHouseThatKilledThem() {
        HistoryManager m = HistoryManager.create(9L, catalog);
        House killer = m.world().gashHolder(2);
        int prestige = killer.prestige;

        m.recordSeekerFell(killer.id);
        m.recordSeekerFell(-1);

        assertEquals(2, m.world().seekersFallen());
        assertEquals(prestige + 3, killer.prestige);
        HistoryEvent first = m.world().events().get(m.world().events().size() - 2);
        assertEquals(EventType.SEEKER_FELL, first.type);
        assertEquals(killer.id, first.houseA);
        assertEquals(1, first.detail);
    }

    @Test
    public void readingAFragmentUnlocksAFittingEventAndItsCause() {
        HistoryManager m = HistoryManager.create(21L, catalog);
        for (com.bpm.minotaur.gamedata.history.FragmentKind kind : com.bpm.minotaur.gamedata.history.FragmentKind.values()) {
            HistoryEvent e = m.readFragment(kind);
            assertNotNull(kind.name(), e);
            assertTrue(kind + " cannot tell of " + e.type, kind.tells(e.type));
            assertTrue(m.isUnlocked(e.id));
            if (e.causeEventId >= 0) assertTrue("its cause comes with it", m.isUnlocked(e.causeEventId));
        }
    }

    @Test
    public void fragmentsNeverRepeatAndRunOutGracefully() {
        HistoryManager m = HistoryManager.create(22L, catalog);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        HistoryEvent e;
        while ((e = m.readFragment(com.bpm.minotaur.gamedata.history.FragmentKind.PROCLAMATION)) != null) {
            assertTrue("repeated " + e, seen.add(e.id));
        }
        assertFalse(seen.isEmpty());
    }

    @Test
    public void theSameReadingsUnlockTheSameHistoryAfterALoad() {
        HistoryManager a = HistoryManager.create(23L, catalog);
        a.readFragment(com.bpm.minotaur.gamedata.history.FragmentKind.PAGE);
        a.readFragment(com.bpm.minotaur.gamedata.history.FragmentKind.BANNER);
        HistoryManager b = HistoryManager.fromSave(0L, a.toSave(), catalog);
        assertEquals(a.unlockedEvents(), b.unlockedEvents());
        assertEquals(a.readFragment(com.bpm.minotaur.gamedata.history.FragmentKind.PAGE).id,
                b.readFragment(com.bpm.minotaur.gamedata.history.FragmentKind.PAGE).id);
    }

    @Test
    public void eachNewDoomStageIsTarminZulsAscendancyAndIsChronicledOnce() {
        HistoryManager m = HistoryManager.create(31L, catalog);
        int before = m.world().events().size();
        m.noteDoomStage(1);
        assertEquals("stage one is the quiet; nothing to tell", before, m.world().events().size());

        m.noteDoomStage(2);
        m.noteDoomStage(2);
        m.noteDoomStage(1);
        assertEquals(1, count(m, EventType.TARMIN_ASCENDANT));

        m.noteDoomStage(3);
        assertEquals(2, count(m, EventType.TARMIN_ASCENDANT));
        HistoryEvent last = m.world().events().get(m.world().events().size() - 1);
        assertEquals("at the third stage a house is made to kneel", EventType.VASSAL_OATH, last.type);
        assertEquals(m.world().tarminHouse().id, last.houseB);
        assertEquals(1, last.detail);
    }

    @Test
    public void ascendancySurvivesALoad() {
        HistoryManager a = HistoryManager.create(32L, catalog);
        a.noteDoomStage(3);
        a.onSleep();
        HistoryManager b = HistoryManager.fromSave(0L, a.toSave(), catalog);
        assertEquals(a.world().fingerprint(), b.world().fingerprint());
        b.noteDoomStage(3);
        assertEquals("already chronicled before the save", a.world().events().size(), b.world().events().size());
    }

    private static int count(HistoryManager m, EventType type) {
        int n = 0;
        for (HistoryEvent e : m.world().events()) if (e.type == type) n++;
        return n;
    }

    @Test
    public void aClockThatJumpsStagesStillChroniclesEachOne() {
        HistoryManager m = HistoryManager.create(33L, catalog);
        m.noteDoomStage(4);
        assertEquals(3, count(m, EventType.TARMIN_ASCENDANT));
    }
}
