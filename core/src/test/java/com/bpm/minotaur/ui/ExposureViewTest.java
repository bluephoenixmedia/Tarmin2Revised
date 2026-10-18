package com.bpm.minotaur.ui;

import com.bpm.minotaur.gamedata.player.Exposure;
import org.junit.Test;

import static org.junit.Assert.*;

/** Every Exposure band has exactly one HUD view: pill, colour, danger frame and tip line (#194). */
public class ExposureViewTest {

    @Test
    public void everyBandHasItsOwnView() {
        for (Exposure.Band band : Exposure.Band.values()) {
            ExposureView view = ExposureView.of(band);
            assertEquals(band, view.band);
            assertNotNull(view.color);
            assertNotNull(view.effect);
            if (view.pill != null) assertEquals(view.pill, UiGlyphs.sanitize(view.pill));
        }
        assertEquals(Exposure.Band.values().length, ExposureView.values().length);
    }

    @Test
    public void onlyTheBitingBandsRaiseAPillAndOnlyTheExtremesFrameDanger() {
        assertNull(ExposureView.of(Exposure.Band.COMFORTABLE).pill);
        assertNull(ExposureView.of(Exposure.Band.WARM).pill);
        assertEquals("HYPO", ExposureView.of(Exposure.Band.HYPOTHERMIC).pill);
        assertTrue(ExposureView.of(Exposure.Band.HOT).danger);
        assertTrue(ExposureView.of(Exposure.Band.FREEZING).danger);
        assertFalse(ExposureView.of(Exposure.Band.COLD).danger);
    }
}
