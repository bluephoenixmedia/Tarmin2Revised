package com.bpm.minotaur.gamedata.history;

/** A power of the Maze (plan D5). Great houses hold a gash; lesser houses hold a holdfast. */
public class House {
    public final int id;
    public final String name;
    public final String doctrineId;
    public final String sigil;
    public final String words;
    public final int foundedSeason;
    /** The holdfast's name; a gash or the castle is named by {@link HistoryWorld}. */
    public final String holdfast;
    /** 0..2 for the gash this house holds, or -1. */
    public int gashIndex = -1;
    /** True for Tarmin-Zul's house, whose seat is the castle. */
    public boolean holdsCastle;
    public int lordId = -1;
    /** The house this one is sworn to, or -1. */
    public int liegeId = -1;
    public float strength;
    public int prestige;
    public int extinctSeason = -1;

    public House(int id, String name, String doctrineId, String sigil, String words, int foundedSeason,
            String holdfast) {
        this.id = id;
        this.name = name;
        this.doctrineId = doctrineId;
        this.sigil = sigil;
        this.words = words;
        this.foundedSeason = foundedSeason;
        this.holdfast = holdfast;
    }

    public boolean isExtinct() {
        return extinctSeason >= 0;
    }

    public boolean isGreat() {
        return gashIndex >= 0;
    }

    public float strengthCap() {
        if (holdsCastle) return 120f;
        return isGreat() ? 100f : 60f;
    }

    @Override
    public String toString() {
        return name + "#" + id;
    }
}
