package com.bpm.minotaur.gamedata.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Whatever kills an Incarnation (#269, #283), named and kept: it stays in the region it killed in,
 * grows with every life it takes, and its epithet rises with them. Raised by the history from the
 * death deeds, so a replay raises the same ones (ADR 0004). ASCII only (UiGlyphs).
 */
public class Nemesis {

    public final int id;
    /** "Gorvak the Gnawer", or a house Figure's own name. */
    public final String name;
    /** The house Figure it is, or -1 for a beast of the maze. */
    public final int figureId;
    /** The monsters.json type it is placed as. */
    public final String monsterType;
    /** Its region: the level and chunk of its first kill. */
    public final int level;
    public final int chunkX;
    public final int chunkY;
    /** The lives it took, oldest first. */
    public final List<String> victims = new ArrayList<>();
    public int deathSeason = -1;

    public Nemesis(int id, String name, int figureId, String monsterType, int level, int chunkX, int chunkY) {
        this.id = id;
        this.name = name;
        this.figureId = figureId;
        this.monsterType = monsterType;
        this.level = level;
        this.chunkX = chunkX;
        this.chunkY = chunkY;
    }

    public int tally() {
        return victims.size();
    }

    public boolean isAlive() {
        return deathSeason < 0;
    }

    /** "Slayer of Brannoc Thatch", then "Bane of Two Lives", rising with every life. */
    public String epithet() {
        if (victims.isEmpty()) return "";
        if (victims.size() == 1) return "Slayer of " + victims.get(0);
        return "Bane of " + count(victims.size()) + " Lives";
    }

    /** "Gorvak the Gnawer, Slayer of Brannoc Thatch" */
    public String title() {
        String epithet = epithet();
        return epithet.isEmpty() ? name : name + ", " + epithet;
    }

    private static final String[] COUNTS = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight",
            "Nine", "Ten", "Eleven", "Twelve"};

    private static String count(int n) {
        return n < COUNTS.length ? COUNTS[n] : String.valueOf(n);
    }
}
