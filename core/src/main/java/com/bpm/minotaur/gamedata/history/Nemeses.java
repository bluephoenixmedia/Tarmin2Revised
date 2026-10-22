package com.bpm.minotaur.gamedata.history;

import java.util.Random;

/**
 * Raises and grows the Nemeses from the death deeds (#283). Everything comes from the deed and
 * the history's own seed, so the replay of a save raises the same Nemesis with the same name.
 * ASCII only (UiGlyphs).
 */
final class Nemeses {

    private static final String[] FIRST = {
            "Gor", "Skar", "Mal", "Vosh", "Grub", "Thak", "Ulm", "Krel", "Drog", "Hesk", "Mur", "Zag"
    };
    private static final String[] LAST = {
            "vak", "rith", "gul", "nash", "bek", "zor", "mok", "tch", "rag", "dun", "lisk", "oth"
    };
    private static final String[] TITLES = {
            "the Gnawer", "the Patient", "the Red-Handed", "the Grinning", "Bonecrack", "the Unhurried",
            "Gutripper", "the Lean", "Many-Teeth", "the Quiet", "Skullsplitter", "the Waiting"
    };

    private static final String[] ORDINALS = {"", "", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh"};

    private Nemeses() {
    }

    /** A life was taken: its killer becomes a Nemesis, or a Nemesis takes one more life. */
    static void fell(HistoryWorld world, PlayerDeed deed) {
        Nemesis n = world.nemesis(deed.nemesis);
        if (n == null && deed.figure >= 0) {
            for (Nemesis known : world.nemeses) {
                if (known.figureId == deed.figure) n = known;
            }
        }
        if (n == null) {
            if (deed.monsterType == null) return; // thirst, a fall, the maze itself: no one to remember
            Figure figure = world.figure(deed.figure);
            String name = figure != null ? figure.name : forge(world);
            n = new Nemesis(world.nemeses.size(), name, figure != null ? figure.id : -1, deed.monsterType,
                    deed.level, deed.chunkX, deed.chunkY);
            world.nemeses.add(n);
        }
        if (!n.isAlive()) return;
        n.victims.add(deed.victim != null ? deed.victim : "a nameless prisoner");
    }

    /** "Gorvak the Gnawer": seeded by the history and how many have fallen, so a replay agrees. */
    private static String forge(HistoryWorld world) {
        Random rng = new Random(world.seed * 1_000_003L + world.seekersFallen);
        String name = null;
        for (int attempt = 0; attempt < 16; attempt++) {
            name = FIRST[rng.nextInt(FIRST.length)] + LAST[rng.nextInt(LAST.length)] + " "
                    + TITLES[rng.nextInt(TITLES.length)];
            if (world.usedNames.add(name)) return name;
        }
        // Every draw taken: a namesake of an older one ("Gorvak the Gnawer the Second").
        for (int n = 2; ; n++) {
            String heir = name + " the " + (n < ORDINALS.length ? ORDINALS[n] : "Later " + n);
            if (world.usedNames.add(heir)) return heir;
        }
    }
}
