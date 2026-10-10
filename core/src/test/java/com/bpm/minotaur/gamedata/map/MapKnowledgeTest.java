package com.bpm.minotaur.gamedata.map;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * What exploring teaches the map (docs/DEsign/Requirements_ Expedition Map.md, section 3).
 */
public class MapKnowledgeTest {

    private BiomeManager bm;
    private ShelterNetwork net;
    private MapKnowledge map;

    @Before
    public void setUp() {
        bm = new BiomeManager(42L);
        net = ShelterNetwork.getInstance();
        net.resetForNewGame();
        map = MapKnowledge.getInstance();
        map.resetForNewGame();
    }

    private GridPoint2 shelter(int road, int i) {
        return bm.getRoads().getRoad(road).getShelters().get(i);
    }

    @Test
    public void standingInTheWildsGlimpsesTheEightChunksAround() {
        GridPoint2 here = shelter(ShelterRoads.CASTLE_ROAD, 0);
        map.recordArrival(1, here, bm, net);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                assertTrue("neighbour " + dx + "," + dy, map.isGlimpsed(new GridPoint2(here.x + dx, here.y + dy)));
            }
        }
        assertFalse(map.isGlimpsed(new GridPoint2(here.x + 2, here.y)));
    }

    @Test
    public void theMazeWallsHideTheNextChunk() {
        map.recordArrival(1, new GridPoint2(3, 3), bm, net);
        assertFalse(map.isGlimpsed(new GridPoint2(4, 3)));
    }

    @Test
    public void nothingIsGlimpsedUnderground() {
        GridPoint2 here = shelter(ShelterRoads.CASTLE_ROAD, 0);
        map.recordArrival(2, here, bm, net);
        assertFalse(map.isGlimpsed(new GridPoint2(here.x + 1, here.y)));
    }

    @Test
    public void theNextShelterOnTheRoadIsSightedByItsBeacon() {
        for (ShelterRoads.Road road : bm.getRoads().getRoads()) {
            List<GridPoint2> stops = road.getShelters();
            if (stops.size() < 2) continue;
            assertFalse(map.isSighted(stops.get(1)));
            map.recordArrival(1, stops.get(0), bm, net);
            assertTrue("road " + road.getIndex(), map.isSighted(stops.get(1)));
        }
    }

    @Test
    public void aSealSitePillarIsNotASighting() {
        ShelterRoads.Road road = bm.getRoads().getRoad(1);
        List<GridPoint2> stops = road.getShelters();
        map.recordArrival(1, stops.get(stops.size() - 1), bm, net);
        assertFalse(map.isSighted(road.getEnd()));
    }

    @Test
    public void reachingTheWaypointClearsIt() {
        GridPoint2 target = new GridPoint2(2, 2);
        map.setWaypoint(1, target);
        map.recordArrival(1, new GridPoint2(1, 2), bm, net);
        assertNotNull("elsewhere keeps it", map.getWaypoint());
        map.recordArrival(2, target, bm, net);
        assertNotNull("the same chunk on another floor keeps it", map.getWaypoint());
        map.recordArrival(1, target, bm, net);
        assertNull(map.getWaypoint());
    }

    @Test
    public void thePinCyclesThroughEveryIconThenClears() {
        GridPoint2 c = new GridPoint2(5, -4);
        for (MapKnowledge.Pin expected : MapKnowledge.Pin.values()) {
            map.cyclePin(1, c);
            assertSame(expected, map.getPin(1, c));
        }
        assertNull(map.getPin(2, c));
        map.cyclePin(1, c);
        assertNull(map.getPin(1, c));
        assertEquals(6, MapKnowledge.Pin.values().length);
    }

    @Test
    public void aNewWorldIsForgottenEntirely() {
        GridPoint2 here = shelter(ShelterRoads.CASTLE_ROAD, 0);
        map.recordArrival(1, here, bm, net);
        map.cyclePin(1, here);
        map.setWaypoint(1, new GridPoint2(9, 9));

        map.forgetWorld();

        assertFalse(map.isGlimpsed(new GridPoint2(here.x + 1, here.y)));
        assertFalse(map.isSighted(shelter(ShelterRoads.CASTLE_ROAD, 1)));
        assertNull(map.getPin(1, here));
        assertNull(map.getWaypoint());
    }

    @Test
    public void theTilePinCyclesThroughEveryIconThenClears() {
        GridPoint2 c = new GridPoint2(3, -2);
        for (MapKnowledge.Pin expected : MapKnowledge.Pin.values()) {
            map.cycleTilePin(1, c, 5, 7);
            assertSame(expected, map.getTilePin(1, c, 5, 7));
        }
        assertNull("different tile has no pin", map.getTilePin(1, c, 5, 8));
        assertNull("different floor has no pin", map.getTilePin(2, c, 5, 7));
        map.cycleTilePin(1, c, 5, 7);
        assertNull("cleared after last pin", map.getTilePin(1, c, 5, 7));
    }

    @Test
    public void tilePinsAreForgottenOnNewWorld() {
        GridPoint2 c = new GridPoint2(2, 4);
        map.cycleTilePin(1, c, 10, 10);
        assertNotNull(map.getTilePin(1, c, 10, 10));
        map.forgetWorld();
        assertNull(map.getTilePin(1, c, 10, 10));
        assertTrue(map.getTilePins(1, c).isEmpty());
    }

    @Test
    public void highestPriorityTilePinReflectsDangerOverLoot() {
        GridPoint2 c = new GridPoint2(0, 0);
        map.setTilePin(1, c, 2, 2, MapKnowledge.Pin.LOOT);
        assertEquals(MapKnowledge.Pin.LOOT, map.getHighestPriorityTilePin(1, c));
        map.setTilePin(1, c, 4, 4, MapKnowledge.Pin.DANGER);
        assertEquals("Danger takes priority over loot", MapKnowledge.Pin.DANGER, map.getHighestPriorityTilePin(1, c));
    }
}

