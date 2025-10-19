package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Projectile implements Renderable {
    private final Vector2 position;
    private final Vector2 velocity;
    private final Color color;
    private float lifetime;
    private final String[] spriteData; // ADD THIS


    public Projectile(Vector2 startPosition, Vector2 velocity, Color color, float lifetime, String[] spriteData) {
        this.position = startPosition.cpy();
        this.velocity = velocity.cpy();
        this.color = color;
        this.lifetime = lifetime;
        this.spriteData = spriteData; // ADD THIS

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

    public String[] getSpriteData() {
        return spriteData;
    }
}
