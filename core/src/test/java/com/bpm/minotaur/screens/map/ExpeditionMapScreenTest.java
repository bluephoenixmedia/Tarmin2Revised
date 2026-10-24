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
        assertEquals("?1", ExpeditionMapScreen.coord(new GridPoint2(-1, 0)));
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
        // At rot = 0 (default view facing North-East):
        // Needle should point along the isometric projection of world North (+Y):
        // Screen dx = -28, Screen dy = +14.
        float angle0 = CompassWidget.calculateNeedleAngle(0f);
        assertFalse(Float.isNaN(angle0));
        assertFalse(Float.isInfinite(angle0));

        // Screen vector corresponding to angle0
        float cos0 = (float) Math.cos(angle0);
        float sin0 = (float) Math.sin(angle0);
        assertTrue("Cos should be negative (pointing left on screen)", cos0 < 0);
        assertTrue("Sin should be positive (pointing up on screen)", sin0 > 0);

        // At rot = PI / 2 (rotated 90 deg clockwise):
        float angle90 = CompassWidget.calculateNeedleAngle((float) (Math.PI / 2.0));
        float cos90 = (float) Math.cos(angle90);
        float sin90 = (float) Math.sin(angle90);
        assertTrue("Cos should be negative", cos90 < 0);
        assertTrue("Sin should be negative (pointing down on screen)", sin90 < 0);

        // Smooth rotation over 360 degrees
        for (int deg = 0; deg <= 360; deg += 15) {
            float rad = (float) Math.toRadians(deg);
            float a = CompassWidget.calculateNeedleAngle(rad);
            assertFalse("Angle must not be NaN at " + deg + " deg", Float.isNaN(a));
        }
    }
}
