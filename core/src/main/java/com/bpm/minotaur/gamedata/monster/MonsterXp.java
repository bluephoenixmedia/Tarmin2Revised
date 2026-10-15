package com.bpm.minotaur.gamedata.monster;

/**
 * What a kill is worth, from the monster's level alone (#183, decided in #162).
 *
 * <p>XP grows by {@link #GROWTH} a level, the same rate as the player's curve
 * ({@code PlayerStats}), so the number of on-level kills a level costs stays the
 * same at every level. A template's {@code xpMultiplier} lifts elites and bosses
 * above the formula; the colour variant's multiplier is applied at the kill.
 */
public final class MonsterXp {

    /** XP for a level-1 monster, before its colour. */
    public static final int BASE = 10;
    /** XP growth per monster level; {@code PlayerStats} grows its curve by the same rate. */
    public static final double GROWTH = 1.2;
    /** A bones ghost is a whole fallen Incarnation: worth several of a monster of its level. */
    public static final float BONES_GHOST_MULTIPLIER = 3f;

    private MonsterXp() {
    }

    /** XP for a monster of this level with no template multiplier and no colour. */
    public static int forLevel(int level) {
        return (int) Math.round(BASE * Math.pow(GROWTH, Math.max(1, level) - 1));
    }

    /** XP a template is worth before colour: its level's XP times its elite or boss multiplier. */
    public static int forTemplate(MonsterTemplate template) {
        if (template == null) return forLevel(1);
        return Math.max(1, Math.round(forLevel(template.baseLevel) * template.xpMultiplier));
    }

    /** XP the player gets for a kill: the monster's XP times its colour variant's multiplier. */
    public static int forKill(int baseExperience, MonsterColor color) {
        float colorMultiplier = color != null ? color.getXpMultiplier() : 1f;
        return (int) (baseExperience * colorMultiplier);
    }
}
