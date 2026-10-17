package com.bpm.minotaur.gamedata.history.town;

import com.bpm.minotaur.gamedata.history.HistoryWorld;

/**
 * A named mortal of the history (ADR 0005): one keeper of a settlement's seat, born, seated and
 * dead in the history's own seasons. Mortals are not figures of the houses: they answer to none.
 */
public final class Mortal {
    public final int id;
    /** "Brenna Thatch". */
    public final String name;
    /** The family the seat stays in: "Thatch". */
    public final String family;
    public final int settlementId;
    public final int seat;
    public final int birthSeason;
    /** The season they took the seat. */
    public final int seatedSeason;
    /** Whom they succeeded, or -1 for the first keeper of the seat. */
    public final int predecessorId;
    /** A monster sprite to stand in for them until town art lands. */
    public final String sprite;
    public int deathSeason = -1;

    public Mortal(int id, String name, String family, int settlementId, int seat, int birthSeason, int seatedSeason,
            int predecessorId, String sprite) {
        this.id = id;
        this.name = name;
        this.family = family;
        this.settlementId = settlementId;
        this.seat = seat;
        this.birthSeason = birthSeason;
        this.seatedSeason = seatedSeason;
        this.predecessorId = predecessorId;
        this.sprite = sprite;
    }

    public boolean isAlive() {
        return deathSeason < 0;
    }

    public int ageAt(int season) {
        return (season - birthSeason) / HistoryWorld.SEASONS_PER_YEAR;
    }
}
