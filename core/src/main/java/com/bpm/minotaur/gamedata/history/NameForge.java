package com.bpm.minotaur.gamedata.history;

import java.util.List;
import java.util.Random;

/** Names for figures, houses, places and sigils, flavoured by doctrine. ASCII only (UiGlyphs). */
public final class NameForge {

    private static final String[] PLACE_FIRST = {
            "Ashen", "Weeping", "Hook", "Salt", "Bleak", "Red", "Gallows", "Hollow", "Thorn", "Bitter",
            "Black", "Knell", "Widow", "Cinder", "Gutter", "Lantern", "Rook", "Sorrow", "Iron", "Last"
    };
    private static final String[] PLACE_SECOND = {
            "Ford", "Field", "Stair", "Gate", "Mire", "Crossing", "Hill", "Pass", "Barrow", "Causeway",
            "Bridge", "Hollow", "Reach", "Wells", "Steps", "Cut"
    };
    private static final String[] FIELDS = {
            "black", "red", "bone white", "tarnished gold", "ash grey", "bruise purple", "bile green"
    };
    private static final String[] GASH_FIRST = {
            "Weeping", "Red", "Screaming", "Silent", "Hungering", "Lidless", "Bleeding", "Open"
    };
    private static final String[] GASH_SECOND = {
            "Wound", "Seam", "Mouth", "Rent", "Scar", "Tear", "Lesion"
    };

    private NameForge() {
    }

    public static String given(Doctrine d, Random rng) {
        String head = pick(d.givenNames, rng);
        String tail = pick(d.givenEndings, rng);
        if (Character.toLowerCase(head.charAt(head.length() - 1)) == tail.charAt(0)) {
            tail = tail.substring(1);
        }
        return head + tail;
    }

    /** A house name not yet used in this world. */
    static String house(Doctrine d, HistoryWorld world, Random rng) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String name = "House " + pick(d.houseNames, rng);
            if (world.usedNames.add(name)) return name;
        }
        while (true) {
            String name = "House " + given(d, rng);
            if (world.usedNames.add(name)) return name;
        }
    }

    static String place(Random rng) {
        return "the " + PLACE_FIRST[rng.nextInt(PLACE_FIRST.length)] + " " + PLACE_SECOND[rng.nextInt(PLACE_SECOND.length)];
    }

    static String sigil(Doctrine d, Random rng) {
        return pick(d.charges, rng) + " on " + FIELDS[rng.nextInt(FIELDS.length)];
    }

    static String gash(HistoryWorld world, Random rng) {
        while (true) {
            String name = "the " + GASH_FIRST[rng.nextInt(GASH_FIRST.length)] + " " + GASH_SECOND[rng.nextInt(GASH_SECOND.length)];
            if (world.usedNames.add(name)) return name;
        }
    }

    static String pick(List<String> options, Random rng) {
        return options.get(rng.nextInt(options.size()));
    }
}
