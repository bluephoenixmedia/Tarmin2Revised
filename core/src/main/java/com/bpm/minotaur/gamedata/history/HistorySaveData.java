package com.bpm.minotaur.gamedata.history;

import java.util.ArrayList;
import java.util.List;

/**
 * The history as the save stores it: a replay, not a snapshot (ADR 0004). Prehistory comes back
 * from {@link #seed}; the other fields are everything a seed cannot reproduce.
 */
public class HistorySaveData {
    /**
     * The seed prehistory grows from. Kept apart from the world seed, which death re-rolls:
     * the map is reborn, the Maze remembers.
     */
    public Long seed;
    public int liveSeasons;
    /**
     * Player turns since the history began, surface and strata alike (plan D22). Fronts are a
     * function of it, so it is state, not replay: a reload simply resumes it.
     */
    public long warClock;
    /** Megabeast wounds: ids and the hit points each has left. Beasts not listed are whole. */
    public List<Integer> beastHpIds = new ArrayList<>();
    public List<Integer> beastHpValues = new ArrayList<>();
    /** A megabeast following the player, or null. */
    public com.bpm.minotaur.gamedata.history.beast.BeastTracks.Hunt hunt;
    /** Every player deed, in the order it happened. */
    public List<PlayerDeed> deeds = new ArrayList<>();
    /** Event ids whose chronicle entries the player has unlocked through fragments. */
    public List<Integer> unlockedEvents = new ArrayList<>();
}
