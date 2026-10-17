package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Pixmap;

/**
 * A coarse grid of which parts of a monster sprite are actually body, so a wound can be placed on
 * the creature rather than in the empty corner of its bounding box.
 *
 * <p>Coordinates match {@link WoundDecal}: {@code u} runs left to right and {@code v} runs from
 * the bottom of the sprite up. Row 0 of the grid is the bottom, so callers need no flip.
 */
public final class SilhouetteMask {

    public static final int GRID = 24;
    private static final int SOLID_ALPHA = 32;

    private final int width;
    private final int height;
    private final boolean[] solid;

    public SilhouetteMask(int width, int height, boolean[] solid) {
        this.width = width;
        this.height = height;
        this.solid = solid;
    }

    /** Every cell solid: the answer when a sprite cannot be read. */
    public static SilhouetteMask full() {
        boolean[] all = new boolean[GRID * GRID];
        java.util.Arrays.fill(all, true);
        return new SilhouetteMask(GRID, GRID, all);
    }

    /**
     * Builds a mask from the sub-rectangle of a pixmap that a sprite occupies. A cell is solid when
     * any pixel in it is meaningfully opaque, so thin limbs are not lost to the downsample.
     */
    public static SilhouetteMask fromPixmap(Pixmap pixmap, int regionX, int regionY, int regionW, int regionH) {
        if (pixmap == null || regionW <= 0 || regionH <= 0) {
            return full();
        }
        boolean[] cells = new boolean[GRID * GRID];
        boolean any = false;
        for (int gy = 0; gy < GRID; gy++) {
            for (int gx = 0; gx < GRID; gx++) {
                int x0 = regionX + gx * regionW / GRID;
                int x1 = Math.max(x0 + 1, regionX + (gx + 1) * regionW / GRID);
                // gy counts from the bottom; pixmap rows count from the top.
                int yTop = regionY + (GRID - 1 - gy) * regionH / GRID;
                int yBottom = Math.max(yTop + 1, regionY + (GRID - gy) * regionH / GRID);
                boolean hit = false;
                for (int y = yTop; y < yBottom && !hit; y++) {
                    for (int x = x0; x < x1; x++) {
                        if (x < 0 || y < 0 || x >= pixmap.getWidth() || y >= pixmap.getHeight()) {
                            continue;
                        }
                        if ((pixmap.getPixel(x, y) & 0xFF) > SOLID_ALPHA) {
                            hit = true;
                            break;
                        }
                    }
                }
                cells[gy * GRID + gx] = hit;
                any |= hit;
            }
        }
        return any ? new SilhouetteMask(GRID, GRID, cells) : full();
    }

    public int solidCount() {
        int n = 0;
        for (boolean b : solid) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /** Whether the point (u, v) falls on a solid cell. */
    public boolean isSolidAt(float u, float v) {
        int gx = Math.min(width - 1, Math.max(0, (int) (u * width)));
        int gy = Math.min(height - 1, Math.max(0, (int) (v * height)));
        return solid[gy * width + gx];
    }

    /** Picks a uniformly random solid cell and returns a point inside it as {u, v}. */
    public float[] randomSolidPoint(java.util.Random random) {
        int count = solidCount();
        if (count == 0) {
            return new float[] { 0.5f, 0.5f };
        }
        int target = random.nextInt(count);
        for (int i = 0; i < solid.length; i++) {
            if (solid[i] && target-- == 0) {
                int gx = i % width;
                int gy = i / width;
                return new float[] {
                        (gx + random.nextFloat()) / width,
                        (gy + random.nextFloat()) / height };
            }
        }
        return new float[] { 0.5f, 0.5f };
    }
}
