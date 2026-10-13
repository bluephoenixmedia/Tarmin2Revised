package com.bpm.minotaur.rendering;

import com.bpm.minotaur.gamedata.GameEvent;
import com.bpm.minotaur.managers.GameEventManager;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ChronicleScrollTest {

    @Test
    public void testHistoryCapacityAndRevisionIncrement() {
        GameEventManager manager = new GameEventManager();
        assertEquals(0, manager.getMessageHistoryRevision());
        assertTrue(manager.getMessageHistory().isEmpty());

        for (int i = 1; i <= 250; i++) {
            manager.addEvent(new GameEvent("Combat log message " + i, 2f));
        }

        List<String> history = manager.getMessageHistory();
        assertEquals("History should cap at 200 items", 200, history.size());
        assertEquals("Combat log message 250", history.get(0)); // newest first
        assertEquals(250, manager.getMessageHistoryRevision());
    }

    @Test
    public void testNonMessageEventsDoNotPolluteMessageHistory() {
        GameEventManager manager = new GameEventManager();
        manager.addEvent(new GameEvent("Visible message", 2f));
        int revision = manager.getMessageHistoryRevision();

        manager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
        assertEquals("Non-message event should not increment message revision", revision, manager.getMessageHistoryRevision());
        assertEquals(1, manager.getMessageHistory().size());
    }
}
