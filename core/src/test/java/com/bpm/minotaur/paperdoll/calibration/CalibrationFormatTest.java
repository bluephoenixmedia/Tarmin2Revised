package com.bpm.minotaur.paperdoll.calibration;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The calibration file has two writers: {@link CalibrationStore#serialize()} in Java and
 * write_calibration() in tools/bake_paperdoll_layers.py. They must agree.
 *
 * They already diverged once: the Python writer emitted only the five geometry fields,
 * so a re-bake erased hidesHair, hidesBeard and needsArtRedo — while
 * docs/paperdoll_triage.md instructs the reader to set needsArtRedo in that very file
 * and then builds the worklist by reading it back. These tests run against the real
 * generated asset so the next divergence fails here instead of silently emptying a
 * worklist.
 */
public class CalibrationFormatTest {

    private static File resolveFile(String relativePath) {
        File f = new File(relativePath);
        if (!f.exists()) {
            f = new File("../" + relativePath);
        }
        return f;
    }

    private CalibrationStore loadGenerated() throws IOException {
        File f = resolveFile("assets/data/paperdoll_calibration.json");
        assertTrue("generated calibration should exist: " + f, f.exists());
        CalibrationStore store = new CalibrationStore();
        store.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        return store;
    }

    @Test
    public void javaCanReadWhatThePythonBakerWrites() throws IOException {
        CalibrationStore store = loadGenerated();
        assertTrue("expected the full baked layer set", store.size() >= 300);

        LayerCalibration breastplate = store.get("chest/breastplate");
        assertTrue("a baked layer should carry real placement", !breastplate.isIdentity());
        assertNotNull("the baker records the digest it measured", breastplate.sourceHash);
    }

    @Test
    public void reSerialisingTheGeneratedFileIsStable() throws IOException {
        // Round trip through the Java writer and back. Any field the Java writer drops,
        // or writes in a shape it cannot read, shows up as a mismatch here.
        CalibrationStore first = loadGenerated();
        CalibrationStore second = new CalibrationStore();
        second.parse(first.serialize());

        assertEquals(first.size(), second.size());
        for (String layerId : first.all().keySet()) {
            LayerCalibration a = first.get(layerId);
            LayerCalibration b = second.get(layerId);
            assertEquals(layerId + " offsetX", a.offsetX, b.offsetX, 0.0001f);
            assertEquals(layerId + " offsetY", a.offsetY, b.offsetY, 0.0001f);
            assertEquals(layerId + " scaleX", a.scaleX, b.scaleX, 0.0001f);
            assertEquals(layerId + " scaleY", a.scaleY, b.scaleY, 0.0001f);
            assertEquals(layerId + " rotation", a.rotation, b.rotation, 0.0001f);
            assertEquals(layerId + " hidesHair", a.hidesHair, b.hidesHair);
            assertEquals(layerId + " hidesBeard", a.hidesBeard, b.hidesBeard);
            assertEquals(layerId + " needsArtRedo", a.needsArtRedo, b.needsArtRedo);
            assertEquals(layerId + " sourceHash", a.sourceHash, b.sourceHash);
        }
    }

    @Test
    public void everyFieldSurvivesTheJavaWriter() {
        // Explicitly covers the flags, which are the fields the Python writer dropped.
        CalibrationStore store = new CalibrationStore();
        LayerCalibration cal = new LayerCalibration();
        cal.offsetX = 11.5f;
        cal.offsetY = -22.25f;
        cal.scaleX = 1.3f;
        cal.scaleY = 0.7f;
        cal.rotation = 15f;
        cal.hidesHair = true;
        cal.hidesBeard = true;
        cal.needsArtRedo = true;
        cal.sourceHash = "cafebabe";
        store.put("head/great_helm", cal);

        CalibrationStore reloaded = new CalibrationStore();
        reloaded.parse(store.serialize());
        LayerCalibration out = reloaded.get("head/great_helm");

        assertTrue(out.hidesHair);
        assertTrue(out.hidesBeard);
        assertTrue(out.needsArtRedo);
        assertEquals("cafebabe", out.sourceHash);
        assertEquals(15f, out.rotation, 0.0001f);
    }

    @Test
    public void theBakerWritesASlotDefaultForEverySlotItBakes() throws IOException {
        CalibrationStore store = loadGenerated();
        String[] slots = {"head", "chest", "arms", "hands", "legs", "feet", "shield", "weapon"};
        for (String slot : slots) {
            LayerCalibration def = store.slotDefault(slot);
            assertNotNull("missing slot default for " + slot, def);
            assertTrue("a slot default of identity would put new art at canvas centre: " + slot,
                    !def.isIdentity());
        }
    }

    @Test
    public void anUncalibratedLayerFallsBackToItsSlotDefault() throws IOException {
        // The behaviour that keeps newly added art from rendering at chest height.
        CalibrationStore store = loadGenerated();
        LayerCalibration brandNew = store.get("head/some_helmet_added_tomorrow");
        LayerCalibration headDefault = store.slotDefault("head");

        assertEquals(headDefault.offsetY, brandNew.offsetY, 0.0001f);
        assertEquals(headDefault.scaleX, brandNew.scaleX, 0.0001f);
    }

    @Test
    public void anUnknownSlotStillDegradesToIdentityRatherThanFailing() {
        CalibrationStore store = new CalibrationStore();
        assertTrue(store.get("nosuchslot/nosuchlayer").isIdentity());
        assertTrue(store.get(null).isIdentity());
        assertTrue(store.get("noslashatall").isIdentity());
    }
}
