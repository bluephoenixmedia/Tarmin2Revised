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
}
