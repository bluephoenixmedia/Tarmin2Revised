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
        Gdx.gl.glClearColor(11f / 255f, 9f / 255f, 7f / 255f, 1f); // #0B0907
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Render flat diamond placeholder grid
        renderFlatDiamondGrid(provider);

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

    private void renderFlatDiamondGrid(MapKnowledgeMask.KnowledgeProvider provider) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw 10x10 chunk diamonds
        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                MapKnowledgeMask.KnowledgeProvider.State state = (provider != null)
                        ? provider.getChunkState(cx, cy)
                        : MapKnowledgeMask.KnowledgeProvider.State.UNKNOWN;

                Color fillColor;
                if (state == MapKnowledgeMask.KnowledgeProvider.State.VISITED) {
                    fillColor = new Color(0.18f, 0.16f, 0.14f, 1f); // Visited stone #2e2923
                } else if (state == MapKnowledgeMask.KnowledgeProvider.State.GLIMPSED) {
                    fillColor = new Color(0.13f, 0.15f, 0.17f, 1f); // Glimpsed silhouette #20262c
                } else {
                    fillColor = new Color(0.08f, 0.07f, 0.06f, 1f); // Unknown dark #141210
                }
                shapeRenderer.setColor(fillColor);

                Vector2 c0 = toGdxScreen(project(cx, cy, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 c1 = toGdxScreen(project(cx + 1, cy, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 c2 = toGdxScreen(project(cx + 1, cy + 1, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 c3 = toGdxScreen(project(cx, cy + 1, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

                shapeRenderer.triangle(c0.x, c0.y, c1.x, c1.y, c2.x, c2.y);
                shapeRenderer.triangle(c0.x, c0.y, c2.x, c2.y, c3.x, c3.y);
            }
        }
        shapeRenderer.end();

        // Draw wireframe grid lines
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(new Color(0.23f, 0.16f, 0.09f, 0.6f)); // #3A2A18

        for (int cy = 0; cy < CHUNK_GRID_SIZE; cy++) {
            for (int cx = 0; cx < CHUNK_GRID_SIZE; cx++) {
                Vector2 c0 = toGdxScreen(project(cx, cy, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 c1 = toGdxScreen(project(cx + 1, cy, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 c2 = toGdxScreen(project(cx + 1, cy + 1, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
                Vector2 c3 = toGdxScreen(project(cx, cy + 1, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

                shapeRenderer.line(c0.x, c0.y, c1.x, c1.y);
                shapeRenderer.line(c1.x, c1.y, c2.x, c2.y);
                shapeRenderer.line(c2.x, c2.y, c3.x, c3.y);
                shapeRenderer.line(c3.x, c3.y, c0.x, c0.y);
            }
        }

        // Draw world board border
        shapeRenderer.setColor(new Color(0.43f, 0.30f, 0.15f, 0.9f)); // #6E4D25
        Vector2 b0 = toGdxScreen(project(0, 0, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 b1 = toGdxScreen(project(CHUNK_GRID_SIZE, 0, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 b2 = toGdxScreen(project(CHUNK_GRID_SIZE, CHUNK_GRID_SIZE, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));
        Vector2 b3 = toGdxScreen(project(0, CHUNK_GRID_SIZE, 0f, camX, camY, camZoom, camRot, FBO_WIDTH, FBO_HEIGHT));

        shapeRenderer.line(b0.x, b0.y, b1.x, b1.y);
        shapeRenderer.line(b1.x, b1.y, b2.x, b2.y);
        shapeRenderer.line(b2.x, b2.y, b3.x, b3.y);
        shapeRenderer.line(b3.x, b3.y, b0.x, b0.y);

        shapeRenderer.end();
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
