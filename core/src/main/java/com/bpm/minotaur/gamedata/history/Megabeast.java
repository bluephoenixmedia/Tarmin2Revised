package com.bpm.minotaur.gamedata.history;

/**
 * A named, unique creature of the history (plan D19, D33-D35): born in some season, lairing at
 * a depth of the strata, raiding houses and being bargained with, until something kills it.
 */
public class Megabeast {
    public final int id;
    /** "Ulgrath the Obsidian Wyrm". */
    public final String name;
    public final String archetypeId;
    public final String materialId;
    public final String breathId;
    /** A {@code DamageType} name it takes extra harm from. */
    public final String weakness;
    /** The level its lair is on: 2 is the first stratum. */
    public final int lairLevel;
    public final int awakenSeason;
    public int deathSeason = -1;
    /** The season the player bought its peace, or -1 (plan T4.5). */
    public int pacifiedSeason = -1;

    public Megabeast(int id, String name, String archetypeId, String materialId, String breathId, String weakness,
            int lairLevel, int awakenSeason) {
        this.id = id;
        this.name = name;
        this.archetypeId = archetypeId;
        this.materialId = materialId;
        this.breathId = breathId;
        this.weakness = weakness;
        this.lairLevel = lairLevel;
        this.awakenSeason = awakenSeason;
    }

    public boolean isAwake(int season) {
        return season >= awakenSeason;
    }

    public boolean isAlive() {
        return deathSeason < 0;
    }

    /** It took the player's offering, and will not hunt them. */
    public boolean isPacified() {
        return pacifiedSeason >= 0;
    }
}
