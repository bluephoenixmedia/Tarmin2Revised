package com.bpm.minotaur.weather;

import com.bpm.minotaur.gamedata.liquid.LiquidManager;
import com.bpm.minotaur.gamedata.liquid.LiquidType;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.DayNightManager;
import com.bpm.minotaur.managers.WorldManager;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class DesertHazardTest {

    @Test
    public void desertTemperatureFollowsSolarElevation() throws Exception {
        DayNightManager dayNight = new DayNightManager();
        WorldManager world = createMockWorldManager(dayNight);
        WeatherManager weather = new WeatherManager(world);
        weather.setCurrentWeather(WeatherType.CLEAR);
        weather.setCurrentIntensity(WeatherIntensity.LIGHT);

        // 1. Midday (Solar Noon, 12:00 -> timeOfDay = 0.50f)
        dayNight.setTimeOfDay(0.50f);
        float middayTemp = weather.getAmbientTemperature(Biome.DESERT);
        // Base 42°C + 2°C (clear weather) = 44°C
        assertTrue("Midday desert temperature must produce heatstroke levels (>= 42°C), but was " + middayTemp,
                middayTemp >= 42.0f);

        // 2. Midnight (00:00 -> timeOfDay = 0.0f)
        dayNight.setTimeOfDay(0.0f);
        float midnightTemp = weather.getAmbientTemperature(Biome.DESERT);
        // Base 14°C + 2°C (clear weather) = 16°C
        assertTrue("Midnight desert temperature must drop to survivable chill (<= 17°C), but was " + midnightTemp,
                midnightTemp <= 17.0f);

        // 3. Dawn (06:00 -> timeOfDay = 0.25f)
        dayNight.setTimeOfDay(0.25f);
        float dawnTemp = weather.getAmbientTemperature(Biome.DESERT);
        // Base 24°C + 2°C (clear weather) = 26°C
        assertEquals(26.0f, dawnTemp, 0.5f);
    }

    @Test
    public void quicksandSlowsMovement() {
        LiquidManager lm = new LiquidManager();
        lm.setLiquidAt(5, 5, LiquidType.QUICKSAND);
        assertEquals(LiquidType.QUICKSAND, lm.getLiquidAt(5, 5));
        assertTrue("Quicksand must slow movement and cost a second turn tick",
                lm.slowsMovement(5, 5, null));
    }

    private WorldManager createMockWorldManager(DayNightManager dayNight) throws Exception {
        WorldManager wm = (WorldManager) allocateInstance(WorldManager.class);
        Field dnmField = WorldManager.class.getDeclaredField("dayNightManager");
        dnmField.setAccessible(true);
        dnmField.set(wm, dayNight);
        return wm;
    }

    private Object allocateInstance(Class<?> clazz) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return unsafe.allocateInstance(clazz);
    }
}
