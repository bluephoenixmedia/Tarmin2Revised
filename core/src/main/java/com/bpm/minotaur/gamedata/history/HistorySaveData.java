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
    /** Every player deed, in the order it happened. */
    public List<PlayerDeed> deeds = new ArrayList<>();
    /** Event ids whose chronicle entries the player has unlocked through fragments. */
    public List<Integer> unlockedEvents = new ArrayList<>();
}
