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
    }
}
