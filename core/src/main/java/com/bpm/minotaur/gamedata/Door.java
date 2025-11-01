package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;

import java.util.Map;

public class Door {
    public enum DoorState { CLOSED, OPENING, OPEN }

    private DoorState state = DoorState.CLOSED;
    private float animationProgress = 0f; // 0.0 = closed, 1.0 = open
    private static final float ANIMATION_SPEED = 2.0f; // Opens in 0.5 seconds

    private Maze maze; // <-- ADD THIS

    public DoorState getState() {
        return state;
    }

    public void close() {
        state = DoorState.CLOSED;
        animationProgress = 0f;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    public void startOpening() {
        if (state == DoorState.CLOSED) {
            state = DoorState.OPENING;
        }
    }

    public void update(float delta) {
        if (state == DoorState.OPENING) {
            animationProgress += delta * ANIMATION_SPEED;
            if (animationProgress >= 1.0f) {
                animationProgress = 1.0f;
                state = DoorState.OPEN;
            }
        }
    }

    /**
     * Used by ChunkData to restore a door's state from a save file.
     */
    public void setState(DoorState state, float animationProgress) {
        this.state = state;
        this.animationProgress = animationProgress;
    }

    // --- ADD THIS METHOD ---
    public void setMaze(Maze maze) {
        this.maze = maze;
    }

    // --- ADD THIS METHOD ---
    public Maze getMaze() {
        return maze;
    }

    // --- ADD THIS METHOD ---
    /**
     * Helper method for ChunkData to find its own grid position when saving.
     */
    public GridPoint2 findPositionIn(Map<GridPoint2, Object> gameObjects) {
        for (Map.Entry<GridPoint2, Object> entry : gameObjects.entrySet()) {
            if (entry.getValue() == this) {
                return entry.getKey();
            }
        }
        return null; // Should not happen if door is in the map
    }
}
