package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.bpm.minotaur.gamedata.ShopInventory;
import com.bpm.minotaur.gamedata.ShopkeeperNpc;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.gamedata.GameEvent;

/**
 * Two-panel shop window displayed when the player is adjacent to the Shopkeeper
 * NPC.
 *
 * Left panel = Shopkeeper stock (buy from)
 * Right panel = Player backpack (sell from)
 *
 * Keyboard: W/S or UP/DOWN to navigate active panel, TAB to switch panels,
 * B to buy, V to sell, ESCAPE to close.
 */
public class ShopkeeperWindow extends Table {

    // ── References ───────────────────────────────────────────────────────────
    private Player player;
    private ShopkeeperNpc shopkeeper;
    private GameEventManager eventManager;
    private ItemDataManager itemDataManager;
    private BitmapFont font;
    private Runnable onClose;

    // ── UI Children ──────────────────────────────────────────────────────────
    private final Label titleLabel;
    private final Label goldLabel;
    private final Label statusLabel;

    private final Table shopListTable;
    private final Table playerListTable;
    private final ScrollPane shopScroll;
    private final ScrollPane playerScroll;

    // Item rows (shop side)
    private java.util.List<Item> shopItems = new java.util.ArrayList<>();
    private java.util.List<Item> playerItems = new java.util.ArrayList<>();

    private int shopSelection = 0;
    private int playerSelection = 0;
    private boolean focusOnShop = true; // true = left panel active, false = right

    // ── Style resources (created once) ───────────────────────────────────────
    private TextureRegionDrawable rowNormal;
    private TextureRegionDrawable rowSelected;
    private Texture bgTexture;

    // ── Constructor ───────────────────────────────────────────────────────────
    public ShopkeeperWindow(BitmapFont font) {
        this.font = font;

        Label.LabelStyle titleStyle = new Label.LabelStyle(font, Color.GOLD);
        Label.LabelStyle bodyStyle = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle statusStyle = new Label.LabelStyle(font, Color.LIGHT_GRAY);

        // background
        bgTexture = new Texture(Gdx.files.internal("images/hud_bg.png"));
        int split = 60;
        com.badlogic.gdx.graphics.g2d.NinePatch patch = new com.badlogic.gdx.graphics.g2d.NinePatch(bgTexture, split,
                split, split, split);
        this.setBackground(new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(patch));

        rowNormal = makeRowDrawable(new Color(0.08f, 0.08f, 0.08f, 0.9f));
        rowSelected = makeRowDrawable(new Color(0.25f, 0.20f, 0.05f, 0.9f));

        this.pad(split);
        this.defaults().space(6);
        this.align(Align.top);

        // ─── Row 1: Title + gold ──────────────────────────────────────────
        titleLabel = new Label("TRAVELING MERCHANT", titleStyle);
        titleLabel.setAlignment(Align.center);
        goldLabel = new Label("Gold: 0", bodyStyle);
        goldLabel.setAlignment(Align.right);

        Table topRow = new Table();
        topRow.add(titleLabel).expandX().left();
        topRow.add(goldLabel).right().padLeft(20);
        this.add(topRow).growX().padBottom(10).row();

        // ─── Row 2: Two panels ────────────────────────────────────────────
        Label shopHeader = new Label("[ SHOP ]", titleStyle);
        Label playerHeader = new Label("[ YOUR ITEMS ]", titleStyle);

        shopListTable = new Table();
        playerListTable = new Table();

        shopScroll = new ScrollPane(shopListTable, makePaneStyle());
        playerScroll = new ScrollPane(playerListTable, makePaneStyle());

        Table panelsRow = new Table();
        panelsRow.add(shopHeader).center().expandX().padBottom(4);
        panelsRow.add(playerHeader).center().expandX().padBottom(4);
        panelsRow.row();
        panelsRow.add(shopScroll).width(500).height(400).top().padRight(20);
        panelsRow.add(playerScroll).width(500).height(400).top();
        this.add(panelsRow).growX().row();

        // ─── Row 3: Buttons ───────────────────────────────────────────────
        TextButton.TextButtonStyle btnStyle = makeButtonStyle();
        TextButton buyBtn = new TextButton("[B] BUY", btnStyle);
        TextButton sellBtn = new TextButton("[V] SELL", btnStyle);
        TextButton tabBtn = new TextButton("[TAB] SWITCH PANEL", btnStyle);
        TextButton closeBtn = new TextButton("[ESC] CLOSE", btnStyle);

        buyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                doBuy();
            }
        });
        sellBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                doSell();
            }
        });
        tabBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                switchPanel();
            }
        });
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                close();
            }
        });

        Table btnRow = new Table();
        btnRow.add(buyBtn).width(200).padRight(10);
        btnRow.add(sellBtn).width(200).padRight(10);
        btnRow.add(tabBtn).width(260).padRight(10);
        btnRow.add(closeBtn).width(200);
        this.add(btnRow).padTop(10).row();

        // ─── Row 4: Status ────────────────────────────────────────────────
        statusLabel = new Label("", statusStyle);
        this.add(statusLabel).growX().padTop(6).row();

        this.setVisible(false);
    }

    // ── Configure & Show ─────────────────────────────────────────────────────

    public void configure(Player player, ShopkeeperNpc shopkeeper,
            GameEventManager eventManager, ItemDataManager itemDataManager,
            Runnable onClose) {
        this.player = player;
        this.shopkeeper = shopkeeper;
        this.eventManager = eventManager;
        this.itemDataManager = itemDataManager;
        this.onClose = onClose;
    }

    public void show() {
        if (player == null || shopkeeper == null)
            return;

        refresh();

        this.pack();
        float w = Math.max(1100, getPrefWidth());
        float h = Math.max(700, getPrefHeight());
        this.setSize(w, h);

        if (getStage() != null) {
            setPosition(getStage().getWidth() / 2f, getStage().getHeight() / 2f, Align.center);
            getStage().setKeyboardFocus(this);
        }

        this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        this.setVisible(true);
        this.toFront();

        Gdx.input.setCursorCatched(false);
        status("Walk up to the merchant to trade. B=Buy, V=Sell, TAB=Switch Panel");
    }

    // ── Keyboard input ────────────────────────────────────────────────────────

    public boolean handleInput(int keycode) {
        if (!isVisible())
            return false;

        switch (keycode) {
            case Input.Keys.ESCAPE:
                close();
                return true;
            case Input.Keys.TAB:
                switchPanel();
                return true;
            case Input.Keys.B:
                doBuy();
                return true;
            case Input.Keys.V:
                doSell();
                return true;
            case Input.Keys.UP:
            case Input.Keys.W:
                navigate(-1);
                return true;
            case Input.Keys.DOWN:
            case Input.Keys.S:
                navigate(1);
                return true;
        }
        return false;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doBuy() {
        if (!focusOnShop || shopItems.isEmpty()) {
            status("Select an item in the SHOP panel first.");
            return;
        }
        int idx = Math.min(shopSelection, shopItems.size() - 1);
        Item item = shopItems.get(idx);
        int price = ShopInventory.getBuyPrice(item, itemDataManager);
        int gold = player.getStats().getTreasureScore();

        if (gold < price) {
            status("Not enough gold! Need " + price + "g, have " + gold + "g.");
            return;
        }
        if (!player.getInventory().pickupToBackpack(item)) {
            status("Your inventory is full!");
            return;
        }
        player.getStats().setTreasureScore(gold - price);
        shopkeeper.getInventory().removeItem(item);
        eventManager.addEvent(new GameEvent("Bought " + item.getDisplayName() + " for " + price + "g.", 2.5f));
        status("Purchased " + item.getDisplayName() + " (-" + price + "g).");
        refresh();
    }

    private void doSell() {
        if (focusOnShop || playerItems.isEmpty()) {
            status("Select an item in YOUR ITEMS panel first.");
            return;
        }
        int idx = Math.min(playerSelection, playerItems.size() - 1);
        Item item = playerItems.get(idx);
        int price = ShopInventory.getSellPrice(item, itemDataManager);

        player.getInventory().removeItem(item);
        player.getStats().setTreasureScore(player.getStats().getTreasureScore() + price);
        eventManager.addEvent(new GameEvent("Sold " + item.getDisplayName() + " for " + price + "g.", 2.5f));
        status("Sold " + item.getDisplayName() + " (+" + price + "g).");
        refresh();
    }

    private void navigate(int delta) {
        if (focusOnShop) {
            shopSelection = clamp(shopSelection + delta, 0, shopItems.size() - 1);
        } else {
            playerSelection = clamp(playerSelection + delta, 0, playerItems.size() - 1);
        }
        refresh();
    }

    private void switchPanel() {
        focusOnShop = !focusOnShop;
        status(focusOnShop ? "SHOP panel selected." : "YOUR ITEMS panel selected.");
        refresh();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refresh() {
        if (player == null || shopkeeper == null)
            return;

        goldLabel.setText("Gold: " + player.getStats().getTreasureScore() + "g");

        // ── Shop stock ──
        shopItems = shopkeeper.getInventory().getMainInventory();
        shopListTable.clearChildren();
        for (int i = 0; i < shopItems.size(); i++) {
            Item it = shopItems.get(i);
            int price = ShopInventory.getBuyPrice(it, itemDataManager);
            String txt = String.format("%-28s %5dg", it.getDisplayName(), price);
            Label lbl = new Label(txt, new Label.LabelStyle(font,
                    (focusOnShop && i == shopSelection) ? Color.GOLD : Color.WHITE));

            TextureRegionDrawable bg = (focusOnShop && i == shopSelection) ? rowSelected : rowNormal;
            Table row = new Table();
            row.setBackground(bg);
            row.add(lbl).expandX().left().pad(4, 8, 4, 8);
            shopListTable.add(row).growX().padBottom(2).row();
        }
        if (shopItems.isEmpty()) {
            shopListTable.add(new Label("Out of stock.", new Label.LabelStyle(font, Color.GRAY))).row();
        }

        // ── Player items ──
        playerItems = player.getInventory().getMainInventory();
        playerListTable.clearChildren();
        for (int i = 0; i < playerItems.size(); i++) {
            Item it = playerItems.get(i);
            int price = ShopInventory.getSellPrice(it, itemDataManager);
            String txt = String.format("%-28s %5dg", it.getDisplayName(), price);
            Label lbl = new Label(txt, new Label.LabelStyle(font,
                    (!focusOnShop && i == playerSelection) ? Color.GOLD : Color.WHITE));

            TextureRegionDrawable bg = (!focusOnShop && i == playerSelection) ? rowSelected : rowNormal;
            Table row = new Table();
            row.setBackground(bg);
            row.add(lbl).expandX().left().pad(4, 8, 4, 8);
            playerListTable.add(row).growX().padBottom(2).row();
        }
        if (playerItems.isEmpty()) {
            playerListTable.add(new Label("Nothing to sell.", new Label.LabelStyle(font, Color.GRAY))).row();
        }
    }

    private void close() {
        this.setVisible(false);
        if (shopkeeper != null) {
            shopkeeper.setState(ShopkeeperNpc.ShopkeeperState.WANDERING);
        }
        if (getStage() != null)
            getStage().setKeyboardFocus(null);
        if (onClose != null)
            onClose.run();
    }

    private void status(String msg) {
        if (statusLabel != null)
            statusLabel.setText(msg);
    }

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo)
            return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    // ── Drawable factory helpers ──────────────────────────────────────────────

    private TextureRegionDrawable makeRowDrawable(Color color) {
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(color);
        px.fill();
        Texture t = new Texture(px);
        px.dispose();
        return new TextureRegionDrawable(new TextureRegion(t));
    }

    private ScrollPane.ScrollPaneStyle makePaneStyle() {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        // Transparent backing — the row drawables provide their own background
        return style;
    }

    private TextButton.TextButtonStyle makeButtonStyle() {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = font;
        s.fontColor = Color.WHITE;
        s.overFontColor = Color.GOLD;
        s.up = makeRowDrawable(new Color(0.15f, 0.12f, 0.05f, 0.95f));
        s.over = makeRowDrawable(new Color(0.30f, 0.25f, 0.05f, 0.95f));
        s.down = makeRowDrawable(new Color(0.10f, 0.08f, 0.02f, 0.95f));
        return s;
    }

    public boolean isVisible() {
        return super.isVisible();
    }
}
