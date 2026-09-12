package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.bpm.minotaur.gamedata.spells.VisualArchetype;

/**
 * Manages post-processing spell visual effects:
 * - Radial shockwave refraction (Fireball, Thunderwave, Shatter)
 * - Chromatic aberration displacement (Spatial warp, psychic shock)
 * - Edge vignettes and elemental tints (Frost, heat, necrotic gloom)
 * - Spatial glitch line displacement (Misty Step, Teleport)
 *
 * Can upload uniforms directly to CRT shader or render via standalone spell_fx shader.
 */
public class SpellPostProcessor implements Disposable {

    private ShaderProgram spellShader;
    private boolean isShaderCompiled = false;

    // Shockwave state
    private final Vector2 shockwaveCenter = new Vector2(0.5f, 0.5f);
    private float shockwaveProgress = 0f;
    private float shockwaveDuration = 0f;
    private float shockwaveTimer = 0f;
    private float shockwaveStrength = 0.08f;

    // Vignette state
    private final Color vignetteColor = new Color(0, 0, 0, 0);
    private float vignetteIntensity = 0f;
    private float vignetteTargetIntensity = 0f;
    private float vignetteDuration = 0f;
    private float vignetteTimer = 0f;

    // Glitch state
    private float glitchFactor = 0f;
    private float glitchDuration = 0f;
    private float glitchTimer = 0f;

    // Chromatic aberration state
    private float chromaticIntensity = 0f;
    private float chromaticDuration = 0f;
    private float chromaticTimer = 0f;

    private float elapsedTime = 0f;

    public SpellPostProcessor() {
        initShader();
    }

    private void initShader() {
        try {
            ShaderProgram.pedantic = false;
            spellShader = new ShaderProgram(
                    Gdx.files.internal("shaders/spell_fx.vert"),
                    Gdx.files.internal("shaders/spell_fx.frag")
            );
            if (spellShader.isCompiled()) {
                isShaderCompiled = true;
            } else {
                Gdx.app.error("SpellPostProcessor", "Shader compilation failed:\n" + spellShader.getLog());
                isShaderCompiled = false;
            }
        } catch (Exception e) {
            Gdx.app.error("SpellPostProcessor", "Could not load spell shaders: " + e.getMessage());
            isShaderCompiled = false;
        }
    }

    public void update(float delta) {
        elapsedTime += delta;

        // Shockwave update
        if (shockwaveTimer < shockwaveDuration) {
            shockwaveTimer += delta;
            if (shockwaveTimer >= shockwaveDuration) {
                shockwaveProgress = 0f;
            } else {
                shockwaveProgress = MathUtils.clamp(shockwaveTimer / shockwaveDuration, 0f, 1f);
            }
        } else {
            shockwaveProgress = 0f;
        }

        // Vignette update
        if (vignetteTimer < vignetteDuration) {
            vignetteTimer += delta;
            if (vignetteTimer >= vignetteDuration) {
                vignetteIntensity = 0f;
            } else {
                float t = vignetteTimer / vignetteDuration;
                vignetteIntensity = vignetteTargetIntensity * (1.0f - (t * t));
            }
        } else {
            vignetteIntensity = 0f;
        }

        // Glitch update
        if (glitchTimer < glitchDuration) {
            glitchTimer += delta;
            if (glitchTimer >= glitchDuration) {
                glitchFactor = 0f;
            } else {
                float t = glitchTimer / glitchDuration;
                glitchFactor = (1.0f - t);
            }
        } else {
            glitchFactor = 0f;
        }

        // Chromatic update
        if (chromaticTimer < chromaticDuration) {
            chromaticTimer += delta;
            if (chromaticTimer >= chromaticDuration) {
                chromaticIntensity = 0f;
            } else {
                float t = chromaticTimer / chromaticDuration;
                chromaticIntensity = (1.0f - t);
            }
        } else {
            chromaticIntensity = 0f;
        }
    }

    /**
     * Uploads spell effect uniforms to any compatible shader (e.g. crtShader or spellShader).
     */
    public void applyUniforms(ShaderProgram shader, float time) {
        if (shader == null || !shader.isCompiled()) return;

        shader.setUniformf("u_shockwaveCenter", shockwaveCenter.x, shockwaveCenter.y);
        shader.setUniformf("u_shockwaveProgress", shockwaveProgress);
        shader.setUniformf("u_shockwaveStrength", shockwaveStrength);

        shader.setUniformf("u_vignetteColor", vignetteColor.r, vignetteColor.g, vignetteColor.b, vignetteColor.a);
        shader.setUniformf("u_vignetteIntensity", vignetteIntensity);

        shader.setUniformf("u_glitchFactor", glitchFactor);
        shader.setUniformf("u_spellChromatic", chromaticIntensity);
    }

    /**
     * Triggers a radial shockwave originating at screen UV coordinates (0..1).
     */
    public void triggerShockwave(float normCenterX, float normCenterY, float duration, float strength) {
        this.shockwaveCenter.set(normCenterX, normCenterY);
        this.shockwaveDuration = Math.max(0.1f, duration);
        this.shockwaveTimer = 0f;
        this.shockwaveProgress = 0.001f;
        this.shockwaveStrength = strength;
    }

    /**
     * Triggers an edge vignette tint (e.g. cyan for frost, orange for heat, purple for necrotic).
     */
    public void triggerVignette(Color color, float intensity, float duration) {
        this.vignetteColor.set(color);
        this.vignetteTargetIntensity = intensity;
        this.vignetteIntensity = intensity;
        this.vignetteDuration = Math.max(0.1f, duration);
        this.vignetteTimer = 0f;
    }

    /**
     * Triggers horizontal line glitch distortion (Misty Step, Teleport).
     */
    public void triggerGlitch(float intensity, float duration) {
        this.glitchFactor = intensity;
        this.glitchDuration = Math.max(0.1f, duration);
        this.glitchTimer = 0f;
    }

    /**
     * Triggers chromatic aberration RGB shift.
     */
    public void triggerChromaticAberration(float intensity, float duration) {
        this.chromaticIntensity = intensity;
        this.chromaticDuration = Math.max(0.1f, duration);
        this.chromaticTimer = 0f;
    }

    /**
     * Automatically dispatches screen effects for a given visual archetype.
     */
    public void triggerArchetypeFX(VisualArchetype archetype, float normCenterX, float normCenterY) {
        if (archetype == null) return;

        if (archetype.hasShockwave()) {
            float strength = (archetype == VisualArchetype.EXPLOSIVE_BURST || archetype == VisualArchetype.THUNDER_CONCUSSION) ? 0.09f : 0.05f;
            float dur = (archetype == VisualArchetype.THUNDER_CONCUSSION) ? 0.6f : 0.45f;
            triggerShockwave(normCenterX, normCenterY, dur, strength);
        }

        if (archetype.hasVignette()) {
            triggerVignette(archetype.getPrimaryColor(), 0.85f, 0.65f);
        }

        if (archetype.hasGlitch()) {
            triggerGlitch(0.8f, 0.35f);
            triggerChromaticAberration(0.8f, 0.4f);
        }
    }

    /**
     * Renders the FBO texture using the standalone spell shader when CRT filter is OFF.
     */
    public void renderModern(SpriteBatch batch, Texture fboTexture, float x, float y, float width, float height) {
        if (isShaderCompiled && spellShader != null) {
            batch.setShader(spellShader);
            spellShader.setUniformf("u_time", elapsedTime);
            applyUniforms(spellShader, elapsedTime);
        } else {
            batch.setShader(null);
        }
        batch.draw(fboTexture, x, y, width, height, 0, 0, 1, 1);
        batch.setShader(null);
    }

    public boolean isActive() {
        return shockwaveProgress > 0f || vignetteIntensity > 0.01f || glitchFactor > 0.01f || chromaticIntensity > 0.01f;
    }

    public ShaderProgram getSpellShader() {
        return spellShader;
    }

    @Override
    public void dispose() {
        if (spellShader != null) {
            spellShader.dispose();
            spellShader = null;
        }
    }
}
