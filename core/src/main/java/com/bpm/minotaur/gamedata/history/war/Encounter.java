package com.bpm.minotaur.gamedata.history.war;

import com.badlogic.gdx.math.GridPoint2;

/**
 * A piece of the surface war happening now (Living War W2): a skirmish, a column on the march, a
 * raid, a pitched battle brought to the player, a war camp, or a fight too far off to see. Made by
 * {@link EncounterScheduler}; never saved -- the same clock and ledger always make the same ones.
 */
public final class Encounter {

    public enum Kind {
        /** Two war-bands meeting in a chunk, fighting until one routs. */
        SKIRMISH,
        /** One house's soldiers marching in file across chunks. */
        COLUMN,
        /** A small party of one house burning another's ground. */
        RAID,
        /** A pitched battle: a front brought to where the player is (W5). */
        BATTLE,
        /** A house's camp near a front, for as long as the war lasts. */
        CAMP,
        /** A fight out of sight: heard, and seen as smoke, never walked into. */
        DISTANT
    }

    public final Kind kind;
    /** The schedule slot it belongs to; -1 for the first skirmish, -2 for a camp. */
    public final long slot;
    /** The war it is part of, or -1 for a raid in peacetime. */
    public final int warId;
    /** The acting house: the attacker, the marchers, the raiders, the camp's. */
    public final int houseA;
    /** The other side, or -1 (a column, a camp). */
    public final int houseB;
    public final long start;
    public final long end;
    /** Where it begins, and where it ends; the same chunk for all but a column. */
    public final GridPoint2 from;
    public final GridPoint2 to;

    public Encounter(Kind kind, long slot, int warId, int houseA, int houseB, long start, long end,
            GridPoint2 from, GridPoint2 to) {
        this.kind = kind;
        this.slot = slot;
        this.warId = warId;
        this.houseA = houseA;
        this.houseB = houseB;
        this.start = start;
        this.end = end;
        this.from = new GridPoint2(from);
        this.to = new GridPoint2(to);
    }

    public boolean activeAt(long clock) {
        return clock >= start && clock < end;
    }

    /** The chunk it is in at {@code clock}: a column walks its route at an even pace. */
    public GridPoint2 chunkAt(long clock) {
        if (from.equals(to) || end <= start) return new GridPoint2(from);
        float t = Math.max(0f, Math.min(1f, (clock - start) / (float) (end - start)));
        return new GridPoint2(Math.round(from.x + (to.x - from.x) * t), Math.round(from.y + (to.y - from.y) * t));
    }

    /** Whether the player can walk into it: everything but a distant fight. */
    public boolean visible() {
        return kind != Kind.DISTANT;
    }

    /** Whether it is a fight, heard as one. A column marches and a camp waits. */
    public boolean fighting() {
        return kind == Kind.SKIRMISH || kind == Kind.RAID || kind == Kind.BATTLE || kind == Kind.DISTANT;
    }

    /** What the ledger calls it once the player has dealt with it. */
    public String key() {
        if (kind == Kind.CAMP) return kind.name() + ":" + slot + ":" + warId + ":" + houseA;
        // Raids below ground share slots with the surface's; the place tells them apart.
        if (kind == Kind.RAID) return kind.name() + ":" + slot + "@" + from.x + "," + from.y;
        return kind.name() + ":" + slot;
    }

    @Override
    public String toString() {
        return kind + "@" + slot + " h" + houseA + ">" + houseB + " " + from + (from.equals(to) ? "" : "->" + to)
                + " [" + start + "," + end + ")";
    }
}
