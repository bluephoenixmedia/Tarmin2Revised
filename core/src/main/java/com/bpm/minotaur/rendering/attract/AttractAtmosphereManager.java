package com.bpm.minotaur.rendering.attract;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Orchestrates the 24-hour celestial lighting arc, dynamic atmospheric fog,
 * biome weather (rain ribbons, dust haze), and lightning strikes for the 3D Flyover.
 */
public class AttractAtmosphereManager {

    private final Color ambientColor = new Color();
    private final Vector3 dirLightDir = new Vector3(-0.4f, -0.8f, -0.4f);
    private final Color dirLightColor = new Color();
    private final Color fogColor = new Color();
    private float fogDistance = 65f;
    private float doomFactor = 1.0f;

    private boolean precipitationActive = false;
    private boolean lightningActive = false;
    private float lightningFlashTimer = 0f;
    private float thunderDelayTimer = 0f;
    private float nextLightningTimer = 4.0f;

    // Rain particles data: [x, y, z, len] per particle
    public static final int NUM_RAIN_STREAKS = 200;
    private final float[] rainParticles = new float[NUM_RAIN_STREAKS * 4];

    public AttractAtmosphereManager() {
        initRainParticles();
        // Initial setup at t = 0 (Dawn)
        evaluateLightingArc(0f);
    }

    private void initRainParticles() {
        for (int i = 0; i < NUM_RAIN_STREAKS; i++) {
            rainParticles[i * 4] = (float) (Math.random() * 30.0 - 15.0);
            rainParticles[i * 4 + 1] = (float) (Math.random() * 20.0);
            rainParticles[i * 4 + 2] = (float) (Math.random() * 30.0 - 15.0);
            rainParticles[i * 4 + 3] = 0.5f + (float) (Math.random() * 0.4);
        }
    }

    public void update(float delta, float flightTime, Vector3 cameraPos, AttractSoundManager soundManager) {
        evaluateLightingArc(flightTime);

        // Thunder delay handling
        if (thunderDelayTimer > 0f) {
            thunderDelayTimer -= delta;
            if (thunderDelayTimer <= 0f && soundManager != null) {
                soundManager.playThunder();
            }
        }

        // Lightning flash timer
        if (lightningActive) {
            lightningFlashTimer -= delta;
            if (lightningFlashTimer <= 0f) {
                lightningActive = false;
            } else {
                // Apply lightning flash spike to lights
                dirLightColor.set(1.2f, 1.3f, 1.6f, 1f);
                ambientColor.set(0.75f, 0.80f, 0.95f, 1f);
            }
        }

        // Sector 4 (Castle Tarmin Midnight Siege, t in [68..90]) has lightning & rain
        if (flightTime >= 68f && flightTime <= 90f) {
            precipitationActive = true;
            nextLightningTimer -= delta;
            if (nextLightningTimer <= 0f) {
                triggerLightningFlash(soundManager);
                nextLightningTimer = 3.5f + (float) Math.random() * 3.5f;
            }

            // Update falling rain streaks
            updateRain(delta);
        } else {
            precipitationActive = false;
        }
    }

    private void evaluateLightingArc(float time) {
        float normalized = (time % 90.0f) / 90.0f;
        if (normalized < 0) normalized += 1.0f;

        if (time < 18f) {
            // Sector 0: Lakelands Dawn (t: 0 - 18s)
            float k = time / 18f;
            ambientColor.set(0.38f + k * 0.04f, 0.42f + k * 0.04f, 0.52f + k * 0.02f, 1f);
            dirLightDir.set(-0.6f, -0.45f - k * 0.25f, -0.5f).nor();
            dirLightColor.set(0.78f + k * 0.05f, 0.68f + k * 0.08f, 0.52f + k * 0.15f, 1f);
            fogColor.set(0.35f, 0.45f, 0.65f, 1f);
            fogDistance = MathUtils.lerp(65f, 70f, k);
            doomFactor = 1.0f;

        } else if (time < 36f) {
            // Sector 1: Ancient Forest High Noon (t: 18 - 36s)
            float k = (time - 18f) / 18f;
            ambientColor.set(0.42f + k * 0.03f, 0.48f + k * 0.02f, 0.42f, 1f);
            dirLightDir.set(-0.2f, -0.9f, -0.2f).nor();
            dirLightColor.set(0.85f, 0.82f, 0.75f, 1f);
            fogColor.set(0.12f, 0.22f, 0.14f, 1f);
            fogDistance = MathUtils.lerp(70f, 55f, k);
            doomFactor = 1.0f;

        } else if (time < 54f) {
            // Sector 2: Desert Canyons Dusk / Golden Hour (t: 36 - 54s)
            float k = (time - 36f) / 18f;
            ambientColor.set(0.50f - k * 0.12f, 0.42f - k * 0.12f, 0.35f - k * 0.05f, 1f);
            dirLightDir.set(-0.8f, -0.25f, -0.4f).nor();
            dirLightColor.set(0.95f - k * 0.15f, 0.55f - k * 0.20f, 0.30f - k * 0.10f, 1f);
            fogColor.set(0.65f - k * 0.15f, 0.55f - k * 0.15f, 0.38f - k * 0.10f, 1f);
            fogDistance = MathUtils.lerp(55f, 60f, k);
            doomFactor = 1.0f;

        } else if (time < 68f) {
            // Sector 3: Mountain Twilight (t: 54 - 68s)
            float k = (time - 54f) / 14f;
            ambientColor.set(0.28f - k * 0.05f, 0.26f - k * 0.05f, 0.38f - k * 0.05f, 1f);
            dirLightDir.set(0.4f, -0.6f, 0.5f).nor();
            dirLightColor.set(0.35f - k * 0.05f, 0.38f - k * 0.05f, 0.50f - k * 0.05f, 1f);
            fogColor.set(0.25f - k * 0.08f, 0.25f - k * 0.08f, 0.35f - k * 0.10f, 1f);
            fogDistance = MathUtils.lerp(60f, 50f, k);
            doomFactor = 0.85f;

        } else {
            // Sector 4: Castle Tarmin Midnight Thunderstorm (t: 68 - 90s)
            float k = (time - 68f) / 22f;
            ambientColor.set(0.20f, 0.20f, 0.26f, 1f);
            dirLightDir.set(-0.4f, -0.8f, -0.4f).nor();
            dirLightColor.set(0.25f, 0.25f, 0.30f, 1f);
            fogColor.set(0.14f, 0.14f, 0.20f, 1f);
            fogDistance = MathUtils.lerp(50f, 45f, k);
            doomFactor = 0.70f;
        }
    }

    private void updateRain(float delta) {
        float fallSpeed = 24.0f * delta;
        float windX = -2.5f * delta;
        float windZ = -1.8f * delta;

        for (int i = 0; i < NUM_RAIN_STREAKS; i++) {
            rainParticles[i * 4] += windX;
            rainParticles[i * 4 + 1] -= fallSpeed;
            rainParticles[i * 4 + 2] += windZ;

            // Wrap vertically
            if (rainParticles[i * 4 + 1] < -2.0f) {
                rainParticles[i * 4 + 1] = 16.0f;
                rainParticles[i * 4] = (float) (Math.random() * 30.0 - 15.0);
                rainParticles[i * 4 + 2] = (float) (Math.random() * 30.0 - 15.0);
            }
        }
    }

    public void triggerLightningFlash(AttractSoundManager soundManager) {
        lightningActive = true;
        lightningFlashTimer = 0.18f;
        thunderDelayTimer = 0.35f;
        dirLightColor.set(1.2f, 1.3f, 1.6f, 1f);
        ambientColor.set(0.75f, 0.80f, 0.95f, 1f);
    }

    public Color getAmbientColor() { return ambientColor; }
    public Vector3 getDirLightDir() { return dirLightDir; }
    public Color getDirectionalLightColor() { return dirLightColor; }
    public Color getFogColor() { return fogColor; }
    public float getFogDistance() { return fogDistance; }
    public float getDoomFactor() { return doomFactor; }
    public boolean isPrecipitationActive() { return precipitationActive; }
    public boolean isLightningActive() { return lightningActive; }
    public float[] getRainParticles() { return rainParticles; }
}
