package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class Gib implements Pool.Poolable {
    public Vector3 position = new Vector3();
    public Vector3 velocity = new Vector3();
    public String[] spriteData;
    public Color color = new Color();

    public float rotation;
    public float rotationalVelocity;
    public boolean onGround;
    public float lifeTimer;

    // Gibs stay for a while, then disappear or persist (we'll fade them after 20s)
    public static final float MAX_GIB_LIFE = 20.0f;

    public Gib() {}

    public void init(Vector3 pos, Vector3 vel, GibType type, Color overrideColor) {
        this.position.set(pos);
        this.velocity.set(vel);
        this.spriteData = type.spriteData;
        // Use monster color for meat, but default colors for bones/eyes
        if (type == GibType.MEAT_CHUNK && overrideColor != null) {
            this.color.set(overrideColor);
        } else {
            this.color.set(type.defaultColor);
        }

        this.rotation = MathUtils.random(0, 360);
        this.rotationalVelocity = MathUtils.random(-300, 300);
        this.onGround = false;
        this.lifeTimer = MAX_GIB_LIFE;
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        onGround = false;
        lifeTimer = 0;
        rotation = 0;
    }

    public void update(float delta) {
        if (onGround) return;

        // Gravity
        velocity.y -= 22.0f * delta; // Fall fast

        position.mulAdd(velocity, delta);
        rotation += rotationalVelocity * delta;

        // Floor Bounce Logic
        if (position.y <= 0.1f) {
            // Bounce!
            if (Math.abs(velocity.y) > 2.0f) {
                position.y = 0.1f;
                velocity.y = -velocity.y * 0.4f; // Lose 60% energy
                velocity.x *= 0.6f; // Friction
                velocity.z *= 0.6f;
                rotationalVelocity *= 0.5f;
            } else {
                // Stop
                position.y = 0.1f;
                velocity.setZero();
                rotationalVelocity = 0;
                onGround = true;
                rotation = 0; // Lie flat
            }
        }

        lifeTimer -= delta;
    }
}
