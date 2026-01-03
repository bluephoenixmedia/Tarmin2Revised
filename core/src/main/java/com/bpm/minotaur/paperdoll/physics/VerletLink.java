package com.bpm.minotaur.paperdoll.physics;

import com.badlogic.gdx.math.Vector2;

/**
 * A single particle link in the Verlet chain.
 */
public class VerletLink {
    public Vector2 position;
    public Vector2 prevPosition;
    public boolean pinned; // If true, this link is fixed to a position (e.g. hand)

    public VerletLink(float x, float y) {
        this.position = new Vector2(x, y);
        this.prevPosition = new Vector2(x, y);
        this.pinned = false;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
        // Reset velocity? Usually not if we just want to move it.
        // But if we want to teleport it, we should update prevPosition too.
        // For pinning, we update position but keep prevPosition logic in solver.
    }
}
