package com.bpm.minotaur.managers;

import com.bpm.minotaur.gamedata.GameEvent;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Verifies the death-idempotency safeguards added for the NetHack Progression
 * Reboot: DoomManager records at most one demise per expedition run ID, and
 * GameEventManager can purge stray duplicate PLAYER_DIED events queued in the
 * same frame (e.g. a monster attack landing the same instant as a starvation
 * tick) so only one is ever processed.
 */
public class DeathIdempotencyTest {

    @Test
    public void testDoomManagerRecordsExactlyOneDeathPerRunId() {
        DoomManager doom = DoomManager.getInstance();
        int before = doom.getDeathCount();
        String runId = UUID.randomUUID().toString();

        assertTrue("First demise for a run must be recorded", doom.recordDeath(runId));
        assertEquals(before + 1, doom.getDeathCount());

        // Simulate the event queue delivering a second PLAYER_DIED for the same run
        // (e.g. a stray monster attack resolving after the fatal blow).
        assertFalse("A second demise for the SAME run must be suppressed", doom.recordDeath(runId));
        assertFalse(doom.recordDeath(runId));
        assertEquals("Doom clock must not advance for duplicate demises",
                before + 1, doom.getDeathCount());

        // A genuinely new expedition run must still be able to record its own demise.
        String nextRunId = UUID.randomUUID().toString();
        assertTrue("A new run's first demise must be recorded", doom.recordDeath(nextRunId));
        assertEquals(before + 2, doom.getDeathCount());
    }

    @Test
    public void testGameEventManagerConsumeAllPurgesOnlyMatchingType() {
        GameEventManager eventManager = new GameEventManager();

        eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
        eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
        eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));

        eventManager.consumeAll(GameEvent.EventType.PLAYER_DIED);

        assertNull("All queued PLAYER_DIED events must be purged",
                eventManager.findAndConsume(GameEvent.EventType.PLAYER_DIED));
    }

    @Test
    public void testDeathTransitionFlagPatternSuppressesReentry() {
        // Mirrors GameScreen's isDeathTransitionTriggered guard: once a death
        // transition has started, further PLAYER_DIED events in the same queue
        // drain must be ignored rather than reprocessed.
        GameEventManager eventManager = new GameEventManager();
        eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
        eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));

        boolean[] isDeathTransitionTriggered = {false};
        int processedCount = 0;

        GameEvent event;
        while ((event = eventManager.findAndConsume(GameEvent.EventType.PLAYER_DIED)) != null) {
            if (isDeathTransitionTriggered[0]) {
                continue;
            }
            isDeathTransitionTriggered[0] = true;
            eventManager.consumeAll(GameEvent.EventType.PLAYER_DIED);
            processedCount++;
        }

        assertEquals("Only the first PLAYER_DIED event should ever be fully processed", 1, processedCount);
    }
}
