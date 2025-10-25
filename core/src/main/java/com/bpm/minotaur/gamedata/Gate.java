package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;

public class Gate implements Renderable {
    private final Vector2 position;

    // --- NEW FIELDS for Chunk Transitioning ---
    private GridPoint2 targetChunkId;
    private GridPoint2 targetPlayerPos;

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
}
