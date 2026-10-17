package com.bpm.minotaur.gamedata.player;

/**
 * A poultice working on the protagonist (#190): it restores {@link #HEAL_SHARE} of max HP
 * spread over {@link #TURNS} turns, and any wound taken meanwhile tears it off, losing the
 * rest. Wounds are read from {@link PlayerStats#getWoundsTaken()}, so a hit that temporary
 * HP absorbs still counts.
 */
public final class Poultice {

    public static final float HEAL_SHARE = 0.30f;
    public static final int TURNS = 5;

    private int remainingHp;
    private int turnsLeft;
    private int woundsSeen;

    private Poultice(int totalHp, int woundsTaken) {
        this.remainingHp = totalHp;
        this.turnsLeft = TURNS;
        this.woundsSeen = woundsTaken;
    }

    /** A poultice just applied to someone with {@code maxHp}, who has taken {@code woundsTaken} wounds so far. */
    public static Poultice apply(int maxHp, int woundsTaken) {
        return new Poultice(Math.max(1, Math.round(maxHp * HEAL_SHARE)), woundsTaken);
    }

    /**
     * Advances one turn. Returns the HP to restore this turn, or -1 when a wound since the
     * last turn has cancelled the rest.
     */
    public int afterTurn(int woundsTaken) {
        if (woundsTaken != woundsSeen) {
            remainingHp = 0;
            turnsLeft = 0;
            return -1;
        }
        if (turnsLeft <= 0) return 0;
        int heal = (remainingHp + turnsLeft - 1) / turnsLeft;
        remainingHp -= heal;
        turnsLeft--;
        return heal;
    }

    public boolean isDone() {
        return turnsLeft <= 0;
    }

    public int getRemainingHp() {
        return remainingHp;
    }
}
