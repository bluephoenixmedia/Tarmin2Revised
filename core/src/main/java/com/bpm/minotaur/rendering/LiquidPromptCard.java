package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.gamedata.player.Thirst;
import com.bpm.minotaur.managers.ActionKeys;

/**
 * The text of the facing-tile card shown at a drinkable liquid (#193): what it is, what a
 * drink is worth and what it risks, and the two keys, [O] Drink and [P] Fill waterskin, read
 * from the live bindings so a rebound key shows as bound. Text only; Hud puts it on the
 * shared WorldInteractionCard.
 */
public final class LiquidPromptCard {

    public static final String DRINK_ACTION = com.bpm.minotaur.managers.Action.INTERACT;
    public static final String FILL_ACTION = com.bpm.minotaur.managers.Action.PICKUP_DROP;

    private LiquidPromptCard() {
    }

    public static String tag(boolean strata) {
        return strata ? "[STRATA LIQUID]" : "[SURFACE LIQUID]";
    }

    public static String subTag(LiquidType liquid, boolean strata) {
        float risk = Thirst.illnessRisk(liquid, strata);
        if (risk >= 1f) return "[CERTAIN ILLNESS]";
        if (risk >= 0.5f) return "[GREAT RISK]";
        if (risk > 0f) return "[SMALL RISK]";
        if (Thirst.isChilling(liquid)) return "[CHILLING]";
        return "[SAFE TO DRINK]";
    }

    public static String title(LiquidType liquid) {
        return liquid.getDisplayName();
    }

    public static String description(LiquidType liquid, boolean strata) {
        StringBuilder sb = new StringBuilder();
        sb.append(Thirst.hydrationGain(liquid) >= Thirst.DRINK_FULL ? "Quenches thirst fully." : "Quenches thirst by half.");
        int riskPct = Math.round(Thirst.illnessRisk(liquid, strata) * 100f);
        if (riskPct >= 100) {
            sb.append(" Drinking it will make you ill.");
        } else if (riskPct > 0) {
            sb.append(" About ").append(riskPct).append(" in 100 drinkers fall ill");
            sb.append(Thirst.startingIllnessStage(liquid).getSeverityLevel() >= 2 ? ", straight into fever." : ".");
        }
        if (Thirst.isChilling(liquid)) {
            sb.append(" It chills you to the bone.");
        }
        return sb.toString();
    }

    public static String keyBadge(ActionKeys keys) {
        return "[ " + keys.keyName(DRINK_ACTION) + " / " + keys.keyName(FILL_ACTION) + " ]";
    }

    public static String actionText(ActionKeys keys) {
        return "Drink [" + keys.keyName(DRINK_ACTION) + "]  |  Fill Waterskin [" + keys.keyName(FILL_ACTION) + "]";
    }
}
