package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.managers.BiomeManager;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Pins the road layout in docs/DEsign/Requirements_ Shelter Roads.md, section 4: four
 * roads about 90 degrees apart, one to the castle and three to seal sites at the
 * configured distances, shelters spaced so the next is always in beacon range, and
 * every road walkable from the maze.
 */
public class ShelterRoadsTest {

    private static final int SEEDS = 40;
    private static final int R = WorldConstants.CENTRAL_MAZE_RADIUS;

    private static long seed(int i) {
        return 0x9E3779B97F4A7C15L * (i + 7) ^ 0x2545F4914F6CDD1DL;
    }

    private static int cheb(GridPoint2 c) {
        return Math.max(Math.abs(c.x), Math.abs(c.y));
    }

    @Test
    public void fourRoadsLeaveTheMazeAboutNinetyDegreesApart() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            ShelterRoads roads = bm.getRoads();
            assertNotNull(roads);
            assertEquals(4, roads.getRoads().size());
            for (int r = 0; r < 4; r++) {
                double a = roads.getRoad(r).getBearingDegrees();
                double b = roads.getRoad((r + 1) % 4).getBearingDegrees();
                double gap = ((a - b) % 360 + 360) % 360; // roads run clockwise
                assertEquals("seed " + i + " roads " + r + "/" + ((r + 1) % 4), 90.0, gap, 15.0);
            }
        }
    }

    @Test
    public void theCastleRoadEndsAtTheCastleAndTheSealRoadsAtTheConfiguredDistances() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            ShelterRoads roads = bm.getRoads();
            assertSame(ShelterRoads.Kind.CASTLE, roads.getRoad(0).getKind());
            assertEquals(bm.getCastleSite(), roads.getRoad(0).getEnd());

            List<Integer> wanted = new ArrayList<>();
            for (int d : WorldConstants.SEAL_SITE_DISTANCES) wanted.add(d);
            List<Integer> got = new ArrayList<>();
            for (int r = 1; r < 4; r++) {
                ShelterRoads.Road road = roads.getRoad(r);
                assertSame(ShelterRoads.Kind.SEAL, road.getKind());
                GridPoint2 end = road.getEnd();
                got.add((int) Math.round(Math.hypot(end.x, end.y)));
                assertTrue("seal site inside the maze", cheb(end) > R);
            }
            Collections.sort(got);
            Collections.sort(wanted);
            for (int k = 0; k < 3; k++) {
                assertEquals("seed " + i, wanted.get(k), got.get(k), 1.0);
            }
        }
    }

    @Test
    public void theNextShelterOnARoadIsAlwaysInBeaconRange() {
        for (int i = 0; i < SEEDS; i++) {
            ShelterRoads roads = new BiomeManager(seed(i)).getRoads();
            for (ShelterRoads.Road road : roads.getRoads()) {
                List<GridPoint2> stops = road.getShelters();
                assertFalse("seed " + i + " road " + road.getIndex() + " has no shelter", stops.isEmpty());
                GridPoint2 prev = null;
                for (GridPoint2 s : stops) {
                    assertTrue("shelter in the maze", cheb(s) > R);
                    if (prev != null) {
                        double gap = Math.hypot(s.x - prev.x, s.y - prev.y);
                        assertTrue("gap " + gap, gap >= WorldConstants.ROAD_SHELTER_SPACING_MIN - 1.5);
                        assertTrue("gap " + gap, gap <= WorldConstants.BEACON_RANGE_CHUNKS);
                    }
                    prev = s;
                }
                GridPoint2 last = stops.get(stops.size() - 1);
                double toEnd = Math.hypot(road.getEnd().x - last.x, road.getEnd().y - last.y);
                assertTrue("seed " + i + ": road end " + toEnd + " chunks past the last shelter",
                        toEnd <= WorldConstants.BEACON_RANGE_CHUNKS);
            }
        }
    }

    @Test
    public void noTwoShelterSitesStandCloserThanTheMinimumSpacing() {
        for (int i = 0; i < 12; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            List<GridPoint2> all = new ArrayList<>();
            for (ShelterRoads.Site s : bm.getRoads().sheltersWithin(new GridPoint2(0, 0), 70, bm::isOpenLand)) {
                all.add(s.getChunk());
            }
            for (ShelterRoads.Road road : bm.getRoads().getRoads()) all.add(road.getEnd());
            for (int a = 0; a < all.size(); a++) {
                for (int b = a + 1; b < all.size(); b++) {
                    GridPoint2 p = all.get(a);
                    GridPoint2 q = all.get(b);
                    // Stops on one road are spaced by the road rule, which is tested above.
                    if (sameRoad(bm.getRoads(), p, q)) continue;
                    assertTrue("seed " + i + ": " + p + " and " + q + " too close",
                            Math.hypot(p.x - q.x, p.y - q.y) >= WorldConstants.SHELTER_MIN_SPACING - 0.01);
                }
            }
        }
    }

    @Test
    public void offRoadSheltersExistAndStandOnOpenLand() {
        int total = 0;
        for (int i = 0; i < 12; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            for (ShelterRoads.Site s : bm.getRoads().sheltersWithin(new GridPoint2(0, 0), 60, bm::isOpenLand)) {
                if (s.isOffRoad()) {
                    total++;
                    assertTrue(bm.isOpenLand(s.getChunk()));
                    assertEquals(s, bm.getShelterSite(s.getChunk()));
                }
            }
        }
        assertTrue("expected off-road shelters, found " + total, total > 12 * 4);
    }

    @Test
    public void everyRoadShelterAndRoadEndCanBeReachedOnFoot() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            Set<GridPoint2> reach = walkable(bm, 75);
            for (ShelterRoads.Road road : bm.getRoads().getRoads()) {
                for (GridPoint2 s : road.getShelters()) {
                    assertTrue("seed " + i + ": shelter " + s + " unreachable", reach.contains(s));
                    ShelterRoads.Site site = bm.getShelterSite(s);
                    assertNotNull(site);
                    assertEquals(road.getIndex(), site.getRoad());
                }
                assertTrue("seed " + i + ": road end " + road.getEnd() + " unreachable", reach.contains(road.getEnd()));
            }
        }
    }

    @Test
    public void portalsLandAtAShelterOfTheirBiomePreferringTheCastleRoad() {
        Biome[] portalBiomes = {Biome.FOREST, Biome.DESERT, Biome.LAKELANDS, Biome.TUNDRA, Biome.BLIGHT};
        int found = 0, total = 0;
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            ShelterRoads roads = bm.getRoads();
            for (Biome b : portalBiomes) {
                total++;
                GridPoint2 at = roads.portalArrival(b, bm::getBiome);
                if (at == null) continue;
                found++;
                assertSame(b, bm.getBiome(at));
                assertNotNull("lands at a shelter", bm.getShelterSite(at));
                for (GridPoint2 c : roads.getRoad(ShelterRoads.CASTLE_ROAD).getShelters()) {
                    if (bm.getBiome(c) == b) {
                        assertEquals("castle road first", c, at);
                        break;
                    }
                }
            }
        }
        assertTrue("portals mostly land at a shelter: " + found + "/" + total, found >= total * 0.8);
    }

    @Test
    public void theSameSeedLaysOutTheSameRoads() {
        ShelterRoads a = new BiomeManager(seed(3)).getRoads();
        ShelterRoads b = new BiomeManager(seed(3)).getRoads();
        for (int r = 0; r < 4; r++) {
            assertEquals(a.getRoad(r).getEnd(), b.getRoad(r).getEnd());
            assertEquals(a.getRoad(r).getShelters(), b.getRoad(r).getShelters());
        }
    }

    @Test
    public void olderWorldGenVersionsHaveNoRoads() {
        assertNull(new BiomeManager(seed(1), WorldConstants.WORLD_GEN_LEGACY).getRoads());
        assertNull(new BiomeManager(seed(1), WorldConstants.WORLD_GEN_BANDS).getRoads());
        assertEquals(WorldConstants.WORLD_GEN_BANDS, new BiomeManager(seed(1), WorldConstants.WORLD_GEN_BANDS).getVersion());
    }

    private static boolean sameRoad(ShelterRoads roads, GridPoint2 p, GridPoint2 q) {
        for (ShelterRoads.Road road : roads.getRoads()) {
            boolean hasP = road.getShelters().contains(p) || road.getEnd().equals(p);
            boolean hasQ = road.getShelters().contains(q) || road.getEnd().equals(q);
            if (hasP && hasQ) return true;
        }
        return false;
    }

    /** Chunks walkable on foot from the maze edge. */
    private static Set<GridPoint2> walkable(BiomeManager bm, int bound) {
        Set<GridPoint2> seen = new HashSet<>();
        Deque<GridPoint2> queue = new ArrayDeque<>();
        for (int i = -R - 1; i <= R + 1; i++) {
            for (GridPoint2 c : new GridPoint2[]{new GridPoint2(i, R + 1), new GridPoint2(i, -R - 1),
                    new GridPoint2(R + 1, i), new GridPoint2(-R - 1, i)}) {
                if (bm.isOpenLand(c) && seen.add(c)) queue.add(c);
            }
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            GridPoint2 c = queue.poll();
            for (int[] s : steps) {
                GridPoint2 n = new GridPoint2(c.x + s[0], c.y + s[1]);
                if (cheb(n) > bound) continue;
                if (!bm.isOpenLand(n) || !seen.add(n)) continue;
                queue.add(n);
            }
        }
        return seen;
    }
}
