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
}
