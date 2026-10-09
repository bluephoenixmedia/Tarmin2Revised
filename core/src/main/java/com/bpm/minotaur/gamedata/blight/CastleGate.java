package com.bpm.minotaur.gamedata.blight;

import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.gamedata.shelter.ShelterNetwork;
import com.bpm.minotaur.generation.BlightChunkGenerator;

/**
 * The front door of Castle Tarmin, and the hook Phase 5 hangs the Final Run on.
 *
 * <p>The gate stands at the castle site in the Blighted Marches. It opens to the
 * three ancient seals, one held at the end of each seal road (see
 * docs/DEsign/Requirements_ Shelter Roads.md, section 7). Phase 5 (Castle Tarmin
 * &amp; The Final Run) makes passing an open gate load the castle zone; until then
 * the gate only tells the player how many seals they hold.
 */
public final class CastleGate {

    public static final int SEALS_REQUIRED = 3;

    public static final String SEALED_MESSAGE =
            "The gates of Castle Tarmin are sealed. Ancient seals, torn from the deep strata, bar the way.";

    private CastleGate() {
    }

    public static boolean isCastleGate(Scenery scenery) {
        return scenery != null && BlightChunkGenerator.CASTLE_GATE_PROP.equals(scenery.getPropId());
    }

    public static int sealsHeld() {
        return ShelterNetwork.getInstance().getSealCount();
    }

    /** Whether the gate yields: every seal is held. */
    public static boolean isOpen() {
        return isOpen(sealsHeld());
    }

    static boolean isOpen(int seals) {
        return seals >= SEALS_REQUIRED;
    }

    /** What knocking at the gate tells the player. */
    public static String knockMessage(int seals) {
        if (isOpen(seals)) {
            return "The three seals burn in the gate's locks. The gates of Castle Tarmin stand ready to open.";
        }
        return SEALED_MESSAGE + " (" + seals + "/" + SEALS_REQUIRED + " seals held)";
    }
}
