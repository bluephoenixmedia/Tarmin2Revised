package com.bpm.minotaur.gamedata;

public class GameEvent {

    // --- NEW: EventType Enum ---
    public enum EventType {
        MESSAGE,
        CHUNK_TRANSITION
    }

    // --- Fields ---
    public final EventType type;
    public final String message;
    public final Object payload;
    public float timeToLive;

    /**
     * Constructor for MESSAGE events.
     * @param message The text to be displayed on the HUD.
     * @param timeToLive How long the message should be displayed.
     */
    public GameEvent(String message, float timeToLive) {
        this.type = EventType.MESSAGE;
        this.message = message;
        this.timeToLive = timeToLive;
        this.payload = null;
    }

    /**
     * Constructor for non-MESSAGE (system) events.
     * @param type The type of event (e.g., CHUNK_TRANSITION).
     * @param payload The data object associated with the event (e.g., a Gate).
     */
    public GameEvent(EventType type, Object payload) {
        this.type = type;
        this.message = null;
        this.timeToLive = 0; // System events are consumed immediately, not timed
        this.payload = payload;
    }

    public void update(float delta) {
        if (this.type == EventType.MESSAGE) {
            this.timeToLive -= delta;
        }
    }

    public boolean isFinished() {
        return this.type == EventType.MESSAGE && this.timeToLive <= 0;
    }
}
