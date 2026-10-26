package com.bpm.minotaur.gamedata.progression;

import com.bpm.minotaur.gamedata.progression.ShelterAltar.Station;
import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.*;

/** The shelter names what your Divinity can buy (#310; the #257 grilling). */
public class AltarNudgeTest {

    private final AltarNudge nudge = new AltarNudge();
    private final Set<Station> owned = EnumSet.noneOf(Station.class);

    private String step(boolean inShelter, int divinity) {
        return nudge.step(inShelter, divinity, owned::contains, s -> s.getRequiredDepth() == 0);
    }

    @Test
    public void enteringWithFifteenAndNoBedNamesTheBedOnce() {
        assertNull("outside, nothing", step(false, 15));
        String line = step(true, 15);
        assertNotNull(line);
        assertTrue(line, line.contains(Station.BED.getDisplayName()));
        assertTrue(line, line.contains("(15)"));
        assertNull("a second step inside the same visit posts nothing", step(true, 15));
    }

    @Test
    public void steppingOutOfTheDoorAndBackIsStillTheSameVisit() {
        step(true, 15);
        step(false, 15);
        assertNull(step(true, 15));
    }

    @Test
    public void aNewVisitSpeaksAgain() {
        step(true, 15);
        nudge.wake(); // the player left the shelter's chunk
        assertNotNull(step(true, 15));
    }

    @Test
    public void wakingInTheShelterAfterADeathIsANewVisit() {
        step(true, 15);
        nudge.wake();
        assertNotNull(step(true, 15));
    }

    @Test
    public void withoutTheBedItNamesTheMostUsefulAffordableStation() {
        String line = step(true, 12);
        assertNotNull("the Lantern costs 10", line);
        assertTrue(line, line.contains(Station.LANTERN.getDisplayName()));
    }

    @Test
    public void theBedIsPreferredOverCheaperStations() {
        String line = step(true, 40);
        assertTrue(line, line.contains(Station.BED.getDisplayName()));
    }

    @Test
    public void nothingAffordableMeansNothingPosted() {
        assertNull(step(true, 9));
    }

    @Test
    public void everythingAffordableAlreadyBoughtMeansNothingPosted() {
        owned.add(Station.BED);
        owned.add(Station.LANTERN);
        owned.add(Station.ARCHIVE_LECTERN);
        assertNull(step(true, 15));
    }

    @Test
    public void aStationNotYetOfferedIsNeverNamed() {
        for (Station s : Station.values()) if (s.getRequiredDepth() == 0) owned.add(s);
        assertNull("the portals are hidden until their depth", step(true, 999));
    }

    @Test
    public void theLineSurvivesTheFont() {
        for (Station s : Station.values()) {
            String line = AltarNudge.line(s);
            assertEquals(line, UiGlyphs.sanitize(line));
        }
    }
}
