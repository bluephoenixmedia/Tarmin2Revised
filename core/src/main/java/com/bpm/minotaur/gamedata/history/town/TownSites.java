package com.bpm.minotaur.gamedata.history.town;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.Stratum;
import com.bpm.minotaur.generation.StratumMap;

/**
 * Where towns stand (plan D37, T4.2): on strata 2 to 4 (levels 3 to 5), in strata that allow
 * them, about one in eleven of those chunks. The chunk grid is cut into cells of {@link #CELL} by {@link #CELL};
 * each cell has one candidate chunk, which holds a town if its stratum allows and a roll says so.
 */
public final class TownSites {

    /** Strata 2 to 4: level 2 is the first stratum. */
    public static final int MIN_LEVEL = 3;
    public static final int MAX_LEVEL = 5;
    static final int CELL = 3;
    static final int TOWN_PERCENT = 80;

    private TownSites() {
    }

    public static boolean isTown(long worldSeed, GridPoint2 chunk, int level) {
        if (chunk == null || level < MIN_LEVEL || level > MAX_LEVEL) return false;
        if (chunk.x == 0 && chunk.y == 0) return false;
        int cx = Math.floorDiv(chunk.x, CELL);
        int cy = Math.floorDiv(chunk.y, CELL);
        long h = mix(worldSeed ^ (cx * 0x632BE59BD9B4E019L) ^ (cy * 0x85157AF5L) ^ (level * 0x9E3779B97F4A7C15L));
        int ox = (int) Math.floorMod(h, (long) CELL);
        int oy = (int) Math.floorMod(h >>> 16, (long) CELL);
        if (chunk.x != cx * CELL + ox || chunk.y != cy * CELL + oy) return false;
        if (Math.floorMod(h >>> 32, 100L) >= TOWN_PERCENT) return false;
        Stratum stratum = StratumMap.of(worldSeed, chunk, level);
        return stratum.allowsTowns;
    }

    private static long mix(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }
}
