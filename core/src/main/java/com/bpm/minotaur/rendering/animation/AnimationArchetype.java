package com.bpm.minotaur.rendering.animation;

import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemTemplate;

/**
 * Categorizes weapons and combat equipment into distinct visual and kinematic animation archetypes.
 */
public enum AnimationArchetype {
    SLASHING_1H,
    SLASHING_2H,
    AXE_CHOPPING,
    POLEARM_SWEEP,
    BLUNT_CRUSHING,
    FLAIL_WHIP,
    THRUSTING_PIERCE,
    BRAWLING,
    RANGED_BOW,
    RANGED_FIREARM,
    SHIELD;

    /**
     * Determines the appropriate animation archetype for an item using template overrides,
     * equipment flags, and intelligent keyword heuristics.
     */
    public static AnimationArchetype fromItem(Item item) {
        if (item == null) {
            return BRAWLING; // Bare hands
        }

        ItemTemplate template = item.getTemplate();
        if (template != null && template.animationArchetype != null && !template.animationArchetype.trim().isEmpty()) {
            try {
                return AnimationArchetype.valueOf(template.animationArchetype.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Fall back to heuristic classification
            }
        }

        if (item.isShield() || (template != null && template.isShield)) {
            return SHIELD;
        }

        StringBuilder sb = new StringBuilder();
        if (item.getType() != null) {
            sb.append(item.getType().name()).append(" ");
        }
        if (template != null && template.friendlyName != null) {
            sb.append(template.friendlyName.toUpperCase().replace(" ", "_")).append(" ");
        }
        String name = sb.toString();

        if (item.isRanged() || (template != null && template.isRanged)) {
            // Powder weapons only. A crossbow used to land here -- it was the sole
            // occupant, because the firearms were mis-flagged as melee and never
            // reached this branch at all -- and so played the recoil kick of a gun.
            // Now that real firearms use that profile, a crossbow draws like the bow
            // it is.
            if (name.contains("ARQUEBUS") || name.contains("BLUNDERBUS")
                    || name.contains("MUSKET") || name.contains("PISTOL") || name.contains("TUFENK")) {
                return RANGED_FIREARM;
            }
            return RANGED_BOW;
        }

        // Check for flails, whips, and chained/momentum weapons
        if (name.contains("FLAIL") || name.contains("MORNING_STAR") || name.contains("WHIP")
                || name.contains("NUNCHAKU") || name.contains("CHAIN") || name.contains("SCOURGE")) {
            return FLAIL_WHIP;
        }

        // Check for polearms and extended reach weapons
        if (name.contains("HALBERD") || name.contains("GUISARME") || name.contains("NAGINATA")
                || name.contains("GLAIVE") || name.contains("VOULGE") || name.contains("PARTISAN")
                || name.contains("RANSEUR") || name.contains("SPETUM") || name.contains("FAUCHARD")
                || name.contains("POLEARM") || name.contains("BARDICHE") || name.contains("MANCATCHER")
                || name.contains("TETSUBO") || name.contains("TRIKAL")) {
            return POLEARM_SWEEP;
        }

        // Check for blunt crushing weapons (checked before axes so maces/hammers with ItemType.AXE match properly)
        if (name.contains("MACE") || name.contains("HAMMER") || name.contains("CLUB")
                || name.contains("WARHAMMER") || name.contains("GADA") || name.contains("SAP")
                || name.contains("BO_STICK") || name.contains("KNOBKERRIE") || name.contains("BELAYING_PIN")
                || name.contains("FLINDBAR") || name.contains("ANKUS") || name.contains("QUARTERSTAFF")
                || name.contains("STAFF") || name.contains("WOODEN_CROSS")) {
            return BLUNT_CRUSHING;
        }

        // Check for axes and hatchets
        if (name.contains("AXE") || name.contains("HATCHET") || name.contains("TOMAHAWK")) {
            return AXE_CHOPPING;
        }

        // Check for two-handed colossal swords / greatswords
        if (name.contains("TWO_HANDED") || name.contains("CLAYMORE") || name.contains("FLAMBERGE")
                || name.contains("GREAT_SWORD") || name.contains("GREATSWORD") || name.contains("GIANT_KIN")) {
            return SLASHING_2H;
        }

        // Check for brawling / natural fist weapons
        if (name.contains("CESTUS") || name.contains("BAGH_NAKH") || name.contains("TALID")
                || name.contains("GLOVE_NAIL") || name.contains("PUNCH") || name.contains("KICK")
                || name.contains("FIST") || name.contains("CLAW") || name.contains("HORA")
                || name.contains("DEJADA_CESTUS")) {
            return BRAWLING;
        }

        // Check for thrusting and piercing weapons
        if (name.contains("DAGGER") || name.contains("SPEAR") || name.contains("RAPIER")
                || name.contains("STILETTO") || name.contains("DIRK") || name.contains("PIKE")
                || name.contains("JAVELIN") || name.contains("LANCE") || name.contains("TRIDENT")
                || name.contains("HARPOON") || name.contains("ASSEGAI") || name.contains("PESHKABZ")
                || name.contains("MAIN_GAUCHE") || name.contains("SAI") || name.contains("JAMBIYA")
                || name.contains("PUCHIK") || name.contains("KUKRI") || name.contains("IMPALER")
                || name.contains("RITIIK") || name.contains("KNIFE") || name.contains("RAZOR")
                || name.contains("BASILARD") || name.contains("CAVILER")) {
            return THRUSTING_PIERCE;
        }

        if (name.contains("SHIELD") || name.contains("BUCKLER")) {
            return SHIELD;
        }

        // Default 1H slashing
        return SLASHING_1H;
    }
}
