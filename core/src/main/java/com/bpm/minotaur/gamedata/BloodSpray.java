package com.bpm.minotaur.gamedata;

import com.badlogic.gdx.math.Vector3;

public class BloodSpray {
    public Vector3 position;
    public float lifetime;
    public float maxLifetime;

    public BloodSpray(Vector3 pos) {
        this.position = new Vector3(pos);
        this.maxLifetime = 0.8f; // Lasts nearly 1 second
        this.lifetime = maxLifetime;
    }

    public void update(float delta) {
        lifetime -= delta;
    }
}
