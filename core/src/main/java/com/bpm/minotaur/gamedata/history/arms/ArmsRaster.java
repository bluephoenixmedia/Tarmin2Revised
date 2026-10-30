package com.bpm.minotaur.gamedata.history.arms;

/**
 * Draws Arms into plain pixels (Living War A43, A45), with no {@code Gdx.*} calls: a heater shield
 * with the doctrine as its border, and a swallowtail banner for a captain to carry. Pixels are
 * RGBA8888, row by row from the top.
 */
public final class ArmsRaster {

    /** The drawing and its size. */
    public static final class Image {
        public final int width;
        public final int height;
        public final int[] rgba;

        Image(int width, int height) {
            this.width = width;
            this.height = height;
            this.rgba = new int[width * height];
        }

        void set(int x, int y, int rgb) {
            rgba[y * width + x] = rgb << 8 | 0xFF;
        }

        /** Colour over what is there, by {@code alpha} from 0 to 1. */
        void blend(int x, int y, int rgb, float alpha) {
            int under = rgba[y * width + x];
            if ((under & 0xFF) == 0) {
                rgba[y * width + x] = rgb << 8 | Math.round(alpha * 255);
                return;
            }
            int r = mix(under >>> 24, rgb >> 16 & 255, alpha), g = mix(under >>> 16 & 255, rgb >> 8 & 255, alpha), b = mix(under >>> 8 & 255, rgb & 255, alpha);
            rgba[y * width + x] = r << 24 | g << 16 | b << 8 | 0xFF;
        }

        private static int mix(int a, int b, float t) {
            return Math.round(a + (b - a) * t);
        }
    }

    /** A charge's silhouette: coverage from 0 to 255, side by side, row by row. */
    public static final class Mask {
        public final int size;
        public final byte[] coverage;

        public Mask(int size, byte[] coverage) {
            this.size = size;
            this.coverage = coverage;
        }

        /** Mean coverage of the mask's square from (u0, v0) to (u1, v1), each from 0 to 1. */
        float over(float u0, float v0, float u1, float v1) {
            int x0 = (int) (u0 * size), x1 = Math.max(x0 + 1, (int) Math.ceil(u1 * size));
            int y0 = (int) (v0 * size), y1 = Math.max(y0 + 1, (int) Math.ceil(v1 * size));
            int sum = 0, n = 0;
            for (int y = Math.max(0, y0); y < Math.min(size, y1); y++) {
                for (int x = Math.max(0, x0); x < Math.min(size, x1); x++) {
                    sum += coverage[y * size + x] & 0xFF;
                    n++;
                }
            }
            return n == 0 ? 0f : sum / (255f * n);
        }
    }

    private static final float SHIELD_ASPECT = 1.12f;
    private static final float BORDER = 0.08f;
    private static final float CHARGE_SIZE = 0.58f;
    private static final int OUTLINE = 0x000000;
    private static final int INK_DARK = 0x151515;
    private static final int INK_LIGHT = 0xF1E8D2;
    /** The ink must stand this far off both field colours to be drawn dark. */
    private static final double INK_CONTRAST = 2.2;
    /** From this size up a shield has a black outline, and a banner's charge has a halo. */
    private static final int DETAIL_FROM = 20;
    private static final int POLE = 0x5A4A32;

    private ArmsRaster() {
    }

    /**
     * A shield {@code size} pixels wide.
     *
     * @param borderRgb the doctrine's colour
     * @param charge the charge's silhouette, or null to leave the field bare
     */
    public static Image shield(HouseArms arms, int borderRgb, Mask charge, int size) {
        int h = (int) Math.ceil(size * SHIELD_ASPECT);
        Image out = new Image(size, h);
        float b = Math.max(1f, size * BORDER);
        int first = HouseArms.rgb(arms.field), second = HouseArms.rgb(arms.secondField);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < size; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                if (!inShield(px, py, 0f, 0f, size, h)) continue;
                boolean inner = inShield(px, py, b, b, size - 2 * b, h - 2 * b);
                if (!inner) {
                    boolean edge = size >= DETAIL_FROM && !inShield(px, py, 1f, 1f, size - 2f, h - 2f);
                    out.set(x, y, edge ? OUTLINE : borderRgb);
                } else {
                    out.set(x, y, inSecond(arms.division, px / size, py / h) ? second : first);
                }
            }
        }
        float box = size * CHARGE_SIZE;
        paintCharge(out, arms, charge, size / 2f - box / 2f, h * 0.52f - box / 2f, box, first, second);
        return out;
    }

    /**
     * A banner on its crossbar, {@code size} pixels wide overall: a band of the doctrine's colours
     * above the house's divided field and charge, carried by an army's captain (A45).
     */
    public static Image banner(HouseArms arms, int borderRgb, int secondaryRgb, Mask charge, int size) {
        int h = Math.round(size * 1.3f);
        Image out = new Image(size, h);
        int first = HouseArms.rgb(arms.field), second = HouseArms.rgb(arms.secondField);
        float w = size * 0.8f, left = (size - w) / 2f, top = Math.max(1f, size * 0.05f), bodyH = h - top;
        float band = bodyH * 0.18f, line = Math.max(1f, bodyH * 0.04f);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < size; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                if (py < top) {
                    out.set(x, y, POLE);
                    continue;
                }
                float u = (px - left) / w, v = (py - top) / bodyH;
                if (u < 0f || u > 1f) continue;
                // The swallowtail: the hem rises to a notch at the middle.
                float hem = 1f - 0.2f * (1f - Math.abs(u - 0.5f) * 2f);
                if (v > hem) continue;
                float fromTop = py - top;
                if (fromTop < band) out.set(x, y, borderRgb);
                else if (fromTop < band + line) out.set(x, y, secondaryRgb);
                else out.set(x, y, inSecond(arms.division, u, v) ? second : first);
            }
        }
        float box = w * 0.72f;
        paintCharge(out, arms, charge, left + w / 2f - box / 2f, top + bodyH * 0.52f - box / 2f, box, first, second);
        return out;
    }

    private static void paintCharge(Image out, HouseArms arms, Mask charge, float x0, float y0, float box, int first, int second) {
        if (charge == null) return;
        int ink = ink(first, second);
        int halo = ink == INK_DARK ? 0xFFFFFF : 0x000000;
        boolean withHalo = out.width >= DETAIL_FROM;
        float cell = 1f / box;
        for (int y = 0; y < out.height; y++) {
            for (int x = 0; x < out.width; x++) {
                if ((out.rgba[y * out.width + x] & 0xFF) == 0) continue;
                float u = (x - x0) / box, v = (y - y0) / box;
                float cover = u + cell <= 0f || v + cell <= 0f || u >= 1f || v >= 1f ? 0f : charge.over(u, v, u + cell, v + cell);
                if (cover > 0.05f) {
                    out.blend(x, y, ink, cover);
                } else if (withHalo) {
                    float near = charge.over(u - cell, v - cell, u + 2 * cell, v + 2 * cell);
                    if (near > 0.05f) out.blend(x, y, halo, 0.35f);
                }
            }
        }
    }

    /** Dark if it stands off both field colours, else light. */
    static int ink(int first, int second) {
        return HouseArms.contrast(INK_DARK, first) >= INK_CONTRAST && HouseArms.contrast(INK_DARK, second) >= INK_CONTRAST ? INK_DARK : INK_LIGHT;
    }

    /** A heater shield: square shouldered, curving in to a point. */
    private static boolean inShield(float px, float py, float x, float y, float w, float h) {
        float u = (px - x) / w, v = (py - y) / h;
        if (u < 0f || u > 1f || v < 0f || v > 1f) return false;
        if (v <= 0.55f) return true;
        // The quadratic from the shoulder (1, .55), by (1, .85), to the point (.5, 1), mirrored.
        float best = 0f;
        for (int i = 0; i <= 12; i++) {
            float t = i / 12f, s = 1f - t;
            float cy = s * s * 0.55f + 2 * s * t * 0.85f + t * t;
            float cx = s * s + 2 * s * t + t * t * 0.5f;
            if (cy >= v) {
                best = cx;
                break;
            }
        }
        return Math.abs(u - 0.5f) <= best - 0.5f;
    }

    private static boolean inSecond(HouseArms.Division d, float u, float v) {
        switch (d) {
            case PER_PALE: return u >= 0.5f;
            case PER_BEND: return u >= v;
            case PER_CHEVRON: return v >= 0.45f + Math.abs(u - 0.5f) * 1.1f;
            case QUARTERLY: return (u >= 0.5f) == (v < 0.5f);
            default: return v >= 0.5f;
        }
    }
}
