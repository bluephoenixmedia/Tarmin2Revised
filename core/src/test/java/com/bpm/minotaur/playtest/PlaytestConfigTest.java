package com.bpm.minotaur.playtest;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaytestConfigTest {

    @Test
    public void testDefaultConfig() {
        PlaytestConfig config = PlaytestConfig.parse(new String[]{"--playtest"});
        assertTrue(config.isEnabled());
        assertEquals("all", config.getScenario());
        assertFalse(config.isHeadless());
        assertEquals(500, config.getTurns());
        assertEquals("checkpoints", config.getScreenshotMode());
    }

    @Test
    public void testScenarioParsing() {
        PlaytestConfig config = PlaytestConfig.parse(new String[]{"--playtest=towns", "--headless"});
        assertTrue(config.isEnabled());
        assertEquals("towns", config.getScenario());
        assertTrue(config.isHeadless());
    }

    @Test
    public void testCustomTurnsAndSeed() {
        PlaytestConfig config = PlaytestConfig.parse(new String[]{
            "--playtest=explore",
            "--turns=1200",
            "--seed=42",
            "--turbo=20",
            "--screenshots=all"
        });
        assertTrue(config.isEnabled());
        assertEquals("explore", config.getScenario());
        assertEquals(1200, config.getTurns());
        assertNotNull(config.getSeed());
        assertEquals(42L, config.getSeed().longValue());
        assertEquals(20, config.getTurbo());
        assertEquals("all", config.getScreenshotMode());
    }

    @Test
    public void testDisabledByDefault() {
        PlaytestConfig config = PlaytestConfig.parse(new String[]{"--some-other-flag"});
        assertFalse(config.isEnabled());
    }
}
