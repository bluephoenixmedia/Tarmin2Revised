package com.bpm.minotaur.rendering.mesh;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Wall variety has to be varied, stable, and free.
 *
 * <p>Stable is the one that bites: if the choice were rolled at render time the
 * walls would shimmer as the player left a chunk and came back, and a chunk
 * rebuilt from a save would look nothing like the one that was saved.
 */
public class WallVariantsTest {

    private static final long SEED = 0x1234_5678_9ABC_DEF0L;

    @Test
    public void theSameFaceAlwaysGetsTheSameVariant() {
        for (int i = 0; i < 100; i++) {
            assertEquals("Re-asking must never change the answer",
                    WallVariants.variantFor(SEED, 4, 7, WallVariants.FACE_NORTH),
                    WallVariants.variantFor(SEED, 4, 7, WallVariants.FACE_NORTH));
        }
        assertEquals("A chunk's palette must be stable too",
                WallVariants.paletteFor(SEED), WallVariants.paletteFor(SEED));
    }

    @Test
    public void everyVariantIsInRange() {
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 40; y++) {
                for (int face = 0; face < 4; face++) {
                    int v = WallVariants.variantFor(SEED, x, y, face);
                    assertTrue("Variant " + v + " is outside 0.." + (WallVariants.VARIANT_COUNT - 1),
                            v >= 0 && v < WallVariants.VARIANT_COUNT);
                }
            }
        }
    }

    @Test
    public void allSixVariantsActuallyGetUsedWithinOneChunk() {
        // A hash that technically ranges 0..5 but only ever returns 2 values would
        // pass the range test and still look monotonous in play.
        Set<Integer> seen = new HashSet<>();
        for (int x = 0; x < 20; x++) {
            for (int y = 0; y < 20; y++) {
                for (int face = 0; face < 4; face++) {
                    seen.add(WallVariants.variantFor(SEED, x, y, face));
                }
            }
        }
        assertEquals("A 20x20 chunk should exercise every variant",
                WallVariants.VARIANT_COUNT, seen.size());
    }

    @Test
    public void theDistributionIsNotLopsided() {
        int[] counts = new int[WallVariants.VARIANT_COUNT];
        int total = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                for (int face = 0; face < 4; face++) {
                    counts[WallVariants.variantFor(SEED, x, y, face)]++;
                    total++;
                }
            }
        }
        int expected = total / WallVariants.VARIANT_COUNT;
        for (int v = 0; v < counts.length; v++) {
            // +/-25% of even. Generous, but it catches a hash that clumps.
            assertTrue("Variant " + v + " appeared " + counts[v] + " times, expected near " + expected,
                    counts[v] > expected * 0.75 && counts[v] < expected * 1.25);
        }
    }

    @Test
    public void neighbouringFacesDisagreeOftenEnoughToLookVaried() {
        // The real failure mode of a weak hash is not being out of range, it is
        // returning the same value for every tile in a row, which reads as stripes.
        int differing = 0;
        int compared = 0;
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                int here = WallVariants.variantFor(SEED, x, y, WallVariants.FACE_NORTH);
                int right = WallVariants.variantFor(SEED, x + 1, y, WallVariants.FACE_NORTH);
                if (here != right) differing++;
                compared++;
            }
        }
        // With 6 variants, ~83% of neighbours should differ by chance.
        assertTrue("Only " + differing + "/" + compared + " horizontal neighbours differ -- hash is striping",
                differing > compared * 0.7);
    }

    @Test
    public void thefourFacesOfOneTileAreChosenIndependently() {
        int tilesWithVariety = 0;
        for (int x = 0; x < 30; x++) {
            for (int y = 0; y < 30; y++) {
                Set<Integer> faces = new HashSet<>();
                for (int face = 0; face < 4; face++) {
                    faces.add(WallVariants.variantFor(SEED, x, y, face));
                }
                if (faces.size() > 1) tilesWithVariety++;
            }
        }
        assertTrue("Faces of a tile are not varying independently (" + tilesWithVariety + "/900)",
                tilesWithVariety > 800);
    }

    @Test
    public void bothPalettesOccurAcrossChunks() {
        int green = 0, grey = 0;
        for (int cx = -10; cx <= 10; cx++) {
            for (int cy = -10; cy <= 10; cy++) {
                long chunkSeed = seedFor(cx, cy);
                if (WallVariants.paletteFor(chunkSeed) == WallVariants.Palette.GREEN) green++;
                else grey++;
            }
        }
        assertTrue("Green never occurs", green > 0);
        assertTrue("Grey never occurs", grey > 0);
        // 441 chunks; a fair split should be nowhere near 90/10.
        assertTrue("Palette split is lopsided: " + green + " green vs " + grey + " grey",
                green > 441 * 0.3 && grey > 441 * 0.3);
    }

    @Test
    public void adjacentChunksDoNotAlternatePalettePerfectly() {
        // A 50/50 split is not enough: a strict checkerboard is also 50/50, and
        // reads as obviously mechanical. Without avalanche in the hash the palette
        // came straight off the low bit of the chunk seed and did exactly that.
        int sharing = 0;
        int compared = 0;
        for (int cx = -10; cx < 10; cx++) {
            for (int cy = -10; cy <= 10; cy++) {
                if (WallVariants.paletteFor(seedFor(cx, cy))
                        == WallVariants.paletteFor(seedFor(cx + 1, cy))) {
                    sharing++;
                }
                compared++;
            }
        }
        assertTrue("Neighbouring chunks never share a palette (" + sharing + "/" + compared
                + ") -- the palette is a checkerboard, not a choice",
                sharing > compared * 0.25);
    }

    @Test
    public void aWholeChunkSharesOnePalette() {
        // The palette is a property of the chunk, not the tile: a chunk must read
        // as one place. This is really a statement about the API shape, and it
        // would fail if paletteFor ever took tile coordinates.
        long chunkSeed = seedFor(3, -4);
        WallVariants.Palette p = WallVariants.paletteFor(chunkSeed);
        for (int i = 0; i < 50; i++) {
            assertEquals(p, WallVariants.paletteFor(chunkSeed));
        }
    }

    @Test
    public void differentChunksGetDifferentWalls() {
        int same = 0;
        for (int cx = 0; cx < 20; cx++) {
            int a = WallVariants.variantFor(seedFor(cx, 0), 5, 5, WallVariants.FACE_NORTH);
            int b = WallVariants.variantFor(seedFor(cx + 1, 0), 5, 5, WallVariants.FACE_NORTH);
            if (a == b) same++;
        }
        assertTrue("The same tile in adjacent chunks nearly always matches -- "
                + "the chunk seed is not reaching the hash", same < 12);
    }

    @Test
    public void texturePathsMapToTheFilesOnDisk() {
        assertEquals("images/wall.png",
                WallVariants.texturePath(WallVariants.Palette.GREEN, 0));
        assertEquals("images/wall_2.png",
                WallVariants.texturePath(WallVariants.Palette.GREEN, 1));
        assertEquals("images/wall_6.png",
                WallVariants.texturePath(WallVariants.Palette.GREEN, 5));
        assertEquals("images/grey_wall.png",
                WallVariants.texturePath(WallVariants.Palette.GREY, 0));
        assertEquals("images/grey_wall_3.png",
                WallVariants.texturePath(WallVariants.Palette.GREY, 2));
    }

    @Test
    public void everyGeneratedTexturePathExistsOnDisk() {
        // The mapping above is only correct if it names real files.
        for (WallVariants.Palette p : WallVariants.Palette.values()) {
            for (int v = 0; v < WallVariants.VARIANT_COUNT; v++) {
                String path = WallVariants.texturePath(p, v);
                java.io.File f = new java.io.File("assets/" + path);
                if (!f.exists()) f = new java.io.File("../assets/" + path);
                assertTrue("Missing wall texture: " + path, f.exists());
            }
        }
    }

    /** Mirrors WorldManager.getChunkSeed so the test exercises real seed values. */
    private long seedFor(int chunkX, int chunkY) {
        long seed = 0xABCDEF01L;
        seed = 31 * seed + 1;
        seed = 31 * seed + chunkX;
        seed = 31 * seed + chunkY;
        return seed;
    }
}
