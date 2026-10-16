package com.bpm.minotaur.generation;

import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks 2-3 spread-out tiles for a chunk's down ladders (#200), the same
 * best-of-samples spread the maze and forest generators use: each pick tries
 * a few candidates and keeps the one farthest from everything already placed,
 * settling early once one is at least {@link #GOOD_SPACING} steps away.
 */
final class LadderScatter {

    static final int MIN_LADDERS = 2;
    static final int MAX_LADDERS = 3;
    private static final int SAMPLES = 15;
    private static final int GOOD_SPACING = 8;

    private LadderScatter() {}

    /**
     * Removes the chosen tiles from {@code candidates} and returns them.
     *
     * @param anchors tiles to keep away from (an up ladder, say); may be empty
     */
    static List<GridPoint2> pick(List<GridPoint2> candidates, List<GridPoint2> anchors, Random random) {
        int target = MIN_LADDERS + random.nextInt(MAX_LADDERS - MIN_LADDERS + 1);
        List<GridPoint2> placed = new ArrayList<>(anchors);
        List<GridPoint2> chosen = new ArrayList<>();
        for (int i = 0; i < target && !candidates.isEmpty(); i++) {
            GridPoint2 best = null;
            int bestDist = -1;
            int samples = Math.min(SAMPLES, candidates.size());
            for (int s = 0; s < samples; s++) {
                GridPoint2 cand = candidates.get(random.nextInt(candidates.size()));
                int minDist = Integer.MAX_VALUE;
                for (GridPoint2 p : placed) {
                    minDist = Math.min(minDist, Math.abs(cand.x - p.x) + Math.abs(cand.y - p.y));
                }
                if (minDist >= GOOD_SPACING) {
                    best = cand;
                    break;
                }
                if (minDist > bestDist) {
                    bestDist = minDist;
                    best = cand;
                }
            }
            candidates.remove(best);
            placed.add(best);
            chosen.add(best);
        }
        return chosen;
    }
}
