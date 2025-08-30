package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.DebugManager;

/**
 * The main screen where the game is played.
 * Now handles basic input for toggling a debug overlay.
 */
public class GameScreen extends BaseScreen implements InputProcessor {

    private BitmapFont debugFont;

    public GameScreen(Tarmin2 game) {
        super(game);
    }

    @Override
    public void show() {
        // Set this screen as the current input processor.
        Gdx.input.setInputProcessor(this);

        // Create a simple font for debug text.
        debugFont = new BitmapFont();
        debugFont.setColor(Color.WHITE);
    }

    @Override
    public void render(float delta) {
        // Clear the screen with black.
        ScreenUtils.clear(Color.BLACK);

        // Update the batch's projection matrix with the viewport's camera.
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);

        // If the debug overlay is visible, draw debug text.
        if (DebugManager.INSTANCE.isDebugOverlayVisible) {
            game.batch.begin();
            debugFont.draw(game.batch, "DEBUG MODE - (F1 to toggle)", 20, game.viewport.getWorldHeight() - 20);
            game.batch.end();
        }
    }

    @Override
    public void resize(int width, int height) {
        game.viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        // Dispose of game screen assets here.
        if (debugFont != null) debugFont.dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        // Toggle the debug overlay when F1 is pressed.
        if (keycode == Input.Keys.F1) {
            DebugManager.INSTANCE.toggleOverlay();
            return true;
        }
        return false;
    }

    // --- Unused InputProcessor methods ---
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}

