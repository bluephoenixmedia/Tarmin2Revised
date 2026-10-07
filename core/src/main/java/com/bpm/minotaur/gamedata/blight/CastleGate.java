package com.bpm.minotaur.gamedata.blight;

import com.bpm.minotaur.gamedata.Scenery;
import com.bpm.minotaur.generation.BlightChunkGenerator;

/**
 * The front door of Castle Tarmin, and the hook Phase 5 hangs the Final Run on.
 *
 * <p>The gate stands at the castle site in the Blighted Marches. Today it is
 * sealed: bumping it tells the player what it will take. Phase 5 (Castle Tarmin
 * &amp; The Final Run) replaces {@link #isOpen()} with the ancient-seal check and
 * makes passing it load the castle zone. Nothing else should need to change.
 */
public final class CastleGate {

    public static final String SEALED_MESSAGE =
            "The gates of Castle Tarmin are sealed. Ancient seals, torn from the deep strata, bar the way.";

    private CastleGate() {
    }

    public static boolean isCastleGate(Scenery scenery) {
        return scenery != null && BlightChunkGenerator.CASTLE_GATE_PROP.equals(scenery.getPropId());
    }

    /** Whether the castle can be entered. Always false until Phase 5 builds the castle zone. */
    public static boolean isOpen() {
        return false;
    }
}
