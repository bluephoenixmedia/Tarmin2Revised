package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.GameEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameEventManager {

    private final List<GameEvent> events = new ArrayList<>();

    public void addEvent(GameEvent event) {
        events.add(event);
    }

    public void update(float delta) {
        // Iterate over a copy to avoid ConcurrentModificationException
        Iterator<GameEvent> iterator = new ArrayList<>(events).iterator();
        while (iterator.hasNext()) {
            GameEvent event = iterator.next();

            // Only update timed MESSAGE events
            if (event.type == GameEvent.EventType.MESSAGE) {
                event.update(delta);
                if (event.isFinished()) {
                    events.remove(event);
                }
            }
        }
    }

    /**
     * Finds, removes, and returns the first event of a specific type.
     * This is used for consuming system events.
     *
     * @param type The EventType to look for.
     * @return The found GameEvent, or null if not found.
     */
    public GameEvent findAndConsume(GameEvent.EventType type) {
        GameEvent foundEvent = null;
        for (GameEvent event : events) {
            if (event.type == type) {
                foundEvent = event;
                break;
            }
        }

        if (foundEvent != null) {
            events.remove(foundEvent);
        }

        return foundEvent;
    }

    /**
     * Gets the list of active events, filtered to only include MESSAGE types
     * for the HUD to display.
     * @return A list of message-based GameEvents.
     */
    public List<GameEvent> getMessageEvents() {
        List<GameEvent> messageEvents = new ArrayList<>();
        for (GameEvent event : events) {
            if (event.type == GameEvent.EventType.MESSAGE) {
                messageEvents.add(event);
            }
        }
        return messageEvents;
    }
}
