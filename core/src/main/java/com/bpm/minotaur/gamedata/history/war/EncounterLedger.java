package com.bpm.minotaur.gamedata.history.war;

import java.util.ArrayList;
import java.util.List;

/**
 * The little of the surface war that is saved (Living War W3): where each schedule slot was
 * anchored when it began, the first skirmish, which encounters the player has already dealt with,
 * and the battlefields left behind. Everything else is scheduled afresh from the clock.
 *
 * <p>Plain public lists, so the save reader can write and read it as it is.
 */
public class EncounterLedger {

    /** A slot's anchor: the player's chunk when it began, and what it turned out to be. */
    public static class Anchor {
        public long slot;
        public int x;
        public int y;
        /** {@link Encounter.Kind} name; null when the player was underground and it passed them by. */
        public String kind;

        public Anchor() {
        }

        Anchor(long slot, int x, int y, String kind) {
            this.slot = slot;
            this.x = x;
            this.y = y;
            this.kind = kind;
        }
    }

    /** A battlefield, left for three sleeps (W2). */
    public static class Aftermath {
        public int chunkX;
        public int chunkY;
        /** The sleep count after which it is cleared. */
        public int clearAtSleep;
        /** Tiles the field's props stand on, as x,y pairs. */
        public List<Integer> tiles = new ArrayList<>();
    }

    /** Anchors of recent slots, oldest first; old ones are pruned. */
    public List<Anchor> anchors = new ArrayList<>();
    /** War-clock turn the first skirmish begins, or -1 until the player first walks the surface. */
    public long firstAt = -1;
    public int firstX;
    public int firstY;
    public boolean firstAnchored;
    public boolean firstDone;
    /** {@link Encounter#key()}s the player has broken up or watched to the end. */
    public List<String> spent = new ArrayList<>();
    /** Visible encounters this expedition, and whether its pitched battle has come. */
    public int expeditionSlots;
    public boolean battleBrought;
    /** Shelter sleeps so far; aftermath fields clear by it. */
    public int sleeps;
    public List<Aftermath> aftermath = new ArrayList<>();

    /** How many old anchors are kept. */
    static final int KEEP = 8;

    Anchor anchor(long slot) {
        for (Anchor a : anchors) if (a.slot == slot) return a;
        return null;
    }

    Anchor setAnchor(long slot, int x, int y, Encounter.Kind kind) {
        Anchor a = new Anchor(slot, x, y, kind == null ? null : kind.name());
        anchors.add(a);
        while (anchors.size() > KEEP) anchors.remove(0);
        return a;
    }

    public boolean isSpent(Encounter e) {
        return spent.contains(e.key());
    }

    public void spend(Encounter e) {
        if (!spent.contains(e.key())) spent.add(e.key());
        while (spent.size() > 64) spent.remove(0);
    }

    /** A shelter sleep, or a death: a new expedition begins. */
    public void newExpedition(boolean slept) {
        expeditionSlots = 0;
        battleBrought = false;
        if (slept) sleeps++;
    }
}
