package com.bpm.minotaur.rendering;

import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.rendering.attract.AttractProjectileManager;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AttractProjectileManagerTest {

    private AttractProjectileManager manager;

    @Before
    public void setUp() {
        manager = new AttractProjectileManager();
    }

    @Test
    public void testVolleyGenerationInActiveSectors() {
        // Sector 4 is Castle Tarmin Siege (flight time 70s - 90s)
        manager.update(0.1f, 75.0f, new Vector3(180f, 1.6f, -140f), null);
        List<AttractProjectileManager.AttractArrow> arrows = manager.getActiveArrows();
        assertFalse("Castle siege sector should spawn active flying arrows", arrows.isEmpty());
    }

    @Test
    public void testParabolicTrajectoryAndImpactSticking() {
        // Spawn a test arrow directly
        Vector3 start = new Vector3(180f, 2f, -125f);
        Vector3 target = new Vector3(180f, 1f, -150f);
        AttractProjectileManager.AttractArrow arrow = manager.spawnArrow(start, target, 2.0f, 6.0f, true);

        assertEquals("Starts at startPos", start.x, arrow.getCurrentPos().x, 0.01f);
        assertEquals("Starts at startPos", start.y, arrow.getCurrentPos().y, 0.01f);
        assertEquals("Starts at startPos", start.z, arrow.getCurrentPos().z, 0.01f);

        // Update halfway (t = 1.0s)
        manager.update(1.0f, 0f, new Vector3(), null);
        assertFalse("Should still be airborne", arrow.isImpactStuck());
        assertTrue("Altitude should peak above start and target due to parabola arc",
                arrow.getCurrentPos().y > Math.max(start.y, target.y));

        // Update past flight time (t = 1.2s -> total 2.2s >= 2.0s)
        manager.update(1.2f, 0f, new Vector3(), null);
        assertTrue("Should have struck target and become stuck", arrow.isImpactStuck());
        assertEquals("Should be at target X", target.x, arrow.getCurrentPos().x, 0.05f);
        assertEquals("Should be at target Z", target.z, arrow.getCurrentPos().z, 0.05f);
    }

    @Test
    public void testCloseCameraWhistleCallback() {
        AtomicInteger whistleCount = new AtomicInteger(0);
        AttractProjectileManager.WhistleListener listener = whistleCount::incrementAndGet;
        manager.setWhistleListener(listener);

        // Spawn arrow that passes through (180, 2.0, -135)
        Vector3 start = new Vector3(180f, 2f, -125f);
        Vector3 target = new Vector3(180f, 2f, -145f);
        Vector3 cameraPos = new Vector3(180f, 2f, -135f); // Directly on path

        manager.spawnArrow(start, target, 1.0f, 0.5f, false);

        // Step through trajectory
        for (int i = 0; i < 15; i++) {
            manager.update(0.1f, 0f, cameraPos, null);
        }

        assertTrue("Whistle should have fired at least once for close pass", whistleCount.get() >= 1);
        assertEquals("Whistle should not trigger repeatedly for the same arrow", 1, whistleCount.get());
    }

    @Test
    public void testStuckArrowExpiration() {
        Vector3 start = new Vector3(0, 0, 0);
        Vector3 target = new Vector3(10, 0, 0);
        AttractProjectileManager.AttractArrow arrow = manager.spawnArrow(start, target, 0.1f, 1f, false);

        manager.update(0.2f, 0f, new Vector3(), null);
        assertTrue("Should be stuck", arrow.isImpactStuck());
        assertTrue("Should be in stuck list", manager.getStuckArrows().contains(arrow));

        // Advance 5 seconds past stuck duration
        manager.update(5.0f, 0f, new Vector3(), null);
        assertFalse("Expired stuck arrows should be evicted", manager.getStuckArrows().contains(arrow));
    }
}
