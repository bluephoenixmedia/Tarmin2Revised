package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.GameEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameEventManager {

    private final List<GameEvent> events = new ArrayList<>();
    private final List<String> messageHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 30;

    private static final String LOG_FILE = "logs/game_log.txt";

    public GameEventManager() {
        // Clear log file on startup
        try {
            com.badlogic.gdx.files.FileHandle file = com.badlogic.gdx.Gdx.files.local(LOG_FILE);
            file.writeString("", false); // Overwrite with empty string
        } catch (Exception e) {
            // Ignore if file doesn't exist etc
        }
    }

    public void addEvent(GameEvent event) {
        events.add(event);
        if (event.type == GameEvent.EventType.MESSAGE) {
            // Update in-memory history
            messageHistory.add(0, event.message); // Add to front (newest first)
            if (messageHistory.size() > MAX_HISTORY_SIZE) {
                messageHistory.remove(messageHistory.size() - 1);
            }

            // Write to log file
            try {
                com.badlogic.gdx.files.FileHandle file = com.badlogic.gdx.Gdx.files.local(LOG_FILE);
                file.writeString(event.message + "\n", true);
            } catch (Exception e) {
                com.badlogic.gdx.Gdx.app.error("GameEventManager", "Failed to write to log file", e);
            }
        }
    }

    public List<String> getMessageHistory() {
        return messageHistory;
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
     * 
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
