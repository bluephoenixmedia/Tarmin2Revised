package com.bpm.minotaur.gamedata.history;

/** A declared war between two houses, alive until peace, a seizure or an extinction ends it. */
public class War {
    public final int id;
    public final int attackerId;
    public final int defenderId;
    public final CasusBelli casusBelli;
    public final int declaredEventId;
    public final int startSeason;
    public int endSeason = -1;

    public War(int id, int attackerId, int defenderId, CasusBelli casusBelli, int declaredEventId, int startSeason) {
        this.id = id;
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.casusBelli = casusBelli;
        this.declaredEventId = declaredEventId;
        this.startSeason = startSeason;
    }

    public boolean isActive() {
        return endSeason < 0;
    }

    public boolean involves(int houseId) {
        return attackerId == houseId || defenderId == houseId;
    }

    public int enemyOf(int houseId) {
        return attackerId == houseId ? defenderId : attackerId;
    }
}
