package com.bpm.minotaur.gamedata.history;

import java.util.ArrayList;
import java.util.List;

/** A named character of the history: a lord, an heir, kin, or a sworn sword (plan D9). */
public class Figure {

    public enum Role { KIN, SWORN_SWORD }

    public enum Fate { NATURAL, BATTLE, ASSASSINATED, EXECUTED, DISPUTE, PLAYER }

    public final int id;
    public final String name;
    public final int houseId;
    public final int birthSeason;
    public final Role role;
    public final int parentId;
    public final List<Trait> traits = new ArrayList<>();
    public int deathSeason = -1;
    public Fate fate;
    /** House holding this figure hostage, or -1. */
    public int hostageOf = -1;
    public int spouseId = -1;
    /** Never dies of age. Tarmin-Zul only. */
    public boolean ageless;

    public Figure(int id, String name, int houseId, int birthSeason, Role role, int parentId) {
        this.id = id;
        this.name = name;
        this.houseId = houseId;
        this.birthSeason = birthSeason;
        this.role = role;
        this.parentId = parentId;
    }

    public boolean isAlive() {
        return deathSeason < 0;
    }

    public int ageAt(int season) {
        return (season - birthSeason) / HistoryWorld.SEASONS_PER_YEAR;
    }

    public boolean has(Trait t) {
        return traits.contains(t);
    }

    @Override
    public String toString() {
        return name + "#" + id;
    }
}
