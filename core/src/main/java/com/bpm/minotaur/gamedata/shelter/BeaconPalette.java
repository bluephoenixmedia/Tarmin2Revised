package com.bpm.minotaur.gamedata.shelter;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.generation.ShelterRoads;

/**
 * The colour of each road's beacons, so the sky says which road a fire belongs to.
 *
 * <p>The castle road burns purple the whole way from home, not only in the Blight.
 * The seal colours are first choices, expected to change in playtesting.
 */
public final class BeaconPalette {

    public static final Color CASTLE = new Color(0.62f, 0.30f, 0.95f, 1f);
    private static final Color[] SEAL_ROADS = {
            new Color(1.00f, 0.66f, 0.20f, 1f), // amber
            new Color(0.25f, 0.85f, 0.80f, 1f), // teal
            new Color(0.92f, 0.94f, 1.00f, 1f), // white
    };
    /** Plain hearth smoke: an off-road shelter. */
    public static final Color SMOKE = new Color(0.55f, 0.53f, 0.50f, 1f);
    /** How much of its colour a road keeps once its seal is won. */
    public static final float SPENT = 0.4f;

    private BeaconPalette() {
    }

    /** The colour of a road's beacons; off-road ({@link ShelterRoads#OFF_ROAD}) is plain smoke. */
    public static Color roadColor(int road) {
        if (road == ShelterRoads.CASTLE_ROAD) return new Color(CASTLE);
        if (road >= 1 && road <= SEAL_ROADS.length) return new Color(SEAL_ROADS[road - 1]);
        return new Color(SMOKE);
    }
}
