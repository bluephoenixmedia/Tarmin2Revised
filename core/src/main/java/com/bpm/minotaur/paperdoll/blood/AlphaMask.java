package com.bpm.minotaur.paperdoll.blood;

/**
 * A layer's silhouette at blood resolution: which blood pixels sit on the art.
 *
 * Kept on the CPU because blood is both aimed with it (does a splash hit this piece, or
 * go through to the one underneath?) and clipped by it (never paint blood in the empty
 * air around a gauntlet).
 */
public final class AlphaMask {

    public final int width;
    public final int height;
    /** Blood pixels per layer-canvas pixel. */
    public final float scale;
    private final byte[] alpha;

    public AlphaMask(int width, int height, float scale, byte[] alpha) {
        if (alpha.length != width * height) {
            throw new IllegalArgumentException("alpha is " + alpha.length + ", expected " + width * height);
        }
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.alpha = alpha;
    }

    /** Samples a full-resolution layer down to blood resolution, one point per blood pixel. */
    public static AlphaMask sample(int srcWidth, int srcHeight, int downsample, AlphaSource source) {
        int w = Math.max(1, srcWidth / downsample);
        int h = Math.max(1, srcHeight / downsample);
        byte[] a = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int sy = Math.min(srcHeight - 1, (int) ((y + 0.5f) * srcHeight / h));
            for (int x = 0; x < w; x++) {
                int sx = Math.min(srcWidth - 1, (int) ((x + 0.5f) * srcWidth / w));
                a[y * w + x] = (byte) source.alphaAt(sx, sy);
            }
        }
        return new AlphaMask(w, h, w / (float) srcWidth, a);
    }

    public int alphaAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return alpha[y * width + x] & 0xFF;
    }

    /** Solid enough to hold blood. The threshold keeps soft anti-aliased fringes clean. */
    public boolean solidAt(int x, int y) {
        return alphaAt(x, y) >= 128;
    }

    /** Same test at a layer-canvas position rather than a blood pixel. */
    public boolean solidAtLayerPixel(float lx, float ly) {
        return solidAt((int) Math.floor(lx * scale), (int) Math.floor(ly * scale));
    }

    public interface AlphaSource {
        /** 0..255 at a full-resolution pixel. */
        int alphaAt(int x, int y);
    }
}
