package com.bpm.minotaur.managers;

/**
 * Central "has the player died" check.
 *
 * <p>Every damage source used to be responsible for noticing it had reduced the player to zero:
 * {@link CombatManager} checked after a monster's blow, {@link TurnManager} after starvation and
 * thirst ticks, and the toxic-potion path checked for itself. {@code InjuryManager} did not, so
 * bleeding out never actually killed anyone -- a bleed death only registered whenever the next
 * unrelated check happened to run, despite the game having a "Bled out from untended wounds"
 * epitaph written for it.
 *
 * <p>This runs once per frame over the player's current HP, so no damage source can silently fail
 * to kill. It is deliberately additive: the existing checks stay, because duplicate
 * {@code PLAYER_DIED} events are already made harmless by {@code isDeathTransitionTriggered} and
 * {@code GameEventManager.consumeAll} (see {@code DeathIdempotencyTest}).
 */
public final class DeathWatch {

    private boolean deathReported;

    /**
     * Observes the player's current HP.
     *
     * @return true exactly once per death, on the first observation at or below zero.
     */
    public boolean observe(int currentHp) {
        if (currentHp > 0) {
            // Re-arm on its own rather than relying on a caller remembering to reset. A missed
            // reset would otherwise make the player quietly immortal for the rest of the session,
            // which is a far worse failure than an extra (already harmless) death event.
            deathReported = false;
            return false;
        }
        if (deathReported) {
            return false;
        }
        deathReported = true;
        return true;
    }

    /** Clears the latch, so the next observation at or below zero reports again. */
    public void reset() {
        deathReported = false;
    }

    public boolean hasReportedDeath() {
        return deathReported;
    }
}
