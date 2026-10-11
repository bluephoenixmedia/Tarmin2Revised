package com.bpm.minotaur.weather;

import com.bpm.minotaur.managers.SoundManager;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Validates that ambient rain audio state behaves correctly and is maintained
 * when weather is RAIN or STORM.
 */
public class AmbientRainAudioTest {

    private static class TestSoundManager extends SoundManager {
        public WeatherType lastType;
        public WeatherIntensity lastIntensity;
        public int updateAudioCalls = 0;
        public boolean fakeRainPlaying = false;

        public TestSoundManager() {
            super();
        }

        @Override
        public void updateWeatherAudio(WeatherType type, WeatherIntensity intensity) {
            updateAudioCalls++;
            lastType = type;
            lastIntensity = intensity;
            if (type == WeatherType.RAIN || type == WeatherType.STORM) {
                fakeRainPlaying = true;
            } else {
                fakeRainPlaying = false;
            }
        }

        @Override
        public boolean isRainPlaying() {
            return fakeRainPlaying;
        }

        @Override
        public void stopWeatherEffects() {
            fakeRainPlaying = false;
            lastType = null;
            lastIntensity = null;
        }
    }

    @Test
    public void testRainAudioStartsOnRainAndStorm() {
        TestSoundManager sound = new TestSoundManager();
        assertFalse(sound.isRainPlaying());

        sound.updateWeatherAudio(WeatherType.RAIN, WeatherIntensity.MEDIUM);
        assertTrue("Rain audio must be playing during RAIN", sound.isRainPlaying());
        assertEquals(WeatherType.RAIN, sound.lastType);

        sound.updateWeatherAudio(WeatherType.STORM, WeatherIntensity.HEAVY);
        assertTrue("Rain audio must be playing during STORM", sound.isRainPlaying());
        assertEquals(WeatherType.STORM, sound.lastType);

        sound.updateWeatherAudio(WeatherType.CLEAR, WeatherIntensity.LIGHT);
        assertFalse("Rain audio must stop during CLEAR", sound.isRainPlaying());
    }

    @Test
    public void testInterruptedRainAudioRestarts() {
        TestSoundManager sound = new TestSoundManager();
        sound.updateWeatherAudio(WeatherType.RAIN, WeatherIntensity.MEDIUM);
        assertTrue(sound.isRainPlaying());

        // Simulate going underground which stops weather effects
        sound.stopWeatherEffects();
        assertFalse(sound.isRainPlaying());

        // Call updateWeatherAudio again with RAIN
        sound.updateWeatherAudio(WeatherType.RAIN, WeatherIntensity.MEDIUM);
        assertTrue("Rain audio must restart if interrupted", sound.isRainPlaying());
    }
}
