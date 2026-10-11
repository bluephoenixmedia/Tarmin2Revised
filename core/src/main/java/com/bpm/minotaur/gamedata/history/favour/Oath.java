package com.bpm.minotaur.gamedata.history.favour;

/**
 * The player's oath to a house (Living War W20-W22, W26): which house, the contract it has set,
 * how many it has seen done, and how far the seal-grant chain has come.
 *
 * <p>Plain public fields, so the save reader can write and read it as it is.
 */
public class Oath {

    /** Contracts between relics: every third one done earns a doctrine relic (W25). */
    public static final int RELIC_EVERY = 3;
    /** Hard contracts, done at Friend while sworn to a gash's holder, before its lord grants the seal (W26). */
    public static final int SEAL_CHAIN = 3;

    /** The house sworn to, or -1. */
    public int houseId = -1;
    /** The task it has set now, or null. */
    public Contract contract;
    /** Contracts done for it, all told. */
    public int done;
    /** Hard contracts done toward the seal. */
    public int hardDone;
    /** The seal has been granted. */
    public boolean sealGranted;

    public boolean sworn() {
        return houseId >= 0;
    }

    /** Swears to {@code house}: everything owed to a former house is gone. */
    public void swear(int house) {
        houseId = house;
        contract = null;
        done = 0;
        hardDone = 0;
        sealGranted = false;
    }

    public void forsake() {
        swear(-1);
    }

    /**
     * The contract set now is done: counts it, and says whether it earns a relic. The caller pays
     * its favour and coin.
     */
    public boolean complete() {
        if (contract == null || !contract.done) return false;
        done++;
        if (contract.hard) hardDone++;
        contract = null;
        return done % RELIC_EVERY == 0;
    }

    /** Whether the seal-grant chain is complete and its seal not yet given. */
    public boolean sealDue() {
        return sworn() && !sealGranted && hardDone >= SEAL_CHAIN;
    }
}
