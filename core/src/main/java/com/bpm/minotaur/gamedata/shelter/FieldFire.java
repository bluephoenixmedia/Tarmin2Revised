package com.bpm.minotaur.gamedata.shelter;

import com.bpm.minotaur.gamedata.player.Exposure;

/**
 * A Tinder Bundle field fire (#194 item 5): it burns about {@link #BURN_TURNS} turns, warms
 * and dries within {@link Exposure#FIRE_RADIUS} tiles, allows cooking (#191) and can be seen by
 * monsters within {@link #SIGHT_RADIUS}. Feeding it Firewood adds {@link #FIREWOOD_TURNS}.
 *
 * <p>Field fires are not saved, as the lights they carry are not: a fire left behind in a
 * chunk does not outlast a reload.
 */
public final class FieldFire {

    public static final int BURN_TURNS = 100;
    public static final int FIREWOOD_TURNS = 100;
    /** A fire never banks past this. */
    public static final int MAX_TURNS = 300;
    /** Monsters with a line of sight within this many tiles see the flames. */
    public static final float SIGHT_RADIUS = 8f;
    /** How often the flames draw the eye, in turns. */
    public static final int SIGHT_EVERY = 5;

    /** The fire's scenery and light share this id prefix. */
    public static final String LIGHT_PREFIX = "campfire_field_";

    public final int x;
    public final int y;
    private int turnsLeft;

    public FieldFire(int x, int y) {
        this(x, y, BURN_TURNS);
    }

    public FieldFire(int x, int y, int turns) {
        this.x = x;
        this.y = y;
        this.turnsLeft = Math.max(0, Math.min(MAX_TURNS, turns));
    }

    public int getTurnsLeft() {
        return turnsLeft;
    }

    public String lightId() {
        return LIGHT_PREFIX + x + "_" + y;
    }

    /** Burns one turn; true when that was the last of it. */
    public boolean burn() {
        if (turnsLeft > 0) turnsLeft--;
        return turnsLeft == 0;
    }

    public boolean isOut() {
        return turnsLeft <= 0;
    }

    public void feed(int turns) {
        turnsLeft = Math.min(MAX_TURNS, turnsLeft + Math.max(0, turns));
    }

    /** True when tile ({@code tx}, {@code ty}) is within {@code radius} tiles, diagonals included. */
    public boolean reaches(int tx, int ty, int radius) {
        return !isOut() && Math.max(Math.abs(tx - x), Math.abs(ty - y)) <= radius;
    }

    /** Within warming, drying and cooking range of the fire. */
    public boolean warms(int tx, int ty) {
        return reaches(tx, ty, Exposure.FIRE_RADIUS);
    }
}
