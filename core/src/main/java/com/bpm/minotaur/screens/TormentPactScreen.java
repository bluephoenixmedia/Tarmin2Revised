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
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.managers.TormentManager;
import com.bpm.minotaur.managers.TormentManager.TormentModifier;

/**
 * Screen for configuring Hades-style Torment / Heat modifiers before starting an ascended expedition.
 */
public class TormentPactScreen extends BaseScreen {

    private final BaseScreen parentScreen;
    private final TormentManager tormentManager;

    private Stage stage;
    private BitmapFont font;
    private BitmapFont titleFont;
    private Texture whitePixel;
    private Texture cardBg;

    private Label heatLevelLabel;
    private Table modifiersTable;

    public TormentPactScreen(Tarmin2 game, BaseScreen parentScreen) {
        super(game);
        this.parentScreen = parentScreen;
        this.tormentManager = TormentManager.getInstance();
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

        Pixmap cardPix = new Pixmap(60, 60, Pixmap.Format.RGBA8888);
        cardPix.setColor(0.15f, 0.15f, 0.2f, 0.95f);
        cardPix.fill();
        cardPix.setColor(0.35f, 0.35f, 0.45f, 1f);
        cardPix.drawRectangle(0, 0, 60, 60);
        cardBg = new Texture(cardPix);
        cardPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.4f);

        titleFont = new BitmapFont();
        titleFont.getData().setScale(2.2f);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // Header
        Label title = new Label("PACT OF TORMENT", new Label.LabelStyle(titleFont, new Color(0.95f, 0.3f, 0.2f, 1f)));
        root.add(title).padTop(40).padBottom(5).row();

        Label subtitle = new Label("Invoke infernal pacts to increase expedition difficulty and glory.",
                new Label.LabelStyle(font, Color.LIGHT_GRAY));
        root.add(subtitle).padBottom(15).row();

        heatLevelLabel = new Label("CURRENT HEAT LEVEL: " + tormentManager.getHeatLevel(),
                new Label.LabelStyle(titleFont, new Color(1f, 0.8f, 0.2f, 1f)));
        root.add(heatLevelLabel).padBottom(25).row();

        // Modifiers List
        modifiersTable = new Table();
        populateModifiersTable();

        ScrollPane scrollPane = new ScrollPane(modifiersTable);
        scrollPane.setFadeScrollBars(false);
        root.add(scrollPane).size(1100, 520).padBottom(30).row();

        // Footer buttons
        Table buttonBar = new Table();

        TextButton.TextButtonStyle confirmStyle = new TextButton.TextButtonStyle();
        confirmStyle.font = font;
        confirmStyle.fontColor = Color.WHITE;
        confirmStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.2f, 0.5f, 0.2f, 0.9f));
        confirmStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.7f, 0.3f, 1f));

        TextButton confirmBtn = new TextButton("SEAL PACT & RETURN", confirmStyle);
        confirmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });
        buttonBar.add(confirmBtn).width(300).height(55).padRight(30);

        TextButton.TextButtonStyle clearStyle = new TextButton.TextButtonStyle();
        clearStyle.font = font;
        clearStyle.fontColor = Color.WHITE;
        clearStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.4f, 0.2f, 0.2f, 0.9f));
        clearStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.6f, 0.3f, 0.3f, 1f));

        TextButton clearBtn = new TextButton("CLEAR ALL", clearStyle);
        clearBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                tormentManager.clearModifiers();
                refreshUI();
            }
        });
        buttonBar.add(clearBtn).width(200).height(55);

        root.add(buttonBar).padBottom(40).row();
    }

    private void populateModifiersTable() {
        modifiersTable.clear();

        for (final TormentModifier mod : TormentModifier.values()) {
            final boolean active = tormentManager.hasModifier(mod);

            Table row = new Table();
            row.setBackground(new TextureRegionDrawable(cardBg));

            // Status Indicator / Toggle Button
            TextButton.TextButtonStyle toggleStyle = new TextButton.TextButtonStyle();
            toggleStyle.font = font;
            toggleStyle.fontColor = Color.WHITE;
            Color btnColor = active ? new Color(0.7f, 0.2f, 0.2f, 0.9f) : new Color(0.25f, 0.25f, 0.3f, 0.8f);
            toggleStyle.up = new TextureRegionDrawable(whitePixel).tint(btnColor);

            TextButton toggleBtn = new TextButton(active ? "[ ACTIVE ]" : "[ INACTIVE ]", toggleStyle);
            toggleBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    tormentManager.toggleModifier(mod);
                    refreshUI();
                }
            });
            row.add(toggleBtn).width(160).height(50).pad(12);

            // Title and Description
            Table infoTable = new Table();
            Label nameLabel = new Label(mod.getTitle() + "  (+" + mod.getHeatValue() + " Heat)",
                    new Label.LabelStyle(font, active ? new Color(1f, 0.85f, 0.4f, 1f) : Color.WHITE));
            nameLabel.setAlignment(Align.left);
            infoTable.add(nameLabel).expandX().fillX().row();

            Label descLabel = new Label(mod.getDescription(),
                    new Label.LabelStyle(font, Color.LIGHT_GRAY));
            descLabel.setAlignment(Align.left);
            infoTable.add(descLabel).expandX().fillX().row();

            row.add(infoTable).expandX().fillX().padLeft(15);

            modifiersTable.add(row).width(1050).padBottom(10).row();
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
        if (keycode == Input.Keys.ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.06f, 0.04f, 0.08f, 1f);
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
        if (font != null) font.dispose();
        if (titleFont != null) titleFont.dispose();
        if (whitePixel != null) whitePixel.dispose();
        if (cardBg != null) cardBg.dispose();
    }
}
