package com.bpm.minotaur.generation;

import java.util.Random;

/**
 * NetHack-style Random Number Generator.
 * Implements core RNG functions rn2, rn1, and rnl with Luck bias.
 */
public class NetHackRNG {
    private final Random random;

    public NetHackRNG(Random random) {
        this.random = random;
    }

    public NetHackRNG() {
        this(new Random());
    }

    /**
     * Returns a random integer from 0 to x-1.
     * Equivalent to random.nextInt(x).
     *
     * @param x Upper bound (exclusive)
     * @return Random integer in [0, x)
     */
    public int rn2(int x) {
        if (x <= 0)
            return 0; // Standard safety
        return random.nextInt(x);
    }

    /**
     * Returns a random integer from 1 to x.
     * Equivalent to rn2(x) + 1.
     *
     * @param x Upper bound (inclusive)
     * @return Random integer in [1, x]
     */
    public int rnd(int x) {
        if (x <= 0)
            return 0;
        return random.nextInt(x) + 1;
    }

    /**
     * Simulates rolling n dice with x sides and summing the result.
     * Produces a normal distribution (Bell Curve).
     *
     * @param n Number of dice
     * @param x Sides per die
     * @return Sum of rolls
     */
    public int d(int n, int x) {
        if (n <= 0 || x <= 0)
            return n; // Fallback
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += rnd(x);
        }
        return sum;
    }

    /**
     * Returns a value based on exponential decay.
     * Used for enchantment generation to make high power items rare.
     * Logic: Start at 1. While rn2(x) == 0, increment.
     *
     * @param x Probability denominator (e.g. 3 means 1/3 chance to increment)
     * @return Generated value (usually small)
     */
    public int rne(int x) {
        int val = 1;
        // Cap at a reasonable limit to prevent theoretical infinite loops
        while (rn2(x) == 0 && val < 100) {
            val++;
        }
        return val;
    }

    /**
     * Returns rn2(x) + y.
     *
     * @param x Range
     * @param y Base
     * @return Standard random number with offset
     */
    public int rn1(int x, int y) {
        return rn2(x) + y;
    }

    /**
     * Luck-biased random number generator.
     * Adjusts the result of rn2(x) based on Luck.
     *
     * Logic:
     * int i = rn2(x);
     * If luck > 0 and rn2(3) == 0, i = 0 (Simplified bias).
     * If luck < 0 and rn2(3) == 0, i = x - 1.
     * Return i.
     *
     * @param x    Upper bound
     * @param luck Current Luck value (can be positive or negative)
     * @return Biased random integer
     */
    public int rnl(int x, int luck) {
        int i = rn2(x);

        if (luck > 0 && rn2(3) == 0) {
            i = 0;
        } else if (luck < 0 && rn2(3) == 0) {
            i = x - 1;
        }

        return i;
    }

    public long nextLong(long bound) {
        // Handle bound <= 0
        if (bound <= 0)
            return 0;
        // Since Random.nextLong(bound) is Java 17+, and we might be on older
        // Android/LibGDX,
        // we can simulate it if needed, but assuming modern Java for desktop:
        // Actually, java.util.Random on Android/LibGDX usually supports nextLong() but
        // not always nextLong(bound).
        // Let's implement safe fallback.
        long r = random.nextLong();
        long m = bound - 1;
        if ((bound & m) == 0) // i.e., bound is a power of 2
            r = (r & m);
        else {
            for (long u = r >>> 1; u + m - (r = u % bound) < 0; u = random.nextLong() >>> 1)
                ;
        }
        return r;
    }
}
