package com.bpm.minotaur.screens;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.bpm.minotaur.Tarmin2;

/**
 * An abstract base class for all screens in the game.
 * It implements the libGDX Screen interface and provides a common structure
 * for all game screens to extend.
 */
public abstract class BaseScreen implements Screen, InputProcessor {

    // A protected reference to the main game class.
    // This allows subclasses to access the game instance, for example, to switch
    // screens.
    protected final Tarmin2 game;

    /**
     * Constructor for the BaseScreen.
     * 
     * @param game A reference to the main Tarmin2 game instance.
     */
    public BaseScreen(final Tarmin2 game) {
        this.game = game;
    }

    /**
     * Called when this screen becomes the current screen for a Game.
     */
    @Override
    public void show() {
        // To be implemented by subclasses
    }

    /**
     * Called when the screen should render itself.
     * 
     * @param delta The time in seconds since the last render.
     */
    @Override
    public void render(float delta) {
        // To be implemented by subclasses
    }

    /**
     * Called when the application is resized.
     * 
     * @param width  the new width in pixels
     * @param height the new height in pixels
     * @see com.badlogic.gdx.ApplicationListener#resize(int, int)
     */
    @Override
    public void resize(int width, int height) {
        // To be implemented by subclasses
    }

    /**
     * Called when the application is paused.
     * 
     * @see com.badlogic.gdx.ApplicationListener#pause()
     */
    @Override
    public void pause() {
        // To be implemented by subclasses
    }

    /**
     * Called when the application is resumed from a paused state.
     * 
     * @see com.badlogic.gdx.ApplicationListener#resume()
     */
    @Override
    public void resume() {
        // To be implemented by subclasses
    }

    /**
     * Called when this screen is no longer the current screen for a Game.
     */
    @Override
    public void hide() {
        // To be implemented by subclasses
    }

    /**
     * Called when this screen should release all its resources.
     */
    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    @Override
    public void dispose() {
        // To be implemented by subclasses
    }
}
