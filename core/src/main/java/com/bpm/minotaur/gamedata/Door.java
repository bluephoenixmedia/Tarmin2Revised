package com.bpm.minotaur.gamedata;

public class Door {
    public enum DoorState { CLOSED, OPENING, OPEN }

    private DoorState state = DoorState.CLOSED;
    private float animationProgress = 0f; // 0.0 = closed, 1.0 = open
    private static final float ANIMATION_SPEED = 2.0f; // Opens in 0.5 seconds

    public DoorState getState() {
        return state;
    }

    public void close() {
        state = DoorState.CLOSED;
        animationProgress = 0f;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    public void startOpening() {
        if (state == DoorState.CLOSED) {
            state = DoorState.OPENING;
        }
    }

    public void update(float delta) {
        if (state == DoorState.OPENING) {
            animationProgress += delta * ANIMATION_SPEED;
            if (animationProgress >= 1.0f) {
                animationProgress = 1.0f;
                state = DoorState.OPEN;
            }
        }
    }
}
