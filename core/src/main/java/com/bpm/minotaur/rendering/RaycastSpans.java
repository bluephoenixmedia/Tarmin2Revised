package com.bpm.minotaur.rendering;

/**
 * Column and row arithmetic for the raycaster's sprite drawing.
 *
 * The raycaster hides a sprite behind walls one screen column at a time, and
 * it used to draw one column (or one pixel) at a time to match. That is fine
 * for a monster a few hundred pixels wide; a forest tree up close spans the
 * whole screen and many thousands of rows. Drawing each run of unoccluded
 * columns, and each cell of a 24x24 ASCII sprite, as one rectangle gives the
 * same picture for a tiny fraction of the work.
 */
final class RaycastSpans {

    interface RunSink {
        void run(int from, int to);
    }

    private RaycastSpans() {
    }

    /**
     * First pixel of {@code cell} when {@code size} pixels are divided into
     * {@code cells} cells: the smallest p with floor(p * cells / size) >= cell.
     * Cell c covers [cellStart(c), cellStart(c + 1)).
     */
    static int cellStart(int cell, int cells, int size) {
        long num = (long) cell * size;
        return (int) ((num + cells - 1) / cells);
    }

    /**
     * Calls {@code sink} once per run of consecutive columns in [from, to)
     * where something at {@code depth} is in front of the wall in the depth
     * buffer. Columns outside the buffer are skipped.
     */
    static void visibleRuns(int from, int to, float depth, float[] depthBuffer, RunSink sink) {
        int start = -1;
        int end = Math.min(to, depthBuffer.length);
        for (int x = Math.max(0, from); x < end; x++) {
            boolean visible = depth < depthBuffer[x];
            if (visible && start < 0) {
                start = x;
            } else if (!visible && start >= 0) {
                sink.run(start, x);
                start = -1;
            }
        }
        if (start >= 0) sink.run(start, end);
    }
}
