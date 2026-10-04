package com.bpm.minotaur.gamedata.gore;

/**
 * How hard a monster visibly reels from a blow that does not kill it.
 *
 * <p>Visual only: the sprite springs back and flashes, then returns to its
 * tile. Knocking it a tile would change the fight's rules, and blunt weapons
 * already have stun and stagger for that.
 */
public final class HitReaction {

    /** A scratch barely flinches. */
    public static final float MIN_RECOIL = 0.10f;
    /** A crit, or a blow taking a third of its health, staggers it well back. */
    public static final float MAX_RECOIL = 0.35f;
    /** Share of max HP at which a blow counts as fully heavy. */
    public static final float HEAVY_SHARE = 0.35f;

    public static final float MIN_FLASH_SEC = 0.12f;
    public static final float MAX_FLASH_SEC = 0.26f;

    private HitReaction() {
    }

    /** 0 for a scratch, 1 for a crit or a heavy blow. */
    public static float weight(int taken, int maxHp, boolean crit) {
        if (crit) return 1f;
        if (taken <= 0) return 0f;
        float share = (float) taken / (float) Math.max(1, maxHp);
        return Math.min(1f, share / HEAVY_SHARE);
    }

    /** How far the sprite springs back, in tiles. */
    public static float recoilDistance(float weight) {
        return MIN_RECOIL + (MAX_RECOIL - MIN_RECOIL) * clamp01(weight);
    }

    /** How long the flash and spring-back last. */
    public static float flashSeconds(float weight) {
        return MIN_FLASH_SEC + (MAX_FLASH_SEC - MIN_FLASH_SEC) * clamp01(weight);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
