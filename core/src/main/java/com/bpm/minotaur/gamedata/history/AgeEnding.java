package com.bpm.minotaur.gamedata.history;

/**
 * How one Tarmin's reign, an Age, ended (#276, #291, #292): by the Champion, who returned the
 * Treasure or took the throne, or off-screen, by a House's siege, a Nemesis or a Megabeast. Whoever
 * slays Tarmin may take the name. Raised by the replay, so it is the same after a load.
 */
public final class AgeEnding {

    public enum Kind {
        /** The Champion slew Tarmin and returned the Treasure: the Rift is sealed. */
        RETURNED,
        /** The Champion slew Tarmin and took the throne. */
        THRONE,
        /** A House's siege of Castle Tarmin succeeded. */
        SIEGE,
        /** A Nemesis grown to its height challenged Tarmin and won. */
        NEMESIS,
        /** A Megabeast broke into the castle. */
        MEGABEAST
    }

    /** The Age that ended, counting from 0. */
    public final int age;
    public final Kind kind;
    /** The title of the Tarmin who fell. */
    public final String oldTitle;
    /** The title of the Tarmin who now reigns: "Tarmin-Gorvak". */
    public final String newTitle;
    /** Who took the throne, by the name they bore: "Gorvak the Gnawer". */
    public final String by;

    AgeEnding(int age, Kind kind, String oldTitle, String newTitle, String by) {
        this.age = age;
        this.kind = kind;
        this.oldTitle = oldTitle;
        this.newTitle = newTitle;
        this.by = by;
    }

    /** "Tarmin-Gorvak, who was Gorvak the Gnawer" */
    public String fullTitle() {
        return newTitle + ", who was " + by;
    }

    /** Whether the Champion ended it, rather than a monster off-screen. */
    public boolean byChampion() {
        return kind == Kind.RETURNED || kind == Kind.THRONE;
    }

    /** "Tarmin-" and the first true word of a name: "the Hooded Tam" gives "Tarmin-Hooded". */
    static String titleFrom(String name) {
        for (String word : name.split(" ")) {
            if (!word.isEmpty() && !word.equalsIgnoreCase("the")) return "Tarmin-" + word;
        }
        return "Tarmin-Nameless";
    }
}
