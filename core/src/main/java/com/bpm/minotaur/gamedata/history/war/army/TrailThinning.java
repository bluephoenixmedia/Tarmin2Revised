package com.bpm.minotaur.gamedata.history.war.army;

import java.util.SplittableRandom;

/**
 * An army's road through the natives (Living War A29): about half of what lived on a chunk it
 * crossed is gone, and always the same half, so a chunk thinned on a reload stays as it was.
 */
public final class TrailThinning {

    private static final double GONE = 0.5;

    private TrailThinning() {
    }

    /** Whether the native numbered {@code native} on this chunk fell to the host that passed. */
    public static boolean fell(long seed, int chunkX, int chunkY, int season, int nativeIndex) {
        long mix = seed ^ (chunkX * 0x9E3779B97F4A7C15L) ^ (chunkY * 0xC2B2AE3D27D4EB4FL) ^ (season * 0x165667B19E3779F9L) ^ (nativeIndex * 0xD6E8FEB86659FD93L);
        return new SplittableRandom(mix).nextDouble() < GONE;
    }
}
