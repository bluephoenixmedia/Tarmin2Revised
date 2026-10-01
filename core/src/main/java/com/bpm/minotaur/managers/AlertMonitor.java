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
        final float healthShare;
        final int unspentPoints;
        final int hungerRank;
        final int thirstRank;

        public Snapshot(float healthShare, int unspentPoints, int hungerRank, int thirstRank) {
            this.healthShare = healthShare;
            this.unspentPoints = unspentPoints;
            this.hungerRank = hungerRank;
            this.thirstRank = thirstRank;
        }
    }

    public static final float COOLDOWN_SECONDS = 3f;
    /** Health under this share is an alert; it must climb back past the re-arm share to count again. */
    public static final float LOW_HEALTH_SHARE = 0.25f;
    public static final float REARM_HEALTH_SHARE = 0.40f;

    private float cooldown;
    private boolean lowHealthArmed = true;
    private boolean noticedPending;
    private int lastUnspent;
    private int lastHunger;
    private int lastThirst;

    /** A monster has just noticed the player for the first time. */
    public void noteMonsterNoticed() {
        noticedPending = true;
    }

    /** @return the reason to alert now, or null. */
    public Reason update(float delta, Snapshot s) {
        cooldown = Math.max(0f, cooldown - delta);

        // Detect everything that changed this frame, and spend each one, so a change that arrives
        // inside the cooldown is dropped rather than waiting to fire late and out of context.
        Reason lowHealth = null;
        if (s.healthShare < LOW_HEALTH_SHARE && lowHealthArmed) {
            lowHealth = Reason.LOW_HEALTH;
            lowHealthArmed = false;
        } else if (s.healthShare >= REARM_HEALTH_SHARE) {
            lowHealthArmed = true;
        }

        Reason noticed = noticedPending ? Reason.NOTICED : null;
        noticedPending = false;

        Reason hunger = s.hungerRank > lastHunger ? Reason.HUNGER : null;
        Reason thirst = s.thirstRank > lastThirst ? Reason.THIRST : null;
        Reason points = s.unspentPoints > lastUnspent ? Reason.UNSPENT_POINTS : null;
        lastHunger = s.hungerRank;
        lastThirst = s.thirstRank;
        lastUnspent = s.unspentPoints;

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
