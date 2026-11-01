package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for HintManager (#169): first-time hints shown once per save,
 * togglable in settings, safe across slots, and sanitized for the intellivision font.
 */
public class HintManagerTest {

    private GameEventManager eventManager;
    private HintManager hintManager;
    private boolean originalShowHints;

    @Before
    public void setUp() {
        hintManager = HintManager.getInstance();
        hintManager.resetForNewGame();
        eventManager = new GameEventManager();
        originalShowHints = SettingsManager.getInstance().isShowHints();
        SettingsManager.getInstance().setShowHints(true);
    }

    @After
    public void tearDown() {
        SettingsManager.getInstance().setShowHints(originalShowHints);
        hintManager.resetForNewGame();
    }

    @Test
    public void hintsAreShownOnceAndOnlyOnce() {
        assertFalse(hintManager.hasSeen(HintManager.HintId.HUNGER));
        assertTrue(hintManager.checkHunger(eventManager));
        assertTrue(hintManager.hasSeen(HintManager.HintId.HUNGER));

        assertEquals(1, eventManager.getMessageEvents().size());
        assertTrue(eventManager.getMessageEvents().get(0).message.contains("hungry"));

        // Subsequent check is suppressed and does not add new event
        assertFalse(hintManager.checkHunger(eventManager));
        assertEquals(1, eventManager.getMessageEvents().size());
    }

    // ---- the Living War (A51) ----

    private void fireEveryWarHint() {
        hintManager.checkWarTable(eventManager);
        hintManager.checkWarSighting("House Vellum", "north", eventManager);
        hintManager.checkWarEarshot("east", eventManager);
        hintManager.checkWarTrail("north-east", eventManager);
        hintManager.checkWarTolerated("House Vellum", eventManager);
    }

    @Test
    public void eachWarHintFiresOnceAcrossAReload() {
        fireEveryWarHint();
        assertNotNull(hintManager.takeFirstFightWords());
        assertTrue(hintManager.noteWarTableOpened());
        int shown = eventManager.getMessageEvents().size();
        assertEquals(5, shown);

        fireEveryWarHint();
        assertEquals("once", shown, eventManager.getMessageEvents().size());

        hintManager.load();
        fireEveryWarHint();
        assertEquals("and once more after a reload is still once", shown, eventManager.getMessageEvents().size());
        assertNull(hintManager.takeFirstFightWords());
        assertFalse(hintManager.noteWarTableOpened());
        assertTrue(hintManager.livingWarUnlocked());
    }

    @Test
    public void warHintsAreWordedAsAgreed() {
        fireEveryWarHint();
        List<GameEvent> e = eventManager.getMessageEvents();
        assertEquals("A host of House Vellum marches north. Armies move whether you watch or not; the map remembers where you saw them.", e.get(1).message);
        assertEquals("Horns to the east: a battle. Follow the sound, and choose a side when you arrive.", e.get(2).message);
        assertEquals("Tracks: a host passed here, heading north-east. Following them updates where you think it is.", e.get(3).message);
        assertEquals("House Vellum tolerates you. At Friend, kneel at its banner to swear to it.", e.get(4).message);
    }

    @Test
    public void everyWarStringSurvivesTheFont() {
        fireEveryWarHint();
        for (GameEvent e : eventManager.getMessageEvents()) assertEquals(e.message, UiGlyphs.sanitize(e.message));
        String fight = hintManager.takeFirstFightWords();
        assertEquals(fight, UiGlyphs.sanitize(fight));
        for (String line : com.bpm.minotaur.gamedata.history.war.WarCodex.FIRST_OPENING) assertEquals(line, UiGlyphs.sanitize(line));
        for (com.bpm.minotaur.gamedata.history.war.WarCodex.Section s : com.bpm.minotaur.gamedata.history.war.WarCodex.PAGE) {
            assertEquals(s.heading, UiGlyphs.sanitize(s.heading));
            assertEquals(s.body, UiGlyphs.sanitize(s.body));
        }
    }

    /** A51 lists the three lines and the page apart from its hints: turning hints off loses neither. */
    @Test
    public void theFirstOpeningAndTheCodexPageComeWithHintsOff() {
        SettingsManager.getInstance().setShowHints(false);
        assertFalse(hintManager.livingWarUnlocked());
        assertTrue(hintManager.noteWarTableOpened());
        assertTrue(hintManager.livingWarUnlocked());
    }

    @Test
    public void aTableAlreadyReadIsNotPointedOut() {
        hintManager.noteWarTableOpened();
        assertFalse(hintManager.checkWarTable(eventManager));
        assertEquals(0, eventManager.getMessageEvents().size());
    }

    @Test
    public void aHostOrAFightWithNoHeadingStillReads() {
        hintManager.checkWarSighting("House Vellum", null, eventManager);
        hintManager.checkWarEarshot(null, eventManager);
        hintManager.checkWarTrail(null, eventManager);
        List<GameEvent> e = eventManager.getMessageEvents();
        assertTrue(e.get(0).message.startsWith("A host of House Vellum has made camp. "));
        assertTrue(e.get(1).message.startsWith("Horns close by: a battle."));
        assertTrue(e.get(2).message.startsWith("Tracks: a host passed here. "));
    }

    @Test
    public void hintsAreSuppressedWhenDisabledInSettings() {
        SettingsManager.getInstance().setShowHints(false);

        assertFalse(hintManager.checkHunger(eventManager));
        assertFalse(hintManager.hasSeen(HintManager.HintId.HUNGER));
        assertEquals(0, eventManager.getMessageEvents().size());

        // When re-enabled, unseen hint can now fire
        SettingsManager.getInstance().setShowHints(true);
        assertTrue(hintManager.checkHunger(eventManager));
        assertTrue(hintManager.hasSeen(HintManager.HintId.HUNGER));
        assertEquals(1, eventManager.getMessageEvents().size());
    }

    @Test
    public void arrivalHintUsesVengeanceStoryAndNeverDaughter() {
        assertTrue(hintManager.checkArrival(eventManager));
        assertTrue(hintManager.hasSeen(HintManager.HintId.ARRIVAL));

        List<GameEvent> events = eventManager.getMessageEvents();
        assertEquals(3, events.size());

        String allArrivalText = events.get(0).message + " "
                + events.get(1).message + " "
                + events.get(2).message;

        // Revenge story per #189
        assertTrue(allArrivalText.toLowerCase().contains("vengeance"));
        assertTrue(allArrivalText.toLowerCase().contains("shelter"));

        // Must never mention daughter
        assertFalse("Story must follow #189 revenge arc, not daughter",
                allArrivalText.toLowerCase().contains("daughter"));

        // Calling again does nothing
        assertFalse(hintManager.checkArrival(eventManager));
        assertEquals(3, eventManager.getMessageEvents().size());
    }

    @Test
    public void badAndNastyMonsterHintsExplainResistances() {
        // Bad monster hint
        assertTrue(hintManager.checkBadMonster(null, eventManager));
        assertTrue(hintManager.hasSeen(HintManager.HintId.BAD_MONSTER));
        String badMsg = eventManager.getMessageEvents().get(0).message;
        assertTrue(badMsg.contains("War"));
        assertTrue(badMsg.contains("Spiritual"));

        // Nasty monster hint
        assertTrue(hintManager.checkNastyMonster(null, eventManager));
        assertTrue(hintManager.hasSeen(HintManager.HintId.NASTY_MONSTER));
        String nastyMsg = eventManager.getMessageEvents().get(1).message;
        assertTrue(nastyMsg.contains("War"));
        assertTrue(nastyMsg.contains("Spiritual"));

        // Both only show once
        assertFalse(hintManager.checkBadMonster(null, eventManager));
        assertFalse(hintManager.checkNastyMonster(null, eventManager));
    }

    @Test
    public void otherHintsFireAndSanitizeCorrectly() {
        assertTrue(hintManager.checkThirst(eventManager));
        assertTrue(hintManager.checkCold(eventManager));
        assertTrue(hintManager.checkDivinity(eventManager));
        assertTrue(hintManager.checkDoom(eventManager));
        assertTrue(hintManager.checkLockedSpellSlot(eventManager));

        // Check that all hint messages survive sanitization without change
        for (GameEvent ev : eventManager.getMessageEvents()) {
            assertEquals("Hint message must be valid with UiGlyphs.sanitize",
                    ev.message, UiGlyphs.sanitize(ev.message));
        }
    }

    @Test
    public void resetForNewGameClearsAllSeenHints() {
        hintManager.checkHunger(eventManager);
        hintManager.checkThirst(eventManager);
        assertTrue(hintManager.hasSeen(HintManager.HintId.HUNGER));
        assertTrue(hintManager.hasSeen(HintManager.HintId.THIRST));

        hintManager.resetForNewGame();

        assertFalse(hintManager.hasSeen(HintManager.HintId.HUNGER));
        assertFalse(hintManager.hasSeen(HintManager.HintId.THIRST));
    }
}
