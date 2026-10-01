package com.bpm.minotaur.managers;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The alert has to stay rare to keep its meaning: it fires on the edge of a change, not while
 * something stays bad, and never more than once every few seconds.
 */
public class AlertMonitorTest {

    private static final float DT = 0.1f;

    /** A monitor that has already taken its baseline, so the next change is a real one. */
    private AlertMonitor primed() {
        AlertMonitor monitor = new AlertMonitor();
        monitor.update(DT, calm());
        return monitor;
    }

    /** Healthy, nothing owed, fed and watered. */
    private AlertMonitor.Snapshot calm() {
        return new AlertMonitor.Snapshot(1.0f, 0, 0, 0);
    }

    @Test
    public void nothingHappensWhileNothingChanges() {
        AlertMonitor monitor = new AlertMonitor();
        for (int i = 0; i < 100; i++) {
            assertNull(monitor.update(DT, calm()));
        }
    }

    @Test
    public void droppingBelowAQuarterHealthAlertsOnceNotContinuously() {
        AlertMonitor monitor = new AlertMonitor();
        monitor.update(DT, calm());

        assertEquals(AlertMonitor.Reason.LOW_HEALTH, monitor.update(DT, new AlertMonitor.Snapshot(0.20f, 0, 0, 0)));
        for (int i = 0; i < 200; i++) {
            assertNull("must not re-fire while health stays low",
                    monitor.update(DT, new AlertMonitor.Snapshot(0.20f, 0, 0, 0)));
        }
    }

    @Test
    public void lowHealthRearmsOnlyAfterRecoveringWellAboveTheLine() {
        AlertMonitor monitor = new AlertMonitor();
        monitor.update(DT, new AlertMonitor.Snapshot(0.20f, 0, 0, 0));
        elapse(monitor, 5f, new AlertMonitor.Snapshot(0.30f, 0, 0, 0)); // above, but not enough to re-arm
        assertNull(monitor.update(DT, new AlertMonitor.Snapshot(0.20f, 0, 0, 0)));

        elapse(monitor, 5f, new AlertMonitor.Snapshot(0.60f, 0, 0, 0));
        assertEquals(AlertMonitor.Reason.LOW_HEALTH, monitor.update(DT, new AlertMonitor.Snapshot(0.20f, 0, 0, 0)));
    }

    @Test
    public void aMonsterFirstNoticingYouAlerts() {
        AlertMonitor monitor = new AlertMonitor();
        monitor.noteMonsterNoticed();
        assertEquals(AlertMonitor.Reason.NOTICED, monitor.update(DT, calm()));
        assertNull("the flag is spent", monitor.update(DT, calm()));
    }

    @Test
    public void newlyUnspentPointsAlertButHavingThemDoesNot() {
        AlertMonitor monitor = primed();
        assertEquals(AlertMonitor.Reason.UNSPENT_POINTS, monitor.update(DT, new AlertMonitor.Snapshot(1f, 2, 0, 0)));
        elapse(monitor, 10f, new AlertMonitor.Snapshot(1f, 2, 0, 0));
        assertNull(monitor.update(DT, new AlertMonitor.Snapshot(1f, 2, 0, 0)));
        // spending some is not news; gaining more is
        elapse(monitor, 5f, new AlertMonitor.Snapshot(1f, 1, 0, 0));
        assertEquals(AlertMonitor.Reason.UNSPENT_POINTS, monitor.update(DT, new AlertMonitor.Snapshot(1f, 3, 0, 0)));
    }

    @Test
    public void gettingHungrierOrThirstierAlertsButEatingDoesNot() {
        AlertMonitor monitor = primed();
        assertEquals(AlertMonitor.Reason.HUNGER, monitor.update(DT, new AlertMonitor.Snapshot(1f, 0, 1, 0)));
        elapse(monitor, 5f, new AlertMonitor.Snapshot(1f, 0, 1, 0));
        assertEquals(AlertMonitor.Reason.HUNGER, monitor.update(DT, new AlertMonitor.Snapshot(1f, 0, 2, 0)));
        elapse(monitor, 5f, new AlertMonitor.Snapshot(1f, 0, 0, 0)); // ate
        assertEquals(AlertMonitor.Reason.THIRST, monitor.update(DT, new AlertMonitor.Snapshot(1f, 0, 0, 1)));
    }

    @Test
    public void nothingFiresAgainWithinTheCooldown() {
        AlertMonitor monitor = new AlertMonitor();
        monitor.noteMonsterNoticed();
        assertNotNull(monitor.update(DT, calm()));

        monitor.noteMonsterNoticed();
        assertNull("inside the cooldown", monitor.update(DT, calm()));
        elapse(monitor, AlertMonitor.COOLDOWN_SECONDS, calm());
        monitor.noteMonsterNoticed();
        assertNotNull("after the cooldown", monitor.update(DT, calm()));
    }

    @Test
    public void healthTakesPriorityWhenSeveralThingsHappenAtOnce() {
        AlertMonitor monitor = primed();
        monitor.noteMonsterNoticed();
        assertEquals(AlertMonitor.Reason.LOW_HEALTH, monitor.update(DT, new AlertMonitor.Snapshot(0.1f, 3, 2, 2)));
    }

    @Test
    public void theFirstLookOnlySetsABaselineSoLoadingASaveNeverAlerts() {
        // A save loaded with points to spend, low health or an empty stomach is not news.
        AlertMonitor monitor = new AlertMonitor();
        assertNull(monitor.update(DT, new AlertMonitor.Snapshot(0.10f, 4, 2, 2)));
        elapse(monitor, 10f, new AlertMonitor.Snapshot(0.10f, 4, 2, 2));
        assertNull("still the same state, still no alert", monitor.update(DT, new AlertMonitor.Snapshot(0.10f, 4, 2, 2)));
        // ...but getting worse after that is.
        assertEquals(AlertMonitor.Reason.UNSPENT_POINTS, monitor.update(DT, new AlertMonitor.Snapshot(0.10f, 5, 2, 2)));
    }

    @Test
    public void resettingTakesANewBaselineAndDropsAnythingPending() {
        AlertMonitor monitor = new AlertMonitor();
        monitor.update(DT, calm());
        monitor.noteMonsterNoticed();
        monitor.reset();

        assertNull("the pending notice must not survive a death", monitor.update(DT, calm()));
        assertNull("a respawn with points owed is a baseline, not an alert",
                monitor.update(DT, new AlertMonitor.Snapshot(1f, 0, 0, 0)));
    }

    @Test
    public void ranksComeFromTheRealStats() {
        com.bpm.minotaur.gamedata.player.PlayerStats stats =
                new com.bpm.minotaur.gamedata.player.PlayerStats(com.bpm.minotaur.gamedata.Difficulty.MEDIUM);
        stats.setMaxHP(20);
        stats.setCurrentHP(5);
        stats.setSatiety(60f);
        stats.setHydration(60f);
        AlertMonitor.Snapshot fine = AlertMonitor.Snapshot.of(stats);
        assertEquals(0.25f, fine.healthShare(), 0.001f);
        assertEquals(0, fine.hungerRank());
        assertEquals(0, fine.thirstRank());

        stats.setSatiety(10f);
        stats.setHydration(10f);
        assertEquals("hungry", 1, AlertMonitor.Snapshot.of(stats).hungerRank());
        assertEquals("thirsty", 1, AlertMonitor.Snapshot.of(stats).thirstRank());

        stats.setSatiety(0f);
        stats.setHydration(0f);
        assertEquals("starving", 2, AlertMonitor.Snapshot.of(stats).hungerRank());
        assertEquals("parched", 2, AlertMonitor.Snapshot.of(stats).thirstRank());
    }

    private static void elapse(AlertMonitor monitor, float seconds, AlertMonitor.Snapshot snapshot) {
        for (float t = 0; t < seconds; t += DT) {
            monitor.update(DT, snapshot);
        }
    }
}
