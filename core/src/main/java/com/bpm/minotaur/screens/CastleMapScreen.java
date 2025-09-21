package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Player;
import com.bpm.minotaur.gamedata.Maze;

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

        shapeRenderer.setProjectionMatrix(game.viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw the maze levels
        for (int i = 1; i <= 12; i++) {
            shapeRenderer.setColor(Color.BLUE);
            shapeRenderer.rect(
                (game.viewport.getWorldWidth() / 2) - (150 + i * 10),
                (game.viewport.getWorldHeight() / 2) - (i * 20),
                300 + i * 20,
                10
            );
        }

        // Draw the player's location
        if (animationTimer % 0.5f < 0.25f) {
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.circle(
                (game.viewport.getWorldWidth() / 2) - 150 + (player.getPosition().x * 10),
                (game.viewport.getWorldHeight() / 2) - (maze.getLevel() * 20),
                5
            );
        }

        // Draw the Tarmin Treasure
        shapeRenderer.setColor(Color.YELLOW);
        shapeRenderer.rect(
            (game.viewport.getWorldWidth() / 2) - 10,
            (game.viewport.getWorldHeight() / 2) - (12 * 20) - 10,
            20,
            20
        );


        shapeRenderer.end();

        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        font.getData().setScale(2);
        font.setColor(Color.WHITE);
        font.draw(game.batch, "Castle Map", (game.viewport.getWorldWidth() / 2) - 50, game.viewport.getWorldHeight() - 50);
        font.draw(game.batch, "Treasure: " + player.getTreasureScore(), game.viewport.getWorldWidth() - 200, game.viewport.getWorldHeight() - 50);


        game.batch.end();
    }

    @Override
    public void dispose() {
        font.dispose();
        shapeRenderer.dispose();
    }
}
