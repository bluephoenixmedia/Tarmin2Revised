package com.bpm.minotaur.gamedata;

public class GameEvent {
    public enum EventType {
        HUD_MESSAGE
    }

    public final EventType type;
    public final String message;
    public float duration;

    public GameEvent(String message, float duration) {
        this.type = EventType.HUD_MESSAGE;
        this.message = message;
        this.duration = duration;
    }

    public void update(float delta) {
        if (duration > 0) {
            duration -= delta;
        }
    }

    public boolean isFinished() {
        return duration <= 0;
    }
}
