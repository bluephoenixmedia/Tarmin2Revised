package com.bpm.minotaur.gamedata.item;

import com.bpm.minotaur.gamedata.effects.StatusEffectType;

/**
 * What happens when the player throws an item, kept apart from the combat code so each rule can be
 * tested: how far each kind of item flies, the penalty for throwing something not made for it, and
 * what a thrown potion does to whatever it shatters on.
 */
public final class ThrowRules {

    public enum Kind { WEAPON, POTION, OTHER }

    /** To-hit penalty for hurling a weapon that was not built for throwing (a sword, a mace). */
    public static final int NON_THROWING_PENALTY = 4;
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 8;
    /** Tiles from the impact that a gas potion (poison, blindness, confusion, sleep) also reaches. */
    public static final int GAS_RADIUS = 1;

    private ThrowRules() {
    }

    public static Kind kindOf(Item item) {
        // A wand or a bow is a weapon in the data but is not hurled; it just drops.
        boolean notHurled = item.isWand() || (item.isRanged() && !item.isThrown());
        if (item.isWeapon() && !notHurled) {
            return Kind.WEAPON;
        }
        if (item.isPotion()) {
            return Kind.POTION;
        }
        return Kind.OTHER;
    }

    /**
     * Tiles the item flies. A weapon made for throwing keeps its own range; anything else carries
     * further the stronger the thrower and the lighter the item, so a dagger-weight trinket goes
     * far and a heavy two-hander drops close.
     */
    public static int range(Item item, int strengthModifier, float weight) {
        if (item.isWeapon() && item.isThrown()) {
            return Math.max(3, item.getRange());
        }
        int range = Math.round(7 + strengthModifier - weight * 0.5f);
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
    }

    public static int toHitPenalty(Item weapon) {
        return weapon.isThrown() ? 0 : NON_THROWING_PENALTY;
    }

    /** What a shattered potion does to its target. */
    public static final class Splash {
        /** Share of the target's maximum HP restored (0 for none). */
        public final float healFraction;
        /** Status applied to the target, or null. */
        public final StatusEffectType status;
        public final int duration;
        /** True for vapours, which also reach the tiles around the impact. */
        public final boolean gas;

        Splash(float healFraction, StatusEffectType status, int duration, boolean gas) {
            this.healFraction = healFraction;
            this.status = status;
            this.duration = duration;
            this.gas = gas;
        }
    }

    /** The splash for a potion's effect, or null when the potion does nothing to a target (it just breaks). */
    public static Splash splashFor(PotionEffectType effect) {
        if (effect == null) {
            return null;
        }
        switch (effect) {
            case HEALING:           return new Splash(0.25f, null, 0, false);
            case GREATER_HEALING:   return new Splash(0.50f, null, 0, false);
            case SUPERIOR_HEALING:  return new Splash(0.75f, null, 0, false);
            case SUPREME_HEALING:
            case ELIXIR_HEALTH:     return new Splash(1.00f, null, 0, false);
            case VITALITY:          return new Splash(0.25f, null, 0, false);
            case POISON:            return new Splash(0f, StatusEffectType.POISONED, 8, true);
            case BLINDNESS:         return new Splash(0f, StatusEffectType.BLIND, 6, true);
            case CONFUSION:         return new Splash(0f, StatusEffectType.CONFUSED, 6, true);
            case SLEEP:             return new Splash(0f, StatusEffectType.SLEEP, 5, true);
            case SPEED:             return new Splash(0f, StatusEffectType.HASTED, 10, false);
            case BERZERK:           return new Splash(0f, StatusEffectType.BERZERK, 8, false);
            case HEROISM:           return new Splash(0f, StatusEffectType.HEROISM, 10, false);
            case GIANT_STRENGTH:
            case HILL_GIANT_STRENGTH:
            case FIRE_GIANT_STRENGTH:
            case STORM_GIANT_STRENGTH: return new Splash(0f, StatusEffectType.GIANT_STRENGTH, 10, false);
            case INVULNERABILITY:   return new Splash(0f, StatusEffectType.INVULNERABILITY, 3, false);
            case INVISIBILITY:      return new Splash(0f, StatusEffectType.INVISIBLE, 10, false);
            case DIMINUTION:        return new Splash(0f, StatusEffectType.DIMINUTIVE, 10, false);
            case GROWTH:            return new Splash(0f, StatusEffectType.ENLARGED, 10, false);
            case RESISTANCE_FIRE:      return new Splash(0f, StatusEffectType.RESIST_FIRE, 10, false);
            case RESISTANCE_COLD:      return new Splash(0f, StatusEffectType.RESIST_COLD, 10, false);
            case RESISTANCE_LIGHTNING: return new Splash(0f, StatusEffectType.RESIST_LIGHTNING, 10, false);
            case RESISTANCE_ACID:      return new Splash(0f, StatusEffectType.RESIST_ACID, 10, false);
            case RESISTANCE_NECROTIC:  return new Splash(0f, StatusEffectType.RESIST_NECROTIC, 10, false);
            default:                return null;
        }
    }
}
