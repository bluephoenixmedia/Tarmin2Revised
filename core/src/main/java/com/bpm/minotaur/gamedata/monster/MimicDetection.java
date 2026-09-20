package com.bpm.minotaur.gamedata.monster;

import com.bpm.minotaur.gamedata.item.Item;

import java.util.Random;

/**
 * The passive perception check that lets an observant player spot a mimic before
 * reaching for it.
 *
 * <p>This is the first perception check in the codebase. Wisdom is the stat: the
 * Shelter Altar already describes it as "Spiritual energy, divine boons, and secret
 * perception", and it has an ascension tree where Luck does not.
 *
 * <p>The roll is spent on first contact, pass or fail, and the outcome persists on the
 * Item through chunk reloads. Both properties matter: a re-rollable check standing in
 * front of the chest turns 25% into a certainty within a few turns, and a check that
 * forgot its result would let a player walk out of the chunk and back to try again.
 */
public final class MimicDetection {

    /** Chance at Wisdom 10, before any scaling. */
    private static final int BASE_CHANCE = 25;
    /** Percentage points gained per point of Wisdom above 10. */
    private static final int PER_POINT = 5;
    /** Noticing is never impossible and never guaranteed. */
    private static final int MIN_CHANCE = 5;
    private static final int MAX_CHANCE = 95;

    private MimicDetection() {
    }

    /**
     * The player's chance, in percent, of seeing through a disguise.
     *
     * <p>Callers must pass effective Wisdom from {@code PlayerStats.getWisdom()}, not
     * {@code getWisModifier()} -- the modifier methods read the raw field and bypass the
     * Shelter Altar ascension bonus, which would make altar investment worthless here.
     */
    public static int chancePercent(int wisdom) {
        int chance = BASE_CHANCE + (wisdom - 10) * PER_POINT;
        return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, chance));
    }

    /**
     * Rolls the perception check against a chest, if one is owed.
     *
     * @return true only when this call is what revealed the disguise, so the caller can
     *         post the discovery event exactly once.
     */
    public static boolean attempt(Item item, int wisdom, Random random) {
        if (item == null || !item.isMimic() || item.isMimicRollSpent()) {
            return false;
        }

        item.setMimicRollSpent(true);

        if (random.nextInt(100) < chancePercent(wisdom)) {
            item.setMimicSeen(true);
            return true;
        }
        return false;
    }
}
