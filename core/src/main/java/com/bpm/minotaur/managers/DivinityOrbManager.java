package com.bpm.minotaur.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;

public class DivinityOrbManager {

    private static DivinityOrbManager instance;

    // Virtual screen space: 1920×1080. Game area occupies y=180–1080 (HUD=180px at bottom).
    // Orb spawns at the centre of the game area and targets the "DIV:" counter in the HUD.
    private static final float SPAWN_X  = 960f;
    private static final float SPAWN_Y  = 630f;  // 180 + 900/2
    private static final float TARGET_X =  92f;
    private static final float TARGET_Y = 118f;

    private final Array<DivinityOrb> activeOrbs = new Array<>(false, 4);

    public static DivinityOrbManager getInstance() {
        if (instance == null) instance = new DivinityOrbManager();
        return instance;
    }

    public void spawnOrb() {
        activeOrbs.add(new DivinityOrb());
    }

    public void update(float delta) {
        for (int i = activeOrbs.size - 1; i >= 0; i--) {
            DivinityOrb orb = activeOrbs.get(i);
            orb.update(delta);
            if (orb.isDone()) activeOrbs.removeIndex(i);
        }
    }

    public void render(ShapeRenderer sr, Viewport viewport) {
        if (activeOrbs.size == 0) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(viewport.getCamera().combined);
        for (DivinityOrb orb : activeOrbs) {
            orb.render(sr);
        }
    }

    // -------------------------------------------------------------------------

    private static class DivinityOrb {
        private static final float APPEAR_DUR = 0.30f;
        private static final float FLOAT_DUR  = 0.40f;
        private static final float SPIRAL_DUR = 0.75f;
        private static final float TOTAL_DUR  = APPEAR_DUR + FLOAT_DUR + SPIRAL_DUR;

        private static final float FLOAT_RISE  = 30f;
        private static final float SPIRAL_AMP  = 90f;
        private static final float MAX_RADIUS  = 12f;

        private float time   = 0f;
        private float x      = SPAWN_X;
        private float y      = SPAWN_Y;
        private float radius = 0f;
        private float alpha  = 0f;

        void update(float delta) {
            time += delta;

            if (time < APPEAR_DUR) {
                // Phase 1 — materialise
                float t = time / APPEAR_DUR;
                radius = MathUtils.lerp(0f, MAX_RADIUS, t * t);
                alpha  = t;
                x = SPAWN_X;
                y = SPAWN_Y;

            } else if (time < APPEAR_DUR + FLOAT_DUR) {
                // Phase 2 — drift upward, pulse
                float t = (time - APPEAR_DUR) / FLOAT_DUR;
                radius = MAX_RADIUS;
                alpha  = 1f;
                x = SPAWN_X;
                y = SPAWN_Y + t * FLOAT_RISE;

            } else {
                // Phase 3 — spiral toward the divinity counter
                float t = Math.min((time - APPEAR_DUR - FLOAT_DUR) / SPIRAL_DUR, 1f);
                float eased = t * t * (3f - 2f * t); // smoothstep

                float fromX = SPAWN_X;
                float fromY = SPAWN_Y + FLOAT_RISE;

                float dx = TARGET_X - fromX;
                float dy = TARGET_Y - fromY;
                float len = (float) Math.sqrt(dx * dx + dy * dy);

                // Perpendicular unit vector for the spiral offset
                float perpX = -dy / len;
                float perpY =  dx / len;
                float spiralOffset = MathUtils.sin(t * MathUtils.PI * 3f) * (1f - t) * SPIRAL_AMP;

                x = MathUtils.lerp(fromX, TARGET_X, eased) + perpX * spiralOffset;
                y = MathUtils.lerp(fromY, TARGET_Y, eased) + perpY * spiralOffset;

                radius = MathUtils.lerp(MAX_RADIUS, 3f, eased);
                alpha  = MathUtils.lerp(1f, 0f, eased * eased);
            }
        }

        boolean isDone() { return time >= TOTAL_DUR; }

        void render(ShapeRenderer sr) {
            sr.begin(ShapeRenderer.ShapeType.Filled);

            // Glow rings: outer deep-purple → mid-blue → inner bright-purple
            float scale = radius / MAX_RADIUS;
            sr.setColor(0.30f, 0.00f, 0.50f, 0.12f * alpha); sr.circle(x, y, radius * 3.0f * scale + 6f);
            sr.setColor(0.15f, 0.05f, 0.80f, 0.22f * alpha); sr.circle(x, y, radius * 2.2f * scale + 3f);
            sr.setColor(0.55f, 0.00f, 0.90f, 0.32f * alpha); sr.circle(x, y, radius * 1.6f * scale + 1f);

            // Core: near-black with a faint purple tint
            sr.setColor(0.04f, 0.00f, 0.10f, Math.min(alpha + 0.2f, 1f));
            sr.circle(x, y, radius);

            sr.end();
        }
    }
}
