package com.bpm.minotaur.ui;

import com.bpm.minotaur.managers.DoomManager;
import com.bpm.minotaur.gamedata.player.Exposure;

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

        public Tip(String title, String category, String details, String remedy) {
            this.title = UiGlyphs.sanitize(title);
            this.category = UiGlyphs.sanitize(category);
            this.details = UiGlyphs.sanitize(details);
            this.remedy = UiGlyphs.sanitize(remedy);
        }
    }

    public static Tip navigationTip(String heading, String timeDisplay, String phase) {
        String details = "Facing: " + (heading != null ? heading : "Unknown")
                + (timeDisplay != null ? "\nTime: " + timeDisplay : "");
        String remedy = phase != null ? "Phase: " + phase : "Track your heading and time of day.";
        return new Tip("Orientation & Time", "[NAVIGATION]", details, remedy);
    }

    /** True once the Hunger is close enough to the cap that the boss is the thing to know. */
    public static boolean bossNear(int deaths, int max) {
        return max > 0 && deaths * 10 >= max * 9;
    }

    /**
     * "TARMIN 4/50" (Tarmin's Hunger), with a "!" once the bridge boss is near or loose. Fits the
     * vitals cell, and reads apart from the food bar and the Hungry pill beside it.
     */
    public static String hungerLabel(int deaths, int max, boolean bossActive) {
        String label = "TARMIN " + deaths + "/" + max;
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
            details.append("The Bridge reaches the far shore. Its Warden guards the last stone,\n");
            details.append("and one more death before the Warden falls lets Tarmin cross.");
            remedy = "Slay the Warden and the Bridge crumbles: the Hunger falls back to 0.";
        } else {
            int next = DoomManager.nextDeathStageAt(deaths);
            if (next > 0) {
                details.append("It turns more savage again at ").append(next).append(" deaths.\n");
            }
            details.append("At ").append(max).append(" the Bridge reaches the far shore and its Warden comes.");
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
    public static String exposureMeterText(Exposure.Band band) {
        return UiGlyphs.sanitize(band.label.toUpperCase(java.util.Locale.ROOT));
    }

    /** How full the meter reads, 0..1: empty at the body's floor, full at heatstroke. */
    public static float exposureMeterFill(float bodyTemp) {
        float lo = Exposure.BODY_FLOOR;
        float hi = Exposure.HOT_ABOVE + 1f;
        return Math.max(0f, Math.min(1f, (bodyTemp - lo) / (hi - lo)));
    }

    public static Tip exposureTip(Exposure.Band band,
                                  Exposure.Wetness wet) {
        String effect = ExposureView.of(band).effect;
        String wetLine = wet == Exposure.Wetness.DRY ? ""
                : "\nYou are " + wet.label.toLowerCase(java.util.Locale.ROOT) + ": heat goes "
                        + (int) wet.heatLossMult + "x as fast.";
        String remedy = band == Exposure.Band.HOT
                ? "Drink, and get out of the heat."
                : "A cloak, warmer gear, a Tinder Bundle fire or a lit shelter hearth.";
        return new Tip("Exposure: " + band.label, "[BODY]", effect + wetLine, remedy);
    }

    public static Tip wetnessTip(Exposure.Wetness wet) {
        return new Tip(wet.label, "[EXPOSURE]",
                "Wet clothes lose heat " + (int) wet.heatLossMult + "x as fast. Rain wets you; "
                        + "freezing water soaks you through.",
                "Dry off by a fire. A cloak or hood keeps the rain off.");
    }
}
