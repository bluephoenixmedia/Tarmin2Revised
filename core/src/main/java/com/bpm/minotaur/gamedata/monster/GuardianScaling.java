package com.bpm.minotaur.gamedata.monster;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A themed chunk's Guardian, met at the stratum it is found in (#302; the #256 grilling). However
 * great it is elsewhere, at a shallow depth it is at most {@link #OUT_OF_DEPTH} levels over the
 * stratum, as the 0.0.2 targets allow an out-of-depth monster: its level is capped, and its hit
 * points and damage are cut in the same proportion, as the targets cut damage with speed. A floor
 * keeps the cut Guardian at {@link #FLOOR} of a Heavy of its new level, so it stays the hardest
 * thing in the chunk. Its sprite, kind, abilities and tether are untouched.
 */
public final class GuardianScaling {

    /** How far over the stratum's EDL a Guardian may be met. */
    public static final int OUT_OF_DEPTH = 2;
    /** The share of a same-level Heavy a cut Guardian never falls below. */
    public static final float FLOOR = 0.6f;
    /**
     * And the most it may be of one: its hit points and its damage. Tuned in the duel simulator to
     * a fight a fresh body wins about one time in three (#302).
     */
    public static final float HP_CEILING = 1.0f;
    public static final float DAMAGE_CEILING = 0.85f;

    private static final Pattern DICE = Pattern.compile("(\\d*)d(\\d+)([+-]\\d+)?");

    /** A Guardian's stats at its stratum. */
    public static final class Stats {
        public final int level;
        public final int maxHp;
        public final String damageDice;

        Stats(int level, int maxHp, String damageDice) {
            this.level = level;
            this.maxHp = maxHp;
            this.damageDice = damageDice;
        }
    }

    /** A Heavy of the Guardian's new level, the yardstick for the floor. */
    public static final class Heavy {
        final int hp;
        final String damageDice;

        public Heavy(int hp, String damageDice) {
            this.hp = hp;
            this.damageDice = damageDice;
        }
    }

    private GuardianScaling() {
    }

    /**
     * Scales a placed Guardian to a stratum of {@code edl}: its level, its hit points (the Guardian
     * bonus already added) and its damage dice. Its template gives the base level; a Guardian with
     * none is left as it is. {@code strength} is its theme's own factor on the ceilings (1 for none).
     */
    public static void apply(Monster guardian, int edl, MonsterDataManager data, float strength) {
        MonsterTemplate t = guardian.getTemplate();
        if (t == null || t.baseLevel <= 0) return;
        int level = cappedLevel(t.baseLevel, edl);
        Stats s = scale(t.baseLevel, guardian.getMaxHP(), guardian.getDamageDice(), edl,
                heavyOfLevel(level, data), strength);
        guardian.setLevel(s.level);
        guardian.setMaxHP(s.maxHp);
        guardian.setCurrentHP(s.maxHp);
        guardian.setDamageDice(s.damageDice);
    }

    /** A Heavy of {@code level}: the loaded Heavy of the highest base level not above it, or null. */
    static Heavy heavyOfLevel(int level, MonsterDataManager data) {
        if (data == null || data.getLoadedTypes() == null) return null;
        MonsterTemplate best = null;
        for (Monster.MonsterType type : data.getLoadedTypes()) {
            MonsterTemplate t = data.getTemplate(type);
            if (t == null || t.getRole() != MonsterRole.HEAVY || t.baseLevel > level || t.damageDice == null) continue;
            if (best == null || t.baseLevel > best.baseLevel) best = t;
        }
        return best == null ? null : new Heavy(best.maxHP, best.damageDice);
    }

    /** The level a Guardian of {@code baseLevel} is met at in a stratum of {@code edl}. */
    public static int cappedLevel(int baseLevel, int edl) {
        return Math.max(1, Math.min(baseLevel, edl + OUT_OF_DEPTH));
    }

    /** {@link #scale(int, int, String, int, Heavy, float)} at a theme strength of 1. */
    public static Stats scale(int baseLevel, int maxHp, String damageDice, int edl, Heavy heavy) {
        return scale(baseLevel, maxHp, damageDice, edl, heavy, 1f);
    }

    /**
     * The Guardian's stats in a stratum of {@code edl}: {@code maxHp} (its Guardian bonus included)
     * and the average of {@code damageDice} are multiplied by capped level / base level, and while
     * it is still over the stratum it is held between the {@code heavy} floor and ceilings (none if
     * null): its Guardian bonus alone could otherwise make an uncut Guardian a certain death. At or
     * under the stratum's EDL it is itself.
     *
     * @param strength the theme's factor on the ceilings: one rule cannot make every Guardian equally
     *        hard, since one that shoots as the player crosses its ground outdoes one that must close,
     *        so each theme tunes its own to the same one-in-three fight (#302)
     */
    public static Stats scale(int baseLevel, int maxHp, String damageDice, int edl, Heavy heavy, float strength) {
        int level = cappedLevel(baseLevel, edl);
        if (level <= edl) return new Stats(baseLevel, maxHp, damageDice); // in its own depth: itself
        float ratio = level / (float) baseLevel;
        int hp = Math.round(maxHp * ratio);
        double damage = averageOf(damageDice) * ratio;
        if (heavy != null) {
            hp = Math.min(Math.max(hp, Math.round(FLOOR * heavy.hp)), Math.round(HP_CEILING * strength * heavy.hp));
            damage = Math.min(Math.max(damage, FLOOR * averageOf(heavy.damageDice)),
                    DAMAGE_CEILING * strength * averageOf(heavy.damageDice));
        }
        String dice = level < baseLevel ? diceForAverage(damageDice, damage)
                : damage < averageOf(damageDice) ? diceForAverage(damageDice, damage) : damageDice;
        return new Stats(level, Math.max(1, hp), dice);
    }

    /** The average roll of a dice expression: "4d12+10" is 36, a bare "5" is 5. */
    public static double averageOf(String dice) {
        if (dice == null || dice.isEmpty()) return 0;
        String clean = dice.replaceAll("\\s+", "").toLowerCase();
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException notANumber) {
            // a dice expression
        }
        Matcher m = DICE.matcher(clean);
        if (!m.matches()) return 0;
        int count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
        int sides = Integer.parseInt(m.group(2));
        int bonus = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        return count * (sides + 1) / 2.0 + bonus;
    }

    /**
     * Dice of the same kind as {@code like} (the same die, where it fits) averaging about
     * {@code average}: as few dice as reach it, the rest a flat bonus. "4d12+10" at 8 is "1d12+2".
     */
    public static String diceForAverage(String like, double average) {
        Matcher m = DICE.matcher(like == null ? "" : like.replaceAll("\\s+", "").toLowerCase());
        int sides = m.matches() ? Integer.parseInt(m.group(2)) : 6;
        double perDie = (sides + 1) / 2.0;
        if (average < perDie) {
            // Too little for even one of these dice: one smaller die that averages it.
            int smaller = Math.max(2, (int) Math.round(2 * average - 1));
            return "1d" + smaller;
        }
        int count = Math.max(1, (int) Math.floor(average / perDie));
        int bonus = (int) Math.round(average - count * perDie);
        return count + "d" + sides + (bonus > 0 ? "+" + bonus : "");
    }
}
