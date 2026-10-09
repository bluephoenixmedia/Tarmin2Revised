package com.bpm.minotaur.gamedata.history.town;

import java.util.Random;

/**
 * The mortal powers the underground towns answer to (plan D40). None of them is of the Maze;
 * they feud among themselves, and a town's standing spills over to its sisters.
 */
public enum Allegiance {
    REFUGEE_COUNCIL("the Refugee Council",
            new String[]{"Brenna", "Oswin", "Maud", "Tobiah", "Edda", "Corwin", "Hesper", "Aldous", "Wynn", "Rosalind"},
            new String[]{"DWARF", "SAGE", "JESTER"}),
    GOBLIN_CLANS("the Goblin Clans",
            new String[]{"Skrag", "Nibbet", "Grulka", "Pockle", "Zit", "Morg", "Snitter", "Bukka", "Rattle", "Gritch"},
            new String[]{"GOBLIN", "HOBGOBLIN", "KOBOLD"}),
    OUTCAST_COVENANT("the Outcast Covenant",
            new String[]{"Sister Vey", "Brother Lom", "Ashen Kel", "Mother Ruth", "the Hooded Tam", "Orrin", "Ysolde", "Father Crane", "Nell", "Silas"},
            new String[]{"SAGE", "JESTER", "TROGLODYTE"});

    public final String displayName;
    private final String[] names;
    /** monsters.json types whose sprites stand in for this allegiance's folk. */
    private final String[] sprites;

    Allegiance(String displayName, String[] names, String[] sprites) {
        this.displayName = displayName;
        this.names = names;
        this.sprites = sprites;
    }

    String name(Random rng) {
        return names[rng.nextInt(names.length)];
    }

    String sprite(Random rng) {
        return sprites[rng.nextInt(sprites.length)];
    }

    /** The allegiances this one is feuding with: standing won with one costs standing with these. */
    public boolean feudsWith(Allegiance other) {
        return other != this && !(this == REFUGEE_COUNCIL && other == OUTCAST_COVENANT);
    }
}
