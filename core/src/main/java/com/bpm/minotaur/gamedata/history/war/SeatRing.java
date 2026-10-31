package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.List;

/**
 * The walled ring around a seat's chunk (Living War A32): a square about the centre with a gate in its
 * north and south walls. The garrison holds what is inside it; the camp stands outside. Pure geometry.
 */
public final class SeatRing {

    /** Tiles from the centre to the wall, and each side of the middle of a wall left open for a gate. */
    public static final int RADIUS = 8;
    static final int GATE_HALF = 1;

    private SeatRing() {
    }

    /** The wall's tiles, leaving out the two gates. */
    public static List<GridPoint2> wall(int width, int height) {
        int cx = width / 2, cy = height / 2;
        List<GridPoint2> out = new ArrayList<>();
        for (int d = -RADIUS; d <= RADIUS; d++) {
            if (Math.abs(d) > GATE_HALF) {
                out.add(new GridPoint2(cx + d, cy + RADIUS));
                out.add(new GridPoint2(cx + d, cy - RADIUS));
            }
            if (Math.abs(d) < RADIUS) {
                out.add(new GridPoint2(cx + RADIUS, cy + d));
                out.add(new GridPoint2(cx - RADIUS, cy + d));
            }
        }
        return out;
    }

    /** The middle tile of each gate: north, then south. */
    public static List<GridPoint2> gates(int width, int height) {
        List<GridPoint2> out = new ArrayList<>();
        out.add(new GridPoint2(width / 2, height / 2 + RADIUS));
        out.add(new GridPoint2(width / 2, height / 2 - RADIUS));
        return out;
    }

    /** Whether a tile lies within the wall. */
    public static boolean inside(int width, int height, int x, int y) {
        return Math.abs(x - width / 2) < RADIUS && Math.abs(y - height / 2) < RADIUS;
    }
}
