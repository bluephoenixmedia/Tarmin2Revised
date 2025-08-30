package com.bpm.minotaur;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.screens.MainMenuScreen;

/**
 * The main game class. Manages the core rendering objects (SpriteBatch, Viewport)
 * and controls which screen is currently active.
 */
public class Tarmin2 extends Game {
    public SpriteBatch batch;
    public Viewport viewport;

    // Constants for a consistent virtual resolution.
    private static final float VIRTUAL_WIDTH = 1920f;
    private static final float VIRTUAL_HEIGHT = 1080f;

    @Override
    public void create() {
        batch = new SpriteBatch();
        // An OrthographicCamera is used for 2D rendering.
        OrthographicCamera camera = new OrthographicCamera();
        // A FitViewport maintains the aspect ratio by adding black bars if needed.
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);

        // Set the initial screen to the main menu.
        this.setScreen(new MainMenuScreen(this));
    }

    @Override
    public void render() {
        // The Game class automatically calls the render method of the active screen.
        super.render();
    }

    @Override
    public void dispose() {
        // Dispose of shared resources when the game closes.
        batch.dispose();
        // Also dispose the current screen's resources.
        if (getScreen() != null) {
            getScreen().dispose();
        }
    }

    @Override
    public void resize(int width, int height) {
        // Update the viewport when the window is resized.
        viewport.update(width, height, true);
        // Ensure the SpriteBatch uses the updated viewport projection.
        batch.setProjectionMatrix(viewport.getCamera().combined);
    }
}

