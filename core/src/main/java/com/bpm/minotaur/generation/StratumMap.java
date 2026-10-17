package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;

/**
 * Which {@link Stratum} a chunk of a level is (plan D36, T4.1): depth bands choose the strata a
 * level may hold, and a slow noise field over the chunk grid lays them out in regions several
 * chunks across, so a stratum is a place you walk through, not a checkerboard.
 */
public final class StratumMap {

    /** Noise frequency over chunk coordinates: regions run several chunks across. */
    static final float REGION_FREQUENCY = 0.045f;

    private static final Stratum[] SHALLOW = {Stratum.MAZE, Stratum.MAZE, Stratum.FUNGAL_FOREST, Stratum.FLOODED_HALLS};
    private static final Stratum[] MIDDLE = {Stratum.MAZE, Stratum.FUNGAL_FOREST, Stratum.FLOODED_HALLS, Stratum.OSSUARY};
    private static final Stratum[] DEEP = {Stratum.MAZE, Stratum.OSSUARY, Stratum.MAGMA_DEEPS, Stratum.MAGMA_DEEPS};

    private StratumMap() {
    }

    /** The strata a level can hold, by depth band; level 1 is the surface and has none. */
    public static Stratum[] band(int level) {
        if (level <= 1) return new Stratum[]{Stratum.MAZE};
        if (level <= 3) return SHALLOW;
        if (level <= 5) return MIDDLE;
        return DEEP;
    }

    public static Stratum of(long worldSeed, GridPoint2 chunk, int level) {
        Stratum[] band = band(level);
        if (band.length == 1) return band[0];
        FastNoiseLite noise = new FastNoiseLite((int) (worldSeed ^ (worldSeed >>> 32)) ^ (level * 7919));
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(REGION_FREQUENCY);
        float n = (noise.GetNoise(chunk.x, chunk.y) + 1f) / 2f;
        int index = Math.min(band.length - 1, Math.max(0, (int) (n * band.length)));
        return band[index];
    }
}
