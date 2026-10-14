package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Modern post-processing pass applying AMD Contrast-Adaptive Sharpening (CAS),
 * cinematic ACES filmic tonemapping, and microscopic dither film grain to dissolve
 * 8-bit gradient banding in dark subterranean atmospheres.
 */
public class FilmicCasPostProcessor implements Disposable {

    private static final String TAG = "FilmicCasPostProcessor";

    private ShaderProgram shader;
    private boolean isCompiled = false;

    private float sharpness = 0.45f;
    private float grain = 0.0f;
    private float vignetteStrength = 0.85f;
    private float contrast = 1.22f;

    public FilmicCasPostProcessor() {
        initShader();
    }

    private void initShader() {
        if (Gdx.files == null) {
            isCompiled = false;
            return;
        }
        try {
            ShaderProgram.pedantic = false;
            shader = new ShaderProgram(
                    Gdx.files.internal("shaders/filmic_cas.vert"),
                    Gdx.files.internal("shaders/filmic_cas.frag")
            );
            if (shader.isCompiled()) {
                isCompiled = true;
            } else {
                if (Gdx.app != null) {
                    Gdx.app.error(TAG, "Shader compilation failed:\n" + shader.getLog());
                }
                isCompiled = false;
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error(TAG, "Failed to load filmic CAS shader: " + e.getMessage());
            }
            isCompiled = false;
        }
    }

    /**
     * Renders the scene texture through the CAS and ACES tonemapping post-process pipeline.
     */
    public void render(SpriteBatch batch, Texture sceneTexture, float x, float y, float width, float height, float time, SpellPostProcessor spellPostProcessor) {
        if (!isCompiled || shader == null) {
            batch.setShader(null);
            batch.draw(sceneTexture, x, y, width, height, 0, 0, 1, 1);
            return;
        }

        batch.setShader(shader);
        shader.setUniformf("u_texelSize", 1.0f / width, 1.0f / height);
        shader.setUniformf("u_sharpness", sharpness);
        shader.setUniformf("u_grain", grain);
        shader.setUniformf("u_vignetteStrength", vignetteStrength);
        shader.setUniformf("u_contrast", contrast);
        shader.setUniformf("u_time", time);

        if (spellPostProcessor != null) {
            spellPostProcessor.applyUniforms(shader, time);
        } else {
            // Clear spell uniforms to safe defaults
            shader.setUniformf("u_glitchFactor", 0f);
            shader.setUniformf("u_shockwaveProgress", 0f);
            shader.setUniformf("u_spellChromatic", 0f);
            shader.setUniformf("u_vignetteIntensity", 0f);
            shader.setUniformf("u_sonarProgress", 0f);
            shader.setUniformf("u_wisdomIrisIntensity", 0f);
        }

        batch.draw(sceneTexture, x, y, width, height, 0, 0, 1, 1);
        batch.setShader(null);
    }

    public boolean isCompiled() {
        return isCompiled;
    }

    public float getSharpness() {
        return sharpness;
    }

    public void setSharpness(float sharpness) {
        this.sharpness = sharpness;
    }

    public float getGrain() {
        return grain;
    }

    public void setGrain(float grain) {
        this.grain = grain;
    }

    public float getVignetteStrength() {
        return vignetteStrength;
    }

    public void setVignetteStrength(float vignetteStrength) {
        this.vignetteStrength = vignetteStrength;
    }

    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
            isCompiled = false;
        }
    }
}
