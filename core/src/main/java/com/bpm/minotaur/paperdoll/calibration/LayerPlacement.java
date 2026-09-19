package com.bpm.minotaur.paperdoll.calibration;

/**
 * A resolved draw call: where a calibrated layer lands on the stage.
 *
 * Maps canvas-space calibration onto the widget's rectangle. Because calibration is
 * relative to the canvas rather than the stage, moving the portrait translates every
 * layer with it instead of invalidating the numbers.
 */
public final class LayerPlacement {

    public float x;
    public float y;
    public float width;
    public float height;
    public float originX;
    public float originY;
    public float rotation;

    public static LayerPlacement compute(LayerCalibration cal,
                                         float rectX, float rectY,
                                         float rectW, float rectH,
                                         int canvasW, int canvasH) {
        LayerPlacement p = new LayerPlacement();

        // Stage pixels per canvas pixel. The portrait rectangle is deliberately not
        // 2:3 — the inventory page art is itself stretched non-uniformly onto the
        // stage, and the doll has to match the page it sits on.
        float sx = rectW / canvasW;
        float sy = rectH / canvasH;

        p.width = rectW * cal.scaleX;
        p.height = rectH * cal.scaleY;

        // +offsetY is downward in image space; stage space is y-up, hence the subtraction.
        float centreX = rectX + rectW / 2f + cal.offsetX * sx;
        float centreY = rectY + rectH / 2f - cal.offsetY * sy;

        p.x = centreX - p.width / 2f;
        p.y = centreY - p.height / 2f;

        // Scale and rotation both act about the layer's own centre. Anchoring at the
        // corner instead would slide the piece off the body on every resize, which is
        // what makes slider-driven fitting feel unusable.
        p.originX = p.width / 2f;
        p.originY = p.height / 2f;
        p.rotation = cal.rotation;

        return p;
    }

    /**
     * The inverse of {@link #compute}: which pixel of a layer's own texture sits under a
     * master-canvas point. Both are 1024x1536-style canvases with +y down.
     *
     * Worked in the stage rectangle rather than on the canvas because the rectangle is
     * not 2:3, and rotation only undoes correctly in the space it was applied in. The
     * rectangle's position is irrelevant, so only its size is taken.
     *
     * @return {x, y} in layer pixels; may fall outside the texture
     */
    public static float[] toLayerPixel(LayerCalibration cal, float canvasX, float canvasY,
                                       float rectW, float rectH, int canvasW, int canvasH) {
        LayerPlacement p = compute(cal, 0f, 0f, rectW, rectH, canvasW, canvasH);

        // Canvas point to stage space (y up), relative to the layer's centre.
        float sx = canvasX * rectW / canvasW;
        float sy = rectH - canvasY * rectH / canvasH;
        float dx = sx - (p.x + p.originX);
        float dy = sy - (p.y + p.originY);

        // Undo the CCW rotation SpriteBatch applied about that centre.
        double rad = Math.toRadians(-cal.rotation);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float ux = dx * cos - dy * sin;
        float uy = dx * sin + dy * cos;

        float lx = (ux / p.width + 0.5f) * canvasW;
        float ly = (0.5f - uy / p.height) * canvasH;
        return new float[]{lx, ly};
    }
}
