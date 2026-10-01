package com.bpm.minotaur.rendering.vfx;

/**
 * The ids of the frame-sequence clips in {@code assets/data/fx.json}, so callers do not repeat
 * strings that would only fail at runtime when mistyped. The list itself is built by
 * {@code tools/build_fx_atlas.py}; a test checks every id here is really in the data.
 */
public final class FxClipIds {

    public static final String BLOOD_SMALL = "blood_splatter_small";
    public static final String BLOOD_LARGE = "blood_splatter_large";
    public static final String HIT_SMOKE = "hit_smoke";
    public static final String HIT_SPARKS = "hit_sparks";
    public static final String ALERT = "alert";

    public static final String[] ALL = { BLOOD_SMALL, BLOOD_LARGE, HIT_SMOKE, HIT_SPARKS, ALERT };

    private FxClipIds() {
    }
}
