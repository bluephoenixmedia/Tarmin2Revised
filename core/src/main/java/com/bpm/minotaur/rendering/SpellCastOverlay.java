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
 * During the hold/tremor phase, an elemental mystic rune corresponding to the
 * spell's school of magic glows and pulses directly on the center of the palm.
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
    private final Color handColor = new Color(0.82f, 0.68f, 0.55f, 1f); // Flesh tone
    private final Color runeColor = new Color(0.35f, 0.85f, 1.0f, 1f);  // Glowing rune
    private String runeSchool = "EVOCATION";

    /** Trigger the hand-raise animation with default flesh-tone color. */
    public void triggerCast(float duration) {
        triggerCast(duration, new Color(0.82f, 0.68f, 0.55f, 1f), "EVOCATION", new Color(1f, 0.8f, 0.2f, 1f));
    }

    /** Trigger the hand-raise with a custom hand tint and school rune. */
    public void triggerCast(float duration, Color tint, String runeSchool) {
        Color rColor = (tint != null) ? tint.cpy() : new Color(1f, 0.85f, 0.3f, 1f);
        triggerCast(duration, new Color(0.82f, 0.68f, 0.55f, 1f), runeSchool, rColor);
    }

    /** Full parameter trigger for hand-raise with custom hand color, school rune, and rune glow color. */
    public void triggerCast(float duration, Color handTint, String runeSchool, Color runeGlowColor) {
        this.active = true;
        this.timer = 0f;
        this.duration = Math.max(0.2f, duration);
        if (handTint != null) {
            this.handColor.set(handTint);
        } else {
            this.handColor.set(0.82f, 0.68f, 0.55f, 1f);
        }
        this.runeSchool = (runeSchool != null) ? runeSchool.toUpperCase() : "EVOCATION";
        if (runeGlowColor != null) {
            this.runeColor.set(runeGlowColor);
        } else {
            this.runeColor.set(1f, 0.85f, 0.3f, 1f);
        }
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
        //   0.35 – 0.65 : hold extended (slight tremor + glowing palm rune)
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
        int cols = CAST_HAND_SPRITE[0].length();
        float spriteH = h * 0.20f;
        float spriteW = w * 0.12f;
        float pixW = spriteW / cols;
        float pixH = spriteH / rows;

        // Alpha: fades in as the hand extends, fades out as it retracts
        float alpha = extendT * 0.92f;

        // 1. Draw Hand Palm
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

        // 2. Draw Palm Mystic Rune during hold/tremor phase
        if (progress >= 0.20f && progress <= 0.85f) {
            float holdProgress = (progress - 0.20f) / 0.65f;
            float runeFade = MathUtils.sin(holdProgress * MathUtils.PI); // 0 -> 1 -> 0
            float pulse = 0.85f + 0.15f * MathUtils.sin(timer * 24f);
            float finalRuneAlpha = alpha * runeFade * pulse;

            float palmCenterX = currentX + (cols * 0.5f) * pixW;
            float palmCenterY = currentY + (rows * 0.55f) * pixH;
            float runeUnit = Math.min(pixW, pixH) * 0.38f;

            renderSchoolRune(shapeRenderer, palmCenterX, palmCenterY, runeUnit, finalRuneAlpha);
        }
    }

    /**
     * Renders an authentic procedural retro pixel rune for the active School of Magic.
     */
    private void renderSchoolRune(ShapeRenderer shapeRenderer, float cx, float cy, float u, float alpha) {
        // Outer glowing aura
        shapeRenderer.setColor(runeColor.r, runeColor.g, runeColor.b, alpha * 0.35f);
        shapeRenderer.rect(cx - u * 3.5f, cy - u * 3.5f, u * 7f, u * 7f);

        // Core bright rune color
        shapeRenderer.setColor(runeColor.r, runeColor.g, runeColor.b, alpha);

        switch (runeSchool) {
            case "EVOCATION":
                // Radiant 8-pointed starburst / flare
                // Center 2x2 core
                shapeRenderer.rect(cx - u, cy - u, u * 2, u * 2);
                // Cross points
                shapeRenderer.rect(cx - u * 0.5f, cy + u, u, u * 2.5f);     // Top
                shapeRenderer.rect(cx - u * 0.5f, cy - u * 3.5f, u, u * 2.5f); // Bottom
                shapeRenderer.rect(cx - u * 3.5f, cy - u * 0.5f, u * 2.5f, u); // Left
                shapeRenderer.rect(cx + u, cy - u * 0.5f, u * 2.5f, u);     // Right
                // Diagonals
                shapeRenderer.rect(cx - u * 2.2f, cy + u * 1.5f, u * 1.2f, u * 1.2f);
                shapeRenderer.rect(cx + u * 1.0f, cy + u * 1.5f, u * 1.2f, u * 1.2f);
                shapeRenderer.rect(cx - u * 2.2f, cy - u * 2.7f, u * 1.2f, u * 1.2f);
                shapeRenderer.rect(cx + u * 1.0f, cy - u * 2.7f, u * 1.2f, u * 1.2f);
                break;

            case "ABJURATION":
                // Protective hexagonal aegis shield
                shapeRenderer.rect(cx - u * 2.5f, cy + u * 1.5f, u * 5f, u * 0.9f); // Top rim
                shapeRenderer.rect(cx - u * 2.5f, cy - u * 1.0f, u * 0.9f, u * 2.5f); // Left rim
                shapeRenderer.rect(cx + u * 1.6f, cy - u * 1.0f, u * 0.9f, u * 2.5f); // Right rim
                shapeRenderer.rect(cx - u * 1.5f, cy - u * 2.5f, u * 3.0f, u * 0.9f); // Bottom point
                shapeRenderer.rect(cx - u * 0.6f, cy - u * 1.2f, u * 1.2f, u * 2.4f); // Inner boss
                break;

            case "NECROMANCY":
                // Fractured ossuary skull glyph
                shapeRenderer.rect(cx - u * 2.0f, cy + u * 0.5f, u * 4.0f, u * 1.8f); // Brow
                shapeRenderer.rect(cx - u * 1.2f, cy - u * 1.5f, u * 2.4f, u * 1.5f); // Jaw
                // Crossbones below
                shapeRenderer.rect(cx - u * 2.8f, cy - u * 2.8f, u * 5.6f, u * 0.8f);
                shapeRenderer.rect(cx - u * 0.4f, cy - u * 3.4f, u * 0.8f, u * 2.0f);
                break;

            case "CONJURATION":
                // Swirling dimensional vortex / spiral
                shapeRenderer.rect(cx - u * 2.5f, cy + u * 1.8f, u * 4.5f, u * 0.8f);
                shapeRenderer.rect(cx + u * 1.2f, cy - u * 1.5f, u * 0.8f, u * 3.3f);
                shapeRenderer.rect(cx - u * 1.8f, cy - u * 1.5f, u * 3.0f, u * 0.8f);
                shapeRenderer.rect(cx - u * 1.8f, cy - u * 0.5f, u * 0.8f, u * 1.8f);
                shapeRenderer.rect(cx - u * 0.5f, cy - u * 0.5f, u, u); // Core singularity
                break;

            case "TRANSMUTATION":
                // Triangular alchemical knot
                shapeRenderer.rect(cx - u * 0.5f, cy + u * 2.2f, u, u); // Apex
                shapeRenderer.rect(cx - u * 2.5f, cy - u * 1.8f, u * 5.0f, u * 0.9f); // Base
                shapeRenderer.rect(cx - u * 2.0f, cy - u * 0.5f, u * 0.9f, u * 2.0f); // Left strut
                shapeRenderer.rect(cx + u * 1.1f, cy - u * 0.5f, u * 0.9f, u * 2.0f); // Right strut
                shapeRenderer.rect(cx - u * 0.5f, cy - u * 0.5f, u, u); // Center salt glyph
                break;

            case "DIVINATION":
                // Radiant all-seeing eye
                shapeRenderer.rect(cx - u * 2.8f, cy + u * 0.5f, u * 5.6f, u * 0.8f); // Upper lid
                shapeRenderer.rect(cx - u * 2.8f, cy - u * 0.5f, u * 5.6f, u * 0.8f); // Lower lid
                shapeRenderer.rect(cx - u * 0.8f, cy - u * 0.8f, u * 1.6f, u * 1.6f); // Pupil
                shapeRenderer.rect(cx - u * 0.4f, cy + u * 1.8f, u * 0.8f, u * 1.5f); // Upper sight ray
                shapeRenderer.rect(cx - u * 0.4f, cy - u * 2.8f, u * 0.8f, u * 1.5f); // Lower sight ray
                break;

            case "ENCHANTMENT":
                // Concentric hypnotic ripple rings
                shapeRenderer.rect(cx - u * 0.7f, cy - u * 0.7f, u * 1.4f, u * 1.4f); // Inner dot
                shapeRenderer.rect(cx - u * 2.0f, cy - u * 2.0f, u * 4.0f, u * 0.7f); // Bottom mid
                shapeRenderer.rect(cx - u * 2.0f, cy + u * 1.3f, u * 4.0f, u * 0.7f); // Top mid
                shapeRenderer.rect(cx - u * 2.0f, cy - u * 1.3f, u * 0.7f, u * 2.6f); // Left mid
                shapeRenderer.rect(cx + u * 1.3f, cy - u * 1.3f, u * 0.7f, u * 2.6f); // Right mid
                break;

            case "ILLUSION":
            default:
                // Shimmering fractured diamond / prism
                shapeRenderer.rect(cx - u * 0.5f, cy + u * 2.5f, u, u); // Top point
                shapeRenderer.rect(cx - u * 2.2f, cy - u * 0.5f, u, u); // Left point
                shapeRenderer.rect(cx + u * 1.2f, cy - u * 0.5f, u, u); // Right point
                shapeRenderer.rect(cx - u * 0.5f, cy - u * 2.5f, u, u); // Bottom point
                shapeRenderer.rect(cx - u * 1.2f, cy - u * 1.2f, u * 2.4f, u * 2.4f); // Inner gem
                break;
        }
    }
}
