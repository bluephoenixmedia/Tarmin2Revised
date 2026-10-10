package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages ballistic projectile volleys, parabolic arc physics, close-camera whistle
 * audio triggers, and impact ground-sticking for the 3D Main Menu Flyover.
 */
public class AttractProjectileManager {

    public interface WhistleListener {
        void onArrowWhistle();
    }

    public static class AttractArrow {
        private final Vector3 startPos = new Vector3();
        private final Vector3 targetPos = new Vector3();
        private final Vector3 currentPos = new Vector3();
        private final Vector3 dir = new Vector3(0, 0, -1);
        private final float flightDuration;
        private float elapsed = 0f;
        private final float arcHeight;
        private final boolean flaming;
        private boolean impactStuck = false;
        private float stuckTimer = 0f;
        private boolean whistlePlayed = false;

        public AttractArrow(Vector3 start, Vector3 target, float duration, float arcHeight, boolean flaming) {
            this.startPos.set(start);
            this.targetPos.set(target);
            this.currentPos.set(start);
            this.flightDuration = Math.max(0.1f, duration);
            this.arcHeight = arcHeight;
            this.flaming = flaming;
            this.dir.set(target).sub(start).nor();
        }

        public void update(float delta) {
            if (impactStuck) {
                stuckTimer += delta;
                return;
            }

            elapsed += delta;
            float u = Math.min(1.0f, elapsed / flightDuration);

            // Linear base
            float lx = MathUtils.lerp(startPos.x, targetPos.x, u);
            float ly = MathUtils.lerp(startPos.y, targetPos.y, u);
            float lz = MathUtils.lerp(startPos.z, targetPos.z, u);

            // Parabolic height offset: 4 * h * u * (1 - u)
            float arcOffset = 4.0f * arcHeight * u * (1.0f - u);
            currentPos.set(lx, ly + arcOffset, lz);

            if (u >= 1.0f) {
                impactStuck = true;
                currentPos.set(targetPos);
            }
        }

        public Vector3 getCurrentPos() { return currentPos; }
        public Vector3 getDir() { return dir; }
        public boolean isFlaming() { return flaming; }
        public boolean isImpactStuck() { return impactStuck; }
        public float getStuckTimer() { return stuckTimer; }
        public boolean isWhistlePlayed() { return whistlePlayed; }
        public void setWhistlePlayed(boolean played) { this.whistlePlayed = played; }
    }

    private final List<AttractArrow> activeArrows = new ArrayList<>();
    private final List<AttractArrow> stuckArrows = new ArrayList<>();

    private WhistleListener whistleListener = null;

    // Volley timers (initialized to fire upon entering sector)
    private float castleVolleyTimer = 1.0f;
    private float desertVolleyTimer = 2.0f;
    private float mountainVolleyTimer = 2.5f;

    private static final float STUCK_LIFETIME = 4.5f;
    private static final float WHISTLE_DISTANCE_SQ = 4.5f * 4.5f;

    public void setWhistleListener(WhistleListener listener) {
        this.whistleListener = listener;
    }

    public AttractArrow spawnArrow(Vector3 start, Vector3 target, float duration, float arcHeight, boolean flaming) {
        AttractArrow arrow = new AttractArrow(start, target, duration, arcHeight, flaming);
        activeArrows.add(arrow);
        return arrow;
    }

    public void update(float delta, float flightTime, Vector3 cameraPos, AttractSoundManager soundManager) {
        // --- SECTOR VOLLEY CHOREOGRAPHY ---
        // Sector 2 (Desert Canyons, t: 36s - 54s)
        if (flightTime >= 36f && flightTime <= 54f) {
            desertVolleyTimer += delta;
            if (desertVolleyTimer >= 2.0f) {
                desertVolleyTimer = 0f;
                spawnDesertSkirmishVolley();
            }
        }

        // Sector 3 (Mountain Gorge, t: 54s - 68s)
        if (flightTime >= 54f && flightTime <= 68f) {
            mountainVolleyTimer += delta;
            if (mountainVolleyTimer >= 2.2f) {
                mountainVolleyTimer = 0f;
                spawnMountainDefensiveVolley();
            }
        }

        // Sector 4 (Castle Tarmin Midnight Siege, t: 68s - 90s)
        if (flightTime >= 68f && flightTime <= 90f) {
            castleVolleyTimer += delta;
            if (castleVolleyTimer >= 0.75f) {
                castleVolleyTimer = 0f;
                spawnCastleSiegeCrossfire();
            }
        }

        // --- UPDATE ACTIVE AIRBORNE ARROWS ---
        Iterator<AttractArrow> it = activeArrows.iterator();
        while (it.hasNext()) {
            AttractArrow a = it.next();
            a.update(delta);

            // Close-camera whistle check
            if (!a.isWhistlePlayed() && !a.isImpactStuck() && cameraPos != null) {
                if (a.getCurrentPos().dst2(cameraPos) <= WHISTLE_DISTANCE_SQ) {
                    a.setWhistlePlayed(true);
                    if (whistleListener != null) {
                        whistleListener.onArrowWhistle();
                    }
                    if (soundManager != null) {
                        soundManager.playArrowWhistle();
                    }
                }
            }

            if (a.isImpactStuck()) {
                stuckArrows.add(a);
                it.remove();
            }
        }

        // --- UPDATE STUCK IMPACT ARROWS ---
        Iterator<AttractArrow> stuckIt = stuckArrows.iterator();
        while (stuckIt.hasNext()) {
            AttractArrow a = stuckIt.next();
            a.update(delta);
            if (a.getStuckTimer() >= STUCK_LIFETIME) {
                stuckIt.remove();
            }
        }
    }

    private void spawnCastleSiegeCrossfire() {
        // Attackers firing from southern avenue toward ramparts
        float startX = 175f + (float) Math.random() * 10f;
        float targetX = 174f + (float) Math.random() * 12f;
        Vector3 siegeAttacker = new Vector3(startX, 1.2f, -125f);
        Vector3 rampartTarget = new Vector3(targetX, 2.6f, -150f);
        spawnArrow(siegeAttacker, rampartTarget, 1.8f, 7.5f, true);

        // Defenders firing from ramparts back into siege lines
        if (Math.random() < 0.7f) {
            float defX = 175f + (float) Math.random() * 10f;
            float defTargetX = 174f + (float) Math.random() * 12f;
            Vector3 defStart = new Vector3(defX, 2.5f, -149f);
            Vector3 defTarget = new Vector3(defTargetX, 0.2f, -126f);
            spawnArrow(defStart, defTarget, 1.7f, 6.0f, Math.random() < 0.5f);
        }
    }

    private void spawnDesertSkirmishVolley() {
        float x1 = 145f + (float) Math.random() * 8f;
        float x2 = 135f + (float) Math.random() * 8f;
        Vector3 start = new Vector3(x1, 1.5f, -85f);
        Vector3 target = new Vector3(x2, 0.2f, -92f);
        spawnArrow(start, target, 1.4f, 4.0f, false);
    }

    private void spawnMountainDefensiveVolley() {
        float x = 88f + (float) Math.random() * 5f;
        Vector3 ridge = new Vector3(x, 4.0f, -188f);
        Vector3 gorgeFloor = new Vector3(x + 3f, 0.3f, -195f);
        spawnArrow(ridge, gorgeFloor, 1.2f, 3.5f, false);
    }

    public List<AttractArrow> getActiveArrows() {
        return activeArrows;
    }

    public List<AttractArrow> getStuckArrows() {
        return stuckArrows;
    }

    public void clear() {
        activeArrows.clear();
        stuckArrows.clear();
    }
}
