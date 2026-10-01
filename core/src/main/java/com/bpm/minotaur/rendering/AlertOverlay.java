package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.rendering.vfx.FxClipIds;
import com.bpm.minotaur.rendering.vfx.FxClips;

/**
 * The red alert symbol, flashed near the top of the screen when the player needs to look at
 * something. When to flash is {@link com.bpm.minotaur.managers.AlertMonitor}'s decision; this only
 * plays the clip.
 */
public class AlertOverlay {

    /** Height of the symbol on the 1080-unit UI canvas, and how far up the screen it sits. */
    private static final float SIZE = 150f;
    private static final float HEIGHT_FRACTION = 0.74f;

    private float elapsed = -1f;

    public void trigger() {
        elapsed = 0f;
    }

    public boolean isActive() {
        return elapsed >= 0f;
    }

    public void update(float delta) {
        if (elapsed < 0f) {
            return;
        }
        elapsed += delta;
        FxClips.Clip clip = FxClips.getInstance().get(FxClipIds.ALERT);
        if (clip == null || elapsed >= clip.duration) {
            elapsed = -1f;
        }
    }

    public void render(SpriteBatch batch, Viewport viewport) {
        if (elapsed < 0f) {
            return;
        }
        FxClips fx = FxClips.getInstance();
        FxClips.Clip clip = fx.get(FxClipIds.ALERT);
        if (clip == null) {
            return;
        }
        TextureRegion frame = fx.getFrame(FxClipIds.ALERT, elapsed / clip.duration);
        if (frame == null) {
            return;
        }
        float aspect = clip.frameWidth / (float) clip.frameHeight;
        float w = SIZE * aspect;
        float x = (viewport.getWorldWidth() - w) * 0.5f;
        float y = viewport.getWorldHeight() * HEIGHT_FRACTION;
        batch.draw(frame, x, y, w, SIZE);
    }
}
