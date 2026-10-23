package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.worldlog.WorldLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The myth comes out in layers as Tarmin's Hunger rises (#267, #272, #288): at first only
 * revenge and "old shrines"; then the Bridge, the tithe and the old gods' names; then the first
 * life, the Treasure and the Rift; and at the last stand, the whole truth. A revelation is read
 * once, in order, from the fragments and Void glyphs found once its layer is open. ASCII only.
 */
public final class Reveal {

    public enum Layer { ARRIVAL, BRIDGE, FIRST_LIFE, LAST_STAND }

    /** The Hunger at which the Bridge layer and the first-life layer open: the Doom stages. */
    static final int BRIDGE_AT = com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_2;
    static final int FIRST_LIFE_AT = com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_3;

    private static final Object[][] REVELATIONS = {
            {Layer.BRIDGE, "The dead of the maze are not lost. Tarmin lays them, stone by stone, into a Bridge of Souls, and it reaches toward the world above."},
            {Layer.BRIDGE, "The kingdoms above pay their tithe in prisoners, that the Antlered Host might raid them less. Every seeker is a stone owed."},
            {Layer.BRIDGE, "Thirteen old powers keep shrines in the maze, older than Tarmin: Asterion, Ixxath, Ossery, Harrowgast, Grandfather Ash, the Unremembered, Mawgrim, Sessaline, Bulwark, the Colony, Old Muddjaw, Venna and the Hollow Hood."},
            {Layer.FIRST_LIFE, "Long ago a seeker came to the island of Tarmin, slew the Minotaur King who warded it, and carried off its Treasure."},
            {Layer.FIRST_LIFE, "The Treasure was the seal on the Obsidian Rift. With the warden dead and the seal gone, Tarmin-Zul rose through the Rift and took the island's name for his own."},
            {Layer.FIRST_LIFE, "The old gods speak of a day they call Revenge of the Master, when the old order returns to the maze."},
            {Layer.LAST_STAND, "The old gods keep one soul whole when every other is laid in the Bridge, and send it back against Tarmin, life after life."},
            {Layer.LAST_STAND, "The soul you wear was that first seeker. Only the one who broke the seal can mend it."}
    };

    private Reveal() {
    }

    /** How much the soul may learn: by the Hunger, or all of it once the Warden stands. */
    public static Layer layerFor(int hunger, boolean wardenActive) {
        if (wardenActive) return Layer.LAST_STAND;
        if (hunger >= FIRST_LIFE_AT) return Layer.FIRST_LIFE;
        if (hunger >= BRIDGE_AT) return Layer.BRIDGE;
        return Layer.ARRIVAL;
    }

    /** The next revelation the log has not read, if {@code layer} has opened it; marked read. Null if none. */
    public static String take(WorldLog log, Layer layer) {
        int next = log.revelationsRead();
        if (next >= REVELATIONS.length) return null;
        if (((Layer) REVELATIONS[next][0]).ordinal() > layer.ordinal()) return null;
        log.markRevelationRead();
        return (String) REVELATIONS[next][1];
    }

    public static List<String> all() {
        List<String> texts = new ArrayList<>();
        for (Object[] r : REVELATIONS) texts.add((String) r[1]);
        return texts;
    }
}
