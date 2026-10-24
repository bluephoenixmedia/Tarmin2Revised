package com.bpm.minotaur.gamedata.worldlog;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.history.AgeEnding;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** "Ages, Tarmin slain and the throne" (#291): the World Log's largest division. */
public class WorldLogAgesTest {

    private static void live(WorldLog log, String name, int hunger) {
        log.wake(new Life(name, 1, "the Salt Wells", Allegiance.REFUGEE_COUNCIL, Life.Origin.TITHED,
                null, "debt", "the Refugee Council"), hunger);
        log.note(Fact.of(Fact.Kind.SWORE_OATH, "House Gall"));
        log.die("Slain", "a Kobold", "level 2", 10);
    }

    private static AgeEnding throne() throws IOException {
        HistoryManager m = HistoryManager.create(11L, DoctrineCatalogTest.loadCatalog());
        return m.recordTarminSlain(true, "B");
    }

    @Test
    public void bridgesGroupIntoAgesEachClosedByItsReckoning() throws IOException {
        WorldLog log = new WorldLog(3L);
        live(log, "A", 0);
        log.closeBridge(Reckonings.wardenFell(Bridge.nameOf(log.currentBridge()), log.last()));
        live(log, "B", 0);
        log.closeAge(Reckonings.ageEnded(Age.nameOf(log.currentAge()), log.last(), throne()));
        live(log, "C", 0);

        List<Age> ages = log.ages();
        assertEquals(2, ages.size());
        assertEquals("the Second Age", ages.get(0).name);
        assertEquals("the First Age", ages.get(1).name);
        assertNull(ages.get(0).reckoning);
        assertNotNull(ages.get(1).reckoning);
        assertEquals("the first Age held two Bridges", 2, ages.get(1).bridges.size());
        assertEquals(1, ages.get(0).bridges.size());
        assertEquals("C", ages.get(0).bridges.get(0).eras.get(0).lives.get(0).name);
        assertEquals(1, log.currentAge());
        assertEquals("an Age's end closes its Bridge too", 3, log.bridges().size());
    }

    @Test
    public void theAgesSurviveTheSave() throws IOException {
        WorldLog log = new WorldLog(3L);
        live(log, "A", 0);
        log.closeAge(Reckonings.ageEnded(Age.nameOf(log.currentAge()), log.last(), throne()));
        live(log, "B", 0);
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        WorldLog loaded = json.fromJson(WorldLog.class, json.toJson(log));
        assertEquals(1, loaded.currentAge());
        assertEquals(log.ages().get(1).reckoning.plain, loaded.ages().get(1).reckoning.plain);
        assertEquals(2, loaded.ages().size());
    }

    @Test
    public void everyEndOfAnAgeIsToldBothWaysAndNamesTheNewTarmin() throws IOException {
        HistoryManager m = HistoryManager.create(11L, DoctrineCatalogTest.loadCatalog());
        WorldLog log = new WorldLog(3L);
        live(log, "Brannoc Thatch", 0);
        AgeEnding returned = m.recordTarminSlain(false, "Brannoc Thatch");
        AgeEnding throne = m.recordTarminSlain(true, "Brannoc Thatch");
        for (AgeEnding e : new AgeEnding[]{returned, throne}) {
            Reckoning r = Reckonings.ageEnded("the First Age", log.last(), e);
            for (String text : new String[]{r.plain, r.mythic}) {
                assertEquals(text, UiGlyphs.sanitize(text));
                assertTrue(text, text.contains(e.newTitle));
            }
            assertNotEquals(r.plain, r.mythic);
        }
        assertTrue(Reckonings.ageEnded("the First Age", log.last(), returned).mythic
                .contains("returned the Treasure to the Rift, and was allowed to die"));
    }
}
