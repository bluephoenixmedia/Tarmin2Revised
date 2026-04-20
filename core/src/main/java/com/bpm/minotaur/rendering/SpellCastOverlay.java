package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Renders a first-person hand-raise animation used when the player casts a spell.
 * The hand extends from the lower-right corner toward the center of the screen,
 * as if the player is reaching out toward a target.
 *
 * Uses ShapeRenderer pixel-block art so it works in both retro and modern modes.
 */
public class SpellCastOverlay {

    // ASCII sprite: open palm facing away from the player (toward the monster).
    // Read top-to-bottom, '#' = filled pixel block.
    private static final String[] CAST_HAND_SPRITE = {
        "# # #",
        "#####",
        "#####",
        " ### ",
        "  #  ",
        "  #  "
    };

    private boolean active = false;
    private float timer = 0f;
    private float duration = 0.8f;
    private Color handColor = new Color(0.82f, 0.68f, 0.55f, 1f); // Flesh tone

    /** Trigger the hand-raise animation with default flesh-tone color. */
    public void triggerCast(float duration) {
        this.active = true;
        this.timer = 0f;
        this.duration = duration;
        this.handColor.set(0.82f, 0.68f, 0.55f, 1f);
    }

    /** Trigger the hand-raise with a custom tint (e.g. blood red for Drain). */
    public void triggerCast(float duration, Color tint) {
        triggerCast(duration);
        this.handColor.set(tint);
    }

    public void update(float delta) {
        if (!active) return;
        timer += delta;
        if (timer >= duration) {
            active = false;
            timer = 0f;
        }
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Renders the casting hand using ShapeRenderer filled rectangles.
     * Must be called between shapeRenderer.begin() and shapeRenderer.end().
     */
    public void render(ShapeRenderer shapeRenderer, Viewport viewport) {
        if (!active) return;

        float progress = timer / duration;

        // Three-phase animation:
        //   0.00 – 0.35 : raise (hand slides in from lower-right, eases in)
        //   0.35 – 0.65 : hold extended (slight tremor)
        //   0.65 – 1.00 : retract (eases out back to lower-right)
        float extendT;
        if (progress < 0.35f) {
            float t = progress / 0.35f;
            extendT = t * t; // ease in
        } else if (progress < 0.65f) {
            extendT = 1.0f;
        } else {
            float t = (progress - 0.65f) / 0.35f;
            extendT = 1.0f - (t * t); // ease out
        }

        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();

        // Anchor positions in screen space (relative to viewport)
        float retractX = w * 0.76f;
        float retractY = h * -0.08f; // below screen

        float extendX  = w * 0.40f;
        float extendY  = h * 0.16f;

        float currentX = MathUtils.lerp(retractX, extendX, extendT);
        float currentY = MathUtils.lerp(retractY, extendY, extendT);

        // Subtle tremor during hold phase (casting effort)
        if (progress > 0.35f && progress < 0.65f) {
            float tremor = MathUtils.sin(timer * 28f) * 2.5f;
            currentX += tremor;
            currentY += tremor * 0.4f;
        }

        // Sprite dimensions scaled to a fraction of the screen
        int rows = CAST_HAND_SPRITE.length;
        int cols  = CAST_HAND_SPRITE[0].length();
        float spriteH = h * 0.20f;
        float spriteW = w * 0.12f;
        float pixW = spriteW / cols;
        float pixH = spriteH / rows;

        // Alpha: fades in as the hand extends, fades out as it retracts
        float alpha = extendT * 0.92f;

        shapeRenderer.setColor(handColor.r, handColor.g, handColor.b, alpha);

        for (int row = 0; row < rows; row++) {
            String line = CAST_HAND_SPRITE[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    float px = currentX + col * pixW;
                    float py = currentY + (rows - 1 - row) * pixH; // flip y so row 0 is top
                    shapeRenderer.rect(px, py, pixW, pixH);
                }
            }
        }
    }
}
