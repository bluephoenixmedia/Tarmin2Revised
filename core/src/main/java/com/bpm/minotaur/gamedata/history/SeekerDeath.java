package com.bpm.minotaur.gamedata.history;

/**
 * How one life ended, as the history takes it (#283): what killed it -- by name, by kind, and
 * as a house Figure or a Nemesis when it already was one -- whose life it was, and where.
 */
public final class SeekerDeath {

    public final String killerName;
    /** A monsters.json type; null when no monster did it (thirst, the maze), which raises no Nemesis. */
    public final String monsterType;
    public final int killerHouse;
    int killerFigure = -1;
    int killerNemesis = -1;
    String victim;
    int level = -1;
    int hunger = -1;
    int chunkX;
    int chunkY;

    private SeekerDeath(String killerName, String monsterType, int killerHouse) {
        this.killerName = killerName;
        this.monsterType = monsterType;
        this.killerHouse = killerHouse;
    }

    public static SeekerDeath by(String killerName, String monsterType, int killerHouse) {
        return new SeekerDeath(killerName, monsterType, killerHouse);
    }

    /** The killer is this house Figure. */
    public SeekerDeath figure(int figureId) {
        this.killerFigure = figureId;
        return this;
    }

    /** The killer is this Nemesis, killing again. */
    public SeekerDeath nemesis(int nemesisId) {
        this.killerNemesis = nemesisId;
        return this;
    }

    /** The life's name. */
    public SeekerDeath of(String victim) {
        this.victim = victim;
        return this;
    }

    /** The level and chunk it happened in: the Nemesis's region. */
    public SeekerDeath at(int level, int chunkX, int chunkY) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        return this;
    }

    /** Tarmin's Hunger after this death: a new Era may see Tarmin slain off-screen (#292). */
    public SeekerDeath hunger(int hunger) {
        this.hunger = hunger;
        return this;
    }

    /** The deed the history keeps and replays. */
    public PlayerDeed deed(int liveSeason) {
        PlayerDeed deed = new PlayerDeed(PlayerDeed.Kind.SEEKER_FELL, killerHouse, liveSeason);
        deed.note = killerName;
        deed.figure = killerFigure;
        deed.nemesis = killerNemesis;
        deed.monsterType = monsterType;
        deed.victim = victim;
        deed.level = level;
        deed.chunkX = chunkX;
        deed.chunkY = chunkY;
        deed.hunger = hunger;
        return deed;
    }
}
