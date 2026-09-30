package com.bpm.minotaur.gamedata.gore;

import java.util.List;
import java.util.Random;

/**
 * Where on a monster the next wound goes.
 *
 * <p>Wounds used to land at {@code 0.5 +/- 0.15} on both axes, so every cut on every monster piled
 * into the middle of the sprite. This samples the creature's own silhouette and, of a handful of
 * candidates, keeps the one furthest from the wounds it already has.
 */
public final class WoundPlacement {

    private static final int CANDIDATES = 12;
    private static final float EDGE = 0.04f;

    private WoundPlacement() {
    }

    /** @return {u, v}, both in [0, 1], v measured from the bottom. */
    public static float[] pick(SilhouetteMask mask, List<WoundDecal> existing, Random random) {
        SilhouetteMask m = (mask != null) ? mask : SilhouetteMask.full();
        float[] best = null;
        float bestClearance = -1f;
        for (int i = 0; i < CANDIDATES; i++) {
            float[] p = m.randomSolidPoint(random);
            p[0] = Math.max(EDGE, Math.min(1f - EDGE, p[0]));
            p[1] = Math.max(EDGE, Math.min(1f - EDGE, p[1]));
            float clearance = clearance(p, existing);
            if (clearance > bestClearance) {
                bestClearance = clearance;
                best = p;
            }
        }
        return best;
    }

    private static float clearance(float[] p, List<WoundDecal> existing) {
        if (existing == null || existing.isEmpty()) {
            return Float.MAX_VALUE;
        }
        float nearest = Float.MAX_VALUE;
        for (WoundDecal w : existing) {
            float dx = p[0] - w.u;
            float dy = p[1] - w.v;
            nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy));
        }
        return nearest;
    }
}
