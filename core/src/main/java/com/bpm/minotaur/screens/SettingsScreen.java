package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.managers.SettingsManager;
import com.bpm.minotaur.managers.SoundManager;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * Settings configuration screen.
 * Polished with HudSkin dark fantasy double-borders, gold highlights, and keyboard shortcuts.
 */
public class SettingsScreen extends BaseScreen {

    private final SettingsManager settingsManager;
    private final HudSkin hudSkin;
    private Stage stage;

    private TextButton difficultyButton;
    private TextButton modeButton;
    private Label musicVolLabel;
    private Label sfxVolLabel;

    public SettingsScreen(Tarmin2 game) {
        super(game);
        this.settingsManager = SettingsManager.getInstance();
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

        // Main Settings Card
        Table card = new Table();
        card.setBackground(hudSkin.getDoubleBorderPanel());
        card.pad(35, 50, 35, 50);

        Label title = new Label("S E T T I N G S", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        card.add(title).colspan(2).padBottom(30).center().row();

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = hudSkin.getFontMain();
        btnStyle.fontColor = HudSkin.COL_GOLD_ANTIQUE;
        btnStyle.overFontColor = Color.WHITE;
        btnStyle.up = hudSkin.getSlotRecessed();
        btnStyle.down = hudSkin.getSlotActive();
        btnStyle.over = hudSkin.getSlotActive();

        Label.LabelStyle labelStyle = new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE);

        // 1. Difficulty
        card.add(new Label("Difficulty", labelStyle)).left().padRight(40).padBottom(16);
        difficultyButton = new TextButton(settingsManager.getDifficulty().name(), btnStyle);
        difficultyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cycleDifficulty();
            }
        });
        card.add(difficultyButton).minWidth(240).height(48).padBottom(16).row();

        // 2. Game Mode
        card.add(new Label("Game Mode", labelStyle)).left().padRight(40).padBottom(16);
        modeButton = new TextButton(settingsManager.isAdvancedMode() ? "Advanced" : "Original", btnStyle);
        modeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                toggleGameMode();
            }
        });
        card.add(modeButton).minWidth(240).height(48).padBottom(16).row();

        // 3. Music Volume
        card.add(new Label("Music Volume", labelStyle)).left().padRight(40).padBottom(16);
        Table musicTable = new Table();
        TextButton musicMinus = new TextButton("-", btnStyle);
        musicMinus.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                adjustMusicVolume(-0.10f);
            }
        });
        musicTable.add(musicMinus).width(48).height(44);

        int currentMusicPercent = Math.round(settingsManager.getMusicVolume() * 100);
        musicVolLabel = new Label(currentMusicPercent + "%", labelStyle);
        musicVolLabel.setAlignment(Align.center);
        musicTable.add(musicVolLabel).width(140);

        TextButton musicPlus = new TextButton("+", btnStyle);
        musicPlus.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                adjustMusicVolume(0.10f);
            }
        });
        musicTable.add(musicPlus).width(48).height(44);
        card.add(musicTable).width(240).padBottom(16).row();

        // 4. SFX Volume
        card.add(new Label("SFX Volume", labelStyle)).left().padRight(40).padBottom(28);
        Table sfxTable = new Table();
        TextButton sfxMinus = new TextButton("-", btnStyle);
        sfxMinus.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                adjustSfxVolume(-0.10f);
            }
        });
        sfxTable.add(sfxMinus).width(48).height(44);

        int currentSfxPercent = Math.round(settingsManager.getSfxVolume() * 100);
        sfxVolLabel = new Label(currentSfxPercent + "%", labelStyle);
        sfxVolLabel.setAlignment(Align.center);
        sfxTable.add(sfxVolLabel).width(140);

        TextButton sfxPlus = new TextButton("+", btnStyle);
        sfxPlus.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                adjustSfxVolume(0.10f);
            }
        });
        sfxTable.add(sfxPlus).width(48).height(44);
        card.add(sfxTable).width(240).padBottom(28).row();

        // 5. Controls CTA Button
        TextButton.TextButtonStyle ctaStyle = new TextButton.TextButtonStyle();
        ctaStyle.font = hudSkin.getFontMain();
        ctaStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        ctaStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
        ctaStyle.up = hudSkin.getPrimaryButtonUp();
        ctaStyle.down = hudSkin.getPrimaryButtonDown();
        ctaStyle.over = hudSkin.getPrimaryButtonDown();

        TextButton controlsButton = new TextButton("KEY BINDINGS & CONTROLS  [C]", ctaStyle);
        controlsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ControlsScreen(game, SettingsScreen.this));
            }
        });
        card.add(controlsButton).colspan(2).minWidth(400).height(54).padBottom(16).center().row();

        // 6. Back Button
        TextButton backButton = new TextButton("BACK TO MAIN MENU  [ESC]", btnStyle);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new MainMenuScreen(game));
            }
        });
        card.add(backButton).colspan(2).minWidth(400).height(54).center().row();

        root.add(card);

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

    private void adjustMusicVolume(float delta) {
        float current = settingsManager.getMusicVolume();
        float newVol = Math.max(0.0f, Math.min(1.0f, Math.round((current + delta) * 10f) / 10f));
        settingsManager.setMusicVolume(newVol);
        if (musicVolLabel != null) {
            musicVolLabel.setText(Math.round(newVol * 100) + "%");
        }
    }

    private void adjustSfxVolume(float delta) {
        float current = settingsManager.getSfxVolume();
        float newVol = Math.max(0.0f, Math.min(1.0f, Math.round((current + delta) * 10f) / 10f));
        settingsManager.setSfxVolume(newVol);
        if (sfxVolLabel != null) {
            sfxVolLabel.setText(Math.round(newVol * 100) + "%");
        }
        if (SoundManager.getInstance() != null) {
            SoundManager.getInstance().playSound("ui_click");
        }
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
        if (keycode == Input.Keys.ESCAPE) {
            game.setScreen(new MainMenuScreen(game));
            return true;
        }
        if (keycode == Input.Keys.C) {
            game.setScreen(new ControlsScreen(game, SettingsScreen.this));
            return true;
        }
        if (keycode == Input.Keys.D) {
            cycleDifficulty();
            return true;
        }
        if (keycode == Input.Keys.M) {
            toggleGameMode();
            return true;
        }
        return false;
    }
}
