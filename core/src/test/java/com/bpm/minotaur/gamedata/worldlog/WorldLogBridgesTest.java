package com.bpm.minotaur.gamedata.worldlog;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.history.OldGod;
import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** "World Log: Bridges and Reckonings" (#290): lives grouped by Bridge, each closed by a Reckoning. */
public class WorldLogBridgesTest {

    private static void live(WorldLog log, String name, int hunger) {
        log.wake(new Life(name, 1, "the Salt Wells", Allegiance.REFUGEE_COUNCIL, Life.Origin.TITHED,
                null, "debt", "the Refugee Council"), hunger);
        log.note(Fact.of(Fact.Kind.SWORE_OATH, "House Gall"));
        log.die("Slain", "a Kobold", "level 2", 10);
    }

    private static WorldLog twoBridges() {
        WorldLog log = new WorldLog(3L);
        live(log, "A", 0);
        live(log, "B", 12);
        Reckoning r = Reckonings.wardenFell("the First Bridge", log.last());
        log.closeBridge(Reckoning.End.WARDEN_FELL, r);
        live(log, "C", 0);
        live(log, "D", 3);
        return log;
    }

    @Test
    public void livesGroupByBridgeThenEraAndEachBridgeIsNamed() {
        WorldLog log = twoBridges();
        List<Bridge> bridges = log.bridges();
        assertEquals(2, bridges.size());
        assertEquals("the Second Bridge", bridges.get(0).name);
        assertEquals("the First Bridge", bridges.get(1).name);
        assertNull("the Bridge being built has no end yet", bridges.get(0).reckoning);
        assertEquals(Reckoning.End.WARDEN_FELL, bridges.get(1).reckoning.end);
        assertEquals(1, bridges.get(0).eras.size());
        assertEquals(2, bridges.get(1).eras.size());
        assertEquals("D", bridges.get(0).eras.get(0).lives.get(0).name);
        assertNotEquals("each Bridge names its own Eras",
                bridges.get(0).eras.get(0).name, bridges.get(1).eras.get(1).name);
        assertEquals(1, log.currentBridge());
    }

    @Test
    public void theBridgesSurviveTheSave() {
        WorldLog log = twoBridges();
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        WorldLog loaded = json.fromJson(WorldLog.class, json.toJson(log));
        assertEquals(1, loaded.currentBridge());
        assertEquals(log.bridges().get(1).reckoning.plain, loaded.bridges().get(1).reckoning.plain);
        assertEquals(log.bridges().get(0).eras.get(0).name, loaded.bridges().get(0).eras.get(0).name);
    }

    @Test
    public void aLifeFromBeforeBridgesStandsInTheFirst() {
        WorldLog log = new WorldLog(3L);
        live(log, "A", 0);
        log.last().bridge = -1;
        assertEquals(0, log.last().bridgeIndex());
        assertEquals(1, log.bridges().size());
    }

    @Test
    public void eachReckoningTellsItsFactsPlainlyAndMythically() {
        WorldLog log = twoBridges();
        LifeRecord last = log.last();
        Reckoning fell = Reckonings.wardenFell("the Second Bridge", last);
        Reckoning crossed = Reckonings.crossed("the Second Bridge", OldGod.IXXATH, "House Gall", last);
        Reckoning final_ = Reckonings.lastCrossing("the Second Bridge", "House Gall", last);
        for (Reckoning r : new Reckoning[]{fell, crossed, final_}) {
            for (String text : new String[]{r.plain, r.mythic}) {
                assertEquals(text, UiGlyphs.sanitize(text));
                assertTrue(text, text.toLowerCase().contains("bridge"));
            }
            assertNotEquals(r.plain, r.mythic);
        }
        assertTrue(fell.plain, fell.plain.contains("D"));
        assertTrue(crossed.plain, crossed.plain.contains("Ixxath") && crossed.plain.contains("House Gall"));
        assertTrue(crossed.mythic, crossed.mythic.contains("Ixxath"));
        assertTrue(final_.plain, final_.plain.contains("No old god"));
        assertEquals(Reckoning.End.LAST_CROSSING, final_.end);
        assertEquals(crossed.plain, Reckonings.crossed("the Second Bridge", OldGod.IXXATH, "House Gall", last).plain);
    }
}
