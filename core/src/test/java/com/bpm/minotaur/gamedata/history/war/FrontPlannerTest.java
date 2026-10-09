package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.War;
import com.bpm.minotaur.generation.ShelterRoads;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/** Plan T2.1: where the houses sit, and where their wars are fought. */
public class FrontPlannerTest {

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static SeatMap seats(HistoryWorld w, long worldSeed) {
        ShelterRoads roads = new ShelterRoads(worldSeed, new GridPoint2(0, 50));
        return SeatMap.of(w, roads, new GridPoint2(0, 50), worldSeed);
    }

    /** A world with at least one war under way. */
    private static HistoryWorld atWar(long fromSeed) {
        for (long seed = fromSeed; ; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            if (!w.activeWars().isEmpty()) return w;
        }
    }

    @Test
    public void greatHousesSitAtTheirSealSitesAndTarminZulAtTheCastle() {
        HistoryWorld w = HistorySimulator.prehistory(3L, catalog);
        ShelterRoads roads = new ShelterRoads(77L, new GridPoint2(0, 50));
        SeatMap seats = SeatMap.of(w, roads, new GridPoint2(0, 50), 77L);
        for (int g = 0; g < HistoryWorld.GASH_COUNT; g++) {
            House h = w.gashHolder(g);
            assertEquals(roads.getRoad(g + 1).getEnd(), seats.seat(h.id));
        }
        assertEquals(new GridPoint2(0, 50), seats.seat(w.tarminHouse().id));
        for (House h : w.livingHouses()) {
            GridPoint2 s = seats.seat(h.id);
            assertNotNull(h.name, s);
            if (!h.isGreat() && !h.holdsCastle) {
                double d = Math.sqrt(s.x * s.x + s.y * s.y);
                assertTrue(h.name + " holdfast at " + s, d >= SeatMap.HOLDFAST_MIN && d <= SeatMap.HOLDFAST_MAX + 1);
            }
        }
    }

    @Test
    public void everyActiveWarHasOneFrontBetweenItsSeats() {
        HistoryWorld w = atWar(0);
        SeatMap seats = seats(w, 5L);
        List<Front> fronts = FrontPlanner.fronts(w, seats, 1234L);
        assertEquals(w.activeWars().size(), fronts.size());
        for (Front f : fronts) {
            War war = w.wars().get(f.warId);
            assertTrue(war.isActive());
            GridPoint2 a = seats.seat(war.attackerId), b = seats.seat(war.defenderId);
            assertTrue("front lies between the seats", f.center.x >= Math.min(a.x, b.x) - 1 && f.center.x <= Math.max(a.x, b.x) + 1);
            assertTrue(f.center.y >= Math.min(a.y, b.y) - 1 && f.center.y <= Math.max(a.y, b.y) + 1);
        }
    }

    @Test
    public void frontsMoveDeterministicallyWithTheWarClock() {
        HistoryWorld w = atWar(10);
        SeatMap seats = seats(w, 6L);
        assertEquals(FrontPlanner.fronts(w, seats, 500L).get(0).center, FrontPlanner.fronts(w, seats, 500L).get(0).center);
        boolean moved = false;
        GridPoint2 first = FrontPlanner.fronts(w, seats, 0L).get(0).center;
        for (long clock = 0; clock < FrontPlanner.PERIOD; clock += 50) {
            if (!FrontPlanner.fronts(w, seats, clock).get(0).center.equals(first)) moved = true;
        }
        assertTrue("a front drifts as the war goes on", moved);
    }

    @Test
    public void aFrontCanCrossARoad() {
        boolean crossed = false;
        for (long seed = 0; seed < 30 && !crossed; seed++) {
            HistoryWorld w = HistorySimulator.prehistory(seed, catalog);
            if (w.activeWars().isEmpty()) continue;
            ShelterRoads roads = new ShelterRoads(seed, new GridPoint2(0, 50));
            SeatMap seats = SeatMap.of(w, roads, new GridPoint2(0, 50), seed);
            for (long clock = 0; clock < FrontPlanner.PERIOD && !crossed; clock += 25) {
                for (Front f : FrontPlanner.fronts(w, seats, clock)) {
                    for (GridPoint2 c : f.chunks()) {
                        if (roads.inCorridor(c.x, c.y)) crossed = true;
                    }
                }
            }
        }
        assertTrue(crossed);
    }

    @Test
    public void anEndedWarHasNoFront() {
        HistoryWorld w = atWar(20);
        SeatMap seats = seats(w, 7L);
        int before = FrontPlanner.fronts(w, seats, 0L).size();
        War war = w.activeWars().get(0);
        for (int i = 0; i < 400 && war.isActive(); i++) HistorySimulator.tickSeason(w, catalog);
        assertFalse("war ended within 100 years", war.isActive());
        for (Front f : FrontPlanner.fronts(w, seats, 0L)) assertNotEquals(war.id, f.warId);
        assertTrue(before >= 1);
    }

    @Test
    public void aFrontCoversThreeByThreeChunks() {
        Front f = new Front(0, 1, 2, new GridPoint2(5, -3));
        assertEquals(9, f.chunks().size());
        assertTrue(f.covers(new GridPoint2(6, -2)));
        assertFalse(f.covers(new GridPoint2(7, -3)));
    }
}
