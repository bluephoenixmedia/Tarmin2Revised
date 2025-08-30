package com.bpm.minotaur.managers;

/**
 * A singleton manager for handling global debug flags and states.
 * This allows easy access to debug information from any part of the application.
 */
public class DebugManager {
    /** The single, globally accessible instance of the DebugManager. */
    public static final DebugManager INSTANCE = new DebugManager();

    public boolean isDebugOverlayVisible = false;

    // Private constructor to prevent instantiation from outside.
    private DebugManager() { }

    /**
     * Toggles the visibility of the debug overlay.
     */
    public void toggleOverlay() {
        isDebugOverlayVisible = !isDebugOverlayVisible;
    }
}
