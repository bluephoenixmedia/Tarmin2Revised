package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class SurfaceDecal implements Pool.Poolable {
    public Vector3 position = new Vector3();
    public Color color = new Color();
    public float size;
    public float lifeTimer;

    // Decals last much longer than particles (e.g., 60 seconds)
    public static final float MAX_DECAL_LIFE = 60.0f;

    public SurfaceDecal() {
    }

    public void init(Vector3 pos, Color color, float size) {
        this.position.set(pos);
        this.color.set(color);
        this.size = size;
        this.lifeTimer = MAX_DECAL_LIFE;
    }

    @Override
    public void reset() {
        position.setZero();
        color.set(Color.WHITE);
        lifeTimer = 0;
    }

    public void update(float delta) {
        lifeTimer -= delta;
    }
}
