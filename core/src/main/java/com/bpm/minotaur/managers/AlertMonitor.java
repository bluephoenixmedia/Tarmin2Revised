package com.bpm.minotaur.managers;

/**
 * Decides when the player needs a red alert.
 *
 * <p>The alert only means something if it is rare, so it fires on the edge of a change and never
 * while something merely stays bad, and never more than once every {@link #COOLDOWN_SECONDS}.
 * Several things happening together give one alert, for the most pressing of them. A pure state
 * machine: the game feeds it a snapshot each frame and plays the effect when it answers.
 */
public final class AlertMonitor {

    public enum Reason {
        LOW_HEALTH, NOTICED, HUNGER, THIRST, UNSPENT_POINTS
    }

    /** What the monitor needs to know this frame. Ranks are 0 (fine), 1 (low), 2 (empty). */
    public static final class Snapshot {
        private final float healthShare;
        private final int unspentPoints;
        private final int hungerRank;
        private final int thirstRank;

        public Snapshot(float healthShare, int unspentPoints, int hungerRank, int thirstRank) {
            this.healthShare = healthShare;
            this.unspentPoints = unspentPoints;
            this.hungerRank = hungerRank;
            this.thirstRank = thirstRank;
        }

        public float healthShare() {
            return healthShare;
        }

        public int unspentPoints() {
            return unspentPoints;
        }

        public int hungerRank() {
            return hungerRank;
        }

        public int thirstRank() {
            return thirstRank;
        }

        /** Water at or below this is "thirsty"; at zero it is "parched". */
        private static final float THIRSTY_BELOW = 25f;

        public static Snapshot of(com.bpm.minotaur.gamedata.player.PlayerStats stats) {
            int maxHp = Math.max(1, stats.getMaxHP());
            com.bpm.minotaur.gamedata.player.PlayerStats.SatiationState food = stats.getSatiationState();
            int hunger = food == com.bpm.minotaur.gamedata.player.PlayerStats.SatiationState.STARVING ? 2
                    : food == com.bpm.minotaur.gamedata.player.PlayerStats.SatiationState.HUNGRY ? 1 : 0;
            float water = stats.getHydrationFloat();
            int thirst = water <= 0f ? 2 : water <= THIRSTY_BELOW ? 1 : 0;
            int points = stats.getUnallocatedAttributePoints() + stats.getUnallocatedSkillPoints();
            return new Snapshot(stats.getCurrentHP() / (float) maxHp, points, hunger, thirst);
        }
    }

    public static final float COOLDOWN_SECONDS = 3f;
    /** Health under this share is an alert; it must climb back past the re-arm share to count again. */
    public static final float LOW_HEALTH_SHARE = 0.25f;
    public static final float REARM_HEALTH_SHARE = 0.40f;

    private float cooldown;
    /** False until the first look, which only takes a baseline: a loaded save is not news. */
    private boolean primed;
    private boolean lowHealthArmed = true;
    private boolean noticedPending;
    private int lastUnspent;
    private int lastHunger;
    private int lastThirst;

    /**
     * Forgets everything it knows and takes a fresh baseline at the next look. Called when the
     * character dies and respawns: what was pending belonged to the life that ended.
     */
    public void reset() {
        primed = false;
        noticedPending = false;
        cooldown = 0f;
    }

    /** A monster has just noticed the player for the first time. */
    public void noteMonsterNoticed() {
        noticedPending = true;
    }

    /** @return the reason to alert now, or null. */
    public Reason update(float delta, Snapshot s) {
        cooldown = Math.max(0f, cooldown - delta);

        // Detect everything that changed this frame, and spend each one, so a change that arrives
        // inside the cooldown is dropped rather than waiting to fire late and out of context.
        Reason noticed = noticedPending ? Reason.NOTICED : null;
        noticedPending = false;

        Reason lowHealth = null;
        Reason hunger = null;
        Reason thirst = null;
        Reason points = null;
        if (!primed) {
            // The first look is a baseline, not a change: a save loaded already hungry, already low
            // or already owed points has nothing to announce. Low health starts spent, so it only
            // alerts again after a recovery.
            primed = true;
            lowHealthArmed = s.healthShare >= LOW_HEALTH_SHARE;
        } else {
            if (s.healthShare() < LOW_HEALTH_SHARE && lowHealthArmed) {
                lowHealth = Reason.LOW_HEALTH;
                lowHealthArmed = false;
            } else if (s.healthShare() >= REARM_HEALTH_SHARE) {
                lowHealthArmed = true;
            }
            hunger = s.hungerRank() > lastHunger ? Reason.HUNGER : null;
            thirst = s.thirstRank() > lastThirst ? Reason.THIRST : null;
            points = s.unspentPoints() > lastUnspent ? Reason.UNSPENT_POINTS : null;
        }
        lastHunger = s.hungerRank();
        lastThirst = s.thirstRank();
        lastUnspent = s.unspentPoints();

        Reason chosen = first(lowHealth, noticed, hunger, thirst, points);
        if (chosen == null || cooldown > 0f) {
            return null;
        }
        cooldown = COOLDOWN_SECONDS;
        return chosen;
    }

    private static Reason first(Reason... candidates) {
        for (Reason r : candidates) {
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}
