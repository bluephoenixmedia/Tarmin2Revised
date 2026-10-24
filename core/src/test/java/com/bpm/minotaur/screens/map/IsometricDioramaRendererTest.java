package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
        // dx = -5, dy = -5. rx = -5, ry = -5.
        // hw = 28, hh = 14.
        // screenX = W/2 + (rx - ry)*hw = 360 + (-5 - -5)*28 = 360.
        // screenY = H/2 + (rx + ry)*hh = 224 + (-5 + -5)*14 = 224 - 140 = 84.
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
}
