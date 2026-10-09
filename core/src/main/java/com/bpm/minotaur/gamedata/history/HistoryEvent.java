package com.bpm.minotaur.gamedata.history;

/**
 * One thing that happened. Field meaning depends on {@link #type}; the convention is that
 * {@code houseA}/{@code figureA} acted and {@code houseB}/{@code figureB} were acted upon.
 * For a battle, A won and B lost.
 */
public class HistoryEvent {
    public final int id;
    public final int season;
    public final EventType type;
    public int houseA = -1;
    public int houseB = -1;
    public int figureA = -1;
    public int figureB = -1;
    /** Set on every {@link EventType#WAR_DECLARED}, and on rebellions and betrayals. */
    public CasusBelli casusBelli;
    /** The earlier event that caused this one, or -1. */
    public int causeEventId = -1;
    /** The gash involved, or -1. */
    public int gashIndex = -1;
    /** A battlefield or other place name, or null. */
    public String place;
    /** Type-specific number: a seeker's ordinal, or 1 when a vassal oath was forced. */
    public int detail;
    /** The megabeast involved, or -1. */
    public int beastId = -1;

    public HistoryEvent(int id, int season, EventType type) {
        this.id = id;
        this.season = season;
        this.type = type;
    }

    public boolean involvesHouse(int houseId) {
        return houseA == houseId || houseB == houseId;
    }

    /** A stable one-line form, used for determinism checks and debugging. */
    @Override
    public String toString() {
        return id + "@" + season + " " + type + " h" + houseA + ">" + houseB + " f" + figureA + ">" + figureB
                + (casusBelli != null ? " cb=" + casusBelli : "")
                + (causeEventId >= 0 ? " cause=" + causeEventId : "")
                + (gashIndex >= 0 ? " gash=" + gashIndex : "")
                + (place != null ? " at=" + place : "")
                + (detail != 0 ? " d=" + detail : "")
                + (beastId >= 0 ? " beast=" + beastId : "");
    }
}
