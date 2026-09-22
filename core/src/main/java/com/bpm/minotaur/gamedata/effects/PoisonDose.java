package com.bpm.minotaur.gamedata.effects;

import com.badlogic.gdx.math.MathUtils;

/**
 * How much venom a monster delivers.
 *
 * <p>{@code POISONED} used to be a pure timer: every caller passed a hard-coded duration and
 * nothing ever applied damage, so being poisoned cost the player nothing. Duration now comes from
 * here instead, scaled by the level of whatever bit you, so venom from the deep strata is a
 * problem you have to answer rather than a status icon that expires on its own.
 *
 * <p>Duration climbs with level; per-tick damage climbs far more slowly and is capped, because
 * potency is what actually kills. A long weak poison is a race against the clock, which is the
 * intent; a short lethal one is just an unavoidable chunk of HP.
 */
public final class PoisonDose {

    /** No dose outlasts this, regardless of what delivered it. */
    public static final int MAX_TICKS = 18;

    /** Hard ceiling on damage per tick. */
    public static final int MAX_POTENCY = 4;

    private PoisonDose() {
    }

    /**
     * Turns of poison delivered by a monster of the given level.
     *
     * <p>Random within a band that widens as level rises, so the same creature is not always
     * exactly as dangerous.
     */
    public static int ticksFor(int monsterLevel) {
        int level = Math.max(1, monsterLevel);
        // Guard against overflow from a degenerate level before it reaches the RNG.
        if (level > MAX_TICKS) {
            level = MAX_TICKS;
        }

        int min = 3 + (level - 1);
        int max = 6 + (level - 1) * 2;

        min = Math.min(min, MAX_TICKS);
        max = Math.min(max, MAX_TICKS);
        if (max < min) {
            max = min;
        }
        return MathUtils.random(min, max);
    }

    /** Damage per tick from a monster of the given level: one point per five levels, capped. */
    public static int potencyFor(int monsterLevel) {
        int level = Math.max(1, monsterLevel);
        int potency = 1 + (level / 5);
        return Math.min(potency, MAX_POTENCY);
    }
}
