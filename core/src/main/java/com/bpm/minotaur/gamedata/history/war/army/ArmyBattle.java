package com.bpm.minotaur.gamedata.history.war.army;

/**
 * One battle under way, watched or not (Living War A20): the armies on each side, and each side's
 * morale and numbers. Saved with the roster. The dead stay here until it ends and its deed takes
 * them off their houses, so a house's garrison has to count them ({@link WarRoster#garrison}).
 */
public class ArmyBattle {

    /** Soldiers of a side that can strike at once, and that stand on the field while it is watched (A20, A21). */
    public static final int LINE = 16;

    public int id;
    public int x;
    public int y;
    public int turn;
    /** The house each side fights for. */
    public int houseA;
    public int houseB;
    public int[] armiesA = new int[0];
    public int[] armiesB = new int[0];
    /** Side A's "house" when side A is a megabeast. */
    public static final int THE_BEAST = -2;
    /** The megabeast that is side A, or -1 (A28): it fights alone, by its hit points. */
    public int beastId = -1;
    public int beastHp;
    /** A beast's hit points as soldiers' blows it can still take, for weighing it against a line. */
    public int beastStanding() {
        return Math.max(0, beastHp) / ArmyTuning.BEAST_BLOW;
    }

    /** The house whose garrison stands with side B at its own seat, or -1. */
    public int garrisonOf = -1;
    /** What side B's position is worth against side A's blows; 0 for what its garrison's walls always were. */
    public float wallEdge;
    /** A storming of the seat, not its garrison coming out: fought over the walls, and then the yard (A33). */
    public boolean assault;
    /** The walls are lost, and the last of the garrison stands in the yard: only clearing it takes the seat. */
    public boolean yard;
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
    /** Enemy soldiers the player has killed here, and whether one was a captain (A40). */
    public int playerKills;
    public boolean playerSlewCaptain;
    /** At the end: the player's share of the enemy dead, and whether they stayed for it. */
    public transient float playerShare;
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

    /** The house the player stood with: by the blows they struck, or by their word if they struck none (A26, A40). */
    public int stoodWith() {
        if (foughtFor() >= 0) return foughtFor();
        return playerSide == 1 ? houseA : playerSide == 2 ? houseB : -1;
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
