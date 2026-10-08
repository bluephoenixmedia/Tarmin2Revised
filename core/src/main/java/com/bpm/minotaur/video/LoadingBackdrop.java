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
 * <p>Plays {@code video/loading_video.mp4} on a muted loop, centred at half the
 * size that would fill the screen, under a black vignette laid over the video
 * itself: the face shows clearly in the middle and fades away toward the video's
 * borders, so its edges dissolve into the black around it with no frame.
 *
 * <p>Runs after the studio stinger and before the attract-mode flyover.
 */
public final class LoadingBackdrop implements Disposable {

    public static final String VIDEO = "video/loading_video.mp4";

    /** The video's size as a share of the size that would cover the whole screen. */
    static final float SIZE = 0.5f;
    /** Out to this normalised radius the video shows untouched. */
    static final float CLEAR_RADIUS = 0.25f;
    /** By this radius (the midpoints of the video's edges) it has faded to black. */
    static final float BLACK_RADIUS = 1.0f;
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
        float scale = SIZE * Math.max(screenWidth / frame.getWidth(), screenHeight / frame.getHeight());
        float w = frame.getWidth() * scale;
        float h = frame.getHeight() * scale;
        float x = (screenWidth - w) / 2f;
        float y = (screenHeight - h) / 2f;
        batch.setColor(fade, fade, fade, 1f);
        batch.draw(frame, x, y, w, h);
        batch.setColor(1f, 1f, 1f, 1f);
        if (vignette != null) {
            batch.draw(vignette, x, y, w, h); // over the video, so its borders fade into the black screen
        }
    }

    /**
     * How dark the vignette is at a normalised distance from the video's centre (0 centre,
     * 1 at the midpoints of its edges): clear in the middle, darkening steadily to black.
     */
    static float darknessAt(float r) {
        return smooth((r - CLEAR_RADIUS) / (BLACK_RADIUS - CLEAR_RADIUS));
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
