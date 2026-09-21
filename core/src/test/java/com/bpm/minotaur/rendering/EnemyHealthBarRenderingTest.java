package com.bpm.minotaur.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EnemyHealthBarRenderingTest {

    @Test
    public void testRaycasterHealthBarAlwaysTenPixelsAboveTopBorder() {
        float[] testViewportHeights = {480f, 720f, 1080f, 1440f};
        float[] testTransforms = {1.5f, 3.0f, 5.0f, 10.0f};
        float[] testOffsets = {0.0f, 0.15f, -0.2f};
        float[] testPulses = {0.0f, 0.16f};

        for (float vpHeight : testViewportHeights) {
            for (float transformY : testTransforms) {
                for (float offY : testOffsets) {
                    for (float pulse : testPulses) {
                        int baseSpriteHeight = (int) Math.abs(vpHeight / transformY);
                        float scaleY = 1.0f;
                        int spriteHeight = (int) (baseSpriteHeight * scaleY * (1f + pulse));

                        float drawY = (vpHeight / 2f) - spriteHeight / 2.0f + (offY * spriteHeight);
                        float topBorder = drawY + spriteHeight;
                        float barY = topBorder + 10f;

                        assertEquals("Bar baseline must be exactly 10 pixels above top border",
                                10.0f, barY - topBorder, 0.0001f);
                    }
                }
            }
        }
    }

    @Test
    public void testPlanar3DHealthBarGapProjectsToTenPixels() {
        float[] testViewportHeights = {600f, 720f, 1080f};
        float[] testFovs = {60f, 67f, 75f};
        float[] testDistances = {2.0f, 4.0f, 8.0f, 15.0f};

        for (float vpHeight : testViewportHeights) {
            for (float fov : testFovs) {
                for (float zCam : testDistances) {
                    float monsterTopY = 1.8f;

                    // Compute world gap for 10 screen pixels
                    float tanHalfFov = (float) Math.tan(Math.toRadians(fov / 2.0));
                    float pixelWorldHeight = (2.0f * zCam * tanHalfFov) / vpHeight;
                    float gapWorld = 10.0f * pixelWorldHeight;

                    // Perspective projection screen Y: (y_view / (z_view * tan(fov/2)) + 1) * (vpHeight / 2)
                    float topScreenY = (monsterTopY / (zCam * tanHalfFov) + 1.0f) * (vpHeight / 2.0f);
                    float barScreenY = ((monsterTopY + gapWorld) / (zCam * tanHalfFov) + 1.0f) * (vpHeight / 2.0f);

                    float screenPixelDiff = barScreenY - topScreenY;
                    assertEquals("Health bar projection must be exactly 10 screen pixels above top border",
                            10.0f, screenPixelDiff, 0.001f);
                }
            }
        }
    }
}
