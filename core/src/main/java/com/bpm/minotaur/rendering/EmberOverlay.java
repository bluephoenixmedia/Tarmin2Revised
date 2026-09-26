package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Embers drifting through the play area when doom runs high.
 *
 * <p>Everything else the volcanic sky does stays safely behind the walls. This is the one effect
 * that puts moving, bright pixels in front of them, which is why it is gated behind a doom
 * threshold rather than being on all the time: the world becoming harder to read as doom rises is
 * the point, but only once things are genuinely bad.
 *
 * <p>Screen-space rather than world-space on purpose. These are meant to read as cinders caught
 * close to the eye, so they should not be occluded by geometry or shrink with distance, and they
 * should cost nothing to simulate.
 */
public class EmberOverlay implements Disposable {

    /** Below this doom fraction no embers appear at all. */
    private static final float DOOM_THRESHOLD = 0.55f;

    /** Embers on screen when doom is at maximum. */
    private static final int MAX_EMBERS = 54;

    private static final Color EMBER_HOT = new Color(1.0f, 0.55f, 0.18f, 1f);
    private static final Color EMBER_COOL = new Color(0.95f, 0.26f, 0.06f, 1f);

    private static final class Ember {
        float x;
        float y;
        float driftX;
        float riseY;
        float size;
        float life;
        float maxLife;
        float flickerPhase;
        float flickerRate;
        boolean hot;
    }

    private final Array<Ember> embers = new Array<>(MAX_EMBERS);
    private final Texture dot;
    private final Color tint = new Color();

    public EmberOverlay() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        dot = new Texture(pixmap);
        pixmap.dispose();
    }

    /**
     * @param doom01   0 = no doom, 1 = fully doomed.
     * @param width    play-area width in virtual pixels.
     * @param bottom   bottom edge of the play area in virtual pixels.
     * @param height   play-area height in virtual pixels.
     */
    public void update(float delta, float doom01, float width, float bottom, float height) {
        float intensity = intensityFor(doom01);

        if (intensity <= 0f) {
            if (embers.size > 0) {
                embers.clear();
            }
            return;
        }

        int target = Math.round(MAX_EMBERS * intensity);

        for (int i = embers.size - 1; i >= 0; i--) {
            Ember e = embers.get(i);
            e.life += delta;
            e.x += e.driftX * delta;
            e.y += e.riseY * delta;
            if (e.life >= e.maxLife || e.y > bottom + height || e.x < -20f || e.x > width + 20f) {
                embers.removeIndex(i);
            }
        }

        while (embers.size < target) {
            embers.add(spawn(width, bottom, height));
        }
        // Doom can fall as well as rise (the bridge can be repaired), so trim rather than waiting
        // for natural expiry.
        while (embers.size > target) {
            embers.removeIndex(embers.size - 1);
        }
    }

    public void render(SpriteBatch batch, float doom01) {
        if (embers.size == 0) return;

        int srcFunc = batch.getBlendSrcFunc();
        int dstFunc = batch.getBlendDstFunc();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        float intensity = intensityFor(doom01);
        for (Ember e : embers) {
            // Fade in and out so embers never pop into or out of existence.
            float lifeFraction = e.life / e.maxLife;
            float envelope = MathUtils.clamp(
                    Math.min(lifeFraction * 6f, (1f - lifeFraction) * 3f), 0f, 1f);
            float flicker = 0.6f + 0.4f * MathUtils.sin(e.flickerPhase + e.life * e.flickerRate);

            tint.set(e.hot ? EMBER_HOT : EMBER_COOL);
            tint.a = envelope * flicker * intensity * 0.85f;
            batch.setColor(tint);
            batch.draw(dot, e.x, e.y, e.size, e.size);
        }

        batch.setColor(Color.WHITE);
        batch.setBlendFunction(srcFunc, dstFunc);
    }

    /** Ramps from nothing at the threshold to full at maximum doom. */
    private float intensityFor(float doom01) {
        if (doom01 <= DOOM_THRESHOLD) return 0f;
        return MathUtils.clamp((doom01 - DOOM_THRESHOLD) / (1f - DOOM_THRESHOLD), 0f, 1f);
    }

    private Ember spawn(float width, float bottom, float height) {
        Ember e = new Ember();
        e.x = MathUtils.random(0f, width);
        // Scatter through the band rather than all entering from below, so raising doom does not
        // produce a visible wave rising up the screen.
        e.y = MathUtils.random(bottom, bottom + height);
        e.driftX = MathUtils.random(-26f, 26f);
        e.riseY = MathUtils.random(14f, 52f);
        e.size = MathUtils.random(2.5f, 6.5f);
        e.maxLife = MathUtils.random(2.2f, 5.0f);
        e.life = 0f;
        e.flickerPhase = MathUtils.random(0f, MathUtils.PI2);
        e.flickerRate = MathUtils.random(4f, 11f);
        e.hot = MathUtils.randomBoolean(0.35f);
        return e;
    }

    @Override
    public void dispose() {
        dot.dispose();
    }
}
