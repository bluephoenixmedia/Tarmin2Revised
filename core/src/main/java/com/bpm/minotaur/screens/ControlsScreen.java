// Path: core/src/main/java/com/bpm/minotaur/screens/ControlsScreen.java
package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.SettingsManager;

import java.util.Map;

/**
 * Lists every rebindable action in the game and lets the player rebind them.
 * Reached from the Settings screen; returns to whichever screen opened it.
 */
public class ControlsScreen extends BaseScreen {

    private final SettingsManager settingsManager;
    private final Screen backScreen;

    private Stage stage;
    private Skin skin;
    private BitmapFont font;

    // State for key binding
    private boolean isListeningForKey = false;
    private String actionToBind = null;
    private TextButton listeningButton = null;

    public ControlsScreen(Tarmin2 game, Screen backScreen) {
        super(game);
        this.settingsManager = SettingsManager.getInstance();
        this.backScreen = backScreen;
    }

    @Override
    public void show() {
        stage = new Stage(game.getViewport(), game.getBatch());

        skin = new Skin();
        font = new BitmapFont();
        skin.add("default", font);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));
        pixmap.dispose();

        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.font = font;
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.down = skin.newDrawable("white", Color.LIGHT_GRAY);
        textButtonStyle.checked = skin.newDrawable("white", Color.GRAY);
        skin.add("default", textButtonStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);

        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.hScroll = skin.newDrawable("white", Color.DARK_GRAY);
        scrollPaneStyle.hScrollKnob = skin.newDrawable("white", Color.LIGHT_GRAY);
        scrollPaneStyle.vScroll = skin.newDrawable("white", Color.DARK_GRAY);
        scrollPaneStyle.vScrollKnob = skin.newDrawable("white", Color.LIGHT_GRAY);
        skin.add("default", scrollPaneStyle);

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        Label title = new Label("C O N T R O L S", labelStyle);
        title.setFontScale(2.0f);
        table.add(title).colspan(2).padBottom(30);
        table.row();

        Table keyTable = new Table();
        for (Map.Entry<String, String> entry : settingsManager.getKeyBindingDescriptions().entrySet()) {
            addKeyBindingRow(keyTable, entry.getKey(), entry.getValue(), skin);
        }
        ScrollPane scrollPane = new ScrollPane(keyTable, skin);
        scrollPane.setFadeScrollBars(false);

        table.add(scrollPane).colspan(2).height(400).width(450);
        table.row();

        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isListeningForKey) {
                    cancelListening();
                }
                game.setScreen(backScreen);
            }
        });
        table.add(backButton).colspan(2).padTop(30).width(200);

        stage.addActor(table);

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void addKeyBindingRow(Table table, final String action, String description, Skin skin) {
        table.add(new Label(description, skin)).left().padRight(20);

        String keyName = Input.Keys.toString(settingsManager.getKey(action));
        final TextButton keyButton = new TextButton(keyName, skin);
        keyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isListeningForKey) {
                    cancelListening();
                }
                startListening(action, keyButton);
            }
        });

        table.add(keyButton).width(150);
        table.row().padTop(5);
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
        if (isListeningForKey) {
            bindNewKey(keycode);
            return true; // Key was "consumed" for binding
        }
        if (keycode == Input.Keys.ESCAPE) {
            game.setScreen(backScreen);
            return true;
        }
        return false; // Let stage handle it
    }
}
