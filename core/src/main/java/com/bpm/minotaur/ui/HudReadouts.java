package com.bpm.minotaur.ui;

import com.bpm.minotaur.managers.DoomManager;

/**
 * Text for the HUD's Tarmin's Hunger and Divinity readouts and their hover tooltips (#173).
 *
 * <p>The readouts were bare numbers -- "DIV: 12", "TARMIN'S HUNGER 4 - 8%" -- that nothing on
 * screen explained, and the Hunger line was wide enough that its "BOSS SOON" suffix was ellipsized
 * off the end of its cell. The cell now gets a short label and the tooltip carries the rest.
 */
public final class HudReadouts {

    private HudReadouts() {
    }

    /** One tooltip's worth of text, in the shape {@code HudTooltip.showStatus} takes. */
    public static final class Tip {
        public final String title;
        public final String category;
        public final String details;
        public final String remedy;

        Tip(String title, String category, String details, String remedy) {
            this.title = UiGlyphs.sanitize(title);
            this.category = UiGlyphs.sanitize(category);
            this.details = UiGlyphs.sanitize(details);
            this.remedy = UiGlyphs.sanitize(remedy);
        }
    }

    /** True once the Hunger is close enough to the cap that the boss is the thing to know. */
    public static boolean bossNear(int deaths, int max) {
        return max > 0 && deaths * 10 >= max * 9;
    }

    /** "HUNGER 4/50", with a "!" once the bridge boss is near or loose. Fits the vitals cell. */
    public static String hungerLabel(int deaths, int max, boolean bossActive) {
        String label = "HUNGER " + deaths + "/" + max;
        return (bossActive || bossNear(deaths, max)) ? label + " !" : label;
    }

    public static Tip hungerTip(int deaths, int max, boolean bossActive) {
        int pct = max > 0 ? Math.min(100, Math.round(deaths * 100f / max)) : 0;
        StringBuilder details = new StringBuilder();
        details.append("Every death feeds Tarmin: ").append(deaths).append(" of ").append(max)
                .append(" (").append(pct).append("%).\n");
        details.append("The maze grows more savage as he feeds.\n");
        String remedy;
        if (bossActive) {
            details.append("The Bridge has fallen. The Bringer of Death is loose,\n");
            details.append("and one more death before it falls ends the game.");
            remedy = "Slay the Bringer of Death to starve the Hunger back to 0.";
        } else {
            int next = DoomManager.nextDeathStageAt(deaths);
            if (next > 0) {
                details.append("It turns more savage again at ").append(next).append(" deaths.\n");
            }
            details.append("At ").append(max).append(" the Bridge falls and the Bringer of Death comes.");
            remedy = bossNear(deaths, max)
                    ? "The boss is near. Unlocks and altar stations are your answer."
                    : "Unlocks and altar stations offset the growing Hunger.";
        }
        return new Tip("Tarmin's Hunger", "[DEATHS]", details.toString(), remedy);
    }

    /** "DIV: 12": the short label the vitals cell can always hold. */
    public static String divinityLabel(int divinities) {
        return "DIV: " + divinities;
    }

    public static Tip divinityTip(int divinities) {
        return new Tip("Divinity",
                "[ESSENCE]",
                "You hold " + UiNames.plural(divinities, "Divinity", "Divinities") + ".\n"
                        + "Spiritual essence, earned by exploring new ground\n"
                        + "and slaying monsters. Dropped where you die;\n"
                        + "step over it to reclaim it.",
                "Spend it at the Divinity Altar in the Shelter.");
    }

    // --- Exposure (#194) ---------------------------------------------------------------

    /** The meter's text: just the band, which fits the 210-unit gauge at every band. */
    public static String exposureMeterText(com.bpm.minotaur.gamedata.player.Exposure.Band band) {
        return UiGlyphs.sanitize(band.label.toUpperCase(java.util.Locale.ROOT));
    }

    /** How full the meter reads, 0..1: empty at the body's floor, full at heatstroke. */
    public static float exposureMeterFill(float bodyTemp) {
        float lo = com.bpm.minotaur.gamedata.player.Exposure.BODY_FLOOR;
        float hi = com.bpm.minotaur.gamedata.player.Exposure.HOT_ABOVE + 1f;
        return Math.max(0f, Math.min(1f, (bodyTemp - lo) / (hi - lo)));
    }

    public static Tip exposureTip(com.bpm.minotaur.gamedata.player.Exposure.Band band,
                                  com.bpm.minotaur.gamedata.player.Exposure.Wetness wet) {
        String effect;
        switch (band) {
            case HOT: effect = "Overheating: thirst runs twice as fast and you tire."; break;
            case WARM: effect = "Warmed through by a fire or a hearth."; break;
            case CHILLY: effect = "No penalty yet, but the cold is getting in."; break;
            case COLD: effect = "Cold: move and attack speed -10%."; break;
            case FREEZING: effect = "Freezing: speed -20%, and wounds no longer knit on their own."; break;
            case HYPOTHERMIC: effect = "Hypothermic: speed -20%, no natural healing, 1 damage every "
                    + com.bpm.minotaur.gamedata.player.Exposure.HYPOTHERMIA_DAMAGE_EVERY
                    + " turns, and now and then a lost action."; break;
            default: effect = "No penalty."; break;
        }
        String wetLine = wet == com.bpm.minotaur.gamedata.player.Exposure.Wetness.DRY ? ""
                : "\nYou are " + wet.label.toLowerCase(java.util.Locale.ROOT) + ": heat goes "
                        + (int) wet.heatLossMult + "x as fast.";
        String remedy = band == com.bpm.minotaur.gamedata.player.Exposure.Band.HOT
                ? "Drink, and get out of the heat."
                : "A cloak, warmer gear, a Tinder Bundle fire or a lit shelter hearth.";
        return new Tip("Exposure: " + band.label, "[BODY]", effect + wetLine, remedy);
    }

    public static Tip wetnessTip(com.bpm.minotaur.gamedata.player.Exposure.Wetness wet) {
        return new Tip(wet.label, "[EXPOSURE]",
                "Wet clothes lose heat " + (int) wet.heatLossMult + "x as fast. Rain wets you; "
                        + "freezing water soaks you through.",
                "Dry off by a fire. A cloak or hood keeps the rain off.");
    }
}
