package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.SaveManager;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * Translucent in-game pause modal accessed via ESC during first-person exploration.
 * Polished with HudSkin double-borders, gold highlights, and clean hotkey badges.
 */
public class PauseScreen extends BaseScreen {

    private final GameScreen gameScreen;
    private final HudSkin hudSkin;

    private Stage stage;
    private Texture tintPixel;

    public PauseScreen(Tarmin2 game, GameScreen gameScreen) {
        super(game);
        this.gameScreen = gameScreen;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        tintPixel = new Texture(pixmap);
        pixmap.dispose();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        Table panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.pad(35, 45, 35, 45);

        Label title = new Label("GAME PAUSED", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        panel.add(title).padBottom(30).center().row();

        // 1. Resume (Primary)
        TextButton.TextButtonStyle resumeStyle = new TextButton.TextButtonStyle();
        resumeStyle.font = hudSkin.getFontMain();
        resumeStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        resumeStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
        resumeStyle.up = hudSkin.getPrimaryButtonUp();
        resumeStyle.down = hudSkin.getPrimaryButtonDown();
        resumeStyle.over = hudSkin.getPrimaryButtonDown();

        TextButton resumeBtn = new TextButton("RESUME  [ESC / SPACE]", resumeStyle);
        resumeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                resumeGame();
            }
        });
        panel.add(resumeBtn).width(400).height(54).padBottom(14).row();

        // Secondary style for other actions
        TextButton.TextButtonStyle secStyle = new TextButton.TextButtonStyle();
        secStyle.font = hudSkin.getFontMain();
        secStyle.fontColor = HudSkin.COL_GOLD_ANTIQUE;
        secStyle.overFontColor = Color.WHITE;
        secStyle.up = hudSkin.getSlotRecessed();
        secStyle.down = hudSkin.getSlotActive();
        secStyle.over = hudSkin.getSlotActive();

        // 2. Settings
        TextButton settingsBtn = new TextButton("SETTINGS  [S]", secStyle);
        settingsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new SettingsScreen(game));
            }
        });
        panel.add(settingsBtn).width(400).height(54).padBottom(14).row();

        // 3. Pact of Torment (Available when unlocked)
        if (SaveManager.getInstance().isClassicModeUnlocked()) {
            TextButton.TextButtonStyle tormentStyle = new TextButton.TextButtonStyle();
            tormentStyle.font = hudSkin.getFontMain();
            tormentStyle.fontColor = HudSkin.COL_GOLD_BRIGHT;
            tormentStyle.overFontColor = Color.WHITE;
            tormentStyle.up = hudSkin.getSlotRecessed();
            tormentStyle.down = hudSkin.getSlotActive();
            tormentStyle.over = hudSkin.getSlotActive();

            TextButton tormentBtn = new TextButton("PACT OF TORMENT  [T]", tormentStyle);
            tormentBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new TormentPactScreen(game, PauseScreen.this));
                }
            });
            panel.add(tormentBtn).width(400).height(54).padBottom(14).row();
        }

        // 4. Save & Quit
        TextButton.TextButtonStyle saveQuitStyle = new TextButton.TextButtonStyle();
        saveQuitStyle.font = hudSkin.getFontMain();
        saveQuitStyle.fontColor = Color.WHITE;
        saveQuitStyle.overFontColor = Color.WHITE;
        saveQuitStyle.up = hudSkin.getSlotRecessed();
        saveQuitStyle.down = hudSkin.getSlotActive();
        saveQuitStyle.over = hudSkin.getSlotActive();

        TextButton saveQuitBtn = new TextButton("SAVE & QUIT TO MENU  [Q]", saveQuitStyle);
        saveQuitBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                saveAndQuit();
            }
        });
        panel.add(saveQuitBtn).width(400).height(54).padBottom(14).row();

        // 5. Abandon Run
        TextButton.TextButtonStyle abandonStyle = new TextButton.TextButtonStyle();
        abandonStyle.font = hudSkin.getFontMain();
        abandonStyle.fontColor = HudSkin.COL_HP_CRITICAL;
        abandonStyle.overFontColor = Color.WHITE;
        abandonStyle.up = hudSkin.getSlotRecessed();
        abandonStyle.down = hudSkin.getSlotActive();
        abandonStyle.over = hudSkin.getSlotActive();

        TextButton abandonBtn = new TextButton("ABANDON EXPEDITION  [A]", abandonStyle);
        abandonBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                confirmAbandon();
            }
        });
        panel.add(abandonBtn).width(400).height(54).row();

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
        Dialog dialog = new Dialog("ABANDON EXPEDITION",
                new Window.WindowStyle(hudSkin.getFontHeader(), HudSkin.COL_HP_CRITICAL, hudSkin.getDoubleBorderPanel())) {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    if (gameScreen != null) {
                        gameScreen.killPlayer();
                    }
                    resumeGame();
                }
            }
        };

        TextButton.TextButtonStyle alertBtnStyle = new TextButton.TextButtonStyle();
        alertBtnStyle.font = hudSkin.getFontSmall();
        alertBtnStyle.fontColor = HudSkin.COL_HP_CRITICAL;
        alertBtnStyle.overFontColor = Color.WHITE;
        alertBtnStyle.up = hudSkin.getSlotRecessed();
        alertBtnStyle.down = hudSkin.getSlotActive();
        alertBtnStyle.over = hudSkin.getSlotActive();

        TextButton.TextButtonStyle cancelBtnStyle = new TextButton.TextButtonStyle();
        cancelBtnStyle.font = hudSkin.getFontSmall();
        cancelBtnStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        cancelBtnStyle.up = hudSkin.getPrimaryButtonUp();
        cancelBtnStyle.down = hudSkin.getPrimaryButtonDown();
        cancelBtnStyle.over = hudSkin.getPrimaryButtonDown();

        dialog.getContentTable().pad(25);
        dialog.getButtonTable().pad(20);
        dialog.getButtonTable().defaults().pad(8).height(48);

        dialog.text(new Label("Are you sure you want to abandon this expedition?\nYou will succumb to the Doom and leave your gear where you fell.",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE)));
        dialog.button("YES, ABANDON", true, alertBtnStyle);
        dialog.button("CANCEL", false, cancelBtnStyle);
        dialog.show(stage);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.SPACE) {
            resumeGame();
            return true;
        }
        if (keycode == Input.Keys.S) {
            game.setScreen(new SettingsScreen(game));
            return true;
        }
        if (keycode == Input.Keys.T && SaveManager.getInstance().isClassicModeUnlocked()) {
            game.setScreen(new TormentPactScreen(game, PauseScreen.this));
            return true;
        }
        if (keycode == Input.Keys.Q) {
            saveAndQuit();
            return true;
        }
        if (keycode == Input.Keys.A) {
            confirmAbandon();
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        if (gameScreen != null) {
            gameScreen.render(0f);
        }

        // Draw dark translucent overlay
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        game.getBatch().begin();
        game.getBatch().setColor(0f, 0f, 0f, 0.72f);
        game.getBatch().draw(tintPixel, 0, 0, game.getViewport().getWorldWidth(), game.getViewport().getWorldHeight());
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
        if (tintPixel != null) tintPixel.dispose();
        if (hudSkin != null) hudSkin.dispose();
    }
}
