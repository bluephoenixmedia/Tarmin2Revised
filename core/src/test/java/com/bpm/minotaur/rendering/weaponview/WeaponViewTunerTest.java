package com.bpm.minotaur.rendering.weaponview;

import com.bpm.minotaur.paperdoll.calibration.CalibrationStore;
import com.bpm.minotaur.paperdoll.calibration.LayerCalibration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The tuner writes straight into the store the overlay draws from, so these check the
 * store -- which is what ends up on screen and on disk -- rather than the tuner's view.
 */
public class WeaponViewTunerTest {

    private WeaponViewCalibration calibration;
    private CalibrationStore store;
    private WeaponViewTuner tuner;

    @Before
    public void setUp() {
        calibration = new WeaponViewCalibration();
        store = calibration.store();
        tuner = new WeaponViewTuner(store);
        tuner.setEquipment("SLASHING_1H", "SWORD", "SMALL_SHIELD");
    }

    @Test
    public void editingAWeaponWritesItsOwnEntryAndLeavesTheArchetypeAlone() {
        tuner.nudge(0.01f, 0.02f);

        LayerCalibration sword = calibration.get("SLASHING_1H", "SWORD");
        assertEquals(0.01f, sword.offsetX, 1e-6f);
        assertEquals(0.02f, sword.offsetY, 1e-6f);
        assertNull(store.slotDefault("SLASHING_1H"));
        // Another sword-family weapon is untouched.
        assertEquals(0f, calibration.get("SLASHING_1H", "DAGGER").offsetX, 0f);
    }

    @Test
    public void archetypeScopeMovesEveryWeaponWithoutItsOwnEntry() {
        tuner.toggleScope();
        tuner.nudge(0.03f, 0f);

        assertEquals(0.03f, calibration.get("SLASHING_1H", "SWORD").offsetX, 1e-6f);
        assertEquals(0.03f, calibration.get("SLASHING_1H", "DAGGER").offsetX, 1e-6f);
        assertEquals(0f, calibration.get("AXE_CHOPPING", "AXE").offsetX, 0f);
    }

    @Test
    public void aNewWeaponEntryStartsFromItsArchetypeDefault() {
        // Otherwise the first nudge on a weapon inheriting a tuned archetype would snap
        // it back to the hardcoded anchor, far from where it visibly was.
        tuner.toggleScope();
        tuner.nudge(0.05f, 0f);
        tuner.toggleScope();
        tuner.nudge(0.01f, 0f);

        assertEquals(0.06f, calibration.get("SLASHING_1H", "SWORD").offsetX, 1e-6f);
        assertEquals(0.05f, store.slotDefault("SLASHING_1H").offsetX, 1e-6f);
    }

    @Test
    public void editingAWeaponNeverMutatesTheSharedIdentity() {
        // CalibrationStore hands out one IDENTITY instance on every miss. Editing it in
        // place would silently tune every untuned weapon in the game.
        tuner.nudge(0.02f, 0f);
        assertEquals(0f, calibration.get("BLUNT_CRUSHING", "MACE").offsetX, 0f);
        assertEquals(0f, store.get(null).offsetX, 0f);
    }

    @Test
    public void offHandTunesUnderItsOwnArchetype() {
        tuner.toggleHand();
        tuner.rotateBy(5f);

        assertEquals(5f, calibration.get(WeaponViewCalibration.OFF_HAND, "SMALL_SHIELD").rotation, 1e-6f);
        assertEquals(0f, calibration.get("SLASHING_1H", "SWORD").rotation, 0f);
    }

    @Test
    public void anEmptyHandHasNothingToTune() {
        tuner.setEquipment("BRAWLING", null, null);
        tuner.nudge(0.1f, 0.1f);
        tuner.toggleFlip();

        assertFalse(tuner.hasTarget());
        assertFalse(tuner.isDirty());
        assertEquals(0, store.size());
    }

    @Test
    public void scaleIsUniformAndCannotReachZero() {
        tuner.scaleBy(1.5f);
        LayerCalibration cal = calibration.get("SLASHING_1H", "SWORD");
        assertEquals(1.5f, cal.scaleX, 1e-6f);
        assertEquals(1.5f, cal.scaleY, 1e-6f);

        for (int i = 0; i < 200; i++) {
            tuner.scaleBy(0.5f);
        }
        assertTrue(cal.scaleY > 0f);
    }

    @Test
    public void clearingAWeaponFallsBackToItsArchetype() {
        tuner.toggleScope();
        tuner.nudge(0.04f, 0f);
        tuner.toggleScope();
        tuner.nudge(0.10f, 0f);

        tuner.clear();

        assertFalse(store.has(WeaponViewCalibration.key("SLASHING_1H", "SWORD")));
        assertEquals(0.04f, calibration.get("SLASHING_1H", "SWORD").offsetX, 1e-6f);
    }

    @Test
    public void clearingAnArchetypeFallsBackToNoTuning() {
        tuner.toggleScope();
        tuner.nudge(0.04f, 0f);
        tuner.clear();

        assertNull(store.slotDefault("SLASHING_1H"));
        assertTrue(calibration.get("SLASHING_1H", "SWORD").isIdentity());
    }

    @Test
    public void clearingNothingIsNotAnUnsavedChange() {
        tuner.clear();
        assertFalse(tuner.isDirty());
    }

    @Test
    public void editsAreDirtyUntilSaved() {
        tuner.toggleFlip();
        assertTrue(tuner.isDirty());
        tuner.markSaved();
        assertFalse(tuner.isDirty());
    }

    @Test
    public void tuningSurvivesARoundTripThroughTheFile() {
        tuner.toggleScope();
        tuner.nudge(0.02f, -0.01f);
        tuner.toggleScope();
        tuner.rotateBy(-7f);
        tuner.toggleFlip();

        WeaponViewCalibration reread = new WeaponViewCalibration();
        reread.store().parse(store.serialize());

        LayerCalibration sword = reread.get("SLASHING_1H", "SWORD");
        assertEquals(-7f, sword.rotation, 1e-6f);
        assertTrue(sword.flipX);
        assertEquals(0.02f, reread.get("SLASHING_1H", "DAGGER").offsetX, 1e-6f);
    }

    @Test
    public void theFileSaysItsOwnUnits() {
        // Same store class, different units from the paperdoll: the comment is the only
        // place a hand-editor finds out which, so it must not say "canvas pixels".
        String json = store.serialize();
        assertTrue(json.contains("viewport fractions"));
        assertFalse(json.contains("1024x1536"));
    }
}
