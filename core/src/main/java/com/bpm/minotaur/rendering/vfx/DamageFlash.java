package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * A short red flash at the edges of the screen when the player is hurt.
 *
 * <p>Separate from the status vignettes, which show a lasting condition (bleeding, cold, poison)
 * and are capped as a whole. This is one blow landing: it appears at once, scaled to how much of
 * the player's health the blow took, and is gone in a third of a second. It only ever touches the
 * edges, so the middle of the screen is never hidden.
 */
public final class DamageFlash {

    public static final float DURATION = 0.35f;
    public static final float MAX_ALPHA = 0.45f;
    private static final float MIN_ALPHA = 0.14f;

    private float remaining;
    private float peak;

    /** @param lostShare the share of maximum health this blow took (0.2 = a fifth). */
    public void trigger(float lostShare) {
        float strength = Math.max(MIN_ALPHA, Math.min(MAX_ALPHA, MIN_ALPHA + lostShare * 1.2f));
        // A second blow never dims a flash that is still showing.
        float current = alpha();
        peak = Math.max(strength, current);
        remaining = DURATION;
    }

    public void update(float delta) {
        remaining = Math.max(0f, remaining - delta);
    }

    public boolean isActive() {
        return remaining > 0f;
    }

    public float alpha() {
        return remaining <= 0f ? 0f : peak * (remaining / DURATION);
    }

    public void reset() {
        remaining = 0f;
    }

    /** Four soft bars along the edges, strongest at the border and fading inward. */
    public void render(ShapeRenderer shapes, Viewport viewport) {
        float a = alpha();
        if (a <= 0f || shapes == null || viewport == null) {
            return;
        }
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        float tw = w * 0.10f;
        float th = h * 0.14f;
        Color edge = new Color(0.85f, 0.04f, 0.04f, a);
        Color clear = new Color(0.85f, 0.04f, 0.04f, 0f);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.rect(0, 0, w, th, edge, edge, clear, clear);        // bottom
        shapes.rect(0, h - th, w, th, clear, clear, edge, edge);   // top
        shapes.rect(0, 0, tw, h, edge, clear, clear, edge);        // left
        shapes.rect(w - tw, 0, tw, h, clear, edge, edge, clear);   // right
        shapes.end();
    }
}
