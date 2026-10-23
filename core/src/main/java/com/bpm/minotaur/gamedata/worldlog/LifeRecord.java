package com.bpm.minotaur.gamedata.worldlog;

import com.bpm.minotaur.gamedata.history.Life;

import java.util.ArrayList;

/**
 * One Incarnation in the World Log (#280): who the prisoner was, the few things worth
 * remembering, and how they died. The identity is copied as text, so the entry reads the same
 * however the history moves on.
 */
public final class LifeRecord {

    /** Most notable lines kept per life. */
    public static final int MAX_LINES = 8;

    /** The life's place among every life of the soul, counting the unremembered ones. */
    public int number;
    public String name;
    /** "Brenna Thatch of the Ashen Ford" */
    public String title;
    /** "the Ashen Ford": kept apart, since a name may hold an "of" of its own (#286). */
    public String home;
    public Life.Origin origin;
    /** "Taken from the Ashen Ford in a raid by the Antlered Host." */
    public String howTheyCame;
    /** Most notable first. */
    public ArrayList<Fact> lines = new ArrayList<>();
    /** Tarmin's Hunger when the life woke, which sets its Era; -1 in records saved before Eras. */
    public int hunger = -1;
    /** Whether this life has felled a Heavy yet; only the first is a line. */
    public boolean firstHeavyNoted;

    /** The Houses' accounts of this life, written at its death (#285). */
    public ArrayList<Telling> tellings = new ArrayList<>();

    public boolean dead;
    public String epitaph;
    public String killer;
    public String place;
    public int turn;

    /** For the save reader. */
    public LifeRecord() {
    }

    LifeRecord(int number, Life life) {
        this.number = number;
        wake(life);
    }

    void wake(Life life) {
        this.name = life.name;
        this.title = life.title();
        this.home = life.settlement;
        this.origin = life.origin;
        this.howTheyCame = life.howTheyCame();
    }

    /** The Hunger at waking; a record from before Eras assumes no Warden had fallen yet. */
    public int hungerAtWaking() {
        return hunger >= 0 ? hunger : number - 1;
    }

    /** The home settlement; for a record from before it was kept, read off the title. */
    public String homeName() {
        if (home != null) return home;
        return title != null && title.startsWith(name + " of ") ? title.substring(name.length() + 4) : "nowhere remembered";
    }

    public boolean isDead() {
        return dead;
    }

    /** Places the line among the others by how notable it is; the least notable falls off past eight. */
    void note(Fact fact) {
        int at = 0;
        while (at < lines.size() && lines.get(at).kind.weight >= fact.kind.weight) at++;
        if (at >= MAX_LINES) return;
        lines.add(at, fact);
        if (lines.size() > MAX_LINES) lines.remove(lines.size() - 1);
    }

    void die(String epitaph, String killer, String place, int turn) {
        this.dead = true;
        this.epitaph = epitaph;
        this.killer = killer;
        this.place = place;
        this.turn = turn;
    }
}
