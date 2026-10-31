package com.bpm.minotaur.gamedata.history.favour;

import com.bpm.minotaur.gamedata.history.House;
import com.bpm.minotaur.gamedata.history.HistoryWorld;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Each house's regard for the player (Living War W18, W19), from Enemy to Friend. Every house
 * starts at zero, Unknown, hostile on sight as ever (W23). What is done for one house is held
 * against the houses at war with it, at half the weight.
 */
public final class Favour {

    public enum Tier {
        /** Its soldiers hunt the player, and bounty hunters come. */
        ENEMY,
        DISTRUSTED,
        /** Hostile on sight, as every house always was. */
        UNKNOWN,
        /** Its soldiers leave the player be unless struck. */
        TOLERATED,
        /** Its soldiers salute; its camps let the player rest and trade; its oath is offered. */
        FRIEND
    }

    public static final int MIN = -100;
    public static final int MAX = 100;
    public static final int ENEMY_AT = -50;
    public static final int DISTRUSTED_AT = -10;
    public static final int TOLERATED_AT = 25;
    public static final int FRIEND_AT = 60;

    /** What the houses make of the player's deeds (W19). */
    public static final int KILLED_SOLDIER = -4;
    public static final int WON_FOR = 10;
    public static final int SIGNET_RETURNED = 15;
    public static final int HERALD_SLAIN = -15;
    public static final int OATH_BROKEN_ALL = -20;

    private final Map<Integer, Integer> byHouse = new LinkedHashMap<>();
    /** A turncoat's new liege, and the live Season until which its regard stays short of Friend (A41). */
    private int heldHouse = -1;
    private int heldUntil = -1;

    /** House {@code houseId} holds the player at Tolerated or below until {@code liveSeason}. */
    public void holdAtTolerated(int houseId, int liveSeason) {
        heldHouse = houseId;
        heldUntil = liveSeason;
        if (of(houseId) >= FRIEND_AT) set(houseId, FRIEND_AT - 1);
    }

    public int of(int houseId) {
        return byHouse.getOrDefault(houseId, 0);
    }

    public Tier tier(int houseId) {
        return tierOf(of(houseId));
    }

    public static Tier tierOf(int value) {
        if (value <= ENEMY_AT) return Tier.ENEMY;
        if (value <= DISTRUSTED_AT) return Tier.DISTRUSTED;
        if (value < TOLERATED_AT) return Tier.UNKNOWN;
        if (value < FRIEND_AT) return Tier.TOLERATED;
        return Tier.FRIEND;
    }

    /**
     * How house {@code houseId} treats the player now: the sworn house never less than Tolerated,
     * and every house at war with it an Enemy on sight (W20).
     */
    public Tier stance(HistoryWorld world, int houseId, int swornTo) {
        if (swornTo >= 0 && world != null && houseId != swornTo && world.activeWarBetween(houseId, swornTo) != null) {
            return Tier.ENEMY;
        }
        Tier t = tier(houseId);
        if (houseId == swornTo && t.ordinal() < Tier.TOLERATED.ordinal()) return Tier.TOLERATED;
        return t;
    }

    /** {@code delta} with house {@code houseId}; its enemies at war hear half of it, reversed (W19). */
    public void change(HistoryWorld world, int houseId, int delta) {
        if (world == null) set(houseId, of(houseId) + delta);
        else setHeld(world, houseId, of(houseId) + delta);
        if (world == null || delta / 2 == 0) return;
        for (House h : world.livingHouses()) {
            if (h.id != houseId && world.activeWarBetween(h.id, houseId) != null) setHeld(world, h.id, of(h.id) - delta / 2);
        }
    }

    private void setHeld(HistoryWorld world, int houseId, int value) {
        set(houseId, houseId == heldHouse && world.liveSeasons() < heldUntil ? Math.min(value, FRIEND_AT - 1) : value);
    }

    /** Every house's regard falls by {@code delta}, without spillover: a betrayal heard by all. */
    public void changeAll(HistoryWorld world, int delta) {
        if (world == null) return;
        for (House h : world.livingHouses()) set(h.id, of(h.id) + delta);
    }

    public void set(int houseId, int value) {
        byHouse.put(houseId, Math.max(MIN, Math.min(MAX, value)));
    }

    /** As the save holds it. */
    public Map<Integer, Integer> all() {
        return byHouse;
    }

    /** The word for a tier, for the War Table. */
    public static String word(Tier t) {
        switch (t) {
            case ENEMY: return "Enemy";
            case DISTRUSTED: return "Distrusted";
            case TOLERATED: return "Tolerated";
            case FRIEND: return "Friend";
            default: return "Unknown";
        }
    }
}
