package com.bpm.minotaur.gamedata.map;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.ShelterRoads;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * What the map shows, from what the player knows
 * (docs/DEsign/Requirements_ Expedition Map.md, sections 3, 5 and 7).
 */
public class MapModelTest {

    private static final GridPoint2 HOME = new GridPoint2(0, 0);

    private BiomeManager bm;
    private ShelterRoads roads;
    private ShelterNetwork net;
    private MapKnowledge knowledge;
    private final Map<Integer, Set<GridPoint2>> visits = new HashMap<>();

    @Before
    public void setUp() {
        bm = new BiomeManager(42L);
        roads = bm.getRoads();
        net = ShelterNetwork.getInstance();
        net.resetForNewGame();
        knowledge = MapKnowledge.getInstance();
        knowledge.resetForNewGame();
        visits.clear();
        visit(1, HOME);
    }

    private void visit(int floor, GridPoint2 c) {
        visits.computeIfAbsent(floor, f -> new HashSet<>()).add(new GridPoint2(c));
    }

    private MapModel model() {
        return new MapModel(bm, net, knowledge, f -> visits.getOrDefault(f, new HashSet<>()), 9);
    }

    private GridPoint2 shelter(int road, int i) {
        return roads.getRoad(road).getShelters().get(i);
    }

    /** Seal roads, nearest seal site first. */
    private List<ShelterRoads.Road> sealRoadsByDistance() {
        List<ShelterRoads.Road> out = new ArrayList<>();
        for (ShelterRoads.Road r : roads.getRoads()) if (r.getKind() == ShelterRoads.Kind.SEAL) out.add(r);
        out.sort((a, b) -> Float.compare(a.getEnd().dst2(HOME), b.getEnd().dst2(HOME)));
        return out;
    }

    @Test
    public void aChunkIsKnownByEnteringItOrGlimpsingIt() {
        GridPoint2 here = shelter(0, 0);
        visit(1, here);
        knowledge.recordArrival(1, here, bm, net);
        MapModel m = model();
        assertSame(MapModel.Knowledge.VISITED, m.knowledgeOf(1, here));
        assertSame(MapModel.Knowledge.GLIMPSED, m.knowledgeOf(1, new GridPoint2(here.x + 1, here.y)));
        assertSame(MapModel.Knowledge.UNKNOWN, m.knowledgeOf(1, new GridPoint2(here.x + 5, here.y + 5)));
        assertSame("glimpses are surface-only", MapModel.Knowledge.UNKNOWN,
                m.knowledgeOf(2, new GridPoint2(here.x + 1, here.y)));
    }

    @Test
    public void aShelterIsRumouredUntilEnteredAndLitOnceClaimed() {
        GridPoint2 first = shelter(0, 0);
        GridPoint2 second = shelter(0, 1);
        assertSame(MapModel.ShelterMark.HOME, model().shelterAt(HOME));
        assertNull("unknown shelters are not shown", model().shelterAt(second));

        visit(1, first);
        knowledge.recordArrival(1, first, bm, net);
        assertSame(MapModel.ShelterMark.COLD, model().shelterAt(first));
        assertSame(MapModel.ShelterMark.RUMOURED, model().shelterAt(second));

        net.claim(first);
        assertSame(MapModel.ShelterMark.LIT, model().shelterAt(first));
        assertNull("not a shelter", model().shelterAt(new GridPoint2(first.x + 1, first.y + 1)));
    }

    @Test
    public void knownSheltersAreHomeAndEveryShelterEnteredSightedOrLit() {
        GridPoint2 first = shelter(0, 0);
        GridPoint2 far = shelter(1, 0);
        visit(1, first);
        knowledge.recordArrival(1, first, bm, net);
        net.claim(far);

        Map<GridPoint2, MapModel.ShelterMark> known = new HashMap<>();
        for (MapModel.KnownShelter s : model().knownShelters()) known.put(s.getChunk(), s.getMark());

        assertSame(MapModel.ShelterMark.HOME, known.get(HOME));
        assertSame(MapModel.ShelterMark.COLD, known.get(first));
        assertSame(MapModel.ShelterMark.RUMOURED, known.get(shelter(0, 1)));
        assertSame(MapModel.ShelterMark.LIT, known.get(far));
        List<GridPoint2> castleRoad = roads.getRoad(0).getShelters();
        assertFalse("beyond beacon range", known.containsKey(castleRoad.get(castleRoad.size() - 1)));
        for (MapModel.KnownShelter s : model().knownShelters()) {
            if (s.getChunk().equals(first)) assertEquals(0, s.getRoad());
        }
    }

    @Test
    public void aRememberedShelterIsLitWithoutBeingEntered() {
        GridPoint2 far = shelter(0, 2);
        net.claim(far);
        assertSame(MapModel.ShelterMark.LIT, model().shelterAt(far));
    }

    @Test
    public void aRoadIsDrawnBetweenKnownPointsOnly() {
        GridPoint2 first = shelter(0, 0);
        assertTrue(model().knownRoadSegments().isEmpty());

        visit(1, first);
        knowledge.recordArrival(1, first, bm, net);
        List<MapModel.RoadSegment> segs = model().knownRoadSegments();

        boolean homeToFirst = false;
        boolean firstToSecond = false;
        for (MapModel.RoadSegment s : segs) {
            if (s.getRoad() != 0) continue;
            homeToFirst |= s.getFrom().equals(HOME) && s.getTo().equals(first);
            firstToSecond |= s.getFrom().equals(first) && s.getTo().equals(shelter(0, 1));
            assertFalse("the castle is not known", s.getTo().equals(bm.getCastleSite()));
        }
        assertTrue(homeToFirst);
        assertTrue("the second shelter was sighted", firstToSecond);
    }

    @Test
    public void aSealSiteAppearsWhenTheLastShelterOnItsRoadIsLit() {
        ShelterRoads.Road road = roads.getRoad(2);
        List<GridPoint2> stops = road.getShelters();
        assertFalse(model().isSealSiteKnown(2));
        net.claim(stops.get(stops.size() - 1));
        assertTrue(model().isSealSiteKnown(2));
        assertFalse(model().isSealSiteKnown(1));
        visit(1, roads.getRoad(1).getEnd());
        assertTrue("or when entered", model().isSealSiteKnown(1));
    }

    @Test
    public void theSuggestionWalksTheNearestSealRoadFirst() {
        ShelterRoads.Road nearest = sealRoadsByDistance().get(0);
        List<GridPoint2> stops = nearest.getShelters();
        visit(1, stops.get(0));
        assertEquals(stops.get(0), model().suggestion());

        net.claim(stops.get(0));
        if (stops.size() > 1) {
            visit(1, stops.get(1));
            assertEquals(stops.get(1), model().suggestion());
        }

        for (GridPoint2 c : stops) net.claim(c);
        assertEquals("then the seal site itself", nearest.getEnd(), model().suggestion());

        net.awardSeal(nearest.getIndex());
        ShelterRoads.Road next = sealRoadsByDistance().get(1);
        visit(1, next.getShelters().get(0));
        assertEquals(next.getShelters().get(0), model().suggestion());
    }

    @Test
    public void theSuggestionNeverPointsAtGroundThePlayerDoesNotKnow() {
        assertNull("no shelter on the nearest seal road is known yet", model().suggestion());
        ShelterRoads.Road nearest = sealRoadsByDistance().get(0);
        knowledge.recordArrival(1, HOME, bm, net);
        GridPoint2 first = nearest.getShelters().get(0);
        assertTrue("its beacon stands in sight of home", knowledge.isSighted(first));
        assertEquals("a sighted shelter is known", first, model().suggestion());
    }

    @Test
    public void withEverySealWonTheSuggestionTakesTheCastleRoad() {
        for (int r = 1; r < ShelterRoads.ROAD_COUNT; r++) net.awardSeal(r);
        visit(1, shelter(0, 0));
        assertEquals(shelter(0, 0), model().suggestion());
        for (GridPoint2 c : roads.getRoad(0).getShelters()) net.claim(c);
        assertEquals("the castle's bearing is always known", bm.getCastleSite(), model().suggestion());
    }

    @Test
    public void onlyGroundThePlayerKnowsCanBeMarked() {
        GridPoint2 first = shelter(0, 0);
        GridPoint2 nowhere = new GridPoint2(first.x + 7, first.y - 7);
        assertTrue("home", model().isMarkable(1, HOME));
        assertFalse(model().isMarkable(1, nowhere));

        visit(1, first);
        knowledge.recordArrival(1, first, bm, net);
        MapModel m = model();
        assertTrue("entered", m.isMarkable(1, first));
        assertTrue("glimpsed", m.isMarkable(1, new GridPoint2(first.x + 1, first.y)));
        assertTrue("a rumoured shelter", m.isMarkable(1, shelter(0, 1)));
        assertFalse("the castle, unreached", m.isMarkable(1, bm.getCastleSite()));

        visit(3, nowhere);
        assertTrue("an explored stratum chunk", model().isMarkable(3, nowhere));
        assertFalse(model().isMarkable(2, nowhere));

        net.claim(roads.getRoad(1).getShelters().get(roads.getRoad(1).getShelters().size() - 1));
        assertTrue("a revealed seal site", model().isMarkable(1, roads.getRoad(1).getEnd()));
    }

    @Test
    public void theDepthBadgeIsTheDeepestStratumExploredBeneath() {
        GridPoint2 c = new GridPoint2(4, -2);
        assertEquals(0, model().deepestStratumBelow(c));
        visit(2, c);
        visit(4, c);
        visit(3, new GridPoint2(9, 9));
        assertEquals("floor 4 is stratum 3", 3, model().deepestStratumBelow(c));
    }

    @Test
    public void theNearestLitShelterIsHomeUntilAnotherIsCloser() {
        GridPoint2 first = shelter(0, 0);
        GridPoint2 beyond = new GridPoint2(first.x * 2, first.y * 2);
        assertEquals(HOME, model().nearestLitShelter(beyond));
        net.claim(first);
        assertEquals(first, model().nearestLitShelter(beyond));
        assertEquals(Math.abs(first.x) + Math.abs(first.y), MapModel.distance(HOME, first));
    }

    @Test
    public void newGameArrivalSightsNearestSheltersAndPopulatesRoadSegments() {
        knowledge.recordArrival(1, HOME, bm, net);
        MapModel m = model();
        List<MapModel.RoadSegment> segments = m.knownRoadSegments();
        assertFalse("Shelter lines should be visible from home at game start", segments.isEmpty());
        boolean connectsFromHome = segments.stream().anyMatch(s -> s.getFrom().equals(HOME) || s.getTo().equals(HOME));
        assertTrue("Road segments should connect from home", connectsFromHome);
    }
}
