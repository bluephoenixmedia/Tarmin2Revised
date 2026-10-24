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

    public static final Color COLOR_LADDER_HOLE = new Color(0x0a / 255f, 0x08 / 255f, 0x06 / 255f, 1f); // #0a0806
    public static final Color COLOR_LADDER_WOOD = new Color(0x9a / 255f, 0x74 / 255f, 0x42 / 255f, 1f); // #9a7442

    public static final Color COLOR_CHEST_TOP = new Color(0xa0 / 255f, 0x70 / 255f, 0x2f / 255f, 1f); // #a0702f
    public static final Color COLOR_CHEST_LIT = new Color(0x7a / 255f, 0x4f / 255f, 0x22 / 255f, 1f); // #7a4f22
    public static final Color COLOR_CHEST_SHADE = new Color(0x55 / 255f, 0x36 / 255f, 0x14 / 255f, 1f); // #553614

    public static final Color COLOR_PLAYER_CYAN = new Color(0x6c / 255f, 0xcf / 255f, 0xe0 / 255f, 1f); // #6CCFE0
    public static final Color COLOR_PLAYER_CYAN_DARK = new Color(0x3d / 255f, 0x8a / 255f, 0x99 / 255f, 1f); // #3d8a99
    public static final Color COLOR_PLAYER_HEAD = new Color(0xe3 / 255f, 0xcf / 255f, 0xa6 / 255f, 1f); // #e3cfa6

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
    private SpriteBatch fboBatch;
    private ShapeRenderer shapeRenderer;

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

        fboBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();

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

        boolean cave = (floor != 1);
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

    /**
     * Renders the low-res FBO scene and draws it upscaled into the destination bounds.
     */
    public void render(Batch batch, float targetX, float targetY, float targetWidth, float targetHeight,
                       MapKnowledgeMask.KnowledgeProvider provider) {
        if (!initialized) {
            init();
            if (provider != null && knowledgeMask.getMaskTexture() == null) {
                knowledgeMask.rebuild(provider);
            }
        }

        if (fbo == null) return;

        // 1. Render scene to 720x448 low-res buffer
        fbo.begin();
        Gdx.gl.glClearColor(COLOR_TABLE_BG.r, COLOR_TABLE_BG.g, COLOR_TABLE_BG.b, 1f); // #0B0907
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Render 3D diorama scene (board, chunk blocks, floor tiles, maze walls)
        renderDioramaScene(provider);

        // Render fog shader pass on top
        if (fogShader != null && fogShader.isCompiled() && knowledgeMask.getMaskTexture() != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            fboBatch.begin();
            Vector2 orig = project(0f, 0f, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT);
            fogShader.render(fboBatch, noiseTextures, knowledgeMask.getMaskTexture(),
                    elapsedTime, camX, camY, camZoom, camRot, orig.x, orig.y,
                    fogStrength, isCave, FBO_WIDTH, FBO_HEIGHT);
            fboBatch.end();
        }

        fbo.end();

        // 2. Draw upscaled FBO to screen stage batch
        batch.draw(fboRegion, targetX, targetY, targetWidth, targetHeight);
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

    private final List<GridPoint2> sortedChunks = new ArrayList<>(CHUNK_GRID_SIZE * CHUNK_GRID_SIZE);
    private final List<WallBox> wallBoxPool = new ArrayList<>(64);
    private final List<Prop> chunkProps = new ArrayList<>(64);
    private final List<RenderItem> renderItems = new ArrayList<>(128);

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

    private static Vector2 toGdxScreen(Vector2 topDown) {
        return new Vector2(topDown.x, FBO_HEIGHT - topDown.y);
    }

    @Override
    public void dispose() {
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
