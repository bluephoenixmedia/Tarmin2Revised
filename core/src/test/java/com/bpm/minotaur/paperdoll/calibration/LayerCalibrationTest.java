package com.bpm.minotaur.paperdoll.calibration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Calibration is the single source of truth for where a paperdoll layer lands.
 * These tests pin the invariants the whole pipeline leans on:
 *   - an uncalibrated layer behaves exactly like the old "stretch to fill" blit,
 *   - calibration survives a save/load round trip without drift.
 */
public class LayerCalibrationTest {

    @Test
    public void defaultCalibrationIsIdentity() {
        LayerCalibration cal = new LayerCalibration();
        assertEquals(0f, cal.offsetX, 0f);
        assertEquals(0f, cal.offsetY, 0f);
        assertEquals(1f, cal.scaleX, 0f);
        assertEquals(1f, cal.scaleY, 0f);
        assertEquals(0f, cal.rotation, 0f);
        assertFalse(cal.hidesHair);
        assertFalse(cal.hidesBeard);
        assertFalse(cal.needsArtRedo);
        assertTrue("a fresh calibration must be identity", cal.isIdentity());
    }

    @Test
    public void nudgingAnyTransformFieldBreaksIdentity() {
        LayerCalibration offset = new LayerCalibration();
        offset.offsetX = 1f;
        assertFalse(offset.isIdentity());

        LayerCalibration scaled = new LayerCalibration();
        scaled.scaleY = 1.01f;
        assertFalse(scaled.isIdentity());

        LayerCalibration rotated = new LayerCalibration();
        rotated.rotation = 0.5f;
        assertFalse(rotated.isIdentity());
    }

    @Test
    public void flagsAloneDoNotBreakIdentity() {
        // hidesHair/needsArtRedo are metadata, not geometry: a layer carrying them
        // still draws exactly where an uncalibrated one would.
        LayerCalibration cal = new LayerCalibration();
        cal.hidesHair = true;
        cal.needsArtRedo = true;
        assertTrue(cal.isIdentity());
    }

    @Test
    public void unknownLayerResolvesToIdentityRatherThanNull() {
        CalibrationStore store = new CalibrationStore();
        LayerCalibration cal = store.get("chest/never_calibrated");
        assertNotNull("missing layers must degrade to identity, not NPE", cal);
        assertTrue(cal.isIdentity());
    }

    @Test
    public void storeRoundTripsThroughJsonWithoutDrift() {
        CalibrationStore store = new CalibrationStore();
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = -37.5f;
        cal.offsetY = 112f;
        cal.scaleX = 1.24f;
        cal.scaleY = 0.86f;
        cal.rotation = -12.5f;
        cal.hidesHair = true;
        cal.needsArtRedo = true;
        cal.sourceHash = "deadbeef";
        store.put("chest/breastplate", cal);

        CalibrationStore reloaded = new CalibrationStore();
        reloaded.parse(store.serialize());

        LayerCalibration out = reloaded.get("chest/breastplate");
        assertEquals(-37.5f, out.offsetX, 0.0001f);
        assertEquals(112f, out.offsetY, 0.0001f);
        assertEquals(1.24f, out.scaleX, 0.0001f);
        assertEquals(0.86f, out.scaleY, 0.0001f);
        assertEquals(-12.5f, out.rotation, 0.0001f);
        assertTrue(out.hidesHair);
        assertFalse(out.hidesBeard);
        assertTrue(out.needsArtRedo);
        assertEquals("deadbeef", out.sourceHash);
    }

    @Test
    public void serializedFormIsHandEditablePlainJson() {
        // skeleton.json was written by libGDX's Json with {"class":"java.lang.Float"}
        // wrappers, which is unreadable and unusable as a hand-edit escape hatch.
        // The calibration file must never look like that.
        CalibrationStore store = new CalibrationStore();
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 5f;
        store.put("head/bascinet", cal);

        String json = store.serialize();
        assertFalse("must not emit java class tags", json.contains("java.lang.Float"));
        assertTrue(json.contains("\"head/bascinet\""));
        assertTrue(json.contains("\"offsetX\""));
    }

    @Test
    public void parsingToleratesAPartiallySpecifiedLayer() {
        // Hand-edited entries will omit fields they do not care about; those must
        // fall back to identity rather than zeroing the scale and vanishing the layer.
        CalibrationStore store = new CalibrationStore();
        store.parse("{\"layers\":{\"legs/greaves\":{\"offsetY\":40}}}");

        LayerCalibration cal = store.get("legs/greaves");
        assertEquals(40f, cal.offsetY, 0.0001f);
        assertEquals("omitted scale must default to 1, not 0", 1f, cal.scaleX, 0.0001f);
        assertEquals(1f, cal.scaleY, 0.0001f);
        assertEquals(0f, cal.offsetX, 0.0001f);
    }
}
