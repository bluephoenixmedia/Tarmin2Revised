package com.bpm.minotaur.gamedata.shelter;

import java.util.Set;

/**
 * What a death costs the shelter roads.
 *
 * <p>Death still rerolls the world, but the new world remembers how far the
 * player got along each road: each road keeps its claimed count, except the road
 * of the last rest, which loses one. A road whose seal is won loses nothing and
 * keeps every shelter. The player wakes at the same place on the same road, or
 * one shelter back if that place was lost. See
 * docs/DEsign/Requirements_ Shelter Roads.md, section 8.
 */
public final class ShelterMemory {

    /** A road or respawn of {@code HOME}: the home shelter in the maze, or an off-road rest. */
    public static final int HOME = -1;
    /** A count meaning "every shelter on the road", for roads whose seal is won. */
    public static final int ALL = Integer.MAX_VALUE;

    public static final class Result {
        private final int[] counts;
        private final int respawnRoad;
        private final int respawnIndex;

        Result(int[] counts, int respawnRoad, int respawnIndex) {
            this.counts = counts;
            this.respawnRoad = respawnRoad;
            this.respawnIndex = respawnIndex;
        }

        /** Shelters to pre-claim on each road of the new world, from the maze outward. */
        public int[] getCounts() { return counts.clone(); }
        /** The road to wake on, or {@link #HOME}. */
        public int getRespawnRoad() { return respawnRoad; }
        /** The shelter on that road to wake at, from the maze outward. */
        public int getRespawnIndex() { return respawnIndex; }
    }

    private ShelterMemory() {
    }

    /**
     * @param claimed   shelters claimed on each road of the dying world
     * @param restRoad  the road of the last rest, or {@link #HOME}
     * @param restIndex the last rest's place on that road
     * @param sealsWon  roads whose seal the player holds
     */
    public static Result afterDeath(int[] claimed, int restRoad, int restIndex, Set<Integer> sealsWon) {
        int[] counts = claimed.clone();
        for (int r = 0; r < counts.length; r++) {
            if (sealsWon.contains(r)) counts[r] = ALL;
        }
        if (restRoad < 0 || restRoad >= counts.length) {
            return new Result(counts, HOME, 0);
        }
        if (counts[restRoad] != ALL) {
            counts[restRoad] = Math.max(0, counts[restRoad] - 1);
        }
        int index = Math.min(restIndex, counts[restRoad] == ALL ? restIndex : counts[restRoad] - 1);
        return index < 0 ? new Result(counts, HOME, 0) : new Result(counts, restRoad, index);
    }
}
