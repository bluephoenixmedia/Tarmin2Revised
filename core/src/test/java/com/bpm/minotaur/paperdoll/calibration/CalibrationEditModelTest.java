package com.bpm.minotaur.paperdoll.calibration;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The editing rules behind the paperdoll editor, kept out of the LibGDX screen so they
 * can be tested without a GL context.
 *
 * The acceptance condition for this whole pipeline was that calibrating a piece is easy
 * and happens once. "Easy" here means concrete things: stepping through layers without
 * losing edits, nudging by a predictable amount, and being able to tell at a glance
 * whether anything is unsaved.
 */
public class CalibrationEditModelTest {

    private CalibrationStore store;
    private CalibrationEditModel model;

    @Before
    public void setUp() {
        store = new CalibrationStore();
        store.parse("{\"slotDefaults\":{\"chest\":{\"offsetX\":0,\"offsetY\":-30,\"scaleX\":0.98,\"scaleY\":0.98}},"
                + "\"layers\":{"
                + "\"chest/banded\":{\"offsetX\":-10,\"offsetY\":-28.5,\"scaleX\":0.98,\"scaleY\":0.98},"
                + "\"chest/breastplate\":{\"offsetX\":5,\"offsetY\":-20,\"scaleX\":1.0,\"scaleY\":1.0},"
                + "\"head/bascinet\":{\"offsetX\":0,\"offsetY\":-498,\"scaleX\":0.97,\"scaleY\":0.97}"
                + "}}");
        model = new CalibrationEditModel(store);
    }

    @Test
    public void listsLayersGroupedBySlotInAStableOrder() {
        List<String> slots = model.slots();
        assertEquals(Arrays.asList("chest", "head"), slots);
        assertEquals(Arrays.asList("chest/banded", "chest/breastplate"), model.layersInSlot("chest"));
    }

    @Test
    public void selectingALayerExposesItsCurrentCalibration() {
        model.select("chest/banded");
        assertEquals("chest/banded", model.selectedLayerId());
        assertEquals(-10f, model.current().offsetX, 0.0001f);
        assertEquals(-28.5f, model.current().offsetY, 0.0001f);
    }

    @Test
    public void editsAreWrittenThroughToTheStore() {
        model.select("chest/banded");
        model.current().offsetX = 42f;
        model.commit();
        assertEquals(42f, store.get("chest/banded").offsetX, 0.0001f);
    }

    @Test
    public void editingOneLayerDoesNotDisturbAnother() {
        model.select("chest/banded");
        model.current().offsetX = 99f;
        model.commit();

        model.select("chest/breastplate");
        assertEquals("editing one layer must not leak into the next",
                5f, model.current().offsetX, 0.0001f);
    }

    @Test
    public void steppingThroughLayersKeepsEdits() {
        // Calibrating 106 pieces means stepping constantly; an edit lost on step is the
        // difference between a one-time cost and doing it all again.
        model.select("chest/banded");
        model.current().offsetY = 7f;
        model.next();
        model.previous();
        assertEquals("chest/banded", model.selectedLayerId());
        assertEquals(7f, model.current().offsetY, 0.0001f);
    }

    @Test
    public void nextAndPreviousWalkEveryLayerAcrossSlotBoundaries() {
        model.select("chest/banded");
        model.next();
        assertEquals("chest/breastplate", model.selectedLayerId());
        model.next();
        assertEquals("stepping should cross into the next slot, not stop at its end",
                "head/bascinet", model.selectedLayerId());
    }

    @Test
    public void steppingStopsAtTheEndsRatherThanWrapping() {
        // Wrapping silently sends you back to the start of a 106-item pass with no
        // signal that you finished.
        model.select("head/bascinet");
        model.next();
        assertEquals("head/bascinet", model.selectedLayerId());
        assertTrue(model.atLast());

        model.select("chest/banded");
        model.previous();
        assertEquals("chest/banded", model.selectedLayerId());
        assertTrue(model.atFirst());
    }

    @Test
    public void nudgeMovesByTheGivenStep() {
        model.select("chest/banded");
        model.nudge(1f, 0f);
        assertEquals(-9f, model.current().offsetX, 0.0001f);
        model.nudge(0f, -10f);
        assertEquals(-38.5f, model.current().offsetY, 0.0001f);
    }

    @Test
    public void scalingIsMultiplicativeSoItFeelsTheSameAtEverySize() {
        // An additive step of 0.05 is a 5% change on a layer at scale 1.0 but a 50%
        // change on one at 0.1; multiplicative keeps the feel constant.
        model.select("chest/breastplate");
        model.scaleBy(1.1f, 1f);
        assertEquals(1.1f, model.current().scaleX, 0.0001f);
        assertEquals(1.0f, model.current().scaleY, 0.0001f);
    }

    @Test
    public void scaleIsClampedAwayFromZero() {
        // A layer scaled to zero vanishes with no way to grab it back.
        model.select("chest/breastplate");
        for (int i = 0; i < 200; i++) {
            model.scaleBy(0.5f, 0.5f);
        }
        assertTrue("scale must never reach zero", model.current().scaleX > 0f);
    }

    @Test
    public void tracksWhetherThereAreUnsavedChanges() {
        assertFalse(model.isDirty());
        model.select("chest/banded");
        model.nudge(1f, 0f);
        assertTrue(model.isDirty());
        model.markSaved();
        assertFalse(model.isDirty());
    }

    @Test
    public void revertRestoresTheLayerToItsLastSavedState() {
        model.select("chest/banded");
        float before = model.current().offsetX;
        model.nudge(25f, 25f);
        model.revert();
        assertEquals(before, model.current().offsetX, 0.0001f);
    }

    @Test
    public void resetToSlotDefaultUsesTheBakersMedianPlacement() {
        // The escape hatch when a layer has been nudged into nonsense.
        model.select("chest/banded");
        model.resetToSlotDefault();
        assertEquals(0f, model.current().offsetX, 0.0001f);
        assertEquals(-30f, model.current().offsetY, 0.0001f);
    }

    @Test
    public void flaggingArtForRedoIsPartOfTheEditNotASeparateChore() {
        model.select("chest/banded");
        model.current().needsArtRedo = true;
        model.commit();
        assertTrue(store.get("chest/banded").needsArtRedo);
        assertTrue(model.isDirty());
    }

    @Test
    public void countsLayersFlaggedForArtRecreation() {
        assertEquals(0, model.countNeedingArtRedo());

        model.select("chest/banded");
        model.current().needsArtRedo = true;
        model.commit();
        assertEquals(1, model.countNeedingArtRedo());

        model.select("head/bascinet");
        model.current().needsArtRedo = true;
        model.commit();
        assertEquals("the count spans every slot, not just the one being edited",
                2, model.countNeedingArtRedo());

        model.current().needsArtRedo = false;
        model.commit();
        assertEquals(1, model.countNeedingArtRedo());
    }

    @Test
    public void selectingAnUnknownLayerIsIgnoredRatherThanCrashing() {
        model.select("chest/banded");
        model.select("nosuch/layer");
        assertEquals("chest/banded", model.selectedLayerId());
    }

    @Test
    public void anEmptyStoreLeavesTheModelUsable() {
        CalibrationEditModel empty = new CalibrationEditModel(new CalibrationStore());
        assertTrue(empty.slots().isEmpty());
        assertNull(empty.selectedLayerId());
        assertNotNull("current() must never be null, even with nothing selected", empty.current());
        empty.next();
        empty.nudge(5f, 5f);
        assertNull(empty.selectedLayerId());
    }
}
