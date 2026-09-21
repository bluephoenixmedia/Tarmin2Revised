package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.audio.Music;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.MusicManager;
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

    private static final String[] LOADING_PHRASES = {
        "Building the maze...",
        "Delving into the subterranean...",
        "Placing the hoards...",
        "Carving forgotten catacombs...",
        "Kindling subterranean torches...",
        "Awakening ancient horrors...",
        "Stirring the Minotaur from his slumber...",
        "Planning your demise..."
    };

    private final GlyphLayout layout = new GlyphLayout();
    private float phraseTimer = 0f;
    private BitmapFont font;
    private JavaCVVideoPlayer videoPlayer;
    private boolean videoFinished = false;
    private boolean videoError = false;
    private boolean musicStarted = false;
    private final boolean autoProceed;
    private boolean proceeded = false;

    public LoadingScreen(Tarmin2 game) {
        this(game, true);
    }

    public LoadingScreen(Tarmin2 game, boolean autoProceed) {
        this.game = game;
        this.autoProceed = autoProceed;
        this.assetManager = game.getAssetManager();
        this.batch = game.getBatch();

        this.font = new BitmapFont();
        this.font.getData().setScale(1.6f);

        boolean skipIntro = com.bpm.minotaur.managers.SettingsManager.getInstance().isSkipIntroVideo()
                || Tarmin2.isCaptureBaseline() || Tarmin2.isCapturePolished();
        if (skipIntro) {
            Gdx.app.log("LoadingScreen", "Intro video skipped by configuration or capture mode.");
            videoFinished = true;
            return;
        }

        // Initialize VideoPlayer
        try {
            videoPlayer = new JavaCVVideoPlayer();
            videoPlayer.setMuted(true);

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
        phraseTimer += delta;

        // --- 0. Check User Skip Input (Space, Enter, Escape, Left Click) ---
        if (!videoFinished) {
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ANY_KEY)
                    || Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.LEFT)) {
                Gdx.app.log("LoadingScreen", "Intro video skipped by user input.");
                videoFinished = true;
                if (videoPlayer != null) {
                    try {
                        videoPlayer.stop();
                    } catch (Exception ignored) {
                    }
                }
                // Ensure music is started if user skipped before video started
                if (!musicStarted) {
                    musicStarted = true;
                    MusicManager.getInstance().playTrack("sounds/music/tarmin_core.mp3");
                }
            }
        }

        // Start music once the video starts playing / displaying frames
        if (!musicStarted && videoPlayer != null && !videoFinished && !videoError) {
            if (videoPlayer.isPlaying() || videoPlayer.getTexture() != null) {
                musicStarted = true;
                MusicManager.getInstance().playTrack("sounds/music/tarmin_core.mp3");
                Gdx.app.log("LoadingScreen", "Started music playback in sync with video: tarmin_core.mp3");
            }
        }

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

        // --- 3. Draw Video Frame or Loading UI ---
        game.getViewport().apply();
        batch.setProjectionMatrix(game.getViewport().getCamera().combined);
        batch.begin();

        float screenWidth = game.getViewport().getWorldWidth();
        float screenHeight = game.getViewport().getWorldHeight();

        if (videoPlayer != null && !videoFinished && !videoError) {
            Texture frame = videoPlayer.getTexture();
            if (frame != null) {
                float videoWidth = frame.getWidth();
                float videoHeight = frame.getHeight();

                float scale = Math.min(screenWidth / videoWidth, screenHeight / videoHeight);
                float drawWidth = videoWidth * scale;
                float drawHeight = videoHeight * scale;
                float x = (screenWidth - drawWidth) / 2f;
                float y = (screenHeight - drawHeight) / 2f;

                batch.draw(frame, x, y, drawWidth, drawHeight);
            }
            // Skip hint
            font.getData().setScale(1.2f);
            font.setColor(0.7f, 0.7f, 0.7f, 0.75f);
            layout.setText(font, "[Press ANY KEY or Click to Skip]");
            font.draw(batch, "[Press ANY KEY or Click to Skip]", screenWidth - layout.width - 25f, screenHeight - 25f);
        }

        // --- 4. Draw Atmospheric Loading Phrases (Replacing Percentages) ---
        float progress = assetManager.getProgress();
        String phrase = getLoadingPhrase(progress);
        float alphaPulse = 0.82f + 0.18f * (float) Math.sin(phraseTimer * 4.0f);

        if (videoFinished) {
            // Draw prominent loading message if video was skipped or completed while assets finish
            font.getData().setScale(2.4f);
            font.setColor(0.95f, 0.82f, 0.38f, 1f); // Warm ember gold
            layout.setText(font, "CASTLE TARMIN");
            font.draw(batch, "CASTLE TARMIN", (screenWidth - layout.width) / 2f, screenHeight / 2f + 40f);

            font.getData().setScale(1.6f);
            font.setColor(0.85f, 0.85f, 0.85f, alphaPulse); // Resonant pulsing silver
            layout.setText(font, phrase);
            font.draw(batch, phrase, (screenWidth - layout.width) / 2f, screenHeight / 2f - 20f);
        } else {
            // During intro video playback: drawn along the bottom with warm gold styling
            font.getData().setScale(1.5f);
            font.setColor(0.95f, 0.82f, 0.38f, alphaPulse);
            layout.setText(font, phrase);
            font.draw(batch, phrase, (screenWidth - layout.width) / 2f, 50f);
        }

        batch.end();

        // --- 5. Asset Loading Budget ---
        // While the intro video is playing, keep the asset loading slice small (5ms)
        // so the render loop maintains a smooth 60 FPS without dropping video frames.
        // Once the video finishes or is skipped, boost to 100ms to load any remaining assets rapidly.
        int assetBudgetMs = (!videoFinished && !videoError) ? 5 : 100;
        boolean assetsLoaded = assetManager.update(assetBudgetMs);

        // Check if we can proceed to MainMenu
        if (autoProceed && !proceeded && assetsLoaded && (videoFinished || videoError)) {
            proceeded = true;
            Gdx.app.log("LoadingScreen", "Asset loading and video sequence complete!");
            game.proceedToMainMenu();
        }
    }

    private String getLoadingPhrase(float progress) {
        if (progress >= 0.88f) {
            return "Planning your demise...";
        }
        int progressIndex = (int) (progress * (LOADING_PHRASES.length - 1));
        int timeIndex = (int) (phraseTimer / 1.6f);
        int index = Math.max(progressIndex, timeIndex) % (LOADING_PHRASES.length - 1);
        return LOADING_PHRASES[index];
    }

    @Override
    public void hide() {
        font.dispose();
        if (videoPlayer != null) {
            videoPlayer.dispose();
            videoPlayer = null;
        }
        // Music continues playing into MainMenuScreen
    }

    @Override
    public void dispose() {
        // hide is called on screen switch
    }
}
