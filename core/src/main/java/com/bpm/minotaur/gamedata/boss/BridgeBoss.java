package com.bpm.minotaur.gamedata.boss;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.generation.WorldConstants;

/**
 * The guardian that walks the surface when bridge integrity reaches 100%.
 *
 * <p>It is deliberately <b>not</b> a themed-chunk champion. A themed chunk locks
 * its gates on entry, and this boss is meant to roam and be fled from: the
 * tension is a thing you can see coming across the maze, not a room you are
 * sealed into. Reusing {@code themeChampion} would have brought the seal with
 * it.
 *
 * <p>Everything here is derived from the world seed and the summon count, so
 * the boss stands in the same place for a given summoning no matter how many
 * times the chunk is unloaded and rebuilt.
 */
public final class BridgeBoss {

    /** Reusing an existing type with existing art means tuning a fight, not building one. */
    public static final Monster.MonsterType TYPE = Monster.MonsterType.IRON_GOLEM;

    /**
     * Billboard width, set to exactly the renderer's clamp.
     *
     * <p>{@code World3DRenderer} caps monster billboard <em>width</em> at 0.82
     * and scales height down proportionally to match. An authored
     * {@code scale.x} above that silently shrinks the boss: at (2.2, 2.2) it
     * renders 0.82 high, which is shorter than a 1.0 wall. Sitting exactly on
     * the clamp lets all the height through untouched.
     */
    public static final float SCALE_X = 0.82f;

    /** Two and a half tiles, so head and shoulders clear a 1.0 wall from a distance. */
    public static final float SCALE_Y = 2.5f;

    /** The player's own speed: you can hold distance, but never shake it. */
    public static final int MOVE_SPEED = 12;

    /** Authored difficulty for the first summoning; later ones scale off this. */
    public static final int BASE_HP_BONUS = 120;

    private static final long BOSS_STREAM = 0x4252_4944_4745_424FL;

    private BridgeBoss() {
    }

    /**
     * Which level-1 chunk this summoning stands in.
     *
     * <p>Random across the maze, never the shelter chunk. The shelter is the
     * preparation space the feature exists to make matter, and it is also the
     * respawn tile, so a boss standing there is a death loop.
     *
     * @param summons the summoning number, so each one picks a fresh chunk
     */
    public static GridPoint2 chunkFor(long worldSeed, int summons) {
        int radius = WorldConstants.CENTRAL_MAZE_RADIUS;
        int span = radius * 2 + 1;

        long h = mix(worldSeed ^ BOSS_STREAM ^ (summons * 0x9E3779B97F4A7C15L));
        int x = (int) Math.floorMod(h, (long) span) - radius;
        int y = (int) Math.floorMod(mix(h), (long) span) - radius;

        if (x == 0 && y == 0) {
            // Nudge off the shelter rather than re-rolling, so the result stays
            // a pure function of the inputs.
            x = 1;
        }
        return new GridPoint2(x, y);
    }

    /** True when this chunk hosts the current summoning. */
    public static boolean isBossChunk(long worldSeed, int summons, int level, GridPoint2 chunkId) {
        if (level != 1 || chunkId == null || summons <= 0) return false;
        return chunkFor(worldSeed, summons).equals(chunkId);
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
