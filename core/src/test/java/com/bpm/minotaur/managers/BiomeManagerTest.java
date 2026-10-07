package com.bpm.minotaur.managers;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.progression.BiomePortal;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.WorldConstants;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Pins the world layout rules in docs/DEsign/Requirements_ Procedural World &amp;
 * The Blighted Marches.md: legacy worlds keep their old layout; current worlds
 * follow the portal ladder outward, put Castle Tarmin 40-60 chunks out in the
 * Blight, and can always walk there.
 */
public class BiomeManagerTest {

    private static final int SEEDS = 40;
    private static final int R = WorldConstants.CENTRAL_MAZE_RADIUS;

    private static long seed(int i) {
        return 0x9E3779B97F4A7C15L * (i + 1) ^ 0x5DEECE66DL;
    }

    private static boolean passable(Biome b) {
        return b != Biome.OCEAN && b != Biome.MOUNTAINS;
    }

    @Test
    public void legacyWorldsKeepTheForestRingAndTheNorthernTundraBand() {
        BiomeManager legacy = new BiomeManager();
        assertTrue(legacy.isLegacy());
        assertNull("legacy worlds have no castle site", legacy.getCastleSite());

        for (int x = -60; x <= 60; x++) {
            for (int y = -60; y <= 60; y++) {
                Biome b = legacy.getBiome(new GridPoint2(x, y));
                int cheb = Math.max(Math.abs(x), Math.abs(y));
                assertNotSame("legacy worlds never generate the Blight", Biome.BLIGHT, b);
                if (cheb <= R) {
                    assertSame(Biome.MAZE, b);
                } else if (cheb <= R + WorldConstants.FOREST_BORDER_SIZE) {
                    assertSame("forest ring at " + x + "," + y, Biome.FOREST, b);
                } else if (passable(b)) {
                    assertEquals("Tundra exactly where y >= 16 at " + x + "," + y, y >= 16, b == Biome.TUNDRA);
                }
            }
        }
    }

    @Test
    public void legacyWorldsMatchThePreVersioningAlgorithmChunkForChunk() {
        // A frozen copy of BiomeManager.getBiome as it stood on develop before world-gen
        // versions (9b2c6ba1). Legacy saves have chunks on disk placed by it.
        com.bpm.minotaur.generation.FastNoiseLite noise = new com.bpm.minotaur.generation.FastNoiseLite(12345);
        noise.SetNoiseType(com.bpm.minotaur.generation.FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.02f);
        com.bpm.minotaur.generation.FastNoiseLite humidity = new com.bpm.minotaur.generation.FastNoiseLite(12345 + 1013);
        humidity.SetNoiseType(com.bpm.minotaur.generation.FastNoiseLite.NoiseType.OpenSimplex2);
        humidity.SetFrequency(0.08f);

        BiomeManager legacy = new BiomeManager();
        for (int x = -60; x <= 60; x++) {
            for (int y = -60; y <= 60; y++) {
                Biome expected;
                int cheb = Math.max(Math.abs(x), Math.abs(y));
                float e = noise.GetNoise(x, y);
                float h = humidity.GetNoise(x, y);
                if (cheb <= 10) expected = Biome.MAZE;
                else if (cheb <= 15) expected = Biome.FOREST;
                else if (e < -0.3f) expected = Biome.OCEAN;
                else if (e > 0.6f) expected = Biome.MOUNTAINS;
                else if (y >= 16) expected = Biome.TUNDRA;
                else if (h < -0.30f) expected = Biome.DESERT;
                else if (h > 0.30f) expected = Biome.LAKELANDS;
                else expected = Biome.FOREST;
                assertSame("legacy chunk " + x + "," + y, expected, legacy.getBiome(new GridPoint2(x, y)));
            }
        }
    }

    @Test
    public void worldGenVersionRoundTripsAndOldSavesReadAsLegacy() {
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        com.bpm.minotaur.gamedata.save.WorldSaveData data = new com.bpm.minotaur.gamedata.save.WorldSaveData();
        data.worldGenVersion = WorldConstants.WORLD_GEN_CURRENT;
        com.bpm.minotaur.gamedata.save.WorldSaveData back =
                json.fromJson(com.bpm.minotaur.gamedata.save.WorldSaveData.class, json.toJson(data));
        assertEquals(WorldConstants.WORLD_GEN_CURRENT, back.worldGenVersion);

        com.bpm.minotaur.gamedata.save.WorldSaveData old =
                json.fromJson(com.bpm.minotaur.gamedata.save.WorldSaveData.class, "{masterSeed:777,currentLevel:1}");
        assertEquals("a save from before versions is legacy", WorldConstants.WORLD_GEN_LEGACY, old.worldGenVersion);
    }

    @Test
    public void theLegacyLayoutIgnoresTheWorldSeed() {
        BiomeManager a = new BiomeManager(1L, WorldConstants.WORLD_GEN_LEGACY);
        BiomeManager b = new BiomeManager(987654321L, WorldConstants.WORLD_GEN_LEGACY);
        for (int x = -40; x <= 40; x += 3) {
            for (int y = -40; y <= 40; y += 3) {
                GridPoint2 c = new GridPoint2(x, y);
                assertSame(a.getBiome(c), b.getBiome(c));
            }
        }
    }

    @Test
    public void theSameSeedAlwaysLaysOutTheSameWorld() {
        BiomeManager a = new BiomeManager(42L);
        BiomeManager b = new BiomeManager(42L);
        assertEquals(a.getCastleSite(), b.getCastleSite());
        for (int x = -70; x <= 70; x += 7) {
            for (int y = -70; y <= 70; y += 7) {
                GridPoint2 c = new GridPoint2(x, y);
                assertSame(a.getBiome(c), b.getBiome(c));
            }
        }
        assertNotEquals("different seeds place the castle differently",
                a.getCastleSite(), new BiomeManager(43L).getCastleSite());
    }

    @Test
    public void theMazeIsUnchangedAndTheWayOutIsAlwaysOpen() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            for (int x = -R - WorldConstants.CLEAR_GROUND_RADIUS; x <= R + WorldConstants.CLEAR_GROUND_RADIUS; x++) {
                for (int y = -R - WorldConstants.CLEAR_GROUND_RADIUS; y <= R + WorldConstants.CLEAR_GROUND_RADIUS; y++) {
                    Biome b = bm.getBiome(new GridPoint2(x, y));
                    if (Math.max(Math.abs(x), Math.abs(y)) <= R) {
                        assertSame(Biome.MAZE, b);
                    } else {
                        assertTrue("no sea or mountain next to the maze, seed " + i + " at " + x + "," + y, passable(b));
                    }
                }
            }
        }
    }

    @Test
    public void castleTarminStandsFortyToSixtyChunksOutInTheBlight() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            GridPoint2 site = bm.getCastleSite();
            assertNotNull(site);
            double dist = Math.hypot(site.x, site.y);
            assertTrue("seed " + i + " castle at " + dist,
                    dist >= WorldConstants.CASTLE_MIN_DISTANCE - 1 && dist <= WorldConstants.CASTLE_MAX_DISTANCE + 1);
            assertSame(Biome.BLIGHT, bm.getBiome(site));
            assertTrue(bm.isCastleChunk(site));
            // The guaranteed Blight around it.
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = -5; dy <= 5; dy++) {
                    if (dx * dx + dy * dy > 25) continue;
                    assertSame("Blight surrounds the castle, seed " + i,
                            Biome.BLIGHT, bm.getBiome(new GridPoint2(site.x + dx, site.y + dy)));
                }
            }
        }
    }

    @Test
    public void theCastleCanAlwaysBeReachedOnFootFromTheMaze() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            assertTrue("seed " + i + ": no overland route to the castle",
                    walkable(bm, bm.getCastleSite()).contains(bm.getCastleSite()));
        }
    }

    @Test
    public void theCrimsonGateLandsOnBlightThatReachesTheCastle() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            GridPoint2 entry = bm.findCorridorBlightEntry();
            assertSame(Biome.BLIGHT, bm.getBiome(entry));
            assertTrue("seed " + i + ": arrival is cut off from the castle",
                    walkableFrom(bm, entry, 90).contains(bm.getCastleSite()));
        }
    }

    @Test
    public void biomesAppearOutwardInPortalLadderOrder() {
        Biome[] ladder = {Biome.FOREST, Biome.DESERT, Biome.LAKELANDS, Biome.TUNDRA, Biome.BLIGHT};
        double[] meanDistance = new double[ladder.length];
        int[] counts = new int[ladder.length];
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            for (int x = -70; x <= 70; x += 2) {
                for (int y = -70; y <= 70; y += 2) {
                    int d = Math.max(Math.abs(x), Math.abs(y)) - R;
                    if (d <= 0) continue;
                    Biome b = bm.getBiome(new GridPoint2(x, y));
                    for (int k = 0; k < ladder.length; k++) {
                        if (ladder[k] == b) {
                            meanDistance[k] += d;
                            counts[k]++;
                        }
                    }
                }
            }
        }
        for (int k = 0; k < ladder.length; k++) {
            assertTrue(ladder[k] + " never generated", counts[k] > 0);
            meanDistance[k] /= counts[k];
        }
        for (int k = 1; k < ladder.length; k++) {
            assertTrue(ladder[k - 1] + " (" + meanDistance[k - 1] + ") should lie nearer than "
                    + ladder[k] + " (" + meanDistance[k] + ")", meanDistance[k - 1] < meanDistance[k]);
        }
    }

    @Test
    public void directionDoesNotPredictTheTundra() {
        // The old layout put all Tundra in the north. Now every quadrant gets some.
        int[] quadrant = new int[4];
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            for (int x = -50; x <= 50; x += 2) {
                for (int y = -50; y <= 50; y += 2) {
                    if (bm.getBiome(new GridPoint2(x, y)) != Biome.TUNDRA) continue;
                    quadrant[(x >= 0 ? 0 : 1) + (y >= 0 ? 0 : 2)]++;
                }
            }
        }
        int total = quadrant[0] + quadrant[1] + quadrant[2] + quadrant[3];
        for (int q = 0; q < 4; q++) {
            assertTrue("quadrant " + q + " has " + quadrant[q] + " of " + total + " Tundra samples",
                    quadrant[q] > total / 8);
        }
    }

    @Test
    public void everyPortalDestinationGeneratesInEveryWorld() {
        for (int i = 0; i < SEEDS; i++) {
            BiomeManager bm = new BiomeManager(seed(i));
            Set<Biome> seen = EnumSet.noneOf(Biome.class);
            for (int x = -60; x <= 60; x++) {
                for (int y = -60; y <= 60; y++) {
                    seen.add(bm.getBiome(new GridPoint2(x, y)));
                }
            }
            for (BiomePortal portal : BiomePortal.values()) {
                assertTrue("seed " + i + " never generates " + portal.getDestination(),
                        seen.contains(portal.getDestination()));
            }
        }
    }

    /** Chunks walkable from the maze edge, within the castle's bounding radius. */
    private static Set<GridPoint2> walkable(BiomeManager bm, GridPoint2 site) {
        return walkableFrom(bm, new GridPoint2(R + 1, 0), (int) Math.ceil(Math.hypot(site.x, site.y)) + 12);
    }

    private static Set<GridPoint2> walkableFrom(BiomeManager bm, GridPoint2 start, int bound) {
        Set<GridPoint2> seen = new HashSet<>();
        Deque<GridPoint2> queue = new ArrayDeque<>();
        // Seed with every wilderness chunk on the maze's edge; the maze connects them all.
        if (Math.max(Math.abs(start.x), Math.abs(start.y)) == R + 1) {
            for (int i = -R - 1; i <= R + 1; i++) {
                for (GridPoint2 c : new GridPoint2[]{new GridPoint2(i, R + 1), new GridPoint2(i, -R - 1),
                        new GridPoint2(R + 1, i), new GridPoint2(-R - 1, i)}) {
                    if (passable(bm.getBiome(c)) && seen.add(c)) queue.add(c);
                }
            }
        } else {
            seen.add(start);
            queue.add(start);
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            GridPoint2 c = queue.poll();
            for (int[] s : steps) {
                GridPoint2 n = new GridPoint2(c.x + s[0], c.y + s[1]);
                if (Math.max(Math.abs(n.x), Math.abs(n.y)) > bound) continue;
                Biome b = bm.getBiome(n);
                if (b == Biome.MAZE || !passable(b) || !seen.add(n)) continue;
                queue.add(n);
            }
        }
        return seen;
    }
}
