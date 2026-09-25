package com.bpm.minotaur.managers;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Travelling through a biome portal must not change which dimension the player
 * is in.
 *
 * <p>RETRO rendering and the raycaster engine are the Ancient Void's identity,
 * not a generic warp effect. Reusing {@code triggerDimensionalWarp} for ordinary
 * travel dropped the player into ASCII rendering in the mortal realm.
 */
public class TravelWarpTest {

    private DebugManager debug;

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null);
        }
        debug = DebugManager.getInstance();
        debug.setRenderModeDirect(DebugManager.RenderMode.MODERN);
        debug.setRenderEngine(DebugManager.RenderEngine.PLANAR_3D);
    }

    @Test
    public void aTravelWarpKeepsTheCurrentRenderMode() {
        debug.triggerTravelWarp();

        // Drive the transition to completion.
        for (int i = 0; i < 200; i++) {
            debug.update(0.05f);
        }

        assertEquals("Travelling must not switch the player to retro rendering",
                DebugManager.RenderMode.MODERN, debug.getRenderMode());
        assertEquals("Travelling must not switch the render engine",
                DebugManager.RenderEngine.PLANAR_3D, debug.getRenderEngine());
    }

    @Test
    public void aDimensionalWarpStillSwapsIntoTheVoid() {
        // The Void's own portal must keep its behaviour.
        debug.triggerDimensionalWarp(true);
        for (int i = 0; i < 200; i++) {
            debug.update(0.05f);
        }

        assertEquals("Entering the Void must switch to retro rendering",
                DebugManager.RenderMode.RETRO, debug.getRenderMode());
        assertEquals("Entering the Void must switch to the raycaster",
                DebugManager.RenderEngine.RAYCASTER, debug.getRenderEngine());
    }
}
