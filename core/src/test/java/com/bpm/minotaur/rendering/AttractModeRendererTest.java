package com.bpm.minotaur.rendering;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.bpm.minotaur.rendering.attract.AttractModeRenderer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class AttractModeRendererTest {

    @BeforeClass
    public static void initNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void testAttractModeRendererSubsystemsInitialized() {
        AttractModeRenderer renderer = new AttractModeRenderer();

        assertNotNull("WarDirector must be initialized", renderer.getWarDirector());
        assertNotNull("ProjectileManager must be initialized", renderer.getProjectileManager());
        assertNotNull("AtmosphereManager must be initialized", renderer.getAtmosphereManager());
        assertNotNull("DayNightManager must be initialized", renderer.getDayNightManager());
        assertNotNull("Controller must be initialized", renderer.getController());
        assertNotNull("Camera must be initialized", renderer.getCamera());

        // Update step
        renderer.update(0.1f);
        assertTrue("Camera position must be populated", renderer.getCamera().position.len2() > 0f);

        // Dive transition test
        final AtomicBoolean diveComplete = new AtomicBoolean(false);
        renderer.startExpeditionDive(() -> diveComplete.set(true));
        assertTrue("Controller should be diving", renderer.getController().isDiving());

        // Update past dive duration (0.8s)
        renderer.update(0.9f);
        assertTrue("Dive callback must have fired", diveComplete.get());

        // Clean disposal test
        renderer.dispose();
    }
}
