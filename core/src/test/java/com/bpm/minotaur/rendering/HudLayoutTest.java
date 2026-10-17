package com.bpm.minotaur.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** #108: the dashboard's columns are budgeted from line heights, so they neither spill nor sit empty. */
public class HudLayoutTest {

    @Test
    public void theChronicleNeverRunsPastTheBand() {
        for (float row = 18f; row <= 34f; row += 1f) {
            for (float header = 18f; header <= 28f; header += 2f) {
                int rows = HudLayout.chronicleRows(12f, header, row);
                float used = 12f + header + rows * row;
                if (rows > HudLayout.CHRONICLE_MIN_ROWS) {
                    assertTrue("rows=" + rows + " row=" + row + " header=" + header,
                            used <= HudLayout.DASHBOARD_HEIGHT - HudLayout.DASHBOARD_PAD_V);
                }
            }
        }
    }

    @Test
    public void theChronicleUsesTheRoomItHas() {
        // 168 inner, 24 header: 144 left, 6 rows of 24 exactly.
        assertEquals(6, HudLayout.chronicleRows(12f, 24f, 24f));
        assertEquals(5, HudLayout.chronicleRows(12f, 24f, 28f));
    }

    @Test
    public void vitalsBarsShrinkBeforeTheStatBlockOverflows() {
        // 164 inner, 10 of gaps, a 90-unit stat block: 64 left, 21 a bar.
        assertEquals(21f, HudLayout.vitalsBarHeight(16f, 10f, 90f), 0.001f);
        assertEquals(HudLayout.BAR_MAX_HEIGHT, HudLayout.vitalsBarHeight(16f, 10f, 60f), 0.001f);
        float h = HudLayout.vitalsBarHeight(16f, 10f, 96f);
        assertTrue(16f + 10f + 96f + 3 * h <= HudLayout.DASHBOARD_HEIGHT - HudLayout.DASHBOARD_PAD_V);
    }

    @Test
    public void olderRowsAreDimmer() {
        assertEquals(0.40f, HudLayout.chronicleRowAlpha(0, 5), 0.001f);
        assertEquals(1.0f, HudLayout.chronicleRowAlpha(4, 5), 0.001f);
        assertTrue(HudLayout.chronicleRowAlpha(1, 5) < HudLayout.chronicleRowAlpha(2, 5));
    }
}
