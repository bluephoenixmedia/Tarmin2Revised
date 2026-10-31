package com.bpm.minotaur.gamedata.history.war;

import java.util.ArrayList;
import java.util.List;

/**
 * The little of the surface war the roster does not hold (ADR 0006): which encounters the player
 * has already dealt with, and the battlefields left behind.
 *
 * <p>Plain public lists, so the save reader can write and read it as it is.
 */
public class EncounterLedger {

    /**
     * Props the war left in a chunk, so they can be taken away again: a battlefield, cleared after
     * three sleeps (W2), or a war camp, struck when its war ends.
     */
    public static class Dressing {
        public int chunkX;
        public int chunkY;
        /** "AFTERMATH" or "CAMP". */
        public String kind;
        /** AFTERMATH: the sleep count it is cleared at. */
        public int clearAtSleep;
        /** CAMP: the war it stands for, and the house that pitched it. */
        public int warId = -1;
        public int houseId = -1;
        /** The props' tiles as x,y pairs, and their prop ids, in step. */
        public List<Integer> tiles = new ArrayList<>();
        public List<String> props = new ArrayList<>();
    }

    /** A battle's field, not yet laid on its chunk: every battle leaves one, watched or not (A23, A24). */
    public static class Field {
        public int chunkX;
        public int chunkY;
        public int loser = -1;
        /** The sleep count it is gone by. */
        public int clearAtSleep;
        /** The player stood there to the end, and has had the spoils; anyone later finds what is left. */
        public boolean witnessed;
    }

    /** Battlefields waiting for someone to come and see them. */
    public List<Field> fields = new ArrayList<>();

    /** {@link Encounter#key()}s the player has broken up or watched to the end. */
    public List<String> spent = new ArrayList<>();
    /** Chunks whose natives have already paid for a host passing, as "x,y,season" (A29). */
    public List<String> thinned = new ArrayList<>();
    /** Shelter sleeps so far; battlefields clear by it. */
    public int sleeps;
    public List<Dressing> dressings = new ArrayList<>();
    /** Houses whose heralds the player has cut down, once for each (W29); favour will hear of it. */
    public List<Integer> heraldsSlain = new ArrayList<>();

    public boolean isSpent(Encounter e) {
        return spent.contains(e.key());
    }

    public void spend(Encounter e) {
        if (!spent.contains(e.key())) spent.add(e.key());
        while (spent.size() > 64) spent.remove(0);
    }

    /** A shelter sleep, or a death: a new expedition begins. */
    public void newExpedition(boolean slept) {
        if (slept) sleeps++;
        fields.removeIf(f -> sleeps >= f.clearAtSleep);
    }

    /** A battle has ended here. {@code afterSleeps} is how long its field lasts. */
    public void battleFought(int chunkX, int chunkY, int loser, boolean witnessed, int afterSleeps) {
        Field f = new Field();
        f.chunkX = chunkX;
        f.chunkY = chunkY;
        f.loser = loser;
        f.witnessed = witnessed;
        f.clearAtSleep = sleeps + afterSleeps;
        fields.add(f);
        while (fields.size() > 64) fields.remove(0);
    }
}
