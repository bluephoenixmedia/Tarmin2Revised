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
    /** Keys of the underground towns the player has found. */
    public List<String> townsFound = new ArrayList<>();
    /** Which settlement of the history each town is (ADR 0005): parallel lists, -1 for a made-up town. */
    public List<String> townSettlementKeys = new ArrayList<>();
    public List<Integer> townSettlementIds = new ArrayList<>();
    /** "townKey=figureId": the exile each town took in (plan D37). */
    public List<String> townExiles = new ArrayList<>();
    /** Standing with each town, and with each allegiance, as parallel lists. */
    public List<String> standingTowns = new ArrayList<>();
    public List<Integer> standingTownValues = new ArrayList<>();
    public List<String> standingAllegiances = new ArrayList<>();
    public List<Integer> standingAllegianceValues = new ArrayList<>();
    /** Every task a town has given the player. */
    public List<com.bpm.minotaur.gamedata.history.town.Quest> quests = new ArrayList<>();
    /** What of the surface war is not scheduled afresh (Living War W3); null in an older save. */
    public com.bpm.minotaur.gamedata.history.war.EncounterLedger encounters;
    /** Each house's regard for the player (Living War W18), as parallel lists. */
    public List<Integer> favourHouses = new ArrayList<>();
    public List<Integer> favourValues = new ArrayList<>();
    /** The player's oath, or null when sworn to no house (W20). */
    public com.bpm.minotaur.gamedata.history.favour.Oath oath;
    /** Every player deed, in the order it happened. */
    public List<PlayerDeed> deeds = new ArrayList<>();
    /** Event ids whose chronicle entries the player has unlocked through fragments. */
    public List<Integer> unlockedEvents = new ArrayList<>();
}
