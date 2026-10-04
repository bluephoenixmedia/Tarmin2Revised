package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.spells.VisualArchetype;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;

/**
 * What killed a monster, as far as its death gore cares: the weapon that swung,
 * the spell that landed, or neither (bleed, poison, the environment).
 *
 * <p>Before this the kill gore always read the player's right hand, so a
 * fireball kill came apart like a sword kill.
 */
public final class KillCause {

    private static final KillCause NONE = new KillCause(null, null, false);

    /** The weapon's swing archetype, or null. */
    public final AnimationArchetype weapon;
    /** The spell's visual archetype, or null. Wins over {@link #weapon}. */
    public final VisualArchetype spell;
    /** The killing blow was a critical hit or a combo finisher. */
    public final boolean critOrFinisher;

    private KillCause(AnimationArchetype weapon, VisualArchetype spell, boolean critOrFinisher) {
        this.weapon = weapon;
        this.spell = spell;
        this.critOrFinisher = critOrFinisher;
    }

    public static KillCause weapon(AnimationArchetype weapon, boolean critOrFinisher) {
        return new KillCause(weapon, null, critOrFinisher);
    }

    public static KillCause spell(VisualArchetype spell) {
        return spell == null ? NONE : new KillCause(null, spell, false);
    }

    public static KillCause none() {
        return NONE;
    }
}
