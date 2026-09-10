package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.bpm.minotaur.utils.ShatterUtils;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class Gib implements Pool.Poolable {
    public Vector3 position = new Vector3();
    public Vector3 velocity = new Vector3();
    public String[] spriteData;
    public TextureRegion textureRegion; // For Modern 3D Mode
    public PolygonRegion polygonRegion;
    public float centroidX, centroidY;
    public Color color = new Color();
    private final Color baseColor = new Color(Color.WHITE);

    public float rotation;
    public float rotationalVelocity;
    public boolean onGround;
    public float lifeTimer;

    // Gibs stay for 30s, fading out over the final 3s
    public static final float MAX_GIB_LIFE = 30.0f;
    public static final float FADE_DURATION = 3.0f;

    public Gib() {
    }

    public void init(Vector3 pos, Vector3 vel, TextureRegion region) {
        init(pos, vel, region, Color.WHITE);
    }

    public void init(Vector3 pos, Vector3 vel, TextureRegion region, Color tint) {
        this.position.set(pos);
        this.velocity.set(vel);
        this.spriteData = null;
        this.polygonRegion = null;
        this.textureRegion = region;
        this.baseColor.set(tint != null ? tint : Color.WHITE);
        this.color.set(baseColor);

        this.rotation = MathUtils.random(0, 360);
        this.rotationalVelocity = MathUtils.random(-360, 360);
        this.onGround = false;
        this.lifeTimer = MAX_GIB_LIFE;
    }

    public void init(Vector3 pos, Vector3 vel, GibType type, Color overrideColor) {
        this.position.set(pos);
        this.velocity.set(vel);
        this.spriteData = type.spriteData;
        this.textureRegion = null;
        this.polygonRegion = null;
        if (type == GibType.MEAT_CHUNK && overrideColor != null) {
            this.baseColor.set(overrideColor);
        } else {
            this.baseColor.set(type.defaultColor);
        }
        this.color.set(baseColor);

        this.rotation = MathUtils.random(0, 360);
        this.rotationalVelocity = MathUtils.random(-300, 300);
        this.onGround = false;
        this.lifeTimer = MAX_GIB_LIFE;
    }

    public void init(Vector3 pos, Vector3 vel, String[] spriteData, Color color) {
        this.position.set(pos);
        this.velocity.set(vel);
        this.spriteData = spriteData;
        this.textureRegion = null;
        this.polygonRegion = null;
        this.baseColor.set(color != null ? color : Color.WHITE);
        this.color.set(baseColor);
        this.rotation = MathUtils.random(0, 360);
        this.rotationalVelocity = MathUtils.random(-300, 300);
        this.onGround = false;
        this.lifeTimer = MAX_GIB_LIFE;
    }

    public void init(Vector3 pos, Vector3 vel, ShatterUtils.Shard shard) {
        this.position.set(pos);
        this.velocity.set(vel);
        this.spriteData = null;
        this.textureRegion = null;
        this.polygonRegion = shard.region;
        this.centroidX = shard.centroidX;
        this.centroidY = shard.centroidY;
        this.baseColor.set(Color.WHITE);
        this.color.set(baseColor);
        this.rotation = MathUtils.random(0, 360);
        this.rotationalVelocity = MathUtils.random(-300, 300);
        this.onGround = false;
        this.lifeTimer = MAX_GIB_LIFE;
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        textureRegion = null;
        polygonRegion = null;
        spriteData = null;
        onGround = false;
        lifeTimer = 0;
        rotation = 0;
        rotationalVelocity = 0;
        color.set(Color.WHITE);
        baseColor.set(Color.WHITE);
    }

    public void update(float delta) {
        lifeTimer -= delta;

        // Fade out at end of life
        if (lifeTimer <= FADE_DURATION) {
            color.a = MathUtils.clamp(lifeTimer / FADE_DURATION, 0f, 1f) * baseColor.a;
        } else {
            color.a = baseColor.a;
        }

        if (onGround)
            return;

        // Gravity
        velocity.y -= 22.0f * delta; // Fall fast

        position.mulAdd(velocity, delta);
        rotation += rotationalVelocity * delta;

        // Floor Bounce Logic (y = 0.0f is floor level)
        if (position.y <= 0.0f) {
            if (Math.abs(velocity.y) > 2.0f) {
                // Bounce with energy loss
                position.y = 0.0f;
                velocity.y = -velocity.y * 0.4f; // Lose 60% vertical velocity
                velocity.x *= 0.6f; // Ground friction
                velocity.z *= 0.6f;
                rotationalVelocity *= 0.5f;
            } else {
                // Come to rest flat on the floor
                position.y = 0.0f;
                velocity.setZero();
                rotationalVelocity = 0;
                onGround = true;
                rotation = 0;
            }
        }
    }
}
