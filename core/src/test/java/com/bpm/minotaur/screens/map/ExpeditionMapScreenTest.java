package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.math.GridPoint2;
import com.bpm.minotaur.generation.Biome;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExpeditionMapScreenTest {

    @Test
    public void testCoordFormatting() {
        assertEquals("A1", ExpeditionMapScreen.coord(new GridPoint2(0, 0)));
        assertEquals("F5", ExpeditionMapScreen.coord(new GridPoint2(5, 4)));
        assertEquals("Z10", ExpeditionMapScreen.coord(new GridPoint2(25, 9)));
        assertEquals("??", ExpeditionMapScreen.coord(null));
        assertEquals("(-1, 0)", ExpeditionMapScreen.coord(new GridPoint2(-1, 0)));
    }

    @Test
    public void testDangerWordAndPips() {
        assertEquals("Unknown", ExpeditionMapScreen.dangerWord(0));
        assertEquals("[.....]", ExpeditionMapScreen.dangerPips(0));

        assertEquals("Low", ExpeditionMapScreen.dangerWord(1));
        assertEquals("[#....]", ExpeditionMapScreen.dangerPips(1));

        assertEquals("Wary", ExpeditionMapScreen.dangerWord(2));
        assertEquals("[##...]", ExpeditionMapScreen.dangerPips(2));

        assertEquals("Perilous", ExpeditionMapScreen.dangerWord(3));
        assertEquals("[###..]", ExpeditionMapScreen.dangerPips(3));

        assertEquals("Deadly", ExpeditionMapScreen.dangerWord(4));
        assertEquals("[####.]", ExpeditionMapScreen.dangerPips(4));

        assertEquals("Lethal", ExpeditionMapScreen.dangerWord(5));
        assertEquals("[#####]", ExpeditionMapScreen.dangerPips(5));

        assertEquals("Extreme", ExpeditionMapScreen.dangerWord(7));
        assertEquals("[#####]", ExpeditionMapScreen.dangerPips(7));
    }

    @Test
    public void testBiomeDescriptionsPresent() {
        for (Biome b : Biome.values()) {
            String desc = ExpeditionMapScreen.biomeDescription(b);
            assertNotNull("Biome description should not be null for " + b, desc);
            assertFalse("Biome description should not be empty for " + b, desc.trim().isEmpty());
        }
        assertNotNull(ExpeditionMapScreen.biomeDescription(null));
    }

    @Test
    public void testCompassNeedleAngleCalculation() {
        // World north (+y) draws up-right at rotation 0, as in the handoff's compass.
        float a0 = CompassWidget.calculateNeedleAngle(0f);
        assertTrue("points right", Math.cos(a0) > 0);
        assertTrue("points up", Math.sin(a0) > 0);

        // The needle always agrees with where the renderer actually draws north.
        for (int step = 0; step < 4; step++) {
            float rot = (float) (step * Math.PI / 2.0);
            com.badlogic.gdx.math.Vector2 c = IsometricDioramaRenderer.project(0f, 0f, 0f, 0f, 0f, 1f, rot, 720f, 448f);
            com.badlogic.gdx.math.Vector2 n = IsometricDioramaRenderer.project(0f, 1f, 0f, 0f, 0f, 1f, rot, 720f, 448f);
            double drawn = Math.atan2(-(n.y - c.y), n.x - c.x); // top-down y to y-up
            float needle = CompassWidget.calculateNeedleAngle(rot);
            assertEquals(Math.cos(drawn), Math.cos(needle), 0.001);
            assertEquals(Math.sin(drawn), Math.sin(needle), 0.001);
        }
    }
}
