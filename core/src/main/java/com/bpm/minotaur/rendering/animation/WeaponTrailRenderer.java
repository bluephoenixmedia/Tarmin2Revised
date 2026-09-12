package com.bpm.minotaur.rendering.animation;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;

/**
 * Renders a procedural translucent motion ribbon following the blade and hilt of the weapon
 * during explosive strike frames.
 */
public class WeaponTrailRenderer {

    public static class TrailNode {
        public float tipX, tipY;
        public float hiltX, hiltY;
        public float life; // 0 (new) to maxLife (dead)
    }

    private final Array<TrailNode> nodes = new Array<>();
    private final float maxLife = 0.15f; // Seconds before segment dissipates
    private Color tintColor = new Color(1f, 1f, 1f, 0.65f);
    private boolean emitting = false;

    public void setTintColor(Color color) {
        if (color != null) {
            this.tintColor.set(color);
        }
    }

    public void setEmitting(boolean emitting) {
        this.emitting = emitting;
    }

    public void addSample(float tipX, float tipY, float hiltX, float hiltY) {
        if (!emitting) return;
        TrailNode node = new TrailNode();
        node.tipX = tipX;
        node.tipY = tipY;
        node.hiltX = hiltX;
        node.hiltY = hiltY;
        node.life = 0f;
        nodes.add(node);
    }

    public void update(float delta) {
        for (int i = nodes.size - 1; i >= 0; i--) {
            TrailNode n = nodes.get(i);
            n.life += delta;
            if (n.life >= maxLife) {
                nodes.removeIndex(i);
            }
        }
    }

    public void clear() {
        nodes.clear();
        emitting = false;
    }

    public void render(ShapeRenderer shapeRenderer) {
        if (nodes.size < 2) {
            return;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int i = 0; i < nodes.size - 1; i++) {
            TrailNode a = nodes.get(i);
            TrailNode b = nodes.get(i + 1);

            float alphaA = Math.max(0f, (1f - (a.life / maxLife)) * tintColor.a);
            float alphaB = Math.max(0f, (1f - (b.life / maxLife)) * tintColor.a);

            Color colA = new Color(tintColor.r, tintColor.g, tintColor.b, alphaA);
            Color colB = new Color(tintColor.r, tintColor.g, tintColor.b, alphaB);

            // Triangle 1: a.tip, a.hilt, b.tip
            shapeRenderer.triangle(a.tipX, a.tipY, a.hiltX, a.hiltY, b.tipX, b.tipY, colA, colA, colB);
            // Triangle 2: a.hilt, b.tip, b.hilt
            shapeRenderer.triangle(a.hiltX, a.hiltY, b.tipX, b.tipY, b.hiltX, b.hiltY, colA, colB, colB);
        }

        shapeRenderer.end();
    }
}
