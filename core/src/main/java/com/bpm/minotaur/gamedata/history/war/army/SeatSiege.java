package com.bpm.minotaur.gamedata.history.war.army;

/**
 * An enemy seat under investment (Living War A30-A34): who sits around it, how worn its walls are and
 * how far the works have come. Saved with the roster. Plain public fields, so the save reader can
 * write and read it as it is.
 */
public class SeatSiege {

    /** Walls begin here, against the attackers' blows (A30, tune). */
    public static final float WALLS_START = 4f;
    public static final float WALLS_FLOOR = 1.5f;
    /** What the walls lose to a Season under siege. */
    public static final float WALLS_WEAR = 0.5f;
    /** What the ram at a stone gate, or the fire at a palisade, takes off them for the assault (A31). */
    public static final float WORKS_BREACH = 1f;
    /** The edge of a last stand in the yard, with no wall left to stand behind (A33). */
    public static final float YARD_EDGE = 1.5f;
    /** Turns to the garrison's next soldier lost to hunger. */
    public static final int STARVE_TURNS = 100;
    /** Turns of work to build the ladders, and then the ram or the fire. */
    public static final int LADDER_TURNS = 200;
    public static final int RAM_TURNS = 400;
    /** How often the captain weighs an assault and the garrison a sally. */
    public static final int THINK_EVERY = 25;
    /** A besieger this many times what the walls are worth is ready to go in, by temper. */
    static final float ASSAULT_AT = 1f;
    static final float ASSAULT_AT_WRATHFUL = 0.55f;
    static final float ASSAULT_AT_PATIENT = 1.8f;
    /** The chance a garrison spoiling for it comes out, each time it thinks. */
    static final float SALLY_CHANCE = 0.06f;
    /** It won't come out with fewer than this against each besieger. */
    static final float SALLY_FROM = 0.5f;

    public int id;
    public int x;
    public int y;
    /** The house whose seat this is. */
    public int holder;
    /** The side laying the siege. */
    public int besieger;
    public int[] armies = new int[0];
    public long since;
    public float walls = WALLS_START;
    public int works;
    /** A stone wall at a seal site, or a holdfast's timber palisade (A32). */
    public boolean stone;

    /** For the save reader. */
    public SeatSiege() {
    }

    public boolean laddersBuilt() {
        return works >= LADDER_TURNS;
    }

    /** A ram for a stone gate, or the means to fire a palisade. */
    public boolean ramBuilt() {
        return works >= RAM_TURNS;
    }

    /** What the walls are worth to an assault going in now. */
    public float assaultEdge() {
        return Math.max(WALLS_FLOOR, walls - (ramBuilt() ? WORKS_BREACH : 0f));
    }

    /** Worn by the Season, never below the floor. */
    void wear() {
        walls = Math.max(WALLS_FLOOR, walls - WALLS_WEAR);
    }

    /** Numbers against worn walls, weighed by the captain's temper: nothing else starts it (A33). */
    static boolean readyToAssault(int soldiers, int garrison, float edge, boolean laddersBuilt, boolean wrathful, boolean patient) {
        if (!laddersBuilt) return false;
        float at = patient ? ASSAULT_AT_PATIENT : wrathful ? ASSAULT_AT_WRATHFUL : ASSAULT_AT;
        return soldiers >= at * garrison * edge;
    }

    /** Whether a garrison's lord has the temper and the numbers to strike the camp (A34). */
    static boolean readyToSally(int garrison, int soldiers, boolean spoilingForIt) {
        return spoilingForIt && garrison > 0 && garrison >= soldiers * SALLY_FROM;
    }
}
