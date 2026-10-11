package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Lightweight scenic actor container for the Attract Mode 3D flyover.
 * Zero-garbage runtime representation for soldiers, archers, camps, monsters, and props.
 */
public class AttractActor {

    public enum ActorType {
        SOLDIER_MELEE,
        SOLDIER_ARCHER,
        SOLDIER_OFFICER,
        SOLDIER_SHIELD,
        MEGABEAST,
        WILD_PREDATOR,
        BLIGHTED_HORROR,
        CAMPFIRE,
        TORCH,
        TENT,
        SMOKE_PLUME,
        LOOT_CHEST,
        DISCARDED_SHIELD,
        BARRICADE
    }

    private final int id;
    private final ActorType type;
    private final Vector3 position = new Vector3();
    private final Vector2 scale = new Vector2(1f, 1f);
    private String houseId = "neutral";
    private final Color tint = new Color(Color.WHITE);
    private float facingDeg = 0f;
    private boolean inCombat = false;
    private float animTimer = 0f;
    private int animFrame = 0;
    private float hitFlash = 1f; // 1.0 = normal, < 1.0 = flashing red/white on hit
    private String spriteId = null; // monster type or prop texture key

    public AttractActor(int id, ActorType type, float x, float y, float z) {
        this.id = id;
        this.type = type;
        this.position.set(x, y, z);
    }

    public void update(float delta) {
        animTimer += delta;
        if (animTimer >= 0.15f) {
            animTimer -= 0.15f;
            animFrame = (animFrame + 1) % 9;
        }

        if (hitFlash < 1f) {
            hitFlash = Math.min(1f, hitFlash + delta * 5f);
        }

        // Periodic clash feedback for combat actors
        if (inCombat && Math.random() < delta * 0.8f) {
            hitFlash = 0.2f;
        }
    }

    public int getId() {
        return id;
    }

    public ActorType getType() {
        return type;
    }

    public Vector3 getPosition() {
        return position;
    }

    public Vector2 getScale() {
        return scale;
    }

    public AttractActor setScale(float width, float height) {
        this.scale.set(width, height);
        return this;
    }

    public String getHouseId() {
        return houseId;
    }

    public AttractActor setHouseId(String houseId) {
        this.houseId = houseId;
        return this;
    }

    public Color getTint() {
        return tint;
    }

    public AttractActor setTint(Color color) {
        if (color != null) this.tint.set(color);
        return this;
    }

    public float getFacingDeg() {
        return facingDeg;
    }

    public AttractActor setFacingDeg(float facingDeg) {
        this.facingDeg = facingDeg;
        return this;
    }

    public boolean isInCombat() {
        return inCombat;
    }

    public AttractActor setInCombat(boolean inCombat) {
        this.inCombat = inCombat;
        return this;
    }

    public float getAnimTimer() {
        return animTimer;
    }

    public int getAnimFrame() {
        return animFrame;
    }

    public float getHitFlash() {
        return hitFlash;
    }

    public String getSpriteId() {
        return spriteId;
    }

    public AttractActor setSpriteId(String spriteId) {
        this.spriteId = spriteId;
        return this;
    }
}
