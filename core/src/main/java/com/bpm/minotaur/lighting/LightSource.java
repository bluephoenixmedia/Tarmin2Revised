package com.bpm.minotaur.lighting;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Represents a dynamic 2D/3D point light source in world coordinates with
 * customizable RGB color temperature, falloff radius, and animated micro-flicker profiles.
 */
public class LightSource {

    public enum FlickerProfile {
        /** Calm, steady vintage golden radiance with gentle rhythmic breathing (+/- 2%) */
        LANTERN_BREATH,
        /** Dual-octave organic dancing firelight (+/- 12% radius, +/- 10% intensity, chromatic shift) */
        CAMPFIRE_FLICKER,
        /** Rapid subtle wind-flutter pine torch jitter (+/- 6% radius) */
        TORCH_FLUTTER,
        /** Constant, unchanging radiance */
        STEADY
    }

    private final String id;
    private final Vector2 position = new Vector2();
    private final Color baseColor = new Color(1f, 1f, 1f, 1f);
    private final Color currentColor = new Color(1f, 1f, 1f, 1f);

    private float baseRadius;
    private float currentRadius;

    private float baseIntensity;
    private float currentIntensity;

    private FlickerProfile profile;
    private boolean active = true;

    // Temporal accumulator for organic wave synthesis
    private float timeAccumulator;
    private final float flickerOffset;

    public LightSource(String id, float x, float y, Color color, float radius, float intensity, FlickerProfile profile) {
        this.id = id;
        this.position.set(x, y);
        this.baseColor.set(color);
        this.currentColor.set(color);
        this.baseRadius = radius;
        this.currentRadius = radius;
        this.baseIntensity = intensity;
        this.currentIntensity = intensity;
        this.profile = profile;
        this.flickerOffset = MathUtils.random(0f, 100f);
        this.timeAccumulator = this.flickerOffset;
    }

    public void update(float delta) {
        if (!active) {
            currentIntensity = 0f;
            currentRadius = 0f;
            return;
        }

        timeAccumulator += delta;

        switch (profile) {
            case CAMPFIRE_FLICKER: {
                // Primary deep rolling wave + secondary fast ember flicker
                float wave1 = MathUtils.sin((timeAccumulator + flickerOffset) * 4.2f);
                float wave2 = MathUtils.sin((timeAccumulator * 9.7f) + flickerOffset * 2f);
                float combined = (wave1 * 0.65f) + (wave2 * 0.35f);

                // Modulate radius by +/- 12%
                currentRadius = baseRadius * (1.0f + combined * 0.12f);
                // Modulate intensity by +/- 10%
                currentIntensity = baseIntensity * (1.0f + combined * 0.10f);

                // Chromatic warmth shift: embers shift slightly deeper red-orange at lower intensity
                float warmFactor = 1.0f - Math.max(0f, -combined * 0.15f);
                currentColor.set(
                        baseColor.r,
                        baseColor.g * warmFactor,
                        baseColor.b * (warmFactor * 0.9f),
                        1f
                );
                break;
            }
            case TORCH_FLUTTER: {
                // Fast flutter with occasional wind crackle pop
                float flutter = MathUtils.sin((timeAccumulator + flickerOffset) * 11.5f) * 0.04f
                        + MathUtils.sin((timeAccumulator * 23.1f)) * 0.02f;
                currentRadius = baseRadius * (1.0f + flutter);
                currentIntensity = baseIntensity * (1.0f + flutter * 1.2f);
                currentColor.set(baseColor);
                break;
            }
            case LANTERN_BREATH: {
                // Calm, rhythmic breathing (+/- 2%)
                float breath = MathUtils.sin((timeAccumulator + flickerOffset) * 2.1f) * 0.02f;
                currentRadius = baseRadius * (1.0f + breath);
                currentIntensity = baseIntensity * (1.0f + breath * 0.8f);
                currentColor.set(baseColor);
                break;
            }
            case STEADY:
            default: {
                currentRadius = baseRadius;
                currentIntensity = baseIntensity;
                currentColor.set(baseColor);
                break;
            }
        }
    }

    public String getId() { return id; }
    public Vector2 getPosition() { return position; }
    public void setPosition(float x, float y) { this.position.set(x, y); }

    public Color getBaseColor() { return baseColor; }
    public Color getCurrentColor() { return currentColor; }
    public void setBaseColor(Color color) { this.baseColor.set(color); }

    public float getBaseRadius() { return baseRadius; }
    public float getCurrentRadius() { return currentRadius; }
    public void setBaseRadius(float radius) { this.baseRadius = radius; this.currentRadius = radius; }

    public float getBaseIntensity() { return baseIntensity; }
    public float getCurrentIntensity() { return currentIntensity; }
    public void setBaseIntensity(float intensity) { this.baseIntensity = intensity; this.currentIntensity = intensity; }

    public FlickerProfile getProfile() { return profile; }
    public void setProfile(FlickerProfile profile) { this.profile = profile; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
