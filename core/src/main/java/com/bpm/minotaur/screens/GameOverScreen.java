package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;

public class GameOverScreen extends BaseScreen {

    private final BitmapFont font;

    public GameOverScreen(Tarmin2 game) {
        super(game);
        this.font = new BitmapFont();
        this.font.setColor(Color.RED);
        this.font.getData().setScale(3);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ANY_KEY) {
                    game.setScreen(new MainMenuScreen(game));
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                game.setScreen(new MainMenuScreen(game));
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();
        font.draw(game.batch, "GAME OVER", game.viewport.getWorldWidth() / 2 - 150, game.viewport.getWorldHeight() / 2 + 50);
        font.draw(game.batch, "Press any key to return to the Main Menu", game.viewport.getWorldWidth() / 2 - 450, game.viewport.getWorldHeight() / 2 - 50);
        game.batch.end();
    }

    @Override
    public void dispose() {
        font.dispose();
    }
}
