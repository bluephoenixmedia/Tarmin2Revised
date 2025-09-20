package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

public class Animation {

    public enum AnimationType {
        PROJECTILE
    }

    private final AnimationType type;
    private final Vector2 startPosition;
    private final Vector2 endPosition;
    private final Color color;
    private float progress;
    private final float duration;

    public Animation(AnimationType type, Vector2 startPosition, Vector2 endPosition, Color color, float duration) {
        this.type = type;
        this.startPosition = startPosition.cpy();
        this.endPosition = endPosition.cpy();
        this.color = color;
        this.duration = duration;
        this.progress = 0f;
    }

    public void update(float delta) {
        progress += delta / duration;
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
}
