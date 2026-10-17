package com.bpm.minotaur.ui;

import com.badlogic.gdx.graphics.Color;

/**
 * The single token set for every menu, panel and overlay -- SPEC section 3, "Ember".
 *
 * <p><b>Units.</b> The spec is written in {@code vu}, one virtual pixel on a 640x360 UI stage.
 * This game already has one virtual canvas and it is 1920x1080: every {@code Stage} in
 * {@code screens/} is a {@code FitViewport(1920, 1080)}, the HUD is a {@code FitViewport(1920,
 * 1080)}, and the design canvas the mockups came from is authored at 1920x1080. Introducing a
 * second virtual resolution would mean rescaling every layout constant in the codebase and
 * re-deriving the paper doll's frozen geometry, which SPEC section 0 forbids. So the spec's intent
 * -- one integer-scaled canvas, no fractional font scaling -- is met by keeping 1920x1080 and
 * fixing the conversion at <b>1 vu = 3 canvas units</b>. {@link #vu(int)} is that conversion, and
 * every size below is already converted.
 *
 * <p><b>Colors.</b> {@link com.bpm.minotaur.rendering.HudSkin} holds the in-world HUD's palette,
 * which was tuned against a lit dungeon and is deliberately left alone. These are the menu tokens.
 * Where the two agree the values are the same; where they differ, screen code uses these.
 *
 * <p>Every {@code Color} here is shared and mutable, as all libGDX colors are. Treat them as
 * read-only: pass them to styles, never {@code .set(...)} them.
 */
public final class UiTheme {

    private UiTheme() {
    }

    // --- Units ------------------------------------------------------------

    /** Canvas units per spec vu. The UI stage is 1920x1080; the spec's stage is 640x360. */
    public static final int VU = 3;

    /** Converts a spec measurement in vu to canvas units. */
    public static float vu(int specVu) {
        return specVu * (float) VU;
    }

    // --- Surfaces ---------------------------------------------------------

    /** Screen backdrop behind every panel. */
    public static final Color BG_VOID = Color.valueOf("0B0907");
    /** Panel body. */
    public static final Color BG_PANEL = Color.valueOf("15100B");
    /** Cards and list rows sitting on a panel. */
    public static final Color BG_RAISED = Color.valueOf("1F170F");
    /** Item slots, wells, bar troughs -- anything that reads as cut into the panel. */
    public static final Color BG_INSET = Color.valueOf("0E0B08");

    // --- Lines ------------------------------------------------------------

    /** Dividers inside a panel. */
    public static final Color LINE_DIM = Color.valueOf("3A2A18");
    /** Panel and card frames. */
    public static final Color LINE = Color.valueOf("6E4D25");
    /** Hover edge. */
    public static final Color LINE_BRIGHT = Color.valueOf("B8862F");
    /** Keyboard focus ring. Never used as a fill. */
    public static final Color FOCUS = Color.valueOf("FFD77A");

    // --- Text -------------------------------------------------------------

    /** Body copy. 14.9:1 on {@link #BG_PANEL}. */
    public static final Color TEXT = Color.valueOf("EFE3CC");
    /** Field labels and captions. 7.1:1 on {@link #BG_PANEL}. */
    public static final Color TEXT_DIM = Color.valueOf("B39C78");
    /** Disabled only. Below AA on purpose -- never use it for content a player must read. */
    public static final Color TEXT_OFF = Color.valueOf("6F5E46");

    // --- Accents ----------------------------------------------------------

    /** Primary action, screen titles. */
    public static final Color GOLD = Color.valueOf("E9B44C");
    /** Destructive action, and any resource at a dangerous level. */
    public static final Color DANGER = Color.valueOf("E0533D");
    /** Tarmin's Knell: his words in the seeker's head (plan K5). */
    public static final Color KNELL_RED = Color.valueOf("D8231C");
    /** Behind the knell's red, so it reads over any scene. */
    public static final Color KNELL_SHADOW = new Color(0.04f, 0.0f, 0.0f, 0.9f);
    /** Requirement met, value gained. */
    public static final Color SUCCESS = Color.valueOf("79C267");
    /** Hints and informational strips. */
    public static final Color INFO = Color.valueOf("6FA8DC");
    /** Dark text on a gold fill. */
    public static final Color ON_GOLD = Color.valueOf("2A1A08");

    /** Modal scrim: black at 78%. */
    public static final Color SCRIM = new Color(0f, 0f, 0f, 0.78f);

    // --- Resources (bar fills and their icons only, never text) -----------

    public static final Color RES_HP = Color.valueOf("C8372D");
    public static final Color RES_MP = Color.valueOf("3F74D6");
    public static final Color RES_FOOD = Color.valueOf("D08A3A");
    public static final Color RES_WATER = Color.valueOf("4A9BD8");
    public static final Color RES_TOX = Color.valueOf("8FBF3A");

    // --- Skills -----------------------------------------------------------
    // Chosen to survive deuteranopia and protanopia; each school also carries a glyph
    // (see UiGlyphs) because color alone is never the only signal.

    public static final Color SCHOOL_WARFARE = Color.valueOf("E0643D");
    public static final Color SCHOOL_FINESSE = Color.valueOf("4FB7A6");
    public static final Color SCHOOL_ARCANA = Color.valueOf("A583F0");

    // --- Parchment (the inventory book, and nothing else) -----------------

    public static final Color PAPER = Color.valueOf("E9D8B4");
    public static final Color PAPER_SHADE = Color.valueOf("CDB68A");
    public static final Color INK = Color.valueOf("3B2614");
    public static final Color INK_DIM = Color.valueOf("6B4E2E");
    public static final Color INK_ACCENT = Color.valueOf("8A2E1C");

    // --- Expedition map (docs/DEsign/Requirements_ Expedition Map.md, section 4) ---
    // Biomes are muted ink washes over dark vellum, not saturated fills: the map's icons and
    // roads carry the colour, and a wash that competed with them would bury the road home.

    public static final Color MAP_MAZE = Color.valueOf("3E3850");
    public static final Color MAP_FOREST = Color.valueOf("38563A");
    public static final Color MAP_DESERT = Color.valueOf("86703F");
    public static final Color MAP_LAKELANDS = Color.valueOf("335C6A");
    public static final Color MAP_TUNDRA = Color.valueOf("687E92");
    public static final Color MAP_BLIGHT = Color.valueOf("6E2C20");
    public static final Color MAP_MOUNTAINS = Color.valueOf("4A4540");
    public static final Color MAP_OCEAN = Color.valueOf("1F3249");
    /** A stratum chunk the player has entered. */
    public static final Color MAP_UNDERGROUND = Color.valueOf("4B3A28");
    /** An explored tile's floor, in the chunk view. */
    public static final Color MAP_FLOOR = Color.valueOf("2C2117");
    /** Wall lines in the chunk view: ink on vellum. */
    public static final Color MAP_WALL = Color.valueOf("C9B48C");
    /** The player: the one cool accent on the map, so they are always found first. */
    public static final Color MAP_PLAYER = INFO;
    /** How strongly an entered chunk's wash covers the vellum. */
    public static final float MAP_WASH_ALPHA = 0.85f;
    /** A glimpsed chunk: seen across the border, never entered. */
    public static final float MAP_GLIMPSED_ALPHA = 0.3f;
    /** The surface biome drawn beneath a stratum view, so the player knows what is overhead. */
    public static final float MAP_UNDERLAY_ALPHA = 0.18f;
    /** A suggestion or a rumour: there, but not yet certain. */
    public static final float MAP_GHOST_ALPHA = 0.5f;
    /** A war front's wash over the chunks it covers, in the attacking house's colour. */
    public static final float MAP_FRONT_ALPHA = 0.45f;
    /** How far a house's colour is lifted toward gold when it is used for text (Living War W27). */
    public static final float HERALDRY_TEXT_LIFT = 0.45f;
    /** A fresh battlefield's mark on the map (Living War W28). */
    public static final Color MAP_BATTLEFIELD = new Color(0.62f, 0.12f, 0.08f, 1f);
    /** Thickness of the border a front carries in the defending house's colour, and of the minimap's war rim. */
    public static final float FRONT_RIM = 3f;
    /** The HUD's war tally under the minimap (Living War W30): its gap below the map, and its bar's height. */
    public static final float WAR_TALLY_GAP = 4 * 3f;
    public static final float WAR_TALLY_H = 4 * 3f;

    // --- Spacing (SPEC section 3, in canvas units) ------------------------

    public static final float PAD_XS = 2 * VU;
    public static final float PAD_SM = 4 * VU;
    /** Padding inside a panel. */
    public static final float PAD_MD = 8 * VU;
    /** Screen safe area. Nothing a player must read sits outside this. */
    public static final float SAFE = 12 * VU;
    public static final float PAD_XL = 16 * VU;
    /** A list column beside a reading pane, such as the Annals' houses. */
    public static final float LIST_COLUMN_W = 140 * VU;
    public static final float PAD_XXL = 24 * VU;

    // --- Sizes (SPEC section 3, in canvas units) --------------------------

    /** Height of the red alert symbol shown when the player needs to look at something. */
    public static final float ALERT_ICON = 50 * VU;
    /** How wide Tarmin's Knell may run before it wraps. */
    public static final float KNELL_TEXT_W = 480 * VU;
    /** The strength of the knell's red bleed at the very edge of the screen. */
    public static final float KNELL_VIGNETTE_ALPHA = 0.55f;
    /** Thickness of a scroll bar's track and knob. */
    public static final float SCROLL_W = 4 * VU;
    public static final float BUTTON_H = 22 * VU;
    public static final float BUTTON_MIN_W = 96 * VU;
    /** Every item in a vertical menu shares this width, so the column is not ragged. */
    public static final float MENU_ITEM_W = 160 * VU;
    public static final float SLOT = 20 * VU;
    public static final float ICON_XS = 8 * VU;
    public static final float ICON_SM = 16 * VU;
    public static final float ICON_MD = 24 * VU;
    public static final float ICON_LG = 32 * VU;
    public static final float BAR_H = 10 * VU;
    public static final float FRAME_LINE = VU;
    public static final float FOCUS_RING = VU;
    public static final float FOCUS_OFFSET = VU;

    // --- Motion (seconds) -------------------------------------------------

    public static final float T_FOCUS = 0.08f;
    public static final float T_PANEL_IN = 0.16f;
    public static final float T_PANEL_OUT = 0.12f;
    public static final float T_SCRIM = 0.15f;
    /** How long a destructive action must be held before it fires. */
    public static final float T_HOLD_CONFIRM = 0.9f;
    public static final float T_TOAST = 4f;
    public static final float T_TOAST_FADE = 0.3f;

    // --- Layers -----------------------------------------------------------
    // World (+CRT) -> HUD -> screen panels -> tooltip/world prompt -> scrim -> modal -> toasts.
    // Z is only meaningful within one Stage; these are the actor z-indices inside a screen stage.

    public static final int Z_PANEL = 0;
    public static final int Z_TOOLTIP = 100;
    public static final int Z_SCRIM = 200;
    public static final int Z_MODAL = 300;
    public static final int Z_TOAST = 400;
}
