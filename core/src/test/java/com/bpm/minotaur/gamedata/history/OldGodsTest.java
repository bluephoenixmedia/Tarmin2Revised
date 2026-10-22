package com.bpm.minotaur.gamedata.history;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.bpm.minotaur.gamedata.encounters.Encounter;
import com.bpm.minotaur.gamedata.encounters.EncounterChoice;
import com.bpm.minotaur.managers.HistoryManager;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** "Old gods: the thirteen at their shrines" (#287). */
public class OldGodsTest {

    private static final List<Encounter> statues = new ArrayList<>();

    @BeforeClass
    public static void load() throws IOException {
        JsonValue list = new JsonReader().parse(DoctrineCatalogTest.readAsset("data/encounters.json")).get("encounters");
        Json json = new Json();
        for (JsonValue entry : list) {
            Encounter e = json.readValue(Encounter.class, entry);
            if (e.imagePath != null && e.imagePath.contains("/statue/")) statues.add(e);
        }
    }

    @Test
    public void everyStatueIsTheShrineOfExactlyOneOfTheThirteen() {
        assertEquals(13, statues.size());
        Set<OldGod> gods = EnumSet.noneOf(OldGod.class);
        for (Encounter e : statues) {
            OldGod god = OldGod.forEncounter(e.id);
            assertNotNull(e.id + " has no god", god);
            assertTrue(god + " has two shrines", gods.add(god));
        }
        assertEquals(EnumSet.allOf(OldGod.class), gods);
        assertNull(OldGod.forEncounter("EVENT_NOT_A_SHRINE"));
    }

    @Test
    public void theMinotaurStatueIsTheMinotaurKingsNotTarmins() {
        Encounter colossus = statues.stream().filter(e -> e.id.equals("EVENT_TARMIN_STATUE")).findFirst().get();
        assertEquals("Colossus of the Minotaur King", colossus.title);
        assertFalse(colossus.text, colossus.text.contains("Tarmin"));
        assertEquals(OldGod.ASTERION, OldGod.forEncounter(colossus.id));
    }

    @Test
    public void aShrineNamesItsGodAndKindredAndItsRitesAreTheGodsWithTheSameEffects() {
        for (Encounter e : statues) {
            OldGod god = OldGod.forEncounter(e.id);
            Encounter view = Shrine.view(e, god, false);
            assertEquals(e.title, view.title);
            assertTrue(view.text, view.text.contains(god.fullName));
            assertTrue(view.text, view.text.contains(god.kindred.title));
            assertEquals(e.choices.size(), view.choices.size());
            for (int i = 0; i < e.choices.size(); i++) {
                EncounterChoice was = e.choices.get(i);
                EncounterChoice now = view.choices.get(i);
                assertSame(was.successEffect, now.successEffect);
                assertSame(was.failEffect, now.failEffect);
                assertSame(was.requirement, now.requirement);
                assertEquals(was.type, now.type);
                if (Shrine.isLeave(was)) assertEquals(was.text, now.text);
                else assertTrue(now.text, now.text.startsWith(god.riteOwner()));
                assertEquals(now.text, UiGlyphs.sanitize(now.text));
            }
            assertEquals(view.text, UiGlyphs.sanitize(view.text));
            assertNotSame("the loaded encounter is never changed", e.choices, view.choices);
        }
    }

    @Test
    public void aDarkShrineOffersNothingButTheWayOut() {
        for (Encounter e : statues) {
            OldGod god = OldGod.forEncounter(e.id);
            Encounter dark = Shrine.view(e, god, true);
            assertTrue(dark.text, dark.text.contains("dark"));
            assertEquals(1, dark.choices.size());
            assertTrue(Shrine.isLeave(dark.choices.get(0)));
        }
    }

    @Test
    public void aSpentGodStaysSpentAcrossTheReplay() throws IOException {
        HistoryManager m = HistoryManager.create(11L, DoctrineCatalogTest.loadCatalog());
        assertFalse(m.world().isSpent(OldGod.IXXATH));
        m.recordGodSpent(OldGod.IXXATH);
        assertTrue(m.world().isSpent(OldGod.IXXATH));
        assertFalse(m.world().isSpent(OldGod.ASTERION));
        HistoryManager loaded = HistoryManager.fromSave(11L, m.toSave(), DoctrineCatalogTest.loadCatalog());
        assertTrue(loaded.world().isSpent(OldGod.IXXATH));
    }
}
