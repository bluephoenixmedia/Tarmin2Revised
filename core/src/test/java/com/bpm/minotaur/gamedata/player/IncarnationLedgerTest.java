package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.ui.UiGlyphs;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The death screen's account of what the Incarnation loses and keeps (#206). */
public class IncarnationLedgerTest {

    private static String joined(List<IncarnationLedger.Line> lines) {
        StringBuilder sb = new StringBuilder();
        for (IncarnationLedger.Line l : lines) {
            sb.append(l.label).append(' ').append(l.value).append('\n');
        }
        return sb.toString();
    }

    @Test
    public void theIncarnationLosesLevelStatsAndCarriedGear() {
        String lost = joined(IncarnationLedger.lost(7));
        assertTrue(lost.contains("LEVEL"));
        assertTrue(lost.contains("STATS"));
        assertTrue(lost.contains("CARRIED GEAR 7 ITEMS"));
        assertTrue(lost.contains("WITH YOUR REMAINS"));
        assertTrue(joined(IncarnationLedger.lost(1)).contains("1 ITEM\n"));
    }

    @Test
    public void theSoulAndTheWorldKeepTheirShare() {
        String kept = joined(IncarnationLedger.kept());
        for (String expected : new String[]{"KNOWN SPELLS", "FATHER'S SWORD", "UNLOCKS", "ALTAR",
                "SHELTER CHEST", "TARMIN'S HUNGER"}) {
            assertTrue("kept list should name " + expected, kept.contains(expected));
        }
    }

    @Test
    public void nothingClaimsTheBackpackSurvives() {
        String all = joined(IncarnationLedger.lost(3)) + joined(IncarnationLedger.kept());
        assertFalse("The pack dies with the Incarnation", all.contains("PRESERVED"));
        assertFalse(all.contains("BACKPACK"));
        assertFalse(all.contains("QUICKSLOT"));
    }

    @Test
    public void everyLineIsInTheFontsCharacterSet() {
        List<IncarnationLedger.Line> all = new ArrayList<>(IncarnationLedger.lost(12));
        all.addAll(IncarnationLedger.kept());
        for (IncarnationLedger.Line l : all) {
            assertEquals(l.label, UiGlyphs.sanitize(l.label));
            assertEquals(l.value, UiGlyphs.sanitize(l.value));
        }
    }
}
