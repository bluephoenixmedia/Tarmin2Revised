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
import com.bpm.minotaur.generation.Biome;

import java.util.ArrayList;
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

    static final class WallBox {
        float x0, y0, x1, y1;
        float z0, z1;
        float depth;
    }

    private final List<GridPoint2> sortedChunks = new ArrayList<>(CHUNK_GRID_SIZE * CHUNK_GRID_SIZE);
    private final List<WallBox> chunkWalls = new ArrayList<>(64);

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
                drawGlimpsedChunk(c.x, c.y, biome);
            }
        }

        shapeRenderer.end();

        drawBoardLines(shapeRenderer);
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

            // Maze wall boxes (+4.2 height)
            if (biome == Biome.MAZE || isCave) {
                chunkWalls.clear();
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
                            chunkWalls.add(w);
                        }
                    }
                }
                chunkWalls.sort((a, b) -> Float.compare(a.depth, b.depth));
                for (WallBox w : chunkWalls) {
                    drawBox(shapeRenderer, w.x0, w.y0, w.x1, w.y1, w.z0, w.z1,
                            COLOR_WALL_TOP, COLOR_WALL_LIT, COLOR_WALL_SHADE);
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
    }

    private void drawGlimpsedChunk(int cx, int cy, Biome biome) {
        BiomeColors B = getBiomeColors(biome);
        float h = (biome == Biome.OCEAN) ? 1.2f : 2.5f;

        Color top = mix(B.top, COLOR_GLIMPSED_TOP_TINT, 0.5f);
        Color sideA = mix(B.sideA, COLOR_GLIMPSED_SIDE_A_TINT, 0.5f);
        Color sideB = mix(B.sideB, COLOR_GLIMPSED_SIDE_B_TINT, 0.5f);

        drawBox(shapeRenderer, cx, cy, cx + 1, cy + 1, 0f, h, top, sideA, sideB);
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
