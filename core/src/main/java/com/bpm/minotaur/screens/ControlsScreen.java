package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.Map;

/**
 * Lists every rebindable action in the game and lets the player rebind them.
 * Polished with HudSkin dark fantasy styling, native fonts, and clear state indicators.
 */
public class ControlsScreen extends BaseScreen {

    private final SettingsManager settingsManager;
    private final Screen backScreen;
    private final HudSkin hudSkin;

    private Stage stage;

    // State for key binding
    private boolean isListeningForKey = false;
    private String actionToBind = null;
    private TextButton listeningButton = null;

    public ControlsScreen(Tarmin2 game, Screen backScreen) {
        super(game);
        this.settingsManager = SettingsManager.getInstance();
        this.backScreen = backScreen;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(game.getViewport(), game.getBatch());

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        // Main Card Container
        Table card = new Table();
        card.setBackground(hudSkin.getDoubleBorderPanel());
        card.pad(30, 45, 30, 45);

        Label title = new Label("C O N T R O L S", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        card.add(title).colspan(2).padBottom(8).center().row();

        Label subtitle = new Label("Click an action below, then press the desired keyboard key to rebind.  |  [ESC: Cancel/Back]",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        card.add(subtitle).colspan(2).padBottom(20).center().row();

        // Key Bindings Table
        Table keyTable = new Table();
        for (Map.Entry<String, String> entry : settingsManager.getKeyBindingDescriptions().entrySet()) {
            addKeyBindingRow(keyTable, entry.getKey(), entry.getValue());
        }

        Table scrollContainer = new Table();
        scrollContainer.setBackground(hudSkin.getSlotRecessed());
        scrollContainer.pad(12);

        ScrollPane scrollPane = new ScrollPane(keyTable);
        scrollPane.setFadeScrollBars(false);
        scrollContainer.add(scrollPane).size(560, 420).expand().fill();

        card.add(scrollContainer).colspan(2).size(580, 440).padBottom(25).row();

        // Back Button
        TextButton.TextButtonStyle backStyle = new TextButton.TextButtonStyle();
        backStyle.font = hudSkin.getFontMain();
        backStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        backStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
        backStyle.up = hudSkin.getPrimaryButtonUp();
        backStyle.down = hudSkin.getPrimaryButtonDown();
        backStyle.over = hudSkin.getPrimaryButtonDown();

        TextButton backButton = new TextButton("BACK  [ESC]", backStyle);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isListeningForKey) {
                    cancelListening();
                }
                game.setScreen(backScreen);
            }
        });
        card.add(backButton).colspan(2).minWidth(340).height(54).center().row();

        root.add(card);

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void addKeyBindingRow(Table table, final String action, String description) {
        Label descLabel = new Label(description, new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
        table.add(descLabel).left().expandX().padRight(25).padBottom(8);

        String keyName = Input.Keys.toString(settingsManager.getKey(action));
        TextButton.TextButtonStyle keyStyle = new TextButton.TextButtonStyle();
        keyStyle.font = hudSkin.getFontSmall();
        keyStyle.fontColor = HudSkin.COL_GOLD_ANTIQUE;
        keyStyle.overFontColor = Color.WHITE;
        keyStyle.up = hudSkin.getSlotRecessed();
        keyStyle.down = hudSkin.getSlotActive();
        keyStyle.over = hudSkin.getSlotActive();

        final TextButton keyButton = new TextButton(keyName, keyStyle);
        keyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (isListeningForKey) {
                    cancelListening();
                }
                startListening(action, keyButton);
            }
        });

        table.add(keyButton).minWidth(160).height(40).padBottom(8);
        table.row();
    }

    private void startListening(String action, TextButton button) {
        isListeningForKey = true;
        actionToBind = action;
        listeningButton = button;

        TextButton.TextButtonStyle listeningStyle = new TextButton.TextButtonStyle();
        listeningStyle.font = hudSkin.getFontSmall();
        listeningStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        listeningStyle.up = hudSkin.getPrimaryButtonUp();
        listeningStyle.down = hudSkin.getPrimaryButtonDown();
        listeningStyle.over = hudSkin.getPrimaryButtonDown();

        button.setStyle(listeningStyle);
        button.setText("[PRESS KEY]");
    }

    private void cancelListening() {
        if (listeningButton != null && actionToBind != null) {
            String keyName = Input.Keys.toString(settingsManager.getKey(actionToBind));
            TextButton.TextButtonStyle keyStyle = new TextButton.TextButtonStyle();
            keyStyle.font = hudSkin.getFontSmall();
            keyStyle.fontColor = HudSkin.COL_GOLD_ANTIQUE;
            keyStyle.overFontColor = Color.WHITE;
            keyStyle.up = hudSkin.getSlotRecessed();
            keyStyle.down = hudSkin.getSlotActive();
            keyStyle.over = hudSkin.getSlotActive();

            listeningButton.setStyle(keyStyle);
            listeningButton.setText(keyName);
        }
        isListeningForKey = false;
        actionToBind = null;
        listeningButton = null;
    }

    private void bindNewKey(int keycode) {
        if (!isListeningForKey || actionToBind == null || listeningButton == null) {
            return;
        }

        // Prevent binding Escape (reserved for cancel/back)
        if (keycode == Input.Keys.ESCAPE) {
            cancelListening();
            return;
        }

        settingsManager.setKey(actionToBind, keycode);

        TextButton.TextButtonStyle keyStyle = new TextButton.TextButtonStyle();
        keyStyle.font = hudSkin.getFontSmall();
        keyStyle.fontColor = HudSkin.COL_GOLD_ANTIQUE;
        keyStyle.overFontColor = Color.WHITE;
        keyStyle.up = hudSkin.getSlotRecessed();
        keyStyle.down = hudSkin.getSlotActive();
        keyStyle.over = hudSkin.getSlotActive();

        listeningButton.setStyle(keyStyle);
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
        Gdx.gl.glClearColor(0.06f, 0.05f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

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
        if (stage != null) stage.dispose();
        if (hudSkin != null) hudSkin.dispose();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (isListeningForKey) {
            bindNewKey(keycode);
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) {
            game.setScreen(backScreen);
            return true;
        }
        return false;
    }
}
