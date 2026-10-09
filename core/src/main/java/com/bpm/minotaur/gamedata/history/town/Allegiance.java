package com.bpm.minotaur.gamedata.history.town;

import java.util.Random;

/**
 * The mortal powers the underground towns answer to (plan D40). None of them is of the Maze;
 * they feud among themselves, and a town's standing spills over to its sisters.
 */
public enum Allegiance {
    REFUGEE_COUNCIL("the Refugee Council", "DWARF",
            new String[]{"Brenna", "Oswin", "Maud", "Tobiah", "Edda", "Corwin", "Hesper", "Aldous", "Wynn", "Rosalind"},
            new String[]{"Thatch", "Ashdown", "Miller", "Holloway", "Fenn", "Cobb", "Marsh", "Reed", "Hartley", "Stone"},
            new String[]{"DWARF", "SAGE", "JESTER"}),
    GOBLIN_CLANS("the Goblin Clans", "HOBGOBLIN",
            new String[]{"Skrag", "Nibbet", "Grulka", "Pockle", "Zit", "Morg", "Snitter", "Bukka", "Rattle", "Gritch"},
            new String[]{"Ratbite", "Mudfoot", "Longnose", "Grubber", "Sootclaw", "Twiceburnt", "Dripnose", "Bonepicker", "Gristle", "Hookjaw"},
            new String[]{"GOBLIN", "HOBGOBLIN", "KOBOLD"}),
    OUTCAST_COVENANT("the Outcast Covenant", "CLOAKED_SKELETON",
            new String[]{"Sister Vey", "Brother Lom", "Ashen Kel", "Mother Ruth", "the Hooded Tam", "Orrin", "Ysolde", "Father Crane", "Nell", "Silas"},
            new String[]{"of the Ash", "of the Lamp", "of the Hollow", "of the Last Door", "of the Grey", "of the Well", "of the Thorn", "of the Low Road", "of the Ninth Bell", "of the Veil"},
            new String[]{"SAGE", "JESTER", "TROGLODYTE"});

    public final String displayName;
    /** The monsters.json type a town of this allegiance posts as its guard. */
    public final String guard;
    private final String[] names;
    /** The families a settlement's seats stay in (ADR 0005). */
    private final String[] families;
    /** monsters.json types whose sprites stand in for this allegiance's folk. */
    private final String[] sprites;

    Allegiance(String displayName, String guard, String[] names, String[] families, String[] sprites) {
        this.displayName = displayName;
        this.guard = guard;
        this.names = names;
        this.families = families;
        this.sprites = sprites;
    }

    public String givenName(Random rng) {
        return names[rng.nextInt(names.length)];
    }

    public String familyName(Random rng) {
        return families[rng.nextInt(families.length)];
    }

    public String folkSprite(Random rng) {
        return sprites[rng.nextInt(sprites.length)];
    }

    /** The allegiances this one is feuding with: standing won with one costs standing with these. */
    public boolean feudsWith(Allegiance other) {
        return other != this && !(this == REFUGEE_COUNCIL && other == OUTCAST_COVENANT);
    }
}
