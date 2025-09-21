package com.bpm.minotaur.gamedata;

/**
 * Item sprite data extracted from the original Advanced D&D: Treasure of Tarmin assembly code.
 * Based on the manual's item classifications and hex data from the assembly.
 * Each sprite is 8x8 pixels represented as ASCII art.
 * 'X' represents a colored pixel, ' ' represents transparency.
 */
public class ItemSpriteData {

    // WAR WEAPONS

    /**
     * Bow - Starting weapon, uses arrows
     * Colors: Tan(wood/leather), Orange(rusty iron), Blue(steel), Gray(silver), Yellow(gold), White(platinum)
     */
    public static final String[] BOW = {
        " X      ",
        " XX     ",
        " X XX   ",
        " X  XX  ",
        " X  XX  ",
        " X XX   ",
        " XX     ",
        " X      "
    };

    /**
     * Crossbow - Enhanced bow, uses arrows
     * Colors: Same as bow
     */
    public static final String[] CROSSBOW = {
        " XXXXX  ",
        " X   X  ",
        " XXXXX  ",
        "   XX   ",
        "   XX   ",
        "   XX   ",
        "  XXXX  ",
        " XXXXXX "
    };

    /**
     * Knife - Vanishes when used in attack
     * Colors: Same as bow
     */
    public static final String[] KNIFE = {
        "   XX   ",
        "   XX   ",
        "   XX   ",
        "   XX   ",
        "  XXXX  ",
        " XXXXXX ",
        "  XXXX  ",
        "   XX   "
    };

    /**
     * Axe - Vanishes when used in attack
     * Colors: Same as bow
     */
    public static final String[] AXE = {
        " XXXXXX ",
        "XX    XX",
        "X      X",
        "   XX   ",
        "   XX   ",
        "   XX   ",
        "   XX   ",
        "   XX   "
    };

    /**
     * Dart - Small throwing weapon, vanishes when used
     * Colors: Same as bow
     */
    public static final String[] DART = {
        "    XX  ",
        "   XXX  ",
        "  XXXX  ",
        " XXXXX  ",
        "  XXXX  ",
        "   XXX  ",
        "    XX  ",
        "     X  "
    };

    /**
     * Spear - Long weapon, vanishes when used
     * Colors: Same as bow
     */
    public static final String[] SPEAR = {
        "   XXX  ",
        "  XXXXX ",
        " XXXXXXX",
        "   XXX  ",
        "   XXX  ",
        "   XXX  ",
        "   XXX  ",
        "   XXX  "
    };

    // ARMOR

    /**
     * Small Shield - Hold in left hand during fight
     * Colors: Same as weapons
     */
    public static final String[] SMALL_SHIELD = {
        "  XXXX  ",
        " XXXXXX ",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "  XXXX  ",
        "   XX   "
    };

    /**
     * Large Shield - Hold in left hand during fight
     * Colors: Same as weapons
     */
    public static final String[] LARGE_SHIELD = {
        " XXXXXX ",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "  XXXX  "
    };

    /**
     * Gauntlets - Use to put on, best color is kept
     * Colors: Same as weapons
     */
    public static final String[] GAUNTLETS = {
        "XX    XX",
        "XXX  XXX",
        "XXXXXXXX",
        " XXXXXX ",
        " XXXXXX ",
        " XXXXXX ",
        " XX  XX ",
        " XX  XX "
    };

    /**
     * Hauberk - Chain mail armor, use to put on
     * Colors: Same as weapons
     */
    public static final String[] HAUBERK = {
        "X X X X ",
        " X X X X",
        "X X X X ",
        " X X X X",
        "X X X X ",
        " X X X X",
        "X X X X ",
        " X X X X"
    };

    /**
     * Helmet - Head protection, use to put on
     * Colors: Same as weapons
     */
    public static final String[] HELMET = {
        "  XXXX  ",
        " XXXXXX ",
        "XXXXXXXX",
        "XX XX XX",
        "XXXXXXXX",
        " XXXXXX ",
        "        ",
        "        "
    };

    /**
     * Breastplate - Chest armor, use to put on
     * Colors: Same as weapons
     */
    public static final String[] BREASTPLATE = {
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "  XXXX  ",
        "   XX   "
    };

    // SPIRITUAL WEAPONS

    /**
     * Scroll - Reusable, may break
     * Colors: Blue(regular), Gray(greater), White(fair), Pink(medium), Red(high), Purple(super)
     */
    public static final String[] SCROLL = {
        "XXXXXXXX",
        "X      X",
        "X XXXX X",
        "X XXXX X",
        "X XXXX X",
        "X XXXX X",
        "X      X",
        "XXXXXXXX"
    };

    /**
     * Book - Reusable, may break
     * Colors: Same as scroll
     */
    public static final String[] BOOK = {
        "XXXXXXXX",
        "X  XX  X",
        "X XX X X",
        "X XXXX X",
        "X XX X X",
        "X XX X X",
        "X  XX  X",
        "XXXXXXXX"
    };

    /**
     * Small Fireball - Vanishes when used in attack
     * Colors: Same as scroll
     */
    public static final String[] SMALL_FIREBALL = {
        "   XX   ",
        "  XXXX  ",
        " X XX X ",
        "XX XX XX",
        "XX XX XX",
        " X XX X ",
        "  XXXX  ",
        "   XX   "
    };

    /**
     * Large Fireball - Vanishes when used in attack
     * Colors: Same as scroll
     */
    public static final String[] LARGE_FIREBALL = {
        " XXXXXX ",
        "XX XX XX",
        "X  XX  X",
        "X XXXX X",
        "X XXXX X",
        "X  XX  X",
        "XX XX XX",
        " XXXXXX "
    };

    /**
     * Small Lightning Bolt - Vanishes when used in attack
     * Colors: Same as scroll
     */
    public static final String[] SMALL_LIGHTNING = {
        "  XX    ",
        " XX     ",
        "XX      ",
        " XXXXXX ",
        "  XXXXXX",
        "      XX",
        "     XX ",
        "    XX  "
    };

    /**
     * Large Lightning Bolt - Vanishes when used in attack
     * Colors: Same as scroll
     */
    public static final String[] LARGE_LIGHTNING = {
        " XXX    ",
        "XXX     ",
        "XX      ",
        "XXXXXXX ",
        " XXXXXXX",
        "      XX",
        "     XXX",
        "    XXX "
    };

    // RINGS

    /**
     * Small Ring - Use to put on, best color is kept
     * Colors: Same as spiritual weapons
     */
    public static final String[] SMALL_RING = {
        "        ",
        "        ",
        "  XXXX  ",
        " XX  XX ",
        " XX  XX ",
        "  XXXX  ",
        "        ",
        "        "
    };

    /**
     * Large Ring - Use to put on, best color is kept
     * Colors: Same as spiritual weapons
     */
    public static final String[] LARGE_RING = {
        "  XXXX  ",
        " XXXXXX ",
        "XX    XX",
        "XX    XX",
        "XX    XX",
        "XX    XX",
        " XXXXXX ",
        "  XXXX  "
    };

    // CONTAINERS

    /**
     * Regular Chest - Locked, use key to open
     * Colors: Tan(mild), Orange(good), Blue(best)
     */
    public static final String[] REGULAR_CHEST = {
        "XXXXXXXX",
        "X      X",
        "X      X",
        "X XX   X",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX"
    };

    /**
     * Small Bag - Open to grab contents
     * Colors: Same as regular chest
     */
    public static final String[] SMALL_BAG = {
        "   XX   ",
        "  XXXX  ",
        " XXXXXX ",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        "  XXXX  ",
        "   XX   "
    };

    /**
     * Box - Locked, use key to open
     * Colors: Same as regular chest
     */
    public static final String[] BOX = {
        "XXXXXXXX",
        "X      X",
        "X  XX  X",
        "X      X",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX"
    };

    /**
     * Medium Pack - Locked, use key to open
     * Colors: Same as regular chest
     */
    public static final String[] MEDIUM_PACK = {
        " XXXXXX ",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX ",
        " XXXXXX "
    };

    /**
     * Large Bag - Open to grab contents
     * Colors: Same as regular chest
     */
    public static final String[] LARGE_BAG = {
        "  XXXX  ",
        " XXXXXX ",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX "
    };

    /**
     * Super Money Belt - Open to grab contents
     * Colors: Same as regular chest
     */
    public static final String[] MONEY_BELT = {
        "XXXXXXXX",
        "X  XX  X",
        "XXXXXXXX",
        "X  XX  X",
        "XXXXXXXX",
        "X  XX  X",
        "XXXXXXXX",
        "        "
    };

    // TREASURES

    /**
     * Coins - Automatically scored when picked up
     * Values: Silver(10), Gold(30), Platinum(170)
     */
    public static final String[] COINS = {
        "        ",
        " XXXXXX ",
        "XX XX XX",
        "X  XX  X",
        "X  XX  X",
        "XX XX XX",
        " XXXXXX ",
        "        "
    };

    /**
     * Necklace - Jewelry treasure
     * Values: Silver(20), Gold(70), Platinum(200)
     */
    public static final String[] NECKLACE = {
        "X      X",
        " X    X ",
        "  XXXX  ",
        " XXXXXX ",
        "XXXXXXXX",
        " XXXXXX ",
        "  XXXX  ",
        "   XX   "
    };

    /**
     * Ingot - Metal bar treasure
     * Values: Silver(50), Gold(350), Platinum(450)
     */
    public static final String[] INGOT = {
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX"
    };

    /**
     * Lamp - Oil lamp treasure
     * Values: Silver(100), Gold(150), Platinum(220)
     */
    public static final String[] LAMP = {
        "  XXXX  ",
        " XXXXXX ",
        " XX  XX ",
        " XXXXXX ",
        " XXXXXX ",
        "  XXXX  ",
        "   XX   ",
        "XXXXXXXX"
    };

    /**
     * Chalice - Cup treasure
     * Values: Silver(250), Gold(400), Platinum(120)
     */
    public static final String[] CHALICE = {
        " XXXXXX ",
        " XX  XX ",
        " XX  XX ",
        " XXXXXX ",
        "  XXXX  ",
        "  XXXX  ",
        " XXXXXX ",
        "XXXXXXXX"
    };

    /**
     * Crown - Royal treasure
     * Values: Silver(300), Gold(500), Platinum(600)
     */
    public static final String[] CROWN = {
        "X X X X ",
        "XXXXXXXX",
        " XXXXXX ",
        " XXXXXX ",
        " XXXXXX ",
        "        ",
        "        ",
        "        "
    };

    // USEFUL ITEMS

    /**
     * Key - Unlocks containers
     * Colors: Tan(unlock tan), Orange(unlock tan/orange), Blue(unlock any)
     */
    public static final String[] KEY = {
        "  XXXX  ",
        "  X  X  ",
        "  XXXX  ",
        "  XX    ",
        "  XX    ",
        "  XXXX  ",
        "  XXXX  ",
        "        "
    };

    /**
     * War Book - Increases war weapon experience, vanishes on use
     * Colors: Blue(max 99), Pink(max 149), Purple(max 199)
     */
    public static final String[] WAR_BOOK = {
        "XXXXXXXX",
        "X XXXX X",
        "X X  X X",
        "X XXXX X",
        "X X  X X",
        "X X  X X",
        "X XXXX X",
        "XXXXXXXX"
    };

    /**
     * Spiritual Book - Increases spiritual weapon experience, vanishes on use
     * Colors: Blue(max 49), Pink(max 74), Purple(max 99)
     */
    public static final String[] SPIRITUAL_BOOK = {
        "XXXXXXXX",
        "X  XX  X",
        "X XXXX X",
        "X XXXX X",
        "X XXXX X",
        "X XXXX X",
        "X  XX  X",
        "XXXXXXXX"
    };

    /**
     * Small Potion - Various effects, vanishes on use
     * Colors: Blue(refresh strength), Pink(find better things), Purple(invisibility)
     */
    public static final String[] SMALL_POTION = {
        "  XXXX  ",
        " X XX X ",
        " XXXXXX ",
        " XXXXXX ",
        " XXXXXX ",
        " XXXXXX ",
        " XXXXXX ",
        "  XXXX  "
    };

    /**
     * Large Potion - Various effects, vanishes on use
     * Colors: Blue(+10 war), Pink(+10 spiritual), Purple(switch traits)
     */
    public static final String[] LARGE_POTION = {
        " XXXXXX ",
        "X XXXX X",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX "
    };

    /**
     * Special Book - Never vanishes
     * Colors: Blue(teleport), Pink(vision), Purple(midas touch)
     */
    public static final String[] SPECIAL_BOOK = {
        "XXXXXXXX",
        "X  XX  X",
        "X XXXX X",
        "X  XX  X",
        "X XXXX X",
        "X XXXX X",
        "X  XX  X",
        "XXXXXXXX"
    };

    // CONSUMABLES

    /**
     * Quiver - Contains 6-9 arrows, disappears when picked up
     */
    public static final String[] QUIVER = {
        "XXXXXXXX",
        "X XXX  X",
        "X XXX  X",
        "X XXX  X",
        "X XXX  X",
        "X XXX  X",
        "X      X",
        "XXXXXXXX"
    };

    /**
     * Sack of Flour - Contains 6-9 food units, disappears when picked up
     */
    public static final String[] FLOUR_SACK = {
        "   XX   ",
        "  XXXX  ",
        " XXXXXX ",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        "XXXXXXXX",
        " XXXXXX "
    };

    /**
     * Method to get sprite by item type name
     */
    public static String[] getSpriteByType(String itemType) {
        switch (itemType.toUpperCase()) {
            // Weapons
            case "BOW": return BOW;
            case "CROSSBOW": return CROSSBOW;
            case "KNIFE": return KNIFE;
            case "AXE": return AXE;
            case "DART": return DART;
            case "SPEAR": return SPEAR;

            // Armor
            case "SMALL_SHIELD": return SMALL_SHIELD;
            case "LARGE_SHIELD": return LARGE_SHIELD;
            case "GAUNTLETS": return GAUNTLETS;
            case "HAUBERK": return HAUBERK;
            case "HELMET": return HELMET;
            case "BREASTPLATE": return BREASTPLATE;

            // Spiritual weapons
            case "SCROLL": return SCROLL;
            case "BOOK": return BOOK;
            case "SMALL_FIREBALL": return SMALL_FIREBALL;
            case "LARGE_FIREBALL": return LARGE_FIREBALL;
            case "SMALL_LIGHTNING": return SMALL_LIGHTNING;
            case "LARGE_LIGHTNING": return LARGE_LIGHTNING;

            // Rings
            case "SMALL_RING": return SMALL_RING;
            case "LARGE_RING": return LARGE_RING;

            // Containers
            case "REGULAR_CHEST": return REGULAR_CHEST;
            case "SMALL_BAG": return SMALL_BAG;
            case "BOX": return BOX;
            case "MEDIUM_PACK": return MEDIUM_PACK;
            case "LARGE_BAG": return LARGE_BAG;
            case "MONEY_BELT": return MONEY_BELT;

            // Treasures
            case "COINS": return COINS;
            case "NECKLACE": return NECKLACE;
            case "INGOT": return INGOT;
            case "LAMP": return LAMP;
            case "CHALICE": return CHALICE;
            case "CROWN": return CROWN;

            // Useful items
            case "KEY": return KEY;
            case "WAR_BOOK": return WAR_BOOK;
            case "SPIRITUAL_BOOK": return SPIRITUAL_BOOK;
            case "SMALL_POTION": return SMALL_POTION;
            case "LARGE_POTION": return LARGE_POTION;
            case "SPECIAL_BOOK": return SPECIAL_BOOK;

            // Consumables
            case "QUIVER": return QUIVER;
            case "FLOUR_SACK": return FLOUR_SACK;

            default: return COINS; // Default fallback
        }
    }
}
