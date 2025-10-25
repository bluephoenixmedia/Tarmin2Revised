// Path: core/src/main/java/com/bpm/minotaur/screens/SettingsScreen.java
package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.managers.SettingsManager;

import java.util.Map;

public class SettingsScreen extends BaseScreen {

    private final SettingsManager settingsManager;
    private Stage stage;
    private Skin skin;
    private BitmapFont font;

    // UI elements we need to update
    private TextButton difficultyButton;
    private TextButton modeButton;
    private final Map<String, TextButton> keyBindingButtons;

    // State for key binding
    private boolean isListeningForKey = false;
    private String actionToBind = null;
    private TextButton listeningButton = null;

    public SettingsScreen(Tarmin2 game) {
        super(game);
        this.settingsManager = SettingsManager.getInstance();
        this.keyBindingButtons = new java.util.HashMap<>();
    }

    @Override
    public void show() {
        stage = new Stage(game.viewport);

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

        // --- Key Bindings ---
        table.add(new Label("Key Bindings", labelStyle)).colspan(2).padTop(30).padBottom(10);
        table.row();

        // Create a scrollable pane for all the key bindings
        Table keyTable = new Table();
        for (Map.Entry<String, String> entry : settingsManager.getKeyBindingDescriptions().entrySet()) {
            addKeyBindingRow(keyTable, entry.getKey(), entry.getValue(), skin);
        }
        ScrollPane scrollPane = new ScrollPane(keyTable, skin);
        scrollPane.setFadeScrollBars(false);

        table.add(scrollPane).colspan(2).height(300).width(400);
        table.row();

        // --- Back Button ---
        TextButton backButton = new TextButton("Back to Menu", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isListeningForKey) {
                    cancelListening(); // Cancel listening if we go back
                }
                game.setScreen(new MainMenuScreen(game));
            }
        });
        table.add(backButton).colspan(2).padTop(30).width(200);

        stage.addActor(table);

        // We need to process input on BOTH the stage (for buttons)
        // AND this screen (for the keyDown override to bind keys)
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
       // multiplexer.addProcessor(this); // 'this' screen implements InputProcessor via BaseScreen
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void addKeyBindingRow(Table table, final String action, String description, Skin skin) {
        table.add(new Label(description, skin)).left().padRight(20);

        String keyName = Input.Keys.toString(settingsManager.getKey(action));
        final TextButton keyButton = new TextButton(keyName, skin);
        keyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isListeningForKey) {
                    cancelListening(); // Cancel previous one
                }
                startListening(action, keyButton);
            }
        });

        table.add(keyButton).width(150);
        table.row().padTop(5);
        keyBindingButtons.put(action, keyButton);
    }

    private void startListening(String action, TextButton button) {
        isListeningForKey = true;
        actionToBind = action;
        listeningButton = button;
        button.setText("... Press a key ...");
    }

    private void cancelListening() {
        isListeningForKey = false;
        if (listeningButton != null) {
            String keyName = Input.Keys.toString(settingsManager.getKey(actionToBind));
            listeningButton.setText(keyName);
        }
        actionToBind = null;
        listeningButton = null;
    }

    private void bindNewKey(int keycode) {
        if (!isListeningForKey || actionToBind == null || listeningButton == null) {
            return;
        }

        // Prevent binding Escape
        if (keycode == Input.Keys.ESCAPE) {
            cancelListening();
            return;
        }

        settingsManager.setKey(actionToBind, keycode);
        listeningButton.setText(Input.Keys.toString(keycode));

        isListeningForKey = false;
        actionToBind = null;
        listeningButton = null;
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
        ScreenUtils.clear(Color.BLACK);
        stage.act(Math.min(delta, 1 / 30f));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
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
        if (isListeningForKey) {
            bindNewKey(keycode);
            return true; // Key was "consumed" for binding
        }
        return false; // Let stage handle it
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
