package com.bpm.minotaur.gamedata.shelter;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.generation.WorldConstants;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Which beacons the sky shows (docs/DEsign/Requirements_ Shelter Roads.md, section 6).
 */
public class BeaconPlannerTest {

    private BiomeManager bm;
    private ShelterNetwork net;

    @Before
    public void setUp() {
        bm = new BiomeManager(42L);
        net = ShelterNetwork.getInstance();
        net.resetForNewGame();
    }

    private static BeaconPlanner.Beacon find(List<BeaconPlanner.Beacon> list, GridPoint2 chunk) {
        for (BeaconPlanner.Beacon b : list) if (b.getChunk().equals(chunk)) return b;
        return null;
    }

    @Test
    public void fromEveryRoadShelterTheNextOneShowsAtItsBearing() {
        for (ShelterRoads.Road road : bm.getRoads().getRoads()) {
            List<GridPoint2> stops = road.getShelters();
            for (int i = 0; i + 1 < stops.size(); i++) {
                GridPoint2 here = stops.get(i);
                GridPoint2 next = stops.get(i + 1);
                List<BeaconPlanner.Beacon> sky = BeaconPlanner.visible(bm, net, here.x, here.y,
                        WorldConstants.BEACON_RANGE_CHUNKS);
                BeaconPlanner.Beacon b = find(sky, next);
                assertNotNull("road " + road.getIndex() + ": no beacon from " + here + " to " + next, b);
                assertEquals(next.x - here.x, b.getDx(), 1e-4);
                assertEquals(next.y - here.y, b.getDy(), 1e-4);
                assertSame(BeaconPlanner.Kind.SMOKE, b.getKind());
                assertEquals(BeaconPalette.roadColor(road.getIndex()), b.getColor());
            }
        }
    }

    @Test
    public void theShelterYouStandInShowsNoBeacon() {
        GridPoint2 here = bm.getRoads().getRoad(0).getShelters().get(0);
        assertNull(find(BeaconPlanner.visible(bm, net, here.x, here.y, 14f), here));
    }

    @Test
    public void aClaimedShelterGlowsAndASealRoadEndsInAPillar() {
        ShelterRoads.Road road = bm.getRoads().getRoad(1);
        GridPoint2 last = road.getShelters().get(road.getShelters().size() - 1);
        net.claim(last);
        List<BeaconPlanner.Beacon> sky = BeaconPlanner.visible(bm, net, road.getEnd().x, road.getEnd().y + 1, 40f);
        assertSame(BeaconPlanner.Kind.GLOW, find(sky, last).getKind());
        BeaconPlanner.Beacon pillar = find(BeaconPlanner.visible(bm, net, last.x, last.y, 40f), road.getEnd());
        assertNotNull(pillar);
        assertSame(BeaconPlanner.Kind.PILLAR, pillar.getKind());
        assertFalse(pillar.isSpent());

        net.awardSeal(1);
        pillar = find(BeaconPlanner.visible(bm, net, last.x, last.y, 40f), road.getEnd());
        assertTrue("a won seal's road burns dimmed", pillar.isSpent());
    }

    @Test
    public void beaconsOutOfRangeAreHidden() {
        for (BeaconPlanner.Beacon b : BeaconPlanner.visible(bm, net, 0, 0, 14f)) {
            assertTrue(Math.hypot(b.getDx(), b.getDy()) <= 14f + 1e-4);
        }
        assertTrue(BeaconPlanner.visible(bm, net, 0, 0, 0.5f).isEmpty());
    }

    @Test
    public void worldsWithoutRoadsShowNothing() {
        assertTrue(BeaconPlanner.visible(new BiomeManager(42L, WorldConstants.WORLD_GEN_BANDS), net, 20, 0, 50f).isEmpty());
    }
}
