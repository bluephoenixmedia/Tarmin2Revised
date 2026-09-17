package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.List;

/**
 * Interactive container screen for looting recovered items from the decomposing remains
 * of a past player character (NetHack-style Bones recovery).
 */
public class GraveLootScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final BonesData bonesData;
    private final List<Item> recoveredItems;

    private Stage stage;
    private BitmapFont font;
    private BitmapFont titleFont;
    private Texture whitePixel;
    private Texture slotBg;

    private Table graveTable;
    private Table backpackTable;
    private Label graveCountLabel;
    private Label backpackCountLabel;
    private Label statusLabel;

    public GraveLootScreen(Tarmin2 game, GameScreen parentScreen, Player player, BonesData bonesData) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.bonesData = bonesData;
        this.recoveredItems = bonesData.getOrExtractItems(game.getItemDataManager(), game.getAssetManager());
    }

    @Override
    public void show() {
        if (parentScreen != null && parentScreen.getSoundManager() != null) {
            parentScreen.getSoundManager().playChestOpen();
        }

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

        Pixmap slotPix = new Pixmap(48, 48, Pixmap.Format.RGBA8888);
        slotPix.setColor(0.18f, 0.22f, 0.24f, 1f);
        slotPix.fill();
        slotPix.setColor(0.35f, 0.45f, 0.45f, 1f);
        slotPix.drawRectangle(0, 0, 48, 48);
        slotBg = new Texture(slotPix);
        slotPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.5f);

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
        String name = (bonesData != null && bonesData.playerName != null) ? bonesData.playerName.toUpperCase() : "FALLEN HERO";
        Label title = new Label("REMAINS OF " + name, new Label.LabelStyle(titleFont, new Color(0.3f, 0.85f, 0.75f, 1f)));
        root.add(title).padTop(40).padBottom(6).colspan(3).row();

        String epitaph = (bonesData != null && bonesData.epitaph != null) ? bonesData.epitaph : "Fell in the depths";
        int floor = (bonesData != null) ? bonesData.floorLevel : 1;
        int strata = (bonesData != null) ? bonesData.strataDepth : 1;
        statusLabel = new Label("Floor " + floor + " (Strata " + strata + ") — " + epitaph,
                new Label.LabelStyle(font, Color.LIGHT_GRAY));
        root.add(statusLabel).padBottom(20).colspan(3).row();

        // Main Area: Remains (Left), Buttons (Middle), Backpack (Right)
        Table mainArea = new Table();

        // Left Panel: Remains items
        Table leftPanel = new Table();
        graveCountLabel = new Label("Remains (" + recoveredItems.size() + " items)", new Label.LabelStyle(font, Color.WHITE));
        leftPanel.add(graveCountLabel).padBottom(10).row();

        graveTable = new Table();
        populateGraveTable();
        ScrollPane graveScroll = new ScrollPane(graveTable);
        graveScroll.setFadeScrollBars(false);
        leftPanel.add(graveScroll).size(650, 500).row();

        mainArea.add(leftPanel).padRight(40);

        // Middle: Buttons
        Table middlePanel = new Table();
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.18f, 0.45f, 0.35f, 0.9f));
        btnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.25f, 0.65f, 0.50f, 1f));
        btnStyle.down = new TextureRegionDrawable(whitePixel).tint(new Color(0.12f, 0.35f, 0.25f, 1f));

        TextButton recoverAllBtn = new TextButton("RECOVER ALL", btnStyle);
        recoverAllBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                recoverAllItems();
            }
        });
        middlePanel.add(recoverAllBtn).width(200).height(60).padBottom(20).row();

        TextButton.TextButtonStyle closeBtnStyle = new TextButton.TextButtonStyle();
        closeBtnStyle.font = font;
        closeBtnStyle.fontColor = Color.WHITE;
        closeBtnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.3f, 0.35f, 0.9f));
        closeBtnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.45f, 0.45f, 0.5f, 1f));

        TextButton closeBtn = new TextButton("CLOSE (ESC)", closeBtnStyle);
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });
        middlePanel.add(closeBtn).width(200).height(50).row();

        mainArea.add(middlePanel).padRight(40);

        // Right Panel: Player Backpack
        Table rightPanel = new Table();
        int curCap = player.getInventory().getMainInventory().size();
        backpackCountLabel = new Label("Your Backpack (" + curCap + "/30)", new Label.LabelStyle(font, Color.WHITE));
        rightPanel.add(backpackCountLabel).padBottom(10).row();

        backpackTable = new Table();
        populateBackpackTable();
        ScrollPane backpackScroll = new ScrollPane(backpackTable);
        backpackScroll.setFadeScrollBars(false);
        rightPanel.add(backpackScroll).size(650, 500).row();

        mainArea.add(rightPanel);

        root.add(mainArea).expand().fill().row();
    }

    private void populateGraveTable() {
        graveTable.clear();

        if (recoveredItems.isEmpty()) {
            Label empty = new Label("Remains have been picked clean.", new Label.LabelStyle(font, Color.GRAY));
            graveTable.add(empty).pad(20);
            return;
        }

        for (int i = 0; i < recoveredItems.size(); i++) {
            Item item = recoveredItems.get(i);
            Table row = createItemRow(item, true, i);
            graveTable.add(row).expandX().fillX().pad(4).row();
        }
    }

    private void populateBackpackTable() {
        backpackTable.clear();
        List<Item> items = player.getInventory().getMainInventory();

        if (items.isEmpty()) {
            Label empty = new Label("Backpack is empty.", new Label.LabelStyle(font, Color.GRAY));
            backpackTable.add(empty).pad(20);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Table row = createItemRow(item, false, i);
            backpackTable.add(row).expandX().fillX().pad(4).row();
        }
    }

    private Table createItemRow(Item item, boolean fromGrave, int index) {
        Table row = new Table();
        row.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.12f, 0.14f, 0.16f, 0.85f)));
        row.pad(6, 10, 6, 10);

        // Item Icon / Slot
        Image icon = new Image(slotBg);
        if (item.getTexture() != null) {
            icon = new Image(new TextureRegion(item.getTexture()));
        }
        row.add(icon).size(36, 36).padRight(12);

        // Item Name & Details
        Table details = new Table();
        String name = item.getFriendlyName() != null ? item.getFriendlyName() : item.getTypeName();
        Color nameCol = item.getItemColor() != null ? Color.WHITE : Color.LIGHT_GRAY;
        Label nameLabel = new Label(name, new Label.LabelStyle(font, nameCol));
        details.add(nameLabel).left().row();

        String info = "Type: " + item.getType();
        if (item.isWeapon() && item.getDamageDice() != null) {
            info += " | Dmg: " + item.getDamageDice();
        }
        if (item.isArmor() && item.getBaseArmorClassBonus() > 0) {
            info += " | AC: +" + item.getBaseArmorClassBonus();
        }
        Label infoLabel = new Label(info, new Label.LabelStyle(font, Color.GRAY));
        infoLabel.setFontScale(0.8f);
        details.add(infoLabel).left().row();

        row.add(details).expandX().left();

        // Action Button
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;

        if (fromGrave) {
            btnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.18f, 0.45f, 0.35f, 0.9f));
            btnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.25f, 0.65f, 0.50f, 1f));
            TextButton takeBtn = new TextButton("TAKE", btnStyle);
            takeBtn.getLabel().setFontScale(0.85f);
            takeBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    takeSingleItem(index);
                }
            });
            row.add(takeBtn).width(80).height(36);
        }

        return row;
    }

    private void takeSingleItem(int index) {
        if (index < 0 || index >= recoveredItems.size()) return;

        Item item = recoveredItems.get(index);
        if (player.getInventory().pickupToBackpack(item)) {
            recoveredItems.remove(index);
            if (parentScreen != null && parentScreen.getSoundManager() != null) {
                parentScreen.getSoundManager().playPickupItemSound();
            }
            refresh();
        } else {
            statusLabel.setText("Your backpack is full!");
        }
    }

    private void recoverAllItems() {
        int taken = 0;
        for (int i = recoveredItems.size() - 1; i >= 0; i--) {
            Item itm = recoveredItems.get(i);
            if (player.getInventory().pickupToBackpack(itm)) {
                recoveredItems.remove(i);
                taken++;
            } else {
                break;
            }
        }

        if (taken > 0 && parentScreen != null && parentScreen.getSoundManager() != null) {
            parentScreen.getSoundManager().playPickupItemSound();
        }

        if (!recoveredItems.isEmpty()) {
            statusLabel.setText("Backpack full! Took " + taken + " items. Remaining: " + recoveredItems.size());
        } else {
            statusLabel.setText("All items recovered from the remains!");
        }
        refresh();
    }

    private void refresh() {
        graveCountLabel.setText("Remains (" + recoveredItems.size() + " items)");
        backpackCountLabel.setText("Your Backpack (" + player.getInventory().getMainInventory().size() + "/30)");
        populateGraveTable();
        populateBackpackTable();
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
        Gdx.gl.glClearColor(0.06f, 0.07f, 0.08f, 1f);
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
        if (slotBg != null) slotBg.dispose();
    }
}
