package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;

public class CorpsePart {
    public Vector3 position;
    public Vector3 velocity;
    public String[] spriteQuadrant; // The 12x12 char grid
    public float rotation;
    public float rotationalVelocity;
    public boolean onGround;
    public Color color;

    // Dimensions for rendering
    public float width = 0.5f;
    public float height = 0.5f;

    public CorpsePart(Vector3 pos, Vector3 vel, String[] data, Color color) {
        this.position = new Vector3(pos);
        this.velocity = new Vector3(vel);
        this.spriteQuadrant = data;
        this.rotation = 0;
        this.color = color;
        // Random spin between -180 and 180 degrees per second
        this.rotationalVelocity = (float)(Math.random() * 360 - 180);
        this.onGround = false;
    }

    public void update(float delta) {
        if (!onGround) {
            // Gravity
            velocity.y -= 15.0f * delta;

            // Apply Velocity
            position.mulAdd(velocity, delta);
            rotation += rotationalVelocity * delta;

            // Floor Collision (Assuming y=0 is floor)
            // We stop slightly above 0 (0.1f) to prevent Z-fighting with the floor plane
            if (position.y <= 0.1f) {
                position.y = 0.1f;
                velocity.setZero();
                rotationalVelocity = 0;
                onGround = true;
            }
        }
    }
}
