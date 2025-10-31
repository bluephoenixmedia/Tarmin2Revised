package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;

/**
 * A central class to define and store color palettes for the
 * Retro rendering mode (when ShapeRenderer is used).
 * Includes the standard theme and a spectrum of themes based
 * on the Intellivision color palette for Advanced Mode.
 */
public class RetroTheme {

    // A class to hold all colors for a single theme
    public static class Theme {
        public final Color floor;
        public final Color ceiling;
        public final Color wall;
        public final Color wallDark;
        public final Color door;
        public final Color doorDark;

        public Theme(Color floor, Color ceiling, Color wall, Color wallDark, Color door, Color doorDark) {
            this.floor = floor;
            this.ceiling = ceiling;
            this.wall = wall;
            this.wallDark = wallDark;
            this.door = door;
            this.doorDark = doorDark;
        }
    }

    // --- Base Intellivision Color Palette ---
    private static final Color INTV_WHITE = new Color(253/255f, 253/255f, 253/255f, 1.0f);
    private static final Color INTV_YELLOW = new Color(249/255f, 234/255f, 79/255f, 1.0f);
    private static final Color INTV_GREEN = new Color(0/255f, 167/255f, 88/255f, 1.0f);
    private static final Color INTV_DARK_GREEN = new Color(56/255f, 107/255f, 64/255f, 1.0f);
    private static final Color INTV_TAN = new Color(200/255f, 207/255f, 172/255f, 1.0f);
    private static final Color INTV_RED = new Color(254/255f, 60/255f, 17/255f, 1.0f);
    private static final Color INTV_BLUE = new Color(0/255f, 44/255f, 254/255f, 1.0f);
    private static final Color INTV_OLIVE = new Color(85/255f, 110/255f, 0/255f, 1.0f);
    private static final Color INTV_PURPLE = new Color(124/255f, 60/255f, 254/255f, 1.0f);
    private static final Color INTV_CYAN = new Color(0/255f, 167/255f, 174/255f, 1.0f);
    private static final Color INTV_GREY = new Color(128/255f, 128/255f, 128/255f, 1.0f);
    private static final Color INTV_ORANGE = new Color(254/255f, 120/255f, 17/255f, 1.0f);


    // --- Helper Colors (Darker/Lighter versions) ---
    // (Floor) < (Dark Wall) < (Bright Wall) / (Ceiling)
    private static final Color INTV_RED_DARK = new Color(177/255f, 42/255f, 11/255f, 1.0f);
    private static final Color INTV_RED_LIGHT = new Color(254/255f, 118/255f, 90/255f, 1.0f);

    private static final Color INTV_BLUE_DARK = new Color(0/255f, 30/255f, 177/255f, 1.0f);
    private static final Color INTV_BLUE_LIGHT = new Color(85/255f, 113/255f, 254/255f, 1.0f);

    private static final Color INTV_GREEN_DARK = new Color(0/255f, 116/255f, 61/255f, 1.0f);
    private static final Color INTV_GREEN_LIGHT = new Color(85/255f, 190/255f, 138/255f, 1.0f);

    private static final Color INTV_YELLOW_DARK = new Color(174/255f, 163/255f, 55/255f, 1.0f);
    private static final Color INTV_YELLOW_LIGHT = new Color(251/255f, 242/255f, 136/255f, 1.0f);

    private static final Color INTV_TAN_DARK = new Color(140/255f, 144/255f, 120/255f, 1.0f);
    private static final Color INTV_TAN_LIGHT = new Color(216/255f, 222/255f, 194/255f, 1.0f);

    private static final Color INTV_OLIVE_DARK = new Color(59/255f, 77/255f, 0/255f, 1.0f);
    private static final Color INTV_OLIVE_LIGHT = new Color(138/255f, 156/255f, 85/255f, 1.0f);

    private static final Color INTV_PURPLE_DARK = new Color(86/255f, 42/255f, 177/255f, 1.0f);
    private static final Color INTV_PURPLE_LIGHT = new Color(160/255f, 118/255f, 254/255f, 1.0f);

    private static final Color INTV_CYAN_DARK = new Color(0/255f, 116/255f, 121/255f, 1.0f);
    private static final Color INTV_CYAN_LIGHT = new Color(85/255f, 190/255f, 197/255f, 1.0f);

    private static final Color INTV_GREY_DARK = new Color(89/255f, 89/255f, 89/255f, 1.0f);
    private static final Color INTV_GREY_LIGHT = new Color(166/255f, 166/255f, 166/255f, 1.0f);

    private static final Color INTV_WHITE_DARK = new Color(177/255f, 177/255f, 177/255f, 1.0f);

    private static final Color INTV_ORANGE_DARK = new Color(177/255f, 84/255f, 11/255f, 1.0f);
    private static final Color INTV_ORANGE_LIGHT = new Color(254/255f, 160/255f, 90/255f, 1.0f);


    // --- STANDARD THEME ---
    // Unchanged. Follows the Floor (dark), WallDark (medium), Wall (light) logic.
    public static final Theme STANDARD_THEME = new Theme(
        new Color(0.2f, 0.4f, 0.2f, 1),    // Floor
        new Color(0.3f, 0.5f, 0.3f, 1),    // Ceiling
        new Color(0.5f, 0.8f, 0.5f, 1),    // Wall (Bright)
        new Color(0.4f, 0.7f, 0.4f, 1),    // Wall Dark (Medium)
        new Color(0.1f, 0.2f, 0.7f, 1),    // Door
        new Color(0.1f, 0.15f, 0.6f, 1)     // Door Dark
    );


    // --- ADVANCED MODE THEMES ---
    // All themes below are now adjusted for better contrast.
    // Logic: Floor = DARK, WallDark = MEDIUM (main color), Wall = LIGHT

    public static final Theme ADVANCED_COLOR_THEME_RED = new Theme(
        INTV_RED_DARK,     // Floor
        INTV_RED_LIGHT,    // Ceiling
        INTV_RED_LIGHT,    // Wall (Bright)
        INTV_RED,          // Wall Dark (Medium)
        INTV_YELLOW,       // Door
        INTV_YELLOW_DARK   // Door Dark
    );

    public static final Theme ADVANCED_COLOR_THEME_BLUE = new Theme(
        INTV_BLUE_DARK,    // Floor
        INTV_BLUE_LIGHT,   // Ceiling
        INTV_BLUE_LIGHT,   // Wall (Bright)
        INTV_BLUE,         // Wall Dark (Medium)
        INTV_YELLOW,       // Door (FIX: Was INTV_WHITE, now high-contrast yellow)
        INTV_YELLOW_DARK   // Door Dark (FIX: Was INTV_WHITE_DARK)
    );

    public static final Theme ADVANCED_COLOR_THEME_GREEN = new Theme(
        INTV_DARK_GREEN,   // Floor
        INTV_GREEN_LIGHT,  // Ceiling
        INTV_GREEN_LIGHT,  // Wall (Bright)
        INTV_GREEN,        // Wall Dark (Medium)
        INTV_TAN,          // Door
        INTV_TAN_DARK      // Door Dark
    );

    public static final Theme ADVANCED_COLOR_THEME_YELLOW = new Theme(
        INTV_YELLOW_DARK,  // Floor
        INTV_YELLOW_LIGHT, // Ceiling
        INTV_YELLOW_LIGHT, // Wall (Bright)
        INTV_YELLOW,       // Wall Dark (Medium)
        INTV_BLUE,         // Door
        INTV_BLUE_DARK     // Door Dark
    );

    public static final Theme ADVANCED_COLOR_THEME_INDIGO = new Theme(
        INTV_PURPLE_DARK,  // Floor
        INTV_PURPLE_LIGHT, // Ceiling
        INTV_PURPLE_LIGHT, // Wall (Bright)
        INTV_PURPLE,       // Wall Dark (Medium)
        INTV_GREEN,        // Door
        INTV_GREEN_DARK    // Door Dark
    );

    public static final Theme ADVANCED_COLOR_THEME_CYAN = new Theme(
        INTV_CYAN_DARK,    // Floor
        INTV_CYAN_LIGHT,   // Ceiling
        INTV_CYAN_LIGHT,   // Wall (Bright)
        INTV_CYAN,         // Wall Dark (Medium)
        INTV_RED,          // Door
        INTV_RED_DARK      // Door Dark
    );

    public static final Theme ADVANCED_COLOR_THEME_TAN = new Theme(
        INTV_TAN_DARK,     // Floor
        INTV_TAN_LIGHT,    // Ceiling
        INTV_TAN_LIGHT,    // Wall (Bright)
        INTV_TAN,          // Wall Dark (Medium)
        INTV_DARK_GREEN,   // Door
        INTV_OLIVE_DARK    // Door Dark (using olive as the dark tan)
    );

    public static final Theme ADVANCED_COLOR_THEME_OLIVE = new Theme(
        INTV_OLIVE_DARK,   // Floor
        INTV_OLIVE_LIGHT,  // Ceiling
        INTV_OLIVE_LIGHT,  // Wall (Bright)
        INTV_OLIVE,        // Wall Dark (Medium)
        INTV_TAN,          // Door
        INTV_TAN_DARK      // Door Dark
    );

    public static final Theme ADVANCED_COLOR_THEME_ORANGE = new Theme(
        INTV_ORANGE_DARK,  // Floor
        INTV_ORANGE_LIGHT, // Ceiling
        INTV_ORANGE_LIGHT, // Wall (Bright)
        INTV_ORANGE,       // Wall Dark (Medium)
        INTV_BLUE,         // Door
        INTV_BLUE_DARK     // Door Dark
    );

    public static final Theme ADVANCED_COLOR_THEME_GREY = new Theme(
        INTV_GREY_DARK,    // Floor
        INTV_GREY_LIGHT,   // Ceiling
        INTV_GREY_LIGHT,   // Wall (Bright)
        INTV_GREY,         // Wall Dark (Medium)
        INTV_WHITE,        // Door
        INTV_WHITE_DARK    // Door Dark
    );
}
