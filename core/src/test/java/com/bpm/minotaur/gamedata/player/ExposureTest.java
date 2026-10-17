package com.bpm.minotaur.gamedata.player;

import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.generation.Stratum;
import com.bpm.minotaur.weather.WeatherIntensity;
import com.bpm.minotaur.weather.WeatherType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Frostfall-style Exposure (#194, decided in #181): bands, ambient air, wetness, clothing and
 * heat, and the pacing target -- temperate and dry never matters, cold or wet needs a cloak
 * or a fire within about 300 turns.
 */
public class ExposureTest {

    private static final float NOON = 0.5f;
    private static final float MIDNIGHT = 0f;
    private static final float CLOAK = 8f;

    /** Runs {@code turns} turns in fixed conditions and returns the body temperature. */
    private static float after(int turns, float ambient, float warmth, Exposure.Wetness wet, Exposure.Heat heat) {
        float t = Exposure.NORMAL;
        for (int i = 0; i < turns; i++) t = Exposure.step(t, ambient, warmth, wet, heat, 1f);
        return t;
    }

    /** First turn the body reaches {@code band} or colder, or -1 within the cap. */
    private static int turnsUntil(Exposure.Band band, float ambient, float warmth, Exposure.Wetness wet) {
        float t = Exposure.NORMAL;
        for (int i = 1; i <= 5000; i++) {
            t = Exposure.step(t, ambient, warmth, wet, Exposure.Heat.NONE, 1f);
            if (Exposure.bandOf(t).ordinal() >= band.ordinal()) return i;
        }
        return -1;
    }

    // --- Bands ---------------------------------------------------------------------------

    @Test
    public void bandsRunFromWarmToHypothermic() {
        assertEquals(Exposure.Band.HOT, Exposure.bandOf(39f));
        assertEquals(Exposure.Band.WARM, Exposure.bandOf(37.5f));
        assertEquals(Exposure.Band.COMFORTABLE, Exposure.bandOf(37f));
        assertEquals(Exposure.Band.CHILLY, Exposure.bandOf(35.5f));
        assertEquals(Exposure.Band.COLD, Exposure.bandOf(34.5f));
        assertEquals(Exposure.Band.FREEZING, Exposure.bandOf(33f));
        assertEquals(Exposure.Band.HYPOTHERMIC, Exposure.bandOf(32f));
    }

    @Test
    public void coldSlowsAndFreezingStopsRegen() {
        assertEquals(0f, Exposure.Band.CHILLY.speedPenalty, 0.001f);
        assertFalse(Exposure.Band.CHILLY.blocksRegen);
        assertEquals(0.10f, Exposure.Band.COLD.speedPenalty, 0.001f);
        assertFalse(Exposure.Band.COLD.blocksRegen);
        assertEquals(0.20f, Exposure.Band.FREEZING.speedPenalty, 0.001f);
        assertTrue(Exposure.Band.FREEZING.blocksRegen);
        assertTrue(Exposure.Band.HYPOTHERMIC.blocksRegen);
    }

    // --- Ambient -------------------------------------------------------------------------

    @Test
    public void everySurfaceBiomeIsColderAtNight() {
        for (Biome b : new Biome[]{Biome.FOREST, Biome.LAKELANDS, Biome.TUNDRA, Biome.MOUNTAINS, Biome.BLIGHT, Biome.DESERT}) {
            float day = Exposure.surfaceAmbient(b, WeatherType.CLEAR, WeatherIntensity.LIGHT, NOON);
            float night = Exposure.surfaceAmbient(b, WeatherType.CLEAR, WeatherIntensity.LIGHT, MIDNIGHT);
            assertTrue(b + " day " + day + " night " + night, night < day);
        }
    }

    @Test
    public void rainAndStormsAreColderAndMountainsAreColdByDay() {
        float clear = Exposure.surfaceAmbient(Biome.FOREST, WeatherType.CLEAR, WeatherIntensity.LIGHT, NOON);
        float rain = Exposure.surfaceAmbient(Biome.FOREST, WeatherType.RAIN, WeatherIntensity.LIGHT, NOON);
        float storm = Exposure.surfaceAmbient(Biome.FOREST, WeatherType.STORM, WeatherIntensity.LIGHT, NOON);
        assertTrue(rain < clear);
        assertTrue(storm < rain);
        assertTrue(Exposure.surfaceAmbient(Biome.MOUNTAINS, WeatherType.CLEAR, WeatherIntensity.LIGHT, NOON)
                < Exposure.COMFORT_FLOOR - Exposure.RAGS_WARMTH);
    }

    @Test
    public void eachStratumHasItsOwnAir() {
        float maze = Exposure.strataAmbient(Stratum.MAZE);
        assertTrue(Exposure.strataAmbient(Stratum.FLOODED_HALLS) < Exposure.strataAmbient(Stratum.OSSUARY));
        assertTrue(Exposure.strataAmbient(Stratum.OSSUARY) < maze);
        assertTrue(Exposure.strataAmbient(Stratum.MAGMA_DEEPS) > Exposure.HEAT_STRESS_ABOVE);
        assertTrue(Exposure.strataAmbient(Stratum.FUNGAL_FOREST) + Exposure.RAGS_WARMTH >= Exposure.COMFORT_FLOOR);
        assertTrue(maze + Exposure.RAGS_WARMTH >= Exposure.COMFORT_FLOOR);
    }

    // --- Pacing (#194 item 7) ------------------------------------------------------------

    @Test
    public void temperateAndDryColdNeverComesUp() {
        for (float tod : new float[]{NOON, MIDNIGHT}) {
            for (WeatherType w : new WeatherType[]{WeatherType.CLEAR, WeatherType.FOG}) {
                float air = Exposure.surfaceAmbient(Biome.FOREST, w, WeatherIntensity.MEDIUM, tod);
                assertEquals(Exposure.Band.COMFORTABLE,
                        Exposure.bandOf(after(3000, air, Exposure.RAGS_WARMTH, Exposure.Wetness.DRY, Exposure.Heat.NONE)));
            }
        }
        assertEquals(Exposure.Band.COMFORTABLE, Exposure.bandOf(after(3000, Exposure.strataAmbient(Stratum.MAZE),
                Exposure.RAGS_WARMTH, Exposure.Wetness.DRY, Exposure.Heat.NONE)));
    }

    @Test
    public void coldOrWetNeedsACloakOrAFireWithinAboutThreeHundredTurns() {
        float tundra = Exposure.surfaceAmbient(Biome.TUNDRA, WeatherType.CLEAR, WeatherIntensity.LIGHT, NOON);
        int tundraCold = turnsUntil(Exposure.Band.COLD, tundra, Exposure.RAGS_WARMTH, Exposure.Wetness.DRY);
        assertTrue("tundra in rags turns cold, at " + tundraCold, tundraCold > 0 && tundraCold <= 300);

        float halls = Exposure.strataAmbient(Stratum.FLOODED_HALLS);
        int hallsCold = turnsUntil(Exposure.Band.COLD, halls, Exposure.RAGS_WARMTH, Exposure.Wetness.WET);
        assertTrue("wet in the Flooded Halls turns cold, at " + hallsCold, hallsCold > 100 && hallsCold <= 400);

        float rainyNight = Exposure.surfaceAmbient(Biome.FOREST, WeatherType.RAIN, WeatherIntensity.MEDIUM, MIDNIGHT);
        int forestWet = turnsUntil(Exposure.Band.COLD, rainyNight, Exposure.RAGS_WARMTH, Exposure.Wetness.WET);
        assertTrue("a wet night in the forest turns cold, at " + forestWet, forestWet > 100 && forestWet <= 400);

        // The cloak answers the wet forest night and the Flooded Halls outright.
        assertEquals(-1, turnsUntil(Exposure.Band.CHILLY, rainyNight, Exposure.RAGS_WARMTH + CLOAK, Exposure.Wetness.WET));
        assertEquals(-1, turnsUntil(Exposure.Band.CHILLY, halls, Exposure.RAGS_WARMTH + CLOAK, Exposure.Wetness.WET));
    }

    @Test
    public void wetLosesHeatTwiceAsFastAndSoakedThreeTimes() {
        float dry = Exposure.NORMAL - Exposure.step(Exposure.NORMAL, 0f, 0f, Exposure.Wetness.DRY, Exposure.Heat.NONE, 1f);
        float wet = Exposure.NORMAL - Exposure.step(Exposure.NORMAL, 0f, 0f, Exposure.Wetness.WET, Exposure.Heat.NONE, 1f);
        float soaked = Exposure.NORMAL - Exposure.step(Exposure.NORMAL, 0f, 0f, Exposure.Wetness.SOAKED, Exposure.Heat.NONE, 1f);
        assertEquals(2f * dry, wet, 1e-5f);
        assertEquals(3f * dry, soaked, 1e-5f);
    }

    @Test
    public void theBodyNeverFallsThroughTheFloor() {
        assertEquals(Exposure.BODY_FLOOR, after(20000, -40f, 0f, Exposure.Wetness.SOAKED, Exposure.Heat.NONE), 0.001f);
    }

    // --- Heat ----------------------------------------------------------------------------

    @Test
    public void aFireWarmsPastNormalIntoWarmEvenInTheTundra() {
        float t = 33f;
        for (int i = 0; i < 40; i++) t = Exposure.step(t, -20f, 0f, Exposure.Wetness.SOAKED, Exposure.Heat.FIELD_FIRE, 1f);
        assertEquals(Exposure.Band.WARM, Exposure.bandOf(t));
        float h = 33f;
        for (int i = 0; i < 15; i++) h = Exposure.step(h, -20f, 0f, Exposure.Wetness.SOAKED, Exposure.Heat.HEARTH, 1f);
        assertEquals(Exposure.Band.WARM, Exposure.bandOf(h));
    }

    @Test
    public void theLanternIsALittleWarmthAndMoltenFireIsHot() {
        float bare = after(200, 5f, 0f, Exposure.Wetness.DRY, Exposure.Heat.NONE);
        float lit = after(200, 5f, 0f, Exposure.Wetness.DRY, Exposure.Heat.LANTERN);
        assertTrue(lit > bare);
        assertTrue(lit < Exposure.NORMAL);
        assertEquals(Exposure.Band.HOT, Exposure.bandOf(after(60, 10f, 0f, Exposure.Wetness.DRY, Exposure.Heat.MOLTEN)));
    }

    // --- Wetness -------------------------------------------------------------------------

    @Test
    public void rainWetsButNeverSoaksAndCoverageKeepsItOut() {
        float bare = 0f;
        float cloaked = 0f;
        float rain = Exposure.rainPerTurn(WeatherType.RAIN);
        for (int i = 0; i < 400; i++) {
            bare = Exposure.wetnessAfter(bare, rain, 0f, Exposure.Heat.NONE, 1f);
            cloaked = Exposure.wetnessAfter(cloaked, rain, 0.75f, Exposure.Heat.NONE, 1f);
        }
        assertEquals(Exposure.Wetness.WET, Exposure.wetnessOf(bare));
        assertEquals(Exposure.Wetness.DRY, Exposure.wetnessOf(cloaked));
    }

    @Test
    public void youDryOverTimeAndQuicklyBesideAFire() {
        float open = Exposure.afterSoaking();
        float fire = Exposure.afterSoaking();
        for (int i = 0; i < 30; i++) {
            open = Exposure.wetnessAfter(open, 0f, 0f, Exposure.Heat.NONE, 1f);
            fire = Exposure.wetnessAfter(fire, 0f, 0f, Exposure.Heat.FIELD_FIRE, 1f);
        }
        assertEquals(Exposure.Wetness.SOAKED, Exposure.wetnessOf(open));
        assertEquals(Exposure.Wetness.DRY, Exposure.wetnessOf(fire));
        float later = Exposure.afterSoaking();
        for (int i = 0; i < 600; i++) later = Exposure.wetnessAfter(later, 0f, 0f, Exposure.Heat.NONE, 1f);
        assertEquals(Exposure.Wetness.DRY, Exposure.wetnessOf(later));
    }

    @Test
    public void wadingWetsAndFreezingWaterSoaks() {
        assertEquals(Exposure.Wetness.WET, Exposure.wetnessOf(Exposure.afterWading(0f)));
        assertEquals(Exposure.Wetness.SOAKED, Exposure.wetnessOf(Exposure.afterWading(Exposure.afterSoaking())));
        assertTrue(Exposure.isFreezingWater(LiquidType.FREEZING_SLUSH, Biome.FOREST, false, 20f));
        assertTrue(Exposure.isFreezingWater(LiquidType.WATER, Biome.TUNDRA, true, -15f));
        assertTrue(Exposure.isFreezingWater(LiquidType.WATER, Biome.MOUNTAINS, true, 2f));
        assertFalse(Exposure.isFreezingWater(LiquidType.WATER, Biome.MOUNTAINS, false, 6f));
        assertFalse(Exposure.isFreezingWater(LiquidType.WATER, Biome.FOREST, true, 14f));
        assertFalse(Exposure.isFreezingWater(LiquidType.PACKED_ICE, Biome.TUNDRA, true, -20f));
    }
}
