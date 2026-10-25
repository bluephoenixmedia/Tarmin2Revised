package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/**
 * Procedurally generates the 4 tiling fBm value-noise textures (128x128) used by the
 * expedition map's isometric fog shader.
 *
 * <p>Matches the mathematical specifications of {@code noise()} and {@code tex()} in the
 * approved visual prototype ({@code docs/DEsign/tarmin-map-handoff/prototype/Main.dc.html}).
 */
public final class MapNoiseTextures implements Disposable {

    public static final int NOISE_SIZE = 128;

    public static final int[] RGB_BODY = {86, 98, 112};
    public static final int[] RGB_SHADE = {32, 40, 50};
    public static final int[] RGB_HI = {198, 210, 220};
    public static final int[] RGB_WISP = {214, 224, 232};

    private Texture texBody;
    private Texture texShade;
    private Texture texHi;
    private Texture texWisp;

    public Texture getTexBody() {
        return texBody;
    }

    public Texture getTexShade() {
        return texShade;
    }

    public Texture getTexHi() {
        return texHi;
    }

    public Texture getTexWisp() {
        return texWisp;
    }

    /**
     * Builds and binds the 4 GPU textures with linear filtering and wrap-repeat.
     */
    public void buildTextures() {
        dispose();

        float[] nA = generateNoise(NOISE_SIZE, 11, 4);
        float[] nB = generateNoise(NOISE_SIZE, 23, 4);
        float[] nC = generateNoise(NOISE_SIZE, 37, 8);
        float[] nD = generateNoise(NOISE_SIZE, 51, 4);

        Pixmap pBody = createPixmap(nA, RGB_BODY[0], RGB_BODY[1], RGB_BODY[2], MapNoiseTextures::calcBodyAlpha);
        Pixmap pShade = createPixmap(nB, RGB_SHADE[0], RGB_SHADE[1], RGB_SHADE[2], MapNoiseTextures::calcShadeAlpha);
        Pixmap pHi = createPixmap(nD, RGB_HI[0], RGB_HI[1], RGB_HI[2], MapNoiseTextures::calcHiAlpha);
        Pixmap pWisp = createPixmap(nC, RGB_WISP[0], RGB_WISP[1], RGB_WISP[2], MapNoiseTextures::calcWispAlpha);

        texBody = initTexture(pBody);
        texShade = initTexture(pShade);
        texHi = initTexture(pHi);
        texWisp = initTexture(pWisp);

        pBody.dispose();
        pShade.dispose();
        pHi.dispose();
        pWisp.dispose();
    }

    private static Texture initTexture(Pixmap pixmap) {
        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return tex;
    }

    @FunctionalInterface
    public interface FloatUnaryFunction {
        float apply(float v);
    }

    public static Pixmap createPixmap(float[] noise, int r, int g, int b, FloatUnaryFunction alphaFn) {
        int size = NOISE_SIZE;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float nVal = noise[y * size + x];
                float aVal = Math.max(0f, Math.min(1f, alphaFn.apply(nVal)));
                int aInt = Math.round(255f * aVal);
                int rgba = (r << 24) | (g << 16) | (b << 8) | aInt;
                pixmap.drawPixel(x, y, rgba);
            }
        }
        return pixmap;
    }

    public static float calcBodyAlpha(float v) {
        return 0.22f + 0.78f * smoothstep(0.2f, 0.85f, v);
    }

    public static float calcShadeAlpha(float v) {
        return 0.7f * smoothstep(0.45f, 0.9f, v);
    }

    public static float calcHiAlpha(float v) {
        return 0.7f * smoothstep(0.55f, 0.93f, v);
    }

    public static float calcWispAlpha(float v) {
        return smoothstep(0.56f, 0.74f, v) * (1f - smoothstep(0.86f, 0.97f, v));
    }

    public static float smoothstep(float a, float b, float v) {
        float t = Math.max(0f, Math.min(1f, (v - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    public static float[] generateNoise(int size, int seed, int base) {
        SplitMix32 r = new SplitMix32(seed);
        float[] out = new float[size * size];
        float amp = 1.0f;
        for (int o = 0; o < 5; o++) {
            int g = base << o;
            if (g > size) break;
            float[] lat = new float[g * g];
            for (int i = 0; i < g * g; i++) {
                lat[i] = r.nextFloat();
            }
            float cell = (float) size / g;
            for (int y = 0; y < size; y++) {
                float fy = y / cell;
                int y0 = (int) Math.floor(fy);
                float ty = fy - y0;
                float sy = ty * ty * (3f - 2f * ty);
                int y1 = (y0 + 1) % g;
                for (int x = 0; x < size; x++) {
                    float fx = x / cell;
                    int x0 = (int) Math.floor(fx);
                    float tx = fx - x0;
                    float sx = tx * tx * (3f - 2f * tx);
                    int x1 = (x0 + 1) % g;
                    float a = lat[y0 * g + x0];
                    float b = lat[y0 * g + x1];
                    float c = lat[y1 * g + x0];
                    float d = lat[y1 * g + x1];
                    float top = a + (b - a) * sx;
                    float bot = c + (d - c) * sx;
                    out[y * size + x] += amp * (top + (bot - top) * sy);
                }
            }
            amp *= 0.5f;
        }

        float mn = Float.POSITIVE_INFINITY;
        float mx = Float.NEGATIVE_INFINITY;
        for (float v : out) {
            if (v < mn) mn = v;
            if (v > mx) mx = v;
        }
        float range = (mx - mn != 0f) ? (mx - mn) : 1f;
        for (int i = 0; i < out.length; i++) {
            out[i] = (out[i] - mn) / range;
        }
        return out;
    }

    @Override
    public void dispose() {
        if (texBody != null) {
            texBody.dispose();
            texBody = null;
        }
        if (texShade != null) {
            texShade.dispose();
            texShade = null;
        }
        if (texHi != null) {
            texHi.dispose();
            texHi = null;
        }
        if (texWisp != null) {
            texWisp.dispose();
            texWisp = null;
        }
    }

    /**
     * 32-bit SplitMix PRNG matching JavaScript prototype's bitwise logic.
     */
    public static final class SplitMix32 {
        private int state;

        public SplitMix32(int seed) {
            this.state = seed;
        }

        public float nextFloat() {
            state += 0x6D2B79F5;
            int t = state;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * 61;
            long u = (t ^ (t >>> 14)) & 0xFFFFFFFFL;
            return u / 4294967296.0f;
        }
    }
}
