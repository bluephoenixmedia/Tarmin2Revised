package com.bpm.minotaur.gamedata;

import java.util.Random;

/**
 * Magic resistance: a percentage taken off the damage of spells, for the player and for monsters.
 *
 * <p>It used to be a monster-only chance to shrug an effect off completely, rolled in one place.
 * Now it is one number with one meaning: X% resistance cuts spell damage by X%, and a status effect
 * a spell carries is resisted at that same X% rate. Weapon blows are not spells and are untouched.
 *
 * <p>Neither side can reach immunity. The player's cap is lower than a monster's, because the
 * player's comes from stacking gear and should stay a choice rather than a solved build.
 */
public final class MagicResistance {

    public static final int PLAYER_CAP = 75;
    public static final int MONSTER_CAP = 90;

    private MagicResistance() {
    }

    public static int clampForPlayer(int percent) {
        return Math.max(0, Math.min(PLAYER_CAP, percent));
    }

    public static int clampForMonster(int percent) {
        return Math.max(0, Math.min(MONSTER_CAP, percent));
    }

    /**
     * Spell damage after resistance. A spell that connects always does at least 1, so a high
     * resistance blunts magic without switching it off.
     */
    public static int reduce(int damage, int resistancePercent) {
        if (damage <= 0 || resistancePercent <= 0) {
            return Math.max(0, damage);
        }
        int kept = 100 - Math.min(100, resistancePercent);
        return Math.max(1, Math.round(damage * kept / 100f));
    }

    /** Whether a status effect carried by a spell is shrugged off, at the resistance's own rate. */
    public static boolean resists(int resistancePercent, Random rng) {
        return resistancePercent > 0 && rng.nextInt(100) < resistancePercent;
    }
}
