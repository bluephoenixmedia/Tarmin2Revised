package com.bpm.minotaur.paperdoll.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * A lightweight Verlet integration solver for cosmetic chains (flails).
 */
public class VerletPhysics {

    private final Vector2 gravity = new Vector2(0, -1500f); // Gravity in pixels/sec^2
    private final float friction = 0.95f;

    private Array<VerletLink> links;
    private float segmentLength = 15f;
    private int constraintIterations = 10;

    // Accumulator for fixed time step
    private float accumulator = 0;
    private final float TIME_STEP = 1 / 60f;

    public VerletPhysics() {
        this.links = new Array<>();
    }

    public void addLink(VerletLink link) {
        links.add(link);
    }

    public Array<VerletLink> getLinks() {
        return links;
    }

    public void createChain(int count, float startX, float startY) {
        links.clear();
        for (int i = 0; i < count; i++) {
            VerletLink link = new VerletLink(startX, startY - (i * segmentLength));
            if (i == 0)
                link.pinned = true;
            links.add(link);
        }
    }

    /**
     * Updates the pinned position of the first link (the handle).
     */
    public void updatePin(float x, float y) {
        if (links.size > 0) {
            VerletLink handle = links.first();
            handle.setPosition(x, y);
            // If we want it to carry velocity, we don't snap prevPosition completely
            // But for a pinned handle, it strictly follows the socket.
        }
    }

    public void step(float deltaTime) {
        accumulator += deltaTime;
        while (accumulator >= TIME_STEP) {
            integrate(TIME_STEP);
            accumulator -= TIME_STEP;
        }
        // Can implement interpolation here if needed for smoothness
    }

    private void integrate(float dt) {
        // 1. Verlet Integration
        for (VerletLink link : links) {
            if (link.pinned)
                continue;

            // Velocity = (pos - prevPos)
            float velX = (link.position.x - link.prevPosition.x) * friction;
            float velY = (link.position.y - link.prevPosition.y) * friction;

            // Save current pos
            link.prevPosition.set(link.position);

            // Apply Gravity
            velY += gravity.y * dt * dt;

            // Update
            link.position.add(velX, velY);
        }

        // 2. Constraints (Relaxation)
        for (int i = 0; i < constraintIterations; i++) {
            applyConstraints();
        }
    }

    private void applyConstraints() {
        for (int i = 0; i < links.size - 1; i++) {
            VerletLink l1 = links.get(i);
            VerletLink l2 = links.get(i + 1);

            float dist = l1.position.dst(l2.position);
            float diff = (dist - segmentLength) / dist;

            // Prevent division by zero
            if (dist == 0)
                diff = 0;

            float offsetX = (l1.position.x - l2.position.x) * 0.5f * diff;
            float offsetY = (l1.position.y - l2.position.y) * 0.5f * diff;

            if (!l1.pinned) {
                l1.position.sub(offsetX, offsetY);
            }
            if (!l2.pinned) {
                l2.position.add(offsetX, offsetY);
            }
        }
    }
}
