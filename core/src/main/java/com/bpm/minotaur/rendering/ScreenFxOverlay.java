package com.bpm.minotaur.rendering;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.rendering.vfx.FxClips;

/**
 * Plays frame clips over the first-person view, in screen space: the glow of a heal, the speed
 * lines of a haste, the charge gathering at the caster's palm. Anything that happens at a place in
 * the maze belongs on {@link AnimationManager#spawnFx} instead.
 *
 * <p>A clip's id may be unknown (the data is optional); {@link #play} then does nothing.
 */
public class ScreenFxOverlay {

    private static final int MAX_ACTIVE = 8;

    private static final class Active {
        String clipId;
        float anchorX;
        float anchorY;
        float size;
        float elapsed;
    }

    private final Array<Active> active = new Array<>();

    /**
     * @param anchorX centre of the clip as a share of the screen width (0 left, 1 right)
     * @param anchorY centre of the clip as a share of the screen height (0 bottom, 1 top)
     * @param size    height of the clip on the 1920x1080 canvas; its width follows its aspect
     */
    public void play(String clipId, float anchorX, float anchorY, float size) {
        if (clipId == null || FxClips.getInstance().get(clipId) == null || active.size >= MAX_ACTIVE) {
            return;
        }
        Active a = new Active();
        a.clipId = clipId;
        a.anchorX = anchorX;
        a.anchorY = anchorY;
        a.size = size;
        active.add(a);
    }

    public boolean isActive() {
        return active.size > 0;
    }

    public void clear() {
        active.clear();
    }

    public void update(float delta) {
        FxClips fx = FxClips.getInstance();
        for (int i = active.size - 1; i >= 0; i--) {
            Active a = active.get(i);
            a.elapsed += delta;
            FxClips.Clip clip = fx.get(a.clipId);
            if (clip == null || a.elapsed >= clip.duration) {
                active.removeIndex(i);
            }
        }
    }

    /** The batch must already be begun. */
    public void render(SpriteBatch batch, Viewport viewport) {
        if (active.size == 0) {
            return;
        }
        FxClips fx = FxClips.getInstance();
        int srcFunc = batch.getBlendSrcFunc();
        int dstFunc = batch.getBlendDstFunc();
        for (Active a : active) {
            FxClips.Clip clip = fx.get(a.clipId);
            if (clip == null) {
                continue;
            }
            TextureRegion frame = fx.getFrame(a.clipId, a.elapsed / clip.duration);
            if (frame == null) {
                continue;
            }
            float h = a.size;
            float w = h * clip.frameWidth / (float) clip.frameHeight;
            float x = viewport.getWorldWidth() * a.anchorX - w * 0.5f;
            float y = viewport.getWorldHeight() * a.anchorY - h * 0.5f;
            if (clip.additive) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            } else {
                batch.setBlendFunction(srcFunc, dstFunc);
            }
            batch.draw(frame, x, y, w, h);
        }
        batch.setBlendFunction(srcFunc, dstFunc);
    }
}
