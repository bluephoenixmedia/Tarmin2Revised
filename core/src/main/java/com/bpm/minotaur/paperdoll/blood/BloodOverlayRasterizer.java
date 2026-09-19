package com.bpm.minotaur.paperdoll.blood;

import com.bpm.minotaur.gamedata.gore.BloodCoat;
import com.bpm.minotaur.gamedata.gore.BloodStain;

import java.util.Random;

/**
 * Paints a layer's blood into a small RGBA image, drawn over the layer with the layer's
 * own placement.
 *
 * Pixel art, like the doll: rendered at one blood pixel per {@link #DOWNSAMPLE} canvas
 * pixels -- about the size of the art's own pixels -- and drawn with nearest filtering,
 * so blood reads as part of the sprite rather than a smooth decal pasted on top. Each
 * stain gets a dark rim like the art's outlines, fresh stains a wet glint, and drips run
 * straight down.
 *
 * Clipped to the layer's silhouette, as the weapon's decals are clipped to its blade: no
 * blood ever hangs in the air beside a piece.
 *
 * Pure Java, so it is tested without a GL context. Output is RGBA8888 ints (0xRRGGBBAA),
 * row 0 at the top, which is what Pixmap expects.
 */
public final class BloodOverlayRasterizer {

    /** Canvas pixels per blood pixel. */
    public static final int DOWNSAMPLE = 6;

    /** Turns for a stain to dry from crimson to its darkest. */
    static final float DRY_TURNS = 40f;

    /** Soak below this shows nothing. */
    static final float SOAK_FLOOR = 0.05f;
    /** At full soak, the least-soaked patches still stay clean. */
    static final float SOAK_MIN_THRESHOLD = 0.22f;
    /** Soak darkens the art heavily but never hides it. */
    static final float SOAK_MAX_ALPHA = 0.78f;
    private static final float RIM_SHADE = 0.68f;
    private static final float SOAK_R = 0.30f;
    private static final float SOAK_G = 0.035f;
    private static final float SOAK_B = 0.03f;

    private BloodOverlayRasterizer() {
    }

    public static int[] render(BloodCoat coat, AlphaMask mask, int coatSeed) {
        int w = mask.width;
        int h = mask.height;
        float[] r = new float[w * h];
        float[] g = new float[w * h];
        float[] b = new float[w * h];
        float[] a = new float[w * h];

        if (coat.soak > 0f) {
            paintSoak(coat.soak, mask, coatSeed, r, g, b, a);
        }
        for (BloodStain s : coat.stains) {
            paintStain(s, mask, r, g, b, a);
        }

        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) {
            if (a[i] <= 0f || !mask.solidAt(i % w, i / w)) {
                continue;
            }
            out[i] = (channel(r[i]) << 24) | (channel(g[i]) << 16) | (channel(b[i]) << 8) | channel(a[i]);
        }
        return out;
    }

    /**
     * The saturation under the stains. Patchy rather than a flat tint -- a flat tint just
     * reads as a darker piece of armour -- and heavier toward the bottom, where blood runs
     * and pools.
     */
    private static void paintSoak(float soak, AlphaMask mask, int seed,
                                  float[] r, float[] g, float[] b, float[] a) {
        int w = mask.width;
        int h = mask.height;
        // Nothing until blood has genuinely built up: every stain adds a little soak, and
        // without the floor a single droplet would already speckle the whole piece.
        // And never quite everything: a solid wash turns the figure into a red silhouette,
        // where what reads as horror is the art still showing through the gore.
        float threshold = Math.max(SOAK_MIN_THRESHOLD, 1f - Math.max(0f, soak - SOAK_FLOOR) * 1.2f);
        for (int y = 0; y < h; y++) {
            float bias = 0.8f + 0.4f * (y / (float) h);
            for (int x = 0; x < w; x++) {
                if (!mask.solidAt(x, y)) {
                    continue;
                }
                float n = fbm(x, y, seed);
                // Normalised back to 0..1 so the bottom-heavy bias cannot push it past the threshold alone.
                float level = n * bias / 1.2f;
                if (level <= threshold) {
                    continue;
                }
                // Clots: a finer noise picks out near-black spots inside the wash.
                float clot = hash01(x, y, seed ^ 0x2C1B) < 0.12f + 0.2f * soak ? 0.55f : 1f;
                float shade = (0.65f + 0.7f * n) * clot;
                over(r, g, b, a, y * w + x, SOAK_R * shade, SOAK_G * shade, SOAK_B * shade,
                        Math.min(SOAK_MAX_ALPHA, 0.35f + 0.3f * soak + 0.35f * (level - threshold)));
            }
        }
    }

    private static void paintStain(BloodStain s, AlphaMask mask,
                                   float[] r, float[] g, float[] b, float[] a) {
        Shape shape = new Shape(s, mask.scale);
        int x0 = Math.max(0, (int) Math.floor(shape.minX));
        int x1 = Math.min(mask.width - 1, (int) Math.ceil(shape.maxX));
        int y0 = Math.max(0, (int) Math.floor(shape.minY));
        int y1 = Math.min(mask.height - 1, (int) Math.ceil(shape.maxY));
        if (x0 > x1 || y0 > y1) {
            return;
        }

        float dry = Math.min(1f, s.age / DRY_TURNS);
        float fr = ((s.rgb >> 16) & 0xFF) / 255f;
        float fg = ((s.rgb >> 8) & 0xFF) / 255f;
        float fb = (s.rgb & 0xFF) / 255f;
        // Same oxidised end colour as the world's floor and weapon decals.
        float cr = lerp(fr, fr * 0.42f, dry);
        float cg = lerp(fg, fg * 0.15f, dry);
        float cb = lerp(fb, fb * 0.15f, dry);
        float alpha = lerp(0.92f, 0.86f, dry);

        int gx = Math.round(shape.cx - shape.r * 0.35f);
        int gy = Math.round(shape.cy - shape.r * 0.35f);
        boolean glint = dry < 0.25f && shape.r >= 2.5f;

        int w = mask.width;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (!shape.covers(x, y) || !mask.solidAt(x, y)) {
                    continue;
                }
                boolean rim = !shape.covers(x - 1, y) || !shape.covers(x + 1, y)
                        || !shape.covers(x, y - 1) || !shape.covers(x, y + 1);
                float shade = (rim ? RIM_SHADE : 1f) * (0.9f + 0.15f * hash01(x, y, s.seed));
                float pr = cr * shade;
                float pg = cg * shade;
                float pb = cb * shade;
                if (glint && x == gx && y == gy) {
                    pr = Math.min(1f, pr * 1.5f + 0.12f);
                    pg = Math.min(1f, pg * 1.5f + 0.06f);
                    pb = Math.min(1f, pb * 1.5f + 0.06f);
                }
                over(r, g, b, a, y * w + x, pr, pg, pb, alpha);
            }
        }
    }

    /**
     * A stain's outline: an irregular blob of overlapping circles, satellite droplets
     * flung out around it, and a drip running down with a bead at the end. All seeded
     * from the stain, so it redraws the same every time.
     */
    static final class Shape {
        final float cx;
        final float cy;
        final float r;
        private final float[] circles; // x, y, radius triples
        private final float dripX;
        private final float dripTop;
        private final float dripBottom;
        private final float dripWidth;
        private final float dripPhase;
        float minX;
        float maxX;
        float minY;
        float maxY;

        Shape(BloodStain s, float scale) {
            Random rng = new Random(s.seed);
            cx = s.x * scale;
            cy = s.y * scale;
            r = Math.max(0.6f, s.radius * scale);

            int blobs = 4 + rng.nextInt(3);
            int satellites = r >= 1.5f ? 2 + rng.nextInt(5) : 0;
            circles = new float[(1 + blobs + satellites) * 3];
            int i = 0;
            i = put(circles, i, cx, cy, r * 0.75f);
            for (int k = 0; k < blobs; k++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                float dist = rng.nextFloat() * 0.45f * r;
                i = put(circles, i, cx + (float) Math.cos(angle) * dist, cy + (float) Math.sin(angle) * dist,
                        r * (0.5f + rng.nextFloat() * 0.35f));
            }
            for (int k = 0; k < satellites; k++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                float dist = r * (1.2f + rng.nextFloat() * 1.3f);
                i = put(circles, i, cx + (float) Math.cos(angle) * dist, cy + (float) Math.sin(angle) * dist,
                        Math.max(0.5f, r * (0.1f + rng.nextFloat() * 0.15f)));
            }

            minX = Float.MAX_VALUE;
            maxX = -Float.MAX_VALUE;
            minY = Float.MAX_VALUE;
            maxY = -Float.MAX_VALUE;
            for (int k = 0; k < circles.length; k += 3) {
                minX = Math.min(minX, circles[k] - circles[k + 2]);
                maxX = Math.max(maxX, circles[k] + circles[k + 2]);
                minY = Math.min(minY, circles[k + 1] - circles[k + 2]);
                maxY = Math.max(maxY, circles[k + 1] + circles[k + 2]);
            }

            if (s.drip > 0f) {
                dripX = cx + (rng.nextFloat() - 0.5f) * r * 0.6f;
                dripTop = cy;
                dripBottom = cy + r * 0.4f + s.drip * r;
                dripWidth = Math.max(0.5f, r * 0.22f);
                dripPhase = rng.nextFloat() * 6.28f;
                float bead = Math.max(0.7f, r * 0.25f);
                minX = Math.min(minX, dripX - dripWidth - bead - 1f);
                maxX = Math.max(maxX, dripX + dripWidth + bead + 1f);
                maxY = Math.max(maxY, dripBottom + bead);
            } else {
                dripX = 0f;
                dripTop = 0f;
                dripBottom = -1f;
                dripWidth = 0f;
                dripPhase = 0f;
            }
        }

        private static int put(float[] arr, int i, float x, float y, float radius) {
            arr[i] = x;
            arr[i + 1] = y;
            arr[i + 2] = radius;
            return i + 3;
        }

        /** Whether the centre of blood pixel (x, y) is inside the stain. */
        boolean covers(int x, int y) {
            float px = x + 0.5f;
            float py = y + 0.5f;
            for (int k = 0; k < circles.length; k += 3) {
                float dx = px - circles[k];
                float dy = py - circles[k + 1];
                float rad = circles[k + 2];
                if (dx * dx + dy * dy <= rad * rad) {
                    return true;
                }
            }
            if (dripBottom > dripTop && py >= dripTop && py <= dripBottom + Math.max(0.7f, r * 0.25f)) {
                float t = (py - dripTop) / Math.max(0.001f, dripBottom - dripTop);
                if (t <= 1f) {
                    float center = dripX + (float) Math.sin(t * 3f + dripPhase) * 0.6f;
                    float half = dripWidth * (1f - 0.5f * t);
                    if (Math.abs(px - center) <= Math.max(0.5f, half)) {
                        return true;
                    }
                }
                float bead = Math.max(0.7f, r * 0.25f);
                float bx = dripX + (float) Math.sin(3f + dripPhase) * 0.6f;
                float dx = px - bx;
                float dy = py - dripBottom;
                return dx * dx + dy * dy <= bead * bead;
            }
            return false;
        }
    }

    private static void over(float[] r, float[] g, float[] b, float[] a, int i,
                             float sr, float sg, float sb, float sa) {
        float da = a[i];
        float oa = sa + da * (1f - sa);
        if (oa <= 0f) {
            return;
        }
        r[i] = (sr * sa + r[i] * da * (1f - sa)) / oa;
        g[i] = (sg * sa + g[i] * da * (1f - sa)) / oa;
        b[i] = (sb * sa + b[i] * da * (1f - sa)) / oa;
        a[i] = oa;
    }

    /** Two octaves of value noise, 0..1: blotches a few art-pixels across. */
    static float fbm(int x, int y, int seed) {
        return 0.65f * valueNoise(x / 7f, y / 7f, seed) + 0.35f * valueNoise(x / 3f, y / 3f, seed * 31 + 7);
    }

    private static float valueNoise(float x, float y, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float tx = smooth(x - xi);
        float ty = smooth(y - yi);
        float v00 = hash01(xi, yi, seed);
        float v10 = hash01(xi + 1, yi, seed);
        float v01 = hash01(xi, yi + 1, seed);
        float v11 = hash01(xi + 1, yi + 1, seed);
        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), ty);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    static float hash01(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }
}
