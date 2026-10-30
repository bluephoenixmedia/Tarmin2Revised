package com.bpm.minotaur.gamedata.history.war.army;

/**
 * A house's force in the field (Living War A1): soldiers with a place on the overland, a captain
 * and an objective. Plain public fields, so the save reader can write and read it as it is.
 *
 * <p>The way it is marching is saved as where that march began, not the chunks themselves, and is
 * searched again on load. The same start and target give the same way, so a reload walks the road
 * the army would have walked.
 */
public class Army {

    public enum Kind { HOST, LEVY, RAID, SUPPLY, FORAGERS, HUNTING_PARTY, HERALD }

    public enum Objective {
        /** March on an enemy seat. */
        SEAT,
        /** Chase an enemy army it knows of. */
        HUNT,
        /** Burn an enemy's lands or waylay its road. */
        RAID,
        /** Hold at or near its own seat. */
        GUARD,
        /** Go home to refill and restore morale. */
        RECOVER,
        /** March with a liege's army. */
        FOLLOW,
        /** The war is over: go home and rejoin the garrison. */
        DISBAND
    }

    public int id;
    public int houseId;
    /** The liege this is a levy for, or -1. */
    public int levyOf = -1;
    public int warId = -1;
    public Kind kind = Kind.HOST;
    /** The figure leading it, or -1 for a nameless captain. */
    public int captainId = -1;
    public int soldiers;
    public int startSoldiers;
    public int morale = 100;
    public int x;
    public int y;
    /** War turns spent crossing into the next chunk. */
    public int progress;
    public Objective objective = Objective.GUARD;
    /** Where it is marching. */
    public int targetX;
    public int targetY;
    /** The army it hunts or follows, or -1. */
    public int targetArmy = -1;
    /** Where the march under way began, and how many of its chunks are behind. */
    public int marchFromX;
    public int marchFromY;
    public int marchStep;
    /** Fighting: it holds its ground until the battle in its chunk ends. */
    public boolean battle;
    /** The enemy armies it has already seen: a new one makes it think again. */
    public int[] seen = new int[0];

    /** The march's chunks; null when it must be searched again, as after a load. */
    transient int[] path;

    /** For the save reader. */
    public Army() {
    }

    boolean hasSeen(int armyId) {
        for (int s : seen) if (s == armyId) return true;
        return false;
    }

    void see(int armyId) {
        int[] more = java.util.Arrays.copyOf(seen, seen.length + 1);
        more[seen.length] = armyId;
        seen = more;
    }

    /** The side it fights for: a levy fights its liege's war. */
    public int side() {
        return levyOf >= 0 ? levyOf : houseId;
    }
}
