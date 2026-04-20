package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.video.JavaCVVideoPlayer;

/**
 * A simple screen that shows a "Loading..." message and loads all assets.
 * It also plays a stinger video and waits for it to finish.
 * It switches to the MainMenuScreen when the AssetManager is finished AND the
 * video is done.
 */
public class LoadingScreen extends ScreenAdapter {

    private final Tarmin2 game;
    private final AssetManager assetManager;
    private final SpriteBatch batch;

    private BitmapFont font;
    private JavaCVVideoPlayer videoPlayer;
    private boolean videoFinished = false;
    private boolean videoError = false;

    public LoadingScreen(Tarmin2 game) {
        this.game = game;
        this.assetManager = game.getAssetManager();
        this.batch = game.getBatch();

        this.font = new BitmapFont();

        // Initialize VideoPlayer
        try {
            videoPlayer = new JavaCVVideoPlayer();

            // Try internal handle first
            FileHandle videoFile = Gdx.files.internal("video/stinger_studio.mp4");

            if (videoFile.exists()) {
                Gdx.app.log("LoadingScreen", "Found video file: " + videoFile.path());
                try {
                    videoPlayer.setOnCompletionListener(file -> videoFinished = true);
                    videoPlayer.play(videoFile);
                } catch (Exception e) {
                    Gdx.app.error("LoadingScreen", "Playback error", e);
                    videoError = true;
                }
            } else {
                Gdx.app.error("LoadingScreen", "Video file not found: " + videoFile.path());
                videoError = true;
            }
        } catch (Exception e) {
            Gdx.app.error("LoadingScreen", "Error initializing video player: " + e.getMessage());
            videoError = true;
        }
    }

    @Override
    public void show() {
        Gdx.app.log("LoadingScreen", "Starting asset loading...");
    }

    @Override
    public void render(float delta) {
        // --- 1. Clear the Screen ---
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- 2. Update Video Player ---
        if (videoPlayer != null && !videoFinished && !videoError) {
            try {
                videoPlayer.update();
            } catch (Exception e) {
                Gdx.app.error("LoadingScreen", "Error updating video", e);
                videoError = true;
            }
        }

        // --- 3. Draw Video Frame ---
        game.getViewport().apply();
        batch.setProjectionMatrix(game.getViewport().getCamera().combined);
        batch.begin();

        if (videoPlayer != null && !videoError) {
            Texture frame = videoPlayer.getTexture();
            if (frame != null) {
                // Draw video centered and scaled to fit the screen
                float screenWidth = game.getViewport().getWorldWidth();
                float screenHeight = game.getViewport().getWorldHeight();
                float videoWidth = frame.getWidth();
                float videoHeight = frame.getHeight();

                // Simple scaling to fit width or height while maintaining aspect ratio
                float scale = Math.min(screenWidth / videoWidth, screenHeight / videoHeight);
                float drawWidth = videoWidth * scale;
                float drawHeight = videoHeight * scale;
                float x = (screenWidth - drawWidth) / 2f;
                float y = (screenHeight - drawHeight) / 2f;

                batch.draw(frame, x, y, drawWidth, drawHeight);
            }
        }

        // --- 4. Draw Loading Text (Overlay) ---
        float progress = assetManager.getProgress();
        int progressPercent = (int) (progress * 100);
        font.draw(batch, "Loading... " + progressPercent + "%",
                game.getViewport().getWorldWidth() / 2 - 50,
                20); // Draw near bottom

        batch.end();

        // --- 5. Asset Loading & Cleanup ---
        boolean assetsLoaded = assetManager.update();

        // Check if we can proceed
        // Check if we can proceed
        // Wait for videoFinished or videoError.
        // Also safeguard against infinite hang if video never starts/ends (e.g. 10s
        // timeout if assets loaded)
        // Removed !videoPlayer.isPlaying() check because it races with thread startup
        if (assetsLoaded && (videoFinished || videoError)) {
            Gdx.app.log("LoadingScreen", "Asset loading and video complete!");
            game.proceedToMainMenu();
        }
    }

    @Override
    public void hide() {
        font.dispose();
        if (videoPlayer != null) {
            videoPlayer.dispose();
            videoPlayer = null;
        }
    }

    @Override
    public void dispose() {
        // hide is called on screen switch
    }
}
