package com.bpm.minotaur.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.bpm.minotaur.rendering.SkyCaptureHarness;

/**
 * Launcher for the volcanic sky capture harness.
 *
 * <p>Renders the sky across a matrix of time-of-day, weather and heading states and writes a
 * contact sheet of PNGs, so palette changes are reviewable as a regression set rather than by
 * playing to the right hour in the right weather.
 */
public class SkyCaptureLauncher {

    public static void main(String[] args) {
        String outputDir = (args != null && args.length > 0) ? args[0] : "../docs/ux/screenshots/sky";
        new Lwjgl3Application(new SkyCaptureHarness(outputDir), getConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Tarmin 2 - Sky Capture");
        configuration.useVsync(false);
        configuration.setWindowedMode(1920, 880);
        return configuration;
    }
}
