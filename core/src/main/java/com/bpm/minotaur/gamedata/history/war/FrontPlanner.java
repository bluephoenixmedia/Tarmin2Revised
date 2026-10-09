package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.gamedata.history.HistoryWorld;
import com.bpm.minotaur.gamedata.history.War;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every war is being fought at a given moment of the war clock (plan D15, D22, T2.1).
 *
 * <p>A front sways back and forth along the line between the two houses' seats, as the war goes
 * one way and then the other. Its place is a pure function of the war and the clock, so nothing
 * but the clock needs saving and every reload sees the same fronts.
 */
public final class FrontPlanner {

    /** War-clock turns for a front to sway out and back once. */
    public static final long PERIOD = 3000;
    /** How far from the midpoint toward either seat the front sways, as a fraction of the line. */
    static final double SWAY = 0.35;

    private FrontPlanner() {
    }

    public static List<Front> fronts(HistoryWorld world, SeatMap seats, long warClock) {
        List<Front> out = new ArrayList<>();
        for (War w : world.activeWars()) {
            GridPoint2 a = seats.seat(w.attackerId);
            GridPoint2 b = seats.seat(w.defenderId);
            if (a == null || b == null) continue;
            double phase = ((w.id * 0x9E3779B9L + world.seed) & 0xFFFF) / 65536.0 * Math.PI * 2;
            double t = 0.5 + SWAY * Math.sin(Math.PI * 2 * warClock / PERIOD + phase);
            GridPoint2 c = new GridPoint2((int) Math.round(a.x + (b.x - a.x) * t), (int) Math.round(a.y + (b.y - a.y) * t));
            out.add(new Front(w.id, w.attackerId, w.defenderId, c));
        }
        return out;
    }

    /** The front over {@code chunk}, or null. If two overlap, the older war's. */
    public static Front at(List<Front> fronts, GridPoint2 chunk) {
        for (Front f : fronts) {
            if (f.covers(chunk)) return f;
        }
        return null;
    }
}
