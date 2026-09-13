// Path: core/src/main/java/com/bpm/minotaur/screens/SettingsScreen.java
package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.managers.SettingsManager;

public class SettingsScreen extends BaseScreen {

    private final SettingsManager settingsManager;
    private Stage stage;
    private Skin skin;
    private BitmapFont font;

    // UI elements we need to update
    private TextButton difficultyButton;
    private TextButton modeButton;

    public SettingsScreen(Tarmin2 game) {
        super(game);
        this.settingsManager = SettingsManager.getInstance();
    }

    @Override
    public void show() {
        stage = new Stage(game.getViewport(), game.getBatch());

        // We need a Skin for standard UI widgets like buttons
        // Using a basic one here. You can customize this.
        skin = new Skin();
        font = new BitmapFont();
        skin.add("default", font); // Add the font as "default"

// Create a 1x1 white pixmap to use for backgrounds
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pixmap.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        pixmap.fill();

// Add the 1x1 white texture to the skin with the name "white"
        skin.add("white", new com.badlogic.gdx.graphics.Texture(pixmap));

// Dispose of the pixmap as it's no longer needed
        pixmap.dispose();
        // Button style
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.font = font;
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.down = skin.newDrawable("white", Color.LIGHT_GRAY);
        textButtonStyle.checked = skin.newDrawable("white", Color.GRAY);
        skin.add("default", textButtonStyle);

        // Label style
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);

        // ScrollPane style
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.hScroll = skin.newDrawable("white", Color.DARK_GRAY);
        scrollPaneStyle.hScrollKnob = skin.newDrawable("white", Color.LIGHT_GRAY);
        scrollPaneStyle.vScroll = skin.newDrawable("white", Color.DARK_GRAY);
        scrollPaneStyle.vScrollKnob = skin.newDrawable("white", Color.LIGHT_GRAY);
        skin.add("default", scrollPaneStyle);

        // --- Main Layout Table ---
        Table table = new Table();
        table.setFillParent(true);
        table.center();

        Label title = new Label("S E T T I N G S", labelStyle);
        title.setFontScale(2.0f);
        table.add(title).colspan(2).padBottom(30);
        table.row();

        // --- Difficulty ---
        table.add(new Label("Difficulty", labelStyle)).left().padRight(20);
        difficultyButton = new TextButton(settingsManager.getDifficulty().name(), skin);
        difficultyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cycleDifficulty();
            }
        });
        table.add(difficultyButton).width(200);
        table.row().padTop(10);

        // --- Game Mode (Debug) ---
        table.add(new Label("Game Mode", labelStyle)).left();
        modeButton = new TextButton(settingsManager.isAdvancedMode() ? "Advanced" : "Original", skin);
        modeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleGameMode();
            }
        });
        table.add(modeButton).width(200);
        table.row().padTop(10);

        // --- Controls ---
        TextButton controlsButton = new TextButton("Controls", skin);
        controlsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ControlsScreen(game, SettingsScreen.this));
            }
        });
        table.add(controlsButton).colspan(2).padTop(30).width(200);
        table.row();

        // --- Back Button ---
        TextButton backButton = new TextButton("Back to Menu", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new MainMenuScreen(game));
            }
        });
        table.add(backButton).colspan(2).padTop(18).width(200);

        stage.addActor(table);

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void cycleDifficulty() {
        Difficulty current = settingsManager.getDifficulty();
        Difficulty[] allDifficulties = Difficulty.values();
        int nextIndex = (current.ordinal() + 1) % allDifficulties.length;
        Difficulty nextDifficulty = allDifficulties[nextIndex];

        settingsManager.setDifficulty(nextDifficulty);
        difficultyButton.setText(nextDifficulty.name());
    }

    private void toggleGameMode() {
        boolean newMode = !settingsManager.isAdvancedMode();
        settingsManager.setAdvancedMode(newMode);
        modeButton.setText(newMode ? "Advanced" : "Original");
    }

    @Override
    public void render(float delta) {
        if (game.getViewport() != null) {
            game.getViewport().apply();
        }
        ScreenUtils.clear(Color.BLACK);
        stage.act(Math.min(delta, 1 / 30f));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (game.getViewport() != null) {
            game.getViewport().update(width, height, true);
        }
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        font.dispose();
    }

    // --- InputProcessor Methods ---

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            game.setScreen(new MainMenuScreen(game));
            return true;
        }
        return false; // Let stage handle it
    }
}
