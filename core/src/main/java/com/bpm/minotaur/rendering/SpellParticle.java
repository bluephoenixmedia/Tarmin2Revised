package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class SpellParticle implements Pool.Poolable {
    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public final Color color = new Color(Color.WHITE);
    public float lifeTimer;
    public float maxLife;
    public float size;

    public SpellParticle() {
    }

    public void init(Vector3 position, Vector3 velocity, Color color, float life, float size) {
        this.position.set(position);
        this.velocity.set(velocity);
        this.color.set(color);
        this.lifeTimer = life;
        this.maxLife = life;
        this.size = size;
    }

    public void update(float delta) {
        position.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);
        lifeTimer -= delta;
        // Fade out alpha
        color.a = Math.max(0, lifeTimer / maxLife);
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        color.set(Color.WHITE);
        lifeTimer = 0;
        maxLife = 0;
        size = 0;
    }
}
