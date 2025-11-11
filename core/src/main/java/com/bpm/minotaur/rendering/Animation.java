package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;

public class Animation {

    public enum AnimationType {
        PROJECTILE_MONSTER,
        PROJECTILE_PLAYER,
        DAMAGE_TEXT
    }

    private float elapsedTime; // Changed from final so it can be updated

    private final AnimationType type;
    private final Vector2 startPosition;
    private final Vector2 endPosition;
    private final Color color;
    private float progress;
    private final float duration;
    private final String[] spriteData;

    private String damageText;
    private GridPoint2 textPosition;

    // Existing constructor for projectiles
    public Animation(AnimationType type, Vector2 startPosition, Vector2 endPosition, Color color, float duration, String[] spriteData) {
        this.type = type;
        this.startPosition = startPosition.cpy();
        this.endPosition = endPosition.cpy();
        this.color = color;
        this.duration = duration;
        this.progress = 0f;
        this.elapsedTime = 0f;
        this.spriteData = spriteData;
    }

    // NEW constructor for damage text
    public Animation(AnimationType type, GridPoint2 position, String text, float duration) {
        this.type = type;
        this.textPosition = position;
        this.damageText = text;
        this.duration = duration;
        this.progress = 0f;
        this.elapsedTime = 0f;

        // Initialize unused fields for projectiles
        this.startPosition = new Vector2();
        this.endPosition = new Vector2();
        this.color = Color.WHITE;
        this.spriteData = null;
    }

    public void update(float delta) {
        progress += delta / duration;
        elapsedTime += delta; // Update elapsed time
    }

    public boolean isFinished() {
        return progress >= 1.0f;
    }

    public AnimationType getType() {
        return type;
    }

    public Vector2 getStartPosition() {
        return startPosition;
    }

    public Vector2 getEndPosition() {
        return endPosition;
    }

    public float getProgress() {
        return progress;
    }

    public Color getColor() {
        return color;
    }

    public String[] getSpriteData() {
        return spriteData;
    }

    public String getDamageText() {
        return damageText;
    }

    public GridPoint2 getTextPosition() {
        return textPosition;
    }

    public float getElapsedTime() {
        return elapsedTime;
    }
}
