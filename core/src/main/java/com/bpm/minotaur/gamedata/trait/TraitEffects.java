package com.bpm.minotaur.gamedata.trait;

import com.bpm.minotaur.gamedata.effects.StatusEffectType;

/**
 * The trait the player holds right now, as the rest of the game sees it.
 *
 * <p>Shops, the Divinity counter, the light and the monster AI have no player in reach, so the active
 * trait is held here and read with {@link #mult} and {@link #add}. The player sets it when a trait is
 * chosen or a save is loaded, and clears it when a new player is made.
 */
public final class TraitEffects {

    private static TraitDefinition current;

    private TraitEffects() {
    }

    public static void set(TraitDefinition trait) {
        current = trait;
    }

    public static void clear() {
        current = null;
    }

    public static TraitDefinition current() {
        return current;
    }

    /** A scaling effect; 1 when there is no trait or it does not touch this number. */
    public static float mult(String key) {
        return current == null ? 1f : current.mod(key, 1f);
    }

    /** An additive effect; 0 when there is no trait or it does not touch this number. */
    public static float add(String key) {
        return current == null ? 0f : current.mod(key, 0f);
    }

    /** Whole points added to an attribute (STR, DEX, CON, INT, WIS, AGI, CHA). */
    public static int stat(String stat) {
        return Math.round(add("stat." + stat));
    }

    /**
     * Damage over time (poison, bleeding) after a trait that softens it. A fraction is rolled, so
     * 1 damage at 0.5 lands about half the time rather than rounding to nothing or to everything.
     */
    public static int scaleOverTime(int damage, java.util.Random rng) {
        float scaled = damage * mult("overTimeMult");
        int whole = (int) scaled;
        return whole + (rng.nextFloat() < scaled - whole ? 1 : 0);
    }

    public static boolean blocks(StatusEffectType type) {
        return current != null && current.blocked.contains(type);
    }
}
