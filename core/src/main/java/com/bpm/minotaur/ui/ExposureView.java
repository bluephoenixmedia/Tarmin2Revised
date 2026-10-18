package com.bpm.minotaur.ui;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.player.Exposure;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * How each Exposure band shows on the HUD (#194): its status pill, its meter colour and the
 * effect line of its tooltip. One table, so the pill bar, the meter and the tooltip can't drift
 * apart band by band.
 */
public enum ExposureView {
    HOT(Exposure.Band.HOT, "HEAT", HudSkin.COL_HP_CRITICAL, true,
            "Overheating: thirst runs twice as fast and you tire."),
    WARM(Exposure.Band.WARM, null, HudSkin.COL_TEMP_ORANGE, false,
            "Warmed through by a fire or a hearth."),
    COMFORTABLE(Exposure.Band.COMFORTABLE, null, HudSkin.COL_EXPOSURE_COMFORT, false,
            "No penalty."),
    CHILLY(Exposure.Band.CHILLY, "CHILLY", HudSkin.COL_EXPOSURE_CHILLY, false,
            "No penalty yet, but the cold is getting in."),
    COLD(Exposure.Band.COLD, "COLD", HudSkin.COL_WATER_CYAN, false,
            "Cold: move and attack speed -10%."),
    FREEZING(Exposure.Band.FREEZING, "FREEZING", HudSkin.COL_FROST, true,
            "Freezing: speed -20%, and wounds no longer knit on their own."),
    HYPOTHERMIC(Exposure.Band.HYPOTHERMIC, "HYPO", HudSkin.COL_HP_CRITICAL, true,
            "Hypothermic: speed -20%, no natural healing, 1 damage every "
                    + Exposure.HYPOTHERMIA_DAMAGE_EVERY + " turns, and now and then a lost action.");

    public final Exposure.Band band;
    /** The status pill's text, or null when the band raises no pill. */
    public final String pill;
    /** The Exposure meter's fill colour, and the pill's accent. */
    public final Color color;
    /** True when the meter frames itself as a danger. */
    public final boolean danger;
    /** The tooltip's effect line. */
    public final String effect;

    ExposureView(Exposure.Band band, String pill, Color color, boolean danger, String effect) {
        this.band = band;
        this.pill = pill;
        this.color = color;
        this.danger = danger;
        this.effect = effect;
    }

    public static ExposureView of(Exposure.Band band) {
        for (ExposureView v : values()) {
            if (v.band == band) return v;
        }
        return COMFORTABLE;
    }
}
