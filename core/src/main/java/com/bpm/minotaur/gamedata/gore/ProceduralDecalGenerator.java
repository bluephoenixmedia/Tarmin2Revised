package com.bpm.minotaur.gamedata.gore;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/**
 * Generates procedural Pixmap-based wound brush textures at startup for zero-friction
 * decal rasterization (slashes, puncture cavities, bone fractures, scorches, and cut seams).
 */
public class ProceduralDecalGenerator implements Disposable {

    private static ProceduralDecalGenerator instance;

    private Texture slashTexture;
    private Texture punctureTexture;
    private Texture crushTexture;
    private Texture scorchTexture;
    private Texture seamTexture;

    private TextureRegion slashRegion;
    private TextureRegion punctureRegion;
    private TextureRegion crushRegion;
    private TextureRegion scorchRegion;
    private TextureRegion seamRegion;

    public static synchronized ProceduralDecalGenerator getInstance() {
        if (instance == null) {
            instance = new ProceduralDecalGenerator();
        }
        return instance;
    }

    public ProceduralDecalGenerator() {
        if (Gdx.gl != null) {
            try {
                generateTextures();
            } catch (Throwable t) {
                if (Gdx.app != null) {
                    Gdx.app.error("ProceduralDecalGenerator", "Failed to generate procedural wound textures: " + t.getMessage());
                }
            }
        }
    }

    private void generateTextures() {
        // 1. SLASH BRUSH (128 x 32)
        Pixmap slashPm = new Pixmap(128, 32, Pixmap.Format.RGBA8888);
        slashPm.setColor(0, 0, 0, 0);
        slashPm.fill();

        int sw = slashPm.getWidth();
        int sh = slashPm.getHeight();
        float sHalfW = sw * 0.5f;
        float sHalfH = sh * 0.5f;

        for (int x = 0; x < sw; x++) {
            float nx = (x - sHalfW) / sHalfW; // -1 to 1
            float taper = 1.0f - Math.abs(nx); // 0 at ends, 1 in center
            if (taper < 0f) taper = 0f;
            float maxThickness = sHalfH * 0.75f * taper;

            for (int y = 0; y < sh; y++) {
                float ny = Math.abs(y - sHalfH);
                if (ny <= maxThickness) {
                    float distNorm = ny / Math.max(0.001f, maxThickness);
                    float alpha = (1.0f - distNorm) * taper;
                    // Dark rim, bright core
                    float r = 0.85f + 0.15f * (1.0f - distNorm);
                    float g = 0.08f * (1.0f - distNorm);
                    float b = 0.08f * (1.0f - distNorm);
                    slashPm.setColor(r, g, b, alpha);
                    slashPm.drawPixel(x, y);
                }
            }
        }
        slashTexture = new Texture(slashPm);
        slashTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        slashRegion = new TextureRegion(slashTexture);
        slashPm.dispose();

        // 2. PUNCTURE BRUSH (48 x 48)
        Pixmap puncPm = new Pixmap(48, 48, Pixmap.Format.RGBA8888);
        puncPm.setColor(0, 0, 0, 0);
        puncPm.fill();

        int pw = puncPm.getWidth();
        int ph = puncPm.getHeight();
        float pcx = pw * 0.5f;
        float pcy = ph * 0.5f;
        float pMaxR = pw * 0.45f;

        for (int x = 0; x < pw; x++) {
            for (int y = 0; y < ph; y++) {
                float dx = x - pcx;
                float dy = y - pcy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d <= pMaxR) {
                    float norm = d / pMaxR; // 0 at center, 1 at edge
                    float alpha = 1.0f - norm;
                    // Deep dark center, crimson rim
                    float r = (norm < 0.35f) ? 0.3f : 0.8f;
                    float g = (norm < 0.35f) ? 0.02f : 0.06f;
                    float b = (norm < 0.35f) ? 0.02f : 0.06f;
                    puncPm.setColor(r, g, b, alpha);
                    puncPm.drawPixel(x, y);
                }
            }
        }
        punctureTexture = new Texture(puncPm);
        punctureTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        punctureRegion = new TextureRegion(punctureTexture);
        puncPm.dispose();

        // 3. CRUSH BRUSH (64 x 64) - Radial fracture / contusion
        Pixmap crushPm = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        crushPm.setColor(0, 0, 0, 0);
        crushPm.fill();

        int cw = crushPm.getWidth();
        int ch = crushPm.getHeight();
        float ccx = cw * 0.5f;
        float ccy = ch * 0.5f;

        // Base hematoma bruise
        for (int x = 0; x < cw; x++) {
            for (int y = 0; y < ch; y++) {
                float dx = x - ccx;
                float dy = y - ccy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < cw * 0.45f) {
                    float a = (1.0f - d / (cw * 0.45f)) * 0.65f;
                    crushPm.setColor(0.45f, 0.05f, 0.10f, a);
                    crushPm.drawPixel(x, y);
                }
            }
        }

        // Radiating crack lines
        MathUtils.random.setSeed(1337);
        for (int i = 0; i < 7; i++) {
            float angle = i * (MathUtils.PI2 / 7f) + MathUtils.random(-0.2f, 0.2f);
            float length = cw * MathUtils.random(0.35f, 0.48f);
            float ex = ccx + MathUtils.cos(angle) * length;
            float ey = ccy + MathUtils.sin(angle) * length;
            crushPm.setColor(0.2f, 0.02f, 0.02f, 0.95f);
            crushPm.drawLine((int) ccx, (int) ccy, (int) ex, (int) ey);
        }
        crushTexture = new Texture(crushPm);
        crushTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        crushRegion = new TextureRegion(crushTexture);
        crushPm.dispose();

        // 4. SCORCH BRUSH (64 x 64) - Charred burned ring
        Pixmap scorchPm = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        scorchPm.setColor(0, 0, 0, 0);
        scorchPm.fill();

        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                float dx = x - 32f;
                float dy = y - 32f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 30f) {
                    float a = (1.0f - d / 30f);
                    float noise = MathUtils.random(0.85f, 1.0f);
                    scorchPm.setColor(0.12f * noise, 0.08f * noise, 0.06f * noise, a * 0.9f);
                    scorchPm.drawPixel(x, y);
                }
            }
        }
        scorchTexture = new Texture(scorchPm);
        scorchTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        scorchRegion = new TextureRegion(scorchTexture);
        scorchPm.dispose();

        // 5. SEAM RIBBON (32 x 8) - Flesh rim with marrow core
        Pixmap seamPm = new Pixmap(32, 8, Pixmap.Format.RGBA8888);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 8; y++) {
                float edgeDist = Math.min(y, 7 - y) / 3.5f; // 0 at edges, 1 at center
                if (edgeDist > 0.6f) {
                    // Bone marrow core
                    seamPm.setColor(0.88f, 0.85f, 0.78f, 1.0f);
                } else {
                    // Crimson raw muscle / meat
                    seamPm.setColor(0.65f, 0.08f, 0.08f, 1.0f);
                }
                seamPm.drawPixel(x, y);
            }
        }
        seamTexture = new Texture(seamPm);
        seamTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        seamRegion = new TextureRegion(seamTexture);
        seamPm.dispose();
    }

    public TextureRegion getRegionForType(WoundDecal.WoundType type) {
        switch (type) {
            case PUNCTURE:
                return punctureRegion;
            case CRUSH:
                return crushRegion;
            case SCORCH:
                return scorchRegion;
            case SLASH:
            default:
                return slashRegion;
        }
    }

    public TextureRegion getSeamRegion() {
        return seamRegion;
    }

    @Override
    public void dispose() {
        if (slashTexture != null) slashTexture.dispose();
        if (punctureTexture != null) punctureTexture.dispose();
        if (crushTexture != null) crushTexture.dispose();
        if (scorchTexture != null) scorchTexture.dispose();
        if (seamTexture != null) seamTexture.dispose();
    }
}
