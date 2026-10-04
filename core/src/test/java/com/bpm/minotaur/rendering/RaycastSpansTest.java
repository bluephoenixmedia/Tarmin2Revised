package com.bpm.minotaur.rendering;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RaycastSpansTest {

    @Test
    public void cellSpansCoverExactlyThePixelsTheOldPerPixelMappingGaveEachCell() {
        int cells = 24;
        for (int size = 1; size <= 3000; size += 37) {
            for (int p = 0; p < size; p++) {
                // The per-pixel mapping drawAsciiSprite used: (int) ((double) p / size * cells).
                int cell = (int) (((double) p / size) * cells);
                assertTrue("size " + size + " pixel " + p + " should start at or after cell " + cell,
                        RaycastSpans.cellStart(cell, cells, size) <= p);
                assertTrue("size " + size + " pixel " + p + " should end before cell " + (cell + 1),
                        p < RaycastSpans.cellStart(cell + 1, cells, size));
            }
        }
    }

    @Test
    public void visibleRunsMergeNeighbouringColumnsInFrontOfTheWalls() {
        float[] depth = {9, 9, 1, 1, 9, 9, 9, 1, 9, 9};
        List<int[]> runs = new ArrayList<>();
        RaycastSpans.visibleRuns(0, depth.length, 5f, depth, (from, to) -> runs.add(new int[]{from, to}));

        assertEquals(3, runs.size());
        assertArrayEquals(new int[]{0, 2}, runs.get(0));
        assertArrayEquals(new int[]{4, 7}, runs.get(1));
        assertArrayEquals(new int[]{8, 10}, runs.get(2));
    }

    @Test
    public void visibleRunsStayInsideTheDepthBuffer() {
        float[] depth = {9, 9, 9};
        List<int[]> runs = new ArrayList<>();
        RaycastSpans.visibleRuns(-5, 50, 5f, depth, (from, to) -> runs.add(new int[]{from, to}));

        assertEquals(1, runs.size());
        assertArrayEquals(new int[]{0, 3}, runs.get(0));
    }
}
