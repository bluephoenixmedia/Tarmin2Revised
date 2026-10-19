package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.WeaponFamily;

import java.util.EnumMap;
import java.util.Map;

/**
 * The protagonist's rank in each Weapon Family, raised by full hits on anything that is not
 * Trivial (#226). Belongs to the Incarnation: a new body starts Untrained.
 */
public final class Mastery {

    /** The ranks, lowest first. */
    public enum Rank { UNTRAINED, NOVICE, ADEPT, EXPERT, MASTER }

    /** Full hits needed for Novice, Adept, Expert and Master. */
    static final int[] THRESHOLDS = { 10, 50, 150, 400 };

    private final Map<WeaponFamily, Integer> hits = new EnumMap<>(WeaponFamily.class);

    /**
     * The Family a hit trains: the weapon's home Family when swung, Thrown when thrown, and
     * Unarmed with no weapon. Something with no Family trains nothing however it is used.
     */
    public static WeaponFamily familyOfHit(Item weapon, boolean thrown) {
        if (weapon == null) return WeaponFamily.UNARMED;
        WeaponFamily home = weapon.getWeaponFamily();
        if (home == WeaponFamily.NONE) return WeaponFamily.NONE;
        return thrown ? WeaponFamily.THROWN : home;
    }

    /**
     * Counts one full hit with {@code family}.
     *
     * @return the rank the hit raised the Family to, or null when the rank did not change
     */
    public Rank recordHit(WeaponFamily family) {
        if (family == null || family == WeaponFamily.NONE) return null;
        Rank before = rankOf(family);
        hits.merge(family, 1, Integer::sum);
        Rank after = rankOf(family);
        return after != before ? after : null;
    }

    /**
     * What a Passive Art's base proc chance is multiplied by: base at the rank that taught it,
     * +25% of base per rank above (at most 1.5x), and half while the body is below that rank.
     */
    public static float procMultiplier(Rank artRank, Rank current) {
        int above = current.ordinal() - artRank.ordinal();
        if (above < 0) return 0.5f;
        return Math.min(1.5f, 1f + 0.25f * above);
    }

    /** What an Active Art's cooldown is multiplied by: double while the body is below its rank. */
    public static float cooldownMultiplier(Rank artRank, Rank current) {
        return current.ordinal() < artRank.ordinal() ? 2f : 1f;
    }

    /** +1 to-hit per rank on attacks with {@code family}. */
    public int toHitBonus(WeaponFamily family) {
        return rankOf(family).ordinal();
    }

    /** Sets {@code family} to exactly the hits that reach {@code rank}, as the duel simulator needs (#232). */
    public void setRank(WeaponFamily family, Rank rank) {
        if (family == null || family == WeaponFamily.NONE) return;
        if (rank == Rank.UNTRAINED) hits.remove(family);
        else hits.put(family, THRESHOLDS[rank.ordinal() - 1]);
    }

    /** Forgets every Family: a new body starts Untrained. */
    public void clear() {
        hits.clear();
    }

    /** Full hits per Family by name, for the save. */
    public Map<String, Integer> toSave() {
        Map<String, Integer> out = new java.util.HashMap<>();
        hits.forEach((family, n) -> out.put(family.name(), n));
        return out;
    }

    /** Restores {@link #toSave()}; names that are not a Family are dropped. */
    public void restore(Map<String, Integer> saved) {
        hits.clear();
        if (saved == null) return;
        saved.forEach((name, n) -> {
            WeaponFamily family = WeaponFamily.parse(name);
            if (family != WeaponFamily.NONE && n != null && n > 0) hits.put(family, n);
        });
    }

    public Rank rankOf(WeaponFamily family) {
        int n = hits.getOrDefault(family, 0);
        int rank = 0;
        while (rank < THRESHOLDS.length && n >= THRESHOLDS[rank]) rank++;
        return Rank.values()[rank];
    }
}
