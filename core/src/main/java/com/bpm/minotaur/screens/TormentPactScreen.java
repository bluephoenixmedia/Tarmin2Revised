package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.TormentManager;
import com.bpm.minotaur.managers.TormentManager.TormentModifier;
import com.bpm.minotaur.rendering.HudSkin;

/**
 * Screen for configuring Hades-style Torment / Heat modifiers before starting an ascended expedition.
 * Polished with HudSkin dark fantasy double-borders, ember gold highlights, and clean hotkey badges.
 */
public class TormentPactScreen extends BaseScreen {

    private final BaseScreen parentScreen;
    private final TormentManager tormentManager;
    private final HudSkin hudSkin;

    private Stage stage;
    private Label heatLevelLabel;
    private Table modifiersTable;

    public TormentPactScreen(Tarmin2 game, BaseScreen parentScreen) {
        super(game);
        this.parentScreen = parentScreen;
        this.tormentManager = TormentManager.getInstance();
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        // Atmospheric dark backdrop
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(35, 60, 35, 60);
        stage.addActor(root);

        // Header Panel
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(18, 35, 18, 35);

        Label title = new Label("PACT OF TORMENT", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_HP_CRITICAL));
        header.add(title).center().padBottom(6).row();

        Label subtitle = new Label("Invoke infernal pacts to increase expedition difficulty, danger, and glory.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(subtitle).center().padBottom(10).row();

        heatLevelLabel = new Label("CURRENT HEAT LEVEL: " + tormentManager.getHeatLevel(),
                new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        header.add(heatLevelLabel).center().row();

        root.add(header).fillX().padBottom(20).row();

        // Modifiers List Container
        Table listContainer = new Table();
        listContainer.setBackground(hudSkin.getSlotRecessed());
        listContainer.pad(15);

        modifiersTable = new Table();
        populateModifiersTable();

        ScrollPane scrollPane = new ScrollPane(modifiersTable);
        scrollPane.setFadeScrollBars(false);
        listContainer.add(scrollPane).size(1160, 530).expand().fill();

        root.add(listContainer).size(1200, 560).padBottom(24).row();

        // Footer buttons
        Table buttonBar = new Table();

        TextButton.TextButtonStyle confirmStyle = new TextButton.TextButtonStyle();
        confirmStyle.font = hudSkin.getFontMain();
        confirmStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        confirmStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
        confirmStyle.up = hudSkin.getPrimaryButtonUp();
        confirmStyle.down = hudSkin.getPrimaryButtonDown();
        confirmStyle.over = hudSkin.getPrimaryButtonDown();

        TextButton confirmBtn = new TextButton("SEAL PACT & RETURN  [ESC / ENTER]", confirmStyle);
        confirmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });
        buttonBar.add(confirmBtn).minWidth(420).height(58).padRight(30);

        TextButton.TextButtonStyle clearStyle = new TextButton.TextButtonStyle();
        clearStyle.font = hudSkin.getFontMain();
        clearStyle.fontColor = HudSkin.COL_HP_CRITICAL;
        clearStyle.overFontColor = Color.WHITE;
        clearStyle.up = hudSkin.getSlotRecessed();
        clearStyle.down = hudSkin.getSlotActive();
        clearStyle.over = hudSkin.getSlotActive();

        TextButton clearBtn = new TextButton("CLEAR ALL  [C]", clearStyle);
        clearBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tormentManager.clearModifiers();
                refreshUI();
            }
        });
        buttonBar.add(clearBtn).minWidth(240).height(58);

        root.add(buttonBar).padBottom(20).row();
    }

    private void populateModifiersTable() {
        modifiersTable.clear();

        TormentModifier[] modifiers = TormentModifier.values();
        for (int i = 0; i < modifiers.length; i++) {
            final TormentModifier mod = modifiers[i];
            final boolean active = tormentManager.hasModifier(mod);
            final int hotkeyNum = i + 1;

            Table row = new Table();
            row.setBackground(hudSkin.getDoubleBorderPanel());
            row.pad(10, 15, 10, 15);

            // Toggle Button with Hotkey Badge
            TextButton.TextButtonStyle toggleStyle = new TextButton.TextButtonStyle();
            toggleStyle.font = hudSkin.getFontSmall();
            toggleStyle.fontColor = active ? HudSkin.COL_TEXT_ON_GOLD : HudSkin.COL_GOLD_MUTED;
            toggleStyle.overFontColor = active ? HudSkin.COL_TEXT_ON_GOLD : Color.WHITE;
            toggleStyle.up = active ? hudSkin.getPrimaryButtonUp() : hudSkin.getSlotRecessed();
            toggleStyle.down = active ? hudSkin.getPrimaryButtonDown() : hudSkin.getSlotActive();
            toggleStyle.over = active ? hudSkin.getPrimaryButtonDown() : hudSkin.getSlotActive();

            String toggleText = active ? String.format("[%d] ACTIVE", hotkeyNum) : String.format("[%d] INACTIVE", hotkeyNum);
            TextButton toggleBtn = new TextButton(toggleText, toggleStyle);
            toggleBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    tormentManager.toggleModifier(mod);
                    refreshUI();
                }
            });
            row.add(toggleBtn).minWidth(170).height(48).padRight(18);

            // Title and Description
            Table infoTable = new Table();
            String heatTag = String.format("(+%d Heat)", mod.getHeatValue());
            Label nameLabel = new Label(mod.getTitle() + "  " + heatTag,
                    new Label.LabelStyle(hudSkin.getFontMain(), active ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE));
            nameLabel.setAlignment(Align.left);
            infoTable.add(nameLabel).expandX().fillX().padBottom(4).row();

            Label descLabel = new Label(mod.getDescription(),
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            descLabel.setAlignment(Align.left);
            infoTable.add(descLabel).expandX().fillX().row();

            row.add(infoTable).expandX().fillX();

            modifiersTable.add(row).width(1120).padBottom(10).row();
        }
    }

    private void refreshUI() {
        heatLevelLabel.setText("CURRENT HEAT LEVEL: " + tormentManager.getHeatLevel());
        populateModifiersTable();
    }

    private void close() {
        game.setScreen(parentScreen);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.ENTER) {
            close();
            return true;
        }
        if (keycode == Input.Keys.C) {
            tormentManager.clearModifiers();
            refreshUI();
            return true;
        }
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            int idx = keycode - Input.Keys.NUM_1;
            TormentModifier[] mods = TormentModifier.values();
            if (idx >= 0 && idx < mods.length) {
                tormentManager.toggleModifier(mods[idx]);
                refreshUI();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.03f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

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
        if (hudSkin != null) hudSkin.dispose();
    }
}
