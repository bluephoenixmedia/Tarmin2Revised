package com.bpm.minotaur.gamedata.history.war.army;

import org.junit.Test;

import static org.junit.Assert.*;

/** Living War A30-A34: the rules of a siege, without the armies. */
public class SeatSiegeTest {

    @Test
    public void theWallsWearBySeasonAndFloorAtOneAndAHalf() {
        SeatSiege s = new SeatSiege();
        assertEquals(4f, s.walls, 0f);
        s.wear();
        assertEquals(3.5f, s.walls, 0f);
        for (int i = 0; i < 20; i++) s.wear();
        assertEquals(1.5f, s.walls, 0f);
    }

    @Test
    public void theRamOrTheFireTakesFromTheWallsButNotBelowTheFloor() {
        SeatSiege s = new SeatSiege();
        assertEquals(4f, s.assaultEdge(), 0f);
        s.works = SeatSiege.RAM_TURNS;
        assertEquals(3f, s.assaultEdge(), 0f);
        for (int i = 0; i < 20; i++) s.wear();
        assertEquals(1.5f, s.assaultEdge(), 0f);
    }

    @Test
    public void noLaddersNoAssault() {
        assertFalse(SeatSiege.readyToAssault(1000, 1, 1.5f, false, true, false));
        assertTrue(SeatSiege.readyToAssault(1000, 1, 1.5f, true, false, false));
    }

    /** A33: on the same numbers, a patient captain waits for what a wrathful one does not. */
    @Test
    public void aPatientCaptainAssaultsLaterThanAWrathfulOneOnTheSameNumbers() {
        int soldiers = 15, garrison = 6;
        float edge = 4f;
        int wrathful = -1, level = -1, patient = -1;
        for (int season = 0; season < 40; season++) {
            if (wrathful < 0 && SeatSiege.readyToAssault(soldiers, garrison, edge, true, true, false)) wrathful = season;
            if (level < 0 && SeatSiege.readyToAssault(soldiers, garrison, edge, true, false, false)) level = season;
            if (patient < 0 && SeatSiege.readyToAssault(soldiers, garrison, edge, true, false, true)) patient = season;
            edge = Math.max(SeatSiege.WALLS_FLOOR, edge - SeatSiege.WALLS_WEAR);
            if (season % 2 == 1 && garrison > 1) garrison--;
        }
        assertTrue("wrathful " + wrathful + ", level " + level, wrathful < level);
        assertTrue("level " + level + ", patient " + patient, level < patient);
    }

    @Test
    public void onlyAGarrisonWithTheTemperAndTheNumbersSallies() {
        assertTrue(SeatSiege.readyToSally(10, 15, true));
        assertFalse("not its way", SeatSiege.readyToSally(10, 15, false));
        assertFalse("too few", SeatSiege.readyToSally(5, 15, true));
        assertFalse("none left", SeatSiege.readyToSally(0, 0, true));
    }
}
