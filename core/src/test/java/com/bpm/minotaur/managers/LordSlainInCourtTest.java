package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.FragmentKind;
import com.bpm.minotaur.gamedata.history.HistoryEvent;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.text.Headlines;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * A seal lord can die in its own court at a rival's hand before the seeker ever reaches it: the
 * history remembers who did it, the house mourns and crowns an heir, and the slayer walks off with
 * the seal. Emergent, and wanted.
 */
public class LordSlainInCourtTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static House greatHouse(HistoryManager h) {
        return h.world().gashHolder(0);
    }

    private static House rivalOf(HistoryManager h, House house) {
        for (House r : h.world().livingHouses()) if (r.id != house.id) return r;
        throw new AssertionError();
    }

    @Test
    public void theLordDiesInTheHistoryAtTheRivalsHandAndItIsNews() {
        HistoryManager h = HistoryManager.create(51L, catalog);
        House house = greatHouse(h);
        House rival = rivalOf(h, house);
        Figure lord = h.world().lordOf(house);

        h.recordLordSlainInCourt(lord.id, rival.id);

        assertFalse(lord.isAlive());
        assertEquals(Figure.Fate.BATTLE, lord.fate);
        HistoryEvent e = h.world().events().get(h.world().events().size() - 1);
        assertEquals(EventType.LORD_SLAIN_IN_COURT, e.type);
        assertEquals(rival.id, e.houseA);
        assertEquals(house.id, e.houseB);
        assertEquals(lord.id, e.figureB);
        assertEquals(0, e.gashIndex);
        assertTrue("headline news", Headlines.pick(Collections.singletonList(e), 3).contains(e));
        assertTrue("and told in the pages", FragmentKind.PAGE.tells(EventType.LORD_SLAIN_IN_COURT));
    }

    @Test
    public void theHouseCrownsAnHeirAndTheDeathReplaysFromTheSave() {
        HistoryManager h = HistoryManager.create(52L, catalog);
        House house = greatHouse(h);
        Figure lord = h.world().lordOf(house);
        h.recordLordSlainInCourt(lord.id, -1);
        h.onSleep();
        Figure heir = h.world().lordOf(house);
        assertTrue("an heir, or the house is gone", house.isExtinct() || (heir != null && heir.isAlive() && heir.id != lord.id));

        HistoryManager loaded = HistoryManager.fromSave(0L, h.toSave(), catalog);
        assertEquals(h.world().fingerprint(), loaded.world().fingerprint());
        assertFalse(loaded.world().figure(lord.id).isAlive());
    }

    @Test
    public void aDeadLordCannotDieTwice() {
        HistoryManager h = HistoryManager.create(53L, catalog);
        Figure lord = h.world().lordOf(greatHouse(h));
        h.recordLordSlainInCourt(lord.id, -1);
        int events = h.world().events().size();
        h.recordLordSlainInCourt(lord.id, -1);
        assertEquals(events, h.world().events().size());
    }
}
