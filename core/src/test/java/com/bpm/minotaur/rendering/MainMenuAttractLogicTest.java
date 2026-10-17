package com.bpm.minotaur.rendering;

import com.bpm.minotaur.rendering.attract.AttractController;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class MainMenuAttractLogicTest {

    private AttractController controller;

    @Before
    public void setUp() {
        controller = new AttractController();
    }

    @Test
    public void testInitialState() {
        assertEquals("UI should start at full opacity", 1.0f, controller.getUiAlpha(), 0.001f);
        assertFalse("Should not start in attract mode", controller.isAttractModeActive());
        assertFalse("Should not start in dive transition", controller.isDiving());
    }

    @Test
    public void testInactivityTriggersAttractMode() {
        // Less than 15s of inactivity keeps UI at full opacity
        controller.update(10.0f);
        assertEquals(1.0f, controller.getUiAlpha(), 0.001f);
        assertFalse(controller.isAttractModeActive());

        // Crossing 15s triggers attract mode and begins fading out UI
        controller.update(5.1f); // Total: 15.1s
        assertTrue("Attract mode should activate after 15s", controller.isAttractModeActive());
        assertTrue("UI alpha should start fading below 1.0", controller.getUiAlpha() < 1.0f);

        // Advancing further fades UI completely to 0
        controller.update(2.0f);
        assertEquals("UI should fully fade out to 0", 0.0f, controller.getUiAlpha(), 0.001f);
    }

    @Test
    public void testInputResetsAttractMode() {
        // Trigger attract mode
        controller.update(20.0f);
        assertEquals(0.0f, controller.getUiAlpha(), 0.001f);
        assertTrue(controller.isAttractModeActive());

        // Any input event immediately wakes UI
        controller.notifyInputReceived();
        assertFalse("Attract mode should deactivate immediately on input", controller.isAttractModeActive());

        // Updating brings UI alpha back to 1.0 quickly
        controller.update(0.5f);
        assertEquals(1.0f, controller.getUiAlpha(), 0.001f);
    }

    @Test
    public void testExpeditionDiveSequence() {
        final AtomicBoolean launched = new AtomicBoolean(false);

        controller.startDive(() -> launched.set(true));
        assertTrue("Dive should be in progress", controller.isDiving());
        assertFalse("Should not finish immediately", launched.get());

        // Update partially (0.4s)
        controller.update(0.4f);
        assertTrue("Dive still in progress at 0.4s", controller.isDiving());
        assertFalse(launched.get());

        // Update past dive duration (0.8s total)
        controller.update(0.5f);
        assertFalse("Dive should conclude after 0.8s", controller.isDiving());
        assertTrue("Launch callback must be invoked", launched.get());
    }
}
