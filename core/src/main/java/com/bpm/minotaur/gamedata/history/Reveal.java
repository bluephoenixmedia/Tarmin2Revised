package com.bpm.minotaur.gamedata.history;

import com.bpm.minotaur.gamedata.worldlog.WorldLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The myth comes out in layers as Tarmin's Hunger rises (#267, #272, #288): at first only
 * revenge and "old shrines"; then the Bridge, the tithe and the old gods' names; then the first
 * life, the Treasure and the Rift; and at the last stand, the whole truth. A revelation is read
 * once, in order, from the fragments and Void glyphs found once its layer is open, and what the
 * soul has learned it keeps, whatever the Hunger does after. ASCII only (UiGlyphs).
 */
public final class Reveal {

    public enum Layer {
        ARRIVAL, BRIDGE, FIRST_LIFE, LAST_STAND;

        public boolean atLeast(Layer other) {
            return ordinal() >= other.ordinal();
        }
    }

    /** One line of the myth, and the layer that opens it. */
    private static final class Revelation {
        final Layer layer;
        final String text;

        Revelation(Layer layer, String text) {
            this.layer = layer;
            this.text = text;
        }
    }

    /** The Hunger at which the Bridge layer and the first-life layer open: the Doom stages. */
    static final int BRIDGE_AT = com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_2;
    static final int FIRST_LIFE_AT = com.bpm.minotaur.managers.DoomManager.DEATHS_FOR_STAGE_3;

    private static final List<Revelation> REVELATIONS = Arrays.asList(
            new Revelation(Layer.BRIDGE, "The dead of the maze are not lost. Tarmin lays them, stone by stone, into a Bridge of Souls, and it reaches toward the world above."),
            new Revelation(Layer.BRIDGE, "The kingdoms above pay their tithe in prisoners, that the Antlered Host might raid them less. Every seeker is a stone owed."),
            new Revelation(Layer.BRIDGE, "Thirteen old powers keep shrines in the maze, older than Tarmin: " + godNames() + "."),
            new Revelation(Layer.FIRST_LIFE, "Long ago a seeker came to the island of Tarmin, slew the Minotaur King who warded it, and carried off its Treasure."),
            new Revelation(Layer.FIRST_LIFE, "The Treasure was the seal on the Obsidian Rift. With the warden dead and the seal gone, Tarmin-Zul rose through the Rift and took the island's name for his own."),
            new Revelation(Layer.FIRST_LIFE, "The old gods speak of a day they call Revenge of the Master, when the old order returns to the maze."),
            new Revelation(Layer.LAST_STAND, "The old gods keep one soul whole when every other is laid in the Bridge, and send it back against Tarmin, life after life."),
            new Revelation(Layer.LAST_STAND, "The soul you wear was that first seeker. Only the one who broke the seal can mend it.")
    );

    /** Once this many are read, the old gods' names are known: the third names them. */
    private static final int GODS_NAMED_AFTER = 3;

    private Reveal() {
    }

    /** How much the Hunger opens: by its stage, or all of it while the Warden stands. */
    public static Layer layerFor(int hunger, boolean wardenActive) {
        if (wardenActive) return Layer.LAST_STAND;
        if (hunger >= FIRST_LIFE_AT) return Layer.FIRST_LIFE;
        if (hunger >= BRIDGE_AT) return Layer.BRIDGE;
        return Layer.ARRIVAL;
    }

    /**
     * The next revelation the log has not read, if the deepest layer the soul has reached opens it;
     * marked read. Null if none. A Warden's fall resets the Hunger, never what the soul has learned.
     */
    public static String readNext(WorldLog log, Layer now) {
        log.reachLayer(now.ordinal());
        Layer reached = Layer.values()[Math.min(log.deepestLayer(), Layer.values().length - 1)];
        int next = log.revelationsRead();
        if (next >= REVELATIONS.size() || !reached.atLeast(REVELATIONS.get(next).layer)) return null;
        log.markRevelationRead();
        return REVELATIONS.get(next).text;
    }

    /** Whether the soul has read the old gods' names, so their shrines can be told by them. */
    public static boolean godsKnown(WorldLog log) {
        return log.revelationsRead() >= GODS_NAMED_AFTER;
    }

    /** What the soul has learned, in the order it learned it. */
    public static List<String> learned(WorldLog log) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < Math.min(log.revelationsRead(), REVELATIONS.size()); i++) texts.add(REVELATIONS.get(i).text);
        return texts;
    }

    public static List<String> all() {
        List<String> texts = new ArrayList<>();
        for (Revelation r : REVELATIONS) texts.add(r.text);
        return texts;
    }

    /** "Asterion, Ixxath, ... and the Hollow Hood", from the gods themselves. */
    private static String godNames() {
        OldGod[] gods = OldGod.values();
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < gods.length; i++) {
            if (i > 0) names.append(i == gods.length - 1 ? " and " : ", ");
            String name = gods[i].shortName;
            names.append(i == 0 ? Character.toUpperCase(name.charAt(0)) + name.substring(1) : name);
        }
        return names.toString();
    }
}
