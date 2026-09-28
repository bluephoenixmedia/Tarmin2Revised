package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;

/**
 * What lies behind and beneath the attract-mode maze.
 *
 * <p>The flyover used to draw a sky dome, so every gap in the maze floor -- and the whole of the
 * screen above the horizon -- showed a lit seascape with pyramids on it. The camera is supposed
 * to be crossing the ruin of Tarmin, and a holiday postcard visible through the holes in the
 * floor says otherwise. This replaces the dome with darkness, and scatters faint red flashes
 * through the space so the dark reads as depth rather than as an unfinished render.
 *
 * <p><b>Where the embers are.</b> They are not stored. The volume is divided into cells and each
 * cell's single ember is derived from a hash of its integer coordinates, so an ember is in the
 * same place every time the camera passes it, nothing is allocated per frame, and the density is
 * the same everywhere in a world 100 chunks across. Only cells near the camera are visited, and
 * only an ember that is mid-flash emits a quad -- typically a few dozen of the six hundred or so
 * candidates.
 *
 * <p><b>What a flash looks like.</b> Each ember has its own period and phase, so they never
 * pulse together, and the brightness curve is a narrow spike rather than a sine: mostly dark,
 * with a brief swell. They are drawn additively with the depth test on and depth writes off, so
 * the maze occludes them -- an ember below the floor only shows through a gap, which is the
 * point.
 */
public final class VoidBackdrop implements Disposable {

    /** The dark the flyover sits in. Warm rather than blue-black, to match the Ember palette. */
    public static final Color VOID_COLOR = new Color(0.035f, 0.027f, 0.024f, 1f);

    /** Ember tint at the peak of a flash. */
    private static final Color EMBER_COLOR = new Color(0.85f, 0.14f, 0.07f, 1f);

    /** Cell size in world units. One ember lives somewhere inside each cell. */
    private static final float CELL_XZ = 11f;
    private static final float CELL_Y = 9f;

    /** How many cells out from the camera to consider, per axis. */
    private static final int RADIUS_XZ = 5;

    /**
     * The vertical band embers occupy, in world units.
     *
     * <p>Anchored to the world, not the camera: the maze floor is at y=0 and its walls stand a
     * few units above it, so the band runs from well below the floor -- the abyss a gap looks
     * down into -- to a little above the wall tops.
     */
    private static final float BAND_MIN_Y = -54f;
    private static final float BAND_MAX_Y = 10f;

    /** Peak size of an ember quad in world units. Small: these are sparks, not lamps. */
    private static final float SIZE_MIN = 0.22f;
    private static final float SIZE_MAX = 0.70f;

    /** Seconds between one ember's flashes. */
    private static final float PERIOD_MIN = 2.4f;
    private static final float PERIOD_MAX = 11f;

    /** Brightness below this is not worth a draw call. */
    private static final float VISIBLE_THRESHOLD = 0.02f;

    /** Strongest an ember gets. Faint is the brief. */
    private static final float PEAK_ALPHA = 0.55f;

    private final DynamicQuadBatcher batcher = new DynamicQuadBatcher();
    private final Vector3 camRight = new Vector3();
    private final Vector3 camUp = new Vector3();
    private final Color scratchColor = new Color();

    private Texture emberTexture;
    private float clock;

    public VoidBackdrop() {
        emberTexture = buildEmberTexture();
    }

    /** Advances the flash clock. */
    public void update(float delta) {
        clock += delta;
    }

    /** Clears the screen to the void. Replaces the sky dome pass. */
    public void clear() {
        Gdx.gl.glClearColor(VOID_COLOR.r, VOID_COLOR.g, VOID_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Pulls a biome's fog colour most of the way to the void.
     *
     * <p>Distance fog blends geometry toward this colour, so leaving it at the biome's daylight
     * haze would draw a bright band along the horizon of a black sky. A little of the biome is
     * kept, so a desert chunk still dissolves into something faintly warmer than a forest one.
     */
    public void applyFog(Color fogColor) {
        fogColor.lerp(VOID_COLOR, 0.88f);
    }

    /**
     * Emits every ember currently mid-flash within range of the camera.
     *
     * <p>Call after the terrain pass, with the terrain shader still bound. The lighting uniforms
     * are set for an unlit pass here and are the caller's to restore -- the attract renderer
     * sets them afresh every frame, so it does not need to.
     */
    public void render(PerspectiveCamera camera, ShaderProgram shader) {
        if (emberTexture == null || shader == null) {
            return;
        }

        camRight.set(camera.direction).crs(camera.up).nor();
        camUp.set(camRight).crs(camera.direction).nor();

        int baseCX = MathUtils.floor(camera.position.x / CELL_XZ);
        int baseCZ = MathUtils.floor(camera.position.z / CELL_XZ);
        int minCY = MathUtils.floor(BAND_MIN_Y / CELL_Y);
        int maxCY = MathUtils.floor(BAND_MAX_Y / CELL_Y);

        float reach = RADIUS_XZ * CELL_XZ;
        float reachSq = reach * reach;
        boolean any = false;

        for (int cz = baseCZ - RADIUS_XZ; cz <= baseCZ + RADIUS_XZ; cz++) {
            for (int cx = baseCX - RADIUS_XZ; cx <= baseCX + RADIUS_XZ; cx++) {
                for (int cy = minCY; cy <= maxCY; cy++) {
                    float r1 = hash(cx, cy, cz, 1);
                    float r2 = hash(cx, cy, cz, 2);
                    float r3 = hash(cx, cy, cz, 3);
                    float r4 = hash(cx, cy, cz, 4);
                    float r5 = hash(cx, cy, cz, 5);
                    float r6 = hash(cx, cy, cz, 6);

                    // A third of the cells are simply empty, so the scatter is uneven rather
                    // than a grid of one-per-box.
                    if (r6 < 0.34f) {
                        continue;
                    }

                    float period = PERIOD_MIN + r4 * (PERIOD_MAX - PERIOD_MIN);
                    float brightness = flash((clock + r5 * period) / period);
                    if (brightness < VISIBLE_THRESHOLD) {
                        continue;
                    }

                    float ex = (cx + r1) * CELL_XZ;
                    float ey = (cy + r2) * CELL_Y;
                    float ez = (cz + r3) * CELL_XZ;

                    float dx = ex - camera.position.x;
                    float dz = ez - camera.position.z;
                    if (dx * dx + dz * dz > reachSq) {
                        continue;
                    }

                    float size = (SIZE_MIN + r1 * (SIZE_MAX - SIZE_MIN)) * (0.6f + 0.4f * brightness);
                    scratchColor.set(EMBER_COLOR);
                    // A cooler ember is a deeper red; a hot one pushes toward orange.
                    scratchColor.g += 0.22f * r2 * brightness;
                    scratchColor.a = PEAK_ALPHA * brightness;

                    addEmberQuad(ex, ey, ez, size, scratchColor);
                    any = true;
                }
            }
        }

        if (!any) {
            return;
        }

        // Unlit and additive: an ember is light, not a surface.
        shader.setUniformi("u_retroMode", 0);
        shader.setUniformf("u_doomFactor", 1f);
        shader.setUniformf("u_ambientColor", 1f, 1f, 1f);
        shader.setUniformf("u_dirLightColor", 0f, 0f, 0f);
        shader.setUniformi("u_numLights", 0);
        shader.setUniformf("u_wetness", 0f);
        shader.setUniformf("u_snowAccumulation", 0f);
        shader.setUniformf("u_skyRimStrength", 0f);
        shader.setUniformf("u_alphaCutoff", 0f);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        // Depth test on so the maze hides what is behind it; depth write off so embers do not
        // occlude each other or anything drawn after them.
        Gdx.gl.glDepthMask(false);

        batcher.flush(shader, emberTexture);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void addEmberQuad(float x, float y, float z, float size, Color color) {
        float h = size * 0.5f;
        float rx = camRight.x * h, ry = camRight.y * h, rz = camRight.z * h;
        float ux = camUp.x * h, uy = camUp.y * h, uz = camUp.z * h;

        batcher.addParticleQuad(
                x - rx - ux, y - ry - uy, z - rz - uz,
                x + rx - ux, y + ry - uy, z + rz - uz,
                x + rx + ux, y + ry + uy, z + rz + uz,
                x - rx + ux, y - ry + uy, z - rz + uz,
                -camRight.y, 1f, 0f,
                color);
    }

    /**
     * Brightness over one cycle, as a narrow spike.
     *
     * <p>{@code t} is the phase in turns. An ember is dark for most of its period, swells over
     * about a tenth of it and fades over the next tenth -- which reads as a flash rather than as
     * a throb.
     */
    private static float flash(float t) {
        float phase = t - MathUtils.floor(t);
        if (phase > 0.22f) {
            return 0f;
        }
        float k = phase / 0.22f;
        // Fast attack, slower decay.
        return k < 0.3f
                ? (k / 0.3f)
                : 1f - ((k - 0.3f) / 0.7f) * ((k - 0.3f) / 0.7f);
    }

    /**
     * A stable pseudo-random value in [0,1) for a cell and a channel.
     *
     * <p>Deliberately not {@code Random}: the same cell must give the same ember on every frame
     * and on every run, or the void would boil.
     */
    private static float hash(int x, int y, int z, int channel) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647 + channel * 1013904223;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0x00FFFFFF) / (float) 0x01000000;
    }

    /** A soft round falloff, so an ember is a glow rather than a square. */
    private static Texture buildEmberTexture() {
        int size = 32;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float c = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - c) / c;
                float dy = (y - c) / c;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp(1f - d, 0f, 1f);
                // Squared falloff plus a small hot core.
                a = a * a * (0.75f + 0.25f * a);
                p.setColor(1f, 1f, 1f, a);
                p.drawPixel(x, y);
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    @Override
    public void dispose() {
        if (emberTexture != null) {
            emberTexture.dispose();
            emberTexture = null;
        }
        batcher.dispose();
    }
}
