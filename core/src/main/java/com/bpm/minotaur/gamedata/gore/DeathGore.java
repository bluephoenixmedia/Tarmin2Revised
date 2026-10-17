package com.bpm.minotaur.gamedata.gore;

import com.bpm.minotaur.gamedata.spells.VisualArchetype;
import com.bpm.minotaur.rendering.animation.AnimationArchetype;

/**
 * Decides how a monster dies on screen: how hard it was overkilled, what
 * killed it, and therefore which death plays and what corpse it leaves.
 *
 * <p>Pure rules, no rendering: {@code CombatManager} carries a {@link Plan} out.
 * The Project Brutality shape is that a normal kill and a gib death are
 * different events -- tier 1 plays the monster's own death art with its
 * weapon's gore, tier 2 throws the death art away for a full gib death.
 */
public final class DeathGore {

    /** Overkill, as a share of max HP, that dismembers. */
    public static final float TIER1_OVERKILL = 0.15f;
    /** Overkill, as a share of max HP, that obliterates (scaled by the gore level). */
    public static final float TIER2_OVERKILL = 0.35f;

    public enum Style {
        /** The death art (or laid-down body) with a modest spray. */
        CLEAN,
        /** A full gib burst. */
        GIB,
        /** Cut in two along the swing. */
        SLICE,
        /** Smashed: shatter burst. */
        CRUSH,
        /** Run through: an arterial fountain. */
        FOUNTAIN,
        /** A heavy shot blows a gib cloud out of the far side. */
        RANGED_BURST,
        /** The head flies; the body fountains and drops. */
        DECAPITATE,
        CHARRED,
        FROST_SHATTER,
        ASH,
        MELT
    }

    public static final class Plan {
        public final Style style;
        /** The overkill tier after any spell bonus. */
        public final int tier;
        /** Play the monster's hand-drawn death art, where it has some. */
        public final boolean playDeathArt;
        /** Leave a heap rather than a body. */
        public final boolean gorePile;
        public final CorpseFinish finish;
        /** Throw gibs (meat, or ice for frost). */
        public final boolean gibs;
        /** Spray blood. Burnt, frozen, ashed and melted bodies do not bleed. */
        public final boolean blood;

        Plan(Style style, int tier, boolean playDeathArt, boolean gorePile, CorpseFinish finish,
             boolean gibs, boolean blood) {
            this.style = style;
            this.tier = tier;
            this.playDeathArt = playDeathArt;
            this.gorePile = gorePile;
            this.finish = finish;
            this.gibs = gibs;
            this.blood = blood;
        }
    }

    private DeathGore() {
    }

    /**
     * 0 for a clean kill, 1 for dismemberment, 2 for obliteration. A crit or
     * finisher always earns at least 1: gibbing is how a big hit pays off.
     */
    public static int overkillTier(int overkill, int maxHp, boolean critOrFinisher, GoreLevel level) {
        if (level == null) level = GoreLevel.NORMAL;
        if (!level.enabled()) return 0;
        int tier;
        if (maxHp <= 0) {
            tier = overkill > 0 ? 2 : 0;
        } else {
            float ratio = (float) Math.max(0, overkill) / (float) maxHp;
            if (ratio >= TIER2_OVERKILL * level.tier2ThresholdScale()) {
                tier = 2;
            } else if (ratio >= TIER1_OVERKILL) {
                tier = 1;
            } else {
                tier = 0;
            }
        }
        return critOrFinisher ? Math.max(1, tier) : tier;
    }

    public static Plan plan(KillCause cause, int tier, GoreLevel level) {
        if (level == null) level = GoreLevel.NORMAL;
        if (cause == null) cause = KillCause.none();
        tier = Math.max(0, Math.min(2, tier));
        if (!level.enabled()) {
            return new Plan(Style.CLEAN, 0, true, false, CorpseFinish.NONE, false, false);
        }

        if (cause.spell != null) {
            Plan elemental = elemental(cause.spell, tier);
            if (elemental != null) return elemental;
            if (cause.spell == VisualArchetype.FORCE_MISSILE || cause.spell == VisualArchetype.PSYCHIC_SHOCK) {
                tier = Math.min(2, tier + 1);
            }
            return byTier(tier);
        }

        AnimationArchetype w = cause.weapon;
        if (w == null) return byTier(tier);

        if (isSlashing(w) && cause.critOrFinisher) {
            return new Plan(Style.DECAPITATE, Math.max(1, tier), false, false, CorpseFinish.HEADLESS, false, true);
        }
        if (tier == 0) return byTier(0);

        boolean ranged = w == AnimationArchetype.RANGED_BOW || w == AnimationArchetype.RANGED_FIREARM;
        boolean full = tier >= 2;
        Style style;
        if (isSlashing(w)) {
            style = Style.SLICE;
        } else if (isBlunt(w)) {
            style = Style.CRUSH;
        } else if (ranged && full) {
            style = Style.RANGED_BURST;
        } else if (ranged || w == AnimationArchetype.THRUSTING_PIERCE) {
            style = Style.FOUNTAIN;
        } else {
            style = Style.GIB;
        }
        return new Plan(style, tier, !full, full, CorpseFinish.NONE, style != Style.FOUNTAIN || full, true);
    }

    private static Plan elemental(VisualArchetype spell, int tier) {
        switch (spell) {
            case FLAME_BOLT:
                return new Plan(Style.CHARRED, tier, true, false, CorpseFinish.CHARRED, false, false);
            case EXPLOSIVE_BURST: {
                boolean blownApart = tier >= 2;
                return new Plan(Style.CHARRED, tier, !blownApart, blownApart, CorpseFinish.CHARRED, tier >= 1, false);
            }
            case FROST_RAY:
                return new Plan(Style.FROST_SHATTER, tier, false, true, CorpseFinish.FROZEN, true, false);
            case LIGHTNING_ARC:
            case THUNDER_CONCUSSION:
                return new Plan(Style.ASH, tier, false, true, CorpseFinish.ASH, false, false);
            case TOXIC_CLOUD:
                return new Plan(Style.MELT, tier, false, true, CorpseFinish.MELTED, false, false);
            default:
                return null;
        }
    }

    private static Plan byTier(int tier) {
        if (tier >= 2) {
            return new Plan(Style.GIB, 2, false, true, CorpseFinish.NONE, true, true);
        }
        return new Plan(Style.CLEAN, tier, true, false, CorpseFinish.NONE, false, true);
    }

    public static boolean isSlashing(AnimationArchetype w) {
        return w == AnimationArchetype.SLASHING_1H || w == AnimationArchetype.SLASHING_2H
                || w == AnimationArchetype.AXE_CHOPPING || w == AnimationArchetype.POLEARM_SWEEP;
    }

    public static boolean isBlunt(AnimationArchetype w) {
        return w == AnimationArchetype.BLUNT_CRUSHING || w == AnimationArchetype.FLAIL_WHIP
                || w == AnimationArchetype.BRAWLING || w == AnimationArchetype.SHIELD;
    }
}
