package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterChoice;
import com.bpm.minotaur.gamedata.worldlog.WorldLog;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import static org.junit.Assert.*;

/** "Old gods: the layered reveal" (#288): what the soul learns, and when. */
public class RevealTest {

    @Test
    public void eachStageOfTheHungerOpensItsLayer() {
        assertEquals(Reveal.Layer.ARRIVAL, Reveal.layerFor(0, false));
        assertEquals(Reveal.Layer.ARRIVAL, Reveal.layerFor(9, false));
        assertEquals(Reveal.Layer.BRIDGE, Reveal.layerFor(10, false));
        assertEquals(Reveal.Layer.BRIDGE, Reveal.layerFor(24, false));
        assertEquals(Reveal.Layer.FIRST_LIFE, Reveal.layerFor(25, false));
        assertEquals(Reveal.Layer.FIRST_LIFE, Reveal.layerFor(49, false));
        assertEquals(Reveal.Layer.LAST_STAND, Reveal.layerFor(50, true));
        assertEquals("the Warden is the last stand, whatever the count", Reveal.Layer.LAST_STAND, Reveal.layerFor(3, true));
    }

    private static Encounter wyrm() {
        Encounter e = new Encounter();
        e.id = "EVENT_DRAGON_STATUE";
        e.title = "Shrine of the Ancient Wyrm";
        e.text = "A towering obsidian monument.";
        EncounterChoice pray = new EncounterChoice();
        pray.text = "Offer a prayer.";
        pray.type = EncounterChoice.CheckType.RISK_REWARD;
        e.choices.add(pray);
        return e;
    }

    @Test
    public void untilTheBridgeIsKnownAShrineIsOnlyAnOldShrine() {
        Encounter hidden = Shrine.view(wyrm(), OldGod.IXXATH, false, false);
        assertFalse(hidden.text, hidden.text.contains("Ixxath"));
        assertFalse(hidden.text, hidden.text.contains("Spirit-born"));
        assertTrue(hidden.text, hidden.text.contains("old shrines"));
        assertTrue(hidden.choices.get(0).text, hidden.choices.get(0).text.startsWith("The old rite: "));

        Encounter named = Shrine.view(wyrm(), OldGod.IXXATH, false, true);
        assertTrue(named.text.contains("Ixxath"));
        assertTrue(named.choices.get(0).text.startsWith("Ixxath's rite: "));
    }

    @Test
    public void revelationsComeInOrderEachAtItsLayerAndOnce() {
        WorldLog log = new WorldLog(1L);
        assertNull("nothing is revealed on arrival", Reveal.readNext(log, Reveal.Layer.ARRIVAL));

        String first = Reveal.readNext(log, Reveal.Layer.BRIDGE);
        assertNotNull(first);
        assertTrue(first, first.contains("Bridge"));
        int bridgeLayer = 1;
        String next;
        while ((next = Reveal.readNext(log, Reveal.Layer.BRIDGE)) != null) bridgeLayer++;
        assertTrue("the Bridge layer has several revelations", bridgeLayer >= 3);
        assertNull("the Bridge layer is spent", Reveal.readNext(log, Reveal.Layer.BRIDGE));

        String deeper = Reveal.readNext(log, Reveal.Layer.LAST_STAND);
        assertNotNull("a deeper layer goes on where the last left off", deeper);
        assertTrue(deeper, deeper.contains("Treasure") || deeper.contains("Minotaur King"));

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        WorldLog loaded = json.fromJson(WorldLog.class, json.toJson(log));
        assertNotEquals("what was read stays read", first, Reveal.readNext(loaded, Reveal.Layer.LAST_STAND));

        String last = null;
        while ((next = Reveal.readNext(loaded, Reveal.Layer.LAST_STAND)) != null) last = next;
        assertTrue("the whole truth comes last: " + last, last.contains("first seeker"));
    }

    @Test
    public void theGodsAreKnownByReadingTheirNamesAndAWardensFallForgetsNothing() {
        WorldLog log = new WorldLog(1L);
        assertFalse(Reveal.godsKnown(log));
        Reveal.readNext(log, Reveal.Layer.BRIDGE);
        Reveal.readNext(log, Reveal.Layer.BRIDGE);
        assertFalse("the Hunger alone names no god", Reveal.godsKnown(log));
        // The Warden falls: the Hunger is back to nothing, but the Bridge layer stays open.
        String third = Reveal.readNext(log, Reveal.Layer.ARRIVAL);
        assertNotNull(third);
        assertTrue(third, third.contains("Ixxath") && third.contains("Hollow Hood"));
        assertTrue(Reveal.godsKnown(log));
        assertEquals(3, Reveal.learned(log).size());
    }

    @Test
    public void everyRevelationTheFontCanWrite() {
        for (String text : Reveal.all()) assertEquals(text, UiGlyphs.sanitize(text));
    }
}
