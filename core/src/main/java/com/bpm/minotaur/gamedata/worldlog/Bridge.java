package com.bpm.minotaur.gamedata.worldlog;

import java.util.ArrayList;
import java.util.List;

/** One Bridge of Souls as the World Log reads it (#290): its Eras, and its Reckoning once it ends. */
public final class Bridge {

    public final int index;
    /** "the First Bridge" */
    public final String name;
    /** How it ended; null for the Bridge being built. */
    public final Reckoning reckoning;
    /** Newest first. */
    public final List<Era> eras = new ArrayList<>();

    Bridge(int index, Reckoning reckoning) {
        this.index = index;
        this.name = nameOf(index);
        this.reckoning = reckoning;
    }

    /** "the First Bridge", "the Second Bridge" */
    public static String nameOf(int index) {
        String nth = Tellings.ordinal(index + 1);
        return "the " + Character.toUpperCase(nth.charAt(0)) + nth.substring(1) + " Bridge";
    }
}
