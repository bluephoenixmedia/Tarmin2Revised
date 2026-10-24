package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IsometricDioramaRendererTest {

    @Test
    public void testProjectionMathMatchesPrototype() {
        float W = 720f;
        float H = 448f;
        float camX = 5f;
        float camY = 5f;
        float camZoom = 1f;
        float camRot = 0f;

        // Origin (0, 0, 0)
        Vector2 p0 = IsometricDioramaRenderer.project(0f, 0f, 0f, camX, camY, camZoom, camRot, W, H);
        assertEquals(360f, p0.x, 0.001f);
        assertEquals(84f, p0.y, 0.001f);

        // Center (5, 5, 0) should project to center of screen (360, 224)
        Vector2 pCenter = IsometricDioramaRenderer.project(5f, 5f, 0f, camX, camY, camZoom, camRot, W, H);
        assertEquals(360f, pCenter.x, 0.001f);
        assertEquals(224f, pCenter.y, 0.001f);
    }

    @Test
    public void testProjectUnprojectRoundTrip() {
        float W = 720f;
        float H = 448f;
        float camX = 4.2f;
        float camY = 6.8f;
        float camZoom = 2.6f;
        float camRot = (float) (Math.PI / 2.0); // 90 deg rotation

        float testWx = 3.5f;
        float testWy = 7.1f;
        float testZ = 5.0f;

        Vector2 screen = IsometricDioramaRenderer.project(testWx, testWy, testZ, camX, camY, camZoom, camRot, W, H);
        Vector2 world = IsometricDioramaRenderer.unproject(screen.x, screen.y, testZ, camX, camY, camZoom, camRot, W, H);

        assertEquals("Unproject X must match original world X", testWx, world.x, 0.001f);
        assertEquals("Unproject Y must match original world Y", testWy, world.y, 0.001f);
    }

    @Test
    public void testZoomThresholds() {
        assertEquals(IsometricDioramaRenderer.ZoomLevel.WORLD, IsometricDioramaRenderer.levelOf(1.0f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.WORLD, IsometricDioramaRenderer.levelOf(1.69f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.REGION, IsometricDioramaRenderer.levelOf(1.7f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.REGION, IsometricDioramaRenderer.levelOf(2.6f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.REGION, IsometricDioramaRenderer.levelOf(4.19f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.CHUNK, IsometricDioramaRenderer.levelOf(4.2f));
        assertEquals(IsometricDioramaRenderer.ZoomLevel.CHUNK, IsometricDioramaRenderer.levelOf(6.4f));
    }

    @Test
    public void testCameraEasing() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setTarget(5f, 5f, 1f, 0f);
        renderer.update(1.0f); // Fast forward to settle

        renderer.setTarget(8f, 7f, 2.6f, (float) (Math.PI / 2.0));
        // Step a small delta
        renderer.update(0.1f);

        assertTrue("Camera X should move toward target", renderer.getCamX() > 5f && renderer.getCamX() < 8f);
        assertTrue("Camera Y should move toward target", renderer.getCamY() > 5f && renderer.getCamY() < 7f);
        assertTrue("Camera Zoom should move toward target", renderer.getCamZoom() > 1f && renderer.getCamZoom() < 2.6f);
        assertTrue("Camera Rot should move toward target", renderer.getCamRot() > 0f && renderer.getCamRot() < Math.PI / 2.0);

        // Multiple steps to settle
        for (int i = 0; i < 20; i++) {
            renderer.update(0.1f);
        }
        assertEquals(8f, renderer.getCamX(), 0.001f);
        assertEquals(7f, renderer.getCamY(), 0.001f);
        assertEquals(2.6f, renderer.getCamZoom(), 0.001f);
        assertEquals((float) (Math.PI / 2.0), renderer.getCamRot(), 0.001f);
    }

    @Test
    public void test90DegreeRotationSteps() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setTarget(5f, 5f, 1f, 0f);
        renderer.update(1.0f);

        renderer.rotate(1); // 90 degrees right
        assertEquals((float) (Math.PI / 2.0), renderer.getTgtRot(), 0.001f);

        renderer.rotate(1); // 180 degrees
        assertEquals((float) Math.PI, renderer.getTgtRot(), 0.001f);

        renderer.rotate(-1); // back to 90 degrees
        assertEquals((float) (Math.PI / 2.0), renderer.getTgtRot(), 0.001f);
    }

    @Test
    public void testZoomAtPreservesWorldPoint() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setCameraImmediate(5f, 5f, 1f, 0f);

        float screenX = 400f;
        float screenY = 250f;
        Vector2 beforeWorld = IsometricDioramaRenderer.unproject(screenX, screenY, 4f,
                renderer.getCamX(), renderer.getCamY(), renderer.getCamZoom(), renderer.getCamRot(),
                IsometricDioramaRenderer.FBO_WIDTH, IsometricDioramaRenderer.FBO_HEIGHT);

        renderer.zoomAt(screenX, 448f - screenY, 720f, 448f, 1.5f);
        renderer.setCameraImmediate(renderer.getTgtX(), renderer.getTgtY(), renderer.getTgtZoom(), renderer.getTgtRot());

        Vector2 afterWorld = IsometricDioramaRenderer.unproject(screenX, screenY, 4f,
                renderer.getCamX(), renderer.getCamY(), renderer.getCamZoom(), renderer.getCamRot(),
                IsometricDioramaRenderer.FBO_WIDTH, IsometricDioramaRenderer.FBO_HEIGHT);

        assertEquals("World X under cursor should be stationary after zoomAt", beforeWorld.x, afterWorld.x, 0.05f);
        assertEquals("World Y under cursor should be stationary after zoomAt", beforeWorld.y, afterWorld.y, 0.05f);
    }

    @Test
    public void testPickChunkWithZLevels() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setTarget(5f, 5f, 1f, 0f);
        renderer.update(1.0f);

        // Project center of chunk (5, 5) at height z=6
        Vector2 screen = IsometricDioramaRenderer.project(5.5f, 5.5f, 6.0f,
                renderer.getCamX(), renderer.getCamY(), renderer.getCamZoom(), renderer.getCamRot(),
                720f, 448f);

        // Pick with height lookup returning 6.0 for chunk (5, 5)
        GridPoint2 picked = renderer.pickChunk(screen.x, 448f - screen.y, 720f, 448f, 1,
                (cx, cy) -> (cx == 5 && cy == 5) ? 6.0f : 0.0f);

        assertNotNull("Chunk should be picked", picked);
        assertEquals(5, picked.x);
        assertEquals(5, picked.y);
    }

    @Test
    public void testPanScreenAndSnap() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setCameraImmediate(5f, 5f, 1f, 0f);

        // Pan with screen delta
        renderer.panScreen(-28f, 0f, 720f, 448f);
        // At zoom=1, rot=0: dx = -28, a = -1, b = 0 -> rx = -0.5, ry = 0.5
        assertEquals(4.5f, renderer.getTgtX(), 0.001f);
        assertEquals(5.5f, renderer.getTgtY(), 0.001f);

        // Before snap, camX is still 5
        assertEquals(5f, renderer.getCamX(), 0.001f);
        renderer.snapCameraPosition();
        assertEquals(4.5f, renderer.getCamX(), 0.001f);
        assertEquals(5.5f, renderer.getCamY(), 0.001f);
    }

    @Test
    public void testCenterOnAndClamping() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.centerOn(25f, -15f);
        assertEquals(IsometricDioramaRenderer.CHUNK_GRID_SIZE + 1f, renderer.getTgtX(), 0.001f);
        assertEquals(-1f, renderer.getTgtY(), 0.001f);

        renderer.setTargetZoom(20f);
        assertEquals(IsometricDioramaRenderer.MAX_ZOOM, renderer.getTgtZoom(), 0.001f);
        assertEquals(IsometricDioramaRenderer.ZoomLevel.CHUNK, renderer.getZoomLevel());

        renderer.setTargetZoom(0.2f);
        assertEquals(IsometricDioramaRenderer.MIN_ZOOM, renderer.getTgtZoom(), 0.001f);
        assertEquals(IsometricDioramaRenderer.ZoomLevel.WORLD, renderer.getZoomLevel());
    }

    @Test
    public void testPickChunkOceanAndCave() {
        IsometricDioramaRenderer renderer = new IsometricDioramaRenderer();
        renderer.setCameraImmediate(5f, 5f, 1f, 0f);

        // Ocean height z=2.0 on floor 1
        Vector2 screenOcean = IsometricDioramaRenderer.project(3.5f, 4.5f, 2.0f,
                5f, 5f, 1f, 0f, 720f, 448f);
        GridPoint2 pickedOcean = renderer.pickChunk(screenOcean.x, 448f - screenOcean.y, 720f, 448f, 1,
                (cx, cy) -> (cx == 3 && cy == 4) ? 2.0f : 6.0f);
        assertNotNull(pickedOcean);
        assertEquals(3, pickedOcean.x);
        assertEquals(4, pickedOcean.y);

        // Cave mode floor 2 (floor != 1) tests z=5.0
        Vector2 screenCave = IsometricDioramaRenderer.project(2.5f, 6.5f, 5.0f,
                5f, 5f, 1f, 0f, 720f, 448f);
        GridPoint2 pickedCave = renderer.pickChunk(screenCave.x, 448f - screenCave.y, 720f, 448f, 2, null);
        assertNotNull(pickedCave);
        assertEquals(2, pickedCave.x);
        assertEquals(6, pickedCave.y);
    }
}
