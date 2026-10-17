package com.bpm.minotaur.gamedata.shelter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a death costs the shelter roads.
 *
 * <p>Death still rerolls the world, but the new world remembers how far the
 * player got along each road: each road keeps the places it had claimed, except
 * the road of the last rest, which loses its furthest-out claim. A road whose
 * seal is won loses nothing and keeps every shelter. The player wakes at the same
 * place on the same road, or at the nearest remembered shelter behind it. See
 * docs/DEsign/Requirements_ Shelter Roads.md, section 8.
 *
 * <p>Places, not counts: a player who skipped ahead and claimed the fourth and
 * fifth shelters keeps the fourth, not the first.
 */
public final class ShelterMemory {

    /** A road or respawn of {@code HOME}: the home shelter in the maze, or an off-road rest. */
    public static final int HOME = -1;

    public static final class Result {
        private final List<Set<Integer>> claimed;
        private final Set<Integer> wholeRoads;
        private final int respawnRoad;
        private final int respawnIndex;

        Result(List<Set<Integer>> claimed, Set<Integer> wholeRoads, int respawnRoad, int respawnIndex) {
            this.claimed = claimed;
            this.wholeRoads = wholeRoads;
            this.respawnRoad = respawnRoad;
            this.respawnIndex = respawnIndex;
        }

        /** The places, from the maze outward, to pre-claim on each road of the new world. */
        public Set<Integer> getClaimed(int road) { return new TreeSet<>(claimed.get(road)); }
        /** True for a road whose seal is won: every shelter on it is claimed. */
        public boolean isWholeRoad(int road) { return wholeRoads.contains(road); }
        /** The road to wake on, or {@link #HOME}. */
        public int getRespawnRoad() { return respawnRoad; }
        /** The shelter on that road to wake at, from the maze outward. */
        public int getRespawnIndex() { return respawnIndex; }
    }

    private ShelterMemory() {
    }

    /**
     * @param claimed   the places claimed on each road of the dying world
     * @param restRoad  the road of the last rest, or {@link #HOME}
     * @param restIndex the last rest's place on that road
     * @param sealsWon  roads whose seal the player holds
     */
    public static Result afterDeath(List<Set<Integer>> claimed, int restRoad, int restIndex, Set<Integer> sealsWon) {
        List<Set<Integer>> keep = new ArrayList<>();
        for (Set<Integer> road : claimed) keep.add(new TreeSet<>(road));
        Set<Integer> whole = new TreeSet<>();
        for (int r = 0; r < keep.size(); r++) {
            if (sealsWon.contains(r)) whole.add(r);
        }
        if (restRoad < 0 || restRoad >= keep.size()) {
            return new Result(keep, whole, HOME, 0);
        }
        if (whole.contains(restRoad)) {
            return new Result(keep, whole, restRoad, restIndex);
        }
        TreeSet<Integer> road = (TreeSet<Integer>) keep.get(restRoad);
        if (!road.isEmpty()) road.pollLast();
        Integer wake = road.floor(restIndex);
        return wake == null ? new Result(keep, whole, HOME, 0) : new Result(keep, whole, restRoad, wake);
    }
}
