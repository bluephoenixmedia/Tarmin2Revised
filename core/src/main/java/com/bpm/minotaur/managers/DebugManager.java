package com.bpm.minotaur.managers;

/**
 * Singleton manager for handling global debug state.
 */
public class DebugManager {
    private static final DebugManager INSTANCE = new DebugManager();
    private boolean isDebugOverlayVisible = false;

    private DebugManager() {}

    public static DebugManager getInstance() {
        return INSTANCE;
    }

    public boolean isDebugOverlayVisible() {
        return isDebugOverlayVisible;
    }

    public void toggleOverlay() {
        isDebugOverlayVisible = !isDebugOverlayVisible;
    }
}

