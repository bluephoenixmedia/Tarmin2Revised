package com.bpm.minotaur.rendering.mesh;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The default texture should dominate, with variants as occasional punctuation.
 *
 * <p>A flat roll across six wall variants showed the original masonry about a
 * sixth of the time, which reads as six different walls rather than one wall
 * with character.
 */
public class WeightedVariantTest {

    /** Murmur3 finalizer, matching what the callers feed in. */
    private long hash(long i) {
        long z = i * 0x9E3779B97F4A7C15L;
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    private int[] histogram(int variantCount, int samples) {
        int[] counts = new int[variantCount];
        for (int i = 0; i < samples; i++) {
            counts[WeightedVariant.pick(hash(i), variantCount)]++;
        }
        return counts;
    }

    @Test
    public void theDefaultTakesRoughlyThreeQuarters() {
        int samples = 200_000;
        int[] counts = histogram(6, samples);
        double share = counts[0] / (double) samples;
        assertTrue("Default took " + String.format("%.1f%%", share * 100) + ", expected ~75%",
                share > 0.73 && share < 0.77);
    }

    @Test
    public void theRemainingQuarterIsSplitEvenlyAcrossVariants() {
        int samples = 200_000;
        int[] counts = histogram(6, samples);
        // 25% across 5 alternatives is 5% each.
        double expected = samples * 0.05;
        for (int v = 1; v < counts.length; v++) {
            assertTrue("Variant " + v + " appeared " + counts[v] + " times, expected near "
                            + (int) expected,
                    counts[v] > expected * 0.8 && counts[v] < expected * 1.2);
        }
    }

    @Test
    public void everyVariantStillOccurs() {
        // Weighting must not silently retire the rarer textures.
        int[] counts = histogram(6, 50_000);
        for (int v = 0; v < counts.length; v++) {
            assertTrue("Variant " + v + " never appeared", counts[v] > 0);
        }
    }

    @Test
    public void theChoiceIsStableForTheSameSurface() {
        for (int i = 0; i < 1000; i++) {
            assertEquals(WeightedVariant.pick(hash(i), 6), WeightedVariant.pick(hash(i), 6));
        }
    }

    @Test
    public void aSingleVariantAlwaysYieldsTheDefault() {
        // Guards the ceiling case, where no default file may exist yet.
        for (int i = 0; i < 100; i++) {
            assertEquals(0, WeightedVariant.pick(hash(i), 1));
            assertEquals(0, WeightedVariant.pick(hash(i), 0));
        }
    }

    @Test
    public void twoVariantsSplitSeventyFiveTwentyFive() {
        int samples = 100_000;
        int[] counts = histogram(2, samples);
        double share = counts[0] / (double) samples;
        assertTrue("With one alternative the default should still take ~75%, got "
                + String.format("%.1f%%", share * 100), share > 0.73 && share < 0.77);
        assertTrue("The single alternative should take the rest", counts[1] > samples * 0.2);
    }

    @Test
    public void theVariantChosenIsNotTiedToTheRollThatSelectedIt() {
        // Both draws come from the same hash. If they used the same bits, the
        // alternatives would not be evenly reachable -- the roll that says "not
        // default" would also dictate which variant, collapsing the spread.
        int[] counts = histogram(6, 200_000);
        int min = Integer.MAX_VALUE, max = 0;
        for (int v = 1; v < counts.length; v++) {
            min = Math.min(min, counts[v]);
            max = Math.max(max, counts[v]);
        }
        assertTrue("Alternatives are unevenly reachable: min " + min + ", max " + max,
                max - min < max * 0.25);
    }
}
