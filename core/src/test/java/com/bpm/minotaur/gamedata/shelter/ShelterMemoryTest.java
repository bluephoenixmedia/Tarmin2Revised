package com.bpm.minotaur.gamedata.shelter;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The death rules in docs/DEsign/Requirements_ Shelter Roads.md, section 8.
 */
public class ShelterMemoryTest {

    private static final Set<Integer> NO_SEALS = Collections.emptySet();

    @Test
    public void theRoadOfTheLastRestLosesOneAndTheOthersKeepTheirCount() {
        // Claimed 3 on road 1 and 2 on road 2, rested at road 1's third shelter.
        ShelterMemory.Result r = ShelterMemory.afterDeath(new int[]{0, 3, 2, 0}, 1, 2, NO_SEALS);
        assertArrayEquals(new int[]{0, 2, 2, 0}, r.getCounts());
        assertEquals(1, r.getRespawnRoad());
        assertEquals("wakes one shelter back, at the second", 1, r.getRespawnIndex());
    }

    @Test
    public void restingEarlierOnTheRoadWakesThereIfItSurvives() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(new int[]{4, 0, 0, 0}, 0, 1, NO_SEALS);
        assertArrayEquals(new int[]{3, 0, 0, 0}, r.getCounts());
        assertEquals(0, r.getRespawnRoad());
        assertEquals(1, r.getRespawnIndex());
    }

    @Test
    public void restingAtHomeOrOffRoadCostsNothingAndWakesAtHome() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(new int[]{2, 1, 0, 3}, ShelterMemory.HOME, 0, NO_SEALS);
        assertArrayEquals(new int[]{2, 1, 0, 3}, r.getCounts());
        assertEquals(ShelterMemory.HOME, r.getRespawnRoad());
    }

    @Test
    public void losingTheOnlyShelterOnTheRoadWakesAtHome() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(new int[]{1, 0, 0, 0}, 0, 0, NO_SEALS);
        assertArrayEquals(new int[]{0, 0, 0, 0}, r.getCounts());
        assertEquals(ShelterMemory.HOME, r.getRespawnRoad());
    }

    @Test
    public void aRoadWhoseSealIsWonKeepsEveryShelter() {
        ShelterMemory.Result r = ShelterMemory.afterDeath(new int[]{0, 2, 0, 0}, 1, 1, Set.of(1));
        assertEquals(ShelterMemory.ALL, r.getCounts()[1]);
        assertEquals(1, r.getRespawnRoad());
        assertEquals(1, r.getRespawnIndex());
    }

    @Test
    public void aRestPastTheClaimedCountWakesAtTheLastRemembered() {
        // Skipped ahead: one claimed shelter on the road, but it is the fifth.
        ShelterMemory.Result r = ShelterMemory.afterDeath(new int[]{0, 0, 2, 0}, 2, 4, NO_SEALS);
        assertArrayEquals(new int[]{0, 0, 1, 0}, r.getCounts());
        assertEquals(2, r.getRespawnRoad());
        assertEquals(0, r.getRespawnIndex());
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
