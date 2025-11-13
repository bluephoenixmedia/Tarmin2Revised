package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.bpm.minotaur.Tarmin2; // Your main game class

/**
 * A simple screen that shows a "Loading..." message and loads all assets.
 * It switches to the MainMenuScreen when the AssetManager is finished.
 */
public class LoadingScreen extends ScreenAdapter {

    private final Tarmin2 game;
    private final AssetManager assetManager;
    private final SpriteBatch batch;

    // A simple font to draw the loading text.
    // For a real game, you'd load this font *using* the AssetManager,
    // but for a simple loading screen, a default one is fine.
    private BitmapFont font;

    public LoadingScreen(Tarmin2 game) {
        this.game = game;
        this.assetManager = game.getAssetManager();
        this.batch = game.getBatch();

        // Use a default font. Disposed in hide()
        this.font = new BitmapFont();
    }

    @Override
    public void show() {
        Gdx.app.log("LoadingScreen", "Starting asset loading...");
        // In the 'show' method, we ensure all assets are queued (which we did in Tarmin2.create())
    }

    @Override
    public void render(float delta) {
        // --- 1. Clear the Screen ---
        Gdx.gl.glClearColor(0, 0, 0, 1); // Black background
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- 2. Update the AssetManager ---
        // This is the most important part.
        // assetManager.update() loads a chunk of assets and returns true when done.
        if (assetManager.update()) {
            // --- 3. Loading is Finished ---
            Gdx.app.log("LoadingScreen", "Asset loading complete!");
            // Tell the main game class to switch to the main menu
            game.proceedToMainMenu();
        } else {
            // --- 4. Loading is Still in Progress ---

            // Get the loading progress (a float from 0.0 to 1.0)
            float progress = assetManager.getProgress();
            int progressPercent = (int) (progress * 100);

            // Draw the loading text
            game.getViewport().apply(); // Apply the viewport settings
            batch.setProjectionMatrix(game.getViewport().getCamera().combined);

            batch.begin();
            font.draw(batch, "Loading... " + progressPercent + "%",
                game.getViewport().getWorldWidth() / 2 - 50, // Center the text
                game.getViewport().getWorldHeight() / 2);
            batch.end();
        }
    }

    @Override
    public void hide() {
        // Dispose of resources specific to this screen
        font.dispose();
    }

    @Override
    public void dispose() {
        // 'hide()' is called when switching screens, so we dispose there.
    }
}
