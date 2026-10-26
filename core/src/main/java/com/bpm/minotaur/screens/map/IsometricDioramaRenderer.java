package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.Ladder;
import com.bpm.minotaur.gamedata.map.MapModel;
import com.bpm.minotaur.generation.Biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The isometric diorama expedition map (docs/DEsign/tarmin-map-handoff).
 *
 * <ul>
 *   <li>Drawn into a 720x448 frame buffer that the map surface shows at 2x, nearest-filtered.</li>
 *   <li>The prototype's projection, with one difference: the game's chunks grow north (+y) while
 *       the prototype's world grows down the screen, so every projection negates world y about the
 *       camera. North draws up-right, as in the handoff, and the map is never a mirror image.</li>
 *   <li>The static scene (board, chunk blocks, walls, props) is cached per floor and re-rendered
 *       only when the camera or the knowledge changes; lights, fog and overlays draw every frame.</li>
 *   <li>Chunks show the game's own tiles: 36x36 floors, thin walls, doors, ladders and items.</li>
 * </ul>
 */
public final class IsometricDioramaRenderer implements Disposable {

    public static final int FBO_WIDTH = 720;
    public static final int FBO_HEIGHT = 448;
    /** The camera's reach before any knowledge arrives; afterwards the known area sets it. */
    public static final int WORLD_RADIUS = 24;
    /** Chunks of open board kept around the known area. */
    public static final int BOARD_MARGIN = 2;
    /** How far past the known area the camera may wander, in chunks. */
    public static final int CAMERA_MARGIN = 6;

    public static final float ZOOM_WORLD = 1.0f;
    public static final float ZOOM_REGION = 2.6f;
    public static final float ZOOM_CHUNK = 6.4f;

    public static final float MIN_ZOOM = 0.7f;
    public static final float MAX_ZOOM = 8.5f;

    /** Heights in z units (x zoom = FBO pixels). */
    public static final float WALL_HEIGHT = 1.6f;
    public static final float DOOR_HEIGHT = 0.7f;
    /** A thin wall's thickness, as a share of one tile. */
    public static final float WALL_THICKNESS = 0.24f;
    /** Longest run of wall merged into one box, in tiles; longer runs sort badly against props. */
    public static final int WALL_RUN = 6;

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
    // Cave fungi: amber and violet, as the prototype's caveGlow. Cyan belongs to the player alone.
    public static final Color COLOR_FUNGI_GLOW = new Color(217 / 255f, 160 / 255f, 74 / 255f, 1.0f); // #D9A04A
    public static final Color COLOR_FUNGI_GLOW_VIOLET = new Color(166 / 255f, 120 / 255f, 216 / 255f, 1.0f); // #A678D8

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

    public static final Color COLOR_CAVE_FLOOR_OPEN = new Color(0x3f / 255f, 0x32 / 255f, 0x28 / 255f, 1f); // #3f3228
    public static final Color COLOR_THREAT = new Color(0xe0 / 255f, 0x53 / 255f, 0x3d / 255f, 1f); // #E0533D
    public static final Color COLOR_SUGGEST_RING = new Color(0xef / 255f, 0xe3 / 255f, 0xcc / 255f, 1f); // #EFE3CC
    public static final Color COLOR_SUGGEST_INK = new Color(0xb3 / 255f, 0x9c / 255f, 0x78 / 255f, 1f); // #B39C78

    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private FrameBuffer surfaceScene;
    private TextureRegion surfaceSceneRegion;
    private FrameBuffer caveScene;
    private TextureRegion caveSceneRegion;
    private FrameBuffer ghostFbo;
    private TextureRegion ghostRegion;
    private SpriteBatch fboBatch;
    private ShapeRenderer shapeRenderer;

    private float floorT = 0f;
    private float targetFloorT = 0f;

    private final MapNoiseTextures noiseTextures = new MapNoiseTextures();
    private final MapKnowledgeMask surfaceMask = new MapKnowledgeMask(false);
    private final MapKnowledgeMask caveMask = new MapKnowledgeMask(true);
    private MapFogShader fogShader;

    // Current animated camera values; x, y are game world coordinates (chunks, north = +y).
    private float camX = 0.5f;
    private float camY = 0.5f;
    private float camZoom = 1.0f;
    private float camRot = 0.0f; // Radians

    private float tgtX = 0.5f;
    private float tgtY = 0.5f;
    private float tgtZoom = 1.0f;
    private float tgtRot = 0.0f;
    private int rotStep = 0;

    private float fogStrength = 1.0f;
    private boolean isCave = false;
    private float elapsedTime = 0f;
    private boolean initialized = false;

    // The known area, in chunks (inclusive), and the lists the scenes draw.
    private int knownMinX = -WORLD_RADIUS, knownMinY = -WORLD_RADIUS;
    private int knownMaxX = WORLD_RADIUS, knownMaxY = WORLD_RADIUS;
    private boolean boundsFromKnowledge = false;
    private final List<GridPoint2> surfaceChunks = new ArrayList<>();
    private final List<GridPoint2> caveChunks = new ArrayList<>();
    private final List<GridPoint2> caveSceneChunks = new ArrayList<>();
    private boolean knowledgeDirty = true;

    // Scene cache keys: the camera and knowledge each scene was last drawn with.
    private int sceneVersion = 0;
    private final float[] surfaceKey = {Float.NaN, 0, 0, 0, -1};
    private final float[] caveKey = {Float.NaN, 0, 0, 0, -1};

    // The projection of the frame being drawn, cached so the scene projects without allocating.
    private float vcs = 1f, vsn = 0f, vhw = 28f, vhh = 14f, vs = 1f, vcx = 0.5f, vcy = 0.5f;
    private final float[] pq = new float[2];
    private final float[] boxXs = new float[4];
    private final float[] boxYs = new float[4];
    private final float[] quad = new float[8];
    private static final float[][] FACE_NORMALS = {{0f, -1f}, {1f, 0f}, {0f, 1f}, {-1f, 0f}};

    public IsometricDioramaRenderer() {
    }

    public static ZoomLevel levelOf(float zoom) {
        if (zoom < 1.7f) return ZoomLevel.WORLD;
        if (zoom < 4.2f) return ZoomLevel.REGION;
        return ZoomLevel.CHUNK;
    }

    /**
     * World point to FBO pixels, top-down y as in the prototype's {@code P()}. World y is negated
     * about the camera so that north (+y) draws up-right at rotation 0.
     */
    public static Vector2 project(float wx, float wy, float z, float camX, float camY, float zoom, float rot, float W, float H) {
        float hw = 28f * zoom;
        float hh = 14f * zoom;
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        float dx = wx - camX;
        float dy = camY - wy;
        float rx = dx * cs - dy * sn;
        float ry = dx * sn + dy * cs;
        float sx = W / 2f + (rx - ry) * hw;
        float sy = H / 2f + (rx + ry) * hh - z * zoom;
        return new Vector2(sx, sy);
    }

    /** The inverse of {@link #project}: FBO pixels (top-down y) at height z to a world point. */
    public static Vector2 unproject(float sx, float sy, float z, float camX, float camY, float zoom, float rot, float W, float H) {
        float hw = 28f * zoom;
        float hh = 14f * zoom;
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        float a = (sx - W / 2f) / hw;
        float b = (sy - H / 2f + z * zoom) / hh;
        float rx = (a + b) / 2f;
        float ry = (b - a) / 2f;
        float dx = rx * cs + ry * sn;
        float dy = -rx * sn + ry * cs;
        return new Vector2(camX + dx, camY - dy);
    }

    /** Painter's sort key, the prototype's {@code dep()}: larger is nearer the viewer. */
    public static float depth(float wx, float wy, float camX, float camY, float rot) {
        float dx = wx - camX;
        float dy = camY - wy;
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        return (dx * cs - dy * sn) + (dx * sn + dy * cs);
    }

    public float depth(float wx, float wy) {
        float dx = wx - vcx;
        float dy = vcy - wy;
        return (dx * vcs - dy * vsn) + (dx * vsn + dy * vcs);
    }

    /** The compass needle's angle (radians, libGDX y-up) for world north at a camera rotation. */
    public static float northAngle(float rot) {
        float cs = (float) Math.cos(rot);
        float sn = (float) Math.sin(rot);
        // North (0, +1) projects to screen (rx - ry, rx + ry) with rx = sn, ry = -cs.
        return (float) Math.atan2((cs - sn) * 14f, (sn + cs) * 28f);
    }

    public void init() {
        if (initialized || Gdx.gl == null) return;

        fbo = newBuffer();
        fboRegion = regionOf(fbo);
        surfaceScene = newBuffer();
        surfaceSceneRegion = regionOf(surfaceScene);
        caveScene = newBuffer();
        caveSceneRegion = regionOf(caveScene);
        ghostFbo = newBuffer();
        ghostRegion = regionOf(ghostFbo);

        fboBatch = new SpriteBatch();
        fboBatch.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);

        shapeRenderer = new ShapeRenderer(20000);
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);
        shapeRenderer.updateMatrices();

        noiseTextures.buildTextures();
        fogShader = new MapFogShader();

        initialized = true;
    }

    private static FrameBuffer newBuffer() {
        FrameBuffer b = new FrameBuffer(Pixmap.Format.RGBA8888, FBO_WIDTH, FBO_HEIGHT, false);
        b.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return b;
    }

    private static TextureRegion regionOf(FrameBuffer b) {
        TextureRegion r = new TextureRegion(b.getColorBufferTexture());
        r.flip(false, true);
        return r;
    }

    /** Eases the camera toward its targets, the prototype's {@code k = 1 - exp(-dt * 9)}. */
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

        float dF = targetFloorT - floorT;
        if (Math.abs(dF) > 0.003f) {
            floorT += dF * (1.0f - (float) Math.exp(-delta * 3.2));
        } else {
            floorT = targetFloorT;
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

    /** Pans by a drag in actor pixels (libGDX y-up), the prototype's {@code panScreen}. */
    public void panScreen(float screenDx, float screenDy, float actorWidth, float actorHeight) {
        float scaleX = (float) FBO_WIDTH / (actorWidth > 0 ? actorWidth : FBO_WIDTH);
        float scaleY = (float) FBO_HEIGHT / (actorHeight > 0 ? actorHeight : FBO_HEIGHT);
        panFbo(screenDx * scaleX, -screenDy * scaleY);
    }

    /** Pans by FBO pixels, top-down y. */
    public void panFbo(float dx, float dy) {
        float s = tgtZoom;
        float cs = (float) Math.cos(tgtRot);
        float sn = (float) Math.sin(tgtRot);
        float a = dx / (28f * s);
        float b = dy / (14f * s);
        float rx = (a + b) / 2f;
        float ry = (b - a) / 2f;
        tgtX += rx * cs + ry * sn;
        tgtY -= -rx * sn + ry * cs;
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
        if (boundsFromKnowledge) {
            tgtX = Math.max(knownMinX - CAMERA_MARGIN, Math.min(knownMaxX + 1 + CAMERA_MARGIN, tgtX));
            tgtY = Math.max(knownMinY - CAMERA_MARGIN, Math.min(knownMaxY + 1 + CAMERA_MARGIN, tgtY));
        } else {
            tgtX = Math.max(-WORLD_RADIUS, Math.min(WORLD_RADIUS, tgtX));
            tgtY = Math.max(-WORLD_RADIUS, Math.min(WORLD_RADIUS, tgtY));
        }
        tgtZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, tgtZoom));
    }

    private static float toFboX(float localX, float actorWidth) {
        return localX * ((float) FBO_WIDTH / (actorWidth > 0 ? actorWidth : FBO_WIDTH));
    }

    private static float toFboY(float localY, float actorHeight) {
        return (actorHeight - localY) * ((float) FBO_HEIGHT / (actorHeight > 0 ? actorHeight : FBO_HEIGHT));
    }

    /**
     * The chunk under an actor point: the prototype's {@code pick()}, trying the heights a chunk
     * top can stand at, highest first, and taking the first chunk whose top is at that height.
     */
    public GridPoint2 pickChunk(float localX, float localY, float actorWidth, float actorHeight,
                               int floor, ChunkHeightLookup heightLookup) {
        float sx = toFboX(localX, actorWidth);
        float sy = toFboY(localY, actorHeight);

        boolean cave = (floor != 1 || floorT > 0.5f);
        float[] zs = cave ? new float[]{5f} : new float[]{6f, 2.5f, 2f, 1.2f, 0f};

        for (float z : zs) {
            Vector2 w = unproject(sx, sy, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            int cx = (int) Math.floor(w.x);
            int cy = (int) Math.floor(w.y);
            if (cave || z == 0f || heightLookup == null) {
                return new GridPoint2(cx, cy);
            }
            if (Math.abs(heightLookup.getTopZ(cx, cy) - z) < 0.2f) {
                return new GridPoint2(cx, cy);
            }
        }
        return null;
    }

    /**
     * The chunk that will sit at the middle of the view once the camera reaches its target: what
     * keyboard panning selects, so the selection moves with the view like a crosshair.
     */
    public GridPoint2 pickChunkAtTarget(int floor, ChunkHeightLookup heightLookup) {
        boolean cave = floor != 1;
        float[] zs = cave ? new float[]{5f} : new float[]{6f, 2.5f, 2f, 1.2f, 0f};
        for (float z : zs) {
            Vector2 w = unproject(FBO_WIDTH / 2f, FBO_HEIGHT / 2f, z, tgtX, tgtY, tgtZoom, tgtRot, FBO_WIDTH, FBO_HEIGHT);
            int cx = (int) Math.floor(w.x);
            int cy = (int) Math.floor(w.y);
            if (cave || z == 0f || heightLookup == null || Math.abs(heightLookup.getTopZ(cx, cy) - z) < 0.2f) {
                return new GridPoint2(cx, cy);
            }
        }
        return null;
    }

    /** The world point at the middle of the view once the camera reaches its target, on a plane at height z. */
    public Vector2 targetCentre(float z) {
        return unproject(FBO_WIDTH / 2f, FBO_HEIGHT / 2f, z, tgtX, tgtY, tgtZoom, tgtRot, FBO_WIDTH, FBO_HEIGHT);
    }

    /** The world point under an actor point, on a plane at height z. */
    public Vector2 pickPoint(float localX, float localY, float actorWidth, float actorHeight, float z) {
        return unproject(toFboX(localX, actorWidth), toFboY(localY, actorHeight), z,
                camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
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

    /** Knowledge, shelters, pins or loaded tiles changed: rebuild masks, lists and scenes. */
    public void invalidateKnowledgeMask() {
        this.knowledgeDirty = true;
    }

    /** Animates to a floor. A no-op when already there, so callers may repeat it every frame. */
    public void setFloor(int floor) {
        float t = (floor != 1) ? 1.0f : 0.0f;
        boolean cave = floor != 1;
        if (t == targetFloorT && cave == isCave) return;
        this.targetFloorT = t;
        this.isCave = cave;
        invalidateKnowledgeMask();
    }

    public void setFloorImmediate(int floor) {
        this.targetFloorT = (floor != 1) ? 1.0f : 0.0f;
        this.floorT = this.targetFloorT;
        this.isCave = (floor != 1);
        invalidateKnowledgeMask();
    }

    public float getFloorT() {
        return floorT;
    }

    public float getTargetFloorT() {
        return targetFloorT;
    }

    /** Whether the mask, lists and scenes are waiting to be rebuilt. */
    boolean isKnowledgeDirty() {
        return knowledgeDirty;
    }

    public static float ease(float t) {
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }

    public TextureRegion getFboRegion() {
        return fboRegion;
    }

    // ------------------------------------------------------------------
    // Knowledge
    // ------------------------------------------------------------------

    /** Reads the known chunks, sets the bounds and rebuilds both fog masks. */
    void refreshKnowledge(MapKnowledgeMask.KnowledgeProvider provider) {
        knowledgeDirty = false;
        sceneVersion++;
        surfaceChunks.clear();
        caveChunks.clear();
        caveSceneChunks.clear();
        if (provider == null) return;

        surfaceChunks.addAll(provider.knownChunks());
        caveChunks.addAll(provider.caveChunks());
        Set<GridPoint2> union = new HashSet<>(surfaceChunks);
        union.addAll(caveChunks);
        caveSceneChunks.addAll(union);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (GridPoint2 c : union) {
            minX = Math.min(minX, c.x);
            minY = Math.min(minY, c.y);
            maxX = Math.max(maxX, c.x);
            maxY = Math.max(maxY, c.y);
        }
        GridPoint2 p = provider.getPlayerChunk();
        if (p != null) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        if (minX == Integer.MAX_VALUE) {
            minX = minY = -1;
            maxX = maxY = 1;
        }
        knownMinX = minX;
        knownMinY = minY;
        knownMaxX = maxX;
        knownMaxY = maxY;
        boundsFromKnowledge = true;
        clampTarget();

        surfaceMask.rebuild(provider, surfaceChunks, minX, minY, maxX, maxY);
        caveMask.rebuild(provider, caveSceneChunks, minX, minY, maxX, maxY);
    }

    public int getKnownMinX() { return knownMinX; }
    public int getKnownMinY() { return knownMinY; }
    public int getKnownMaxX() { return knownMaxX; }
    public int getKnownMaxY() { return knownMaxY; }

    MapKnowledgeMask getSurfaceMask() {
        return surfaceMask;
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    private void setProj() {
        vcs = (float) Math.cos(camRot);
        vsn = (float) Math.sin(camRot);
        vs = camZoom;
        vhw = 28f * camZoom;
        vhh = 14f * camZoom;
        vcx = camX;
        vcy = camY;
    }

    /** Projects into {@link #pq}, FBO pixels with libGDX's y-up, without allocating. */
    private void P(float wx, float wy, float z) {
        float dx = wx - vcx;
        float dy = vcy - wy;
        float rx = dx * vcs - dy * vsn;
        float ry = dx * vsn + dy * vcs;
        pq[0] = FBO_WIDTH / 2f + (rx - ry) * vhw;
        pq[1] = FBO_HEIGHT / 2f - (rx + ry) * vhh + z * vs;
    }

    private boolean sceneStale(float[] key) {
        return key[0] != camX || key[1] != camY || key[2] != camZoom || key[3] != camRot || key[4] != sceneVersion;
    }

    private void stamp(float[] key) {
        key[0] = camX;
        key[1] = camY;
        key[2] = camZoom;
        key[3] = camRot;
        key[4] = sceneVersion;
    }

    /** Renders the frame into the 720x448 buffer: cached scene, lights, fog, vignette, overlays. */
    public void renderFbo(MapKnowledgeMask.KnowledgeProvider provider) {
        if (!initialized) init();
        if (fbo == null) return;
        if (knowledgeDirty) refreshKnowledge(provider);

        setProj();
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);
        shapeRenderer.updateMatrices();
        fboBatch.getProjectionMatrix().setToOrtho2D(0, 0, FBO_WIDTH, FBO_HEIGHT);

        if (floorT < 0.999f && sceneStale(surfaceKey)) {
            renderScene(surfaceScene, provider, false);
            stamp(surfaceKey);
        }
        if (floorT > 0.001f && sceneStale(caveKey)) {
            renderScene(caveScene, provider, true);
            stamp(caveKey);
        }

        float ft = ease(floorT);
        if (floorT <= 0.001f) {
            fbo.begin();
            drawTable();
            blit(surfaceSceneRegion, 1f, 0f);
            renderLights(shapeRenderer, provider, false);
            renderFog(false);
            renderVignette(shapeRenderer);
            renderOverlays(shapeRenderer, provider, 0f);
            fbo.end();
            return;
        }

        float lift = Math.min(64f, 22f * camZoom) * ft;
        if (floorT < 0.999f) {
            ghostFbo.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            blit(surfaceSceneRegion, 1f, 0f);
            renderLights(shapeRenderer, provider, false);
            renderFog(false);
            ghostFbo.end();
        }

        fbo.begin();
        drawTable();
        blit(caveSceneRegion, 1f, 0f);
        renderLights(shapeRenderer, provider, true);
        renderFog(true);
        if (floorT < 0.999f) blit(ghostRegion, 1f - 0.84f * ft, lift);
        else blit(surfaceSceneRegion, 0.16f, lift);
        renderVignette(shapeRenderer);
        renderOverlays(shapeRenderer, provider, ft);
        fbo.end();
    }

    private void blit(TextureRegion region, float alpha, float lift) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        fboBatch.enableBlending();
        fboBatch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        fboBatch.begin();
        fboBatch.setColor(1f, 1f, 1f, alpha);
        fboBatch.draw(region, 0, lift, FBO_WIDTH, FBO_HEIGHT);
        fboBatch.setColor(Color.WHITE);
        fboBatch.end();
    }

    private void renderFog(boolean cave) {
        MapKnowledgeMask mask = cave ? caveMask : surfaceMask;
        if (fogShader == null || !fogShader.isCompiled() || mask.getMaskTexture() == null) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        fboBatch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        fboBatch.begin();
        Vector2 orig = project(0f, 0f, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        fogShader.render(fboBatch, noiseTextures, mask, elapsedTime, camX, camY, camZoom, camRot,
                orig.x, orig.y, fogStrength, cave, FBO_WIDTH, FBO_HEIGHT);
        fboBatch.end();
    }

    /** The table under the board: void, and the prototype's warm glow at the board's centre. */
    private void drawTable() {
        Gdx.gl.glClearColor(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Vector2 c = project((knownMinX + knownMaxX + 1) / 2f, (knownMinY + knownMaxY + 1) / 2f, 0f,
                camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        float r = Math.max(FBO_WIDTH, 160f * camZoom * 3f);
        drawGlow(shapeRenderer, c.x, c.y, r, 96 / 255f, 64 / 255f, 30 / 255f, 0.34f);
        shapeRenderer.end();
    }

    // ------------------------------------------------------------------
    // Static scene
    // ------------------------------------------------------------------

    private void renderScene(FrameBuffer target, MapKnowledgeMask.KnowledgeProvider provider, boolean cave) {
        target.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawBoard(cave);
        shapeRenderer.end();
        drawBoardLines();

        List<GridPoint2> list = cave ? caveSceneChunks : surfaceChunks;
        list.sort((a, b) -> Float.compare(depth(a.x + 0.5f, a.y + 0.5f), depth(b.x + 0.5f, b.y + 0.5f)));
        float m = 40f * camZoom + 60f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (GridPoint2 c : list) {
            if (!onScreen(c.x + 0.5f, c.y + 0.5f, m)) continue;
            if (provider == null) continue;
            if (cave) {
                drawCaveChunk(c.x, c.y, provider);
            } else if (provider.getChunkState(c.x, c.y) == MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
                drawVisitedChunk(c.x, c.y, provider.getBiome(c.x, c.y), provider.getChunkTiles(c.x, c.y), provider);
            } else {
                drawGlimpsedChunk(c.x, c.y, provider.getBiome(c.x, c.y), provider);
            }
        }
        shapeRenderer.end();
        target.end();
    }

    public boolean onScreen(float wx, float wy, float margin) {
        P(wx, wy, 0f);
        float sy = FBO_HEIGHT - pq[1];
        return pq[0] > -margin && pq[0] < FBO_WIDTH + margin && sy > -margin && sy < FBO_HEIGHT + margin;
    }

    /** A filled quad from four world corners at height z. */
    private void worldQuad(float x0, float y0, float x1, float y1, float z) {
        P(x0, y0, z);
        quad[0] = pq[0];
        quad[1] = pq[1];
        P(x1, y0, z);
        quad[2] = pq[0];
        quad[3] = pq[1];
        P(x1, y1, z);
        quad[4] = pq[0];
        quad[5] = pq[1];
        P(x0, y1, z);
        quad[6] = pq[0];
        quad[7] = pq[1];
        shapeRenderer.triangle(quad[0], quad[1], quad[2], quad[3], quad[4], quad[5]);
        shapeRenderer.triangle(quad[0], quad[1], quad[4], quad[5], quad[6], quad[7]);
    }

    /** The prototype's {@code box()}: visible sides shaded by face normal, then the top. */
    public void drawBox(ShapeRenderer shapes,
                        float x0, float y0, float x1, float y1,
                        float z0, float z1,
                        Color topCol, Color cL, Color cR) {
        boxXs[0] = x0; boxYs[0] = y0;
        boxXs[1] = x1; boxYs[1] = y0;
        boxXs[2] = x1; boxYs[2] = y1;
        boxXs[3] = x0; boxYs[3] = y1;
        for (int i = 0; i < 4; i++) {
            float nx = FACE_NORMALS[i][0];
            float ny = -FACE_NORMALS[i][1]; // world y draws flipped
            float rx = nx * vcs - ny * vsn;
            float ry = nx * vsn + ny * vcs;
            if (rx + ry <= 0.001f) continue;
            Color side = (rx - ry < 0f) ? cL : cR;
            if (side == null) continue;
            int j = (i + 1) % 4;
            P(boxXs[i], boxYs[i], z0);
            float ax = pq[0], ay = pq[1];
            P(boxXs[j], boxYs[j], z0);
            float bx = pq[0], by = pq[1];
            P(boxXs[j], boxYs[j], z1);
            float cx = pq[0], cy = pq[1];
            P(boxXs[i], boxYs[i], z1);
            shapes.setColor(side);
            shapes.triangle(ax, ay, bx, by, cx, cy);
            shapes.triangle(ax, ay, cx, cy, pq[0], pq[1]);
        }
        if (topCol != null) {
            shapes.setColor(topCol);
            worldQuad(x0, y0, x1, y1, z1);
        }
    }

    private float boardMinX() { return knownMinX - BOARD_MARGIN; }
    private float boardMinY() { return knownMinY - BOARD_MARGIN; }
    private float boardMaxX() { return knownMaxX + 1 + BOARD_MARGIN; }
    private float boardMaxY() { return knownMaxY + 1 + BOARD_MARGIN; }

    private void drawBoard(boolean cave) {
        float e = 0.4f;
        float x0 = boardMinX(), y0 = boardMinY(), x1 = boardMaxX(), y1 = boardMaxY();
        shapeRenderer.setColor(COLOR_BOARD_UNDER);
        worldQuad(x0 - 1.8f, y0 - 1.8f, x1 + 1.8f, y1 + 1.8f, -5f);
        drawBox(shapeRenderer, x0 - e, y0 - e, x1 + e, y1 + e, -5f, 0f,
                cave ? COLOR_BOARD_CAVE_TOP : COLOR_BOARD_SURFACE_TOP, COLOR_BOARD_SIDE_A, COLOR_BOARD_SIDE_B);
    }

    /** The board's chunk grid and rim, under the chunks as in the prototype's {@code drawBoard}. */
    private void drawBoardLines() {
        float e = 0.4f;
        float x0 = boardMinX(), y0 = boardMinY(), x1 = boardMaxX(), y1 = boardMaxY();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(COLOR_BOARD_GRID);
        for (int i = (int) x0; i <= (int) x1; i++) {
            P(i, y0 - e, 0f);
            float ax = pq[0], ay = pq[1];
            P(i, y1 + e, 0f);
            shapeRenderer.line(ax, ay, pq[0], pq[1]);
        }
        for (int j = (int) y0; j <= (int) y1; j++) {
            P(x0 - e, j, 0f);
            float ax = pq[0], ay = pq[1];
            P(x1 + e, j, 0f);
            shapeRenderer.line(ax, ay, pq[0], pq[1]);
        }
        shapeRenderer.setColor(COLOR_BOARD_RIM);
        float[][] rim = {{x0 - e, y0 - e}, {x1 + e, y0 - e}, {x1 + e, y1 + e}, {x0 - e, y1 + e}};
        for (int i = 0; i < 4; i++) {
            P(rim[i][0], rim[i][1], 0f);
            float ax = pq[0], ay = pq[1];
            P(rim[(i + 1) % 4][0], rim[(i + 1) % 4][1], 0f);
            shapeRenderer.line(ax, ay, pq[0], pq[1]);
        }
        shapeRenderer.end();
    }

    /** One wall, door, solid tile or prop of a chunk, depth-sorted with its neighbours. */
    static final class RenderItem implements Comparable<RenderItem> {
        float depth;
        int kind; // 0 box, 1 prop
        float x0, y0, x1, y1, z0, z1;
        Color top, lit, shade;
        Prop prop;

        @Override
        public int compareTo(RenderItem o) {
            return Float.compare(this.depth, o.depth);
        }
    }

    private final List<RenderItem> itemPool = new ArrayList<>();
    private final List<RenderItem> renderItems = new ArrayList<>(512);
    private final List<Prop> chunkProps = new ArrayList<>(64);

    private RenderItem nextItem() {
        int i = renderItems.size();
        if (i >= itemPool.size()) itemPool.add(new RenderItem());
        RenderItem it = itemPool.get(i);
        it.prop = null;
        renderItems.add(it);
        return it;
    }

    private void addBox(float x0, float y0, float x1, float y1, float z0, float z1, Color top, Color lit, Color shade) {
        RenderItem it = nextItem();
        it.kind = 0;
        it.x0 = x0;
        it.y0 = y0;
        it.x1 = x1;
        it.y1 = y1;
        it.z0 = z0;
        it.z1 = z1;
        it.top = top;
        it.lit = lit;
        it.shade = shade;
        it.depth = depth((x0 + x1) / 2f, (y0 + y1) / 2f);
    }

    /** Sets the renderer colour to {@code base} lightened or darkened by amt, then mixed toward dark by k. */
    private void setShaded(Color base, float amt, Color dark, float k) {
        float r = base.r, g = base.g, b = base.b;
        if (amt >= 0f) {
            r += (1f - r) * amt;
            g += (1f - g) * amt;
            b += (1f - b) * amt;
        } else {
            r *= 1f + amt;
            g *= 1f + amt;
            b *= 1f + amt;
        }
        if (k > 0f) {
            r += (dark.r - r) * k;
            g += (dark.g - g) * k;
            b += (dark.b - b) * k;
        }
        shapeRenderer.setColor(r, g, b, 1f);
    }

    /** A tile-sized pseudo-random value in [0, 1), stable per chunk and 4x4 tile block. */
    private static float blockVariation(int cx, int cy, int bx, int by) {
        int h = hash(cx * 64 + bx, cy * 64 + by);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65536f;
    }

    /** Floor tiles of a chunk at height h: seen, unseen and solid, merged into runs along each row. */
    private void drawFloor(int cx, int cy, ChunkTiles t, float h, Color floor, Color solid, Color unseenDark, boolean cave) {
        int w = t.width, hgt = t.height;
        for (int ty = 0; ty < hgt; ty++) {
            int runStart = 0;
            int runKey = -1;
            for (int tx = 0; tx <= w; tx++) {
                int key = -2;
                if (tx < w) {
                    boolean seen = t.isSeen(tx, ty);
                    boolean isSolid = t.isSolid(tx, ty);
                    int state = !seen ? 0 : (isSolid ? 2 : 1);
                    key = state * 1024 + (tx * 8 / w) * 32 + (ty * 8 / hgt);
                }
                if (key != runKey) {
                    if (runKey >= 0) {
                        int state = runKey / 1024;
                        int bx = (runKey / 32) % 32;
                        int by = runKey % 32;
                        float v = blockVariation(cx, cy, bx, by) - 0.5f;
                        Color base = state == 2 ? solid : floor;
                        setShaded(base, v * (cave ? 0.2f : 0.16f), unseenDark, state == 0 ? 0.45f : 0f);
                        worldQuad(cx + runStart / (float) w, cy + ty / (float) hgt,
                                cx + tx / (float) w, cy + (ty + 1) / (float) hgt, h);
                    }
                    runStart = tx;
                    runKey = key;
                }
            }
        }
    }

    private static boolean seenNear(ChunkTiles t, int x, int y) {
        return t.isSeen(x, y) || t.isSeen(x + 1, y) || t.isSeen(x - 1, y) || t.isSeen(x, y + 1) || t.isSeen(x, y - 1);
    }

    /**
     * The chunk's real walls as boxes: thin walls on tile edges, doors lower and in wood, solid
     * tiles whole. Runs along an edge merge into one box, up to {@link #WALL_RUN} tiles. Only what
     * borders a seen tile is drawn.
     */
    private void collectWalls(int cx, int cy, ChunkTiles t, float h, float wallH,
                              Color top, Color lit, Color shade, Color doorTop, Color doorLit, Color doorShade) {
        int w = t.width, hgt = t.height;
        float tw = 1f / w, th = 1f / hgt;
        float halfX = WALL_THICKNESS * tw / 2f, halfY = WALL_THICKNESS * th / 2f;

        // Edges along x: y = ey, between rows ey - 1 and ey.
        for (int ey = 0; ey <= hgt; ey++) {
            int runStart = -1;
            int runType = ChunkTiles.EDGE_NONE;
            for (int tx = 0; tx <= w; tx++) {
                int type = ChunkTiles.EDGE_NONE;
                if (tx < w) {
                    boolean southSolid = ey > 0 && t.isSolid(tx, ey - 1);
                    boolean northSolid = ey < hgt && t.isSolid(tx, ey);
                    boolean seen = (ey > 0 && t.isSeen(tx, ey - 1)) || (ey < hgt && t.isSeen(tx, ey));
                    if (seen && !southSolid && !northSolid) type = t.horizontalEdge(tx, ey);
                }
                if (type != runType || (runStart >= 0 && tx - runStart >= WALL_RUN)) {
                    if (runType != ChunkTiles.EDGE_NONE) {
                        boolean door = runType == ChunkTiles.EDGE_DOOR;
                        float y = cy + ey * th;
                        addBox(cx + runStart * tw, y - halfY, cx + tx * tw, y + halfY, h, h + (door ? DOOR_HEIGHT : wallH),
                                door ? doorTop : top, door ? doorLit : lit, door ? doorShade : shade);
                    }
                    runStart = tx;
                    runType = type;
                }
            }
        }
        // Edges along y: x = ex, between columns ex - 1 and ex.
        for (int ex = 0; ex <= w; ex++) {
            int runStart = -1;
            int runType = ChunkTiles.EDGE_NONE;
            for (int ty = 0; ty <= hgt; ty++) {
                int type = ChunkTiles.EDGE_NONE;
                if (ty < hgt) {
                    boolean westSolid = ex > 0 && t.isSolid(ex - 1, ty);
                    boolean eastSolid = ex < w && t.isSolid(ex, ty);
                    boolean seen = (ex > 0 && t.isSeen(ex - 1, ty)) || (ex < w && t.isSeen(ex, ty));
                    if (seen && !westSolid && !eastSolid) type = t.verticalEdge(ex, ty);
                }
                if (type != runType || (runStart >= 0 && ty - runStart >= WALL_RUN)) {
                    if (runType != ChunkTiles.EDGE_NONE) {
                        boolean door = runType == ChunkTiles.EDGE_DOOR;
                        float x = cx + ex * tw;
                        addBox(x - halfX, cy + runStart * th, x + halfX, cy + ty * th, h, h + (door ? DOOR_HEIGHT : wallH),
                                door ? doorTop : top, door ? doorLit : lit, door ? doorShade : shade);
                    }
                    runStart = ty;
                    runType = type;
                }
            }
        }
        // Solid tiles, merged along rows.
        for (int ty = 0; ty < hgt; ty++) {
            int runStart = -1;
            for (int tx = 0; tx <= w; tx++) {
                boolean solid = tx < w && t.isSolid(tx, ty) && seenNear(t, tx, ty);
                if (solid && runStart < 0) runStart = tx;
                if ((!solid || tx - runStart >= WALL_RUN) && runStart >= 0) {
                    addBox(cx + runStart * tw, cy + ty * th, cx + tx * tw, cy + (ty + 1) * th, h, h + wallH, top, lit, shade);
                    runStart = solid ? tx : -1;
                }
            }
        }
    }

    private void flushItems(int cx, int cy, float h, boolean sil) {
        renderItems.sort(null);
        for (RenderItem it : renderItems) {
            if (it.kind == 0) drawBox(shapeRenderer, it.x0, it.y0, it.x1, it.y1, it.z0, it.z1, it.top, it.lit, it.shade);
            else drawProp(shapeRenderer, it.prop, cx, cy, h, sil);
        }
        renderItems.clear();
    }

    private void drawVisitedChunk(int cx, int cy, Biome biome, ChunkTiles tiles, MapKnowledgeMask.KnowledgeProvider provider) {
        BiomeColors B = getBiomeColors(biome);
        float h = (biome == Biome.OCEAN) ? 2.0f : 6.0f;
        drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 0f, h, null, B.sideA, B.sideB);

        renderItems.clear();
        if (tiles == null || tiles.width <= 0 || tiles.height <= 0) {
            shapeRenderer.setColor(B.top);
            worldQuad(cx, cy, cx + 1, cy + 1, h);
        } else {
            drawFloor(cx, cy, tiles, h, B.top, COLOR_WALL_FOOTPRINT, COLOR_UNKNOWN_DARK, false);
            boolean maze = biome == null || biome == Biome.MAZE;
            Color top = maze ? COLOR_WALL_TOP : shade(B.sideA, 0.18f);
            Color lit = maze ? COLOR_WALL_LIT : B.sideA;
            Color dark = maze ? COLOR_WALL_SHADE : B.sideB;
            collectWalls(cx, cy, tiles, h, WALL_HEIGHT, top, lit, dark, COLOR_CHEST_TOP, COLOR_CHEST_LIT, COLOR_CHEST_SHADE);
        }

        chunkProps.clear();
        generateVisitedProps(cx, cy, biome, tiles, provider, chunkProps);
        for (Prop p : chunkProps) {
            if ((p.kind == Prop.Kind.CHEST || p.kind == Prop.Kind.RUBBLE) && camZoom < 1.6f) continue;
            RenderItem it = nextItem();
            it.kind = 1;
            it.prop = p;
            it.depth = depth(cx + p.u, cy + p.v) + (p.kind == Prop.Kind.LADDER ? -0.05f : 0f);
        }
        flushItems(cx, cy, h, false);
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

    /** A chunk of the stratum view: three bands of rock, and the explored cave on top when there is one. */
    private void drawCaveChunk(int cx, int cy, MapKnowledgeMask.KnowledgeProvider provider) {
        boolean open = provider.getCaveState(cx, cy) == MapKnowledgeMask.KnowledgeProvider.State.VISITED;
        ChunkTiles t = open ? provider.getCavernTiles(cx, cy) : null;
        boolean hasCave = t != null && t.width > 0 && t.height > 0;

        drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 0f, 1.7f, null, COLOR_STRATA_1_A, COLOR_STRATA_1_B);
        drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 1.7f, 3.4f, null, COLOR_STRATA_2_A, COLOR_STRATA_2_B);
        drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 3.4f, 5.0f, hasCave ? null : COLOR_STRATA_UNVISITED_TOP,
                COLOR_STRATA_3_A, COLOR_STRATA_3_B);

        if (!hasCave) {
            if (camZoom > 1.5f) {
                shapeRenderer.setColor(COLOR_STRATA_SPECK);
                Mulberry32 rng = new Mulberry32(hash(cx, cy) + 99);
                for (int i = 0; i < 14; i++) {
                    P(cx + rng.nextFloat(), cy + rng.nextFloat(), 5f);
                    shapeRenderer.rect(Math.round(pq[0]), Math.round(pq[1]), 1, 1);
                }
            }
            return;
        }

        drawFloor(cx, cy, t, 5f, COLOR_CAVE_FLOOR_OPEN, COLOR_CAVE_WALL_FLOOR, COLOR_CAVE_WALL_FLOOR, true);
        renderItems.clear();
        collectWalls(cx, cy, t, 5f, 2.4f, COLOR_PILLAR_TOP, COLOR_PILLAR_A, COLOR_PILLAR_B,
                COLOR_CHEST_TOP, COLOR_CHEST_LIT, COLOR_CHEST_SHADE);
        for (Map.Entry<GridPoint2, Ladder.LadderType> e : t.ladders.entrySet()) {
            Prop lad = new Prop();
            lad.kind = Prop.Kind.LADDER;
            lad.u = (e.getKey().x + 0.5f) / t.width;
            lad.v = (e.getKey().y + 0.5f) / t.height;
            lad.va = e.getValue() == Ladder.LadderType.UP ? 1f : 0f;
            RenderItem it = nextItem();
            it.kind = 1;
            it.prop = lad;
            it.depth = depth(cx + lad.u, cy + lad.v) - 0.05f;
        }
        for (GridPoint2 item : t.itemTiles) {
            if (!t.isSeen(item.x, item.y)) continue;
            Prop ch = new Prop();
            ch.kind = Prop.Kind.CHEST;
            ch.u = (item.x + 0.5f) / t.width;
            ch.v = (item.y + 0.5f) / t.height;
            RenderItem it = nextItem();
            it.kind = 1;
            it.prop = ch;
            it.depth = depth(cx + ch.u, cy + ch.v);
            break;
        }
        renderItems.sort(null);
        for (RenderItem it : renderItems) {
            if (it.kind == 0) {
                drawBox(shapeRenderer, it.x0, it.y0, it.x1, it.y1, it.z0, it.z1, it.top, it.lit, it.shade);
            } else if (it.prop.kind == Prop.Kind.LADDER) {
                drawLadderShaftProp(shapeRenderer, cx + it.prop.u, cy + it.prop.v, 5f, it.prop.va > 0.5f ? 10f : 3.5f);
            } else {
                drawProp(shapeRenderer, it.prop, cx, cy, 5f, false);
            }
        }
        renderItems.clear();

        // Fungi: amber and violet specks on seen floor beside rock, as the prototype's caveGlow.
        int k = Math.max(1, Math.round(camZoom * 0.4f));
        for (float[] f : fungiOf(cx, cy, t)) {
            P(cx + f[0], cy + f[1], 5f);
            shapeRenderer.setColor(f[2] > 0.5f ? COLOR_FUNGI_GLOW : COLOR_FUNGI_GLOW_VIOLET);
            shapeRenderer.rect(Math.round(pq[0]), Math.round(pq[1]), k, k);
        }
    }

    /** Up to a dozen glowing fungi per explored cave chunk: {u, v, amber?, phase}. Stable per chunk. */
    static List<float[]> fungiOf(int cx, int cy, ChunkTiles t) {
        List<float[]> out = new ArrayList<>();
        Mulberry32 rng = new Mulberry32(hash(cx, cy) + 4242);
        for (int i = 0; i < 160 && out.size() < 12; i++) {
            int tx = (int) (rng.nextFloat() * t.width);
            int ty = (int) (rng.nextFloat() * t.height);
            float roll = rng.nextFloat();
            if (!t.isSeen(tx, ty) || t.isSolid(tx, ty)) continue;
            boolean nearRock = t.horizontalEdge(tx, ty) != ChunkTiles.EDGE_NONE || t.verticalEdge(tx, ty) != ChunkTiles.EDGE_NONE;
            if (!nearRock || roll > 0.4f) continue;
            out.add(new float[]{(tx + 0.5f) / t.width, (ty + 0.5f) / t.height, rng.nextFloat() < 0.6f ? 1f : 0f, rng.nextFloat() * 6f});
        }
        return out;
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
            float ny = -nrm[i][1]; // north draws up-screen: the view flips world y
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
                float rx = n[0] * cs + n[1] * sn; // normal with world y flipped, then rotated
                float ry = n[0] * sn - n[1] * cs;
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

    /**
     * Props for an explored chunk: biome dressing on seen, open ground (an 8x8 split of the real
     * tiles) and landmarks where the game has them -- ladders, items and the player at their tiles.
     */
    public static void generateVisitedProps(int cx, int cy, Biome biome, ChunkTiles tiles,
                                            MapKnowledgeMask.KnowledgeProvider provider,
                                            List<Prop> out) {
        Mulberry32 rng = new Mulberry32(hash(cx, cy));
        if (biome == null) biome = Biome.MAZE;

        MapModel.ShelterMark shelter = (provider != null) ? provider.getShelter(cx, cy) : null;
        boolean portal = (provider != null) && provider.hasReturnPortal(cx, cy);
        int graves = (provider != null) ? provider.getGraveCount(cx, cy) : 0;
        boolean feat = (shelter != null || portal || graves > 0);
        boolean hasTiles = tiles != null && tiles.width > 0 && tiles.height > 0;

        for (int by = 0; by < 8; by++) {
            for (int bx = 0; bx < 8; bx++) {
                if (hasTiles && (!tiles.isBlockSeen(bx, by) || tiles.isBlockSolid(bx, by))) continue;

                float u = (bx + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                float v = (by + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                float roll = rng.nextFloat();

                boolean nearFeat = feat && Math.abs(u - 0.5f) < 0.2f && Math.abs(v - 0.5f) < 0.2f;
                if (biome != Biome.MAZE && nearFeat) continue;

                switch (biome) {
                    case FOREST: {
                        out.add(prop(Prop.Kind.PINE, u, v, 0.8f + 0.45f * rng.nextFloat()));
                        if (roll < 0.55f) {
                            float u2 = (bx + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                            float v2 = (by + 0.18f + 0.64f * rng.nextFloat()) / 8.0f;
                            if (!feat || Math.abs(u2 - 0.5f) >= 0.2f || Math.abs(v2 - 0.5f) >= 0.2f) {
                                out.add(prop(Prop.Kind.PINE, u2, v2, 0.7f + 0.4f * rng.nextFloat()));
                            }
                        }
                        break;
                    }
                    case MOUNTAINS:
                        if (roll < 0.72f) {
                            Prop p = prop(Prop.Kind.CRAG, u, v, 0.7f + 0.5f * rng.nextFloat());
                            p.va = rng.nextFloat();
                            out.add(p);
                        }
                        break;
                    case DESERT:
                        if (roll < 0.32f) out.add(prop(Prop.Kind.DUNE, u, v, 0.8f + 0.5f * rng.nextFloat()));
                        else if (roll < 0.40f) out.add(prop(Prop.Kind.CACTUS, u, v, 0.8f + 0.3f * rng.nextFloat()));
                        else if (roll < 0.46f) out.add(prop(Prop.Kind.HOODOO, u, v, 0.8f + 0.4f * rng.nextFloat()));
                        else if (roll < 0.51f) out.add(prop(Prop.Kind.BONES, u, v, 1.0f));
                        break;
                    case BLIGHT:
                        if (roll < 0.50f) {
                            Prop p = prop(Prop.Kind.CRACK, u, v, 0.8f + 0.5f * rng.nextFloat());
                            p.va = rng.nextFloat();
                            out.add(p);
                        } else if (roll < 0.66f) {
                            out.add(prop(Prop.Kind.DEADTREE, u, v, 0.8f + 0.4f * rng.nextFloat()));
                        } else if (roll < 0.76f) {
                            out.add(prop(Prop.Kind.PUSTULE, u, v, 0.8f + 0.4f * rng.nextFloat()));
                        }
                        break;
                    case LAKELANDS:
                        if (roll < 0.20f) out.add(prop(Prop.Kind.PINE, u, v, 0.6f + 0.3f * rng.nextFloat()));
                        else if (roll < 0.40f) out.add(prop(Prop.Kind.TUFT, u, v, 1.0f));
                        else if (roll < 0.80f) out.add(prop(Prop.Kind.REEDS, u, v, 0.8f + 0.4f * rng.nextFloat()));
                        break;
                    case TUNDRA:
                        if (roll < 0.35f) out.add(prop(Prop.Kind.MOUND, u, v, 0.8f + 0.5f * rng.nextFloat()));
                        else if (roll < 0.60f) out.add(prop(Prop.Kind.ICE, u, v, 0.8f + 0.4f * rng.nextFloat()));
                        else if (roll < 0.80f) {
                            Prop p = prop(Prop.Kind.PINE, u, v, 0.8f + 0.4f * rng.nextFloat());
                            p.snow = true;
                            out.add(p);
                        }
                        break;
                    case MAZE:
                        if (roll < 0.06f) out.add(prop(Prop.Kind.RUBBLE, u, v, 1.0f));
                        break;
                    case OCEAN:
                    default:
                        break;
                }
            }
        }

        if (biome == Biome.DESERT && rng.nextFloat() < 0.7f) {
            out.add(prop(Prop.Kind.ARCH, 0.25f + 0.5f * rng.nextFloat(), 0.25f + 0.5f * rng.nextFloat(), 1f));
        } else if (biome == Biome.LAKELANDS && rng.nextFloat() < 0.7f) {
            out.add(prop(Prop.Kind.BOAT, 0.25f + 0.5f * rng.nextFloat(), 0.25f + 0.5f * rng.nextFloat(), 1f));
        }

        if (shelter != null) {
            Prop s = prop(Prop.Kind.SHELTER, 0.5f, 0.5f, 1f);
            s.shelterType = shelter;
            out.add(s);
            if (shelter != MapModel.ShelterMark.COLD) out.add(prop(Prop.Kind.FIRE, 0.68f, 0.66f, 1f));
        }
        if (portal) out.add(prop(Prop.Kind.PORTAL, 0.5f, 0.5f, 1f));
        for (int i = 0; i < graves; i++) {
            float u0 = (biome == Biome.LAKELANDS) ? 0.3f : 0.44f;
            float v0 = (biome == Biome.LAKELANDS) ? 0.3f : 0.42f;
            out.add(prop(Prop.Kind.GRAVE, u0 + i * 0.1f, v0, 1f));
        }

        if (hasTiles) {
            for (Map.Entry<GridPoint2, Ladder.LadderType> e : tiles.ladders.entrySet()) {
                GridPoint2 at = e.getKey();
                if (!tiles.isSeen(at.x, at.y)) continue;
                out.add(prop(Prop.Kind.LADDER, (at.x + 0.5f) / tiles.width, (at.y + 0.5f) / tiles.height, 1f));
            }
            int chests = 0;
            for (GridPoint2 at : tiles.itemTiles) {
                if (!tiles.isSeen(at.x, at.y) || chests++ >= 3) continue;
                out.add(prop(Prop.Kind.CHEST, (at.x + 0.5f) / tiles.width, (at.y + 0.5f) / tiles.height, 1f));
            }
        } else {
            if (provider != null && provider.hasLadder(cx, cy)) out.add(prop(Prop.Kind.LADDER, 4.5f / 8f, 4.5f / 8f, 1f));
            if (provider != null && provider.getChestCount(cx, cy) > 0) {
                out.add(prop(Prop.Kind.CHEST, 0.3f + 0.4f * rng.nextFloat(), 0.3f + 0.4f * rng.nextFloat(), 1f));
            }
        }

        if (provider != null && provider.hasPlayer(cx, cy)) {
            Prop ply = prop(Prop.Kind.PLAYER, provider.getPlayerU(cx, cy), provider.getPlayerV(cx, cy), 1f);
            ply.faceX = provider.getPlayerFaceX(cx, cy);
            ply.faceY = provider.getPlayerFaceY(cx, cy);
            out.add(ply);
        }
    }

    private static Prop prop(Prop.Kind kind, float u, float v, float sz) {
        Prop p = new Prop();
        p.kind = kind;
        p.u = u;
        p.v = v;
        p.sz = sz;
        return p;
    }

    public static float getChunkTopZ(MapKnowledgeMask.KnowledgeProvider provider, int cx, int cy) {
        if (provider == null) return 0f;
        MapKnowledgeMask.KnowledgeProvider.State state = provider.getChunkState(cx, cy);
        boolean isOcean = provider.getBiome(cx, cy) == Biome.OCEAN;
        if (state == MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
            return isOcean ? 2.0f : 6.0f;
        } else if (state == MapKnowledgeMask.KnowledgeProvider.State.GLIMPSED) {
            return isOcean ? 1.2f : 2.5f;
        }
        return 0f;
    }

    public static void drawRectCanvas(ShapeRenderer shapes, float x, float y, float w, float h, Color color) {
        shapes.setColor(color);
        shapes.rect(x, FBO_HEIGHT - y - h, w, h);
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

    // ------------------------------------------------------------------
    // Per-frame layers
    // ------------------------------------------------------------------

    public void renderLights(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, boolean cave) {
        if (provider == null) return;
        float t = elapsedTime;
        float s = camZoom;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float m = 40f * camZoom + 60f;

        if (!cave) {
            int litIndex = 0;
            for (GridPoint2 c : surfaceChunks) {
                int cx = c.x;
                int cy = c.y;
                if (!onScreen(cx + 0.5f, cy + 0.5f, m)) continue;
                float h = getChunkTopZ(provider, cx, cy);
                MapModel.ShelterMark shelter = provider.getShelter(cx, cy);
                if (shelter == MapModel.ShelterMark.LIT || shelter == MapModel.ShelterMark.HOME) {
                    float fl = 0.85f + 0.1f * (float) Math.sin(t * 9f + litIndex * 2f) + 0.05f * (float) Math.sin(t * 23f + litIndex);
                    Vector2 f = project(cx + 0.68f, cy + 0.66f, h + 1f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    drawGlow(shapes, f.x, f.y, 15f * s * fl, 240 / 255f, 140 / 255f, 60 / 255f, 0.34f * fl);
                    Vector2 b = project(cx + 0.5f, cy + 0.5f, h + 6.6f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    drawBeam(shapes, b.x, b.y, Math.max(1f, s * 1.1f), 22f * s, 233 / 255f, 180 / 255f, 76 / 255f, 0.32f * fl);
                    drawGlow(shapes, b.x, b.y, 5f * s, 255 / 255f, 215 / 255f, 122 / 255f, 0.45f * fl);
                    litIndex++;
                }
                if (provider.getChunkState(cx, cy) != MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
                    if (provider.hasSeal(cx, cy)) sealGlow(shapes, provider, cx, cy, h, t, s);
                    continue;
                }
                if (provider.hasReturnPortal(cx, cy)) {
                    Vector2 pp = project(cx + 0.5f, cy + 0.5f, h + 2.8f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    float pu = 0.8f + 0.2f * (float) Math.sin(t * 2f);
                    drawGlow(shapes, pp.x, pp.y, 9f * s, 201 / 255f, 166 / 255f, 232 / 255f, 0.32f * pu);
                    Vector2 gpp = toGdxScreen(pp);
                    float lw = Math.max(1f, s * 0.35f);
                    for (int i = 0; i < 3; i++) {
                        float a0 = t * 2f + i * 2.1f;
                        drawArc(shapes, gpp.x, gpp.y, 1.4f * s, 2.3f * s, a0, a0 + 1.7f, lw, COLOR_PORTAL_SWIRL);
                    }
                }
                if (provider.hasSeal(cx, cy)) sealGlow(shapes, provider, cx, cy, h, t, s);
                if (provider.getBiome(cx, cy) == Biome.BLIGHT) {
                    Vector2 bp = project(cx + 0.5f, cy + 0.5f, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    drawGlow(shapes, bp.x, bp.y, 22f * s, 170 / 255f, 50 / 255f, 70 / 255f, 0.09f + 0.04f * (float) Math.sin(t * 0.9f));
                }
            }
        } else {
            for (GridPoint2 c : caveChunks) {
                int cx = c.x;
                int cy = c.y;
                if (!onScreen(cx + 0.5f, cy + 0.5f, m)) continue;
                ChunkTiles ct = provider.getCavernTiles(cx, cy);
                if (ct == null || ct.width <= 0) continue;
                for (Map.Entry<GridPoint2, Ladder.LadderType> e : ct.ladders.entrySet()) {
                    if (e.getValue() != Ladder.LadderType.UP) continue; // only the way out glows; strata hold many ladders down
                    GridPoint2 at = e.getKey();
                    Vector2 lp = project(cx + (at.x + 0.5f) / ct.width, cy + (at.y + 0.5f) / ct.height, 5f,
                            camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    drawGlow(shapes, lp.x, lp.y, 12f * s, 233 / 255f, 180 / 255f, 76 / 255f, 0.24f);
                    drawBeam(shapes, lp.x, lp.y, Math.max(2f, s * 1.6f), 14f * s, 255 / 255f, 215 / 255f, 122 / 255f, 0.22f);
                }
                for (float[] f : fungiOf(cx, cy, ct)) {
                    Vector2 fp = project(cx + f[0], cy + f[1], 5.3f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                    float fa = 0.3f + 0.12f * (float) Math.sin(t * 1.7f + f[3]);
                    Color fc = f[2] > 0.5f ? COLOR_FUNGI_GLOW : COLOR_FUNGI_GLOW_VIOLET;
                    drawGlow(shapes, fp.x, fp.y, 4.5f * s, fc.r, fc.g, fc.b, fa);
                }
            }
        }
        shapes.end();

        if (!cave) {
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            int litIndex = 0;
            for (GridPoint2 c : surfaceChunks) {
                MapModel.ShelterMark shelter = provider.getShelter(c.x, c.y);
                if (shelter != MapModel.ShelterMark.LIT && shelter != MapModel.ShelterMark.HOME) continue;
                if (!onScreen(c.x + 0.5f, c.y + 0.5f, m)) {
                    litIndex++;
                    continue;
                }
                float h = getChunkTopZ(provider, c.x, c.y);
                Vector2 f0 = project(c.x + 0.68f, c.y + 0.66f, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                float kk = Math.max(1f, s * 0.45f);
                float fl = (float) Math.sin(t * 11f + litIndex * 3f);
                float hgt = (2.6f + 0.8f * fl) * kk;
                drawRectCanvas(shapes, f0.x - kk, f0.y - hgt - kk * 0.5f, 2f * kk, hgt, COLOR_SEAL_AMBER);
                drawRectCanvas(shapes, f0.x - kk * 0.5f, f0.y - hgt * 0.7f - kk * 0.5f, kk, hgt * 0.7f, COLOR_BRIGHT_GOLD);
                litIndex++;
            }
            shapes.end();
        }
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void sealGlow(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, int cx, int cy, float h, float t, float s) {
        Vector2 sp = project(cx + 0.5f, cy + 0.5f, h + 1.5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        float a = (0.24f + 0.06f * (float) Math.sin(t * 1.3f)) * (provider.isSealWon(cx, cy) ? 0.35f : 1f);
        drawGlow(shapes, sp.x, sp.y, 10f * s, 208 / 255f, 104 / 255f, 63 / 255f, a);
    }

    public void renderVignette(ShapeRenderer shapes) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float cx = FBO_WIDTH / 2f;
        float cy = FBO_HEIGHT / 2f;
        float rInner = FBO_HEIGHT * 0.32f;
        float rOuter = FBO_WIDTH * 0.7f;
        Color cInner = VIGNETTE_INNER;
        Color cOuter = VIGNETTE_OUTER;

        int segs = 32;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segs);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segs);
            float inX0 = cx + rInner * (float) Math.cos(a0), inY0 = cy + rInner * (float) Math.sin(a0);
            float inX1 = cx + rInner * (float) Math.cos(a1), inY1 = cy + rInner * (float) Math.sin(a1);
            float outX0 = cx + rOuter * (float) Math.cos(a0), outY0 = cy + rOuter * (float) Math.sin(a0);
            float outX1 = cx + rOuter * (float) Math.cos(a1), outY1 = cy + rOuter * (float) Math.sin(a1);
            shapes.triangle(inX0, inY0, outX0, outY0, outX1, outY1, cInner, cOuter, cOuter);
            shapes.triangle(inX0, inY0, outX1, outY1, inX1, inY1, cInner, cOuter, cInner);
        }
        shapes.end();
    }

    private static final Color VIGNETTE_INNER = new Color(4 / 255f, 3 / 255f, 2 / 255f, 0f);
    private static final Color VIGNETTE_OUTER = new Color(4 / 255f, 3 / 255f, 2 / 255f, 0.62f);
    private static final Color COLOR_PORTAL_SWIRL = new Color(220 / 255f, 200 / 255f, 1f, 0.45f);

    // ------------------------------------------------------------------
    // Overlay: drawn over the fog
    // ------------------------------------------------------------------

    private int levelIndex() {
        ZoomLevel z = levelOf(camZoom);
        return z == ZoomLevel.WORLD ? 0 : (z == ZoomLevel.REGION ? 1 : 2);
    }

    private static Color withAlpha(Color c, float a) {
        return new Color(c.r, c.g, c.b, c.a * a);
    }

    public void drawRoads(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        List<MapKnowledgeMask.RoadInfo> roads = provider.getRoadSegments();
        if (roads == null || roads.isEmpty()) return;

        float s = camZoom;
        float dashOffset = -elapsedTime * 4f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color backing = new Color(16 / 255f, 10 / 255f, 5 / 255f, 0.75f * alpha);
        for (int pass = 0; pass < 2; pass++) {
            for (MapKnowledgeMask.RoadInfo r : roads) {
                float z0 = getChunkTopZ(provider, (int) Math.floor(r.fromX), (int) Math.floor(r.fromY)) + 0.4f;
                float z1 = getChunkTopZ(provider, (int) Math.floor(r.toX), (int) Math.floor(r.toY)) + 0.4f;
                Vector2 p0 = toGdxScreen(project(r.fromX + 0.5f, r.fromY + 0.5f, z0, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 p1 = toGdxScreen(project(r.toX + 0.5f, r.toY + 0.5f, z1, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                if (pass == 0) {
                    drawThickLine(shapes, p0.x, p0.y, p1.x, p1.y, Math.max(3f, 1.4f * s), backing);
                } else {
                    Color base = r.color == null ? COLOR_SEAL_AMBER : r.color;
                    Color col = new Color(base.r, base.g, base.b, (r.spent ? 0.35f : 1f) * alpha);
                    drawDashedThickLine(shapes, p0.x, p0.y, p1.x, p1.y, Math.max(1.5f, 0.7f * s), col,
                            Math.max(3f, 2.2f * s), Math.max(2f, 1.4f * s), dashOffset);
                }
            }
        }
        shapes.end();
    }

    /** The suggested way on: a pulsing gold line along the road from the player's chunk. */
    private void drawSuggestedRoute(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        List<GridPoint2> route = provider.getSuggestedRoute();
        if (route == null || route.size() < 2) return;
        float pulse = 0.6f + 0.3f * (float) Math.sin(elapsedTime * 3f);
        Color col = new Color(COLOR_BRIGHT_GOLD.r, COLOR_BRIGHT_GOLD.g, COLOR_BRIGHT_GOLD.b, pulse * alpha);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i + 1 < route.size(); i++) {
            GridPoint2 a = route.get(i);
            GridPoint2 b = route.get(i + 1);
            Vector2 p0 = toGdxScreen(project(a.x + 0.5f, a.y + 0.5f, getChunkTopZ(provider, a.x, a.y) + 0.8f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 p1 = toGdxScreen(project(b.x + 0.5f, b.y + 0.5f, getChunkTopZ(provider, b.x, b.y) + 0.8f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            drawDashedThickLine(shapes, p0.x, p0.y, p1.x, p1.y, 1f, col, 2f, 2f, elapsedTime * 6f);
        }
        shapes.end();
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

    /**
     * Where a point sits against the view's edge: inside, or clamped to an inset rectangle along
     * the ray from the centre. Returns {x, y, dx, dy, inside} in top-down FBO pixels.
     */
    private static float[] edgeClamp(Vector2 p, float m) {
        float W = FBO_WIDTH, H = FBO_HEIGHT, cx = W / 2f, cy = H / 2f;
        float dx = p.x - cx, dy = p.y - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) len = 1f;
        dx /= len;
        dy /= len;
        boolean inside = p.x > m && p.x < W - m && p.y > m && p.y < H - m;
        if (inside) return new float[]{p.x, p.y, dx, dy, 1f};
        float tx = dx > 0 ? (W - m - cx) / dx : (dx < 0 ? (m - cx) / dx : Float.POSITIVE_INFINITY);
        float ty = dy > 0 ? (H - m - cy) / dy : (dy < 0 ? (m - cy) / dy : Float.POSITIVE_INFINITY);
        float tt = Math.min(tx, ty);
        return new float[]{cx + dx * tt, cy + dy * tt, dx, dy, 0f};
    }

    /** An arrowhead on the view's edge pointing along (dx, dy), outlined in table black. */
    private static void edgeArrow(ShapeRenderer shapes, float ax, float ay, float dx, float dy, Color color, float alpha) {
        float nx = -dy, ny = dx;
        Vector2 t0 = toGdxScreen(new Vector2(ax + dx * 8f, ay + dy * 8f));
        Vector2 t1 = toGdxScreen(new Vector2(ax - dx * 4f + nx * 7f, ay - dy * 4f + ny * 7f));
        Vector2 t2 = toGdxScreen(new Vector2(ax - dx * 4f - nx * 7f, ay - dy * 4f - ny * 7f));
        shapes.setColor(withAlpha(COLOR_TABLE_BG, alpha));
        shapes.triangle(t0.x, t0.y, t1.x, t1.y, t2.x, t2.y);
        Vector2 i0 = toGdxScreen(new Vector2(ax + dx * 6f, ay + dy * 6f));
        Vector2 i1 = toGdxScreen(new Vector2(ax - dx * 3f + nx * 5f, ay - dy * 3f + ny * 5f));
        Vector2 i2 = toGdxScreen(new Vector2(ax - dx * 3f - nx * 5f, ay - dy * 3f - ny * 5f));
        shapes.setColor(withAlpha(color, alpha));
        shapes.triangle(i0.x, i0.y, i1.x, i1.y, i2.x, i2.y);
    }

    /**
     * Castle Tarmin: its bearing is always known, its place only once reached. Known and in view,
     * its silhouette stands on the board; otherwise an arrow on the edge points the way.
     */
    public void drawCastleMark(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        Vector2 site = provider.getCastleSite();
        if (site == null) return;

        float[] e = edgeClamp(project(site.x, site.y, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT), 26f);
        boolean inside = e[4] > 0.5f && provider.isCastleKnown();
        float dx = e[2], dy = e[3];
        float k = inside ? Math.min(2.2f, Math.max(1.0f, camZoom)) : 1.2f;
        float sx = e[0] - dx * (inside ? 0f : 34f);
        float sy = e[1] - dy * (inside ? 0f : 34f) + 30f * k;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color dark = withAlpha(COLOR_CASTLE_DARK, 0.42f * alpha);
        drawRectCanvas(shapes, sx - 14f * k, sy - 10f * k, 28f * k, 10f * k, dark);
        drawRectCanvas(shapes, sx - 7f * k, sy - 46f * k, 14f * k, 40f * k, dark);
        drawRectCanvas(shapes, sx - 15f * k, sy - 28f * k, 7f * k, 20f * k, dark);
        drawRectCanvas(shapes, sx + 8f * k, sy - 26f * k, 7f * k, 18f * k, dark);
        for (int i = 0; i < 4; i++) {
            drawRectCanvas(shapes, sx - 7f * k + i * 4f * k, sy - 50f * k, 2f * k, 4f * k, dark);
        }
        drawRectCanvas(shapes, sx - 1f * k, sy - 38f * k, 2f * k, 4f * k, withAlpha(COLOR_CASTLE_PURPLE, 0.6f * alpha));

        if (!inside) {
            float ax = e[4] > 0.5f ? e[0] : e[0];
            float ay = e[1];
            if (e[4] > 0.5f) {
                // In view but not yet known: point toward it from the centre instead of marking the spot.
                float[] far = edgeClamp(new Vector2(FBO_WIDTH / 2f + dx * 2000f, FBO_HEIGHT / 2f + dy * 2000f), 26f);
                ax = far[0];
                ay = far[1];
            }
            edgeArrow(shapes, ax, ay, dx, dy, COLOR_CASTLE_PURPLE, alpha);
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

    public void drawBadges(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        int L = levelIndex();
        float m = 30f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        Set<GridPoint2> chunks = new HashSet<>(surfaceChunks);
        chunks.addAll(provider.pinnedChunks());
        List<BadgeEntry> items = new ArrayList<>(6);
        for (GridPoint2 c : chunks) {
            int cx = c.x;
            int cy = c.y;
            items.clear();
            MapModel.ShelterMark shelter = provider.getShelter(cx, cy);
            if (shelter != null && shelter != MapModel.ShelterMark.RUMOURED) {
                if (shelter == MapModel.ShelterMark.HOME) items.add(new BadgeEntry("home", COLOR_BRIGHT_GOLD));
                else if (shelter == MapModel.ShelterMark.LIT) items.add(new BadgeEntry("shelter", COLOR_GOLD));
                else items.add(new BadgeEntry("shelter", COLOR_COLD_SHELTER));
            }
            if (provider.hasSeal(cx, cy)) items.add(new BadgeEntry("seal", COLOR_SEAL_AMBER));
            if (L >= 1) {
                String pin = provider.getChunkPin(cx, cy);
                if (pin != null) items.add(new BadgeEntry(pin, PIN_COLORS.getOrDefault(pin, COLOR_GOLD)));
                if (provider.getGraveCount(cx, cy) > 0) items.add(new BadgeEntry("grave", COLOR_GRAVE_STONE));
                if (provider.hasReturnPortal(cx, cy)) items.add(new BadgeEntry("portal", COLOR_PORTAL_LAVENDER));
                int depth = provider.getStratumDepth(cx, cy);
                if (depth > 0) items.add(new BadgeEntry(depth));
            }
            if (items.isEmpty()) continue;
            float h = getChunkTopZ(provider, cx, cy);
            Vector2 p = project(cx + 0.5f, cy + 0.5f, h + (L == 2 ? 15f : 8f), camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            if (p.x < -m || p.x > FBO_WIDTH + m || p.y < -m || p.y > FBO_HEIGHT + m) continue;
            for (int i = 0; i < items.size(); i++) {
                BadgeEntry it = items.get(i);
                float x = p.x + (i - (items.size() - 1) / 2f) * 14f;
                if (it.isDepth) drawDepthBadge(shapes, x, p.y, it.depth, alpha);
                else drawPlate(shapes, x, p.y, it.name, it.color, COLOR_INK, alpha);
            }
        }
        if (L == 2) drawChunkDetail(shapes, provider, alpha, false);
        shapes.end();
    }

    /**
     * The chunk view's detail: loot, events and ladders at their real tiles on every explored
     * chunk in view, tile pins and the tile cursor in the selected one. Called inside a begun
     * filled batch.
     */
    private void drawChunkDetail(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha, boolean cave) {
        float m = 80f;
        for (GridPoint2 c : cave ? caveChunks : surfaceChunks) {
            if (!onScreen(c.x + 0.5f, c.y + 0.5f, 200f)) continue;
            boolean visited = cave
                    ? provider.getCaveState(c.x, c.y) == MapKnowledgeMask.KnowledgeProvider.State.VISITED
                    : provider.getChunkState(c.x, c.y) == MapKnowledgeMask.KnowledgeProvider.State.VISITED;
            if (!visited) continue;
            ChunkTiles t = cave ? provider.getCavernTiles(c.x, c.y) : provider.getChunkTiles(c.x, c.y);
            if (t == null || t.width <= 0) continue;
            float h = cave ? 5f : getChunkTopZ(provider, c.x, c.y);
            int shown = 0;
            for (GridPoint2 at : t.itemTiles) {
                if (!t.isSeen(at.x, at.y) || shown++ >= 4) continue;
                tilePlate(shapes, c, t, at, h + 4f, "loot", PIN_COLORS.get("loot"), alpha, m);
            }
            shown = 0;
            for (GridPoint2 at : t.events.keySet()) {
                if (!t.isSeen(at.x, at.y) || shown++ >= 3) continue;
                tilePlate(shapes, c, t, at, h + 4f, "event", PIN_COLORS.get("danger"), alpha, m);
            }
            for (GridPoint2 at : t.ladders.keySet()) {
                if (!t.isSeen(at.x, at.y)) continue;
                tilePlate(shapes, c, t, at, h + 5f, "ladder", COLOR_LADDER_BRONZE, alpha, m);
            }
        }

        GridPoint2 sel = provider.getSelectedChunk();
        if (sel == null) return;
        ChunkTiles t = cave ? provider.getCavernTiles(sel.x, sel.y) : provider.getChunkTiles(sel.x, sel.y);
        if (t == null || t.width <= 0) return;
        float h = cave ? 5f : getChunkTopZ(provider, sel.x, sel.y);
        for (Map.Entry<GridPoint2, String> pin : provider.getTilePins().entrySet()) {
            tilePlate(shapes, sel, t, pin.getKey(), h + 3f, pin.getValue(), PIN_COLORS.getOrDefault(pin.getValue(), COLOR_GOLD), alpha, m);
        }
        GridPoint2 tc = provider.getTileCursor();
        if (tc != null) {
            float x0 = sel.x + tc.x / (float) t.width, y0 = sel.y + tc.y / (float) t.height;
            float x1 = sel.x + (tc.x + 1) / (float) t.width, y1 = sel.y + (tc.y + 1) / (float) t.height;
            float pulse = 0.7f + 0.3f * (float) Math.sin(elapsedTime * 5.0);
            outlineQuad(shapes, x0, y0, x1, y1, h, 3f, new Color(11 / 255f, 9 / 255f, 7 / 255f, 0.85f * alpha));
            outlineQuad(shapes, x0, y0, x1, y1, h, 1f, withAlpha(COLOR_BRIGHT_GOLD, pulse * alpha));
        }
    }

    private void tilePlate(ShapeRenderer shapes, GridPoint2 c, ChunkTiles t, GridPoint2 at, float z, String glyph, Color bg, float alpha, float m) {
        Vector2 p = project(c.x + (at.x + 0.5f) / t.width, c.y + (at.y + 0.5f) / t.height, z,
                camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        if (p.x < -m || p.x > FBO_WIDTH + m || p.y < -m || p.y > FBO_HEIGHT + m) return;
        drawPlate(shapes, p.x, p.y, glyph, bg, COLOR_INK, alpha);
    }

    private void outlineQuad(ShapeRenderer shapes, float x0, float y0, float x1, float y1, float z, float width, Color color) {
        Vector2[] pts = {
                toGdxScreen(project(x0, y0, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT)),
                toGdxScreen(project(x1, y0, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT)),
                toGdxScreen(project(x1, y1, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT)),
                toGdxScreen(project(x0, y1, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT))
        };
        for (int i = 0; i < 4; i++) {
            Vector2 a = pts[i];
            Vector2 b = pts[(i + 1) % 4];
            drawThickLine(shapes, a.x, a.y, b.x, b.y, width, color);
        }
    }

    /** War fronts: the attacker's wash over each chunk, ringed in the defender's colour. */
    private void drawFronts(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        List<MapSurface.FrontMark> fronts = provider.getFronts();
        if (fronts.isEmpty()) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (MapSurface.FrontMark f : fronts) {
            Color wash = withAlpha(f.attacker, 0.32f * alpha);
            Color rim = withAlpha(f.defender, 0.9f * alpha);
            for (GridPoint2 c : f.chunks) {
                if (!onScreen(c.x + 0.5f, c.y + 0.5f, 60f)) continue;
                float z = getChunkTopZ(provider, c.x, c.y) + 0.05f;
                Vector2 a = toGdxScreen(project(c.x, c.y, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 b = toGdxScreen(project(c.x + 1, c.y, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 d = toGdxScreen(project(c.x + 1, c.y + 1, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 e = toGdxScreen(project(c.x, c.y + 1, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                shapes.setColor(wash);
                shapes.triangle(a.x, a.y, b.x, b.y, d.x, d.y);
                shapes.triangle(a.x, a.y, d.x, d.y, e.x, e.y);
                drawDashedThickLine(shapes, a.x, a.y, b.x, b.y, 1f, rim, 3f, 2f, 0f);
                drawDashedThickLine(shapes, b.x, b.y, d.x, d.y, 1f, rim, 3f, 2f, 0f);
                drawDashedThickLine(shapes, d.x, d.y, e.x, e.y, 1f, rim, 3f, 2f, 0f);
                drawDashedThickLine(shapes, e.x, e.y, a.x, a.y, 1f, rim, 3f, 2f, 0f);
            }
        }
        shapes.end();
    }

    /** Battlefields, columns on the march and war camps, in their house's colours. */
    private void drawWarSigns(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        List<MapSurface.WarSign> signs = provider.getWarSigns();
        if (signs.isEmpty()) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (MapSurface.WarSign s : signs) {
            if (s.at == null) continue;
            float z = getChunkTopZ(provider, s.at.x, s.at.y);
            Vector2 p = project(s.at.x + 0.5f, s.at.y + 0.5f, z + 6f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            if (s.kind == MapSurface.WarSign.Kind.COLUMN && s.toward != null) {
                Vector2 a = toGdxScreen(project(s.at.x + 0.5f, s.at.y + 0.5f, z + 0.6f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                float tz = getChunkTopZ(provider, s.toward.x, s.toward.y) + 0.6f;
                Vector2 b = toGdxScreen(project(s.toward.x + 0.5f, s.toward.y + 0.5f, tz, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                drawDashedThickLine(shapes, a.x, a.y, b.x, b.y, 1f, withAlpha(s.color, 0.9f * alpha), 3f, 2f, -elapsedTime * 5f);
            }
            String glyph = s.kind == MapSurface.WarSign.Kind.BATTLEFIELD ? "grave"
                    : s.kind == MapSurface.WarSign.Kind.CAMP ? "danger" : "flag";
            drawPlate(shapes, p.x, p.y, glyph, s.color, COLOR_INK, alpha);
        }
        shapes.end();
    }

    /** A hunting or roaming megabeast: a pulsing danger plate, or an arrow on the edge when out of view. */
    private void drawThreat(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        MapModel.ThreatMark threat = provider.getThreat();
        if (threat == null || threat.chunk == null) return;
        float z = getChunkTopZ(provider, threat.chunk.x, threat.chunk.y);
        float[] e = edgeClamp(project(threat.chunk.x + 0.5f, threat.chunk.y + 0.5f, z, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT), 30f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color red = COLOR_THREAT;
        if (e[4] > 0.5f) {
            float ph = (elapsedTime * 0.7f) % 1f;
            drawRing(shapes, threat.chunk.x + 0.5f, threat.chunk.y + 0.5f, z, 0.3f + 0.2f * ph, withAlpha(red, (1f - ph) * alpha), 1f, null, 0f);
            drawPlate(shapes, e[0], e[1] - 8f * camZoom, "danger", red, COLOR_INK, alpha);
        } else {
            edgeArrow(shapes, e[0], e[1], e[2], e[3], red, alpha);
            drawPlate(shapes, e[0] - e[2] * 18f, e[1] - e[3] * 18f + 7f, "danger", red, COLOR_INK, alpha);
        }
        shapes.end();
    }

    /** Ancient gates: a dashed ray from home toward each destination, its plate clamped into view. */
    private void drawGates(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        List<MapKnowledgeMask.GateInfo> gates = provider.getGates();
        if (gates.isEmpty()) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Vector2 home = toGdxScreen(project(0.5f, 0.5f, getChunkTopZ(provider, 0, 0) + 0.5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        for (MapKnowledgeMask.GateInfo g : gates) {
            Vector2 target = project(g.chunk.x + 0.5f, g.chunk.y + 0.5f, 0.5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            Vector2 tg = toGdxScreen(target);
            Color col = withAlpha(g.tint, 0.22f * alpha);
            drawDashedThickLine(shapes, home.x, home.y, tg.x, tg.y, 1f, col, 2f, 5f, 0f);
            float[] e = edgeClamp(target, 30f);
            drawPlate(shapes, e[0], e[1], "portal", g.tint, COLOR_INK, alpha);
        }
        shapes.end();
    }

    public void drawMarks(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        float t = elapsedTime;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        GridPoint2 w = provider.getWaypointChunk();
        if (w != null) {
            float wh = getChunkTopZ(provider, w.x, w.y);
            drawRing(shapes, w.x + 0.5f, w.y + 0.5f, wh, 0.34f, withAlpha(COLOR_TABLE_BG, alpha), 3f, null, 0f);
            drawRing(shapes, w.x + 0.5f, w.y + 0.5f, wh, 0.34f, withAlpha(COLOR_GOLD, alpha), 1f, new float[]{4f, 3f}, -t * 6f);
            float ph = (t * 0.6f) % 1f;
            drawRing(shapes, w.x + 0.5f, w.y + 0.5f, wh, 0.34f + 0.25f * ph, withAlpha(COLOR_GOLD, (1f - ph) * 0.8f * alpha), 1f, null, 0f);
            Vector2 wp = project(w.x + 0.5f, w.y + 0.5f, wh, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            drawPlate(shapes, wp.x - 8f, wp.y - 6f, "flag", COLOR_GOLD, COLOR_INK, alpha);
        }

        GridPoint2 sug = provider.getSuggestionChunk();
        if (sug != null && (w == null || !sug.equals(w))) {
            float sh = getChunkTopZ(provider, sug.x, sug.y);
            float a = 0.45f + 0.25f * (float) Math.sin(t * 2.4f);
            drawRing(shapes, sug.x + 0.5f, sug.y + 0.5f, sh, 0.3f, withAlpha(COLOR_SUGGEST_RING, a * alpha), 1f, new float[]{2f, 3f}, t * 4f);
            Vector2 sp = project(sug.x + 0.5f, sug.y + 0.5f, sh, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            drawPlate(shapes, sp.x - 16f, sp.y - 4f, "flag", withAlpha(COLOR_RUMOUR_BG, a), withAlpha(COLOR_SUGGEST_INK, a), alpha);
        }
        shapes.end();
    }

    public void drawPlayerMark(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        GridPoint2 pc = provider.getPlayerChunk();
        if (pc == null) return;
        int cx = pc.x, cy = pc.y;
        float wx = cx + provider.getPlayerU(cx, cy);
        float wy = cy + provider.getPlayerV(cx, cy);
        float faceX = provider.getPlayerFaceX(cx, cy);
        float faceY = provider.getPlayerFaceY(cx, cy);
        float h = getChunkTopZ(provider, cx, cy);
        boolean isChunk = levelOf(camZoom) == ZoomLevel.CHUNK;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float ph = (elapsedTime * 0.8f) % 1f;
        float ringR = (isChunk ? 0.04f : 0.08f) + (isChunk ? 0.06f : 0.16f) * ph;
        drawRing(shapes, wx, wy, h, ringR, withAlpha(COLOR_CYAN, (1f - ph) * alpha), 1f, null, 0f);

        Vector2 p = project(wx, wy, h, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
        Color tableBg = withAlpha(COLOR_TABLE_BG, alpha);
        Color cyan = withAlpha(COLOR_CYAN, alpha);
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
            int[] rows = {1, 3, 5, 7, 5, 3, 1};
            for (int j = 0; j < rows.length; j++) {
                drawRectCanvas(shapes, x - (rows[j] + 1) / 2f, y - 4 + j, rows[j] + 2, 1, tableBg);
            }
            drawRectCanvas(shapes, x, y - 5, 1, 1, tableBg);
            drawRectCanvas(shapes, x, y + 3, 1, 1, tableBg);
            for (int j = 0; j < rows.length; j++) {
                drawRectCanvas(shapes, x - (rows[j] - 1) / 2f, y - 4 + j, rows[j], 1, cyan);
            }
            drawRectCanvas(shapes, x - 1, y - 2, 1, 1, withAlpha(COLOR_CYAN_HI, alpha));
        }
        shapes.end();
    }

    /** The stratum's badges: ladders, loot in explored caves, and the player's tether to the ladder up. */
    public void drawCaveBadges(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float alpha) {
        if (provider == null || alpha <= 0.001f) return;
        int L = levelIndex();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        Vector2 ladderPos = null;
        GridPoint2 pc = provider.getPlayerChunk();
        for (GridPoint2 c : caveChunks) {
            ChunkTiles t = provider.getCavernTiles(c.x, c.y);
            if (t == null || t.width <= 0) continue;
            for (Map.Entry<GridPoint2, Ladder.LadderType> e : t.ladders.entrySet()) {
                // One plate per chunk, on its way up where it has one; the chunk detail draws them all at their tiles.
                if (L == 2 || (e.getValue() != Ladder.LadderType.UP && t.ladders.containsValue(Ladder.LadderType.UP))) continue;
                GridPoint2 at = e.getKey();
                Vector2 lp = project(c.x + (at.x + 0.5f) / t.width, c.y + (at.y + 0.5f) / t.height, 16f,
                        camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                if (ladderPos == null && c.equals(pc) && e.getValue() == Ladder.LadderType.UP) ladderPos = lp;
                drawPlate(shapes, lp.x, lp.y, "ladder", COLOR_LADDER_BRONZE, COLOR_INK, alpha);
                break;
            }
            if (L == 1 && !t.itemTiles.isEmpty()) {
                Vector2 p = project(c.x + 0.5f, c.y + 0.5f, 9f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
                drawPlate(shapes, p.x, p.y, "loot", PIN_COLORS.get("loot"), COLOR_INK, alpha);
            }
        }
        if (L == 2) drawChunkDetail(shapes, provider, alpha, true);

        if (pc != null) {
            float pu = provider.getPlayerU(pc.x, pc.y);
            float pv = provider.getPlayerV(pc.x, pc.y);
            Vector2 p = project(pc.x + pu, pc.y + pv, L == 2 ? 12f : 22f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            float x = Math.round(p.x);
            float y = Math.round(p.y);
            Color cyan = withAlpha(COLOR_CYAN, 0.75f * alpha);
            Vector2 d0 = toGdxScreen(new Vector2(x + 0.5f, y - 4f));
            Vector2 d1 = toGdxScreen(new Vector2(x + 4.5f, y));
            Vector2 d2 = toGdxScreen(new Vector2(x + 0.5f, y + 4f));
            Vector2 d3 = toGdxScreen(new Vector2(x - 3.5f, y));
            drawThickLine(shapes, d0.x, d0.y, d1.x, d1.y, 1f, cyan);
            drawThickLine(shapes, d1.x, d1.y, d2.x, d2.y, 1f, cyan);
            drawThickLine(shapes, d2.x, d2.y, d3.x, d3.y, 1f, cyan);
            drawThickLine(shapes, d3.x, d3.y, d0.x, d0.y, 1f, cyan);
            Vector2 foot = toGdxScreen(project(pc.x + pu, pc.y + pv, 5f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
            Vector2 start = toGdxScreen(new Vector2(x + 0.5f, y + 5f));
            drawDashedThickLine(shapes, start.x, start.y, foot.x, foot.y, 1f, cyan, 2f, 2f, 0f);
        }
        shapes.end();
    }

    public void drawSelection(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider) {
        if (provider == null) return;
        GridPoint2 sel = provider.getSelectedChunk();
        if (sel == null) return;

        float z = (floorT > 0.5f) ? 5.0f : getChunkTopZ(provider, sel.x, sel.y);
        Vector2[] pts = {
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
            drawThickLine(shapes, pts[i].x, pts[i].y, pts[(i + 1) % 4].x, pts[(i + 1) % 4].y, 3f, darkBorder);
        }
        float pulse = 0.7f + 0.3f * (float) Math.sin(elapsedTime * 3.0);
        Color goldBorder = new Color(COLOR_BRIGHT_GOLD.r, COLOR_BRIGHT_GOLD.g, COLOR_BRIGHT_GOLD.b, pulse);
        for (int i = 0; i < 4; i++) {
            drawThickLine(shapes, pts[i].x, pts[i].y, pts[(i + 1) % 4].x, pts[(i + 1) % 4].y, 1f, goldBorder);
        }
        shapes.setColor(COLOR_BRIGHT_GOLD);
        for (Vector2 p : pts) {
            shapes.rect(Math.round(p.x) - 1, Math.round(p.y) - 1, 3, 3);
        }
        shapes.end();
    }

    public void renderOverlays(ShapeRenderer shapes, MapKnowledgeMask.KnowledgeProvider provider, float ft) {
        if (provider == null) return;
        float sa = Math.max(0f, 1f - ft * 2f);
        float ca = Math.max(0f, ft * 2f - 1f);

        if (sa > 0.001f) {
            drawFronts(shapes, provider, sa);
            drawRoads(shapes, provider, sa);
            drawSuggestedRoute(shapes, provider, sa);
            drawRumour(shapes, provider, sa);
            if (levelIndex() >= 1) drawGates(shapes, provider, sa);
            drawCastleMark(shapes, provider, sa);
            drawWarSigns(shapes, provider, sa);
            drawBadges(shapes, provider, sa);
            drawMarks(shapes, provider, sa);
            drawThreat(shapes, provider, sa);
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
        for (FrameBuffer b : new FrameBuffer[]{surfaceScene, caveScene, ghostFbo, fbo}) {
            if (b != null) b.dispose();
        }
        surfaceScene = caveScene = ghostFbo = fbo = null;
        if (fboBatch != null) {
            fboBatch.dispose();
            fboBatch = null;
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
            shapeRenderer = null;
        }
        noiseTextures.dispose();
        surfaceMask.dispose();
        caveMask.dispose();
        if (fogShader != null) {
            fogShader.dispose();
            fogShader = null;
        }
        initialized = false;
    }
}
