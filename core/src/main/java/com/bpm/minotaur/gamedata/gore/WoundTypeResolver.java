package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.DamageType;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;

/**
 * Which kind of wound a blow leaves.
 *
 * <p>The weapon's own damage type is authoritative. The mapping used to guess from the animation
 * archetype alone, so a club whose swing reads as a slash left cuts, and anything unrecognised fell
 * through to a slash. The archetype now only settles what the damage type cannot say: a bow or a
 * gun leaves a puncture whatever its data says, and it is the fallback when the type is missing.
 */
public final class WoundTypeResolver {

    private WoundTypeResolver() {
    }

    /**
     * @param weaponDamageType the weapon's SLASHING / PIERCING / BLUDGEONING string, or null
     * @param archetype        how the weapon is animated
     * @param finesse          whether the weapon is a finesse weapon (narrow, razor cuts)
     * @param elemental        the element of the blow, or null for a purely physical one
     */
    public static WoundDecal.WoundType resolve(String weaponDamageType, AnimationArchetype archetype,
            boolean finesse, DamageType elemental) {
        if (elemental == DamageType.FIRE || elemental == DamageType.MAGICAL || elemental == DamageType.SORCERY) {
            return WoundDecal.WoundType.SCORCH;
        }
        if (archetype == AnimationArchetype.RANGED_BOW || archetype == AnimationArchetype.RANGED_FIREARM) {
            return WoundDecal.WoundType.PUNCTURE;
        }

        String dt = (weaponDamageType != null) ? weaponDamageType.toUpperCase() : "";
        if (dt.contains("BLUDGEON")) {
            return WoundDecal.WoundType.CRUSH;
        }
        if (dt.contains("PIERC")) {
            return WoundDecal.WoundType.STAB;
        }
        if (dt.contains("SLASH")) {
            return finesse ? WoundDecal.WoundType.SLICE : WoundDecal.WoundType.SLASH;
        }

        // No usable damage type: fall back to how the weapon is animated.
        if (archetype != null) {
            switch (archetype) {
                case BLUNT_CRUSHING:
                case FLAIL_WHIP:
                case BRAWLING:
                case SHIELD:
                    return WoundDecal.WoundType.CRUSH;
                case THRUSTING_PIERCE:
                    return WoundDecal.WoundType.STAB;
                default:
                    break;
            }
        }
        return finesse ? WoundDecal.WoundType.SLICE : WoundDecal.WoundType.SLASH;
    }
}
