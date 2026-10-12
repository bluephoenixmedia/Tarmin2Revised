package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SlashArcManagerTest {

    @Before
    public void setUp() {
        if (com.badlogic.gdx.Gdx.app == null) {
            com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.Application.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Application.class},
                    (proxy, method, args) -> null
            );
        }
    }

    @Test
    public void testSpawnAndLifetime() {
        SlashArcManager manager = new SlashArcManager();
        assertEquals(0, manager.getActiveArcCount());

        manager.spawnSlashArc(new Vector3(10f, 0.5f, 10f), -45f, Color.CYAN, false, 1.2f);
        assertEquals(1, manager.getActiveArcCount());

        SlashArcManager.SlashArc arc = manager.getActiveArcs().get(0);
        assertEquals(-45f, arc.cutAngle, 0.01f);
        assertFalse(arc.isCrit);
        assertEquals(1.2f, arc.scale, 0.01f);

        // Update half-way
        manager.update(0.05f, false);
        assertEquals(1, manager.getActiveArcCount());
        assertTrue(arc.progress > 0.4f && arc.progress < 0.6f);

        // Update past duration (0.10s)
        manager.update(0.06f, false);
        assertEquals(0, manager.getActiveArcCount());
    }

    @Test
    public void testMicroDilationSlowdown() {
        SlashArcManager manager = new SlashArcManager();
        manager.spawnSlashArc(new Vector3(5f, 0.5f, 5f), 30f, Color.RED, true, 1.5f);

        SlashArcManager.SlashArc arc = manager.getActiveArcs().get(0);
        assertTrue(arc.isCrit);

        // In micro-dilation, 0.05s elapsed should only advance progress by ~10% (0.1x speed)
        manager.update(0.05f, true);
        assertEquals(1, manager.getActiveArcCount());
        assertTrue("During micro-dilation, progress should advance at ~10% speed", arc.progress < 0.15f);
    }
}
