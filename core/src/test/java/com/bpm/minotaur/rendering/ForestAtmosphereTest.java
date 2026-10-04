package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.weather.WeatherType;
import org.junit.Test;

import static org.junit.Assert.*;

public class ForestAtmosphereTest {

    private static final Color VOLCANIC_DAWN = new Color(0.95f, 0.33f, 0.12f, 1f);
    private static final Color WHITE_FOG = new Color(Color.WHITE);

    private static boolean redDominates(Color c) {
        return c.r > c.g && c.r > c.b;
    }

    @Test
    public void clearWeatherFogIsGreenBlackNotRed() {
        Color fog = ForestAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, VOLCANIC_DAWN, new Color());

        assertFalse("canopy fog must not read red at dawn: " + fog, redDominates(fog));
        assertTrue("canopy fog is near black: " + fog, fog.r + fog.g + fog.b < 0.4f);
        assertTrue("canopy fog keeps a green cast: " + fog, fog.g > fog.r);
    }

    @Test
    public void skyTintStillShiftsTheFogSlightly() {
        Color white = ForestAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, Color.WHITE, new Color());
        Color dawn = ForestAtmosphere.fogColor(WeatherType.CLEAR, WHITE_FOG, VOLCANIC_DAWN, new Color());

        assertNotEquals("dawn should move the fog a little", white, dawn);
        assertEquals("but only a little", white.g, dawn.g, 0.02f);
    }

    @Test
    public void blizzardStillTurnsTheForestGreyWhite() {
        Color blizzardFog = new Color(0.96f, 0.97f, 1.0f, 1f);
        Color fog = ForestAtmosphere.fogColor(WeatherType.BLIZZARD, blizzardFog, VOLCANIC_DAWN, new Color());

        assertTrue("a blizzard must whiten the forest: " + fog, fog.r + fog.g + fog.b > 1.8f);
    }

    @Test
    public void tornadoKeepsItsSicklyGreen() {
        Color fog = ForestAtmosphere.fogColor(WeatherType.TORNADO, WHITE_FOG, VOLCANIC_DAWN, new Color());

        assertTrue("tornado fog is green-led: " + fog, fog.g > fog.r && fog.g > fog.b);
        assertTrue("tornado fog is brighter than the plain canopy: " + fog, fog.g > 0.15f);
    }

    @Test
    public void fogDistanceIsShortOnTrailsAndOpensInGlades() {
        assertEquals(ForestAtmosphere.TRAIL_FOG_DISTANCE, ForestAtmosphere.fogDistance(0f, 70f), 0.001f);
        assertEquals(ForestAtmosphere.GLADE_FOG_DISTANCE, ForestAtmosphere.fogDistance(1f, 70f), 0.001f);
        assertEquals(8f, ForestAtmosphere.TRAIL_FOG_DISTANCE, 0.001f);
        assertEquals(14f, ForestAtmosphere.GLADE_FOG_DISTANCE, 0.001f);
    }

    @Test
    public void weatherCanCloseTheFogFurtherButNeverOpenIt() {
        assertEquals(6.5f, ForestAtmosphere.fogDistance(1f, 6.5f), 0.001f);
        assertEquals(ForestAtmosphere.TRAIL_FOG_DISTANCE, ForestAtmosphere.fogDistance(0f, 45f), 0.001f);
    }

    @Test
    public void ambientUnderTheCanopyIsGreenTealNotRed() {
        Color ambient = ForestAtmosphere.ambientHue(VOLCANIC_DAWN, new Color());

        assertFalse("canopy ambient must not read red: " + ambient, redDominates(ambient));
        assertTrue(ambient.g >= ambient.b);
    }

    @Test
    public void canopyScaleDimsUnderTheTreesAndRestoresInAGlade() {
        assertEquals(0.25f, ForestAtmosphere.canopyScale(1f, 0.25f, 0f), 0.001f);
        assertEquals(1f, ForestAtmosphere.canopyScale(1f, 0.25f, 1f), 0.001f);
        assertEquals(0.625f, ForestAtmosphere.canopyScale(1f, 0.25f, 0.5f), 0.001f);
    }

    @Test
    public void gladeFactorSeparatesTrailFromClearing() {
        // 9x9 of trees with a 1-wide trail along x = 4 and a 5x5 clearing in the corner.
        String[] rows = {
                "TTTTTTTTT",
                "T.....TTT",
                "T.....TTT",
                "T.....TTT",
                "T........",
                "T.....TTT",
                "TTTT.TTTT",
                "TTTT.TTTT",
                "TTTT.TTTT",
        };
        Maze maze = mazeOf(rows);

        float clearing = ForestAtmosphere.gladeFactor(maze, 3, 5);
        float trail = ForestAtmosphere.gladeFactor(maze, 4, 1);

        assertEquals("centre of a clearing is a glade", 1f, clearing, 0.001f);
        assertEquals("a one-wide trail is not", 0f, trail, 0.001f);
    }

    /** Rows are top-down as written; maze y=0 is the bottom row. '.' open, 'T' blocked. */
    private static Maze mazeOf(String[] rows) {
        int h = rows.length;
        int w = rows[0].length();
        boolean[][] open = new boolean[h][w];
        for (int r = 0; r < h; r++) {
            for (int x = 0; x < w; x++) {
                open[h - 1 - r][x] = rows[r].charAt(x) == '.';
            }
        }
        return new Maze(1, new int[h][w]) {
            @Override
            public boolean isPassable(int x, int y) {
                return x >= 0 && y >= 0 && x < w && y < h && open[y][x];
            }
        };
    }
}
