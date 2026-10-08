package com.bpm.minotaur.video;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;

/**
 * The menacing face that breathes behind the loading text.
 *
 * <p>Plays {@code video/loading_video.mp4} on a muted loop, cover-scaled to the
 * screen, under a black vignette that is darkest in the middle. The loading text
 * sits in the middle, so the face is mostly swallowed there and only its glowing
 * eyes and the smoke at its brow show through; the corners close to black.
 *
 * <p>Runs after the studio stinger and before the attract-mode flyover.
 */
public final class LoadingBackdrop implements Disposable {

    public static final String VIDEO = "video/loading_video.mp4";

    /** Darkness at the centre of the screen, where the loading text sits. */
    static final float CENTER_DARKNESS = 0.88f;
    /** Darkness in the ring between the centre and the edges, where the face shows most. */
    static final float RING_DARKNESS = 0.55f;
    /** Darkness at the corners. */
    static final float CORNER_DARKNESS = 1.00f;
    /** Seconds the backdrop takes to fade in from black. */
    private static final float FADE_IN_SECONDS = 1.2f;

    private static final int VIGNETTE_SIZE = 256;

    private JavaCVVideoPlayer player;
    private Texture vignette;
    private float age;

    /** Starts the loop. Does nothing (and draws nothing) if the video is missing or will not play. */
    public void start() {
        if (player != null) return;
        try {
            FileHandle file = Gdx.files.internal(VIDEO);
            if (!file.exists()) {
                Gdx.app.error("LoadingBackdrop", "Video file not found: " + file.path());
                return;
            }
            player = new JavaCVVideoPlayer();
            player.setMuted(true);
            player.setLooping(true);
            player.play(file);
            vignette = buildVignette();
        } catch (Exception e) {
            Gdx.app.error("LoadingBackdrop", "Could not start the loading video", e);
            dispose();
        }
    }

    public boolean isStarted() {
        return player != null;
    }

    /** Draws the video and its vignette across the screen. Call between batch.begin() and end(). */
    public void draw(SpriteBatch batch, float delta, float screenWidth, float screenHeight) {
        if (player == null) return;
        age += delta;
        try {
            player.update();
        } catch (Exception e) {
            Gdx.app.error("LoadingBackdrop", "Error updating the loading video", e);
            return;
        }
        Texture frame = player.getTexture();
        if (frame == null) return;

        float fade = Math.min(1f, age / FADE_IN_SECONDS);
        // Cover, not fit: the face is centred in black, so cropping the sides costs nothing.
        float scale = Math.max(screenWidth / frame.getWidth(), screenHeight / frame.getHeight());
        float w = frame.getWidth() * scale;
        float h = frame.getHeight() * scale;
        batch.setColor(fade, fade, fade, 1f);
        batch.draw(frame, (screenWidth - w) / 2f, (screenHeight - h) / 2f, w, h);
        batch.setColor(1f, 1f, 1f, 1f);
        if (vignette != null) {
            batch.draw(vignette, 0f, 0f, screenWidth, screenHeight);
        }
    }

    /** How dark the vignette is at a normalised distance from the centre (0 centre, 1 at the edge midpoints). */
    static float darknessAt(float r) {
        if (r <= 0.25f) return CENTER_DARKNESS;
        if (r <= 0.60f) return lerp(CENTER_DARKNESS, RING_DARKNESS, smooth((r - 0.25f) / 0.35f));
        if (r <= 0.78f) return RING_DARKNESS;
        return lerp(RING_DARKNESS, CORNER_DARKNESS, smooth(Math.min(1f, (r - 0.78f) / 0.6f)));
    }

    private static Texture buildVignette() {
        Pixmap pm = new Pixmap(VIGNETTE_SIZE, VIGNETTE_SIZE, Pixmap.Format.RGBA8888);
        float half = (VIGNETTE_SIZE - 1) / 2f;
        for (int y = 0; y < VIGNETTE_SIZE; y++) {
            for (int x = 0; x < VIGNETTE_SIZE; x++) {
                float dx = (x - half) / half;
                float dy = (y - half) / half;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                pm.drawPixel(x, y, Math.round(darknessAt(r) * 255f) & 0xFF); // black, alpha = darkness
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public void dispose() {
        if (player != null) {
            player.dispose();
            player = null;
        }
        if (vignette != null) {
            vignette.dispose();
            vignette = null;
        }
    }
}
