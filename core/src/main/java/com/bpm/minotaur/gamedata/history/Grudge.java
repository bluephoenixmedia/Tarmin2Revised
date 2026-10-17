package com.bpm.minotaur.gamedata.history;

/** One house's remembered wrong against another; it fades but feeds war (plan D25). */
public class Grudge {
    public final int holderId;
    public final int againstId;
    public final CasusBelli cause;
    public final int causeEventId;
    public float weight;

    public Grudge(int holderId, int againstId, CasusBelli cause, int causeEventId, float weight) {
        this.holderId = holderId;
        this.againstId = againstId;
        this.cause = cause;
        this.causeEventId = causeEventId;
        this.weight = weight;
    }
}
