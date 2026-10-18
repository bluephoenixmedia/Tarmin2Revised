package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.ui.UiTheme;

/**
 * Which colour the idle first-person weapon glows while the player is buffed (#199), so a
 * buff is visible without reading the status list.
 *
 * <p>A buff is any timed effect at or past {@link StatusEffectType#FOCUSED} in the enum (the
 * existing buff/debuff split), less the three weather exposure tiers parked at its end. A
 * permanent intrinsic ({@code duration == -1}) does not count: the weapon would glow forever
 * and stop meaning anything. An enhanced (honed) weapon glows when nothing else does.
 */
public final class BuffGlow {

    /** How fast the glow fades out for a swing and back in at rest, in full strengths per second. */
    public static final float FADE_RATE = 6f;

    private BuffGlow() {
    }

    /** The glow colour, or null for none. Colours come from {@link UiTheme}. */
    public static Color colorFor(Iterable<ActiveStatusEffect> effects, int weaponEnhancement) {
        boolean strength = false;
        boolean speed = false;
        boolean other = false;
        if (effects != null) {
            for (ActiveStatusEffect e : effects) {
                if (e == null || !isTimedBuff(e)) continue;
                switch (e.getType()) {
                    case HEROISM:
                        return UiTheme.GOLD;
                    case TEMP_STRENGTH:
                    case GIANT_STRENGTH:
                        strength = true;
                        break;
                    case HASTED:
                    case SUPER_SPEED:
                    case TEMP_SPEED:
                        speed = true;
                        break;
                    default:
                        other = true;
                }
            }
        }
        if (strength) return UiTheme.SCHOOL_WARFARE;
        if (speed) return UiTheme.INFO;
        if (other) return UiTheme.SCHOOL_ARCANA;
        if (weaponEnhancement > 0) return UiTheme.LINE_BRIGHT;
        return null;
    }

    /**
     * The effects that make the idle weapon glow while timed: the beneficial ones. Listed, not
     * taken from the enum's order, so adding or moving a status can't change it by accident.
     */
    static final java.util.Set<StatusEffectType> GLOWING_BUFFS = java.util.Collections.unmodifiableSet(
            java.util.EnumSet.of(
            StatusEffectType.FOCUSED, StatusEffectType.ADRENALINE_BOOST, StatusEffectType.HEALTHY, StatusEffectType.IMMUNE_BOOSTED,
            StatusEffectType.TEMP_STRENGTH, StatusEffectType.TEMP_SPEED, StatusEffectType.TEMP_HEALTH, StatusEffectType.PSYCHIC,
            StatusEffectType.FLOATING, StatusEffectType.FLYING, StatusEffectType.SUPER_SPEED, StatusEffectType.HARDENED,
            StatusEffectType.SUPER_INTELLIGENT, StatusEffectType.OMNISCIENT, StatusEffectType.RESIST_FIRE, StatusEffectType.RESIST_COLD,
            StatusEffectType.RESIST_LIGHTNING, StatusEffectType.RESIST_POISON, StatusEffectType.TELEPATHY, StatusEffectType.INVISIBLE,
            StatusEffectType.SEE_INVISIBLE, StatusEffectType.METABOLIZING, StatusEffectType.BLOOD_SURGE, StatusEffectType.SPIRITUAL_WARD,
            StatusEffectType.CARAPACE_HARDENING, StatusEffectType.NIGHT_HUNTER, StatusEffectType.HASTED, StatusEffectType.HEROISM,
            StatusEffectType.GIANT_STRENGTH, StatusEffectType.INVULNERABILITY, StatusEffectType.DIMINUTIVE, StatusEffectType.ENLARGED,
            StatusEffectType.RESIST_ACID, StatusEffectType.RESIST_NECROTIC, StatusEffectType.FREE_ACTION, StatusEffectType.WARMTH,
            StatusEffectType.MOTE_OF_LIGHT, StatusEffectType.COMPREHEND_LANGUAGES));

    private static boolean isTimedBuff(ActiveStatusEffect e) {
        StatusEffectType t = e.getType();
        if (t == null || e.getDuration() == -1) return false;
        return GLOWING_BUFFS.contains(t);
    }

    /** Eases the glow strength toward 1 while the weapon is idle and toward 0 during a swing. */
    public static float approach(float strength, boolean idle, float delta) {
        float target = idle ? 1f : 0f;
        float step = FADE_RATE * delta;
        if (strength < target) return Math.min(target, strength + step);
        return Math.max(target, strength - step);
    }
}
