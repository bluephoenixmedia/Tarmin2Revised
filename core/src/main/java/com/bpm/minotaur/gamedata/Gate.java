package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.bpm.minotaur.managers.WorldManager;

public class Gate implements Renderable {
    private final Vector2 position;

    private GridPoint2 targetChunkId;
    private GridPoint2 targetPlayerPos;

    // --- NEW FIELDS (Copied from Door.java) ---
    public enum GateState {
        CLOSED,
        OPENING,
        OPEN
    }

    private GateState state = GateState.CLOSED;
    private float animationProgress = 0.0f;
    private static final float ANIMATION_TIME = 1.0f; // 1 second to open

    /**
     * Original constructor for stat-jumbling gates (CLASSIC mode).
     * @param x coordinate
     * @param y coordinate
     */
    public Gate(int x, int y) {
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.targetChunkId = null; // No transition
        this.targetPlayerPos = null;
    }



    /**
     * NEW constructor for chunk-transitioning gates (ADVANCED mode).
     * @param x coordinate
     * @param y coordinate
     * @param targetChunkId The (X,Y) ID of the chunk this gate leads to.
     * @param targetPlayerPos The (X,Y) tile the player will appear on in the new chunk.
     */
    public Gate(int x, int y, GridPoint2 targetChunkId, GridPoint2 targetPlayerPos) {
        this.position = new Vector2(x + 0.5f, y + 0.5f);
        this.targetChunkId = targetChunkId;
        this.targetPlayerPos = targetPlayerPos;
    }

    // --- ADD THIS METHOD ---
    /**
     * Used by ChunkData to restore a gate's state from a save file.
     */
    public void setState(GateState state, float animationProgress) {
        this.state = state;
        this.animationProgress = animationProgress;
    }



    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public com.badlogic.gdx.graphics.Color getColor() {
        // Gates are rendered as a special wall type in FirstPersonRenderer
        // This color is a fallback.
        return com.badlogic.gdx.graphics.Color.CYAN;
    }

    /**
     * Resets the gate to its closed state.
     * Used when a transition fails (e.g., hitting an ocean).
     */
    public void close() {
        state = GateState.CLOSED;
        animationProgress = 0.0f;
    }

    // --- NEW Getters ---

    public boolean isChunkTransitionGate() {
        return targetChunkId != null;
    }

    public GridPoint2 getTargetChunkId() {
        return targetChunkId;
    }

    public GridPoint2 getTargetPlayerPos() {
        return targetPlayerPos;
    }

    public GateState getState() {
        return state;
    }

    public void startOpening(WorldManager worldManager) {
        if (state == GateState.CLOSED) {
            state = GateState.OPENING;
            animationProgress = 0.0f;
            if (isChunkTransitionGate() && worldManager != null) {
                worldManager.loadChunk(this.getTargetChunkId());
            }
        }
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    public void update(float delta) {
        if (state == GateState.OPENING) {
            animationProgress += delta / ANIMATION_TIME;
            if (animationProgress >= 1.0f) {
                animationProgress = 1.0f;
                state = GateState.OPEN;
            }
        }
    }
}
