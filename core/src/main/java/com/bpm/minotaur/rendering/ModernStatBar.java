package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;

/**
 * High-readability visual gauge bar with textured stone track,
 * smooth fill, top gloss highlight, and centered shadow text.
 */
public class ModernStatBar extends Actor {

    private final String labelName;
    private float currentValue = 100f;
    private float maxValue = 100f;
    private final Color barColor;
    private final Color trackColor;
    private final HudSkin skin;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    private boolean isTemperature = false;
    private boolean showPercent = false;
    private String customText = null;

    public ModernStatBar(String labelName, Color barColor, HudSkin skin) {
        this.labelName = labelName;
        this.barColor = new Color(barColor);
        this.trackColor = new Color(0.12f, 0.14f, 0.16f, 0.95f);
        this.skin = skin;
        this.font = skin.getFontSmall();
    }

    public void setValue(float current, float max) {
        this.currentValue = current;
        this.maxValue = Math.max(1f, max);
    }

    public void setBarColor(Color color) {
        this.barColor.set(color);
    }

    public void setTemperatureMode(boolean isTemp) {
        this.isTemperature = isTemp;
    }

    public void setShowPercent(boolean show) {
        this.showPercent = show;
    }

    public void setCustomText(String text) {
        this.customText = text;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        // 1. Draw Recessed Gauge Track
        skin.getGaugeTrack().draw(batch, x, y, w, h);

        // 2. Calculate Fill
        float fillRatio = MathUtils.clamp(currentValue / maxValue, 0f, 1f);
        float pad = 2f;
        float innerW = Math.max(0f, (w - pad * 2) * fillRatio);
        float innerH = Math.max(0f, h - pad * 2);

        if (innerW > 0 && innerH > 0 && skin.getWhitePixel() != null) {
            // Main bar fill
            batch.setColor(barColor.r, barColor.g, barColor.b, barColor.a * parentAlpha);
            batch.draw(skin.getWhitePixel(), x + pad, y + pad, innerW, innerH);

            // Gloss highlight on top half
            batch.setColor(1f, 1f, 1f, 0.18f * parentAlpha);
            batch.draw(skin.getWhitePixel(), x + pad, y + pad + innerH * 0.55f, innerW, innerH * 0.45f);
        }

        // 3. Format Centered Text
        String text;
        if (customText != null) {
            text = customText;
        } else if (isTemperature) {
            float fahrenheit = (currentValue * 9.0f / 5.0f) + 32.0f;
            text = String.format("%s: %.1f°F", labelName, fahrenheit);
        } else if (showPercent) {
            int pct = Math.round(fillRatio * 100f);
            text = String.format("%s: %.0f/%.0f (%d%%)", labelName, currentValue, maxValue, pct);
        } else {
            text = String.format("%s: %.0f / %.0f", labelName, currentValue, maxValue);
        }

        layout.setText(font, text);
        float textX = x + (w - layout.width) / 2f;
        float textY = y + (h + layout.height) / 2f - 1f;

        // Shadow
        font.setColor(0f, 0f, 0f, 0.85f * parentAlpha);
        font.draw(batch, text, textX + 1f, textY - 1f);

        // Text Foreground
        font.setColor(Color.WHITE);
        font.draw(batch, text, textX, textY);
    }
}
