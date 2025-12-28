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
        return random.nextLong(bound);
    }
}
