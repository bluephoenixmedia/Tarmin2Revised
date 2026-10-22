package com.bpm.minotaur.gamedata.worldlog;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.bpm.minotaur.gamedata.history.Life;
import com.bpm.minotaur.gamedata.history.town.Allegiance;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import static org.junit.Assert.*;

/** "World Log: a life's record, and the lives unremembered" (#280). */
public class WorldLogTest {

    private static Life brenna() {
        return new Life("Brenna Thatch", 3, "the Ashen Ford", Allegiance.REFUGEE_COUNCIL, Life.Origin.RAIDED,
                "the Antlered Host", null, null);
    }

    private static Life skrag() {
        return new Life("Skrag Ratbite", 5, "the Hook Mire", Allegiance.GOBLIN_CLANS, Life.Origin.TITHED,
                null, "debt", "the Goblin Clans");
    }

    @Test
    public void aLifeIsWhoTheyWereWhatTheyDidAndHowTheyDied() {
        WorldLog log = new WorldLog();
        log.wake(brenna());
        log.note(Fact.of(Fact.Kind.SWORE_OATH, "House Gall"));
        log.die("Slain by a kobold", "a Kobold", "the Weeping Stair", 412);

        assertEquals(1, log.lives().size());
        LifeRecord life = log.lives().get(0);
        assertEquals("Brenna Thatch of the Ashen Ford", life.title);
        assertEquals(1, life.number);
        assertEquals(1, life.lines.size());
        assertTrue(life.lines.get(0).plain().contains("House Gall"));
        assertTrue(life.isDead());
        assertEquals("a Kobold", life.killer);
        assertEquals("the Weeping Stair", life.place);
        assertEquals(412, life.turn);
        assertNull("no life is open after a death", log.current());
    }

    @Test
    public void onlyTheEightMostNotableLinesAreKeptMostNotableFirst() {
        WorldLog log = new WorldLog();
        log.wake(brenna());
        for (int i = 0; i < 9; i++) log.note(Fact.of(Fact.Kind.QUEST_DONE, "Town " + i));
        log.note(Fact.of(Fact.Kind.WARDEN_DESTROYED));
        log.note(Fact.of(Fact.Kind.SWORE_OATH, "House Gall"));

        LifeRecord life = log.current();
        assertEquals(8, life.lines.size());
        assertEquals(Fact.Kind.WARDEN_DESTROYED, life.lines.get(0).kind);
        assertEquals(Fact.Kind.SWORE_OATH, life.lines.get(1).kind);
        assertEquals("the earliest of equals stays", "Town 0", life.lines.get(2).subject);
    }

    @Test
    public void onlyANewDepthForTheSoulIsWorthALine() {
        WorldLog log = new WorldLog();
        log.setSoulDeepest(4);
        log.wake(brenna());
        log.noteDepth(3);
        log.noteDepth(4);
        assertTrue(log.current().lines.isEmpty());
        log.noteDepth(6);
        log.noteDepth(5);
        assertEquals(1, log.current().lines.size());
        assertEquals(Fact.Kind.DEEPEST_STRATUM, log.current().lines.get(0).kind);
        assertEquals(6, log.soulDeepest());
    }

    @Test
    public void theFirstHeavyOfALifeCountsOnceAndEveryBossCounts() {
        WorldLog log = new WorldLog();
        log.wake(brenna());
        log.noteKill("Kobold", false, false);
        log.noteKill("Ogre", true, false);
        log.noteKill("Troll", true, false);
        log.noteKill("Iron Golem", true, true);
        log.noteKill("Lich", true, true);
        assertEquals(3, log.current().lines.size());

        log.die("Slain", "a Troll", "the Cut", 9);
        log.wake(skrag());
        log.noteKill("Ogre", true, false);
        assertEquals("a new life has its own first Heavy", 1, log.current().lines.size());
        assertEquals(2, log.current().number);
    }

    @Test
    public void aRerollBeforeAnyDeedReplacesWhoTheyWere() {
        WorldLog log = new WorldLog();
        log.wake(brenna());
        log.wake(skrag());
        assertEquals(1, log.lives().size());
        assertEquals("Skrag Ratbite of the Hook Mire", log.current().title);
    }

    @Test
    public void everyKindOfLineHasAPlainAndAMythicTextTheFontCanWrite() {
        for (Fact.Kind kind : Fact.Kind.values()) {
            Fact f = Fact.of(kind, "Vorath", "House Gall");
            for (String text : new String[]{f.plain(), f.mythic()}) {
                assertFalse(kind + " has no text", text == null || text.isEmpty());
                assertEquals(text, UiGlyphs.sanitize(text));
            }
            assertNotEquals(kind + " tells it two ways", f.plain(), f.mythic());
        }
    }

    @Test
    public void theLogSurvivesTheSave() {
        WorldLog log = WorldLog.upgradedFrom(7, 5);
        log.wake(brenna());
        log.note(Fact.of(Fact.Kind.SLEW_FIGURE, "Vorath", "House Gall"));
        log.die("Slain", "a Kobold", "the Cut", 9);
        log.wake(skrag());

        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        WorldLog loaded = json.fromJson(WorldLog.class, json.toJson(log));

        assertEquals(7, loaded.unremembered());
        assertEquals(5, loaded.soulDeepest());
        assertEquals(2, loaded.lives().size());
        assertEquals(log.lives().get(0).lines.get(0).plain(), loaded.lives().get(0).lines.get(0).plain());
        assertEquals("a Kobold", loaded.lives().get(0).killer);
        assertEquals(9, loaded.current().number);
    }

    @Test
    public void aSlotFromBeforeTheLogRemembersOnlyHowManyLivesItForgot() {
        WorldLog log = WorldLog.upgradedFrom(12, 3);
        assertEquals(12, log.unremembered());
        assertTrue(log.lives().isEmpty());
        log.wake(brenna());
        assertEquals("life numbers go on from the forgotten", 13, log.current().number);
        assertEquals(0, new WorldLog().unremembered());
    }
}
