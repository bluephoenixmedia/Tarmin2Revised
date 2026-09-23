package com.bpm.minotaur.rendering.mesh;

import org.junit.Test;

import static org.junit.Assert.*;

public class BillboardSlicerTest {

    @Test
    public void testHorizontalBisection() {
        // Quad of width=1.0, height=1.0. Local bounds: x in [-0.5, 0.5], y in [0, 1.0].
        // Line equation for y = 0.5: 0*x + 1*y - 0.5 = 0.
        // Points with y > 0.5 are severed, points with y <= 0.5 are trunk.
        BillboardSlicer.SlicedResult result = BillboardSlicer.sliceBillboard(
                1.0f, 1.0f, null, 0f, 1f, -0.5f
        );

        assertTrue("Billboard should be sliced", result.isSliced);
        assertNotNull(result.trunkVertices);
        assertNotNull(result.severedVertices);
        assertNotNull(result.seamVertices);

        assertEquals("Cut seam must have 2 endpoints (4 floats)", 4, result.seamVertices.length);

        // Seam endpoints should be at y = 0.5
        assertEquals(0.5f, result.seamVertices[1], 0.001f);
        assertEquals(0.5f, result.seamVertices[3], 0.001f);

        // Trunk centroid Y should be below 0.5, Severed centroid Y should be above 0.5
        assertTrue(result.trunkCentroid.y < 0.5f);
        assertTrue(result.severedCentroid.y > 0.5f);
    }

    @Test
    public void testDiagonalWeaponSwingBisection() {
        // Swing from top-right (x=0.9, y=0.1) to bottom-left (x=0.1, y=0.9)
        BillboardSlicer.SlicedResult result = BillboardSlicer.sliceFromSwing(
                1.0f, 1.0f, null,
                0.9f, 0.1f, 0.1f, 0.9f
        );

        assertTrue("Diagonal swing should bisect quad", result.isSliced);
        assertTrue(result.trunkVertices.length >= 6); // At least 3 vertices
        assertTrue(result.severedVertices.length >= 6);
        assertEquals(4, result.seamVertices.length);
    }

    @Test
    public void testMissedCutReturnsNotSliced() {
        // Line far above quad (y = 5.0)
        BillboardSlicer.SlicedResult result = BillboardSlicer.sliceBillboard(
                1.0f, 1.0f, null, 0f, 1f, -5.0f
        );

        assertFalse("Line outside quad bounds should not slice", result.isSliced);
    }

    @Test
    public void testVerticalBisection() {
        // Cut through x = 0
        BillboardSlicer.SlicedResult result = BillboardSlicer.sliceBillboard(
                1.0f, 1.0f, null, 1f, 0f, 0f
        );

        assertTrue("Vertical cut should bisect quad", result.isSliced);
        assertEquals(4, result.seamVertices.length);
        assertEquals(0f, result.seamVertices[0], 0.001f);
        assertEquals(0f, result.seamVertices[2], 0.001f);
    }

    @Test
    public void testReverseDiagonalSwing() {
        // Swing from top-left (0.1, 0.1) to bottom-right (0.9, 0.9)
        BillboardSlicer.SlicedResult result = BillboardSlicer.sliceFromSwing(
                1.2f, 1.5f, null,
                0.1f, 0.1f, 0.9f, 0.9f
        );

        assertTrue("Reverse diagonal swing should bisect", result.isSliced);
        assertTrue(result.trunkVertices.length >= 6);
        assertTrue(result.severedVertices.length >= 6);
        assertEquals(4, result.seamVertices.length);
    }

    @Test
    public void testZeroLengthSwingDefaultsToHorizontalNeckCut() {
        // Identical start and end points
        BillboardSlicer.SlicedResult result = BillboardSlicer.sliceFromSwing(
                1.0f, 1.0f, null,
                0.5f, 0.5f, 0.5f, 0.5f
        );

        assertTrue("Degenerate swing should fallback to neck cut", result.isSliced);
        assertEquals(4, result.seamVertices.length);
    }
}
