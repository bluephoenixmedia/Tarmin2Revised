package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.GameEvent;
import java.util.ArrayList;
import java.util.List;

public class GameEventManager {
    private final List<GameEvent> events = new ArrayList<>();

    public void addEvent(GameEvent event) {
        events.add(event);
    }

    public void update(float delta) {
        events.removeIf(event -> {
            event.update(delta);
            return event.isFinished();
        });
    }

    public List<GameEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}
