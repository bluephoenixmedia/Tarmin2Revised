package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.player.Player;

public class CastleMapScreen extends BaseScreen {

    private final Player player;
    private final Maze maze;
    private final GameScreen gameScreen;
    private final BitmapFont font;
    private final ShapeRenderer shapeRenderer;
    private float animationTimer = 0f;

    public CastleMapScreen(Tarmin2 game, Player player, Maze maze, GameScreen gameScreen) {
        super(game);
        this.player = player;
        this.maze = maze;
        this.gameScreen = gameScreen; // Store the GameScreen instance
        this.font = new BitmapFont();
        this.shapeRenderer = new ShapeRenderer();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                // Return to the existing GameScreen instance
                if (keycode == Input.Keys.M) {
                    game.setScreen(gameScreen);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        animationTimer += delta;
        ScreenUtils.clear(Color.BLACK);

        shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw the maze levels
        for (int i = 1; i <= 12; i++) {
            shapeRenderer.setColor(Color.BLUE);
            shapeRenderer.rect(
                (game.getViewport().getWorldWidth() / 2) - (150 + i * 10),
                (game.getViewport().getWorldHeight() / 2) - (i * 20),
                300 + i * 20,
                10
            );
        }

        // Draw the player's location
        if (animationTimer % 0.5f < 0.25f) {
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.circle(
                (game.getViewport().getWorldWidth() / 2) - 150 + (player.getPosition().x * 10),
                (game.getViewport().getWorldHeight() / 2) - (maze.getLevel() * 20),
                5
            );
        }

        // Draw the Tarmin Treasure
        shapeRenderer.setColor(Color.YELLOW);
        shapeRenderer.rect(
            (game.getViewport().getWorldWidth() / 2) - 10,
            (game.getViewport().getWorldHeight() / 2) - (12 * 20) - 10,
            20,
            20
        );


        shapeRenderer.end();

        game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
        game.getBatch().begin();

        font.getData().setScale(2);
        font.setColor(Color.WHITE);
        font.draw(game.getBatch(), "Castle Map", (game.getViewport().getWorldWidth() / 2) - 50, game.getViewport().getWorldHeight() - 50);
        font.draw(game.getBatch(), "Treasure: " + player.getTreasureScore(), game.getViewport().getWorldWidth() - 200, game.getViewport().getWorldHeight() - 50);


        game.getBatch().end();
    }

    @Override
    public void dispose() {
        font.dispose();
        shapeRenderer.dispose();
    }

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
}
