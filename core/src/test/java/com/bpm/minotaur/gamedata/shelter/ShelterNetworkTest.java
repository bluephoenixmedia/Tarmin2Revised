package com.bpm.minotaur.gamedata.shelter;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Remembered road progress across a death, against real road layouts
 * (docs/DEsign/Requirements_ Shelter Roads.md, section 8).
 */
public class ShelterNetworkTest {

    private ShelterNetwork net;
    private ShelterRoads oldRoads;
    private ShelterRoads newRoads;

    @Before
    public void setUp() {
        net = ShelterNetwork.getInstance();
        net.resetForNewGame();
        oldRoads = new BiomeManager(11L).getRoads();
        newRoads = new BiomeManager(99L).getRoads();
    }

    private static GridPoint2 stop(ShelterRoads roads, int road, int i) {
        return roads.getRoad(road).getShelters().get(i);
    }

    private int claimedOn(ShelterRoads roads, int road) {
        int n = 0;
        for (GridPoint2 c : roads.getRoad(road).getShelters()) if (net.isClaimed(c)) n++;
        return n;
    }

    @Test
    public void homeIsAlwaysClaimed() {
        assertTrue(net.isClaimed(new GridPoint2(0, 0)));
        assertFalse(net.isClaimed(stop(oldRoads, 0, 0)));
    }

    @Test
    public void theRestRoadLosesOneAndThePlayerWakesThere() {
        int a = longest(oldRoads, newRoads);
        int b = (a + 1) % 4;
        net.claim(stop(oldRoads, a, 0));
        net.claim(stop(oldRoads, a, 1));
        net.claim(stop(oldRoads, b, 0));
        net.recordRest(stop(oldRoads, a, 1));

        GridPoint2 wake = net.carryOverDeath(oldRoads, newRoads);

        assertEquals(1, claimedOn(newRoads, a));
        assertEquals(Math.min(1, newRoads.getRoad(b).getShelters().size()), claimedOn(newRoads, b));
        assertEquals("wakes one back on the same road", stop(newRoads, a, 0), wake);
        assertEquals(wake, net.getRestChunk());
    }

    @Test
    public void restingAtHomeWakesAtHomeAndCostsNothing() {
        net.claim(stop(oldRoads, 2, 0));
        net.recordRest(new GridPoint2(0, 0));
        assertNull(net.carryOverDeath(oldRoads, newRoads));
        assertEquals(1, claimedOn(newRoads, 2));
    }

    @Test
    public void aSealedRoadKeepsEveryShelterAndSealsPersist() {
        net.awardSeal(3);
        net.claim(stop(oldRoads, 3, 0));
        net.recordRest(stop(oldRoads, 3, 0));
        net.carryOverDeath(oldRoads, newRoads);
        assertEquals(newRoads.getRoad(3).getShelters().size(), claimedOn(newRoads, 3));
        assertTrue(net.hasSeal(3));
        assertEquals(1, net.getSealCount());
    }

    @Test
    public void skippingAheadKeepsThePlaceOnTheRoadNotTheFirstShelters() {
        int a = longest(oldRoads, newRoads);
        net.claim(stop(oldRoads, a, 1));
        net.recordRest(stop(oldRoads, a, 1));
        net.claim(stop(oldRoads, a, 0)); // claimed after, so the rest stays at #2
        net.recordRest(stop(oldRoads, a, 1));
        GridPoint2 wake = net.carryOverDeath(oldRoads, newRoads);
        assertTrue(net.isClaimed(stop(newRoads, a, 0)));
        assertFalse("the furthest claim on the rest road is lost", net.isClaimed(stop(newRoads, a, 1)));
        assertEquals(stop(newRoads, a, 0), wake);
    }

    /** A road with at least two shelters in both layouts. */
    private static int longest(ShelterRoads a, ShelterRoads b) {
        for (int r = 0; r < 4; r++) {
            List<GridPoint2> x = a.getRoad(r).getShelters();
            List<GridPoint2> y = b.getRoad(r).getShelters();
            if (x.size() >= 2 && y.size() >= 2) return r;
        }
        throw new AssertionError("no road with two shelters in both seeds");
    }
}
