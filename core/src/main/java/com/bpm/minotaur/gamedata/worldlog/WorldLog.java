package com.bpm.minotaur.gamedata.worldlog;

import com.bpm.minotaur.gamedata.history.Life;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The plain record of every Incarnation in order (#268, #280): a list of its own in the slot,
 * read beside the history but never replayed into it (ADR 0004). Lives lived before the log
 * existed are one count, the lives unremembered.
 */
public final class WorldLog {

    private int unremembered;
    /** The deepest level any life of the soul has reached; only a new one is worth a line. */
    private int soulDeepest;
    private ArrayList<LifeRecord> lives = new ArrayList<>();

    /** A new world: nothing forgotten, nothing reached. Also the save reader's constructor. */
    public WorldLog() {
    }

    /** The log for a slot that lived before it: {@code hunger} lives forgotten, their depth kept. */
    public static WorldLog upgradedFrom(int hunger, int deepestLevel) {
        WorldLog log = new WorldLog();
        log.unremembered = Math.max(0, hunger);
        log.soulDeepest = deepestLevel;
        return log;
    }

    public int unremembered() {
        return unremembered;
    }

    public int soulDeepest() {
        return soulDeepest;
    }

    /** Oldest first. */
    public List<LifeRecord> lives() {
        return Collections.unmodifiableList(lives);
    }

    /** The life being lived, or null between a death and the next waking. */
    public LifeRecord current() {
        if (lives.isEmpty()) return null;
        LifeRecord last = lives.get(lives.size() - 1);
        return last.isDead() ? null : last;
    }

    /**
     * The soul wakes in {@code life}. A life that has done nothing worth a line yet is still
     * being chosen -- the reroll -- so it takes the new identity instead of starting another.
     */
    public void wake(Life life) {
        LifeRecord open = current();
        if (open != null && open.lines.isEmpty()) {
            open.wake(life);
            return;
        }
        lives.add(new LifeRecord(unremembered + lives.size() + 1, life));
    }

    public void note(Fact fact) {
        LifeRecord open = current();
        if (open != null) open.note(fact);
    }

    /** A level reached; a line only when no life of the soul has been deeper. */
    public void noteDepth(int level) {
        if (level <= soulDeepest) return;
        soulDeepest = level;
        note(Fact.of(Fact.Kind.DEEPEST_STRATUM, "level " + level));
    }

    /** A kill: every boss is a line, and the first Heavy of each life. */
    public void noteKill(String monster, boolean heavy, boolean boss) {
        LifeRecord open = current();
        if (open == null) return;
        if (boss) {
            open.note(Fact.of(Fact.Kind.BOSS_SLAIN, monster));
        } else if (heavy && !open.firstHeavyNoted) {
            open.firstHeavyNoted = true;
            open.note(Fact.of(Fact.Kind.FIRST_HEAVY, monster));
        }
    }

    public void die(String epitaph, String killer, String place, int turn) {
        LifeRecord open = current();
        if (open != null) open.die(epitaph, killer, place, turn);
    }
}
