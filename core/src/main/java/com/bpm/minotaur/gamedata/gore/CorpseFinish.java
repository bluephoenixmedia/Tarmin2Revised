package com.bpm.minotaur.gamedata.gore;

/**
 * How a corpse looks for the way it died. Saved with the chunk by name, so a
 * charred body is still charred when the player comes back.
 */
public enum CorpseFinish {
    NONE,
    /** Burnt black. */
    CHARRED,
    /** Shattered ice, pale blue. */
    FROZEN,
    /** Crumbled to a dark grey heap. */
    ASH,
    /** Sunk into a low green slick. */
    MELTED,
    /** The body without its head. */
    HEADLESS;

    public static CorpseFinish parse(String name) {
        if (name == null) return NONE;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
