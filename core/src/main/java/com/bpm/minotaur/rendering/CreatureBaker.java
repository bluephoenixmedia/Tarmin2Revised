package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelCache;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.monster.stitcher.StitchedCreature;

import java.util.Random;

/**
 * Renders a {@link StitchedCreature} off-screen and produces a
 * {@link BakedMonsterSprite} whose textures are ready for the existing
 * EntityRenderer column-draw pipeline.
 *
 * Two-pass bake per angle:
 *   Pass 1 — ModelBatch renders the 3D assembly to sceneFbo.
 *   Pass 2 — SpriteBatch + creature_stylize.frag converts the raw 3D render
 *             into chunky pixel-art matching the existing monster sprite style.
 *
 * Call {@link #randomizePalette(long)} before baking to get a different colour
 * scheme per creature.  The default palette is the original purple/cream look.
 */
public class CreatureBaker implements Disposable {

    private static final int BAKE_W = 256;
    private static final int BAKE_H = 256;

    /** Set true to skip the palette shader and output the raw lit 3D render. */
    public static boolean DEBUG_SKIP_STYLIZE = false;

    private final FrameBuffer   sceneFbo;
    private final FrameBuffer   styleFbo;
    private final ModelBatch    modelBatch;
    private final SpriteBatch   spriteBatch;
    private final OrthographicCamera bakeCamera;
    private final ShaderProgram styleShader;
    private final Environment   env;
    private final boolean       shaderAvailable;

    // ── Palette state (randomized per creature) ───────────────────────────────
    // palette[i*3 .. i*3+2] = RGB for tone i  (5 tones × 3 floats = 15 values)
    private final float[] palette    = new float[15];
    // Luminance thresholds separating the 5 tones (must stay ascending in [0,1])
    private final float[] thresholds = new float[4];
    private float blockSize;

    public CreatureBaker() {
        sceneFbo    = new FrameBuffer(Pixmap.Format.RGBA8888, BAKE_W, BAKE_H, true);
        styleFbo    = new FrameBuffer(Pixmap.Format.RGBA8888, BAKE_W, BAKE_H, false);
        modelBatch  = new ModelBatch();
        spriteBatch = new SpriteBatch();

        bakeCamera = new OrthographicCamera(2.2f, 2.2f);
        bakeCamera.position.set(0f, 0.8f, 5f);
        bakeCamera.lookAt(0f, 0.8f, 0f);
        bakeCamera.near = 0.1f;
        bakeCamera.far  = 20f;
        bakeCamera.update();

        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f));
        env.add(new DirectionalLight().set(1f, 0.95f, 0.9f,  -0.2f, -0.7f, -1f));
        env.add(new DirectionalLight().set(0.1f, 0.1f, 0.15f, 0.3f,  0.5f,  1f));

        ShaderProgram shader = null;
        boolean available    = false;
        try {
            shader = new ShaderProgram(
                    Gdx.files.internal("shaders/creature_stylize.vert"),
                    Gdx.files.internal("shaders/creature_stylize.frag"));
            if (!shader.isCompiled()) {
                Gdx.app.error("CreatureBaker", "creature_stylize shader failed: " + shader.getLog());
            } else {
                available = true;
            }
        } catch (Exception e) {
            Gdx.app.error("CreatureBaker", "Could not load creature_stylize shader: " + e.getMessage());
        }
        styleShader     = shader;
        shaderAvailable = available;

        setDefaultPalette();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Randomizes the palette, thresholds, and pixel block-size using the given
     * seed.  Call before {@link #bakeAllFacings} to get a different look for
     * each creature.  The seed is typically the creature's generation seed so
     * the same seed always produces the same palette.
     */
    public void randomizePalette(long seed) {
        Random rng = new Random(seed);

        float hue = rng.nextFloat() * 360f;
        float sat = 0.45f + rng.nextFloat() * 0.45f;   // 0.45–0.90

        // Tone 0: near-black outline, slight hue tint
        setTone(0, hsvToRgb(hue, 0.55f, 0.05f + rng.nextFloat() * 0.08f));
        // Tone 1: dark
        setTone(1, hsvToRgb(hue, sat,        0.18f + rng.nextFloat() * 0.12f));
        // Tone 2: mid
        setTone(2, hsvToRgb(hue, sat,        0.35f + rng.nextFloat() * 0.15f));
        // Tone 3: light, slightly desaturated
        setTone(3, hsvToRgb(hue, sat * 0.7f, 0.55f + rng.nextFloat() * 0.15f));
        // Tone 4: highlight — low saturation, high value (cream/bone feel)
        setTone(4, hsvToRgb(hue, sat * 0.25f, 0.78f + rng.nextFloat() * 0.15f));

        // Thresholds: keep ascending, start low, step ~0.18
        float base = 0.10f + rng.nextFloat() * 0.10f;
        float step = 0.15f + rng.nextFloat() * 0.08f;
        for (int i = 0; i < 4; i++) thresholds[i] = base + step * i;

        // Block size: 2 (64px fine) → 5 (26px chunky)
        blockSize = 2f + rng.nextInt(4);
    }

    /** Resets to the original purple/cream palette used in the parts-grid view. */
    public void setDefaultPalette() {
        setTone(0, new float[]{0.051f, 0.000f, 0.063f});
        setTone(1, new float[]{0.239f, 0.102f, 0.361f});
        setTone(2, new float[]{0.416f, 0.180f, 0.588f});
        setTone(3, new float[]{0.616f, 0.486f, 0.769f});
        setTone(4, new float[]{0.831f, 0.769f, 0.604f});
        thresholds[0] = 0.15f;
        thresholds[1] = 0.35f;
        thresholds[2] = 0.55f;
        thresholds[3] = 0.75f;
        blockSize = 4f;
    }

    public BakedMonsterSprite bakeAllFacings(StitchedCreature creature) {
        Texture north = bakeAtAngle(creature,   0f);
        Texture east  = bakeAtAngle(creature,  90f);
        Texture south = bakeAtAngle(creature, 180f);
        Texture west  = bakeAtAngle(creature, 270f);
        return new BakedMonsterSprite(north, east, south, west);
    }

    public Texture bakeAtAngle(StitchedCreature creature, float yDegrees) {
        if (creature == null) return createFallbackTexture();

        creature.setFacing(yDegrees);

        // Pass 1: render 3D assembly directly (single draw call per material)
        sceneFbo.begin();
        Gdx.gl.glViewport(0, 0, BAKE_W, BAKE_H);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        modelBatch.begin(bakeCamera);
        creature.render(modelBatch, env);
        modelBatch.end();

        if (DEBUG_SKIP_STYLIZE) {
            Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, BAKE_W, BAKE_H);
            sceneFbo.end();
            Pixmap flipped = flipPixmap(pm);
            pm.dispose();
            Texture result = new Texture(flipped);
            result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            flipped.dispose();
            return result;
        }
        sceneFbo.end();

        // Pass 2: stylize
        styleFbo.begin();
        Gdx.gl.glViewport(0, 0, BAKE_W, BAKE_H);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, BAKE_W, BAKE_H);

        if (shaderAvailable) {
            spriteBatch.setShader(styleShader);
            spriteBatch.begin();
            styleShader.setUniformf("u_resolution", BAKE_W, BAKE_H);
            styleShader.setUniformf("u_blockSize",  blockSize);
            styleShader.setUniformf("u_tone0", palette[0],  palette[1],  palette[2]);
            styleShader.setUniformf("u_tone1", palette[3],  palette[4],  palette[5]);
            styleShader.setUniformf("u_tone2", palette[6],  palette[7],  palette[8]);
            styleShader.setUniformf("u_tone3", palette[9],  palette[10], palette[11]);
            styleShader.setUniformf("u_tone4", palette[12], palette[13], palette[14]);
            styleShader.setUniformf("u_thresholds",
                    thresholds[0], thresholds[1], thresholds[2], thresholds[3]);
            spriteBatch.draw(sceneFbo.getColorBufferTexture(),
                    0f, BAKE_H, (float) BAKE_W, -(float) BAKE_H);
            spriteBatch.end();
            spriteBatch.setShader(null);
        } else {
            spriteBatch.begin();
            spriteBatch.draw(sceneFbo.getColorBufferTexture(),
                    0f, BAKE_H, (float) BAKE_W, -(float) BAKE_H);
            spriteBatch.end();
        }

        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, BAKE_W, BAKE_H);
        styleFbo.end();

        Pixmap flipped = flipPixmap(pm);
        pm.dispose();
        Texture result = new Texture(flipped);
        result.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        flipped.dispose();
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setTone(int index, float[] rgb) {
        palette[index * 3]     = rgb[0];
        palette[index * 3 + 1] = rgb[1];
        palette[index * 3 + 2] = rgb[2];
    }

    /** Standard HSV → RGB conversion.  h in [0,360], s and v in [0,1]. */
    private static float[] hsvToRgb(float h, float s, float v) {
        int   i = (int)(h / 60f) % 6;
        float f = (h / 60f) - (int)(h / 60f);
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        switch (i) {
            case 0:  return new float[]{v, t, p};
            case 1:  return new float[]{q, v, p};
            case 2:  return new float[]{p, v, t};
            case 3:  return new float[]{p, q, v};
            case 4:  return new float[]{t, p, v};
            default: return new float[]{v, p, q};
        }
    }

    private Pixmap flipPixmap(Pixmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Pixmap dst = new Pixmap(w, h, src.getFormat());
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                dst.drawPixel(x, h - 1 - y, src.getPixel(x, y));
        return dst;
    }

    private Texture createFallbackTexture() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1f, 0f, 1f, 1f);
        pm.fill();
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    @Override
    public void dispose() {
        sceneFbo.dispose();
        styleFbo.dispose();
        modelBatch.dispose();
        spriteBatch.dispose();
        if (styleShader != null) styleShader.dispose();
    }
}
