package com.bpm.minotaur.gamedata.blight;

/**
 * The Blighted Marches' survival meter: the castle's rot settling into the body.
 *
 * <p>Each wilderness biome asks one survival question. Tundra asks whether you
 * can stay warm; the Blight asks how long you can stand in it. Taint rises only
 * on the Blight surface, stays with the player between expeditions, and is
 * cleared by resting in the shelter bed (or, in the field, by Ashwater). Its
 * tiers take regeneration first, then maximum HP, and at the top they bring the
 * Tarmin Legion down on the player.
 *
 * <p>The rules are pure functions so they can be tested and retuned without a
 * running game. Values are in docs/tuning_knobs.md.
 */
public final class Taint {

    public static final float MAX = 100f;

    /** Taint per turn standing on the Blight surface by day. */
    public static final float BASE_RATE = 0.06f;
    /** Dusk and night double the rate. */
    public static final float NIGHT_MULT = 2f;
    /** Extra per turn while standing in a rot pool. */
    public static final float ROT_POOL_RATE = 0.5f;
    /** A carried ward charm halves every gain from the land. */
    public static final float WARD_MULT = 0.5f;
    /** Each blow from a Blighted monster. Not reduced by the ward: the ward keeps out the air, not a claw. */
    public static final float BLIGHTED_HIT = 3f;
    /** One draught of Ashwater. */
    public static final float ASHWATER_CLEANSE = 40f;

    public static final float SLOWED_REGEN_AT = 25f;
    public static final float WASTING_AT = 50f;
    public static final float CONSUMED_AT = 75f;
    public static final float CLAIMED_AT = 100f;

    /** Natural regeneration takes this many times as long from the first tier. */
    public static final int SLOWED_REGEN_MULT = 2;
    public static final float WASTING_MAX_HP_MULT = 0.85f;
    public static final float CONSUMED_MAX_HP_MULT = 0.70f;

    /** At full Taint, the Legion is roused and a patrol musters this often (turns). */
    public static final int LEGION_MUSTER_INTERVAL = 40;
    /** A muster only adds a patrol when fewer Legion than this are already in the chunk. */
    public static final int LEGION_MUSTER_CAP = 3;

    /** The tiers, for messages and the HUD. Ordered by severity. */
    public enum Tier {
        CLEAN(0f),
        TAINTED(1e-3f),
        FESTERING(SLOWED_REGEN_AT),
        WASTING(WASTING_AT),
        CONSUMED(CONSUMED_AT),
        CLAIMED(CLAIMED_AT);

        public final float threshold;

        Tier(float threshold) {
            this.threshold = threshold;
        }
    }

    private Taint() {
    }

    /** Taint gained in one turn of time on the Blight surface. */
    public static float gainPerTurn(boolean night, boolean inRotPool, boolean warded) {
        float gain = BASE_RATE * (night ? NIGHT_MULT : 1f);
        if (inRotPool) gain += ROT_POOL_RATE;
        if (warded) gain *= WARD_MULT;
        return gain;
    }

    public static float clamp(float taint) {
        return Math.max(0f, Math.min(MAX, taint));
    }

    public static Tier tierOf(float taint) {
        Tier out = Tier.CLEAN;
        for (Tier t : Tier.values()) {
            if (taint >= t.threshold) out = t;
        }
        return out;
    }

    /** Multiplier on maximum HP. */
    public static float maxHpMult(float taint) {
        if (taint >= CONSUMED_AT) return CONSUMED_MAX_HP_MULT;
        if (taint >= WASTING_AT) return WASTING_MAX_HP_MULT;
        return 1f;
    }

    /** Multiplier on the natural regeneration interval (bigger is slower). */
    public static int regenIntervalMult(float taint) {
        return taint >= SLOWED_REGEN_AT ? SLOWED_REGEN_MULT : 1;
    }

    public static boolean rousesLegion(float taint) {
        return taint >= CLAIMED_AT;
    }

    /** What crossing into a tier tells the player; null where nothing need be said. */
    public static String onEnter(Tier tier) {
        switch (tier) {
            case TAINTED:   return "The ash tastes of iron. The Blight is settling into you.";
            case FESTERING: return "Your wounds are slow to close. The Blight festers in you.";
            case WASTING:   return "Your body is wasting. The Blight has hollowed your strength.";
            case CONSUMED:  return "The Blight consumes you. Rest in your shelter, or drink Ashwater.";
            case CLAIMED:   return "The Blight has claimed you. Somewhere, a Legion horn answers.";
            default:        return null;
        }
    }
}
