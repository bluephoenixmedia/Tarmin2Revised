package com.bpm.minotaur.gamedata.worldlog;

/**
 * The Knell's account of how a Bridge ended (#276, #290): its Warden's fall, Tarmin's crossing,
 * or the Last Crossing, told plainly and mythically. Kept with the World Log.
 */
public final class Reckoning {

    public enum End { WARDEN_FELL, CROSSED, LAST_CROSSING }

    /** Which Bridge it closed, counting from 0. */
    public int bridge;
    public End end;
    public String plain;
    public String mythic;

    /** For the save reader. */
    public Reckoning() {
    }

    Reckoning(End end, String plain, String mythic) {
        this.end = end;
        this.plain = plain;
        this.mythic = mythic;
    }
}
