package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.EventType;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.gamedata.history.war.Encounter;
import com.bpm.minotaur.gamedata.save.WorldSaveData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** Living War W3 and W12: a skirmish the player fought in moves the history a little, and the war's ledger is saved. */
public class SkirmishHistoryTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    @Test
    public void aSkirmishMovesALittleStrengthAndIsNotChronicled() {
        HistoryManager h = HistoryManager.create(31L, catalog);
        War w = h.world().activeWars().get(0);
        House a = h.world().house(w.attackerId);
        House d = h.world().house(w.defenderId);
        a.strength = 40f;
        d.strength = 40f;
        int events = h.world().events().size();
        h.recordSkirmish(a.id, d.id, -1);
        assertEquals(42f, a.strength, 0.001f);
        assertEquals(38f, d.strength, 0.001f);
        assertEquals("no captain fell: no chronicle", events, h.world().events().size());
    }

    @Test
    public void aCaptainFallingInItIsChronicledAsABattle() {
        HistoryManager h = HistoryManager.create(33L, catalog);
        War w = h.world().activeWars().get(0);
        Figure captain = null;
        for (Figure f : h.world().swornSwords(h.world().house(w.defenderId))) captain = f;
        assertNotNull(captain);
        h.recordSkirmish(w.attackerId, w.defenderId, captain.id);
        assertFalse(captain.isAlive());
        assertEquals(EventType.BATTLE, h.world().events().get(h.world().events().size() - 1).type);
        assertEquals(captain.id, h.world().events().get(h.world().events().size() - 1).figureB);
    }

    @Test
    public void theLedgerAndTheSkirmishSurviveTheSaveFile() {
        HistoryManager live = HistoryManager.create(35L, catalog);
        for (int i = 0; i < 400; i++) {
            live.tickWarClock();
            List<Encounter> now = live.encounters(new GridPoint2(i / 40, 0), true);
            for (Encounter e : now) if (e.visible() && i == 300) live.encounterLedger().spend(e);
        }
        War w = live.world().activeWars().get(0);
        live.recordSkirmish(w.defenderId, w.attackerId, -1);

        WorldSaveData save = new WorldSaveData();
        save.history = live.toSave();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        WorldSaveData reloaded = json.fromJson(WorldSaveData.class, json.toJson(save));
        HistoryManager restored = HistoryManager.fromSave(-1L, reloaded.history, catalog);

        assertEquals(live.world().fingerprint(), restored.world().fingerprint());
        assertEquals(live.encounterLedger().firstAt, restored.encounterLedger().firstAt);
        assertEquals(live.encounterLedger().spent, restored.encounterLedger().spent);
        assertEquals(live.encounters(new GridPoint2(9, 0), true).toString(),
                restored.encounters(new GridPoint2(9, 0), true).toString());
    }

    @Test
    public void anOlderSaveWithoutALedgerLoads() {
        HistoryManager live = HistoryManager.create(37L, catalog);
        com.bpm.minotaur.gamedata.history.HistorySaveData save = live.toSave();
        save.encounters = null;
        HistoryManager restored = HistoryManager.fromSave(-1L, save, catalog);
        assertNotNull(restored.encounterLedger());
        assertFalse(restored.encounterLedger().firstDone);
    }
}
