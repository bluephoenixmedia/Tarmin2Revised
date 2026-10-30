package com.bpm.minotaur.gamedata.history.war.army;

/**
 * One battle under way, watched or not (Living War A20): the armies on each side, and each side's
 * morale and numbers. Saved with the roster. The dead stay here until it ends and its deed takes
 * them off their houses, so a house's garrison has to count them ({@link WarRoster#garrison}).
 */
public class ArmyBattle {

    public int id;
    public int x;
    public int y;
    public int turn;
    /** The house each side fights for. */
    public int houseA;
    public int houseB;
    public int[] armiesA = new int[0];
    public int[] armiesB = new int[0];
    /** The house whose garrison stands with side B at its own seat, or -1. */
    public int garrisonOf = -1;
    public int moraleA = 100;
    public int moraleB = 100;
    public int startA;
    public int startB;
    /** Each house that has lost soldiers here, and how many, as parallel lists. */
    public int[] deadHouses = new int[0];
    public int[] deadCounts = new int[0];
    /** Armies whose captains have already fallen. */
    public int[] captainFell = new int[0];
    /** The side the player chose to stand with (A26): 0 for neither, 1 for A, 2 for B. */
    public int playerSide;
    /** Sides the player has drawn blood from, as bits: 1 for A, 2 for B. */
    public int playerStruck;
    /** Whether the player was on the ground when it broke, for its deed (A24). */
    public transient boolean witnessed;
    /** Once it has broken: who held the field, who routed, and whether a house's lord fell. */
    public transient int winner = -1;
    public transient int loser = -1;
    public transient boolean lordFell;

    /** For the save reader. */
    public ArmyBattle() {
    }

    /** The house the player fought for: the side whose enemies alone they struck; -1 if both or neither (W19). */
    public int foughtFor() {
        if (playerStruck == 1) return houseB;
        if (playerStruck == 2) return houseA;
        return -1;
    }

    int deadOf(int houseId) {
        for (int i = 0; i < deadHouses.length; i++) {
            if (deadHouses[i] == houseId) return deadCounts[i];
        }
        return 0;
    }

    void addDead(int houseId, int n) {
        for (int i = 0; i < deadHouses.length; i++) {
            if (deadHouses[i] == houseId) {
                deadCounts[i] += n;
                return;
            }
        }
        deadHouses = append(deadHouses, houseId);
        deadCounts = append(deadCounts, n);
    }

    static int[] append(int[] list, int value) {
        int[] more = java.util.Arrays.copyOf(list, list.length + 1);
        more[list.length] = value;
        return more;
    }

    static boolean holds(int[] list, int value) {
        for (int v : list) if (v == value) return true;
        return false;
    }
}
