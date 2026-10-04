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
    /** Fine spray that hangs and fades in the air: never lands as a decal. */
    public boolean mist;

    public com.badlogic.gdx.graphics.g2d.TextureRegion textureRegion; // For Modern Mode

    public BloodParticle() {
        // Empty for pooling
    }

    public void init(Vector3 startPos, Vector3 startVel, Color color, float life, float size,
            com.badlogic.gdx.graphics.g2d.TextureRegion texture) {
        this.position.set(startPos);
        this.velocity.set(startVel);
        this.color.set(color);
        this.maxLife = life;
        this.lifeTimer = life;
        this.size = size;
        this.onGround = false;
        this.mist = false;
        this.textureRegion = texture;
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        lifeTimer = 0;
        onGround = false;
        mist = false;
        color.set(Color.WHITE);
    }

    public void update(float delta) {
        if (onGround)
            return;

        if (mist) {
            // Hangs: light gravity, heavy drag, fading as it disperses.
            velocity.y -= 4.0f * delta;
            velocity.scl(Math.max(0f, 1f - 3.5f * delta));
            color.a = 0.7f * Math.max(0f, lifeTimer / Math.max(0.001f, maxLife));
        } else {
            // Gravity (Heavy for visceral feel)
            velocity.y -= 18.0f * delta;
        }

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
