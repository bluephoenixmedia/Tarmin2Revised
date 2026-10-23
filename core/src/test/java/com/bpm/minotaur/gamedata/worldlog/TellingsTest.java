package com.bpm.minotaur.gamedata.worldlog;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.Figure;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.history.PlayerDeed;
import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** "World Log: the Houses' Tellings, found as fragments" (#285). */
public class TellingsTest {

    private static HistoryWorld world;
    private static House sworn, slainKin, killer, castle;
    private static Figure kin;

    @BeforeClass
    public static void load() throws IOException {
        world = HistorySimulator.prehistory(21L, DoctrineCatalogTest.loadCatalog());
        List<House> houses = new ArrayList<>();
        for (House h : world.livingHouses()) {
            if (h.holdsCastle) castle = h;
            else if (!world.livingMembers(h).isEmpty()) houses.add(h);
        }
        sworn = houses.get(0);
        slainKin = houses.get(1);
        killer = houses.get(2);
        kin = world.livingMembers(slainKin).get(0);
    }

    private static PlayerDeed deed(PlayerDeed.Kind kind, int target) {
        return new PlayerDeed(kind, target, 0);
    }

    private static LifeRecord brannoc() {
        WorldLog log = WorldLog.upgradedFrom(2, 0);
        log.wake(new Life("Brannoc Thatch", 1, "the Ashen Ford", Allegiance.REFUGEE_COUNCIL, Life.Origin.RAIDED,
                "the Antlered Host", null, null), 2);
        log.note(Fact.of(Fact.Kind.SWORE_OATH, sworn.name));
        log.die("Slain", "Gorvak the Gnawer", "level 3", 40);
        return log.lives().get(0);
    }

    @Test
    public void theHousesInvolvedTellTheLifeAndTheKnellOnlyAtANewStage() {
        List<PlayerDeed> deeds = Arrays.asList(
                deed(PlayerDeed.Kind.SWORE_OATH, sworn.id),
                deed(PlayerDeed.Kind.SLEW_FIGURE, kin.id),
                deed(PlayerDeed.Kind.SEEKER_FELL, killer.id));
        List<Tellings.Teller> tellers = Tellings.involved(world, deeds, false);
        assertEquals(3, tellers.size());
        assertTrue(tellers.contains(new Tellings.Teller(sworn.id, Tellings.Role.SWORN)));
        assertTrue(tellers.contains(new Tellings.Teller(slainKin.id, Tellings.Role.KIN_SLAIN)));
        assertTrue(tellers.contains(new Tellings.Teller(killer.id, Tellings.Role.KILLER)));

        List<Tellings.Teller> atStage = Tellings.involved(world, deeds, true);
        assertTrue(atStage.contains(new Tellings.Teller(castle.id, Tellings.Role.KNELL)));
        assertTrue("an uninvolved house says nothing",
                Tellings.involved(world, Arrays.asList(deed(PlayerDeed.Kind.SEEKER_FELL, -1)), false).isEmpty());
    }

    @Test
    public void aHouseTellsALifeOnceInItsWorstVoice() {
        List<PlayerDeed> deeds = Arrays.asList(
                deed(PlayerDeed.Kind.SWORE_OATH, sworn.id),
                deed(PlayerDeed.Kind.BROKE_OATH, sworn.id));
        List<Tellings.Teller> tellers = Tellings.involved(world, deeds, false);
        assertEquals(Arrays.asList(new Tellings.Teller(sworn.id, Tellings.Role.OATHBROKEN)), tellers);
    }

    @Test
    public void everyVoiceKeepsTheFixedFactsAndBendsOnlyTheRest() {
        LifeRecord life = brannoc();
        for (Tellings.Role role : Tellings.Role.values()) {
            int house = role == Tellings.Role.KNELL ? castle.id : role == Tellings.Role.KILLER ? killer.id : slainKin.id;
            Telling t = Tellings.tell(new Tellings.Teller(house, role), life, world, killer.id);
            for (String text : new String[]{t.plain, t.mythic}) {
                assertTrue(role + ": " + text, text.contains("Brannoc Thatch"));
                assertTrue(role + ": " + text, text.contains("third"));
                assertEquals(text, UiGlyphs.sanitize(text));
            }
            assertFalse(t.found);
            assertEquals(house, t.tellerHouseId);
        }
        Telling boast = Tellings.tell(new Tellings.Teller(killer.id, Tellings.Role.KILLER), life, world, killer.id);
        assertTrue("the killer claims the kill as its own: " + boast.plain, boast.plain.contains(killer.name));
        Telling claim = Tellings.tell(new Tellings.Teller(slainKin.id, Tellings.Role.KIN_SLAIN), life, world, -1);
        assertTrue("a house claims a kill no house made: " + claim.plain, claim.plain.contains(slainKin.name));
        assertEquals("the same life told the same way", claim.plain,
                Tellings.tell(new Tellings.Teller(slainKin.id, Tellings.Role.KIN_SLAIN), life, world, -1).plain);
    }

    @Test
    public void aTellingIsFoundOnceThenShownAndKeptThroughTheSave() {
        WorldLog log = WorldLog.upgradedFrom(2, 0);
        log.wake(new Life("Brannoc Thatch", 1, "the Ashen Ford", Allegiance.REFUGEE_COUNCIL, Life.Origin.RAIDED,
                "the Antlered Host", null, null), 2);
        log.die("Slain", "a Kobold", "level 3", 40);
        LifeRecord life = log.lives().get(0);
        log.tell(life.number, Arrays.asList(
                Tellings.tell(new Tellings.Teller(killer.id, Tellings.Role.KILLER), life, world, killer.id),
                Tellings.tell(new Tellings.Teller(sworn.id, Tellings.Role.SWORN), life, world, killer.id)));

        assertTrue(log.hasUnfoundTelling(killer.id));
        Telling found = log.findTelling(killer.id);
        assertNotNull(found);
        assertTrue(found.found);
        assertFalse(log.hasUnfoundTelling(killer.id));
        assertNull(log.findTelling(killer.id));
        assertTrue(log.hasUnfoundTelling(sworn.id));

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        WorldLog loaded = json.fromJson(WorldLog.class, json.toJson(log));
        List<Telling> kept = loaded.lives().get(0).tellings;
        assertEquals(2, kept.size());
        assertTrue(kept.get(0).found);
        assertFalse(kept.get(1).found);
    }
}
