package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.DoctrineCatalog;
import com.bpm.minotaur.gamedata.history.DoctrineCatalogTest;
import com.bpm.minotaur.gamedata.history.HistorySimulator;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/** A holdfast always stands on open land, as little moved from where the seed put it as can be. */
public class SeatMapTest {

    private static final long[] SEEDS = {1000L, 8919L, 24757L, 40595L, 135623L, 151461L, 7L, 21L, 77L, 2024L};

    private static DoctrineCatalog catalog;

    @BeforeClass
    public static void load() throws IOException {
        catalog = DoctrineCatalogTest.loadCatalog();
    }

    private static HistoryWorld world(long seed) {
        return HistorySimulator.prehistory(seed, catalog);
    }

    private static SeatMap seats(HistoryWorld w, BiomeManager biomes, long seed) {
        return SeatMap.of(w, biomes.getRoads(), biomes.getCastleSite(), seed, biomes::isOpenLand);
    }

    @Test
    public void everyHoldfastStandsOnOpenLand() {
        int holdfasts = 0;
        for (long seed : SEEDS) {
            BiomeManager biomes = new BiomeManager(seed);
            HistoryWorld w = world(seed);
            SeatMap map = seats(w, biomes, seed);
            for (House h : w.houses()) {
                if (h.isExtinct() || h.holdsCastle || h.isGreat()) continue;
                holdfasts++;
                GridPoint2 at = map.seat(h.id);
                assertTrue("seed " + seed + ": " + h.name + " stands in " + biomes.getBiome(at) + " at " + at, biomes.isOpenLand(at));
            }
        }
        assertTrue("the seeds should hold plenty of lesser houses, found " + holdfasts, holdfasts > 20);
    }

    @Test
    public void aSeatAlreadyOnOpenLandStaysPut() {
        for (long seed : SEEDS) {
            BiomeManager biomes = new BiomeManager(seed);
            HistoryWorld w = world(seed);
            SeatMap rolled = SeatMap.of(w, biomes.getRoads(), biomes.getCastleSite(), seed, c -> true);
            SeatMap placed = seats(w, biomes, seed);
            for (House h : w.houses()) {
                if (h.isExtinct()) continue;
                if (biomes.isOpenLand(rolled.seat(h.id)) || h.holdsCastle || h.isGreat()) {
                    assertEquals("seed " + seed + ": " + h.name + " moved", rolled.seat(h.id), placed.seat(h.id));
                }
            }
        }
    }

    @Test
    public void theSameSeedGivesTheSameSeats() {
        for (long seed : SEEDS) {
            BiomeManager biomes = new BiomeManager(seed);
            HistoryWorld w = world(seed);
            SeatMap a = seats(w, biomes, seed), b = seats(w, new BiomeManager(seed), seed);
            for (House h : w.houses()) {
                if (!h.isExtinct()) assertEquals(a.seat(h.id), b.seat(h.id));
            }
        }
    }

    /** The search walks outward: a blocked point goes to the nearest free chunk, ties in one fixed order. */
    @Test
    public void aBlockedSeatMovesAsLittleAsPossible() {
        GridPoint2 from = new GridPoint2(20, 5);
        GridPoint2 moved = SeatMap.nearestOpen(from, c -> Math.max(Math.abs(c.x - 20), Math.abs(c.y - 5)) >= 3);
        assertEquals(3, Math.max(Math.abs(moved.x - 20), Math.abs(moved.y - 5)));
        assertEquals(moved, SeatMap.nearestOpen(from, c -> Math.max(Math.abs(c.x - 20), Math.abs(c.y - 5)) >= 3));
    }
}
