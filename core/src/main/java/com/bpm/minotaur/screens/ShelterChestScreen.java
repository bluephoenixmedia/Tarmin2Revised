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
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;

import java.util.List;

/**
 * Interface for the persistent Starting Shelter Stash Chest.
 * Allows depositing and withdrawing items between the player's pack and the chest.
 */
public class ShelterChestScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final ShelterChest chest;

    private Stage stage;
    private BitmapFont font;
    private BitmapFont titleFont;
    private Texture whitePixel;
    private Texture slotBg;

    private Table chestTable;
    private Table backpackTable;
    private Label chestCountLabel;
    private Label backpackCountLabel;
    private Label statusLabel;

    public ShelterChestScreen(Tarmin2 game, GameScreen parentScreen, Player player, ShelterChest chest) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.chest = chest;
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

        Pixmap slotPix = new Pixmap(48, 48, Pixmap.Format.RGBA8888);
        slotPix.setColor(0.2f, 0.2f, 0.25f, 1f);
        slotPix.fill();
        slotPix.setColor(0.4f, 0.4f, 0.5f, 1f);
        slotPix.drawRectangle(0, 0, 48, 48);
        slotBg = new Texture(slotPix);
        slotPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.4f);

        titleFont = new BitmapFont();
        titleFont.getData().setScale(2.5f);

        buildUI();
    }

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(40);

        // --- HEADER ---
        Label title = new Label("Shelter Stash Chest", new Label.LabelStyle(titleFont, Color.GOLD));
        Label subtitle = new Label("Items stored here persist across expeditions and survive death.",
                new Label.LabelStyle(font, Color.LIGHT_GRAY));
        root.add(title).colspan(2).padBottom(5).row();
        root.add(subtitle).colspan(2).padBottom(30).row();

        // --- COLUMNS CONTAINER ---
        Table columns = new Table();

        // 1. CHEST COLUMN (LEFT)
        Table chestCol = new Table();
        chestCol.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.08f, 0.08f, 0.12f, 0.85f)));
        chestCol.pad(20);

        chestCountLabel = new Label("Chest (" + chest.getItemCount() + " / " + chest.getCapacity() + ")",
                new Label.LabelStyle(font, Color.CYAN));
        chestCol.add(chestCountLabel).padBottom(15).row();

        chestTable = new Table();
        chestTable.top().left();
        ScrollPane chestScroll = new ScrollPane(chestTable);
        chestScroll.setFadeScrollBars(false);
        chestCol.add(chestScroll).expand().fill().row();

        columns.add(chestCol).width(800).expandY().fillY().padRight(40);

        // 2. BACKPACK COLUMN (RIGHT)
        Table packCol = new Table();
        packCol.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.08f, 0.08f, 0.12f, 0.85f)));
        packCol.pad(20);

        backpackCountLabel = new Label("Backpack (" + player.getInventory().getMainInventory().size() + " / 30)",
                new Label.LabelStyle(font, Color.ORANGE));
        packCol.add(backpackCountLabel).padBottom(15).row();

        backpackTable = new Table();
        backpackTable.top().left();
        ScrollPane packScroll = new ScrollPane(backpackTable);
        packScroll.setFadeScrollBars(false);
        packCol.add(packScroll).expand().fill().row();

        columns.add(packCol).width(800).expandY().fillY().row();

        root.add(columns).colspan(2).expand().fill().row();

        // --- FOOTER ---
        statusLabel = new Label("Click an item to transfer it.", new Label.LabelStyle(font, Color.WHITE));
        root.add(statusLabel).colspan(2).padTop(15).padBottom(15).row();

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.overFontColor = Color.YELLOW;
        btnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.2f, 0.2f, 0.3f, 1f));
        btnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.3f, 0.45f, 1f));

        TextButton closeBtn = new TextButton("Close Chest [ESC]", btnStyle);
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closeChest();
            }
        });
        root.add(closeBtn).colspan(2).width(300).height(50);

        stage.addActor(root);

        refreshLists();
    }

    private void refreshLists() {
        chestTable.clear();
        backpackTable.clear();

        // 1. Populate Chest
        List<Item> chestItems = chest.getItems();
        if (chestItems.isEmpty()) {
            chestTable.add(new Label("Empty Stash.", new Label.LabelStyle(font, Color.GRAY))).pad(20);
        } else {
            for (int i = 0; i < chestItems.size(); i++) {
                final int index = i;
                final Item item = chestItems.get(i);
                Table row = createItemRow(item, "Withdraw ->", Color.CYAN, new Runnable() {
                    @Override
                    public void run() {
                        withdrawItem(index, item);
                    }
                });
                chestTable.add(row).expandX().fillX().padBottom(6).row();
            }
        }

        // 2. Populate Backpack
        List<Item> packItems = player.getInventory().getMainInventory();
        if (packItems.isEmpty()) {
            backpackTable.add(new Label("Backpack is empty.", new Label.LabelStyle(font, Color.GRAY))).pad(20);
        } else {
            for (int i = 0; i < packItems.size(); i++) {
                final Item item = packItems.get(i);
                Table row = createItemRow(item, "<- Store", Color.ORANGE, new Runnable() {
                    @Override
                    public void run() {
                        storeItem(item);
                    }
                });
                backpackTable.add(row).expandX().fillX().padBottom(6).row();
            }
        }

        chestCountLabel.setText("Chest (" + chest.getItemCount() + " / " + chest.getCapacity() + ")");
        backpackCountLabel.setText("Backpack (" + player.getInventory().getMainInventory().size() + " / 30)");
    }

    private Table createItemRow(final Item item, String actionText, Color accentColor, final Runnable onAction) {
        Table row = new Table();
        row.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.15f, 0.15f, 0.22f, 0.7f)));
        row.pad(8);

        // Icon
        Image icon;
        if (item.getTextureRegion() != null) {
            icon = new Image(item.getTextureRegion());
        } else if (item.getTexture() != null) {
            icon = new Image(item.getTexture());
        } else {
            icon = new Image(slotBg);
        }
        row.add(icon).size(40, 40).padRight(15);

        // Name & Info
        Table infoTable = new Table();
        infoTable.left();
        Label nameLabel = new Label(item.getDisplayName(), new Label.LabelStyle(font, accentColor));
        String typeInfo = item.getType() != null ? item.getType().name() : "";
        Label subLabel = new Label(typeInfo, new Label.LabelStyle(font, Color.GRAY));
        subLabel.setFontScale(0.85f);
        infoTable.add(nameLabel).left().row();
        infoTable.add(subLabel).left();
        row.add(infoTable).expandX().left();

        // Action Button
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.25f, 0.25f, 0.35f, 1f));
        btnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.35f, 0.35f, 0.5f, 1f));

        TextButton actionBtn = new TextButton(actionText, btnStyle);
        actionBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onAction.run();
            }
        });
        row.add(actionBtn).width(140).height(38);

        return row;
    }

    private void withdrawItem(int index, Item item) {
        if (player.getInventory().getMainInventory().size() >= 30) {
            statusLabel.setText("Cannot withdraw: Backpack is full!");
            return;
        }

        Item removed = chest.removeItem(index);
        if (removed != null) {
            player.getInventory().pickupToBackpack(removed);
            statusLabel.setText("Withdrew " + removed.getDisplayName() + " to backpack.");
            refreshLists();
        }
    }

    private void storeItem(Item item) {
        if (chest.isFull()) {
            statusLabel.setText("Cannot store: Shelter Chest is full!");
            return;
        }

        player.getInventory().removeItem(item);
        chest.addItem(item);
        statusLabel.setText("Stored " + item.getDisplayName() + " in chest.");
        refreshLists();
    }

    private void closeChest() {
        chest.save();
        game.setScreen(parentScreen);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.O || keycode == Input.Keys.I) {
            closeChest();
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
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
