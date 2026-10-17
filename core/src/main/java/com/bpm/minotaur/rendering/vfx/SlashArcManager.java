package com.bpm.minotaur.rendering.vfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.bpm.minotaur.rendering.mesh.DynamicQuadBatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages 3D directional crescent slash arcs projected directly against monster hit planes.
 * Supports micro-dilation suspended physics, orientation along strike exit vectors,
 * and additive blended ribbon rendering.
 */
public class SlashArcManager {

    public static class SlashArc {
        public Vector3 position = new Vector3();
        public float cutAngle;
        public float progress;
        public float duration = 0.10f;
        public float scale = 1.0f;
        public Color color = new Color(Color.WHITE);
        public boolean isCrit = false;
    }

    private final List<SlashArc> activeArcs = new ArrayList<>();
    private Texture slashTexture;
    private TextureRegion slashRegion;

    private final Vector3 scratchRight = new Vector3();
    private final Vector3 scratchUp = new Vector3();

    public SlashArcManager() {
    }

    public void spawnSlashArc(Vector3 position, float cutAngle, Color color, boolean isCrit, float scale) {
        SlashArc arc = new SlashArc();
        if (position != null) {
            arc.position.set(position);
        }
        arc.cutAngle = cutAngle;
        arc.progress = 0f;
        arc.duration = isCrit ? 0.14f : 0.10f;
        arc.scale = scale;
        if (color != null) {
            arc.color.set(color);
        }
        arc.isCrit = isCrit;
        activeArcs.add(arc);
    }

    public void update(float delta, boolean microDilationActive) {
        float effectiveDelta = microDilationActive ? delta * 0.10f : delta;
        for (int i = activeArcs.size() - 1; i >= 0; i--) {
            SlashArc arc = activeArcs.get(i);
            arc.progress += effectiveDelta / arc.duration;
            if (arc.progress >= 1.0f) {
                activeArcs.remove(i);
            }
        }
    }

    public int getActiveArcCount() {
        return activeArcs.size();
    }

    public List<SlashArc> getActiveArcs() {
        return Collections.unmodifiableList(activeArcs);
    }

    public void clear() {
        activeArcs.clear();
    }

    private TextureRegion getSlashTextureRegion() {
        if (slashRegion == null) {
            if (slashTexture == null) {
                // Generate a 128x128 procedural crescent slash texture
                int size = 128;
                Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
                pixmap.setColor(0, 0, 0, 0);
                pixmap.fill();

                int cx = size / 2;
                int cy = size / 2;
                float rOuter = size * 0.46f;
                float rInner = size * 0.30f;

                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        float dx = x - cx;
                        float dy = y - cy;
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        // Crescent curve centered along upper-right quadrant
                        if (dist >= rInner && dist <= rOuter && (dx + dy) > 0) {
                            float ringCenter = (rInner + rOuter) * 0.5f;
                            float ringHalfWidth = (rOuter - rInner) * 0.5f;
                            float ringDist = Math.abs(dist - ringCenter) / ringHalfWidth;
                            float alpha = Math.max(0f, 1f - ringDist * ringDist);
                            // Taper tips
                            float angle = (float) Math.atan2(dy, dx);
                            float angleNorm = Math.abs(angle - (MathUtils.PI / 4f)) / (MathUtils.PI / 2f);
                            alpha *= Math.max(0f, 1f - angleNorm);

                            pixmap.setColor(1f, 1f, 1f, alpha);
                            pixmap.drawPixel(x, y);
                        }
                    }
                }
                slashTexture = new Texture(pixmap);
                pixmap.dispose();
            }
            slashRegion = new TextureRegion(slashTexture);
        }
        return slashRegion;
    }

    public void render(DynamicQuadBatcher batcher, ShaderProgram shader, Vector3 camRight, Vector3 camUp, Vector3 camDir) {
        if (activeArcs.isEmpty()) return;

        TextureRegion region = getSlashTextureRegion();
        Texture tex = region.getTexture();

        for (int i = 0; i < activeArcs.size(); i++) {
            SlashArc arc = activeArcs.get(i);
            float alpha = MathUtils.clamp(1.0f - arc.progress, 0f, 1f);
            Color drawColor = new Color(arc.color.r, arc.color.g, arc.color.b, alpha);

            float width = 1.4f * arc.scale;
            float height = 1.4f * arc.scale;

            // Rotate billboard right and up vectors along camera direction by cut angle
            scratchRight.set(camRight).rotate(camDir, arc.cutAngle);
            scratchUp.set(camUp).rotate(camDir, arc.cutAngle);

            float feetY = arc.position.y - (height * 0.5f);
            batcher.addBillboard(arc.position.x, feetY, arc.position.z, width, height, region, drawColor, scratchRight, scratchUp, camDir);
        }
        batcher.flush(shader, tex);
    }

    public void dispose() {
        if (slashTexture != null) {
            slashTexture.dispose();
            slashTexture = null;
            slashRegion = null;
        }
        activeArcs.clear();
    }
}
