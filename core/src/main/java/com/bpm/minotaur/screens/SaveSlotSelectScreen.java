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
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.save.PlayerSaveData;
import com.bpm.minotaur.gamedata.save.SlotMetadata;
import com.bpm.minotaur.gamedata.save.WorldSaveData;
import com.bpm.minotaur.managers.MonsterPursuitManager;
import com.bpm.minotaur.managers.MusicManager;
import com.bpm.minotaur.managers.SaveManager;
import com.bpm.minotaur.rendering.HudSkin;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 3-Slot character management and selection screen.
 * Polished with HudSkin dark fantasy double-borders, ember gold highlights, and native 4-tier pixel fonts.
 */
public class SaveSlotSelectScreen extends BaseScreen {

    private final BaseScreen parentScreen;
    private final boolean newGameMode;
    private final HudSkin hudSkin;

    private Stage stage;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    public SaveSlotSelectScreen(Tarmin2 game, BaseScreen parentScreen, boolean newGameMode) {
        super(game);
        this.parentScreen = parentScreen;
        this.newGameMode = newGameMode;
        this.hudSkin = new HudSkin();
    }

    @Override
    public void show() {
        stage = new Stage(game.getViewport(), game.getBatch());

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        buildUI();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void buildUI() {
        stage.clear();

        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // Header
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(16, 35, 16, 35);

        String titleStr = newGameMode ? "START NEW EXPEDITION - SELECT SLOT" : "EXPEDITION ARCHIVES - LOAD GAME";
        Label title = new Label(titleStr, new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        header.add(title).center().padBottom(6).row();

        String subStr = newGameMode
                ? "Select an empty slot or choose an occupied slot to overwrite.  |  [Keys: 1, 2, 3, ESC]"
                : "Choose an existing hero expedition to resume delve.  |  [Keys: 1, 2, 3, ESC]";
        Label subtitle = new Label(subStr, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(subtitle).center().row();

        root.add(header).padTop(25).padBottom(20).row();

        // 3 Slot Cards Container
        Table cardsRow = new Table();

        for (int i = 1; i <= SaveManager.MAX_SLOTS; i++) {
            Table slotCard = buildSlotCard(i);
            cardsRow.add(slotCard).size(480, 560).pad(12);
        }

        root.add(cardsRow).expand().fill().padBottom(15).row();

        // Footer Back Button
        TextButton.TextButtonStyle backStyle = new TextButton.TextButtonStyle();
        backStyle.font = hudSkin.getFontMain();
        backStyle.fontColor = HudSkin.COL_GOLD_MUTED;
        backStyle.overFontColor = Color.WHITE;
        backStyle.up = hudSkin.getSlotRecessed();
        backStyle.down = hudSkin.getSlotActive();
        backStyle.over = hudSkin.getSlotActive();

        TextButton backBtn = new TextButton("BACK TO MAIN MENU  [ESC]", backStyle);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });
        root.add(backBtn).minWidth(360).height(52).padBottom(25).row();
    }

    private Table buildSlotCard(final int slotIndex) {
        final SlotMetadata meta = SaveManager.getInstance().getSlotMetadata(slotIndex);
        Table card = new Table();
        card.setBackground(hudSkin.getDoubleBorderPanel());
        card.pad(20);

        if (!meta.isOccupied) {
            // Empty Slot Card
            // Same font and padding as the occupied branch below, or the headers sit
            // at different heights across a row of cards.
            Label slotNum = new Label("SLOT " + slotIndex + "  [" + slotIndex + "]",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_TEXT_MUTED));
            card.add(slotNum).padBottom(4).row();

            Label emptyLabel = new Label("- EMPTY SLOT -", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_TEXT_MUTED));
            card.add(emptyLabel).expandY().row();

            TextButton.TextButtonStyle startStyle = new TextButton.TextButtonStyle();
            startStyle.font = hudSkin.getFontMain();
            startStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
            startStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
            startStyle.up = hudSkin.getPrimaryButtonUp();
            startStyle.down = hudSkin.getPrimaryButtonDown();
            startStyle.over = hudSkin.getPrimaryButtonDown();

            TextButton startBtn = new TextButton("CREATE EXPEDITION", startStyle);
            startBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    promptNewGame(slotIndex);
                }
            });
            card.add(startBtn).minWidth(340).height(54).padBottom(20).row();

        } else {
            // Occupied Slot Card
            Label slotNum = new Label("SLOT " + slotIndex + "  [" + slotIndex + "]",
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
            card.add(slotNum).padBottom(4).row();

            Label heroName = new Label(meta.characterName, new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
            card.add(heroName).padBottom(4).row();

            Label heroClass = new Label("Level " + meta.level + " " + meta.characterClass,
                    new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE));
            card.add(heroClass).padBottom(12).row();

            // Stats row
            Table statsTable = new Table();
            statsTable.add(new Label("HP: " + meta.currentHP + "/" + meta.maxHP,
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_HP_ON_DARK))).padRight(18);
            statsTable.add(new Label("MP: " + meta.currentMP + "/" + meta.maxMP,
                    new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_MP_ON_DARK))).row();
            card.add(statsTable).padBottom(12).row();

            // Location
            Label locLabel = new Label(meta.locationName, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_TEXT_ON_DARK));
            card.add(locLabel).padBottom(8).row();

            // Mode & Heat
            String modeDisplay = meta.gameMode;
            if (meta.tormentLevel > 0) {
                modeDisplay += " [Torment " + meta.tormentLevel + "]";
            }
            Label modeLabel = new Label(modeDisplay, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_ANTIQUE));
            card.add(modeLabel).padBottom(8).row();

            // Doom Death Counter
            Color doomColor = (meta.deathCount > 35) ? HudSkin.COL_HP_CRITICAL : HudSkin.COL_TEMP_ORANGE;
            Label doomLabel = new Label("Doom Deaths: " + meta.deathCount + "/" + meta.maxDeaths,
                    new Label.LabelStyle(hudSkin.getFontSmall(), doomColor));
            card.add(doomLabel).padBottom(12).row();

            // Timestamp
            String dateStr = meta.lastPlayedTimestamp > 0 ? dateFormat.format(new Date(meta.lastPlayedTimestamp)) : "Recently";
            Label dateLabel = new Label("Played: " + dateStr, new Label.LabelStyle(hudSkin.getFontMicro(), HudSkin.COL_TEXT_MUTED));
            card.add(dateLabel).expandY().row();

            // Action Buttons
            Table btnTable = new Table();

            TextButton.TextButtonStyle playStyle = new TextButton.TextButtonStyle();
            playStyle.font = hudSkin.getFontSmall();
            playStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
            playStyle.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
            playStyle.up = hudSkin.getPrimaryButtonUp();
            playStyle.down = hudSkin.getPrimaryButtonDown();
            playStyle.over = hudSkin.getPrimaryButtonDown();

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
            btnTable.add(playBtn).minWidth(220).height(48).padRight(12);

            TextButton.TextButtonStyle delStyle = new TextButton.TextButtonStyle();
            delStyle.font = hudSkin.getFontSmall();
            delStyle.fontColor = HudSkin.COL_HP_CRITICAL;
            delStyle.overFontColor = Color.WHITE;
            delStyle.up = hudSkin.getSlotRecessed();
            delStyle.down = hudSkin.getSlotActive();
            delStyle.over = hudSkin.getSlotActive();

            TextButton delBtn = new TextButton("DELETE", delStyle);
            delBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    confirmDelete(slotIndex, meta);
                }
            });
            btnTable.add(delBtn).minWidth(120).height(48);

            card.add(btnTable).padBottom(15).row();
        }

        return card;
    }

    private void promptNewGame(final int slotIndex) {
        if (SaveManager.getInstance().isClassicModeUnlocked()) {
            showModeSelectDialog(slotIndex);
        } else {
            startNewExpedition(slotIndex, "MODERN");
        }
    }

    private void showModeSelectDialog(final int slotIndex) {
        Dialog dialog = new Dialog("SELECT GAME MODE",
                new Window.WindowStyle(hudSkin.getFontHeader(), Color.WHITE, hudSkin.getDoubleBorderPanel())) {
            @Override
            protected void result(Object object) {
                if ("MODERN".equals(object)) {
                    startNewExpedition(slotIndex, "MODERN");
                } else if ("CLASSIC".equals(object)) {
                    startNewExpedition(slotIndex, "CLASSIC");
                }
            }
        };

        TextButton.TextButtonStyle modeBtnStyle = new TextButton.TextButtonStyle();
        modeBtnStyle.font = hudSkin.getFontSmall();
        modeBtnStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        modeBtnStyle.up = hudSkin.getPrimaryButtonUp();
        modeBtnStyle.down = hudSkin.getPrimaryButtonDown();
        modeBtnStyle.over = hudSkin.getPrimaryButtonDown();

        TextButton.TextButtonStyle cancelBtnStyle = new TextButton.TextButtonStyle();
        cancelBtnStyle.font = hudSkin.getFontSmall();
        cancelBtnStyle.fontColor = HudSkin.COL_GOLD_MUTED;
        cancelBtnStyle.up = hudSkin.getSlotRecessed();
        cancelBtnStyle.down = hudSkin.getSlotActive();
        cancelBtnStyle.over = hudSkin.getSlotActive();

        dialog.getContentTable().pad(25);
        dialog.getButtonTable().pad(20);
        dialog.getButtonTable().defaults().pad(8).height(48);

        dialog.text(new Label("Classic Mode has been unlocked! Choose your expedition style:",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE)));
        dialog.button("MODERN EXPEDITION", "MODERN", modeBtnStyle);
        dialog.button("CLASSIC TARMIN (1982)", "CLASSIC", modeBtnStyle);
        dialog.button("CANCEL", null, cancelBtnStyle);
        dialog.show(stage);
    }

    private void confirmOverwrite(final int slotIndex, final SlotMetadata meta) {
        Dialog dialog = new Dialog("OVERWRITE WARNING",
                new Window.WindowStyle(hudSkin.getFontHeader(), HudSkin.COL_HP_CRITICAL, hudSkin.getDoubleBorderPanel())) {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    promptNewGame(slotIndex);
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

        dialog.text(new Label("Are you sure you want to overwrite Level " + meta.level + " " + meta.characterName + "?\nAll save data in Slot " + slotIndex + " will be PERMANENTLY lost.",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE)));
        dialog.button("YES, OVERWRITE", true, alertBtnStyle);
        dialog.button("CANCEL", false, cancelBtnStyle);
        dialog.show(stage);
    }

    private void confirmDelete(final int slotIndex, final SlotMetadata meta) {
        Dialog dialog = new Dialog("DELETE SAVE SLOT",
                new Window.WindowStyle(hudSkin.getFontHeader(), HudSkin.COL_HP_CRITICAL, hudSkin.getDoubleBorderPanel())) {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    SaveManager.getInstance().deleteSlot(slotIndex);
                    buildUI();
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

        dialog.text(new Label("Permanently delete " + meta.characterName + " (Level " + meta.level + ")?\nThis action cannot be undone.",
                new Label.LabelStyle(hudSkin.getFontMain(), Color.WHITE)));
        dialog.button("DELETE", true, alertBtnStyle);
        dialog.button("CANCEL", false, cancelBtnStyle);
        dialog.show(stage);
    }

    private void startNewExpedition(int slotIndex, String mode) {
        MonsterPursuitManager.getInstance().clear();
        SaveManager.getInstance().startNewGame(slotIndex, mode, "Hero", "Warrior");
        MusicManager.getInstance().stopWithFade(0.5f);

        com.bpm.minotaur.gamedata.GameMode gameMode = "CLASSIC".equalsIgnoreCase(mode) ? com.bpm.minotaur.gamedata.GameMode.CLASSIC : com.bpm.minotaur.gamedata.GameMode.ADVANCED;
        game.setScreen(new GameScreen(game, 1, com.bpm.minotaur.gamedata.Difficulty.EASY, gameMode));
    }

    public void launchResumeGame(int slotIndex) {
        launchResumeGame(game, slotIndex);
    }

    public static void launchResumeGame(Tarmin2 game, int slotIndex) {
        SaveManager.getInstance().setActiveSlotIndex(slotIndex);
        MusicManager.getInstance().stopWithFade(0.5f);

        WorldSaveData worldData = SaveManager.getInstance().loadActiveWorldData();
        int level = (worldData != null) ? worldData.currentLevel : 1;
        com.bpm.minotaur.gamedata.GameMode mode = com.bpm.minotaur.gamedata.GameMode.ADVANCED;
        if (worldData != null && "CLASSIC".equalsIgnoreCase(worldData.gameMode)) {
            mode = com.bpm.minotaur.gamedata.GameMode.CLASSIC;
        }

        GameScreen gameScreen = new GameScreen(game, level, com.bpm.minotaur.gamedata.Difficulty.EASY, mode);
        game.setScreen(gameScreen);

        if (worldData != null && gameScreen.getWorldManager() != null) {
            if (worldData.masterSeed != 0) {
                gameScreen.getWorldManager().setWorldSeed(worldData.masterSeed);
            }
            if (worldData.factionMatrix != null && !worldData.factionMatrix.trim().isEmpty()) {
                gameScreen.getWorldManager().setFactionMatrix(
                        com.bpm.minotaur.gamedata.monster.FactionMatrix.deserialize(worldData.factionMatrix));
            }
        }

        // Apply saved player state
        PlayerSaveData playerData = SaveManager.getInstance().loadActivePlayerData();
        if (playerData != null && gameScreen.getPlayer() != null) {
            playerData.applyToPlayer(gameScreen.getPlayer(), game.getItemDataManager(), game.getAssetManager());
        }

        // Restore pending pursuers across gates / ladders
        if (worldData != null && worldData.pendingPursuers != null) {
            MonsterPursuitManager.getInstance().loadFromSaveData(worldData.pendingPursuers, game.getMonsterDataManager(), game.getAssetManager());
        } else {
            MonsterPursuitManager.getInstance().clear();
        }

        // Load persistent shelter chest for the active slot
        com.bpm.minotaur.gamedata.item.ShelterChest.getInstance().load(game.getItemDataManager(), game.getAssetManager());
    }

    private void close() {
        game.setScreen(parentScreen);
    }

    private void triggerSlot(int slotIndex) {
        if (slotIndex < 1 || slotIndex > SaveManager.MAX_SLOTS) return;
        SlotMetadata meta = SaveManager.getInstance().getSlotMetadata(slotIndex);
        if (newGameMode) {
            if (meta.isOccupied) {
                confirmOverwrite(slotIndex, meta);
            } else {
                promptNewGame(slotIndex);
            }
        } else {
            if (meta.isOccupied) {
                launchResumeGame(slotIndex);
            }
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            close();
            return true;
        } else if (keycode == Input.Keys.NUM_1 || keycode == Input.Keys.NUMPAD_1) {
            triggerSlot(1);
            return true;
        } else if (keycode == Input.Keys.NUM_2 || keycode == Input.Keys.NUMPAD_2) {
            triggerSlot(2);
            return true;
        } else if (keycode == Input.Keys.NUM_3 || keycode == Input.Keys.NUMPAD_3) {
            triggerSlot(3);
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.06f, 0.05f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (game.getViewport() != null) {
            game.getViewport().apply();
        }
        stage.act(delta);
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
}
