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
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive container modal for recovering items from a fallen adventurer's corpse.
 * Features a high-contrast 'Recover All' auto-equip action and manual item looting.
 */
public class CorpseLootScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final Item corpse;
    private final Maze maze;

    private Stage stage;
    private BitmapFont font;
    private BitmapFont titleFont;
    private Texture whitePixel;
    private Texture slotBg;

    private Table corpseTable;
    private Table backpackTable;
    private Label corpseCountLabel;
    private Label backpackCountLabel;
    private Label statusLabel;

    public CorpseLootScreen(Tarmin2 game, GameScreen parentScreen, Player player, Item corpse, Maze maze) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.corpse = corpse;
        this.maze = maze;
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
        Label title = new Label("FALLEN ADVENTURER'S REMAINS", new Label.LabelStyle(titleFont, new Color(0.9f, 0.25f, 0.25f, 1f)));
        root.add(title).padTop(40).padBottom(10).colspan(3).row();

        statusLabel = new Label("Inspect remnants and recover gear.", new Label.LabelStyle(font, Color.LIGHT_GRAY));
        root.add(statusLabel).padBottom(20).colspan(3).row();

        // Main content area: Corpse (Left) vs Inventory (Right)
        Table mainArea = new Table();

        // Left: Corpse Container
        Table leftPanel = new Table();
        corpseCountLabel = new Label("Remains (" + getCorpseItems().size() + " items)", new Label.LabelStyle(font, Color.WHITE));
        leftPanel.add(corpseCountLabel).padBottom(10).row();

        corpseTable = new Table();
        populateCorpseTable();
        ScrollPane corpseScroll = new ScrollPane(corpseTable);
        corpseScroll.setFadeScrollBars(false);
        leftPanel.add(corpseScroll).size(650, 500).row();

        mainArea.add(leftPanel).padRight(40);

        // Middle: Action Buttons
        Table middlePanel = new Table();
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up = new TextureRegionDrawable(whitePixel).tint(new Color(0.2f, 0.4f, 0.2f, 0.9f));
        btnStyle.over = new TextureRegionDrawable(whitePixel).tint(new Color(0.3f, 0.6f, 0.3f, 1f));
        btnStyle.down = new TextureRegionDrawable(whitePixel).tint(new Color(0.1f, 0.3f, 0.1f, 1f));

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

        // Right: Player Backpack
        Table rightPanel = new Table();
        backpackCountLabel = new Label("Your Backpack (" + player.getInventory().getMainInventory().size() + "/30)", new Label.LabelStyle(font, Color.WHITE));
        rightPanel.add(backpackCountLabel).padBottom(10).row();

        backpackTable = new Table();
        populateBackpackTable();
        ScrollPane backpackScroll = new ScrollPane(backpackTable);
        backpackScroll.setFadeScrollBars(false);
        rightPanel.add(backpackScroll).size(650, 500).row();

        mainArea.add(rightPanel);

        root.add(mainArea).expand().fill().row();
    }

    private List<Item> getCorpseItems() {
        return corpse.getContents();
    }

    private void populateCorpseTable() {
        corpseTable.clear();
        List<Item> items = getCorpseItems();

        if (items.isEmpty()) {
            Label empty = new Label("Remains have been picked clean.", new Label.LabelStyle(font, Color.GRAY));
            corpseTable.add(empty).pad(20);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Table row = createItemRow(item, true, i);
            corpseTable.add(row).expandX().fillX().pad(4).row();
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

    private Table createItemRow(final Item item, final boolean fromCorpse, final int index) {
        Table row = new Table();
        row.setBackground(new TextureRegionDrawable(slotBg));

        String name = (item != null) ? item.getDisplayName() : "Unknown Item";
        Label label = new Label(name, new Label.LabelStyle(font, Color.WHITE));
        label.setAlignment(Align.left);
        row.add(label).expandX().fillX().padLeft(12);

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up = new TextureRegionDrawable(whitePixel).tint(fromCorpse ? new Color(0.2f, 0.5f, 0.2f, 0.8f) : new Color(0.5f, 0.2f, 0.2f, 0.8f));

        TextButton actionBtn = new TextButton(fromCorpse ? "TAKE" : "STORE", btnStyle);
        actionBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (fromCorpse) {
                    lootSingleItem(item);
                } else {
                    storeItemInCorpse(item);
                }
            }
        });
        row.add(actionBtn).width(100).height(40).padRight(8);

        return row;
    }

    private void lootSingleItem(Item item) {
        if (item == null) return;

        // Try to auto-equip armor or weapons if slots are open
        if (tryAutoEquip(item)) {
            getCorpseItems().remove(item);
            statusLabel.setText("Equipped " + item.getDisplayName() + "!");
        } else if (player.getInventory().pickup(item)) {
            getCorpseItems().remove(item);
            statusLabel.setText("Recovered " + item.getDisplayName() + ".");
        } else {
            statusLabel.setText("Inventory full! Cannot take " + item.getDisplayName() + ".");
            return;
        }

        refreshUI();
        checkCorpseDepletion();
    }

    private void storeItemInCorpse(Item item) {
        if (item == null) return;
        player.getInventory().removeItem(item);
        getCorpseItems().add(item);
        statusLabel.setText("Placed " + item.getDisplayName() + " in remains.");
        refreshUI();
    }

    private void recoverAllItems() {
        List<Item> corpseList = new ArrayList<>(getCorpseItems());
        int recovered = 0;

        for (Item item : corpseList) {
            if (tryAutoEquip(item)) {
                getCorpseItems().remove(item);
                recovered++;
            } else if (player.getInventory().pickup(item)) {
                getCorpseItems().remove(item);
                recovered++;
            }
        }

        if (recovered > 0) {
            statusLabel.setText("Recovered " + recovered + " items from fallen remains.");
        } else {
            statusLabel.setText("Backpack is completely full.");
        }

        refreshUI();
        checkCorpseDepletion();
    }

    private boolean tryAutoEquip(Item item) {
        if (item == null) return false;
        PlayerEquipment eq = player.getEquipment();
        if (eq == null) return false;

        if (item.isHelmet() && eq.getWornHelmet() == null) {
            eq.setWornHelmet(item);
            return true;
        }
        if (item.isArmor() && eq.getWornChest() == null) {
            eq.setWornChest(item);
            return true;
        }
        if (item.isGauntlets() && eq.getWornGauntlets() == null) {
            eq.setWornGauntlets(item);
            return true;
        }
        if (item.isBoots() && eq.getWornBoots() == null) {
            eq.setWornBoots(item);
            return true;
        }
        if (item.isLegs() && eq.getWornLegs() == null) {
            eq.setWornLegs(item);
            return true;
        }
        if (item.isShield() && eq.getWornShield() == null) {
            eq.setWornShield(item);
            return true;
        }
        if (item.isWeapon() && player.getInventory().getRightHand() == null) {
            player.getInventory().setRightHand(item);
            return true;
        }
        return false;
    }

    private void checkCorpseDepletion() {
        if (getCorpseItems().isEmpty()) {
            if (maze != null) {
                maze.removeItem(corpse);
            }
            statusLabel.setText("Remains fully recovered! Corpse crumbled to dust.");
        }
    }

    private void refreshUI() {
        corpseCountLabel.setText("Remains (" + getCorpseItems().size() + " items)");
        backpackCountLabel.setText("Your Backpack (" + player.getInventory().getMainInventory().size() + "/30)");
        populateCorpseTable();
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
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
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
