package com.bpm.minotaur.rendering.gate;

import com.badlogic.gdx.utils.JsonReader;
import org.junit.Test;

import static org.junit.Assert.*;

public class GateUvTest {

    private static float[] map(GateUv.Part p, float u, float v) {
        float[] out = new float[2];
        p.apply(u, v, out);
        return out;
    }

    @Test
    public void byDefaultTheTextureIsFlippedForBlenderUpwardV() {
        float[] uv = map(new GateUv().part("frame"), 0.25f, 0.9f);
        assertEquals(0.25f, uv[0], 1e-5f);
        assertEquals(0.1f, uv[1], 1e-5f);
    }

    @Test
    public void withTheFlipOffAndNoAdjustmentTheUvIsUntouched() {
        GateUv.Part p = new GateUv().part("frame");
        p.flipV = false;
        float[] uv = map(p, 0.3f, 0.7f);
        assertEquals(0.3f, uv[0], 1e-5f);
        assertEquals(0.7f, uv[1], 1e-5f);
    }

    @Test
    public void offsetAndScaleActAboutTheCentre() {
        GateUv.Part p = new GateUv().part("doorLeft");
        p.flipV = false;
        p.scaleU = 2f;
        p.offsetV = 0.1f;
        float[] uv = map(p, 0.75f, 0.5f);
        assertEquals(1.0f, uv[0], 1e-5f);
        assertEquals(0.6f, uv[1], 1e-5f);
        assertEquals(0.5f, map(p, 0.5f, 0.5f)[0], 1e-5f);
    }

    @Test
    public void aQuarterTurnRotatesAboutTheCentre() {
        GateUv.Part p = new GateUv().part("doorRight");
        p.flipV = false;
        p.rotation = 90f;
        float[] uv = map(p, 1f, 0.5f);
        assertEquals(0.5f, uv[0], 1e-5f);
        assertEquals(1f, uv[1], 1e-5f);
    }

    @Test
    public void theMappingSurvivesTheJsonRoundTrip() {
        GateUv uv = new GateUv();
        GateUv.Part p = uv.part("frame");
        p.flipV = false;
        p.offsetU = 0.125f;
        p.scaleV = 1.5f;
        p.rotation = 270f;
        GateUv back = GateUv.parse(new JsonReader().parse(uv.toJson()));
        GateUv.Part q = back.part("frame");
        assertFalse(q.flipV);
        assertEquals(0.125f, q.offsetU, 1e-4f);
        assertEquals(1.5f, q.scaleV, 1e-4f);
        assertEquals(270f, q.rotation, 1e-2f);
        assertTrue(back.part("doorLeft").isDefault());
    }

    @Test
    public void anEmptyOrMissingFileGivesTheDefaults() {
        assertTrue(GateUv.parse(null).part("frame").isDefault());
        assertTrue(GateUv.parse(new JsonReader().parse("{}")).part("doorRight").isDefault());
    }

    @Test
    public void transformRewritesOnlyTheUvFloatsOfAnInterleavedArray() {
        float[] pristine = { 1f, 2f, 3f, 0.2f, 0.9f, 7f, 4f, 5f, 6f, 0.5f, 0.5f, 8f };
        float[] target = pristine.clone();
        GateUv.transform(new GateUv().part("frame"), pristine, target, 6, 3);
        assertEquals(1f, target[0], 0f);
        assertEquals(7f, target[5], 0f);
        assertEquals(0.2f, target[3], 1e-5f);
        assertEquals(0.1f, target[4], 1e-5f);
        assertEquals(0.5f, target[10], 1e-5f);
        assertEquals(0.9f, pristine[4], 0f);
    }
}
