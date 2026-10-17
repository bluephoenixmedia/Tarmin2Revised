package com.bpm.minotaur.gamedata.shelter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

/**
 * The death rules in docs/DEsign/Requirements_ Shelter Roads.md, section 8.
 */
public class ShelterMemoryTest {

    private static final Set<Integer> NO_SEALS = Collections.emptySet();

    /** Four roads; each argument lists the places claimed on one road. */
    private static List<Set<Integer>> roads(int[]... places) {
        List<Set<Integer>> out = new ArrayList<>();
        for (int[] road : places) {
            Set<Integer> s = new TreeSet<>();
            for (int p : road) s.add(p);
            out.add(s);
        }
        return out;
    }

    @Test
    public void theRoadOfTheLastRestLosesOneAndTheOthersKeepTheirPlaces() {
        // Claimed three on road 1 and two on road 2, rested at road 1's third shelter.
        ShelterMemory.Result r = ShelterMemory.afterDeath(
                roads(new int[]{}, new int[]{0, 1, 2}, new int[]{0, 1}, new int[]{}), 1, 2, NO_SEALS);
        assertEquals(Set.of(0, 1), r.getClaimed(1));
        assertEquals(Set.of(0, 1), r.getClaimed(2));
        assertEquals(1, r.getRespawnRoad());
        assertEquals("wakes one shelter back, at the second", 1, r.getRespawnIndex());
    }

    @Test
    public void restingEarlierOnTheRoadWakesThereIfItSurvives() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(
                roads(new int[]{0, 1, 2, 3}, new int[]{}, new int[]{}, new int[]{}), 0, 1, NO_SEALS);
        assertEquals(Set.of(0, 1, 2), r.getClaimed(0));
        assertEquals(0, r.getRespawnRoad());
        assertEquals(1, r.getRespawnIndex());
    }

    @Test
    public void aPlayerWhoSkippedAheadKeepsTheirPlaceNotTheFirstShelters() {
        // Claimed the fourth and fifth, rested at the fifth: the fourth survives and is where they wake.
        ShelterMemory.Result r = ShelterMemory.afterDeath(
                roads(new int[]{}, new int[]{}, new int[]{3, 4}, new int[]{}), 2, 4, NO_SEALS);
        assertEquals(Set.of(3), r.getClaimed(2));
        assertEquals(2, r.getRespawnRoad());
        assertEquals(3, r.getRespawnIndex());
    }

    @Test
    public void aGapWakesAtTheNearestRememberedShelterBehind() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(
                roads(new int[]{0, 2}, new int[]{}, new int[]{}, new int[]{}), 0, 2, NO_SEALS);
        assertEquals(Set.of(0), r.getClaimed(0));
        assertEquals(0, r.getRespawnIndex());
    }

    @Test
    public void restingAtHomeOrOffRoadCostsNothingAndWakesAtHome() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(
                roads(new int[]{0, 1}, new int[]{0}, new int[]{}, new int[]{0, 1, 2}), ShelterMemory.HOME, 0, NO_SEALS);
        assertEquals(Set.of(0, 1), r.getClaimed(0));
        assertEquals(Set.of(0, 1, 2), r.getClaimed(3));
        assertEquals(ShelterMemory.HOME, r.getRespawnRoad());
    }

    @Test
    public void losingTheOnlyShelterOnTheRoadWakesAtHome() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(
                roads(new int[]{0}, new int[]{}, new int[]{}, new int[]{}), 0, 0, NO_SEALS);
        assertTrue(r.getClaimed(0).isEmpty());
        assertEquals(ShelterMemory.HOME, r.getRespawnRoad());
    }

    @Test
    public void aRoadWhoseSealIsWonKeepsEveryShelter() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(
                roads(new int[]{}, new int[]{0, 1}, new int[]{}, new int[]{}), 1, 1, Set.of(1));
        assertTrue(r.isWholeRoad(1));
        assertFalse(r.isWholeRoad(0));
        assertEquals(1, r.getRespawnRoad());
        assertEquals(1, r.getRespawnIndex());
    }

    @Test
    public void hearthLightingCompletesAfterItsTurnsUnlessWounded() {
        HearthLighting lighting = new HearthLighting(0);
        for (int i = 1; i < HearthLighting.TURNS; i++) {
            assertSame(HearthLighting.Step.CONTINUE, lighting.afterTurn(0));
        }
        assertSame(HearthLighting.Step.COMPLETE, lighting.afterTurn(0));

        HearthLighting hurt = new HearthLighting(2);
        assertSame(HearthLighting.Step.CONTINUE, hurt.afterTurn(2));
        assertSame(HearthLighting.Step.INTERRUPTED, hurt.afterTurn(3));
    }
}
