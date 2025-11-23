package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class BloodParticle implements Pool.Poolable {
    public Vector3 position = new Vector3();
    public Vector3 velocity = new Vector3();
    public Color color = new Color();
    public float lifeTimer;
    public float maxLife;
    public float size;
    public boolean onGround;

    public BloodParticle() {
        // Empty for pooling
    }

    public void init(Vector3 startPos, Vector3 startVel, Color color, float life, float size) {
        this.position.set(startPos);
        this.velocity.set(startVel);
        this.color.set(color);
        this.maxLife = life;
        this.lifeTimer = life;
        this.size = size;
        this.onGround = false;
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        lifeTimer = 0;
        onGround = false;
        color.set(Color.WHITE);
    }

    public void update(float delta) {
        if (onGround) return;

        // Gravity (Heavy for visceral feel)
        velocity.y -= 18.0f * delta;

        // Move
        position.mulAdd(velocity, delta);

        // Floor Collision (y=0 is floor)
        // We stop slightly above 0 to prevent Z-fighting
        if (position.y <= 0.02f) {
            position.y = 0.02f;
            velocity.setZero();
            onGround = true;
        }

        lifeTimer -= delta;
    }
}
