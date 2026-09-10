package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class SurfaceDecal implements Pool.Poolable {
    public Vector3 position = new Vector3();
    public Color color = new Color();
    public float size;
    public float initialSize;
    public float targetSize;
    public float expandTimer;
    public float lifeTimer;
    public float maxLife;

    private final Color freshColor = new Color();
    private final Color driedColor = new Color();

    public TextureRegion textureRegion; // For Modern Mode

    public static final float MAX_DECAL_LIFE = 45.0f;
    public static final float EXPAND_DURATION = 0.4f;
    public static final float FADE_DURATION = 5.0f;

    public SurfaceDecal() {
    }

    public void init(Vector3 pos, Color startColor, float targetRadius, TextureRegion texture) {
        this.position.set(pos);
        this.freshColor.set(startColor);

        // Dried color: darkened oxidized maroon, keeping hue
        this.driedColor.set(startColor.r * 0.42f, startColor.g * 0.15f, startColor.b * 0.15f, startColor.a);

        this.color.set(freshColor);
        this.initialSize = Math.max(0.06f, targetRadius * 0.35f);
        this.targetSize = Math.max(0.16f, targetRadius);
        this.size = initialSize;
        this.expandTimer = 0f;
        this.maxLife = MAX_DECAL_LIFE;
        this.lifeTimer = MAX_DECAL_LIFE;
        this.textureRegion = texture;
    }

    @Override
    public void reset() {
        position.setZero();
        color.set(Color.WHITE);
        freshColor.set(Color.WHITE);
        driedColor.set(Color.WHITE);
        size = 0f;
        initialSize = 0f;
        targetSize = 0f;
        expandTimer = 0f;
        lifeTimer = 0f;
        textureRegion = null;
    }

    public void update(float delta) {
        lifeTimer -= delta;
        expandTimer += delta;

        // 1. Dynamic puddle expansion over initial 0.4s
        if (expandTimer < EXPAND_DURATION) {
            float progress = expandTimer / EXPAND_DURATION;
            // Smooth cubic out expansion
            float t = 1.0f - (1.0f - progress) * (1.0f - progress);
            size = MathUtils.lerp(initialSize, targetSize, t);
        } else {
            size = targetSize;
        }

        // 2. Oxidizing color transition (Crimson -> Dark Maroon) over first 30 seconds
        float age = maxLife - lifeTimer;
        float dryT = MathUtils.clamp(age / 30.0f, 0f, 1f);
        color.r = MathUtils.lerp(freshColor.r, driedColor.r, dryT);
        color.g = MathUtils.lerp(freshColor.g, driedColor.g, dryT);
        color.b = MathUtils.lerp(freshColor.b, driedColor.b, dryT);

        // 3. Final 5s Alpha Fadeout
        if (lifeTimer <= FADE_DURATION) {
            color.a = MathUtils.clamp(lifeTimer / FADE_DURATION, 0f, 1f) * freshColor.a;
        } else {
            color.a = freshColor.a;
        }
    }
}
