package com.bpm.minotaur.gamedata.monster;

/**
 * How long a monster keeps searching for a target it can no longer see.
 *
 * <p>{@code MonsterAiManager} already walks a monster to the last tile it saw you on and gives
 * up after a while; the number it gave up after was a hardcoded 5, the same for a rat and for a
 * lich. That matters more now that Fog Cloud can break line of sight deliberately: how long a
 * cloud buys you should depend on what is chasing you.
 *
 * <p>Patience scales with intelligence because the hearing check in {@code checkAwareness}
 * already does -- range {@code 5 + intelligence}, and an intelligence-weighted roll. Making
 * tenacity scale the same way means one idea rather than two: a clever thing tracks you by
 * sound through the fog <i>and</i> keeps looking once the sound stops.
 */
public final class PursuitMemory {

    private PursuitMemory() {
    }

    /** Even a mindless thing casts about for a few turns before losing interest. */
    public static final int MIN_PATIENCE_TURNS = 3;

    /** A ceiling, so a generated monster with an absurd stat cannot hunt you forever. */
    public static final int MAX_PATIENCE_TURNS = 20;

    /** Turns a monster of this intelligence will search before giving up. */
    public static int patienceTurns(int intelligence) {
        int patience = MIN_PATIENCE_TURNS + Math.max(0, intelligence);
        return Math.min(MAX_PATIENCE_TURNS, Math.max(MIN_PATIENCE_TURNS, patience));
    }

    /**
     * Whether a monster that has gone {@code turnsSinceLastSeen} turns without sight of its
     * target should stop hunting and wander.
     */
    public static boolean shouldGiveUp(int turnsSinceLastSeen, int intelligence) {
        return turnsSinceLastSeen >= patienceTurns(intelligence);
    }
}
