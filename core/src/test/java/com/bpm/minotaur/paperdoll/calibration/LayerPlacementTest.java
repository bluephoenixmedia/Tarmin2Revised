package com.bpm.minotaur.paperdoll.calibration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Placement maps a calibration expressed in 1024x1536 canvas pixels onto whatever
 * rectangle the widget happens to occupy on the 1920x1080 stage.
 *
 * Calibration numbers are authored against the canvas so they stay meaningful if
 * the portrait rectangle is ever moved or resized.
 */
public class LayerPlacementTest {

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 1536;

    // The real portrait rectangle, derived by matching base_father.png against
    // new_inventory.png (see PaperDollPanel).
    private static final float RX = 332.9f;
    private static final float RY = 367.8f;
    private static final float RW = 388.2f;
    private static final float RH = 559.3f;

    private LayerPlacement place(LayerCalibration cal) {
        return LayerPlacement.compute(cal, RX, RY, RW, RH, CANVAS_W, CANVAS_H);
    }

    @Test
    public void identityCalibrationFillsTheRectExactly() {
        // This is the compatibility guarantee: every layer that has never been
        // calibrated must draw precisely where the old batch.draw(tex,x,y,w,h) put it.
        LayerPlacement p = place(new LayerCalibration());
        assertEquals(RX, p.x, 0.001f);
        assertEquals(RY, p.y, 0.001f);
        assertEquals(RW, p.width, 0.001f);
        assertEquals(RH, p.height, 0.001f);
        assertEquals(0f, p.rotation, 0.001f);
    }

    @Test
    public void offsetXIsCanvasPixelsConvertedToStagePixels() {
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 102.4f; // exactly 1/10th of the canvas width
        LayerPlacement p = place(cal);
        assertEquals(RX + RW / 10f, p.x, 0.001f);
        assertEquals(RY, p.y, 0.001f);
    }

    @Test
    public void positiveOffsetYMovesTheLayerDownTheScreen() {
        // Artists think in image space (y grows downward). LibGDX is y-up, so a
        // positive offsetY must DECREASE the stage y. Getting this backwards would
        // make every calibration session feel inverted.
        LayerCalibration cal = new LayerCalibration();
        cal.offsetY = 153.6f; // 1/10th of the canvas height
        LayerPlacement p = place(cal);
        assertEquals(RY - RH / 10f, p.y, 0.001f);
    }

    @Test
    public void scaleGrowsAboutTheLayerCentreNotItsCorner() {
        // Scaling from the corner would shift the piece off the body every time you
        // resized it, which is what makes slider-based fitting feel unusable.
        LayerCalibration cal = new LayerCalibration();
        cal.scaleX = 2f;
        LayerPlacement p = place(cal);

        assertEquals(RW * 2f, p.width, 0.001f);
        assertEquals(RH, p.height, 0.001f);

        float centreBefore = RX + RW / 2f;
        float centreAfter = p.x + p.width / 2f;
        assertEquals("centre must be preserved under scaling", centreBefore, centreAfter, 0.001f);
    }

    @Test
    public void scaleXAndScaleYAreIndependent() {
        // Non-uniform scale is the specific tool for "right height, too narrow".
        LayerCalibration cal = new LayerCalibration();
        cal.scaleX = 1.5f;
        cal.scaleY = 0.5f;
        LayerPlacement p = place(cal);
        assertEquals(RW * 1.5f, p.width, 0.001f);
        assertEquals(RH * 0.5f, p.height, 0.001f);
    }

    @Test
    public void rotationOriginIsTheLayerCentre() {
        LayerCalibration cal = new LayerCalibration();
        cal.rotation = 30f;
        LayerPlacement p = place(cal);
        assertEquals(30f, p.rotation, 0.001f);
        assertEquals(p.width / 2f, p.originX, 0.001f);
        assertEquals(p.height / 2f, p.originY, 0.001f);
    }

    @Test
    public void offsetAndScaleCompose() {
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 102.4f;
        cal.scaleX = 2f;
        LayerPlacement p = place(cal);

        // Centre moves by the offset, then the layer grows symmetrically about it.
        float expectedCentre = RX + RW / 2f + RW / 10f;
        assertEquals(expectedCentre, p.x + p.width / 2f, 0.001f);
        assertEquals(RW * 2f, p.width, 0.001f);
    }

    @Test
    public void placementIsIndependentOfWhereThePortraitSits() {
        // Calibration is authored in canvas space, so moving the portrait rectangle
        // must translate every layer with it rather than invalidating the numbers.
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 50f;
        cal.scaleY = 1.3f;

        LayerPlacement here = LayerPlacement.compute(cal, RX, RY, RW, RH, CANVAS_W, CANVAS_H);
        LayerPlacement moved = LayerPlacement.compute(cal, RX + 200f, RY - 80f, RW, RH, CANVAS_W, CANVAS_H);

        assertEquals(here.x + 200f, moved.x, 0.001f);
        assertEquals(here.y - 80f, moved.y, 0.001f);
        assertEquals(here.width, moved.width, 0.001f);
        assertEquals(here.height, moved.height, 0.001f);
    }

    /**
     * Where SpriteBatch actually draws a layer pixel, as a master-canvas point: the
     * forward direction, written independently of toLayerPixel so the two check each other.
     */
    private float[] drawnAt(LayerCalibration cal, float lx, float ly) {
        LayerPlacement p = place(cal);
        float fx = lx / CANVAS_W * p.width - p.originX;
        float fy = (1f - ly / CANVAS_H) * p.height - p.originY;
        double rad = Math.toRadians(p.rotation);
        float wx = p.x + p.originX + (float) (fx * Math.cos(rad) - fy * Math.sin(rad));
        float wy = p.y + p.originY + (float) (fx * Math.sin(rad) + fy * Math.cos(rad));
        return new float[]{(wx - RX) / RW * CANVAS_W, (RH - (wy - RY)) / RH * CANVAS_H};
    }

    private void assertRoundTrip(LayerCalibration cal, float lx, float ly) {
        float[] canvas = drawnAt(cal, lx, ly);
        float[] back = LayerPlacement.toLayerPixel(cal, canvas[0], canvas[1], RW, RH, CANVAS_W, CANVAS_H);
        assertEquals(lx, back[0], 0.05f);
        assertEquals(ly, back[1], 0.05f);
    }

    @Test
    public void toLayerPixelIsTheIdentityForAnUncalibratedLayer() {
        float[] at = LayerPlacement.toLayerPixel(new LayerCalibration(), 300f, 900f, RW, RH, CANVAS_W, CANVAS_H);
        assertEquals(300f, at[0], 0.01f);
        assertEquals(900f, at[1], 0.01f);
    }

    @Test
    public void toLayerPixelUndoesOffsetAndScale() {
        // Blood aimed at a point on the doll has to land on the pixel of the piece that is
        // actually drawn there, or a stain on the breastplate ends up on the belt.
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 12f;
        cal.offsetY = -128f;
        cal.scaleX = 2.0245f;
        cal.scaleY = 1.6416f;
        assertRoundTrip(cal, 512f, 700f);
        assertRoundTrip(cal, 380f, 1000f);
    }

    @Test
    public void toLayerPixelUndoesRotation() {
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = -40f;
        cal.offsetY = 60f;
        cal.scaleX = 0.8f;
        cal.scaleY = 1.2f;
        cal.rotation = 30f;
        assertRoundTrip(cal, 200f, 300f);
        assertRoundTrip(cal, 900f, 1400f);
    }
}
