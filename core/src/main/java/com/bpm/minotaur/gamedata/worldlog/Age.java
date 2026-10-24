package com.bpm.minotaur.gamedata.worldlog;

import java.util.ArrayList;
import java.util.List;

/** One Tarmin's reign as the World Log reads it (#291): its Bridges, and its Reckoning once it ends. */
public final class Age {

    public final int index;
    /** "the First Age" */
    public final String name;
    /** How it ended; null for the Age being lived. */
    public final Reckoning reckoning;
    /** Newest first. */
    public final List<Bridge> bridges = new ArrayList<>();

    Age(int index, Reckoning reckoning) {
        this.index = index;
        this.name = nameOf(index);
        this.reckoning = reckoning;
    }

    /** "the First Age", "the Second Age" */
    public static String nameOf(int index) {
        String nth = Tellings.ordinal(index + 1);
        return "the " + Character.toUpperCase(nth.charAt(0)) + nth.substring(1) + " Age";
    }
}
