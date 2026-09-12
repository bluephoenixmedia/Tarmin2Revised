package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.SaveManager;

/**
 * Translucent in-game pause modal accessed via ESC during first-person exploration.
 * Provides Resume, Settings, Save & Quit, and Abandon Run actions.
 */
public class PauseScreen extends BaseScreen {

    private final GameScreen gameScreen;

    private Stage stage;
    private BitmapFont font;
    private BitmapFont titleFont;
    private Texture whitePixel;
    private Texture dialogBg;

    public PauseScreen(Tarmin2 game, GameScreen gameScreen) {
        super(game);
        this.gameScreen = gameScreen;
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);
        pixmap.dispose();

        Pixmap dlgPix = new Pixmap(500, 600, Pixmap.Format.RGBA8888);
        dlgPix.setColor(0.1f, 0.1f, 0.15f, 0.95f);
        dlgPix.fill();
        dlgPix.setColor(0.4f, 0.4f, 0.55f, 1f);
        dlgPix.drawRectangle(0, 0, 500, 600);
        dialogBg = new Texture(dlgPix);
        dlgPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.5f);

        titleFont = new BitmapFont();
        titleFont.getData().setScale(2.5f);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Table panel = new Table();
        panel.setBackground(new TextureRegionDrawable(dialogBg));
        panel.pad(40);

        Label title = new Label("GAME PAUSED", new Label.LabelStyle(titleFont, new Color(0.95f, 0.85f, 0.3f, 1f)));
        panel.add(title).padBottom(35).row();

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.25f, 0.35f, 0.45f, 0.9f));
        btnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.35f, 0.5f, 0.65f, 1f));

        // 1. Resume
        TextButton resumeBtn = new TextButton("RESUME (ESC)", btnStyle);
        resumeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                resumeGame();
            }
        });
        panel.add(resumeBtn).width(380).height(55).padBottom(18).row();

        // 2. Settings
        TextButton settingsBtn = new TextButton("SETTINGS", btnStyle);
        settingsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new SettingsScreen(game));
            }
        });
        panel.add(settingsBtn).width(380).height(55).padBottom(18).row();

        // 3. Pact of Torment (Available when unlocked)
        if (SaveManager.getInstance().isClassicModeUnlocked()) {
            TextButton.TextButtonStyle tormentStyle = new TextButton.TextButtonStyle();
            tormentStyle.font = font;
            tormentStyle.fontColor = new Color(1f, 0.85f, 0.4f, 1f);
            tormentStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.45f, 0.15f, 0.15f, 0.9f));
            tormentStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.7f, 0.2f, 0.2f, 1f));

            TextButton tormentBtn = new TextButton("PACT OF TORMENT", tormentStyle);
            tormentBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new TormentPactScreen(game, PauseScreen.this));
                }
            });
            panel.add(tormentBtn).width(380).height(55).padBottom(18).row();
        }

        // 3. Save & Quit
        TextButton.TextButtonStyle saveQuitStyle = new TextButton.TextButtonStyle();
        saveQuitStyle.font = font;
        saveQuitStyle.fontColor = Color.WHITE;
        saveQuitStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.2f, 0.5f, 0.25f, 0.9f));
        saveQuitStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.65f, 0.35f, 1f));

        TextButton saveQuitBtn = new TextButton("SAVE & QUIT TO MENU", saveQuitStyle);
        saveQuitBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveAndQuit();
            }
        });
        panel.add(saveQuitBtn).width(380).height(55).padBottom(18).row();

        // 4. Abandon Run
        TextButton.TextButtonStyle abandonStyle = new TextButton.TextButtonStyle();
        abandonStyle.font = font;
        abandonStyle.fontColor = Color.WHITE;
        abandonStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.55f, 0.15f, 0.15f, 0.9f));
        abandonStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.75f, 0.2f, 0.2f, 1f));

        TextButton abandonBtn = new TextButton("ABANDON EXPEDITION", abandonStyle);
        abandonBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                confirmAbandon();
            }
        });
        panel.add(abandonBtn).width(380).height(55).row();

        root.add(panel);
    }

    private void resumeGame() {
        game.setScreen(gameScreen);
    }

    private void saveAndQuit() {
        if (gameScreen != null && gameScreen.getPlayer() != null) {
            SaveManager.getInstance().saveActiveSlot(gameScreen.getPlayer(), gameScreen.getWorldManager());
        }
        game.setScreen(new MainMenuScreen(game));
    }

    private void confirmAbandon() {
        Dialog dialog = new Dialog("ABANDON RUN", new Window.WindowStyle(font, Color.RED, new TextureRegionDrawable(dialogBg))) {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    // Trigger death/doom increment and return to shelter or menu
                    if (gameScreen != null) {
                        gameScreen.killPlayer();
                    }
                    resumeGame();
                }
            }
        };

        TextButton.TextButtonStyle alertBtnStyle = new TextButton.TextButtonStyle();
        alertBtnStyle.font = font;
        alertBtnStyle.fontColor = Color.WHITE;
        alertBtnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.6f, 0.15f, 0.15f, 0.95f));
        alertBtnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.8f, 0.2f, 0.2f, 1f));

        TextButton.TextButtonStyle cancelBtnStyle = new TextButton.TextButtonStyle();
        cancelBtnStyle.font = font;
        cancelBtnStyle.fontColor = Color.LIGHT_GRAY;
        cancelBtnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.25f, 0.25f, 0.3f, 0.95f));
        cancelBtnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.35f, 0.35f, 0.4f, 1f));

        dialog.getContentTable().pad(25);
        dialog.getButtonTable().pad(20);
        dialog.getButtonTable().defaults().pad(8).height(50);

        dialog.text(new Label("Are you sure you want to abandon this expedition?\nYou will succumb to the Doom and leave your gear where you fell.",
                new Label.LabelStyle(font, Color.WHITE)));
        dialog.button("YES, ABANDON", true, alertBtnStyle);
        dialog.button("CANCEL", false, cancelBtnStyle);
        dialog.show(stage);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            resumeGame();
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        // Draw underlying game screen first
        if (gameScreen != null) {
            gameScreen.render(0f);
        }

        // Draw semi-transparent dark tint overlay
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        game.getBatch().begin();
        game.getBatch().setColor(0f, 0f, 0f, 0.65f);
        game.getBatch().draw(whitePixel, 0, 0, game.getViewport().getWorldWidth(), game.getViewport().getWorldHeight());
        game.getBatch().setColor(Color.WHITE);
        game.getBatch().end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
        if (titleFont != null) titleFont.dispose();
        if (whitePixel != null) whitePixel.dispose();
        if (dialogBg != null) dialogBg.dispose();
    }
}
