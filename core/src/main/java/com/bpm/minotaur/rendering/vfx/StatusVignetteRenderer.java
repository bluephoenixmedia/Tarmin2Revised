package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.injury.InjuryManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;

/**
 * Renders atmospheric screen-edge vignettes for active player bodily conditions
 * (Bleed, Cold/Hypothermia, Poison, Fever/Illness, Starvation, Berzerk, Iron Skin).
 * 
 * Enforces spatial zoning (corners for cold, vertical sides for bleed, horizontal borders for poison)
 * and hard-clamps the total combined opacity across all active layers to 45% maximum so that
 * the player's central field of view and monsters are never occluded.
 */
public class StatusVignetteRenderer {

    public static final float MAX_TOTAL_VIGNETTE_ALPHA = 0.45f;

    private float timer = 0f;

    public void update(float delta) {
        timer += delta;
    }

    public void render(ShapeRenderer shapeRenderer, Viewport viewport, Player player) {
        if (player == null || viewport == null || shapeRenderer == null) return;

        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();

        // 1. Calculate raw target intensities
        float bleedWeight = getBleedWeight(player);
        float coldWeight = getColdWeight(player);
        float poisonWeight = getPoisonWeight(player);
        float feverWeight = getFeverWeight(player);
        float starveWeight = getStarveWeight(player);
        float berzerkWeight = getBerzerkWeight(player);
        boolean hasIronSkin = player.getStatusManager() != null && player.getStatusManager().hasEffect(StatusEffectType.HARDENED);

        float totalWeight = bleedWeight + coldWeight + poisonWeight + feverWeight + starveWeight + berzerkWeight;
        if (totalWeight <= 0.001f && !hasIronSkin) {
            return; // No active overlays
        }

        // 2. Normalization & Alpha Clamping (max 45% total edge opacity)
        float scale = 1.0f;
        if (totalWeight > MAX_TOTAL_VIGNETTE_ALPHA) {
            scale = MAX_TOTAL_VIGNETTE_ALPHA / totalWeight;
        }

        float bleedAlpha = bleedWeight * scale;
        float coldAlpha = coldWeight * scale;
        float poisonAlpha = poisonWeight * scale;
        float feverAlpha = feverWeight * scale;
        float starveAlpha = starveWeight * scale;
        float berzerkAlpha = berzerkWeight * scale;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        // 3. Render Layers
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // --- LAYER A: Starvation / Dehydration (Dark tunnel-vision perimeter) ---
        if (starveAlpha > 0.01f) {
            float edgeH = h * 0.08f;
            float edgeW = w * 0.06f;
            shapeRenderer.setColor(0.02f, 0.02f, 0.02f, starveAlpha);
            shapeRenderer.rect(0, 0, w, edgeH); // bottom
            shapeRenderer.rect(0, h - edgeH, w, edgeH); // top
            shapeRenderer.rect(0, 0, edgeW, h); // left
            shapeRenderer.rect(w - edgeW, 0, edgeW, h); // right
        }

        // --- LAYER B: Fever & Infection (Amber-yellow hazy peripheral pulse) ---
        if (feverAlpha > 0.01f) {
            float pulse = 0.7f + 0.3f * MathUtils.sin(timer * 2.0f);
            float a = feverAlpha * pulse;
            float edgeH = h * 0.06f;
            float edgeW = w * 0.05f;
            shapeRenderer.setColor(0.92f, 0.75f, 0.20f, a);
            shapeRenderer.rect(0, 0, w, edgeH);
            shapeRenderer.rect(0, h - edgeH, w, edgeH);
            shapeRenderer.rect(0, 0, edgeW, h);
            shapeRenderer.rect(w - edgeW, 0, edgeW, h);
        }

        // --- LAYER C: Poison & High Toxicity (Viridian/Violet top & bottom creep) ---
        if (poisonAlpha > 0.01f) {
            float pulse = 0.65f + 0.35f * MathUtils.sin(timer * 2.6f);
            float a = poisonAlpha * pulse;
            float barH = h * 0.07f;
            shapeRenderer.setColor(0.20f, 0.75f, 0.35f, a);
            shapeRenderer.rect(0, 0, w, barH);
            shapeRenderer.rect(0, h - barH, w, barH);

            // Subtle purple inner mist
            shapeRenderer.setColor(0.55f, 0.15f, 0.65f, a * 0.45f);
            shapeRenderer.rect(0, barH * 0.5f, w, barH * 0.5f);
            shapeRenderer.rect(0, h - barH, w, barH * 0.5f);
        }

        // --- LAYER D: Bleed (Pulsing crimson heartbeat along left & right sides) ---
        if (bleedAlpha > 0.01f) {
            boolean criticalHp = (player.getStats() != null && player.getCurrentHP() <= player.getStats().getMaxHP() * 0.25f);
            float pulseSpeed = criticalHp ? 7.5f : 4.0f;
            float pulse = 0.45f + 0.55f * Math.max(0f, MathUtils.sin(timer * pulseSpeed));
            float a = bleedAlpha * pulse;
            float sideW = w * 0.06f;

            // Deep crimson edge bands
            shapeRenderer.setColor(0.76f, 0.12f, 0.12f, a);
            shapeRenderer.rect(0, 0, sideW, h);
            shapeRenderer.rect(w - sideW, 0, sideW, h);

            // Dark blood clots along rims
            shapeRenderer.setColor(0.35f, 0.03f, 0.03f, a * 0.65f);
            shapeRenderer.rect(0, 0, sideW * 0.4f, h);
            shapeRenderer.rect(w - sideW * 0.4f, 0, sideW * 0.4f, h);
        }

        // --- LAYER E: Cold & Hypothermia (Icy cyan frost in the 4 corners) ---
        if (coldAlpha > 0.01f) {
            float breathPulse = 0.8f + 0.2f * MathUtils.sin(timer * 1.8f);
            float a = coldAlpha * breathPulse;
            float cornerSize = Math.min(w, h) * 0.20f;

            shapeRenderer.setColor(0.68f, 0.88f, 1.0f, a);
            // 4 corner triangles
            shapeRenderer.triangle(0, 0, cornerSize, 0, 0, cornerSize); // Bottom-left
            shapeRenderer.triangle(w, 0, w - cornerSize, 0, w, cornerSize); // Bottom-right
            shapeRenderer.triangle(0, h, cornerSize, h, 0, h - cornerSize); // Top-left
            shapeRenderer.triangle(w, h, w - cornerSize, h, w, h - cornerSize); // Top-right

            // Frost outer rim
            shapeRenderer.setColor(0.85f, 0.95f, 1.0f, a * 0.5f);
            float rim = cornerSize * 0.45f;
            shapeRenderer.triangle(0, 0, rim, 0, 0, rim);
            shapeRenderer.triangle(w, 0, w - rim, 0, w, rim);
            shapeRenderer.triangle(0, h, rim, h, 0, h - rim);
            shapeRenderer.triangle(w, h, w - rim, h, w, h - rim);
        }

        // --- LAYER F: Berzerk Bloodlust (Intense perimeter crimson surge) ---
        if (berzerkAlpha > 0.01f) {
            float pulse = 0.75f + 0.25f * MathUtils.sin(timer * 5.0f);
            float a = berzerkAlpha * pulse;
            float rimH = h * 0.06f;
            float rimW = w * 0.05f;
            shapeRenderer.setColor(0.95f, 0.08f, 0.08f, a);
            shapeRenderer.rect(0, 0, w, rimH);
            shapeRenderer.rect(0, h - rimH, w, rimH);
            shapeRenderer.rect(0, 0, rimW, h);
            shapeRenderer.rect(w - rimW, 0, rimW, h);
        }

        shapeRenderer.end();

        // --- LAYER G: Iron Skin (Hardened) Stylized Golden Runic Corner Brackets ---
        if (hasIronSkin) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            Gdx.gl.glLineWidth(3f);
            shapeRenderer.setColor(0.88f, 0.75f, 0.25f, 0.85f); // Bright Antique Gold

            float bLen = 50f;
            float m = 4f;
            // Top-left bracket
            shapeRenderer.line(m, h - m, m + bLen, h - m);
            shapeRenderer.line(m, h - m, m, h - m - bLen);

            // Top-right bracket
            shapeRenderer.line(w - m, h - m, w - m - bLen, h - m);
            shapeRenderer.line(w - m, h - m, w - m, h - m - bLen);

            // Bottom-left bracket
            shapeRenderer.line(m, m, m + bLen, m);
            shapeRenderer.line(m, m, m, m + bLen);

            // Bottom-right bracket
            shapeRenderer.line(w - m, m, w - m - bLen, m);
            shapeRenderer.line(w - m, m, w - m, m + bLen);

            // Thin border ring
            Gdx.gl.glLineWidth(1f);
            shapeRenderer.setColor(0.65f, 0.52f, 0.18f, 0.50f);
            shapeRenderer.rect(m + 2, m + 2, w - (m * 2 + 4), h - (m * 2 + 4));

            shapeRenderer.end();
            Gdx.gl.glLineWidth(1f);
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // Weight calculations (0.0 to 1.0)
    public float getBleedWeight(Player player) {
        if (player.getInjuryManager() == null || !player.getInjuryManager().isBleeding()) return 0f;
        int bleedCount = 0;
        for (com.bpm.minotaur.gamedata.injury.InjuryRecord r : player.getInjuryManager().getInjuries().values()) {
            if (r != null && r.isBleeding()) {
                bleedCount += r.getSeverity();
            }
        }
        return MathUtils.clamp(0.18f + (bleedCount * 0.08f), 0.18f, 0.40f);
    }

    public float getColdWeight(Player player) {
        if (player.getStats() == null) return 0f;
        float temp = player.getStats().getBodyTemperature();
        if (temp >= 35.0f) return 0f;

        if (temp < 32.0f) {
            // Hypothermia tier
            float severity = MathUtils.clamp((32.0f - temp) / 2.0f, 0f, 1f);
            return 0.25f + 0.15f * severity;
        } else {
            // Chilled tier (32.0C - 35.0C)
            float severity = MathUtils.clamp((35.0f - temp) / 3.0f, 0f, 1f);
            return 0.12f + 0.12f * severity;
        }
    }

    public float getPoisonWeight(Player player) {
        boolean hasPoison = player.getStatusManager() != null && player.getStatusManager().hasEffect(StatusEffectType.POISONED);
        float tox = player.getStats() != null ? player.getStats().getToxicity() : 0f;

        if (hasPoison) {
            return 0.28f;
        } else if (tox > 50f) {
            return 0.15f + ((tox - 50f) / 50f) * 0.15f;
        }
        return 0f;
    }

    public float getFeverWeight(Player player) {
        boolean isIll = player.getInjuryManager() != null && player.getInjuryManager().getIllnessStage().isIll();
        boolean hasFever = player.getStatusManager() != null &&
                (player.getStatusManager().hasEffect(StatusEffectType.FEVER) ||
                 player.getStatusManager().hasEffect(StatusEffectType.SICK) ||
                 player.getStatusManager().hasEffect(StatusEffectType.VIRUS));

        if (isIll || hasFever) {
            return 0.22f;
        }
        return 0f;
    }

    public float getStarveWeight(Player player) {
        if (player.getStats() == null) return 0f;
        PlayerStats stats = player.getStats();
        boolean starving = stats.getSatietyFloat() <= 0f;
        boolean parched = stats.getHydrationFloat() <= 0f;

        if (starving && parched) return 0.32f;
        if (starving || parched) return 0.22f;
        return 0f;
    }

    public float getBerzerkWeight(Player player) {
        if (player.getStatusManager() != null && player.getStatusManager().hasEffect(StatusEffectType.BERZERK)) {
            return 0.35f;
        }
        return 0f;
    }
}
