package com.bpm.minotaur.gamedata.worldlog;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** "World Log: the Ledger screen" (#281): lives grouped by Era, each Era named once. */
public class WorldLogErasTest {

    private static Life prisoner(String name) {
        return new Life(name, 1, "the Salt Wells", Allegiance.REFUGEE_COUNCIL, Life.Origin.TITHED,
                null, "debt", "the Refugee Council");
    }

    /** Wakes a life at {@code hunger} with one line, so the next waking starts a new life. */
    private static void live(WorldLog log, String name, int hunger) {
        log.wake(prisoner(name), hunger);
        log.note(Fact.of(Fact.Kind.SWORE_OATH, "House Gall"));
        log.die("Slain", "a Kobold", "level 2", 10);
    }

    @Test
    public void livesAreGroupedByTheHungerTheyWokeAtNewestFirst() {
        WorldLog log = new WorldLog(1L);
        live(log, "A", 3);
        live(log, "B", 9);
        live(log, "C", 10);
        live(log, "D", 26);

        List<Era> eras = log.eras();
        assertEquals(3, eras.size());
        assertEquals(2, eras.get(0).stage);
        assertEquals(1, eras.get(1).stage);
        assertEquals(0, eras.get(2).stage);
        assertEquals("D", eras.get(0).lives.get(0).name);
        assertEquals("C", eras.get(1).lives.get(0).name);
        assertEquals("B", eras.get(2).lives.get(0).name);
        assertEquals("A", eras.get(2).lives.get(1).name);
    }

    @Test
    public void aLifeSavedBeforeErasTakesItsEraFromItsNumber() {
        WorldLog log = WorldLog.upgradedFrom(11, 0);
        log.wake(prisoner("Old"));
        assertEquals("life 12 woke after eleven deaths", 1, log.eras().get(0).stage);
    }

    @Test
    public void eachEraIsNamedOnceAndKeepsItsName() {
        WorldLog log = new WorldLog(7L);
        live(log, "A", 0);
        live(log, "B", 12);
        live(log, "C", 30);
        live(log, "D", 45);

        Set<String> names = new HashSet<>();
        for (Era era : log.eras()) {
            assertFalse(era.name.isEmpty());
            assertEquals(era.name, UiGlyphs.sanitize(era.name));
            names.add(era.name);
        }
        assertEquals("each stage has its own name", 4, names.size());

        WorldLog twin = new WorldLog(7L);
        live(twin, "A", 0);
        assertEquals("the same seed names the same Era", log.eras().get(3).name, twin.eras().get(0).name);

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        WorldLog loaded = json.fromJson(WorldLog.class, json.toJson(log));
        for (int i = 0; i < 4; i++) assertEquals(log.eras().get(i).name, loaded.eras().get(i).name);
    }
}
