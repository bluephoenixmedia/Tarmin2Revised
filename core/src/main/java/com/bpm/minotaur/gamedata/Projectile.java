package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Projectile implements Renderable {
    private final Vector2 position;
    private final Vector2 velocity;
    private final Color color;
    private float lifetime;

    public Projectile(Vector2 startPosition, Vector2 velocity, Color color, float lifetime) {
        this.position = startPosition.cpy();
        this.velocity = velocity.cpy();
        this.color = color;
        this.lifetime = lifetime;
    }

    public void update(float delta) {
        position.add(velocity.x * delta, velocity.y * delta);
        lifetime -= delta;
    }

    public boolean isAlive() {
        return lifetime > 0;
    }

    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public Color getColor() {
        return color;
    }
}
