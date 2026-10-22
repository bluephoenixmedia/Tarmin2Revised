package com.bpm.minotaur.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #173: the Hunger and Divinity readouts fit their cell, and their tooltips say what they mean. */
public class HudReadoutsTest {

    /** Roughly what the vitals cell holds in the small HUD font, with room to spare. */
    private static final int CELL_CHARS = 16;

    @Test
    public void theHungerLabelIsShortAtEveryStage() {
        assertEquals("TARMIN 4/50", HudReadouts.hungerLabel(4, 50, false));
        assertEquals("TARMIN 46/50 !", HudReadouts.hungerLabel(46, 50, false));
        assertEquals("TARMIN 50/50 !", HudReadouts.hungerLabel(50, 50, true));
        for (int d = 0; d <= 50; d++) {
            assertTrue(HudReadouts.hungerLabel(d, 50, d >= 50).length() <= CELL_CHARS);
        }
    }

    @Test
    public void theHungerTipNamesItAndExplainsTheCap() {
        HudReadouts.Tip tip = HudReadouts.hungerTip(12, 50, false);
        assertEquals("Tarmin's Hunger", tip.title);
        assertTrue(tip.details, tip.details.contains("12 of 50"));
        assertTrue(tip.details, tip.details.contains("Bridge falls"));
        assertTrue("next stage named", tip.details.contains("25 deaths"));
        assertFalse("never the internal name", (tip.title + tip.details + tip.remedy).toLowerCase().contains("doom"));
    }

    @Test
    public void theHungerTipWarnsWhenTheBossIsLoose() {
        HudReadouts.Tip tip = HudReadouts.hungerTip(50, 50, true);
        assertTrue(tip.details, tip.details.contains("Warden"));
        assertTrue(tip.remedy, tip.remedy.contains("Slay"));
    }

    @Test
    public void theDivinityTipIsPluralAware() {
        assertTrue(HudReadouts.divinityTip(1).details.contains("1 Divinity."));
        assertTrue(HudReadouts.divinityTip(3).details.contains("3 Divinities."));
        assertEquals("DIV: 7", HudReadouts.divinityLabel(7));
    }

    @Test
    public void everyStringSurvivesTheFont() {
        for (int d = 0; d <= 50; d++) {
            for (boolean boss : new boolean[] { false, true }) {
                HudReadouts.Tip t = HudReadouts.hungerTip(d, 50, boss);
                for (String s : new String[] { t.title, t.category, t.details, t.remedy, HudReadouts.hungerLabel(d, 50, boss) }) {
                    assertEquals(-1, UiGlyphs.firstUnsupportedIndex(s));
                }
            }
        }
        HudReadouts.Tip div = HudReadouts.divinityTip(5);
        assertEquals(-1, UiGlyphs.firstUnsupportedIndex(div.details + div.remedy + div.title + div.category));
    }

    // --- Exposure (#194) ---------------------------------------------------------------

    @Test
    public void theExposureMeterNamesTheBandAndFillsWithWarmth() {
        assertEquals("COMFORTABLE", HudReadouts.exposureMeterText(com.bpm.minotaur.gamedata.player.Exposure.Band.COMFORTABLE));
        assertEquals("HYPOTHERMIC", HudReadouts.exposureMeterText(com.bpm.minotaur.gamedata.player.Exposure.Band.HYPOTHERMIC));
        assertTrue(HudReadouts.exposureMeterFill(37f) > HudReadouts.exposureMeterFill(34f));
        assertEquals(0f, HudReadouts.exposureMeterFill(20f), 0f);
        assertEquals(1f, HudReadouts.exposureMeterFill(45f), 0f);
    }

    @Test
    public void everyExposureStringSurvivesTheFont() {
        for (com.bpm.minotaur.gamedata.player.Exposure.Band b : com.bpm.minotaur.gamedata.player.Exposure.Band.values()) {
            for (com.bpm.minotaur.gamedata.player.Exposure.Wetness w : com.bpm.minotaur.gamedata.player.Exposure.Wetness.values()) {
                HudReadouts.Tip tip = HudReadouts.exposureTip(b, w);
                assertEquals(-1, UiGlyphs.firstUnsupportedIndex(tip.title + tip.category + tip.details + tip.remedy
                        + HudReadouts.exposureMeterText(b)));
                assertTrue(HudReadouts.exposureMeterText(b).length() <= 12);
            }
            HudReadouts.Tip wet = HudReadouts.wetnessTip(com.bpm.minotaur.gamedata.player.Exposure.Wetness.SOAKED);
            assertEquals(-1, UiGlyphs.firstUnsupportedIndex(wet.title + wet.category + wet.details + wet.remedy));
        }
    }
}
