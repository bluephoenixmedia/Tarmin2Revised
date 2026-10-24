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
    /** What every Era of this log is named from. */
    private long eraSeed;
    /** Each Era's name by stage, fixed the first time a life wakes in it; "" until then. */
    private ArrayList<String> eraNames = new ArrayList<>();
    /** The Bridge being built, counting from 0 (#290). */
    private int currentBridge;
    /** How every ended Bridge ended, oldest first. */
    private ArrayList<Reckoning> reckonings = new ArrayList<>();
    /** The Age being lived, counting from 0: one Tarmin's reign (#291). */
    private int currentAge;
    /** The Age each Bridge after the first was begun in: entry {@code b - 1} is Bridge {@code b}'s. */
    private ArrayList<Integer> bridgeAges = new ArrayList<>();
    /** How every ended Age ended, oldest first. */
    private ArrayList<Reckoning> ageReckonings = new ArrayList<>();
    /** How many of the myth's revelations the soul has read, in order (#288). */
    private int revelationsRead;
    /** The deepest layer of the myth the Hunger has ever opened; a Warden's fall never closes it. */
    private int deepestLayer;

    /** A new world: nothing forgotten, nothing reached. Also the save reader's constructor. */
    public WorldLog() {
        this(new java.util.Random().nextLong());
    }

    public WorldLog(long eraSeed) {
        this.eraSeed = eraSeed;
    }

    /** The log for a slot that lived before it: {@code hunger} lives forgotten, their depth kept. */
    public static WorldLog upgradedFrom(int hunger, int deepestLevel) {
        WorldLog log = new WorldLog();
        log.unremembered = Math.max(0, hunger);
        log.soulDeepest = deepestLevel;
        return log;
    }

    public int revelationsRead() {
        return revelationsRead;
    }

    public void markRevelationRead() {
        revelationsRead++;
    }

    public int deepestLayer() {
        return deepestLayer;
    }

    public void reachLayer(int layer) {
        deepestLayer = Math.max(deepestLayer, layer);
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

    /** The newest life, living or dead, or null before any. */
    public LifeRecord last() {
        return lives.isEmpty() ? null : lives.get(lives.size() - 1);
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
        wake(life, -1);
    }

    /** As {@link #wake(Life)}, at {@code hunger} deaths: the life's Era. -1 when not known. */
    public void wake(Life life, int hunger) {
        LifeRecord open = current();
        if (open != null && open.lines.isEmpty()) {
            open.wake(life);
            return;
        }
        LifeRecord record = new LifeRecord(unremembered + lives.size() + 1, life);
        record.hunger = hunger;
        record.bridge = currentBridge;
        lives.add(record);
        eraName(currentBridge, Era.stageOf(record.hungerAtWaking()));
    }

    /** Every Era with a life in it, newest first, each with its lives newest first. */
    public List<Era> eras() {
        List<Era> eras = new ArrayList<>();
        for (Bridge bridge : bridges()) eras.addAll(bridge.eras);
        return eras;
    }

    /** The Bridge being built, counting from 0. */
    public int currentBridge() {
        return currentBridge;
    }

    /** The Bridge being built ends, told by its Reckoning; the next life wakes in a new one. */
    public void closeBridge(Reckoning reckoning) {
        reckoning.bridge = currentBridge;
        reckonings.add(reckoning);
        currentBridge++;
        // Bridges closed before Ages were kept stood in the first Age.
        while (bridgeAges.size() < currentBridge - 1) bridgeAges.add(0);
        bridgeAges.add(currentAge);
    }

    /** The Age being lived, counting from 0. */
    public int currentAge() {
        return currentAge;
    }

    /**
     * Tarmin is slain and the Age ends, told by its Reckoning (#291, #292). Its Bridge ends with it:
     * the next Tarmin begins his own, and the next life wakes in both.
     */
    public void closeAge(Reckoning reckoning) {
        reckoning.age = currentAge;
        ageReckonings.add(reckoning);
        currentAge++;
        closeBridge(reckoning);
    }

    /**
     * Ends every Age the history has ended and the log has not (#291, #292): a monster may slay
     * Tarmin off-screen, and the log learns of it at the next Shifting. Returns how many it ended.
     */
    public int closeAgesEnded(List<com.bpm.minotaur.gamedata.history.AgeEnding> endings) {
        int closed = 0;
        for (com.bpm.minotaur.gamedata.history.AgeEnding e : endings) {
            if (e.age < currentAge) continue;
            closeAge(Reckonings.ageEnded(Age.nameOf(currentAge), last(), e));
            closed++;
        }
        return closed;
    }

    /** Every Age with a Bridge or an ending, newest first, each with its Bridges newest first. */
    public List<Age> ages() {
        List<Bridge> bridges = bridges();
        List<Age> ages = new ArrayList<>();
        for (int a = currentAge; a >= 0; a--) {
            Age age = new Age(a, ageReckoningOf(a));
            for (Bridge bridge : bridges) {
                if (ageOfBridge(bridge.index) == a) age.bridges.add(bridge);
            }
            if (!age.bridges.isEmpty() || age.reckoning != null) ages.add(age);
        }
        return ages;
    }

    /** The Age a Bridge was begun in; Bridges from before Ages stand in the first. */
    public int ageOfBridge(int bridge) {
        if (bridge <= 0 || bridge - 1 >= bridgeAges.size()) return 0;
        return bridgeAges.get(bridge - 1);
    }

    private Reckoning ageReckoningOf(int age) {
        for (Reckoning r : ageReckonings) {
            if (r.age == age) return r;
        }
        return null;
    }

    /** Every Bridge with a life in it or an ending, newest first, each with its Eras newest first. */
    public List<Bridge> bridges() {
        List<Bridge> bridges = new ArrayList<>();
        for (int b = currentBridge; b >= 0; b--) {
            Bridge bridge = new Bridge(b, reckoningOf(b));
            Era era = null;
            for (int i = lives.size() - 1; i >= 0; i--) {
                LifeRecord life = lives.get(i);
                if (life.bridgeIndex() != b) continue;
                int stage = Era.stageOf(life.hungerAtWaking());
                if (era == null || era.stage != stage) {
                    era = new Era(stage, eraName(b, stage));
                    bridge.eras.add(era);
                }
                era.lives.add(life);
            }
            if (!bridge.eras.isEmpty() || bridge.reckoning != null) bridges.add(bridge);
        }
        return bridges;
    }

    private Reckoning reckoningOf(int bridge) {
        for (Reckoning r : reckonings) {
            if (r.bridge == bridge) return r;
        }
        return null;
    }

    /** The Era's name, named now and kept if it has none yet; each Bridge names its own. */
    private String eraName(int bridge, int stage) {
        int key = bridge * Era.STAGES + stage;
        while (eraNames.size() <= key) eraNames.add("");
        if (eraNames.get(key).isEmpty()) eraNames.set(key, Era.nameFor(eraSeed + bridge * 7919L, stage));
        return eraNames.get(key);
    }

    /** The Houses' accounts of life {@code number}, written as it ends (#285). */
    public void tell(int number, List<Telling> tellings) {
        for (LifeRecord life : lives) {
            if (life.number == number) life.tellings.addAll(tellings);
        }
    }

    /** Whether {@code houseId} has an account not yet found, of any life. */
    public boolean hasUnfoundTelling(int houseId) {
        return unfound(houseId, false) != null;
    }

    /**
     * A soldier of {@code houseId} takes up its oldest account no other soldier carries; false if
     * none is left to carry. One scroll per account, so no scroll is found empty.
     */
    public boolean carryTelling(int houseId) {
        Telling t = unfound(houseId, true);
        if (t == null) return false;
        t.carried = true;
        return true;
    }

    /** Finds the oldest account {@code houseId} has not yet given up, carried first; null if none is left. */
    public Telling findTelling(int houseId) {
        Telling t = null;
        for (Telling carried : tellingsOf(houseId)) {
            if (carried.carried && !carried.found) {
                t = carried;
                break;
            }
        }
        if (t == null) t = unfound(houseId, false);
        if (t != null) {
            t.found = true;
            t.carried = false;
        }
        return t;
    }

    /** A death shifts the maze: scrolls left lying in the old one are gone, and may be carried again. */
    public void dropUncollectedTellings() {
        for (LifeRecord life : lives) {
            for (Telling t : life.tellings) t.carried = false;
        }
    }

    private List<Telling> tellingsOf(int houseId) {
        List<Telling> of = new ArrayList<>();
        for (LifeRecord life : lives) {
            for (Telling t : life.tellings) {
                if (t.tellerHouseId == houseId) of.add(t);
            }
        }
        return of;
    }

    private Telling unfound(int houseId, boolean uncarried) {
        for (Telling t : tellingsOf(houseId)) {
            if (!t.found && (!uncarried || !t.carried)) return t;
        }
        return null;
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
