package com.bpm.minotaur.gamedata.map;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.progression.BiomePortal;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MapModelExtensionsTest {

    private BiomeManager bm;
    private ShelterNetwork net;
    private MapKnowledge map;
    private MapModel model;

    @Before
    public void setUp() {
        bm = new BiomeManager(42L);
        net = ShelterNetwork.getInstance();
        net.resetForNewGame();
        map = MapKnowledge.getInstance();
        map.resetForNewGame();
        model = new MapModel(bm, net, map, floor -> Collections.singleton(new GridPoint2(0, 0)), 1);
    }

    @Test
    public void gateDestinationReturnsBearingsForKnownBiomes() {
        for (BiomePortal portal : BiomePortal.values()) {
            GridPoint2 target = model.gateChunk(portal);
            if (target != null) {
                String bearing = MapModel.bearingFromHome(target);
                assertNotNull("Bearing must not be null for resolved gate target", bearing);
                assertFalse("Bearing must not be empty", bearing.isEmpty());
            }
        }
    }

    @Test
    public void macroMarkersContainHomeAndShelters() {
        List<MapModel.MacroMarker> markers = model.macroMarkers(1);
        assertFalse("Markers must not be empty on floor 1", markers.isEmpty());
        assertEquals("First marker on floor 1 must be Home", MapModel.MacroMarker.Type.HOME, markers.get(0).type);
    }

    @Test
    public void routeToSuggestionReturnsPointsAlongRoads() {
        GridPoint2 sugg = model.suggestion();
        if (sugg != null) {
            List<GridPoint2> route = model.routeToSuggestion(new GridPoint2(0, 0));
            assertNotNull("Route must not be null", route);
            assertFalse("Route must contain at least target chunk", route.isEmpty());
            assertEquals("Route destination must match suggestion", sugg, route.get(route.size() - 1));
        }
    }
}
