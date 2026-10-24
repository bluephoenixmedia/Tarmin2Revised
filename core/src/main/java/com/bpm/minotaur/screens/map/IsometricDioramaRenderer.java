package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.generation.Biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Renders the isometric diorama expedition map prototype:
 * <ul>
 *   <li>720x448 low-res internal {@link FrameBuffer} upscaled 2x with nearest filtering.</li>
 *   <li>Isometric projection mathematics matching {@code Main.dc.html}.</li>
 *   <li>Continuous camera pan, zoom toward cursor, and 90° animated rotation easing.</li>
 *   <li>Placeholder flat diamond grid with mouse picking across z levels (6, 2.5, 2, 1.2, 0).</li>
 *   <li>Volumetric, scrolling GLSL fog of war shader with knowledge mask holes and frontier wisps.</li>
 * </ul>
 */
public final class IsometricDioramaRenderer implements Disposable {

    public static final int FBO_WIDTH = 720;
    public static final int FBO_HEIGHT = 448;
    public static final int CHUNK_GRID_SIZE = 10;

    public static final float ZOOM_WORLD = 1.0f;
    public static final float ZOOM_REGION = 2.6f;
    public static final float ZOOM_CHUNK = 6.4f;

    public static final float MIN_ZOOM = 0.7f;
    public static final float MAX_ZOOM = 8.5f;

    public enum ZoomLevel {
        WORLD,
        REGION,
        CHUNK
    }

    @FunctionalInterface
    public interface ChunkHeightLookup {
        float getTopZ(int cx, int cy);
    }

    // Board colors matching Main.dc.html
    public static final Color COLOR_BOARD_UNDER = new Color(0x17 / 255f, 0x11 / 255f, 0x0c / 255f, 1.0f); // #17110c
    public static final Color COLOR_BOARD_SURFACE_TOP = new Color(0x21 / 255f, 0x19 / 255f, 0x10 / 255f, 1.0f); // #211910
    public static final Color COLOR_BOARD_CAVE_TOP = new Color(0x14 / 255f, 0x0f / 255f, 0x0b / 255f, 1.0f); // #140f0b
    public static final Color COLOR_BOARD_SIDE_A = new Color(0x3b / 255f, 0x28 / 255f, 0x16 / 255f, 1.0f); // #3b2816
    public static final Color COLOR_BOARD_SIDE_B = new Color(0x24 / 255f, 0x18 / 255f, 0x0d / 255f, 1.0f); // #24180d
    public static final Color COLOR_BOARD_GRID = new Color(0x6e / 255f, 0x4d / 255f, 0x25 / 255f, 0.22f); // rgba(110,77,37,0.22)
    public static final Color COLOR_BOARD_RIM = new Color(0x6e / 255f, 0x4d / 255f, 0x25 / 255f, 1.0f); // #6e4d25

    // Maze wall colors matching Main.dc.html
    public static final Color COLOR_WALL_TOP = new Color(0x4b / 255f, 0x44 / 255f, 0x3a / 255f, 1.0f); // #4b443a
    public static final Color COLOR_WALL_LIT = new Color(0x3a / 255f, 0x34 / 255f, 0x2d / 255f, 1.0f); // #3a342d
    public static final Color COLOR_WALL_SHADE = new Color(0x27 / 255f, 0x23 / 255f, 0x1e / 255f, 1.0f); // #27231e
    public static final Color COLOR_WALL_FOOTPRINT = new Color(0x2e / 255f, 0x29 / 255f, 0x23 / 255f, 1.0f); // #2e2923

    // Glimpsed silhouette tint colors
    public static final Color COLOR_GLIMPSED_TOP_TINT = new Color(0x56 / 255f, 0x61 / 255f, 0x6b / 255f, 1.0f); // #56616b
    public static final Color COLOR_GLIMPSED_SIDE_A_TINT = new Color(0x2a / 255f, 0x30 / 255f, 0x38 / 255f, 1.0f); // #2a3038
    public static final Color COLOR_GLIMPSED_SIDE_B_TINT = new Color(0x1c / 255f, 0x21 / 255f, 0x27 / 255f, 1.0f); // #1c2127

    public static final Color COLOR_UNKNOWN_DARK = new Color(0x14 / 255f, 0x12 / 255f, 0x10 / 255f, 1.0f); // #141210
    public static final Color COLOR_TABLE_BG = new Color(0x0b / 255f, 0x09 / 255f, 0x07 / 255f, 1.0f); // #0B0907

    // Subterranean strata band colors matching Main.dc.html
    public static final Color COLOR_STRATA_1_A = new Color(0x2a / 255f, 0x1f / 255f, 0x16 / 255f, 1.0f); // #2a1f16
    public static final Color COLOR_STRATA_1_B = new Color(0x1c / 255f, 0x14 / 255f, 0x0d / 255f, 1.0f); // #1c140d
    public static final Color COLOR_STRATA_2_A = new Color(0x3a / 255f, 0x2b / 255f, 0x1d / 255f, 1.0f); // #3a2b1d
    public static final Color COLOR_STRATA_2_B = new Color(0x27 / 255f, 0x1c / 255f, 0x12 / 255f, 1.0f); // #271c12
    public static final Color COLOR_STRATA_3_A = new Color(0x30 / 255f, 0x24 / 255f, 0x1a / 255f, 1.0f); // #30241a
    public static final Color COLOR_STRATA_3_B = new Color(0x21 / 255f, 0x17 / 255f, 0x0f / 255f, 1.0f); // #21170f
    public static final Color COLOR_STRATA_UNVISITED_TOP = new Color(0x1b / 255f, 0x15 / 255f, 0x11 / 255f, 1.0f); // #1b1511
    public static final Color COLOR_STRATA_SPECK = new Color(0x2a / 255f, 0x21 / 255f, 0x1a / 255f, 1.0f); // #2a211a

    // Cavern interior colors matching Main.dc.html
    public static final Color COLOR_CAVE_FLOOR = new Color(0x2b / 255f, 0x23 / 255f, 0x1c / 255f, 1.0f); // #2b231c
    public static final Color COLOR_CAVE_WALL_FLOOR = new Color(0x1f / 255f, 0x18 / 255f, 0x13 / 255f, 1.0f); // #1f1813
    public static final Color COLOR_PILLAR_TOP = new Color(0x30 / 255f, 0x26 / 255f, 0x1d / 255f, 1.0f); // #30261d
    public static final Color COLOR_PILLAR_A = new Color(0x25 / 255f, 0x1d / 255f, 0x16 / 255f, 1.0f); // #251d16
    public static final Color COLOR_PILLAR_B = new Color(0x18 / 255f, 0x12 / 255f, 0x0d / 255f, 1.0f); // #18120d
    public static final Color COLOR_LADDER_RAIL = new Color(0x9a / 255f, 0x74 / 255f, 0x42 / 255f, 1.0f); // #9a7442
    public static final Color COLOR_LADDER_HOLE = new Color(0x0a / 255f, 0x08 / 255f, 0x06 / 255f, 1.0f); // #0a0806
    public static final Color COLOR_CAVE_CHEST_TOP = new Color(0xa0 / 255f, 0x70 / 255f, 0x2f / 255f, 1.0f); // #a0702f
    public static final Color COLOR_CAVE_CHEST_A = new Color(0x7a / 255f, 0x4f / 255f, 0x22 / 255f, 1.0f); // #7a4f22
    public static final Color COLOR_CAVE_CHEST_B = new Color(0x55 / 255f, 0x36 / 255f, 0x14 / 255f, 1.0f); // #553614
    public static final Color COLOR_FUNGI_GLOW = new Color(108 / 255f, 207 / 255f, 224 / 255f, 1.0f); // #6CCFE0

    // Prop and landmark palette matching Main.dc.html
    public static final Color COLOR_SIL_LIT = new Color(0x2d / 255f, 0x34 / 255f, 0x3c / 255f, 1f); // #2d343c
    public static final Color COLOR_SIL_DARK = new Color(0x20 / 255f, 0x26 / 255f, 0x2c / 255f, 1f); // #20262c

    public static final Color COLOR_PINE_TRUNK = new Color(0x2a / 255f, 0x1d / 255f, 0x14 / 255f, 1f); // #2a1d14
    public static final Color COLOR_PINE_LIT = new Color(0x34 / 255f, 0x47 / 255f, 0x2c / 255f, 1f); // #34472c
    public static final Color COLOR_PINE_DARK = new Color(0x1e / 255f, 0x2b / 255f, 0x1b / 255f, 1f); // #1e2b1b
    public static final Color COLOR_PINE_SNOW_LIT = new Color(0x5e / 255f, 0x6b / 255f, 0x62 / 255f, 1f); // #5e6b62
    public static final Color COLOR_PINE_SNOW_DARK = new Color(0x3d / 255f, 0x48 / 255f, 0x42 / 255f, 1f); // #3d4842
    public static final Color COLOR_PINE_SNOW_CAP = new Color(0xd6 / 255f, 0xdb / 255f, 0xd4 / 255f, 1f); // #d6dbd4

    public static final Color COLOR_CRAG_LIT = new Color(0x77 / 255f, 0x72 / 255f, 0x6a / 255f, 1f); // #77726a
    public static final Color COLOR_CRAG_DARK = new Color(0x45 / 255f, 0x42 / 255f, 0x3d / 255f, 1f); // #45423d
    public static final Color COLOR_CRAG_SNOW_LIT = new Color(0xd4 / 255f, 0xcf / 255f, 0xc4 / 255f, 1f); // #d4cfc4
    public static final Color COLOR_CRAG_SNOW_DARK = new Color(0xa3 / 255f, 0x9e / 255f, 0x94 / 255f, 1f); // #a39e94

    public static final Color COLOR_DUNE_SHADOW = new Color(0x6f / 255f, 0x55 / 255f, 0x36 / 255f, 1f); // #6f5536
    public static final Color COLOR_DUNE_LIT = new Color(0xa2 / 255f, 0x85 / 255f, 0x5a / 255f, 1f); // #a2855a

    public static final Color COLOR_CACTUS_LIT = new Color(0x4f / 255f, 0x5e / 255f, 0x36 / 255f, 1f); // #4f5e36
    public static final Color COLOR_CACTUS_DARK = new Color(0x34 / 255f, 0x41 / 255f, 0x1f / 255f, 1f); // #34411f

    public static final Color COLOR_HOODOO_BASE_LIT = new Color(0x9a / 255f, 0x5f / 255f, 0x3c / 255f, 1f); // #9a5f3c
    public static final Color COLOR_HOODOO_SHAFT_LIT = new Color(0xa8 / 255f, 0x6d / 255f, 0x47 / 255f, 1f); // #a86d47
    public static final Color COLOR_HOODOO_SHADE = new Color(0x6e / 255f, 0x42 / 255f, 0x29 / 255f, 1f); // #6e4229
    public static final Color COLOR_HOODOO_CAP = new Color(0x4e / 255f, 0x34 / 255f, 0x24 / 255f, 1f); // #4e3424

    public static final Color COLOR_ARCH_LIT = new Color(0xa8 / 255f, 0x70 / 255f, 0x48 / 255f, 1f); // #a87048
    public static final Color COLOR_ARCH_DARK = new Color(0x74 / 255f, 0x48 / 255f, 0x2c / 255f, 1f); // #74482c

    public static final Color COLOR_BONES = new Color(0xd9 / 255f, 0xcf / 255f, 0xb8 / 255f, 1f); // #d9cfb8

    public static final Color COLOR_REEDS_LIT = new Color(0x8a / 255f, 0x7a / 255f, 0x4a / 255f, 1f); // #8a7a4a
    public static final Color COLOR_REEDS_DARK = new Color(0x5a / 255f, 0x4a / 255f, 0x2a / 255f, 1f); // #5a4a2a

    public static final Color COLOR_TUFT = new Color(0x55 / 255f, 0x66 / 255f, 0x3e / 255f, 1f); // #55663e

    public static final Color COLOR_BOAT_HULL = new Color(0x4e / 255f, 0x37 / 255f, 0x26 / 255f, 1f); // #4e3726
    public static final Color COLOR_BOAT_SHADE = new Color(0x2e / 255f, 0x20 / 255f, 0x17 / 255f, 1f); // #2e2017
    public static final Color COLOR_BOAT_RIBS = new Color(0x3a / 255f, 0x2a / 255f, 0x1c / 255f, 1f); // #3a2a1c
    public static final Color COLOR_BOAT_MAST = new Color(0x5b / 255f, 0x43 / 255f, 0x30 / 255f, 1f); // #5b4330

    public static final Color COLOR_MOUND_SHADOW = new Color(0x8e / 255f, 0x96 / 255f, 0x96 / 255f, 1f); // #8e9696
    public static final Color COLOR_MOUND_LIT = new Color(0xdf / 255f, 0xe3 / 255f, 0xde / 255f, 1f); // #dfe3de

    public static final Color COLOR_ICE_LIT = new Color(0xb4 / 255f, 0xcb / 255f, 0xd0 / 255f, 1f); // #b4cbd0
    public static final Color COLOR_ICE_DARK = new Color(0x7f / 255f, 0x9b / 255f, 0xa3 / 255f, 1f); // #7f9ba3

    public static final Color COLOR_DEADTREE = new Color(0x24 / 255f, 0x1a / 255f, 0x1c / 255f, 1f); // #241a1c
    public static final Color COLOR_CRACK_RED = new Color(0x8f / 255f, 0x2c / 255f, 0x40 / 255f, 1f); // #8f2c40

    public static final Color COLOR_PUSTULE_BULB = new Color(0x5e / 255f, 0x24 / 255f, 0x33 / 255f, 1f); // #5e2433
    public static final Color COLOR_PUSTULE_HI = new Color(0xc0 / 255f, 0x56 / 255f, 0x6e / 255f, 1f); // #c0566e

    public static final Color COLOR_RUBBLE_LIT = new Color(0x4f / 255f, 0x48 / 255f, 0x3f / 255f, 1f); // #4f483f
    public static final Color COLOR_RUBBLE_DARK = new Color(0x2f / 255f, 0x2a / 255f, 0x25 / 255f, 1f); // #2f2a25

    public static final Color COLOR_POOL = new Color(0x1d / 255f, 0x2c / 255f, 0x2e / 255f, 1f); // #1d2c2e

    public static final Color COLOR_SHELTER_LIT_A = new Color(0x7a / 255f, 0x64 / 255f, 0x48 / 255f, 1f); // #7a6448
    public static final Color COLOR_SHELTER_LIT_B = new Color(0x4f / 255f, 0x3f / 255f, 0x2d / 255f, 1f); // #4f3f2d
    public static final Color COLOR_SHELTER_COLD_A = new Color(0x57 / 255f, 0x53 / 255f, 0x4c / 255f, 1f); // #57534c
    public static final Color COLOR_SHELTER_COLD_B = new Color(0x3c / 255f, 0x39 / 255f, 0x35 / 255f, 1f); // #3c3935

    public static final Color COLOR_ROOF_LIT_L = new Color(0x7a / 255f, 0x3b / 255f, 0x26 / 255f, 1f); // #7a3b26
    public static final Color COLOR_ROOF_LIT_R = new Color(0x4c / 255f, 0x22 / 255f, 0x19 / 255f, 1f); // #4c2219
    public static final Color COLOR_ROOF_COLD_L = new Color(0x46 / 255f, 0x42 / 255f, 0x3d / 255f, 1f); // #46423d
    public static final Color COLOR_ROOF_COLD_R = new Color(0x2e / 255f, 0x2c / 255f, 0x29 / 255f, 1f); // #2e2c29

    public static final Color COLOR_WINDOW_LIT = new Color(0xff / 255f, 0xcf / 255f, 0x6a / 255f, 1f); // #FFCF6A
    public static final Color COLOR_FLAG_POLE = new Color(0x2a / 255f, 0x1d / 255f, 0x14 / 255f, 1f); // #2a1d14
    public static final Color COLOR_FLAG_GOLD = new Color(0xe9 / 255f, 0xb4 / 255f, 0x4c / 255f, 1f); // #E9B44C
    public static final Color COLOR_CHIMNEY = new Color(0x3a / 255f, 0x37 / 255f, 0x33 / 255f, 1f); // #3a3733

    public static final Color COLOR_FIRE_STONE = new Color(0x5b / 255f, 0x51 / 255f, 0x48 / 255f, 1f); // #5b5148
    public static final Color COLOR_FIRE_WOOD = new Color(0x3a / 255f, 0x24 / 255f, 0x16 / 255f, 1f); // #3a2416

    public static final Color COLOR_GRAVE_MOUND = new Color(0x3a / 255f, 0x2c / 255f, 0x20 / 255f, 1f); // #3a2c20
    public static final Color COLOR_GRAVE_STONE_TOP = new Color(0xa3 / 255f, 0x9d / 255f, 0x92 / 255f, 1f); // #a39d92
    public static final Color COLOR_GRAVE_STONE_LIT = new Color(0x7d / 255f, 0x77 / 255f, 0x6e / 255f, 1f); // #7d776e
    public static final Color COLOR_GRAVE_STONE_SHADE = new Color(0x55 / 255f, 0x51 / 255f, 0x4b / 255f, 1f); // #55514b
    public static final Color COLOR_GRAVE_CROSS = new Color(0x3c / 255f, 0x38 / 255f, 0x33 / 255f, 1f); // #3c3833

    public static final Color COLOR_PORTAL_TOP = new Color(0x8a / 255f, 0x84 / 255f, 0x7a / 255f, 1f); // #8a847a
    public static final Color COLOR_PORTAL_LIT = new Color(0x5f / 255f, 0x5a / 255f, 0x53 / 255f, 1f); // #5f5a53
    public static final Color COLOR_PORTAL_SHADE = new Color(0x3f / 255f, 0x3b / 255f, 0x36 / 255f, 1f); // #3f3b36
    public static final Color COLOR_PORTAL_POST_TOP = new Color(0x7c / 255f, 0x76 / 255f, 0x6d / 255f, 1f); // #7c766d

    public static final Color COLOR_SEAL_STONE_TOP = new Color(0x8a / 255f, 0x84 / 255f, 0x7a / 255f, 1f); // #8a847a
    public static final Color COLOR_SEAL_STONE_LIT = new Color(0x6c / 255f, 0x66 / 255f, 0x5e / 255f, 1f); // #6c665e
    public static final Color COLOR_SEAL_STONE_SHADE = new Color(0x47 / 255f, 0x43 / 255f, 0x3e / 255f, 1f); // #47433e
    public static final Color COLOR_SEAL_ALTAR_TOP = new Color(0x7a / 255f, 0x3a / 255f, 0x22 / 255f, 1f); // #7a3a22

    public static final Color COLOR_LADDER_WOOD = new Color(0x9a / 255f, 0x74 / 255f, 0x42 / 255f, 1f); // #9a7442

    public static final Color COLOR_CHEST_TOP = new Color(0xa0 / 255f, 0x70 / 255f, 0x2f / 255f, 1f); // #a0702f
    public static final Color COLOR_CHEST_LIT = new Color(0x7a / 255f, 0x4f / 255f, 0x22 / 255f, 1f); // #7a4f22
    public static final Color COLOR_CHEST_SHADE = new Color(0x55 / 255f, 0x36 / 255f, 0x14 / 255f, 1f); // #553614

    public static final Color COLOR_PLAYER_CYAN = new Color(0x6c / 255f, 0xcf / 255f, 0xe0 / 255f, 1f); // #6CCFE0
    public static final Color COLOR_PLAYER_CYAN_DARK = new Color(0x3d / 255f, 0x8a / 255f, 0x99 / 255f, 1f); // #3d8a99
    public static final Color COLOR_PLAYER_HEAD = new Color(0xe3 / 255f, 0xcf / 255f, 0xa6 / 255f, 1f); // #e3cfa6

    // Overlay colors and badges matching Main.dc.html
    public static final Color COLOR_INK = new Color(0x20 / 255f, 0x15 / 255f, 0x0b / 255f, 1f); // #20150B
    public static final Color COLOR_CYAN = new Color(0x6c / 255f, 0xcf / 255f, 0xe0 / 255f, 1f); // #6CCFE0
    public static final Color COLOR_CYAN_HI = new Color(0xd8 / 255f, 0xf6 / 255f, 0xfb / 255f, 1f); // #D8F6FB
    public static final Color COLOR_CASTLE_PURPLE = new Color(0x8e / 255f, 0x5b / 255f, 0xc4 / 255f, 1f); // #8E5BC4
    public static final Color COLOR_CASTLE_DARK = new Color(0x24 / 255f, 0x14 / 255f, 0x30 / 255f, 1f); // #241430
    public static final Color COLOR_GOLD = new Color(0xe9 / 255f, 0xb4 / 255f, 0x4c / 255f, 1f); // #E9B44C
    public static final Color COLOR_BRIGHT_GOLD = new Color(0xff / 255f, 0xd7 / 255f, 0x7a / 255f, 1f); // #FFD77A
    public static final Color COLOR_SEAL_AMBER = new Color(0xd0 / 255f, 0x68 / 255f, 0x3f / 255f, 1f); // #D0683F
    public static final Color COLOR_PORTAL_LAVENDER = new Color(0xc9 / 255f, 0xa6 / 255f, 0xe8 / 255f, 1f); // #C9A6E8
    public static final Color COLOR_GRAVE_STONE = new Color(0xb9 / 255f, 0xb1 / 255f, 0xa2 / 255f, 1f); // #B9B1A2
    public static final Color COLOR_LADDER_BRONZE = new Color(0xb8 / 255f, 0x86 / 255f, 0x2f / 255f, 1f); // #B8862F
    public static final Color COLOR_RUMOUR_BG = new Color(0x3a / 255f, 0x2a / 255f, 0x18 / 255f, 1f); // #3A2A18
    public static final Color COLOR_COLD_SHELTER = new Color(0x85 / 255f, 0x7a / 255f, 0x6c / 255f, 1f); // #857A6C

    public static final java.util.Map<String, Color> PIN_COLORS = new java.util.HashMap<>();
    static {
        PIN_COLORS.put("danger", new Color(0xd0 / 255f, 0x7a / 255f, 0x5a / 255f, 1f)); // #D07A5A
        PIN_COLORS.put("loot", new Color(0xe2 / 255f, 0xbe / 255f, 0x6a / 255f, 1f)); // #E2BE6A
        PIN_COLORS.put("return", new Color(0xcd / 255f, 0xbf / 255f, 0x9a / 255f, 1f)); // #CDBF9A
        PIN_COLORS.put("trader", new Color(0xbf / 255f, 0xa8 / 255f, 0x6c / 255f, 1f)); // #BFA86C
        PIN_COLORS.put("locked", new Color(0xa8 / 255f, 0x97 / 255f, 0x7c / 255f, 1f)); // #A8977C
        PIN_COLORS.put("unknown", new Color(0xb3 / 255f, 0xa5 / 255f, 0x8e / 255f, 1f)); // #B3A58E
    }

    public static final java.util.Map<String, String[]> GLYPHS = new java.util.HashMap<>();
    static {
        GLYPHS.put("danger", new String[]{".#####.", "#######", "#..#..#", "#######", ".##.##.", ".#####.", ".#.#.#."});
        GLYPHS.put("loot", new String[]{".......", ".#####.", "#.....#", "###.###", "#..#..#", "#.....#", "#######"});
        GLYPHS.put("return", new String[]{".#.....", ".####..", ".#####.", ".####..", ".#.....", ".#.....", "###...."});
        GLYPHS.put("trader", new String[]{"...#...", "#######", "#..#..#", "#..#..#", "##.#.##", "...#...", ".#####."});
        GLYPHS.put("locked", new String[]{"..###..", ".#...#.", ".#...#.", "#######", "###.###", "###.###", "#######"});
        GLYPHS.put("unknown", new String[]{".#####.", "#.....#", ".....#.", "...##..", "...#...", ".......", "...#..."});
        GLYPHS.put("grave", new String[]{"..###..", ".#####.", ".##.##.", ".#...#.", ".##.##.", ".#####.", "#######"});
        GLYPHS.put("portal", new String[]{"..###..", ".#...#.", "#..#..#", "#.#.#.#", "#..#..#", ".#...#.", "..###.."});
        GLYPHS.put("ladder", new String[]{"#.....#", "#######", "#.....#", "#######", "#.....#", "#######", "#.....#"});
        GLYPHS.put("shelter", new String[]{"...#...", "..###..", ".#####.", "#######", ".#...#.", ".#.#.#.", ".#.#.#."});
        GLYPHS.put("home", new String[]{"#..#...", "##.#...", "#.###..", ".#####.", "#######", ".#.#.#.", ".#####."});
        GLYPHS.put("seal", new String[]{"..#.#..", ".#####.", "##...##", ".#.#.#.", "##...##", ".#####.", "..#.#.."});
        GLYPHS.put("castle", new String[]{"#.#.#.#", "#######", ".#####.", ".##.##.", ".#####.", ".##.##.", "#######"});
        GLYPHS.put("event", new String[]{"...#...", "..###..", "..###..", "...#...", "...#...", ".......", "...#..."});
        GLYPHS.put("flag", new String[]{"##.....", "####...", "#####..", "####...", "##.....", "#......", "#......"});
    }

    public static final java.util.Map<Integer, String[]> DIGITS = new java.util.HashMap<>();
    static {
        DIGITS.put(1, new String[]{".#.", "##.", ".#.", ".#.", "###"});
        DIGITS.put(2, new String[]{"##.", "..#", ".#.", "#..", "###"});
        DIGITS.put(3, new String[]{"##.", "..#", ".#.", "..#", "##."});
    }

    public static class BiomeColors {
        public final Color top;
        public final Color sideA;
        public final Color sideB;

        public BiomeColors(Color top, Color sideA, Color sideB) {
            this.top = top;
            this.sideA = sideA;
            this.sideB = sideB;
        }
    }

    public static BiomeColors getBiomeColors(Biome biome) {
        if (biome == null) biome = Biome.MAZE;
        switch (biome) {
            case FOREST:
                return new BiomeColors(
                    new Color(0x2e / 255f, 0x38 / 255f, 0x24 / 255f, 1f),
                    new Color(0x2e / 255f, 0x25 / 255f, 0x19 / 255f, 1f),
                    new Color(0x1f / 255f, 0x19 / 255f, 0x10 / 255f, 1f)
                );
            case DESERT:
                return new BiomeColors(
                    new Color(0x8a / 255f, 0x6d / 255f, 0x47 / 255f, 1f),
                    new Color(0x77 / 255f, 0x59 / 255f, 0x3a / 255f, 1f),
                    new Color(0x56 / 255f, 0x3f / 255f, 0x28 / 255f, 1f)
                );
            case MOUNTAINS:
                return new BiomeColors(
                    new Color(0x56 / 255f, 0x52 / 255f, 0x4b / 255f, 1f),
                    new Color(0x46 / 255f, 0x42 / 255f, 0x3c / 255f, 1f),
                    new Color(0x30 / 255f, 0x2d / 255f, 0x29 / 255f, 1f)
                );
            case LAKELANDS:
                return new BiomeColors(
                    new Color(0x3a / 255f, 0x46 / 255f, 0x30 / 255f, 1f),
                    new Color(0x2f / 255f, 0x2a / 255f, 0x1f / 255f, 1f),
                    new Color(0x20 / 255f, 0x1d / 255f, 0x15 / 255f, 1f)
                );
            case TUNDRA:
                return new BiomeColors(
                    new Color(0xae / 255f, 0xb3 / 255f, 0xad / 255f, 1f),
                    new Color(0x86 / 255f, 0x8b / 255f, 0x88 / 255f, 1f),
                    new Color(0x63 / 255f, 0x68 / 255f, 0x66 / 255f, 1f)
                );
            case BLIGHT:
                return new BiomeColors(
                    new Color(0x3b / 255f, 0x2a / 255f, 0x2c / 255f, 1f),
                    new Color(0x3a / 255f, 0x24 / 255f, 0x27 / 255f, 1f),
                    new Color(0x27 / 255f, 0x18 / 255f, 0x1a / 255f, 1f)
                );
            case OCEAN:
                return new BiomeColors(
                    new Color(0x18 / 255f, 0x26 / 255f, 0x2d / 255f, 1f),
                    new Color(0x16 / 255f, 0x24 / 255f, 0x2a / 255f, 1f),
                    new Color(0x0f / 255f, 0x19 / 255f, 0x1e / 255f, 1f)
                );
            case MAZE:
            default:
                return new BiomeColors(
                    new Color(0x3b / 255f, 0x34 / 255f, 0x2c / 255f, 1f),
                    new Color(0x2c / 255f, 0x26 / 255f, 0x21 / 255f, 1f),
                    new Color(0x1d / 255f, 0x19 / 255f, 0x15 / 255f, 1f)
                );
        }
    }

    public static Color mix(Color c1, Color c2, float ratio) {
        float r = c1.r * (1f - ratio) + c2.r * ratio;
        float g = c1.g * (1f - ratio) + c2.g * ratio;
        float b = c1.b * (1f - ratio) + c2.b * ratio;
        float a = c1.a * (1f - ratio) + c2.a * ratio;
        return new Color(r, g, b, a);
    }

    public static Color shade(Color c, float amt) {
        if (amt >= 0f) {
            return mix(c, Color.WHITE, amt);
        } else {
            return mix(c, Color.BLACK, -amt);
        }
    }

    private static Color lit(Color c, boolean sil) {
        return sil ? COLOR_SIL_LIT : c;
    }

    private static Color dark(Color c, boolean sil) {
        return sil ? COLOR_SIL_DARK : c;
    }

    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private FrameBuffer surfaceFbo;
    private TextureRegion surfaceRegion;
    private SpriteBatch fboBatch;
    private ShapeRenderer shapeRenderer;

    private float floorT = 0f;
    private float targetFloorT = 0f;

    private final MapNoiseTextures noiseTextures = new MapNoiseTextures();
    private final MapKnowledgeMask knowledgeMask = new MapKnowledgeMask();
    private MapFogShader fogShader;

    // Current animated camera values
    private float camX = 5.0f;
    private float camY = 5.0f;
    private float camZoom = 1.0f;
    private float camRot = 0.0f; // Radians

    // Target values for continuous easing
    private float tgtX = 5.0f;
    private float tgtY = 5.0f;
    private float tgtZoom = 1.0f;
    private float tgtRot = 0.0f;
    private int rotStep = 0; // Steps of 90 degrees

    private float fogStrength = 1.0f;
    private boolean isCave = false;
    private float elapsedTime = 0f;

    private boolean initialized = false;

    public IsometricDioramaRenderer() {
    }

    public static ZoomLevel levelOf(float zoom) {
        if (zoom < 1.7f) return ZoomLevel.WORLD;
        if (zoom < 4.2f) return ZoomLevel.REGION;
        return ZoomLevel.CHUNK;
    }

    public static Vector2 project(float wx, float wy, float z, float camX, float camY, float zoom, float rot, float W, float H) {
        float hw = 28f * zoom;
        float hh = 14f * zoom;
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        float dx = wx - camX;
        float dy = wy - camY;
        float rx = dx * cs - dy * sn;
        float ry = dx * sn + dy * cs;
        float sx = W / 2f + (rx - ry) * hw;
        float sy = H / 2f + (rx + ry) * hh - z * zoom;
        return new Vector2(sx, sy);
    }

    public static Vector2 unproject(float sx, float sy, float z, float camX, float camY, float zoom, float rot, float W, float H) {
        float hw = 28f * zoom;
        float hh = 14f * zoom;
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        float X = sx - W / 2f;
        float Y = sy - H / 2f + z * zoom;
        float a = X / hw;
        float b = Y / hh;
        float rx = (a + b) / 2f;
        float ry = (b - a) / 2f;
        float wx = camX + rx * cs + ry * sn;
        float wy = camY - rx * sn + ry * cs;
        return new Vector2(wx, wy);
    }

    public void init() {
        if (initialized || Gdx.gl == null) return;

        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, FBO_WIDTH, FBO_HEIGHT, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true); // OpenGL Y-flip

        surfaceFbo = new FrameBuffer(Pixmap.Format.RGBA8888, FBO_WIDTH, FBO_HEIGHT, false);
        surfaceFbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        surfaceRegion = new TextureRegion(surfaceFbo.getColorBufferTexture());
        surfaceRegion.flip(false, true);

        fboBatch = new SpriteBatch();
        fboBatch.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);

        shapeRenderer = new ShapeRenderer();
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);
        shapeRenderer.updateMatrices();

        noiseTextures.buildTextures();
        fogShader = new MapFogShader();

        initialized = true;
    }

    public void updateKnowledge(MapKnowledgeMask.KnowledgeProvider provider) {
        if (!initialized) init();
        knowledgeMask.rebuild(provider);
    }

    /**
     * Updates continuous camera easing toward targets matching prototype formula:
     * {@code k = 1 - Math.exp(-dt * 9)}.
     */
    public void update(float delta) {
        elapsedTime += delta;

        float k = 1.0f - (float) Math.exp(-delta * 9.0);

        float dx = tgtX - camX;
        if (Math.abs(dx) > 0.0005f) camX += dx * k; else camX = tgtX;

        float dy = tgtY - camY;
        if (Math.abs(dy) > 0.0005f) camY += dy * k; else camY = tgtY;

        float ds = tgtZoom - camZoom;
        if (Math.abs(ds) > 0.0005f) camZoom += ds * k; else camZoom = tgtZoom;

        float dRot = tgtRot - camRot;
        if (Math.abs(dRot) > 0.0005f) camRot += dRot * k; else camRot = tgtRot;

        float ft = targetFloorT;
        float dF = ft - floorT;
        if (Math.abs(dF) > 0.003f) {
            floorT += dF * (1.0f - (float) Math.exp(-delta * 3.2));
        } else {
            floorT = ft;
        }
    }

    public void setTarget(float x, float y, float zoom, float rot) {
        this.tgtX = x;
        this.tgtY = y;
        this.tgtZoom = zoom;
        this.tgtRot = rot;
        clampTarget();
    }

    public void setCamera(float x, float y, float zoom, float rot) {
        setTarget(x, y, zoom, rot);
    }

    public void setCameraImmediate(float x, float y, float zoom, float rot) {
        setTarget(x, y, zoom, rot);
        this.camX = tgtX;
        this.camY = tgtY;
        this.camZoom = tgtZoom;
        this.camRot = tgtRot;
    }

    public void panScreen(float screenDx, float screenDy, float actorWidth, float actorHeight) {
        float scaleX = (float) FBO_WIDTH / (actorWidth > 0 ? actorWidth : FBO_WIDTH);
        float scaleY = (float) FBO_HEIGHT / (actorHeight > 0 ? actorHeight : FBO_HEIGHT);
        float dx = screenDx * scaleX;
        float dy = -screenDy * scaleY; // Invert for canvas top-down Y vs LibGDX bottom-up Y

        float s = tgtZoom;
        float cs = (float) Math.cos(tgtRot);
        float sn = (float) Math.sin(tgtRot);
        float a = dx / (28f * s);
        float b = dy / (14f * s);
        float rx = (a + b) / 2f;
        float ry = (b - a) / 2f;
        tgtX += rx * cs + ry * sn;
        tgtY += -rx * sn + ry * cs;
        clampTarget();
    }

    public void zoomAt(float localX, float localY, float actorWidth, float actorHeight, float factor) {
        float sx = localX * ((float) FBO_WIDTH / (actorWidth > 0 ? actorWidth : FBO_WIDTH));
        float sy = (actorHeight - localY) * ((float) FBO_HEIGHT / (actorHeight > 0 ? actorHeight : FBO_HEIGHT));

        Vector2 w = unproject(sx, sy, 4f, tgtX, tgtY, tgtZoom, tgtRot, FBO_WIDTH, FBO_HEIGHT);
        float ns = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, tgtZoom * factor));
        float ratio = tgtZoom / ns;
        tgtX = w.x - (w.x - tgtX) * ratio;
        tgtY = w.y - (w.y - tgtY) * ratio;
        tgtZoom = ns;
        clampTarget();
    }

    public void zoomBy(float factor) {
        tgtZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, tgtZoom * factor));
    }

    public void setTargetZoom(float zoom) {
        this.tgtZoom = zoom;
        clampTarget();
    }

    public ZoomLevel getZoomLevel() {
        return levelOf(tgtZoom);
    }

    public void snapCameraPosition() {
        this.camX = this.tgtX;
        this.camY = this.tgtY;
    }

    public void rotate(int dir) {
        rotStep += dir;
        tgtRot = rotStep * (float) (Math.PI / 2.0);
    }

    public float getCameraRotation() {
        return camRot;
    }

    public float getTargetRotation() {
        return tgtRot;
    }

    public void centerOn(float wx, float wy) {
        tgtX = wx;
        tgtY = wy;
        clampTarget();
    }

    private void clampTarget() {
        tgtX = Math.max(-1f, Math.min(CHUNK_GRID_SIZE + 1f, tgtX));
        tgtY = Math.max(-1f, Math.min(CHUNK_GRID_SIZE + 1f, tgtY));
        tgtZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, tgtZoom));
    }

    public GridPoint2 pickChunk(float localX, float localY, float actorWidth, float actorHeight,
                               int floor, ChunkHeightLookup heightLookup) {
        float sx = localX * ((float) FBO_WIDTH / (actorWidth > 0 ? actorWidth : FBO_WIDTH));
        float sy = (actorHeight - localY) * ((float) FBO_HEIGHT / (actorHeight > 0 ? actorHeight : FBO_HEIGHT));

        boolean cave = (floor != 1 || floorT > 0.5f);
        float[] zs = cave ? new float[]{5f} : new float[]{6f, 2.5f, 2f, 1.2f, 0f};

        for (float z : zs) {
            Vector2 w = unproject(sx, sy, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            int cx = (int) Math.floor(w.x);
            int cy = (int) Math.floor(w.y);
            if (cx < 0 || cx >= CHUNK_GRID_SIZE || cy < 0 || cy >= CHUNK_GRID_SIZE) continue;

            if (cave || z == 0f || heightLookup == null) {
                return new GridPoint2(cx, cy);
            }
            float expectedZ = heightLookup.getTopZ(cx, cy);
            if (Math.abs(expectedZ - z) < 0.2f) {
                return new GridPoint2(cx, cy);
            }
        }
        return null;
    }

    public float getCamX() { return camX; }
    public float getCamY() { return camY; }
    public float getCamZoom() { return camZoom; }
    public float getCamRot() { return camRot; }

    public float getTgtX() { return tgtX; }
    public float getTgtY() { return tgtY; }
    public float getTgtZoom() { return tgtZoom; }
    public float getTgtRot() { return tgtRot; }

    public void setFogStrength(float strength) {
        this.fogStrength = strength;
    }

    public void setCave(boolean cave) {
        this.isCave = cave;
    }

    public void setFloor(int floor) {
        this.targetFloorT = (floor != 1) ? 1.0f : 0.0f;
        this.isCave = (floor != 1);
    }

    public void setFloorImmediate(int floor) {
        this.targetFloorT = (floor != 1) ? 1.0f : 0.0f;
        this.floorT = this.targetFloorT;
        this.isCave = (floor != 1);
    }

    public float getFloorT() {
        return floorT;
    }

    public float getTargetFloorT() {
        return targetFloorT;
    }

    public static float ease(float t) {
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    public TextureRegion getFboRegion() {
        return fboRegion;
    }

    /**
     * Renders the low-res 2.5D diorama scene into the internal 720x448 FBO.
     */
    public void renderFbo(MapKnowledgeMask.KnowledgeProvider provider) {
        if (!initialized) {
            init();
            if (provider != null && knowledgeMask.getMaskTexture() == null) {
                knowledgeMask.rebuild(provider);
            }
        }

        if (fbo == null) return;

        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);
        shapeRenderer.updateMatrices();
        fboBatch.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);

        float ft = ease(floorT);

        if (floorT <= 0.001f) {
            // Surface only
            fbo.begin();
            Gdx.gl.glClearColor(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, 1f); // #0B0907
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            renderDioramaScene(provider);
            renderLights(shapeRenderer, provider, false);

            if (fogShader != null && fogShader.isCompiled() && knowledgeMask.getMaskTexture() != null) {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

                fboBatch.begin();
                Vector2 orig = project(0f, 0f, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                fogShader.render(fboBatch, noiseTextures, knowledgeMask.getMaskTexture(),
                        elapsedTime, camX, camY, camZoom, camRot, orig.x, orig.y,
                        fogStrength, false, FBO_WIDTH, FBO_HEIGHT);
                fboBatch.end();
            }

            renderVignette(shapeRenderer);
            renderOverlays(shapeRenderer, provider, 0f);

            fbo.end();
        } else {
            // Subterranean transition or cave view
            // 1. Render surface into surfaceFbo
            if (surfaceFbo != null) {
                surfaceFbo.begin();
                Gdx.gl.glClearColor(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

                renderDioramaScene(provider);
                renderLights(shapeRenderer, provider, false);

                if (fogShader != null && fogShader.isCompiled() && knowledgeMask.getMaskTexture() != null) {
                    Gdx.gl.glEnable(GL20.GL_BLEND);
                    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

                    fboBatch.begin();
                    Vector2 orig = project(0f, 0f, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    fogShader.render(fboBatch, noiseTextures, knowledgeMask.getMaskTexture(),
                            elapsedTime, camX, camY, camZoom, camRot, orig.x, orig.y,
                            fogStrength, false, FBO_WIDTH, FBO_HEIGHT);
                    fboBatch.end();
                }
                surfaceFbo.end();
            }

            // 2. Render cavern into primary fbo
            fbo.begin();
            Gdx.gl.glClearColor(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            renderCaveScene(provider);
            renderLights(shapeRenderer, provider, true);

            if (fogShader != null && fogShader.isCompiled() && knowledgeMask.getMaskTexture() != null) {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

                fboBatch.begin();
                Vector2 orig = project(0f, 0f, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                fogShader.render(fboBatch, noiseTextures, knowledgeMask.getMaskTexture(),
                        elapsedTime, camX, camY, camZoom, camRot, orig.x, orig.y,
                        fogStrength, true, FBO_WIDTH, FBO_HEIGHT);
                fboBatch.end();
            }

            // 3. Composite ghost surface lifted upwards
            if (surfaceRegion != null) {
                float lift = Math.min(64f, 22f * camZoom) * ft;
                float ghostAlpha = floorT < 0.999f ? (1f - 0.84f * ft) : 0.16f;

                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                fboBatch.begin();
                fboBatch.setColor(1f, 1f, 1f, ghostAlpha);
                fboBatch.draw(surfaceRegion, 0, lift, FBO_WIDTH, FBO_HEIGHT);
                fboBatch.setColor(Color.WHITE);
                fboBatch.end();
            }

            // 4. Perimeter vignette
            renderVignette(shapeRenderer);

            // 5. Overlays with animated floor blending
            renderOverlays(shapeRenderer, provider, ft);

            fbo.end();
        }
    }

    /**
     * Renders the low-res FBO scene and draws it upscaled into the destination bounds.
     */
    public void render(Batch batch, float targetX, float targetY, float targetWidth, float targetHeight,
                       MapKnowledgeMask.KnowledgeProvider provider) {
        renderFbo(provider);
        if (batch != null && batch.isDrawing() && fboRegion != null) {
            batch.draw(fboRegion, targetX, targetY, targetWidth, targetHeight);
        }
    }

    public static int hash(int x, int y) {
        long h = (x + 31L) * 73856093L ^ (y + 17L) * 19349663L ^ 0x9e3779b9L;
        return (int) (h & 0xFFFFFFFFL);
    }

    public static final class Mulberry32 {
        private int state;

        public Mulberry32(int seed) {
            this.state = seed;
        }

        public float nextFloat() {
            state += 0x6D2B79F5;
            int t = state;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + ((t ^ (t >>> 7)) * (t | 61));
            long val = (t ^ (t >>> 14)) & 0xFFFFFFFFL;
            return (float) (val / 4294967296.0);
        }
    }

    public static final class Prop {
        public enum Kind {
            PINE, CRAG, DUNE, CACTUS, HOODOO, ARCH, BONES, REEDS, TUFT,
            BOAT, MOUND, ICE, DEADTREE, CRACK, PUSTULE, RUBBLE, POOL, WALLSTUB,
            SHELTER, FIRE, GRAVE, PORTAL, SEAL, LADDER, CHEST, PLAYER
        }

        public Kind kind;
        public float u;
        public float v;
        public float sz = 1.0f;
        public float va = 0.5f;
        public boolean snow = false;
        public MapModel.ShelterMark shelterType = null;
        public float faceX = 0f;
        public float faceY = 1f;
    }

    static final class WallBox {
        float x0, y0, x1, y1;
        float z0, z1;
        float depth;
    }

    static final class RenderItem implements Comparable<RenderItem> {
        float depth;
        boolean isWall;
        WallBox wall;
        Prop prop;

        @Override
        public int compareTo(RenderItem o) {
            return Float.compare(this.depth, o.depth);
        }
    }

    static final class CaveRenderItem implements Comparable<CaveRenderItem> {
        enum Type { PILLAR, CHEST, LADDER }
        float depth;
        Type type;
        float x0, y0, x1, y1;
        float z0, z1;

        @Override
        public int compareTo(CaveRenderItem o) {
            return Float.compare(this.depth, o.depth);
        }
    }

    private final List<GridPoint2> sortedChunks = new ArrayList<>(CHUNK_GRID_SIZE * CHUNK_GRID_SIZE);
    private final List<WallBox> wallBoxPool = new ArrayList<>(64);
    private final List<Prop> chunkProps = new ArrayList<>(64);
    private final List<RenderItem> renderItems = new ArrayList<>(128);
    private final List<CaveRenderItem> renderCaveItems = new ArrayList<>(128);

    public static float depth(float wx, float wy, float camX, float camY, float rot) {
        float dx = wx - camX;
        float dy = wy - camY;
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        return (dx * cs - dy * sn) + (dx * sn + dy * cs);
    }

    public float depth(float wx, float wy) {
        return depth(wx, wy, camX, camY, camRot);
    }

    public boolean onScreen(float wx, float wy, float margin) {
        Vector2 p = project(wx, wy, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        return p.x > -margin && p.x < FBO_WIDTH + margin && p.y > -margin && p.y < FBO_HEIGHT + margin;
    }

    public void drawBox(ShapeRenderer shapes,
                        float x0, float y0, float x1, float y1,
                        float z0, float z1,
                        Color topCol, Color cL, Color cR) {
        drawBox(shapes, x0, y0, x1, y1, z0, z1, topCol, cL, cR,
                camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
    }

    public static void drawBox(ShapeRenderer shapes,
                               float x0, float y0, float x1, float y1,
                               float z0, float z1,
                               Color topCol, Color cL, Color cR,
                               float camX, float camY, float zoom, float rot,
                               float W, float H) {
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);

        float[][] pts = new float[][]{
            {x0, y0},
            {x1, y0},
            {x1, y1},
            {x0, y1}
        };
        float[][] nrm = new float[][]{
            {0f, -1f},
            {1f, 0f},
            {0f, 1f},
            {-1f, 0f}
        };

        for (int i = 0; i < 4; i++) {
            float nx = nrm[i][0];
            float ny = nrm[i][1];
            float rx = nx * cs - ny * sn;
            float ry = nx * sn + ny * cs;
            if (rx + ry <= 0.001f) continue; // Face culling

            float[] a = pts[i];
            float[] b = pts[(i + 1) % 4];

            Vector2 g0 = toGdxScreen(project(a[0], a[1], z0, camX, camY, zoom, rot, W, H));
            Vector2 g1 = toGdxScreen(project(b[0], b[1], z0, camX, camY, zoom, rot, W, H));
            Vector2 g2 = toGdxScreen(project(b[0], b[1], z1, camX, camY, zoom, rot, W, H));
            Vector2 g3 = toGdxScreen(project(a[0], a[1], z1, camX, camY, zoom, rot, W, H));

            Color sideColor = (rx - ry < 0f) ? cL : cR;
            if (sideColor != null) {
                shapes.setColor(sideColor);
                shapes.triangle(g0.x, g0.y, g1.x, g1.y, g2.x, g2.y);
                shapes.triangle(g0.x, g0.y, g2.x, g2.y, g3.x, g3.y);
            }
        }

        if (topCol != null) {
            Vector2 t0 = toGdxScreen(project(pts[0][0], pts[0][1], z1, camX, camY, zoom, rot, W, H));
            Vector2 t1 = toGdxScreen(project(pts[1][0], pts[1][1], z1, camX, camY, zoom, rot, W, H));
            Vector2 t2 = toGdxScreen(project(pts[2][0], pts[2][1], z1, camX, camY, zoom, rot, W, H));
            Vector2 t3 = toGdxScreen(project(pts[3][0], pts[3][1], z1, camX, camY, zoom, rot, W, H));

            shapes.setColor(topCol);
            shapes.triangle(t0.x, t0.y, t1.x, t1.y, t2.x, t2.y);
            shapes.triangle(t0.x, t0.y, t2.x, t2.y, t3.x, t3.y);
        }
    }

    private void drawBoard(ShapeRenderer shapes, boolean cave) {
        float N = CHUNK_GRID_SIZE;
        float e = 0.4f;

        // Under-base quad at z = -5
        Vector2 b0 = toGdxScreen(project(-1.8f, -1.8f, -5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 b1 = toGdxScreen(project(N + 1.8f, -1.8f, -5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 b2 = toGdxScreen(project(N + 1.8f, N + 1.8f, -5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 b3 = toGdxScreen(project(-1.8f, N + 1.8f, -5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

        shapes.setColor(COLOR_BOARD_UNDER);
        shapes.triangle(b0.x, b0.y, b1.x, b1.y, b2.x, b2.y);
        shapes.triangle(b0.x, b0.y, b2.x, b2.y, b3.x, b3.y);

        // Board box slab from z = -5 to z = 0
        Color slabTop = cave ? COLOR_BOARD_CAVE_TOP : COLOR_BOARD_SURFACE_TOP;
        drawBox(shapes, -e, -e, N + e, N + e, -5f, 0f, slabTop, COLOR_BOARD_SIDE_A, COLOR_BOARD_SIDE_B);
    }

    private void drawBoardLines(ShapeRenderer shapes) {
        float N = CHUNK_GRID_SIZE;
        float e = 0.4f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Line);

        // Board grid lines at z = 0
        shapes.setColor(COLOR_BOARD_GRID);
        for (int i = 0; i <= (int) N; i++) {
            Vector2 a1 = toGdxScreen(project(i, -e, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 b1 = toGdxScreen(project(i, N + e, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            shapes.line(a1.x, a1.y, b1.x, b1.y);

            Vector2 a2 = toGdxScreen(project(-e, i, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 b2 = toGdxScreen(project(N + e, i, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            shapes.line(a2.x, a2.y, b2.x, b2.y);
        }

        // Rim border
        shapes.setColor(COLOR_BOARD_RIM);
        Vector2 r0 = toGdxScreen(project(-e, -e, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 r1 = toGdxScreen(project(N + e, -e, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 r2 = toGdxScreen(project(N + e, N + e, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 r3 = toGdxScreen(project(-e, N + e, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        shapes.line(r0.x, r0.y, r1.x, r1.y);
        shapes.line(r1.x, r1.y, r2.x, r2.y);
        shapes.line(r2.x, r2.y, r3.x, r3.y);
        shapes.line(r3.x, r3.y, r0.x, r0.y);

        shapes.end();
    }

    private void renderDioramaScene(MapKnowledgeMask.KnowledgeProvider provider) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawBoard(shapeRenderer, isCave);

        float m = 40f * camZoom + 60f;

        sortedChunks.clear();
        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                MapKnowledgeMask.KnowledgeProvider.State state = (provider != null)
                        ? provider.getChunkState(cx, cy)
                        : MapKnowledgeMask.KnowledgeProvider.State.UNKNOWN;
                if (state != MapKnowledgeMask.KnowledgeProvider.State.UNKNOWN) {
                    sortedChunks.add(new GridPoint2(cx, cy));
                }
            }
        }
        sortedChunks.sort((a, b) -> Float.compare(depth(a.x + 0.5f, a.y + 0.5f), depth(b.x + 0.5f, b.y + 0.5f)));

        for (GridPoint2 c : sortedChunks) {
            if (!onScreen(c.x + 0.5f, c.y + 0.5f, m)) continue;

            MapKnowledgeMask.KnowledgeProvider.State state = (provider != null)
                    ? provider.getChunkState(c.x, c.y)
                    : MapKnowledgeMask.KnowledgeProvider.State.UNKNOWN;

            Biome biome = (provider != null) ? provider.getBiome(c.x, c.y) : Biome.MAZE;
            ChunkTiles tiles = (provider != null) ? provider.getChunkTiles(c.x, c.y) : null;

            if (state == MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
                drawVisitedChunk(c.x, c.y, biome, tiles, provider);
            } else if (state == MapKnowledgeMask.KnowledgeProvider.State.GLIMPSED) {
                drawGlimpsedChunk(c.x, c.y, biome, provider);
            }
        }

        shapeRenderer.end();

        drawBoardLines(shapeRenderer);
    }

    public void renderCaveScene(MapKnowledgeMask.KnowledgeProvider provider) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        drawBoard(shapeRenderer, true);

        float m = 40f * camZoom + 60f;

        sortedChunks.clear();
        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                MapKnowledgeMask.KnowledgeProvider.State state = (provider != null)
                        ? provider.getChunkState(cx, cy)
                        : MapKnowledgeMask.KnowledgeProvider.State.UNKNOWN;
                if (state != MapKnowledgeMask.KnowledgeProvider.State.UNKNOWN) {
                    sortedChunks.add(new GridPoint2(cx, cy));
                }
            }
        }
        sortedChunks.sort((a, b) -> Float.compare(depth(a.x + 0.5f, a.y + 0.5f), depth(b.x + 0.5f, b.y + 0.5f)));

        for (GridPoint2 c : sortedChunks) {
            if (!onScreen(c.x + 0.5f, c.y + 0.5f, m)) continue;

            int cx = c.x;
            int cy = c.y;
            boolean hasCave = (provider != null) && provider.hasCave(cx, cy);

            // 1. Strata band 1: z=0 to 1.7
            drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 0f, 1.7f, null, COLOR_STRATA_1_A, COLOR_STRATA_1_B);

            // 2. Strata band 2: z=1.7 to 3.4
            drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 1.7f, 3.4f, null, COLOR_STRATA_2_A, COLOR_STRATA_2_B);

            // 3. Strata band 3: z=3.4 to 5.0
            Color top3 = hasCave ? null : COLOR_STRATA_UNVISITED_TOP;
            drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 3.4f, 5.0f, top3, COLOR_STRATA_3_A, COLOR_STRATA_3_B);

            if (!hasCave) {
                // If zoom > 1.5, draw bedrock specks
                if (camZoom > 1.5f) {
                    shapeRenderer.setColor(COLOR_STRATA_SPECK);
                    Mulberry32 rng = new Mulberry32(hash(cx, cy) + 11);
                    for (int i = 0; i < 10; i++) {
                        float su = 0.08f + 0.84f * rng.nextFloat();
                        float sv = 0.08f + 0.84f * rng.nextFloat();
                        Vector2 sp = project(cx + su, cy + sv, 5.0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        Vector2 gsp = toGdxScreen(sp);
                        shapeRenderer.rect(Math.round(gsp.x), Math.round(gsp.y), 1, 1);
                    }
                }
                continue;
            }

            // Has cavern: draw 8x8 floor tiles and depth-sorted rock pillars / ladder / chests
            ChunkTiles caveTiles = (provider != null) ? provider.getCavernTiles(cx, cy) : null;
            Mulberry32 rng = new Mulberry32(hash(cx, cy) + 4242);

            // Draw 8x8 floor quad tiles at z=5
            for (int ty = 0; ty < 8; ty++) {
                for (int tx = 0; tx < 8; tx++) {
                    boolean solid = (caveTiles != null) ? caveTiles.isSolid(tx, ty) : (tx == 0 || tx == 7 || ty == 0 || ty == 7 || (tx == 3 && ty == 3));
                    Color floorCol = solid ? COLOR_CAVE_WALL_FLOOR : COLOR_CAVE_FLOOR;

                    float x0 = cx + tx / 8.0f;
                    float y0 = cy + ty / 8.0f;
                    float x1 = cx + (tx + 1) / 8.0f;
                    float y1 = cy + (ty + 1) / 8.0f;

                    Vector2 t0 = toGdxScreen(project(x0, y0, 5.0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                    Vector2 t1 = toGdxScreen(project(x1, y0, 5.0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                    Vector2 t2 = toGdxScreen(project(x1, y1, 5.0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                    Vector2 t3 = toGdxScreen(project(x0, y1, 5.0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

                    shapeRenderer.setColor(floorCol);
                    shapeRenderer.triangle(t0.x, t0.y, t1.x, t1.y, t2.x, t2.y);
                    shapeRenderer.triangle(t0.x, t0.y, t2.x, t2.y, t3.x, t3.y);
                }
            }

            // Collect depth-sorted cavern items: rock pillars, ladder, chest
            renderCaveItems.clear();
            for (int ty = 0; ty < 8; ty++) {
                for (int tx = 0; tx < 8; tx++) {
                    boolean solid = (caveTiles != null) ? caveTiles.isSolid(tx, ty) : (tx == 0 || tx == 7 || ty == 0 || ty == 7 || (tx == 3 && ty == 3));
                    if (solid) {
                        float x0 = cx + tx / 8.0f;
                        float y0 = cy + ty / 8.0f;
                        float hPillar = 2.4f + 1.2f * rng.nextFloat();
                        CaveRenderItem item = new CaveRenderItem();
                        item.depth = depth(x0 + 1f / 16f, y0 + 1f / 16f);
                        item.type = CaveRenderItem.Type.PILLAR;
                        item.x0 = x0;
                        item.y0 = y0;
                        item.x1 = x0 + 1f / 8f;
                        item.y1 = y0 + 1f / 8f;
                        item.z0 = 5.0f;
                        item.z1 = 5.0f + hPillar;
                        renderCaveItems.add(item);
                    }
                }
            }

            if (provider != null && provider.hasLadder(cx, cy)) {
                CaveRenderItem lad = new CaveRenderItem();
                lad.depth = depth(cx + 4.5f / 8f, cy + 4.5f / 8f) - 0.05f;
                lad.type = CaveRenderItem.Type.LADDER;
                lad.x0 = cx + 4.5f / 8f;
                lad.y0 = cy + 4.5f / 8f;
                lad.z0 = 5.0f;
                lad.z1 = 15.0f; // up = 10
                renderCaveItems.add(lad);
            }

            if (provider != null && provider.getChestCount(cx, cy) > 0) {
                CaveRenderItem ch = new CaveRenderItem();
                ch.depth = depth(cx + 0.5f, cy + 0.35f);
                ch.type = CaveRenderItem.Type.CHEST;
                ch.x0 = cx + 0.5f - 0.03f;
                ch.y0 = cy + 0.35f - 0.022f;
                ch.x1 = cx + 0.5f + 0.03f;
                ch.y1 = cy + 0.35f + 0.022f;
                ch.z0 = 5.0f;
                ch.z1 = 6.6f;
                renderCaveItems.add(ch);
            }

            renderCaveItems.sort(null);
            for (CaveRenderItem item : renderCaveItems) {
                switch (item.type) {
                    case PILLAR:
                        drawBox(shapeRenderer, item.x0, item.y0, item.x1, item.y1, item.z0, item.z1,
                                COLOR_PILLAR_TOP, COLOR_PILLAR_A, COLOR_PILLAR_B);
                        break;
                    case CHEST:
                        drawBox(shapeRenderer, item.x0, item.y0, item.x1, item.y1, item.z0, item.z1,
                                COLOR_CAVE_CHEST_TOP, COLOR_CAVE_CHEST_A, COLOR_CAVE_CHEST_B);
                        break;
                    case LADDER:
                        drawLadderShaftProp(shapeRenderer, item.x0, item.y0, item.z0, 10f);
                        break;
                }
            }

            // Bioluminescent fungi dots at z=5
            for (int i = 0; i < 4; i++) {
                float fu = 0.2f + 0.6f * rng.nextFloat();
                float fv = 0.2f + 0.6f * rng.nextFloat();
                Vector2 fp = project(cx + fu, cy + fv, 5.0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                Vector2 gfp = toGdxScreen(fp);
                int k = Math.max(1, Math.round(camZoom * 0.4f));
                shapeRenderer.setColor(COLOR_FUNGI_GLOW);
                shapeRenderer.rect(Math.round(gfp.x), Math.round(gfp.y) - k, k, k);
            }
        }

        shapeRenderer.end();
        drawBoardLines(shapeRenderer);
    }

    public void drawLadderShaftProp(ShapeRenderer shapes, float x, float y, float h, float up) {
        float hz = 1.0f / 16.0f;
        Vector2 p0 = toGdxScreen(project(x - hz, y - hz, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 p1 = toGdxScreen(project(x + hz, y - hz, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 p2 = toGdxScreen(project(x + hz, y + hz, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 p3 = toGdxScreen(project(x - hz, y + hz, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

        shapes.setColor(COLOR_LADDER_HOLE);
        shapes.triangle(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y);
        shapes.triangle(p0.x, p0.y, p2.x, p2.y, p3.x, p3.y);

        float ax = x - 0.03f, ay = y + 0.02f;
        float bx = x + 0.03f, by = y - 0.02f;
        float lw = Math.max(1f, camZoom * 0.3f);

        Vector2 a0 = toGdxScreen(project(ax, ay, h - 1f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 a1 = toGdxScreen(project(ax, ay, h + up, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        drawThickLine(shapes, a0.x, a0.y, a1.x, a1.y, lw, COLOR_LADDER_RAIL);

        Vector2 b0 = toGdxScreen(project(bx, by, h - 1f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 b1 = toGdxScreen(project(bx, by, h + up, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        drawThickLine(shapes, b0.x, b0.y, b1.x, b1.y, lw, COLOR_LADDER_RAIL);

        for (float z = h; z <= h + up; z += 1.2f) {
            Vector2 r0 = toGdxScreen(project(ax, ay, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 r1 = toGdxScreen(project(bx, by, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            drawThickLine(shapes, r0.x, r0.y, r1.x, r1.y, lw, COLOR_LADDER_RAIL);
        }
    }

    public static void drawScreenTri(ShapeRenderer shapes,
                                    float ax, float ay,
                                    float bx, float by,
                                    float cx, float cy,
                                    Color color) {
        if (shapes == null || color == null) return;
        shapes.setColor(color);
        Vector2 A = toGdxScreen(new Vector2(ax, ay));
        Vector2 B = toGdxScreen(new Vector2(bx, by));
        Vector2 C = toGdxScreen(new Vector2(cx, cy));
        shapes.triangle(A.x, A.y, B.x, B.y, C.x, C.y);
    }

    public static void drawScreenRect(ShapeRenderer shapes, float x, float y, float w, float h, Color color) {
        if (shapes == null || color == null || w <= 0f || h <= 0f) return;
        shapes.setColor(color);
        Vector2 p0 = toGdxScreen(new Vector2(x, y));
        Vector2 p1 = toGdxScreen(new Vector2(x + w, y));
        Vector2 p2 = toGdxScreen(new Vector2(x + w, y + h));
        Vector2 p3 = toGdxScreen(new Vector2(x, y + h));
        shapes.triangle(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y);
        shapes.triangle(p0.x, p0.y, p2.x, p2.y, p3.x, p3.y);
    }

    public static void drawScreenPoly(ShapeRenderer shapes, float[][] pts, Color color) {
        if (shapes == null || color == null || pts == null || pts.length < 3) return;
        shapes.setColor(color);
        Vector2 p0 = toGdxScreen(new Vector2(pts[0][0], pts[0][1]));
        for (int i = 1; i < pts.length - 1; i++) {
            Vector2 p1 = toGdxScreen(new Vector2(pts[i][0], pts[i][1]));
            Vector2 p2 = toGdxScreen(new Vector2(pts[i + 1][0], pts[i + 1][1]));
            shapes.triangle(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y);
        }
    }

    public static void drawScreenLine(ShapeRenderer shapes,
                                     float ax, float ay,
                                     float bx, float by,
                                     float width,
                                     Color color) {
        if (shapes == null || color == null) return;
        shapes.setColor(color);
        Vector2 A = toGdxScreen(new Vector2(ax, ay));
        Vector2 B = toGdxScreen(new Vector2(bx, by));
        shapes.rectLine(A.x, A.y, B.x, B.y, width);
    }

    public static void drawScreenEllipse(ShapeRenderer shapes,
                                         float cx, float cy,
                                         float rx, float ry,
                                         float startAngle, float endAngle,
                                         int segments,
                                         Color color) {
        if (shapes == null || color == null || rx <= 0f || ry <= 0f) return;
        shapes.setColor(color);
        Vector2 center = toGdxScreen(new Vector2(cx, cy));
        float step = (endAngle - startAngle) / segments;
        for (int i = 0; i < segments; i++) {
            float a1 = startAngle + i * step;
            float a2 = startAngle + (i + 1) * step;
            float x1 = cx + (float) Math.cos(a1) * rx;
            float y1 = cy + (float) Math.sin(a1) * ry;
            float x2 = cx + (float) Math.cos(a2) * rx;
            float y2 = cy + (float) Math.sin(a2) * ry;
            Vector2 p1 = toGdxScreen(new Vector2(x1, y1));
            Vector2 p2 = toGdxScreen(new Vector2(x2, y2));
            shapes.triangle(center.x, center.y, p1.x, p1.y, p2.x, p2.y);
        }
    }

    public static void drawPyramid(ShapeRenderer shapes,
                                   float x0, float y0, float x1, float y1,
                                   float z0, float z1,
                                   Color cL, Color cR,
                                   float camX, float camY, float zoom, float rot,
                                   float W, float H) {
        if (shapes == null) return;
        Vector2 ap = toGdxScreen(project((x0 + x1) / 2f, (y0 + y1) / 2f, z1, camX, camY, zoom, rot, W, H));
        float[][] pts = new float[][]{
            {x0, y0},
            {x1, y0},
            {x1, y1},
            {x0, y1}
        };
        float[][] nrm = new float[][]{
            {0f, -1f},
            {1f, 0f},
            {0f, 1f},
            {-1f, 0f}
        };
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);

        class Face {
            int i;
            float f;
            float sh;
        }
        Face[] faces = new Face[4];
        for (int i = 0; i < 4; i++) {
            faces[i] = new Face();
            faces[i].i = i;
            float nx = nrm[i][0];
            float ny = nrm[i][1];
            float rx = nx * cs - ny * sn;
            float ry = nx * sn + ny * cs;
            faces[i].f = rx + ry;
            faces[i].sh = rx - ry;
        }
        Arrays.sort(faces, (a, b) -> Float.compare(a.f, b.f));

        for (Face fc : faces) {
            float[] a = pts[fc.i];
            float[] b = pts[(fc.i + 1) % 4];
            Vector2 A = toGdxScreen(project(a[0], a[1], z0, camX, camY, zoom, rot, W, H));
            Vector2 B = toGdxScreen(project(b[0], b[1], z0, camX, camY, zoom, rot, W, H));

            Color col = (fc.f < 0f) ? shade(cR, -0.25f) : ((fc.sh < 0f) ? cL : cR);
            shapes.setColor(col);
            shapes.triangle(A.x, A.y, B.x, B.y, ap.x, ap.y);
        }
    }

    public void drawShelter(ShapeRenderer shapes, int cx, int cy, float h, MapModel.ShelterMark mark) {
        if (shapes == null || mark == null) return;
        float mx = cx + 0.5f;
        float my = cy + 0.5f;
        float r = (mark == MapModel.ShelterMark.HOME) ? 0.13f : 0.11f;
        boolean cold = (mark == MapModel.ShelterMark.COLD);

        Color sideA = cold ? COLOR_SHELTER_COLD_A : COLOR_SHELTER_LIT_A;
        Color sideB = cold ? COLOR_SHELTER_COLD_B : COLOR_SHELTER_LIT_B;
        drawBox(shapes, mx - r, my - r, mx + r, my + r, h, h + 3.4f, null, sideA, sideB);

        Color roofL = cold ? COLOR_ROOF_COLD_L : COLOR_ROOF_LIT_L;
        Color roofR = cold ? COLOR_ROOF_COLD_R : COLOR_ROOF_LIT_R;
        drawPyramid(shapes, mx - r - 0.025f, my - r - 0.025f, mx + r + 0.025f, my + r + 0.025f,
                h + 3.4f, h + 6.6f, roofL, roofR, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);

        if (!cold) {
            float cs = (float) Math.cos(camRot);
            float sn = (float) Math.sin(camRot);
            float[][] nrm = new float[][]{{0f, -1f}, {1f, 0f}, {0f, 1f}, {-1f, 0f}};
            float kk = Math.max(1f, Math.round(camZoom * 0.6f));
            for (float[] n : nrm) {
                float rx = n[0] * cs - n[1] * sn;
                float ry = n[0] * sn + n[1] * cs;
                if (rx + ry <= 0.2f) continue;
                Vector2 p = project(mx + n[0] * r, my + n[1] * r, h + 1.7f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                drawScreenRect(shapes, p.x - kk / 2f, p.y - kk, kk, Math.max(1f, Math.round(kk * 1.4f)), COLOR_WINDOW_LIT);
            }
        }

        if (mark == MapModel.ShelterMark.HOME) {
            Vector2 a = project(mx + r, my - r, h + 3.4f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            Vector2 b = project(mx + r, my - r, h + 12.0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            drawScreenLine(shapes, a.x, a.y, b.x, b.y, Math.max(1f, camZoom * 0.35f), COLOR_FLAG_POLE);

            float k = camZoom;
            float[][] banner = new float[][]{
                {b.x, b.y},
                {b.x + 4f * k, b.y + 1f * k},
                {b.x, b.y + 2.4f * k}
            };
            drawScreenPoly(shapes, banner, COLOR_FLAG_GOLD);
        }

        if (cold) {
            Vector2 a = project(mx - r * 0.5f, my - r * 0.5f, h + 6.6f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            drawScreenRect(shapes, a.x - camZoom * 0.4f, a.y - camZoom * 2f, Math.max(1f, camZoom * 0.8f), camZoom * 2f, COLOR_CHIMNEY);
        }
    }

    public void drawFire(ShapeRenderer shapes, float wx, float wy, float h) {
        float kk = Math.max(1f, camZoom * 0.45f);
        Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        drawScreenRect(shapes, q.x - 2f * kk, q.y - 0.5f * kk, kk, kk, COLOR_FIRE_STONE);
        drawScreenRect(shapes, q.x + kk, q.y - 0.5f * kk, kk, kk, COLOR_FIRE_STONE);
        drawScreenRect(shapes, q.x - 0.5f * kk, q.y + 0.3f * kk, kk, kk, COLOR_FIRE_STONE);
        drawScreenRect(shapes, q.x - 1.5f * kk, q.y - kk, 3f * kk, 0.7f * kk, COLOR_FIRE_WOOD);
    }

    public void drawGrave(ShapeRenderer shapes, float wx, float wy, float h) {
        float hw = 28f * camZoom;
        float hh = 14f * camZoom;
        Vector2 m = project(wx, wy + 0.045f, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        drawScreenEllipse(shapes, m.x, m.y, 0.06f * hw * 1.4f, 0.035f * hh * 2.4f,
                0f, (float) (Math.PI * 2), 16, COLOR_GRAVE_MOUND);

        drawBox(shapes, wx - 0.03f, wy - 0.012f, wx + 0.03f, wy + 0.012f,
                h, h + 2.8f, COLOR_GRAVE_STONE_TOP, COLOR_GRAVE_STONE_LIT, COLOR_GRAVE_STONE_SHADE);

        Vector2 t = project(wx, wy, h + 2.1f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        drawScreenRect(shapes, Math.round(t.x), Math.round(t.y - camZoom * 0.6f),
                Math.max(1f, Math.round(camZoom * 0.25f)), Math.max(1f, Math.round(camZoom * 1.2f)),
                COLOR_GRAVE_CROSS);
    }

    public void drawPortal(ShapeRenderer shapes, float wx, float wy, float h) {
        drawBox(shapes, wx - 0.1f, wy - 0.025f, wx - 0.055f, wy + 0.025f,
                h, h + 5.4f, COLOR_PORTAL_POST_TOP, COLOR_PORTAL_LIT, COLOR_PORTAL_SHADE);
        drawBox(shapes, wx + 0.055f, wy - 0.025f, wx + 0.1f, wy + 0.025f,
                h, h + 5.4f, COLOR_PORTAL_POST_TOP, COLOR_PORTAL_LIT, COLOR_PORTAL_SHADE);
        drawBox(shapes, wx - 0.11f, wy - 0.03f, wx + 0.11f, wy + 0.03f,
                h + 5.4f, h + 6.6f, COLOR_PORTAL_TOP, COLOR_PORTAL_LIT, COLOR_PORTAL_SHADE);
    }

    public void drawSeal(ShapeRenderer shapes, float wx, float wy, float h, boolean sil) {
        Color top = sil ? COLOR_SIL_LIT : COLOR_SEAL_STONE_TOP;
        Color lit = sil ? COLOR_SIL_LIT : COLOR_SEAL_STONE_LIT;
        Color shade = sil ? COLOR_SIL_DARK : COLOR_SEAL_STONE_SHADE;

        float[][] stones = new float[6][2];
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3.0 + 0.3);
            stones[i][0] = wx + (float) Math.cos(a) * 0.22f;
            stones[i][1] = wy + (float) Math.sin(a) * 0.22f;
        }

        Arrays.sort(stones, (a, b) -> Float.compare(depth(a[0], a[1]), depth(b[0], b[1])));
        float centerDepth = depth(wx, wy);

        for (float[] p : stones) {
            if (depth(p[0], p[1]) < centerDepth) {
                drawBox(shapes, p[0] - 0.025f, p[1] - 0.025f, p[0] + 0.025f, p[1] + 0.025f,
                        h, h + 3.4f, top, lit, shade);
            }
        }

        Color altarTop = sil ? COLOR_SIL_LIT : COLOR_SEAL_ALTAR_TOP;
        drawBox(shapes, wx - 0.06f, wy - 0.06f, wx + 0.06f, wy + 0.06f,
                h, h + 1.4f, altarTop, lit, shade);

        for (float[] p : stones) {
            if (depth(p[0], p[1]) >= centerDepth) {
                drawBox(shapes, p[0] - 0.025f, p[1] - 0.025f, p[0] + 0.025f, p[1] + 0.025f,
                        h, h + 3.4f, top, lit, shade);
            }
        }
    }

    public void drawLadder(ShapeRenderer shapes, float wx, float wy, float h, float up) {
        float hz = 1.0f / 16.0f;
        float[][] hole = new float[][]{
            {wx - hz, wy - hz},
            {wx + hz, wy - hz},
            {wx + hz, wy + hz},
            {wx - hz, wy + hz}
        };
        Vector2 q0 = toGdxScreen(project(hole[0][0], hole[0][1], h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 q1 = toGdxScreen(project(hole[1][0], hole[1][1], h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 q2 = toGdxScreen(project(hole[2][0], hole[2][1], h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 q3 = toGdxScreen(project(hole[3][0], hole[3][1], h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        shapes.setColor(COLOR_LADDER_HOLE);
        shapes.triangle(q0.x, q0.y, q1.x, q1.y, q2.x, q2.y);
        shapes.triangle(q0.x, q0.y, q2.x, q2.y, q3.x, q3.y);

        float[] a = new float[]{wx - 0.03f, wy + 0.02f};
        float[] b = new float[]{wx + 0.03f, wy - 0.02f};
        float lineWidth = Math.max(1f, camZoom * 0.3f);

        Vector2 a0 = project(a[0], a[1], h - 1f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        Vector2 a1 = project(a[0], a[1], h + up, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        drawScreenLine(shapes, a0.x, a0.y, a1.x, a1.y, lineWidth, COLOR_LADDER_WOOD);

        Vector2 b0 = project(b[0], b[1], h - 1f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        Vector2 b1 = project(b[0], b[1], h + up, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        drawScreenLine(shapes, b0.x, b0.y, b1.x, b1.y, lineWidth, COLOR_LADDER_WOOD);

        for (float z = h; z <= h + up; z += 1.2f) {
            Vector2 r0 = project(a[0], a[1], z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            Vector2 r1 = project(b[0], b[1], z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            drawScreenLine(shapes, r0.x, r0.y, r1.x, r1.y, lineWidth, COLOR_LADDER_WOOD);
        }
    }

    public void drawChest(ShapeRenderer shapes, float wx, float wy, float h) {
        drawBox(shapes, wx - 0.03f, wy - 0.022f, wx + 0.03f, wy + 0.022f,
                h, h + 1.6f, COLOR_CHEST_TOP, COLOR_CHEST_LIT, COLOR_CHEST_SHADE);
    }

    public void drawPlayer(ShapeRenderer shapes, float wx, float wy, float h, float faceX, float faceY) {
        float kk = camZoom * 0.75f;
        Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        Vector2 f = project(wx + faceX * 0.07f, wy + faceY * 0.07f, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);

        drawScreenLine(shapes, q.x, q.y, f.x, f.y, Math.max(1f, kk * 0.4f), COLOR_PLAYER_CYAN);
        drawScreenTri(shapes, q.x - 2.2f * kk, q.y, q.x, q.y, q.x, q.y - 6.5f * kk, COLOR_PLAYER_CYAN);
        drawScreenTri(shapes, q.x, q.y, q.x + 2.2f * kk, q.y, q.x, q.y - 6.5f * kk, COLOR_PLAYER_CYAN_DARK);
        drawScreenRect(shapes, q.x - kk, q.y - 8.6f * kk, 2f * kk, 2f * kk, COLOR_PLAYER_HEAD);
    }

    public void drawProp(ShapeRenderer shapes, Prop p, int cx, int cy, float h, boolean sil) {
        if (shapes == null || p == null) return;
        float wx = cx + p.u;
        float wy = cy + p.v;
        float s = camZoom;
        float k = s * 0.8f * (p.sz > 0f ? p.sz : 1f);
        float hw = 28f * s;
        float hh = 14f * s;

        switch (p.kind) {
            case SHELTER:
                drawShelter(shapes, cx, cy, h, p.shelterType);
                return;
            case FIRE:
                drawFire(shapes, wx, wy, h);
                return;
            case GRAVE:
                drawGrave(shapes, wx, wy, h);
                return;
            case PORTAL:
                drawPortal(shapes, wx, wy, h);
                return;
            case SEAL:
                drawSeal(shapes, wx, wy, h, sil);
                return;
            case LADDER:
                drawLadder(shapes, wx, wy, h, 3.5f);
                return;
            case CHEST:
                drawChest(shapes, wx, wy, h);
                return;
            case PLAYER:
                drawPlayer(shapes, wx, wy, h, p.faceX, p.faceY);
                return;
            case WALLSTUB:
                drawBox(shapes, wx - 0.07f, wy - 0.025f, wx + 0.07f, wy + 0.025f,
                        h, h + 2.2f, COLOR_SIL_LIT, COLOR_SIL_LIT, COLOR_SIL_DARK);
                return;
            case POOL: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                drawScreenEllipse(shapes, q.x, q.y, 0.28f * hw * 1.4f, 0.28f * hh * 1.4f,
                        0f, (float) (Math.PI * 2), 16, dark(COLOR_POOL, sil));
                return;
            }
            case PINE: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenRect(shapes, x - 0.5f * k, y - 2f * k, k, 2f * k, dark(COLOR_PINE_TRUNK, sil));
                float top = 0f;
                for (int i = 0; i < 3; i++) {
                    float w = (3.4f - i * 0.85f) * k;
                    float yb = y - 1.6f * k - i * 2.3f * k;
                    float ht = 3.6f * k;
                    top = yb - ht;
                    Color cL = p.snow ? COLOR_PINE_SNOW_LIT : COLOR_PINE_LIT;
                    Color cR = p.snow ? COLOR_PINE_SNOW_DARK : COLOR_PINE_DARK;
                    drawScreenTri(shapes, x - w, yb, x, yb, x, yb - ht, lit(cL, sil));
                    drawScreenTri(shapes, x, yb, x + w, yb, x, yb - ht, dark(cR, sil));
                }
                if (p.snow && !sil) {
                    drawScreenTri(shapes, x - 1.1f * k, top + 1.8f * k, x + 1.1f * k, top + 1.8f * k, x, top, COLOR_PINE_SNOW_CAP);
                }
                return;
            }
            case CRAG: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                float v = p.va > 0f ? p.va : 0.5f;
                float hgt = (8f + 7f * v) * k;
                float w = (4.2f + 3f * v) * k;
                float[] ap = new float[]{x + 0.1f * w, y - hgt};
                float[] lf = new float[]{x - w, y};
                float[] fr = new float[]{x + 0.15f * w, y + 0.2f * w};
                float[] rt = new float[]{x + w, y - 0.1f * w};
                drawScreenPoly(shapes, new float[][]{lf, fr, ap}, lit(COLOR_CRAG_LIT, sil));
                drawScreenPoly(shapes, new float[][]{fr, rt, ap}, dark(COLOR_CRAG_DARK, sil));
                if (!sil) {
                    float f = 0.32f;
                    float[] lp1 = new float[]{ap[0] + (lf[0] - ap[0]) * f, ap[1] + (lf[1] - ap[1]) * f};
                    float[] lp2 = new float[]{ap[0] + (fr[0] - ap[0]) * f, ap[1] + (fr[1] - ap[1]) * f};
                    float[] lp3 = new float[]{ap[0] + (rt[0] - ap[0]) * f, ap[1] + (rt[1] - ap[1]) * f};
                    drawScreenPoly(shapes, new float[][]{lp1, lp2, ap}, COLOR_CRAG_SNOW_LIT);
                    drawScreenPoly(shapes, new float[][]{lp2, lp3, ap}, COLOR_CRAG_SNOW_DARK);
                }
                return;
            }
            case DUNE: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenEllipse(shapes, x + 1.2f * k, y, 5f * k, 2.2f * k,
                        (float) Math.PI, (float) (Math.PI * 2), 16, dark(COLOR_DUNE_SHADOW, sil));
                drawScreenEllipse(shapes, x - 0.6f * k, y, 4.2f * k, 2f * k,
                        (float) Math.PI, (float) (Math.PI * 2), 16, lit(COLOR_DUNE_LIT, sil));
                return;
            }
            case CACTUS: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                Color cL = lit(COLOR_CACTUS_LIT, sil);
                drawScreenRect(shapes, x - 0.6f * k, y - 6f * k, 1.2f * k, 6f * k, cL);
                drawScreenRect(shapes, x - 2.4f * k, y - 4f * k, 1.8f * k, 0.9f * k, cL);
                drawScreenRect(shapes, x - 2.4f * k, y - 5.6f * k, 0.9f * k, 2.4f * k, cL);

                Color cD = dark(COLOR_CACTUS_DARK, sil);
                drawScreenRect(shapes, x, y - 6f * k, 0.6f * k, 6f * k, cD);
                drawScreenRect(shapes, x + 0.6f * k, y - 3.2f * k, 1.6f * k, 0.9f * k, cD);
                drawScreenRect(shapes, x + 1.5f * k, y - 4.8f * k, 0.9f * k, 2.4f * k, cD);
                return;
            }
            case HOODOO: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenRect(shapes, x - 1.6f * k, y - 3f * k, 3.2f * k, 3f * k, lit(COLOR_HOODOO_BASE_LIT, sil));
                drawScreenRect(shapes, x - 1.1f * k, y - 6.4f * k, 2.2f * k, 3.4f * k, lit(COLOR_HOODOO_SHAFT_LIT, sil));
                drawScreenRect(shapes, x, y - 3f * k, 1.6f * k, 3f * k, dark(COLOR_HOODOO_SHADE, sil));
                drawScreenRect(shapes, x, y - 6.4f * k, 1.1f * k, 3.4f * k, dark(COLOR_HOODOO_SHADE, sil));
                drawScreenRect(shapes, x - 1.7f * k, y - 7.5f * k, 3.4f * k, 1.2f * k, dark(COLOR_HOODOO_CAP, sil));
                return;
            }
            case ARCH: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                Color cL = lit(COLOR_ARCH_LIT, sil);
                drawScreenRect(shapes, x - 4.5f * k, y - 7f * k, 1.8f * k, 7f * k, cL);
                drawScreenRect(shapes, x + 2.7f * k, y - 7f * k, 1.8f * k, 7f * k, cL);
                drawScreenRect(shapes, x - 4.5f * k, y - 9f * k, 9f * k, 2.2f * k, cL);

                Color cD = dark(COLOR_ARCH_DARK, sil);
                drawScreenRect(shapes, x - 2.7f * k, y - 7f * k, 0.6f * k, 7f * k, cD);
                drawScreenRect(shapes, x + 4.5f * k - 0.6f * k, y - 7f * k, 0.6f * k, 7f * k, cD);
                drawScreenRect(shapes, x - 4.5f * k, y - 7f * k, 9f * k, 0.6f * k, cD);
                return;
            }
            case BONES: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                float kk = Math.max(1f, k * 0.6f);
                Color col = lit(COLOR_BONES, sil);
                drawScreenRect(shapes, x - 2f * kk, y - kk, 1.6f * kk, 1.4f * kk, col);
                for (int i = 0; i < 3; i++) {
                    drawScreenRect(shapes, x + i * 1.2f * kk, y - 1.2f * kk, 0.5f * kk, 1.8f * kk, col);
                }
                drawScreenRect(shapes, x, y - 0.2f * kk, 3.2f * kk, 0.5f * kk, col);
                return;
            }
            case REEDS: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                for (int i = 0; i < 4; i++) {
                    float hh_reed = (3f + (i % 2) * 2f) * k;
                    drawScreenRect(shapes, x - 1.5f * k + i * k, y - hh_reed, Math.max(1f, 0.4f * k), hh_reed, lit(COLOR_REEDS_LIT, sil));
                    drawScreenRect(shapes, x - 1.5f * k + i * k, y - hh_reed, Math.max(1f, 0.4f * k), Math.max(1f, 0.9f * k), dark(COLOR_REEDS_DARK, sil));
                }
                return;
            }
            case TUFT: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                Color col = lit(COLOR_TUFT, sil);
                drawScreenRect(shapes, x - k, y - 1.4f * k, Math.max(1f, 0.4f * k), 1.4f * k, col);
                drawScreenRect(shapes, x, y - 1.9f * k, Math.max(1f, 0.4f * k), 1.9f * k, col);
                drawScreenRect(shapes, x + k, y - 1.2f * k, Math.max(1f, 0.4f * k), 1.2f * k, col);
                return;
            }
            case BOAT: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenPoly(shapes, new float[][]{
                    {x - 5f * k, y - 1.5f * k},
                    {x + 5f * k, y - 2.6f * k},
                    {x + 3.4f * k, y + 0.8f * k},
                    {x - 3.6f * k, y + 1.2f * k}
                }, lit(COLOR_BOAT_HULL, sil));
                drawScreenPoly(shapes, new float[][]{
                    {x - 3.6f * k, y + 1.2f * k},
                    {x + 3.4f * k, y + 0.8f * k},
                    {x + 2.6f * k, y + 1.8f * k},
                    {x - 3f * k, y + 2f * k}
                }, dark(COLOR_BOAT_SHADE, sil));
                Color cRibs = dark(COLOR_BOAT_RIBS, sil);
                for (int i = 0; i < 3; i++) {
                    drawScreenRect(shapes, x - 2.4f * k + i * 2.2f * k, y - 2f * k, Math.max(1f, 0.4f * k), 2.6f * k, cRibs);
                }
                drawScreenPoly(shapes, new float[][]{
                    {x + 0.6f * k, y - 1.6f * k},
                    {x + 1.4f * k, y - 1.6f * k},
                    {x + 3.4f * k, y - 9f * k},
                    {x + 2.6f * k, y - 9f * k}
                }, lit(COLOR_BOAT_MAST, sil));
                return;
            }
            case MOUND: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenEllipse(shapes, x + 0.8f * k, y, 4f * k, 1.8f * k,
                        (float) Math.PI, (float) (Math.PI * 2), 16, dark(COLOR_MOUND_SHADOW, sil));
                drawScreenEllipse(shapes, x - 0.4f * k, y, 3.4f * k, 1.6f * k,
                        (float) Math.PI, (float) (Math.PI * 2), 16, lit(COLOR_MOUND_LIT, sil));
                return;
            }
            case ICE: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenTri(shapes, x - 1.6f * k, y, x, y, x + 0.2f * k, y - 6f * k, lit(COLOR_ICE_LIT, sil));
                drawScreenTri(shapes, x, y, x + 1.4f * k, y, x + 0.2f * k, y - 6f * k, dark(COLOR_ICE_DARK, sil));
                return;
            }
            case DEADTREE: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                Color col = dark(COLOR_DEADTREE, sil);
                drawScreenRect(shapes, x - 0.5f * k, y - 7f * k, k, 7f * k, col);
                float lw = Math.max(1f, 0.5f * k);
                drawScreenLine(shapes, x, y - 5f * k, x - 2.6f * k, y - 7.6f * k, lw, col);
                drawScreenLine(shapes, x, y - 4f * k, x + 2.4f * k, y - 6.4f * k, lw, col);
                drawScreenLine(shapes, x, y - 6.5f * k, x + 1.2f * k, y - 8.6f * k, lw, col);
                return;
            }
            case CRACK: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                float v = p.va > 0f ? p.va : 0.5f;
                Color col = sil ? COLOR_SIL_DARK : COLOR_CRACK_RED;
                float lw = Math.max(1f, 0.45f * k);
                drawScreenLine(shapes, x - 3f * k, y - 0.4f * k, x - 1f * k, y + 0.6f * k * v, lw, col);
                drawScreenLine(shapes, x - 1f * k, y + 0.6f * k * v, x + 0.8f * k, y - 0.6f * k, lw, col);
                drawScreenLine(shapes, x + 0.8f * k, y - 0.6f * k, x + 3f * k, y + 0.5f * k, lw, col);
                drawScreenLine(shapes, x - 1f * k, y + 0.6f * k * v, x - 1.4f * k, y + 1.6f * k, lw, col);
                return;
            }
            case PUSTULE: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenEllipse(shapes, x, y - 0.8f * k, 1.4f * k, 1f * k,
                        0f, (float) (Math.PI * 2), 16, dark(COLOR_PUSTULE_BULB, sil));
                if (!sil) {
                    float sz = Math.max(1f, 0.5f * k);
                    drawScreenRect(shapes, x - 0.6f * k, y - 1.4f * k, sz, sz, COLOR_PUSTULE_HI);
                }
                return;
            }
            case RUBBLE: {
                Vector2 q = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float x = q.x, y = q.y;
                drawScreenRect(shapes, x - k, y - 0.8f * k, 1.2f * k, 0.8f * k, COLOR_RUBBLE_LIT);
                drawScreenRect(shapes, x + 0.4f * k, y - 0.6f * k, k, 0.6f * k, COLOR_RUBBLE_DARK);
                return;
            }
        }
    }

    private static void addRandomProps(List<Prop> out, Prop.Kind kind, int n, Mulberry32 rng, boolean snow) {
        for (int i = 0; i < n; i++) {
            Prop p = new Prop();
            p.kind = kind;
            p.u = 0.15f + 0.7f * rng.nextFloat();
            p.v = 0.15f + 0.7f * rng.nextFloat();
            p.sz = 0.8f + 0.4f * rng.nextFloat();
            p.va = rng.nextFloat();
            p.snow = snow;
            out.add(p);
        }
    }

    public static void generateGlimpsedProps(int cx, int cy, Biome biome, boolean hasSeal, List<Prop> out) {
        Mulberry32 rng = new Mulberry32(hash(cx, cy) + 7);
        if (biome == null) biome = Biome.MAZE;
        switch (biome) {
            case FOREST:
                addRandomProps(out, Prop.Kind.PINE, 7, rng, false);
                break;
            case MOUNTAINS:
                addRandomProps(out, Prop.Kind.CRAG, 3, rng, false);
                break;
            case DESERT:
                addRandomProps(out, Prop.Kind.DUNE, 3, rng, false);
                addRandomProps(out, Prop.Kind.HOODOO, 1, rng, false);
                break;
            case LAKELANDS: {
                Prop pool = new Prop();
                pool.kind = Prop.Kind.POOL;
                pool.u = 0.55f;
                pool.v = 0.55f;
                out.add(pool);
                addRandomProps(out, Prop.Kind.REEDS, 3, rng, false);
                break;
            }
            case TUNDRA:
                addRandomProps(out, Prop.Kind.MOUND, 3, rng, false);
                addRandomProps(out, Prop.Kind.ICE, 2, rng, false);
                addRandomProps(out, Prop.Kind.PINE, 2, rng, true);
                break;
            case BLIGHT:
                addRandomProps(out, Prop.Kind.DEADTREE, 3, rng, false);
                addRandomProps(out, Prop.Kind.CRACK, 2, rng, false);
                break;
            case MAZE:
                addRandomProps(out, Prop.Kind.WALLSTUB, 5, rng, false);
                break;
            case OCEAN:
            default:
                break;
        }
        if (hasSeal) {
            Prop seal = new Prop();
            seal.kind = Prop.Kind.SEAL;
            seal.u = 0.5f;
            seal.v = 0.5f;
            out.add(seal);
        }
    }

    public static void generateVisitedProps(int cx, int cy, Biome biome, ChunkTiles tiles,
                                            MapKnowledgeMask.KnowledgeProvider provider,
                                            List<Prop> out) {
        Mulberry32 rng = new Mulberry32(hash(cx, cy));
        if (biome == null) biome = Biome.MAZE;

        MapModel.ShelterMark shelter = (provider != null) ? provider.getShelter(cx, cy) : null;
        boolean portal = (provider != null) && provider.hasReturnPortal(cx, cy);
        boolean ladder = (provider != null) && provider.hasLadder(cx, cy);
        int graves = (provider != null) ? provider.getGraveCount(cx, cy) : 0;
        int chests = (provider != null) ? provider.getChestCount(cx, cy) : 0;
        boolean feat = (shelter != null || portal || ladder || graves > 0);

        if (tiles != null && tiles.width > 0 && tiles.height > 0) {
            for (int ty = 0; ty < 8; ty++) {
                for (int tx = 0; tx < 8; tx++) {
                    if (!tiles.isSeen(tx, ty) || tiles.isSolid(tx, ty)) continue;

                    float u = (tx + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                    float v = (ty + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                    float roll = rng.nextFloat();

                    boolean nearFeat = feat && Math.abs(u - 0.5f) < 0.2f && Math.abs(v - 0.5f) < 0.2f;
                    if (biome != Biome.MAZE && nearFeat) continue;

                    switch (biome) {
                        case FOREST: {
                            Prop p = new Prop();
                            p.kind = Prop.Kind.PINE;
                            p.u = u; p.v = v; p.sz = 0.8f + 0.45f * rng.nextFloat();
                            out.add(p);
                            if (roll < 0.55f) {
                                float u2 = (tx + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                                float v2 = (ty + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                                if (!feat || Math.abs(u2 - 0.5f) >= 0.2f || Math.abs(v2 - 0.5f) >= 0.2f) {
                                    Prop p2 = new Prop();
                                    p2.kind = Prop.Kind.PINE;
                                    p2.u = u2; p2.v = v2; p2.sz = 0.7f + 0.4f * rng.nextFloat();
                                    out.add(p2);
                                }
                            }
                            break;
                        }
                        case MOUNTAINS: {
                            if (roll < 0.72f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.CRAG;
                                p.u = u; p.v = v; p.sz = 0.7f + 0.5f * rng.nextFloat(); p.va = rng.nextFloat();
                                out.add(p);
                            }
                            break;
                        }
                        case DESERT: {
                            if (roll < 0.32f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.DUNE;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.5f * rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.40f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.CACTUS;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.3f * rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.46f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.HOODOO;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.4f * rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.51f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.BONES;
                                p.u = u; p.v = v; p.sz = 1.0f;
                                out.add(p);
                            }
                            break;
                        }
                        case BLIGHT: {
                            if (roll < 0.50f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.CRACK;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.5f * rng.nextFloat(); p.va = rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.66f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.DEADTREE;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.4f * rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.76f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.PUSTULE;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.4f * rng.nextFloat();
                                out.add(p);
                            }
                            break;
                        }
                        case LAKELANDS: {
                            if (roll < 0.20f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.PINE;
                                p.u = u; p.v = v; p.sz = 0.6f + 0.3f * rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.40f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.TUFT;
                                p.u = u; p.v = v; p.sz = 1.0f;
                                out.add(p);
                            } else if (roll < 0.80f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.REEDS;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.4f * rng.nextFloat();
                                out.add(p);
                            }
                            break;
                        }
                        case TUNDRA: {
                            if (roll < 0.35f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.MOUND;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.5f * rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.60f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.ICE;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.4f * rng.nextFloat();
                                out.add(p);
                            } else if (roll < 0.80f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.PINE;
                                p.u = u; p.v = v; p.sz = 0.8f + 0.4f * rng.nextFloat(); p.snow = true;
                                out.add(p);
                            }
                            break;
                        }
                        case MAZE: {
                            if (roll < 0.06f) {
                                Prop p = new Prop();
                                p.kind = Prop.Kind.RUBBLE;
                                p.u = u; p.v = v; p.sz = 1.0f;
                                out.add(p);
                            }
                            break;
                        }
                        case OCEAN:
                        default:
                            break;
                    }
                }
            }
        }

        if (biome == Biome.DESERT && rng.nextFloat() < 0.7f) {
            Prop arch = new Prop();
            arch.kind = Prop.Kind.ARCH;
            arch.u = 0.25f + 0.5f * rng.nextFloat();
            arch.v = 0.25f + 0.5f * rng.nextFloat();
            out.add(arch);
        } else if (biome == Biome.LAKELANDS && rng.nextFloat() < 0.7f) {
            Prop boat = new Prop();
            boat.kind = Prop.Kind.BOAT;
            boat.u = 0.25f + 0.5f * rng.nextFloat();
            boat.v = 0.25f + 0.5f * rng.nextFloat();
            out.add(boat);
        }

        if (shelter != null) {
            Prop s = new Prop();
            s.kind = Prop.Kind.SHELTER;
            s.u = 0.5f; s.v = 0.5f;
            s.shelterType = shelter;
            out.add(s);
            if (shelter != MapModel.ShelterMark.COLD) {
                Prop fire = new Prop();
                fire.kind = Prop.Kind.FIRE;
                fire.u = 0.68f; fire.v = 0.66f;
                out.add(fire);
            }
        }
        if (portal) {
            Prop port = new Prop();
            port.kind = Prop.Kind.PORTAL;
            port.u = 0.5f; port.v = 0.5f;
            out.add(port);
        }
        for (int i = 0; i < graves; i++) {
            Prop g = new Prop();
            g.kind = Prop.Kind.GRAVE;
            float u0 = (biome == Biome.LAKELANDS) ? 0.3f : 0.44f;
            float v0 = (biome == Biome.LAKELANDS) ? 0.3f : 0.42f;
            g.u = u0 + i * 0.1f;
            g.v = v0;
            out.add(g);
        }
        if (ladder) {
            Prop lad = new Prop();
            lad.kind = Prop.Kind.LADDER;
            lad.u = 4.5f / 8.0f;
            lad.v = 4.5f / 8.0f;
            out.add(lad);
        }
        if (chests > 0) {
            Prop ch = new Prop();
            ch.kind = Prop.Kind.CHEST;
            ch.u = 0.3f + 0.4f * rng.nextFloat();
            ch.v = 0.3f + 0.4f * rng.nextFloat();
            out.add(ch);
        }

        if (provider != null && provider.hasPlayer(cx, cy)) {
            Prop ply = new Prop();
            ply.kind = Prop.Kind.PLAYER;
            ply.u = provider.getPlayerU(cx, cy);
            ply.v = provider.getPlayerV(cx, cy);
            ply.faceX = provider.getPlayerFaceX(cx, cy);
            ply.faceY = provider.getPlayerFaceY(cx, cy);
            out.add(ply);
        }
    }

    private void drawVisitedChunk(int cx, int cy, Biome biome, ChunkTiles tiles, MapKnowledgeMask.KnowledgeProvider provider) {
        BiomeColors B = getBiomeColors(biome);
        float h = (biome == Biome.OCEAN) ? 2.0f : 6.0f;
        if (isCave) h = 5.0f;

        // Base mesa block from z=0 to h
        drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 0f, h, null, B.sideA, B.sideB);

        // Floor quads on top of mesa
        if (tiles != null && tiles.width > 0 && tiles.height > 0) {
            for (int ty = 0; ty < 8; ty++) {
                for (int tx = 0; tx < 8; tx++) {
                    boolean seen = tiles.isSeen(tx, ty);
                    boolean solid = tiles.isSolid(tx, ty);

                    Color tileColor;
                    if (seen) {
                        tileColor = solid ? COLOR_WALL_FOOTPRINT : B.top;
                    } else {
                        tileColor = mix(B.top, COLOR_UNKNOWN_DARK, 0.45f);
                    }

                    float x0 = cx + tx / 8.0f;
                    float y0 = cy + ty / 8.0f;
                    float x1 = cx + (tx + 1) / 8.0f;
                    float y1 = cy + (ty + 1) / 8.0f;

                    Vector2 t0 = toGdxScreen(project(x0, y0, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                    Vector2 t1 = toGdxScreen(project(x1, y0, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                    Vector2 t2 = toGdxScreen(project(x1, y1, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                    Vector2 t3 = toGdxScreen(project(x0, y1, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

                    shapeRenderer.setColor(tileColor);
                    shapeRenderer.triangle(t0.x, t0.y, t1.x, t1.y, t2.x, t2.y);
                    shapeRenderer.triangle(t0.x, t0.y, t2.x, t2.y, t3.x, t3.y);
                }
            }
        } else {
            // No per-tile data: draw single top quad
            Vector2 t0 = toGdxScreen(project(cx, cy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 t1 = toGdxScreen(project(cx + 1, cy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 t2 = toGdxScreen(project(cx + 1, cy + 1, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 t3 = toGdxScreen(project(cx, cy + 1, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

            shapeRenderer.setColor(B.top);
            shapeRenderer.triangle(t0.x, t0.y, t1.x, t1.y, t2.x, t2.y);
            shapeRenderer.triangle(t0.x, t0.y, t2.x, t2.y, t3.x, t3.y);
        }

        renderItems.clear();

        // Maze wall boxes (+4.2 height)
        if (tiles != null && (biome == Biome.MAZE || isCave)) {
            for (int ty = 0; ty < 8; ty++) {
                for (int tx = 0; tx < 8; tx++) {
                    if (tiles.isSolid(tx, ty) && tiles.isSeen(tx, ty)) {
                        WallBox w = new WallBox();
                        w.x0 = cx + tx / 8.0f;
                        w.y0 = cy + ty / 8.0f;
                        w.x1 = w.x0 + 1.0f / 8.0f;
                        w.y1 = w.y0 + 1.0f / 8.0f;
                        w.z0 = h;
                        w.z1 = h + 4.2f;
                        w.depth = depth(w.x0 + 1.0f / 16.0f, w.y0 + 1.0f / 16.0f);

                        RenderItem item = new RenderItem();
                        item.depth = w.depth;
                        item.isWall = true;
                        item.wall = w;
                        renderItems.add(item);
                    }
                }
            }
        }

        // Visited props and landmarks
        chunkProps.clear();
        generateVisitedProps(cx, cy, biome, tiles, provider, chunkProps);
        for (Prop p : chunkProps) {
            if ((p.kind == Prop.Kind.CHEST || p.kind == Prop.Kind.RUBBLE) && camZoom < 1.6f) continue;
            RenderItem item = new RenderItem();
            item.depth = depth(cx + p.u, cy + p.v) + (p.kind == Prop.Kind.LADDER ? -0.05f : 0f);
            item.isWall = false;
            item.prop = p;
            renderItems.add(item);
        }

        renderItems.sort(null);
        for (RenderItem item : renderItems) {
            if (item.isWall) {
                drawBox(shapeRenderer, item.wall.x0, item.wall.y0, item.wall.x1, item.wall.y1,
                        item.wall.z0, item.wall.z1, COLOR_WALL_TOP, COLOR_WALL_LIT, COLOR_WALL_SHADE);
            } else {
                drawProp(shapeRenderer, item.prop, cx, cy, h, false);
            }
        }
    }

    private void drawGlimpsedChunk(int cx, int cy, Biome biome, MapKnowledgeMask.KnowledgeProvider provider) {
        BiomeColors B = getBiomeColors(biome);
        float h = (biome == Biome.OCEAN) ? 1.2f : 2.5f;

        Color top = mix(B.top, COLOR_GLIMPSED_TOP_TINT, 0.5f);
        Color sideA = mix(B.sideA, COLOR_GLIMPSED_SIDE_A_TINT, 0.5f);
        Color sideB = mix(B.sideB, COLOR_GLIMPSED_SIDE_B_TINT, 0.5f);

        drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 0f, h, top, sideA, sideB);

        chunkProps.clear();
        boolean hasSeal = (provider != null) && provider.hasSeal(cx, cy);
        generateGlimpsedProps(cx, cy, biome, hasSeal, chunkProps);

        chunkProps.sort((a, b) -> Float.compare(depth(cx + a.u, cy + a.v), depth(cx + b.u, cy + b.v)));
        for (Prop p : chunkProps) {
            drawProp(shapeRenderer, p, cx, cy, h, true);
        }
    }

    public static void drawRectCanvas(ShapeRenderer shapes, float x, float y, float w, float h, Color color) {
        shapes.setColor(color);
        shapes.rect(x, FBO_HEIGHT - y - h, w, h);
    }

    public static float getChunkTopZ(MapKnowledgeMask.KnowledgeProvider provider, int cx, int cy) {
        if (provider == null) return 0f;
        MapKnowledgeMask.KnowledgeProvider.State state = provider.getChunkState(cx, cy);
        Biome b = provider.getBiome(cx, cy);
        boolean isOcean = (b == Biome.OCEAN);
        if (state == MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
            return isOcean ? 2.0f : 6.0f;
        } else if (state == MapKnowledgeMask.KnowledgeProvider.State.GLIMPSED) {
            return isOcean ? 1.2f : 2.5f;
        }
        return 0f;
    }

    public static void drawGlow(ShapeRenderer shapes, float cx, float cy, float r, float red, float green, float blue, float alpha) {
        if (r < 1f || alpha <= 0.001f) return;
        Vector2 g = toGdxScreen(new Vector2(cx, cy));
        Color centerCol = new Color(red, green, blue, alpha);
        Color edgeCol = new Color(red, green, blue, 0f);

        int segments = 16;
        float prevX = g.x + r;
        float prevY = g.y;
        for (int i = 1; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            float currX = g.x + r * (float) Math.cos(angle);
            float currY = g.y + r * (float) Math.sin(angle);
            shapes.triangle(g.x, g.y, prevX, prevY, currX, currY, centerCol, edgeCol, edgeCol);
            prevX = currX;
            prevY = currY;
        }
    }

    public static void drawBeam(ShapeRenderer shapes, float x, float y, float w, float h, float red, float green, float blue, float alpha) {
        if (w < 0.5f || h < 0.5f || alpha <= 0.001f) return;
        Vector2 b0 = toGdxScreen(new Vector2(x - w * 0.5f, y));
        Vector2 b1 = toGdxScreen(new Vector2(x + w * 0.5f, y));
        Vector2 t0 = toGdxScreen(new Vector2(x - w * 0.5f, y - h));
        Vector2 t1 = toGdxScreen(new Vector2(x + w * 0.5f, y - h));

        Color baseCol = new Color(red, green, blue, alpha);
        Color topCol = new Color(red, green, blue, 0f);

        shapes.triangle(b0.x, b0.y, b1.x, b1.y, t1.x, t1.y, baseCol, baseCol, topCol);
        shapes.triangle(b0.x, b0.y, t1.x, t1.y, t0.x, t0.y, baseCol, topCol, topCol);
    }

    public static void drawThickLine(ShapeRenderer shapes, float x0, float y0, float x1, float y1, float width, Color color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) return;
        float nx = -dy / len * (width * 0.5f);
        float ny = dx / len * (width * 0.5f);

        shapes.setColor(color);
        shapes.triangle(x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 + nx, y1 + ny);
        shapes.triangle(x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny);
    }

    public static void drawDashedThickLine(ShapeRenderer shapes, float x0, float y0, float x1, float y1,
                                           float width, Color color, float dashLen, float gapLen, float dashOffset) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.5f) return;
        dx /= len;
        dy /= len;

        float cycle = dashLen + gapLen;
        float offset = dashOffset % cycle;
        if (offset < 0) offset += cycle;

        float d = -offset;
        while (d < len) {
            float start = Math.max(0f, d);
            float end = Math.min(len, d + dashLen);
            if (end > start) {
                float sx = x0 + dx * start;
                float sy = y0 + dy * start;
                float ex = x0 + dx * end;
                float ey = y0 + dy * end;
                drawThickLine(shapes, sx, sy, ex, ey, width, color);
            }
            d += cycle;
        }
    }

    public static void drawArc(ShapeRenderer shapes, float cx, float cy, float rx, float ry, float startAngle, float endAngle, float lineWidth, Color color) {
        int segs = 16;
        float prevX = cx + rx * (float) Math.cos(startAngle);
        float prevY = cy + ry * (float) Math.sin(startAngle);
        for (int i = 1; i <= segs; i++) {
            float a = startAngle + (endAngle - startAngle) * (i / (float) segs);
            float currX = cx + rx * (float) Math.cos(a);
            float currY = cy + ry * (float) Math.sin(a);
            drawThickLine(shapes, prevX, prevY, currX, currY, lineWidth, color);
            prevX = currX;
            prevY = currY;
        }
    }

    public void drawRing(ShapeRenderer shapes, float wx, float wy, float z, float radius, Color color,
                         float lineWidth, float[] dash, float dashOffset) {
        Vector2 p = project(wx, wy, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        float hw = 28f * camZoom;
        float hh = 14f * camZoom;
        float rx = radius * hw * 1.41421356f;
        float ry = radius * hh * 1.41421356f;
        if (rx < 0.5f || ry < 0.5f) return;

        Vector2 cp = toGdxScreen(p);

        int segments = 48;
        float dashLen = (dash != null && dash.length >= 2) ? dash[0] : 0f;
        float gapLen = (dash != null && dash.length >= 2) ? dash[1] : 0f;
        float cycle = dashLen + gapLen;

        float prevX = cp.x + rx;
        float prevY = cp.y;
        float distTraveled = 0f;

        for (int i = 1; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            float currX = cp.x + rx * (float) Math.cos(angle);
            float currY = cp.y + ry * (float) Math.sin(angle);
            float segLen = (float) Math.hypot(currX - prevX, currY - prevY);

            boolean drawSegment = true;
            if (cycle > 0f) {
                float pos = (distTraveled - dashOffset) % cycle;
                if (pos < 0f) pos += cycle;
                drawSegment = (pos < dashLen);
            }

            if (drawSegment) {
                if (lineWidth > 1.5f) {
                    drawThickLine(shapes, prevX, prevY, currX, currY, lineWidth, color);
                } else {
                    drawThickLine(shapes, prevX, prevY, currX, currY, 1f, color);
                }
            }

            distTraveled += segLen;
            prevX = currX;
            prevY = currY;
        }
    }

    public static void drawPlate(ShapeRenderer shapes, float cx, float by, String name, Color bg, Color ink) {
        drawPlate(shapes, cx, by, name, bg, ink, 1.0f);
    }

    public static void drawPlate(ShapeRenderer shapes, float cx, float by, String name, Color bg, Color ink, float alpha) {
        if (shapes == null || alpha <= 0.001f) return;
        int x0 = Math.round(cx) - 5;
        int y0 = Math.round(by) - 14;
        String[] G = GLYPHS.get(name);
        if (G == null) G = GLYPHS.get("unknown");

        Color shadow = new Color(0f, 0f, 0f, 0.5f * alpha);
        Color aInk = new Color(ink.r, ink.g, ink.b, ink.a * alpha);
        Color aBg = new Color(bg.r, bg.g, bg.b, bg.a * alpha);

        drawRectCanvas(shapes, x0 + 1, y0 + 2, 11, 10, shadow);

        drawRectCanvas(shapes, x0, y0 + 1, 11, 9, aInk);
        drawRectCanvas(shapes, x0 + 1, y0, 9, 11, aInk);

        drawRectCanvas(shapes, x0 + 1, y0 + 1, 9, 9, aBg);

        for (int j = 0; j < 7; j++) {
            String row = G[j];
            for (int i = 0; i < 7; i++) {
                if (row.charAt(i) == '#') {
                    drawRectCanvas(shapes, x0 + 2 + i, y0 + 2 + j, 1, 1, aInk);
                }
            }
        }

        drawRectCanvas(shapes, x0 + 3, y0 + 11, 5, 1, aInk);
        drawRectCanvas(shapes, x0 + 4, y0 + 12, 3, 1, aInk);
        drawRectCanvas(shapes, x0 + 5, y0 + 13, 1, 1, aInk);
    }

    public static void drawDepthBadge(ShapeRenderer shapes, float cx, float by, int n) {
        drawDepthBadge(shapes, cx, by, n, 1.0f);
    }

    public static void drawDepthBadge(ShapeRenderer shapes, float cx, float by, int n, float alpha) {
        if (shapes == null || alpha <= 0.001f) return;
        int x0 = Math.round(cx) - 7;
        int y0 = Math.round(by) - 14;

        Color shadow = new Color(0f, 0f, 0f, 0.5f * alpha);
        Color aBronze = new Color(COLOR_LADDER_BRONZE.r, COLOR_LADDER_BRONZE.g, COLOR_LADDER_BRONZE.b, COLOR_LADDER_BRONZE.a * alpha);
        Color aGold = new Color(COLOR_GOLD.r, COLOR_GOLD.g, COLOR_GOLD.b, COLOR_GOLD.a * alpha);
        Color aBg = new Color(0x15 / 255f, 0x10 / 255f, 0x0b / 255f, alpha);
        Color aDigit = new Color(0xef / 255f, 0xe3 / 255f, 0xcc / 255f, alpha);

        drawRectCanvas(shapes, x0 + 1, y0 + 2, 15, 10, shadow);

        drawRectCanvas(shapes, x0, y0 + 1, 15, 9, aBronze);
        drawRectCanvas(shapes, x0 + 1, y0, 13, 11, aBronze);

        drawRectCanvas(shapes, x0 + 1, y0 + 1, 13, 9, aBg);

        drawRectCanvas(shapes, x0 + 2, y0 + 3, 5, 1, aGold);
        drawRectCanvas(shapes, x0 + 3, y0 + 4, 3, 1, aGold);
        drawRectCanvas(shapes, x0 + 4, y0 + 5, 1, 1, aGold);
        drawRectCanvas(shapes, x0 + 4, y0 + 6, 1, 2, aGold);

        String[] D = DIGITS.get(n);
        if (D == null) D = DIGITS.get(1);
        for (int j = 0; j < 5; j++) {
            String row = D[j];
            for (int i = 0; i < 3; i++) {
                if (row.charAt(i) == '#') {
                    drawRectCanvas(shapes, x0 + 9 + i, y0 + 3 + j, 1, 1, aDigit);
                }
            }
        }

        drawRectCanvas(shapes, x0 + 5, y0 + 11, 5, 1, aBronze);
        drawRectCanvas(shapes, x0 + 6, y0 + 12, 3, 1, aBronze);
        drawRectCanvas(shapes, x0 + 7, y0 + 13, 1, 1, aBronze);
    }

    public void renderLights(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        renderLights(shapes, provider, this.isCave);
    }

    public void renderLights(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, boolean cave) {
        if (provider == null) return;
        float t = elapsedTime;
        float s = camZoom;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        if (!cave) {
            int litIndex = 0;
            for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
                for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                    MapModel.ShelterMark shelter = provider.getShelter(cx, cy);
                    if (shelter == MapModel.ShelterMark.LIT || shelter == MapModel.ShelterMark.HOME) {
                        float h = getChunkTopZ(provider, cx, cy);
                        float fl = 0.85f + 0.1f * (float) Math.sin(t * 9f + litIndex * 2f) + 0.05f * (float) Math.sin(t * 23f + litIndex);
                        Vector2 f = project(cx + 0.68f, cy + 0.66f, h + 1f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawGlow(shapes, f.x, f.y, 15f * s * fl, 240 / 255f, 140 / 255f, 60 / 255f, 0.34f * fl);

                        Vector2 b = project(cx + 0.5f, cy + 0.5f, h + 6.6f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawBeam(shapes, b.x, b.y, Math.max(1f, s * 1.1f), 22f * s, 233 / 255f, 180 / 255f, 76 / 255f, 0.32f * fl);
                        drawGlow(shapes, b.x, b.y, 5f * s, 255 / 255f, 215 / 255f, 122 / 255f, 0.45f * fl);
                        litIndex++;
                    }

                    if (provider.hasReturnPortal(cx, cy)) {
                        float ph = getChunkTopZ(provider, cx, cy);
                        Vector2 pp = project(cx + 0.5f, cy + 0.5f, ph + 2.8f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        float pu = 0.8f + 0.2f * (float) Math.sin(t * 2f);
                        drawGlow(shapes, pp.x, pp.y, 9f * s, 201 / 255f, 166 / 255f, 232 / 255f, 0.32f * pu);

                        Color swirlCol = new Color(220 / 255f, 200 / 255f, 255 / 255f, 0.45f);
                        Vector2 gpp = toGdxScreen(pp);
                        float rx = 1.4f * s;
                        float ry = 2.3f * s;
                        float lw = Math.max(1f, s * 0.35f);
                        for (int i = 0; i < 3; i++) {
                            float a0 = t * 2f + i * 2.1f;
                            drawArc(shapes, gpp.x, gpp.y, rx, ry, a0, a0 + 1.7f, lw, swirlCol);
                        }
                    }

                    if (provider.hasSeal(cx, cy)) {
                        float sh = getChunkTopZ(provider, cx, cy);
                        Vector2 sp = project(cx + 0.5f, cy + 0.5f, sh + 1.5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawGlow(shapes, sp.x, sp.y, 10f * s, 208 / 255f, 104 / 255f, 63 / 255f, 0.24f + 0.06f * (float) Math.sin(t * 1.3f));
                    }

                    if (provider.getBiome(cx, cy) == Biome.BLIGHT) {
                        float bh = getChunkTopZ(provider, cx, cy);
                        Vector2 bp = project(cx + 0.5f, cy + 0.5f, bh, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawGlow(shapes, bp.x, bp.y, 22f * s, 170 / 255f, 50 / 255f, 70 / 255f, 0.09f + 0.04f * (float) Math.sin(t * 0.9f));
                    }
                }
            }
        } else {
            for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
                for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                    if (provider.hasLadder(cx, cy)) {
                        Vector2 lp = project(cx + 4.5f / 8f, cy + 4.5f / 8f, 5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawGlow(shapes, lp.x, lp.y, 12f * s, 233 / 255f, 180 / 255f, 76 / 255f, 0.24f);
                        drawBeam(shapes, lp.x, lp.y, Math.max(2f, s * 1.6f), 14f * s, 255 / 255f, 215 / 255f, 122 / 255f, 0.22f);
                    }
                    if (provider.hasCave(cx, cy) || provider.getChunkState(cx, cy) == MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
                        Mulberry32 rng = new Mulberry32(hash(cx, cy) + 111);
                        for (int i = 0; i < 4; i++) {
                            float fu = rng.nextFloat();
                            float fv = rng.nextFloat();
                            float fph = rng.nextFloat() * 6.28f;
                            Vector2 fp = project(cx + fu, cy + fv, 5.3f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                            float fa = 0.3f + 0.12f * (float) Math.sin(t * 1.7f + fph);
                            drawGlow(shapes, fp.x, fp.y, 4.5f * s, 108 / 255f, 207 / 255f, 224 / 255f, fa);
                        }
                    }
                }
            }
        }

        if (!cave) {
            int litIndex = 0;
            for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
                for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                    MapModel.ShelterMark shelter = provider.getShelter(cx, cy);
                    if (shelter == MapModel.ShelterMark.LIT || shelter == MapModel.ShelterMark.HOME) {
                        float h = getChunkTopZ(provider, cx, cy);
                        Vector2 f0 = project(cx + 0.68f, cy + 0.66f, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        float kk = Math.max(1f, s * 0.45f);
                        float fl = (float) Math.sin(t * 11f + litIndex * 3f);
                        float hgt = (2.6f + 0.8f * fl) * kk;

                        drawRectCanvas(shapes, f0.x - kk, f0.y - hgt - kk * 0.5f, 2f * kk, hgt, COLOR_SEAL_AMBER);
                        drawRectCanvas(shapes, f0.x - kk * 0.5f, f0.y - hgt * 0.7f - kk * 0.5f, kk, hgt * 0.7f, COLOR_BRIGHT_GOLD);
                        litIndex++;
                    }
                }
            }
        }

        shapes.end();
    }

    public void renderVignette(ShapeRenderer shapes) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float cx = FBO_WIDTH / 2f;
        float cy = FBO_HEIGHT / 2f;
        float rxInner = FBO_HEIGHT * 0.32f;
        float ryInner = rxInner;
        float rxOuter = FBO_WIDTH * 0.7f;
        float ryOuter = rxOuter;

        Color cInner = new Color(4 / 255f, 3 / 255f, 2 / 255f, 0f);
        Color cOuter = new Color(4 / 255f, 3 / 255f, 2 / 255f, 0.62f);

        int segs = 32;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segs);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segs);

            float inX0 = cx + rxInner * (float) Math.cos(a0);
            float inY0 = cy + ryInner * (float) Math.sin(a0);
            float inX1 = cx + rxInner * (float) Math.cos(a1);
            float inY1 = cy + ryInner * (float) Math.sin(a1);

            float outX0 = cx + rxOuter * (float) Math.cos(a0);
            float outY0 = cy + ryOuter * (float) Math.sin(a0);
            float outX1 = cx + rxOuter * (float) Math.cos(a1);
            float outY1 = cy + ryOuter * (float) Math.sin(a1);

            shapes.triangle(inX0, inY0, outX0, outY0, outX1, outY1, cInner, cOuter, cOuter);
            shapes.triangle(inX0, inY0, outX1, outY1, inX1, inY1, cInner, cOuter, cInner);
        }

        shapes.end();
    }

    public void drawRoads(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        drawRoads(shapes, provider, 1.0f);
    }

    public void drawRoads(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        List<MapKnowledgeMask.RoadInfo> roads = provider.getRoadSegments();
        if (roads == null || roads.isEmpty()) return;

        float s = camZoom;
        float darkWidth = Math.max(3f, 1.4f * s);
        float roadWidth = Math.max(1.5f, 0.7f * s);
        float dashLen = Math.max(3f, 2.2f * s);
        float gapLen = Math.max(2f, 1.4f * s);
        float dashOffset = -elapsedTime * 4f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color darkBacking = new Color(16 / 255f, 10 / 255f, 5 / 255f, 0.75f * alpha);

        for (MapKnowledgeMask.RoadInfo r : roads) {
            float z0 = getChunkTopZ(provider, (int) r.fromX, (int) r.fromY) + 0.4f;
            float z1 = getChunkTopZ(provider, (int) r.toX, (int) r.toY) + 0.4f;
            Vector2 p0 = toGdxScreen(project(r.fromX + 0.5f, r.fromY + 0.5f, z0, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 p1 = toGdxScreen(project(r.toX + 0.5f, r.toY + 0.5f, z1, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            drawThickLine(shapes, p0.x, p0.y, p1.x, p1.y, darkWidth, darkBacking);
        }

        for (MapKnowledgeMask.RoadInfo r : roads) {
            float z0 = getChunkTopZ(provider, (int) r.fromX, (int) r.fromY) + 0.4f;
            float z1 = getChunkTopZ(provider, (int) r.toX, (int) r.toY) + 0.4f;
            Vector2 p0 = toGdxScreen(project(r.fromX + 0.5f, r.fromY + 0.5f, z0, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 p1 = toGdxScreen(project(r.toX + 0.5f, r.toY + 0.5f, z1, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

            Color baseCol = r.spent ? new Color(208 / 255f, 104 / 255f, 63 / 255f, 0.35f) : r.color;
            Color col = new Color(baseCol.r, baseCol.g, baseCol.b, baseCol.a * alpha);
            drawDashedThickLine(shapes, p0.x, p0.y, p1.x, p1.y, roadWidth, col, dashLen, gapLen, dashOffset);
        }
        shapes.end();
    }

    public void drawRumour(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        drawRumour(shapes, provider, 1.0f);
    }

    public void drawRumour(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        GridPoint2 sug = provider.getSuggestionChunk();
        if (sug == null) return;

        Vector2 b = project(sug.x + 0.5f, sug.y + 0.5f, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        float t = elapsedTime;
        float s = camZoom;
        float hgt = 34f + 16f * Math.min(s, 3f);
        float fl = 0.75f + 0.15f * (float) Math.sin(t * 3.1f) + 0.1f * (float) Math.sin(t * 7.3f);
        float w = 2f + s * 0.6f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float midY = b.y - hgt * 0.55f;
        float topY = b.y - hgt;

        Vector2 b0 = toGdxScreen(new Vector2(b.x - w * 0.5f, b.y));
        Vector2 b1 = toGdxScreen(new Vector2(b.x + w * 0.5f, b.y));
        Vector2 m0 = toGdxScreen(new Vector2(b.x - w * 0.5f, midY));
        Vector2 m1 = toGdxScreen(new Vector2(b.x + w * 0.5f, midY));
        Vector2 t0 = toGdxScreen(new Vector2(b.x - w * 0.5f, topY));
        Vector2 t1 = toGdxScreen(new Vector2(b.x + w * 0.5f, topY));

        Color col0 = new Color(233 / 255f, 180 / 255f, 76 / 255f, 0f);
        Color colMid = new Color(233 / 255f, 180 / 255f, 76 / 255f, 0.5f * fl * alpha);
        Color colTop = new Color(255 / 255f, 215 / 255f, 122 / 255f, 0f);

        shapes.triangle(b0.x, b0.y, b1.x, b1.y, m1.x, m1.y, col0, col0, colMid);
        shapes.triangle(b0.x, b0.y, m1.x, m1.y, m0.x, m0.y, col0, colMid, colMid);
        shapes.triangle(m0.x, m0.y, m1.x, m1.y, t1.x, t1.y, colMid, colMid, colTop);
        shapes.triangle(m0.x, m0.y, t1.x, t1.y, t0.x, t0.y, colMid, colTop, colTop);

        drawGlow(shapes, b.x, midY, 7f + 4f * fl, 255 / 255f, 210 / 255f, 120 / 255f, 0.55f * fl * alpha);
        shapes.end();

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPlate(shapes, b.x, topY - 2f, "shelter", COLOR_RUMOUR_BG, COLOR_GOLD, alpha);
        shapes.end();
    }

    public void drawCastleMark(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        drawCastleMark(shapes, provider, 1.0f);
    }

    public void drawCastleMark(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        Vector2 castleSite = provider.getCastleSite();
        if (castleSite == null) return;

        Vector2 p = project(castleSite.x, castleSite.y, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        float m = 26f;
        float W = FBO_WIDTH;
        float H = FBO_HEIGHT;
        float cx = W / 2f;
        float cy = H / 2f;

        float dx = p.x - cx;
        float dy = p.y - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) len = 1f;
        dx /= len;
        dy /= len;

        boolean inside = (p.x > m && p.x < W - m && p.y > m && p.y < H - m);
        Vector2 q;
        if (!inside) {
            float tx = dx > 0 ? (W - m - cx) / dx : (dx < 0 ? (m - cx) / dx : Float.POSITIVE_INFINITY);
            float ty = dy > 0 ? (H - m - cy) / dy : (dy < 0 ? (m - cy) / dy : Float.POSITIVE_INFINITY);
            float tt = Math.min(tx, ty);
            q = new Vector2(cx + dx * tt, cy + dy * tt);
        } else {
            q = new Vector2(p.x, p.y);
        }

        float k = inside ? Math.min(2.2f, Math.max(1.0f, camZoom)) : 1.2f;
        float sx = q.x - dx * (inside ? 0f : 34f);
        float sy = q.y - dy * (inside ? 0f : 34f) + 30f * k;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        Color castleDark = new Color(COLOR_CASTLE_DARK.r, COLOR_CASTLE_DARK.g, COLOR_CASTLE_DARK.b, 0.42f * alpha);
        drawRectCanvas(shapes, sx - 14f * k, sy - 10f * k, 28f * k, 10f * k, castleDark);
        drawRectCanvas(shapes, sx - 7f * k, sy - 46f * k, 14f * k, 40f * k, castleDark);
        drawRectCanvas(shapes, sx - 15f * k, sy - 28f * k, 7f * k, 20f * k, castleDark);
        drawRectCanvas(shapes, sx + 8f * k, sy - 26f * k, 7f * k, 18f * k, castleDark);
        for (int i = 0; i < 4; i++) {
            drawRectCanvas(shapes, sx - 7f * k + i * 4f * k, sy - 50f * k, 2f * k, 4f * k, castleDark);
        }

        Color castlePurple = new Color(COLOR_CASTLE_PURPLE.r, COLOR_CASTLE_PURPLE.g, COLOR_CASTLE_PURPLE.b, 0.60f * alpha);
        drawRectCanvas(shapes, sx - 1f * k, sy - 38f * k, 2f * k, 4f * k, castlePurple);

        if (!inside) {
            float ax = q.x;
            float ay = q.y;
            float nx = -dy;
            float ny = dx;

            Vector2 t0 = toGdxScreen(new Vector2(ax + dx * 8f, ay + dy * 8f));
            Vector2 t1 = toGdxScreen(new Vector2(ax - dx * 4f + nx * 7f, ay - dy * 4f + ny * 7f));
            Vector2 t2 = toGdxScreen(new Vector2(ax - dx * 4f - nx * 7f, ay - dy * 4f - ny * 7f));
            shapes.setColor(new Color(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, COLOR_TABLE_BG.a * alpha));
            shapes.triangle(t0.x, t0.y, t1.x, t1.y, t2.x, t2.y);

            Vector2 it0 = toGdxScreen(new Vector2(ax + dx * 6f, ay + dy * 6f));
            Vector2 it1 = toGdxScreen(new Vector2(ax - dx * 3f + nx * 5f, ay - dy * 3f + ny * 5f));
            Vector2 it2 = toGdxScreen(new Vector2(ax - dx * 3f - nx * 5f, ay - dy * 3f - ny * 5f));
            shapes.setColor(new Color(COLOR_CASTLE_PURPLE.r, COLOR_CASTLE_PURPLE.g, COLOR_CASTLE_PURPLE.b, COLOR_CASTLE_PURPLE.a * alpha));
            shapes.triangle(it0.x, it0.y, it1.x, it1.y, it2.x, it2.y);

            drawPlate(shapes, ax - dx * 18f, ay - dy * 18f + 7f, "castle", COLOR_CASTLE_PURPLE, COLOR_TABLE_BG, alpha);
        } else {
            drawPlate(shapes, sx, sy - 52f * k, "castle", COLOR_CASTLE_PURPLE, COLOR_TABLE_BG, alpha);
        }

        shapes.end();
    }

    static final class BadgeEntry {
        final String name;
        final Color color;
        final int depth;
        final boolean isDepth;

        BadgeEntry(String name, Color color) {
            this.name = name;
            this.color = color;
            this.depth = 0;
            this.isDepth = false;
        }

        BadgeEntry(int depth) {
            this.name = null;
            this.color = null;
            this.depth = depth;
            this.isDepth = true;
        }
    }

    public void drawBadges(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        drawBadges(shapes, provider, 1.0f);
    }

    public void drawBadges(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        ZoomLevel zoomLvl = levelOf(camZoom);
        int L = (zoomLvl == ZoomLevel.WORLD) ? 0 : ((zoomLvl == ZoomLevel.REGION) ? 1 : 2);
        float m = 30f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        List<BadgeEntry> items = new ArrayList<>(6);

        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                MapKnowledgeMask.KnowledgeProvider.State state = provider.getChunkState(cx, cy);
                String pin = provider.getChunkPin(cx, cy);
                if (state == MapKnowledgeMask.KnowledgeProvider.State.UNKNOWN && pin == null) continue;

                items.clear();
                MapModel.ShelterMark shelter = provider.getShelter(cx, cy);
                if (shelter != null && shelter != MapModel.ShelterMark.RUMOURED) {
                    if (shelter == MapModel.ShelterMark.HOME) {
                        items.add(new BadgeEntry("home", COLOR_BRIGHT_GOLD));
                    } else if (shelter == MapModel.ShelterMark.LIT) {
                        items.add(new BadgeEntry("shelter", COLOR_GOLD));
                    } else {
                        items.add(new BadgeEntry("shelter", COLOR_COLD_SHELTER));
                    }
                }

                if (provider.hasSeal(cx, cy)) {
                    items.add(new BadgeEntry("seal", COLOR_SEAL_AMBER));
                }

                if (L >= 1) {
                    if (pin != null) {
                        Color pinColor = PIN_COLORS.getOrDefault(pin, COLOR_GOLD);
                        items.add(new BadgeEntry(pin, pinColor));
                    }
                    if (provider.getGraveCount(cx, cy) > 0) {
                        items.add(new BadgeEntry("grave", COLOR_GRAVE_STONE));
                    }
                    if (provider.hasReturnPortal(cx, cy)) {
                        items.add(new BadgeEntry("portal", COLOR_PORTAL_LAVENDER));
                    }
                    int depth = provider.getStratumDepth(cx, cy);
                    if (depth > 0) {
                        items.add(new BadgeEntry(depth));
                    }
                }

                if (!items.isEmpty()) {
                    float h = getChunkTopZ(provider, cx, cy);
                    Vector2 p = project(cx + 0.5f, cy + 0.5f, h + (L == 2 ? 15f : 8f), camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    if (p.x >= -m && p.x <= FBO_WIDTH + m && p.y >= -m && p.y <= FBO_HEIGHT + m) {
                        for (int i = 0; i < items.size(); i++) {
                            BadgeEntry it = items.get(i);
                            float x = p.x + (i - (items.size() - 1) / 2f) * 14f;
                            if (it.isDepth) {
                                drawDepthBadge(shapes, x, p.y, it.depth, alpha);
                            } else {
                                drawPlate(shapes, x, p.y, it.name, it.color, COLOR_INK, alpha);
                            }
                        }
                    }
                }

                if (L == 2 && state == MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
                    float h = getChunkTopZ(provider, cx, cy);
                    if (provider.getChestCount(cx, cy) > 0) {
                        Vector2 p = project(cx + 0.5f, cy + 0.5f, h + 4f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawPlate(shapes, p.x, p.y, "loot", PIN_COLORS.get("loot"), COLOR_INK, alpha);
                    }
                    if (provider.hasLadder(cx, cy)) {
                        Vector2 p = project(cx + 4.5f / 8f, cy + 4.5f / 8f, h + 5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawPlate(shapes, p.x, p.y, "ladder", COLOR_LADDER_BRONZE, COLOR_INK, alpha);
                    }
                }
            }
        }

        shapes.end();
    }

    public void drawMarks(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        drawMarks(shapes, provider, 1.0f);
    }

    public void drawMarks(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        float t = elapsedTime;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        GridPoint2 w = provider.getWaypointChunk();
        if (w != null && w.x >= 0 && w.x < CHUNK_GRID_SIZE && w.y >= 0 && w.y < CHUNK_GRID_SIZE) {
            float wh = getChunkTopZ(provider, w.x, w.y);
            Color tableBg = new Color(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, COLOR_TABLE_BG.a * alpha);
            Color gold = new Color(COLOR_GOLD.r, COLOR_GOLD.g, COLOR_GOLD.b, COLOR_GOLD.a * alpha);
            drawRing(shapes, w.x + 0.5f, w.y + 0.5f, wh, 0.34f, tableBg, 3f, null, 0f);
            drawRing(shapes, w.x + 0.5f, w.y + 0.5f, wh, 0.34f, gold, 1f, new float[]{4f, 3f}, -t * 6f);

            float ph = (t * 0.6f) % 1f;
            Color pulseCol = new Color(COLOR_GOLD.r, COLOR_GOLD.g, COLOR_GOLD.b, (1f - ph) * 0.8f * alpha);
            drawRing(shapes, w.x + 0.5f, w.y + 0.5f, wh, 0.34f + 0.25f * ph, pulseCol, 1f, null, 0f);

            Vector2 wp = project(w.x + 0.5f, w.y + 0.5f, wh, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            drawPlate(shapes, wp.x - 8f, wp.y - 6f, "flag", COLOR_GOLD, COLOR_INK, alpha);
        }

        GridPoint2 sug = provider.getSuggestionChunk();
        if (sug != null && (w == null || !sug.equals(w)) && sug.x >= 0 && sug.x < CHUNK_GRID_SIZE && sug.y >= 0 && sug.y < CHUNK_GRID_SIZE) {
            float sh = getChunkTopZ(provider, sug.x, sug.y);
            float a = 0.45f + 0.25f * (float) Math.sin(t * 2.4f);
            Color sugCol = new Color(0xef / 255f, 0xe3 / 255f, 0xcc / 255f, a * alpha);
            drawRing(shapes, sug.x + 0.5f, sug.y + 0.5f, sh, 0.3f, sugCol, 1f, new float[]{2f, 3f}, t * 4f);

            Vector2 sp = project(sug.x + 0.5f, sug.y + 0.5f, sh, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            Color flagBg = new Color(COLOR_RUMOUR_BG.r, COLOR_RUMOUR_BG.g, COLOR_RUMOUR_BG.b, a * alpha);
            Color flagInk = new Color(0xb3 / 255f, 0x9c / 255f, 0x78 / 255f, a * alpha);
            drawPlate(shapes, sp.x - 16f, sp.y - 4f, "flag", flagBg, flagInk, alpha);
        }

        shapes.end();
    }

    public void drawPlayerMark(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        drawPlayerMark(shapes, provider, 1.0f);
    }

    public void drawPlayerMark(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        float t = elapsedTime;

        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                if (provider.hasPlayer(cx, cy)) {
                    float pu = provider.getPlayerU(cx, cy);
                    float pv = provider.getPlayerV(cx, cy);
                    float faceX = provider.getPlayerFaceX(cx, cy);
                    float faceY = provider.getPlayerFaceY(cx, cy);
                    float h = getChunkTopZ(provider, cx, cy);
                    float wx = cx + pu;
                    float wy = cy + pv;

                    ZoomLevel lvl = levelOf(camZoom);
                    boolean isChunk = (lvl == ZoomLevel.CHUNK);

                    Gdx.gl.glEnable(GL20.GL_BLEND);
                    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    shapes.begin(ShapeRenderer.ShapeType.Filled);

                    float ph = (t * 0.8f) % 1f;
                    float ringR = (isChunk ? 0.04f : 0.08f) + (isChunk ? 0.06f : 0.16f) * ph;
                    Color pulseCyan = new Color(COLOR_CYAN.r, COLOR_CYAN.g, COLOR_CYAN.b, (1f - ph) * alpha);
                    drawRing(shapes, wx, wy, h, ringR, pulseCyan, 1f, null, 0f);

                    Vector2 p = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);

                    Color tableBg = new Color(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, COLOR_TABLE_BG.a * alpha);
                    Color cyan = new Color(COLOR_CYAN.r, COLOR_CYAN.g, COLOR_CYAN.b, COLOR_CYAN.a * alpha);

                    if (isChunk) {
                        float k = camZoom * 0.75f;
                        float yTop = Math.round(p.y - 11f * k);
                        float x = Math.round(p.x);

                        drawRectCanvas(shapes, x - 4, yTop - 4, 9, 3, tableBg);
                        drawRectCanvas(shapes, x - 3, yTop - 1, 7, 2, tableBg);
                        drawRectCanvas(shapes, x - 1, yTop + 1, 3, 2, tableBg);

                        drawRectCanvas(shapes, x - 3, yTop - 3, 7, 1, cyan);
                        drawRectCanvas(shapes, x - 2, yTop - 2, 5, 1, cyan);
                        drawRectCanvas(shapes, x - 1, yTop - 1, 3, 1, cyan);
                        drawRectCanvas(shapes, x, yTop, 1, 1, cyan);
                    } else {
                        Vector2 f = project(wx + faceX * 0.3f, wy + faceY * 0.3f, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        Vector2 gp = toGdxScreen(p);
                        Vector2 gf = toGdxScreen(f);
                        drawThickLine(shapes, gp.x, gp.y, gf.x, gf.y, 3f, tableBg);
                        drawThickLine(shapes, gp.x, gp.y, gf.x, gf.y, 1f, cyan);

                        float x = Math.round(p.x);
                        float y = Math.round(p.y) - 7;
                        int[] rows = new int[]{1, 3, 5, 7, 5, 3, 1};
                        for (int j = 0; j < rows.length; j++) {
                            int w = rows[j];
                            drawRectCanvas(shapes, x - (w + 2 - 1) / 2f, y - 4 + j, w + 2, 1, tableBg);
                        }
                        drawRectCanvas(shapes, x, y - 5, 1, 1, tableBg);
                        drawRectCanvas(shapes, x, y + 3, 1, 1, tableBg);

                        for (int j = 0; j < rows.length; j++) {
                            int w = rows[j];
                            drawRectCanvas(shapes, x - (w - 1) / 2f, y - 4 + j, w, 1, cyan);
                        }
                        Color cyanHi = new Color(COLOR_CYAN_HI.r, COLOR_CYAN_HI.g, COLOR_CYAN_HI.b, COLOR_CYAN_HI.a * alpha);
                        drawRectCanvas(shapes, x - 1, y - 2, 1, 1, cyanHi);
                    }

                    shapes.end();
                    return;
                }
            }
        }
    }

    public void drawCaveBadges(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        drawCaveBadges(shapes, provider, 1.0f);
    }

    public void drawCaveBadges(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        ZoomLevel zoomLvl = levelOf(camZoom);
        int L = (zoomLvl == ZoomLevel.WORLD) ? 0 : ((zoomLvl == ZoomLevel.REGION) ? 1 : 2);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        Vector2 ladderScreenPos = null;

        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                if (provider.hasLadder(cx, cy) || provider.hasCave(cx, cy)) {
                    Vector2 lp = project(cx + 4.5f / 8f, cy + 4.5f / 8f, 5f + 11f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    if (ladderScreenPos == null && provider.hasLadder(cx, cy)) {
                        ladderScreenPos = lp;
                    }
                    drawPlate(shapes, lp.x, lp.y, "ladder", COLOR_LADDER_BRONZE, COLOR_INK, alpha);
                }

                if (L >= 1 && provider.hasCave(cx, cy)) {
                    int chests = provider.getChestCount(cx, cy);
                    if (chests > 0) {
                        Vector2 p = project(cx + 0.5f, cy + 0.5f, 9f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                        drawPlate(shapes, p.x, p.y, "loot", PIN_COLORS.get("loot"), COLOR_INK, alpha);
                    }
                }
            }
        }

        // Player tether diamond & dashed line
        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                if (provider.hasPlayer(cx, cy)) {
                    float pu = provider.getPlayerU(cx, cy);
                    float pv = provider.getPlayerV(cx, cy);
                    Vector2 p = project(cx + pu, cy + pv, 22f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    float x = Math.round(p.x);
                    float y = Math.round(p.y);

                    // Diamond
                    Color cyan = new Color(COLOR_CYAN.r, COLOR_CYAN.g, COLOR_CYAN.b, 0.75f * alpha);
                    Vector2 d0 = toGdxScreen(new Vector2(x + 0.5f, y - 4f));
                    Vector2 d1 = toGdxScreen(new Vector2(x + 4.5f, y));
                    Vector2 d2 = toGdxScreen(new Vector2(x + 0.5f, y + 4f));
                    Vector2 d3 = toGdxScreen(new Vector2(x - 3.5f, y));

                    drawThickLine(shapes, d0.x, d0.y, d1.x, d1.y, 1f, cyan);
                    drawThickLine(shapes, d1.x, d1.y, d2.x, d2.y, 1f, cyan);
                    drawThickLine(shapes, d2.x, d2.y, d3.x, d3.y, 1f, cyan);
                    drawThickLine(shapes, d3.x, d3.y, d0.x, d0.y, 1f, cyan);

                    // Dashed line from (x + 0.5, y + 5) to (lp.x + 0.5, lp.y - 16)
                    if (ladderScreenPos != null) {
                        Vector2 start = toGdxScreen(new Vector2(x + 0.5f, y + 5f));
                        Vector2 end = toGdxScreen(new Vector2(ladderScreenPos.x + 0.5f, ladderScreenPos.y - 16f));
                        drawDashedThickLine(shapes, start.x, start.y, end.x, end.y, 1f, cyan, 2f, 2f, 0f);
                    }
                    break;
                }
            }
        }

        shapes.end();
    }

    public void drawSelection(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        if (provider == null) return;
        GridPoint2 sel = provider.getSelectedChunk();
        if (sel == null || sel.x < 0 || sel.x >= CHUNK_GRID_SIZE || sel.y < 0 || sel.y >= CHUNK_GRID_SIZE) return;

        float z = (floorT > 0.5f) ? 5.0f : getChunkTopZ(provider, sel.x, sel.y);

        Vector2[] pts = new Vector2[]{
            toGdxScreen(project(sel.x, sel.y, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT)),
            toGdxScreen(project(sel.x + 1, sel.y, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT)),
            toGdxScreen(project(sel.x + 1, sel.y + 1, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT)),
            toGdxScreen(project(sel.x, sel.y + 1, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT))
        };

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        Color darkBorder = new Color(11 / 255f, 9 / 255f, 7 / 255f, 0.85f);
        for (int i = 0; i < 4; i++) {
            Vector2 a = pts[i];
            Vector2 b = pts[(i + 1) % 4];
            drawThickLine(shapes, a.x, a.y, b.x, b.y, 3f, darkBorder);
        }

        float pulse = 0.7f + 0.3f * (float) Math.sin(elapsedTime * 3.0);
        Color goldBorder = new Color(COLOR_BRIGHT_GOLD.r, COLOR_BRIGHT_GOLD.g, COLOR_BRIGHT_GOLD.b, pulse);
        for (int i = 0; i < 4; i++) {
            Vector2 a = pts[i];
            Vector2 b = pts[(i + 1) % 4];
            drawThickLine(shapes, a.x, a.y, b.x, b.y, 1f, goldBorder);
        }

        shapes.setColor(COLOR_BRIGHT_GOLD);
        for (int i = 0; i < 4; i++) {
            Vector2 p = pts[i];
            shapes.rect(Math.round(p.x) - 1, Math.round(p.y) - 1, 3, 3);
        }

        shapes.end();
    }

    public void renderOverlays(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        renderOverlays(shapes, provider, ease(floorT));
    }

    public void renderOverlays(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float ft) {
        if (provider == null) return;
        float sa = Math.max(0f, 1f - ft * 2f);
        float ca = Math.max(0f, ft * 2f - 1f);

        if (sa > 0.001f) {
            drawRoads(shapes, provider, sa);
            drawRumour(shapes, provider, sa);
            drawCastleMark(shapes, provider, sa);
            drawBadges(shapes, provider, sa);
            drawMarks(shapes, provider, sa);
            drawPlayerMark(shapes, provider, sa);
        }

        if (ca > 0.001f) {
            drawCaveBadges(shapes, provider, ca);
        }

        drawSelection(shapes, provider);
    }

    private static Vector2 toGdxScreen(Vector2 topDown) {
        return new Vector2(topDown.x, FBO_HEIGHT - topDown.y);
    }

    @Override
    public void dispose() {
        if (surfaceFbo != null) {
            surfaceFbo.dispose();
            surfaceFbo = null;
            surfaceRegion = null;
        }
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (fboBatch != null) {
            fboBatch.dispose();
            fboBatch = null;
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
            shapeRenderer = null;
        }
        noiseTextures.dispose();
        knowledgeMask.dispose();
        if (fogShader != null) {
            fogShader.dispose();
            fogShader = null;
        }
        initialized = false;
    }
}
