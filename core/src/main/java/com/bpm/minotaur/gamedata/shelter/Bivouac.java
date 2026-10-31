package com.bpm.minotaur.gamedata.shelter;

import com.bpm.minotaur.gamedata.item.Item;

import java.util.Random;

/**
 * A night in the field (Living War A60-A63): what the kit lasts, what the world may do about a
 * sleeper, and what the sleep is worth. The world moves as far as it does for a Shelter sleep, and
 * none of the Shelter's safety or services come with it. All arithmetic, so it runs headless.
 */
public final class Bivouac {

    /** Nights a new kit lasts, and what a mending gives back for one Leather Scrap. */
    public static final int KIT_NIGHTS = 10;
    public static final int MEND_NIGHTS = 3;
    /** What a worn-out kit comes apart into. */
    public static final int WORN_SCRAPS = 2;

    /** Share of max HP and MP a whole night gives back. */
    public static final float REST_SHARE = 0.5f;
    /** A night counts one turn in this many toward lingering. */
    public static final int LINGER_EVERY = 4;

    private static final float AMBUSH_BASE = 0.15f;
    private static final float AMBUSH_PER_EDL = 0.01f;
    private static final float AMBUSH_PER_HUNGER_STAGE = 0.03f;
    private static final float AMBUSH_UNDERGROUND = 1.5f;
    private static final float AMBUSH_FIRE = 1.5f;
    public static final float AMBUSH_CAP = 0.75f;
    private static final int AMBUSHERS_MIN = 1;
    private static final int AMBUSHERS_MAX = 3;

    private Bivouac() {
    }

    /** The chance a night is ambushed (A62). */
    public static float ambushChance(int edl, int hungerStage, boolean underground, boolean fire) {
        float chance = AMBUSH_BASE + AMBUSH_PER_EDL * Math.max(0, edl) + AMBUSH_PER_HUNGER_STAGE * Math.max(0, hungerStage);
        if (underground) chance *= AMBUSH_UNDERGROUND;
        if (fire) chance *= AMBUSH_FIRE;
        return Math.min(AMBUSH_CAP, chance);
    }

    /** What is coming for this sleeper tonight, if anything. */
    public static final class Night {
        /** The turn of the sleep the ambushers arrive, or -1 for a quiet night. */
        public final int ambushTurn;
        public final int ambushers;

        public Night(int ambushTurn, int ambushers) {
            this.ambushTurn = ambushTurn;
            this.ambushers = ambushers;
        }

        public boolean quiet() {
            return ambushTurn < 0;
        }
    }

    /** Rolls a night of {@code turns} turns: 1-3 monsters at a random turn of it, if at all. */
    public static Night roll(Random rng, float chance, int turns) {
        if (rng.nextFloat() >= chance) return new Night(-1, 0);
        return new Night(1 + rng.nextInt(turns), AMBUSHERS_MIN + rng.nextInt(AMBUSHERS_MAX - AMBUSHERS_MIN + 1));
    }

    /** What sleeping {@code slept} of {@code full} turns gives back of a pool of {@code max}: cut short, less (A61). */
    public static int rest(int max, int slept, int full) {
        float share = full <= 0 ? 0f : Math.max(0f, Math.min(1f, slept / (float) full));
        return Math.round(max * REST_SHARE * share);
    }

    /** Turns a sleep of this length counts toward lingering. */
    public static int linger(int slept) {
        return slept / LINGER_EVERY;
    }

    /** Free attack rounds each adjacent hostile gets on a sleeper; a fire draws them on harder (A63). */
    public static int freeRounds(boolean fire) {
        return fire ? 2 : 1;
    }

    /** Nights left in a kit, a kit that has never been used being new. */
    public static int nightsLeft(Item kit) {
        if (kit.getMaxCharges() <= 0) {
            kit.setMaxCharges(KIT_NIGHTS);
            kit.setCurrentCharges(KIT_NIGHTS);
        }
        return kit.getCurrentCharges();
    }

    /** A night's wear; true when that was the last of it. */
    public static boolean wear(Item kit) {
        int left = nightsLeft(kit) - 1;
        kit.setCurrentCharges(Math.max(0, left));
        return left <= 0;
    }

    /** A mending, up to as good as new. */
    public static void mend(Item kit) {
        kit.setCurrentCharges(Math.min(KIT_NIGHTS, nightsLeft(kit) + MEND_NIGHTS));
    }

    /** Whether a mending would do anything. */
    public static boolean canMend(Item kit) {
        return nightsLeft(kit) < KIT_NIGHTS;
    }
}
