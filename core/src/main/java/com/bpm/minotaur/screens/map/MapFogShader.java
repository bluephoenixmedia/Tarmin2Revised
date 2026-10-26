package com.bpm.minotaur.screens.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Manages the GLSL shader pass for the expedition map's volumetric, scrolling fog of war.
 *
 * <p>Binds the 4 procedural noise textures and the world-space knowledge mask texture,
 * drawing a fullscreen quad over the 720x448 low-res frame buffer.
 */
public final class MapFogShader implements Disposable {

    private static final String TAG = "MapFogShader";

    private ShaderProgram shader;
    private Texture dummyWhiteTexture;
    private boolean compiled = false;

    public MapFogShader() {
        initShader();
    }

    public boolean isCompiled() {
        return compiled;
    }

    public ShaderProgram getShader() {
        return shader;
    }

    private void initShader() {
        if (Gdx.files == null) {
            compiled = false;
            return;
        }
        try {
            ShaderProgram.pedantic = false;
            shader = new ShaderProgram(
                    Gdx.files.internal("shaders/map_fog.vert"),
                    Gdx.files.internal("shaders/map_fog.frag")
            );
            if (shader.isCompiled()) {
                compiled = true;
            } else {
                if (Gdx.app != null) {
                    Gdx.app.error(TAG, "Map fog shader compilation failed:\n" + shader.getLog());
                }
                compiled = false;
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error(TAG, "Failed to load map fog shader: " + e.getMessage());
            }
            compiled = false;
        }

        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.drawPixel(0, 0, 0xFFFFFFFF);
        dummyWhiteTexture = new Texture(p);
        p.dispose();
    }

    /**
     * Renders the fog quad over the low-res buffer (720x448).
     */
    public void render(SpriteBatch batch, MapNoiseTextures noise, MapKnowledgeMask mask,
                       float time, float camX, float camY, float camZoom, float camRot,
                       float origX, float origY, float fogStrength, boolean isCave,
                       float width, float height) {
        Texture maskTexture = mask == null ? null : mask.getMaskTexture();
        if (!compiled || shader == null || noise == null || maskTexture == null) {
            return;
        }

        batch.setShader(shader);
        shader.bind();

        noise.getTexBody().bind(1);
        noise.getTexShade().bind(2);
        noise.getTexHi().bind(3);
        noise.getTexWisp().bind(4);
        maskTexture.bind(5);
        dummyWhiteTexture.bind(0);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

        shader.setUniformi("u_texture", 0);
        shader.setUniformi("u_texBody", 1);
        shader.setUniformi("u_texShade", 2);
        shader.setUniformi("u_texHi", 3);
        shader.setUniformi("u_texWisp", 4);
        shader.setUniformi("u_mask", 5);

        shader.setUniformf("u_time", time);
        shader.setUniformf("u_camPos", camX, camY);
        shader.setUniformf("u_camZoom", camZoom);
        shader.setUniformf("u_camRot", camRot);
        shader.setUniformf("u_origin", origX, origY);
        shader.setUniformf("u_fogStrength", fogStrength);
        shader.setUniformf("u_isCave", isCave ? 1.0f : 0.0f);
        shader.setUniformf("u_resolution", width, height);
        shader.setUniformf("u_maskOrigin", mask.getOriginX(), mask.getOriginY());
        shader.setUniformf("u_maskSpan", (float) mask.getSpan());

        batch.draw(dummyWhiteTexture, 0, 0, width, height);

        batch.setShader(null);
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (dummyWhiteTexture != null) {
            dummyWhiteTexture.dispose();
            dummyWhiteTexture = null;
        }
        compiled = false;
    }
}
