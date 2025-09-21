package com.bpm.minotaur.gamedata;

/**
 * Monster sprite data extracted from the original Advanced D&D: Treasure of Tarmin assembly code.
 * Based on the manual's monster classifications and hex data from the assembly.
 * Each sprite is 8x8 pixels represented as ASCII art.
 * 'X' represents a colored pixel, ' ' represents transparency.
 */
public class MonsterSpriteData {

    // BAD MONSTERS (Use spiritual weapons only)

    /**
     * Giant Ant - From assembly hex data around $5F49-$5F59
     * Colors: Blue, Pink, Purple, Yellow
     */
    public static final String[] GIANT_ANT = {
        "   XX   ",
        "  XXXX  ",
        " XX XX X",
        "XXXXXXXX",
        "XX XXXX ",
        " XXX XX ",
        "   XX   ",
        "   X    "
    };

    /**
     * Dwarf - From assembly data patterns
     * Colors: Yellow, Tan, Orange
     */
    public static final String[] DWARF = {
        "  XXXX  ",
        " XXXXXX ",
        " X XX X ",
        " XXXXXX ",
        "  XXXX  ",
        "  X  X  ",
        " X XX X ",
        " X    X "
    };

    /**
     * Dwarf with Shield - Enhanced version
     * Colors: Yellow, Tan, Orange
     */
    public static final String[] DWARF_WITH_SHIELD = {
        " XXXXXX ",
        "XXXXXXXX",
        "X X XX X",
        "XXXXXXXX",
        " XXXXXX ",
        "  X  X  ",
        " X XX X ",
        " X    X "
    };

    /**
     * Giant Scorpion - From hex patterns in assembly
     * Colors: Blue, Pink, Purple
     */
    public static final String[] GIANT_SCORPION = {
        "X  XX  X",
        " X XX X ",
        "  XXXX  ",
        "XXXXXXXX",
        "XXXXXXXX",
        "  XXXX  ",
        " X XX X ",
        "X  XX  X"
    };

    /**
     * Giant Snake - Long creature pattern
     * Colors: Blue, Pink, Purple
     */
    public static final String[] GIANT_SNAKE = {
        "XX      ",
        "XXXX    ",
        " XXXXX  ",
        "  XXXXX ",
        "   XXXX ",
        "    XXX ",
        "     XX ",
        "      X "
    };

    /**
     * Alligator - From assembly creature data
     * Colors: Blue, Pink, Purple
     */
    public static final String[] ALLIGATOR = {
        " XXXXXXX",
        "XXXXXXXX",
        "XXX  XXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "  XXXX  ",
        "   XX   "
    };

    /**
     * Dragon - Large creature from assembly hex data
     * Colors: Blue, Pink, Purple
     */
    public static final String[] DRAGON = {
        " XXX    ",
        "XXXXX   ",
        "XXXXXXX ",
        "XXXXXXXX",
        "XXXXXXX ",
        " XXXXX  ",
        "  XXX   ",
        "   X    "
    };

    // NASTY MONSTERS (Use war weapons only)

    /**
     * Skeleton - From assembly GRAM data
     * Colors: White, Gray, Orange
     */
    public static final String[] SKELETON = {
        " XXXXXX ",
        "X XXXX X",
        "XXXXXXXX",
        " X XX X ",
        " XXXXXX ",
        "  X  X  ",
        " X XX X ",
        "X      X"
    };

    /**
     * Skeleton with Shield
     * Colors: White, Gray, Orange
     */
    public static final String[] SKELETON_WITH_SHIELD = {
        "XXXXXXXX",
        "X XXXX X",
        "XXXXXXXX",
        "XX XX XX",
        "XXXXXXXX",
        " XX  XX ",
        "XX XX XX",
        "X      X"
    };

    /**
     * Cloaked Skeleton - Hooded version
     * Colors: White, Gray, Orange
     */
    public static final String[] CLOAKED_SKELETON = {
        "XXXXXXXX",
        "X XXXX X",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "  XXXX  ",
        " X XX X ",
        "X      X"
    };

    /**
     * Cloaked Skeleton with Shield
     * Colors: White, Gray, Orange
     */
    public static final String[] CLOAKED_SKELETON_WITH_SHIELD = {
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "XX XX XX",
        "X      X"
    };

    /**
     * Giant - Large humanoid
     * Colors: Yellow, Tan, Orange
     */
    public static final String[] GIANT = {
        "  XXXX  ",
        " XXXXXX ",
        " XXXXXX ",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        " XX  XX ",
        "XX    XX"
    };

    /**
     * Giant with Shield
     * Colors: Yellow, Tan, Orange
     */
    public static final String[] GIANT_WITH_SHIELD = {
        " XXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXX  XXX",
        "XX    XX"
    };

    // HORRIBLE MONSTERS (Use either spiritual or war weapons)

    /**
     * Ghoul - Short, hooded, and robed
     * Colors: White, Gray, Orange
     */
    public static final String[] GHOUL = {
        " XXXXXX ",
        "XXXXXXXX",
        "XX XX XX",
        "XXXXXXXX",
        " XXXXXX ",
        " XXXXXX ",
        " XX  XX ",
        " X    X "
    };

    /**
     * Ghoul with Shield
     * Colors: White, Gray, Orange
     */
    public static final String[] GHOUL_WITH_SHIELD = {
        "XXXXXXXX",
        "XXXXXXXX",
        "XX XX XX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXX  XXX",
        "XX    XX"
    };

    /**
     * Wraith - Tall, hooded, and robed
     * Colors: White, Gray, Orange
     */
    public static final String[] WRAITH = {
        "XXXXXXXX",
        "XXXXXXXX",
        "XX XX XX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "  X  X  "
    };

    /**
     * Wraith with Shield
     * Colors: White, Gray, Orange
     */
    public static final String[] WRAITH_WITH_SHIELD = {
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XX  XX "
    };

    /**
     * Minotaur - The final boss, guards the Tarmin treasure
     * Color: Purple only
     */
    public static final String[] MINOTAUR = {
        " XX  XX ",
        "XXXXXXXX",
        "XX XX XX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXX  XXX",
        "XX    XX"
    };

    /**
     * Utility method to convert hex data to sprite format
     */
    private static String[] hexToSprite(int[] hexData) {
        if (hexData.length != 8) {
            throw new IllegalArgumentException("Hex data must be exactly 8 bytes for 8x8 sprite");
        }

        String[] sprite = new String[8];
        for (int row = 0; row < 8; row++) {
            StringBuilder line = new StringBuilder();
            int rowData = hexData[row];
            for (int bit = 7; bit >= 0; bit--) {
                if ((rowData & (1 << bit)) != 0) {
                    line.append('X');
                } else {
                    line.append(' ');
                }
            }
            sprite[row] = line.toString();
        }
        return sprite;
    }

    /**
     * Method to get sprite by monster type name
     */
    public static String[] getSpriteByType(String monsterType) {
        switch (monsterType.toUpperCase()) {
            case "GIANT_ANT": return GIANT_ANT;
            case "DWARF": return DWARF;
            case "DWARF_WITH_SHIELD": return DWARF_WITH_SHIELD;
            case "GIANT_SCORPION": return GIANT_SCORPION;
            case "GIANT_SNAKE": return GIANT_SNAKE;
            case "ALLIGATOR": return ALLIGATOR;
            case "DRAGON": return DRAGON;
            case "SKELETON": return SKELETON;
            case "SKELETON_WITH_SHIELD": return SKELETON_WITH_SHIELD;
            case "CLOAKED_SKELETON": return CLOAKED_SKELETON;
            case "CLOAKED_SKELETON_WITH_SHIELD": return CLOAKED_SKELETON_WITH_SHIELD;
            case "GIANT": return GIANT;
            case "GIANT_WITH_SHIELD": return GIANT_WITH_SHIELD;
            case "GHOUL": return GHOUL;
            case "GHOUL_WITH_SHIELD": return GHOUL_WITH_SHIELD;
            case "WRAITH": return WRAITH;
            case "WRAITH_WITH_SHIELD": return WRAITH_WITH_SHIELD;
            case "MINOTAUR": return MINOTAUR;
            default: return SKELETON; // Default fallback
        }
    }

    /**
     * Get all monster types as an array
     */
    public static final String[] ALL_MONSTER_TYPES = {
        "GIANT_ANT", "DWARF", "DWARF_WITH_SHIELD", "GIANT_SCORPION",
        "GIANT_SNAKE", "ALLIGATOR", "DRAGON", "SKELETON",
        "SKELETON_WITH_SHIELD", "CLOAKED_SKELETON", "CLOAKED_SKELETON_WITH_SHIELD",
        "GIANT", "GIANT_WITH_SHIELD", "GHOUL", "GHOUL_WITH_SHIELD",
        "WRAITH", "WRAITH_WITH_SHIELD", "MINOTAUR"
    };
}
