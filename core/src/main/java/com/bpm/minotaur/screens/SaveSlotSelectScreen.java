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
import com.bpm.minotaur.gamedata.Difficulty;
import com.bpm.minotaur.gamedata.GameMode;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.gamedata.save.SlotMetadata;
import com.bpm.minotaur.gamedata.save.WorldSaveData;
import com.bpm.minotaur.managers.MusicManager;
import com.bpm.minotaur.managers.SaveManager;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 3-Slot character management and selection screen.
 * Renders rich cards displaying level, location, health/mana, doom deaths, and mode.
 */
public class SaveSlotSelectScreen extends BaseScreen {

    private final BaseScreen parentScreen;
    private final boolean newGameMode;

    private Stage stage;
    private BitmapFont font;
    private BitmapFont titleFont;
    private BitmapFont cardTitleFont;
    private Texture whitePixel;
    private Texture cardBg;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    public SaveSlotSelectScreen(Tarmin2 game, BaseScreen parentScreen, boolean newGameMode) {
        super(game);
        this.parentScreen = parentScreen;
        this.newGameMode = newGameMode;
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

        Pixmap cardPix = new Pixmap(480, 620, Pixmap.Format.RGBA8888);
        cardPix.setColor(0.12f, 0.12f, 0.16f, 0.95f);
        cardPix.fill();
        cardPix.setColor(0.3f, 0.35f, 0.45f, 1f);
        cardPix.drawRectangle(0, 0, 480, 620);
        cardPix.drawRectangle(1, 1, 478, 618);
        cardBg = new Texture(cardPix);
        cardPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.3f);

        cardTitleFont = new BitmapFont();
        cardTitleFont.getData().setScale(1.8f);

        titleFont = new BitmapFont();
        titleFont.getData().setScale(2.5f);

        buildUI();
    }

    private void buildUI() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // Header
        String titleStr = newGameMode ? "START NEW EXPEDITION - SELECT SLOT" : "EXPEDITION ARCHIVES - LOAD GAME";
        Label title = new Label(titleStr, new Label.LabelStyle(titleFont, new Color(0.95f, 0.85f, 0.4f, 1f)));
        root.add(title).padTop(40).padBottom(10).row();

        String subStr = newGameMode
                ? "Select an empty slot or choose an occupied slot to overwrite."
                : "Choose an existing hero expedition to resume delve.";
        Label subtitle = new Label(subStr, new Label.LabelStyle(font, Color.LIGHT_GRAY));
        root.add(subtitle).padBottom(30).row();

        // 3 Slot Cards Container
        Table cardsRow = new Table();

        for (int i = 1; i <= SaveManager.MAX_SLOTS; i++) {
            Table slotCard = buildSlotCard(i);
            cardsRow.add(slotCard).size(500, 640).pad(15);
        }

        root.add(cardsRow).expand().fill().row();

        // Footer Back Button
        TextButton.TextButtonStyle backStyle = new TextButton.TextButtonStyle();
        backStyle.font = font;
        backStyle.fontColor = Color.WHITE;
        backStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.3f, 0.35f, 0.9f));
        backStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.45f, 0.45f, 0.5f, 1f));

        TextButton backBtn = new TextButton("BACK TO MAIN MENU (ESC)", backStyle);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });
        root.add(backBtn).width(340).height(55).padBottom(35).row();
    }

    private Table buildSlotCard(final int slotIndex) {
        final SlotMetadata meta = SaveManager.getInstance().getSlotMetadata(slotIndex);
        Table card = new Table();
        card.setBackground(new TextureRegionDrawable(cardBg));
        card.pad(20);

        if (!meta.isOccupied) {
            // Empty Slot Card
            Label slotNum = new Label("SLOT " + slotIndex, new Label.LabelStyle(cardTitleFont, Color.GRAY));
            card.add(slotNum).padTop(30).padBottom(20).row();

            Label emptyLabel = new Label("- EMPTY SLOT -", new Label.LabelStyle(font, Color.DARK_GRAY));
            card.add(emptyLabel).expandY().row();

            TextButton.TextButtonStyle startStyle = new TextButton.TextButtonStyle();
            startStyle.font = font;
            startStyle.fontColor = Color.WHITE;
            startStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.2f, 0.55f, 0.25f, 0.95f));
            startStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.7f, 0.35f, 1f));

            TextButton startBtn = new TextButton("CREATE EXPEDITION", startStyle);
            startBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    promptNewGame(slotIndex);
                }
            });
            card.add(startBtn).width(360).height(60).padBottom(30).row();

        } else {
            // Occupied Slot Card
            Label slotNum = new Label("SLOT " + slotIndex, new Label.LabelStyle(font, new Color(0.7f, 0.7f, 0.7f, 1f)));
            card.add(slotNum).padBottom(5).row();

            Label heroName = new Label(meta.characterName, new Label.LabelStyle(cardTitleFont, new Color(1f, 0.85f, 0.3f, 1f)));
            card.add(heroName).padBottom(5).row();

            Label heroClass = new Label("Level " + meta.level + " " + meta.characterClass, new Label.LabelStyle(font, Color.WHITE));
            card.add(heroClass).padBottom(15).row();

            // Stats row
            Table statsTable = new Table();
            statsTable.add(new Label("HP: " + meta.currentHP + "/" + meta.maxHP, new Label.LabelStyle(font, new Color(0.9f, 0.3f, 0.3f, 1f)))).padRight(20);
            statsTable.add(new Label("MP: " + meta.currentMP + "/" + meta.maxMP, new Label.LabelStyle(font, new Color(0.3f, 0.6f, 1f, 1f)))).row();
            card.add(statsTable).padBottom(15).row();

            // Location
            Label locLabel = new Label(meta.locationName, new Label.LabelStyle(font, Color.LIGHT_GRAY));
            card.add(locLabel).padBottom(10).row();

            // Mode & Heat
            String modeDisplay = meta.gameMode;
            if (meta.tormentLevel > 0) {
                modeDisplay += " [Torment " + meta.tormentLevel + "]";
            }
            Label modeLabel = new Label(modeDisplay, new Label.LabelStyle(font, new Color(0.85f, 0.6f, 0.9f, 1f)));
            card.add(modeLabel).padBottom(10).row();

            // Doom Death Counter
            Color doomColor = (meta.deathCount > 35) ? Color.RED : Color.ORANGE;
            Label doomLabel = new Label("Doom Deaths: " + meta.deathCount + "/" + meta.maxDeaths, new Label.LabelStyle(font, doomColor));
            card.add(doomLabel).padBottom(15).row();

            // Timestamp
            String dateStr = meta.lastPlayedTimestamp > 0 ? dateFormat.format(new Date(meta.lastPlayedTimestamp)) : "Recently";
            Label dateLabel = new Label("Played: " + dateStr, new Label.LabelStyle(font, Color.GRAY));
            card.add(dateLabel).expandY().row();

            // Action Buttons
            Table btnTable = new Table();

            TextButton.TextButtonStyle playStyle = new TextButton.TextButtonStyle();
            playStyle.font = font;
            playStyle.fontColor = Color.WHITE;
            playStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.2f, 0.5f, 0.2f, 0.95f));
            playStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.65f, 0.3f, 1f));

            TextButton playBtn = new TextButton(newGameMode ? "OVERWRITE" : "RESUME DELVE", playStyle);
            playBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (newGameMode) {
                        confirmOverwrite(slotIndex, meta);
                    } else {
                        launchResumeGame(slotIndex);
                    }
                }
            });
            btnTable.add(playBtn).width(230).height(50).padRight(15);

            TextButton.TextButtonStyle delStyle = new TextButton.TextButtonStyle();
            delStyle.font = font;
            delStyle.fontColor = Color.WHITE;
            delStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.55f, 0.15f, 0.15f, 0.9f));
            delStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.75f, 0.2f, 0.2f, 1f));

            TextButton delBtn = new TextButton("DELETE", delStyle);
            delBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    confirmDelete(slotIndex, meta);
                }
            });
            btnTable.add(delBtn).width(130).height(50);

            card.add(btnTable).padBottom(20).row();
        }

        return card;
    }

    private void promptNewGame(final int slotIndex) {
        if (SaveManager.getInstance().isClassicModeUnlocked()) {
            // Prompt mode choice
            showModeSelectDialog(slotIndex);
        } else {
            startNewExpedition(slotIndex, "MODERN");
        }
    }

    private void showModeSelectDialog(final int slotIndex) {
        Dialog dialog = new Dialog("SELECT GAME MODE", new Window.WindowStyle(cardTitleFont, Color.WHITE, new TextureRegionDrawable(cardBg))) {
            @Override
            protected void result(Object object) {
                if ("MODERN".equals(object)) {
                    startNewExpedition(slotIndex, "MODERN");
                } else if ("CLASSIC".equals(object)) {
                    startNewExpedition(slotIndex, "CLASSIC");
                }
            }
        };
        dialog.text(new Label("Classic Mode has been unlocked! Choose your expedition style:", new Label.LabelStyle(font, Color.WHITE)));
        dialog.button("MODERN EXPEDITION", "MODERN");
        dialog.button("CLASSIC TARMIN (1982)", "CLASSIC");
        dialog.button("CANCEL", null);
        dialog.show(stage);
    }

    private void confirmOverwrite(final int slotIndex, final SlotMetadata meta) {
        Dialog dialog = new Dialog("OVERWRITE WARNING", new Window.WindowStyle(cardTitleFont, Color.RED, new TextureRegionDrawable(cardBg))) {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    promptNewGame(slotIndex);
                }
            }
        };
        dialog.text(new Label("Are you sure you want to overwrite Level " + meta.level + " " + meta.characterName + "?\nAll save data in Slot " + slotIndex + " will be PERMANENTLY lost.",
                new Label.LabelStyle(font, Color.WHITE)));
        dialog.button("YES, OVERWRITE", true);
        dialog.button("CANCEL", false);
        dialog.show(stage);
    }

    private void confirmDelete(final int slotIndex, final SlotMetadata meta) {
        Dialog dialog = new Dialog("DELETE SAVE SLOT", new Window.WindowStyle(cardTitleFont, Color.RED, new TextureRegionDrawable(cardBg))) {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    SaveManager.getInstance().deleteSlot(slotIndex);
                    buildUI();
                }
            }
        };
        dialog.text(new Label("Permanently delete " + meta.characterName + " (Level " + meta.level + ")?\nThis action cannot be undone.",
                new Label.LabelStyle(font, Color.WHITE)));
        dialog.button("DELETE", true);
        dialog.button("CANCEL", false);
        dialog.show(stage);
    }

    private void startNewExpedition(int slotIndex, String mode) {
        SaveManager.getInstance().startNewGame(slotIndex, mode, "Hero", "Warrior");
        MusicManager.getInstance().stop();

        GameMode gameMode = "CLASSIC".equalsIgnoreCase(mode) ? GameMode.CLASSIC : GameMode.ADVANCED;
        game.setScreen(new GameScreen(game, 1, Difficulty.EASY, gameMode));
    }

    public void launchResumeGame(int slotIndex) {
        launchResumeGame(game, slotIndex);
    }

    public static void launchResumeGame(Tarmin2 game, int slotIndex) {
        SaveManager.getInstance().setActiveSlotIndex(slotIndex);
        MusicManager.getInstance().stop();

        WorldSaveData worldData = SaveManager.getInstance().loadActiveWorldData();
        int level = (worldData != null) ? worldData.currentLevel : 1;
        GameMode mode = GameMode.ADVANCED;
        if (worldData != null && "CLASSIC".equalsIgnoreCase(worldData.gameMode)) {
            mode = GameMode.CLASSIC;
        }

        GameScreen gameScreen = new GameScreen(game, level, Difficulty.EASY, mode);
        game.setScreen(gameScreen);

        // Apply saved player state
        PlayerSaveData playerData = SaveManager.getInstance().loadActivePlayerData();
        if (playerData != null && gameScreen.getPlayer() != null) {
            playerData.applyToPlayer(gameScreen.getPlayer(), game.getItemDataManager(), game.getAssetManager());
        }
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
        Gdx.gl.glClearColor(0.07f, 0.07f, 0.1f, 1f);
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
        if (cardTitleFont != null) cardTitleFont.dispose();
        if (whitePixel != null) whitePixel.dispose();
        if (cardBg != null) cardBg.dispose();
    }
}
