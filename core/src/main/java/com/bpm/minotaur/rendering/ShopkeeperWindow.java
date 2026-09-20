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

    /** Width of each of the two list panels. */
    private static final float PANEL_WIDTH = 500f;
    /**
     * Width reserved for the price column.
     *
     * <p>Prices used to be appended to the item name in one space-padded string
     * ({@code "%-28s %5dg"}), which only lined up for short, unmodified names. A display
     * name carries its prefix, enchantment and suffix -- "Vicious Longsword +2 of Flame"
     * -- so on any decorated item the padding did nothing and the price was pushed past
     * the panel edge and clipped out of sight entirely. Giving it its own cell means the
     * price is always visible and always in the same place.
     */
    private static final float PRICE_COLUMN_WIDTH = 96f;
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
    // ── Style resources (created once) ───────────────────────────────────────
    private com.badlogic.gdx.scenes.scene2d.utils.Drawable rowNormal;
    private com.badlogic.gdx.scenes.scene2d.utils.Drawable rowSelected;
    private Texture bgTexture;
    private HudSkin hudSkin;

    // ── Constructor ───────────────────────────────────────────────────────────
    public ShopkeeperWindow(BitmapFont font) {
        this(font, null);
    }

    public ShopkeeperWindow(BitmapFont font, HudSkin hudSkin) {
        this.font = font;
        this.hudSkin = hudSkin;

        Label.LabelStyle titleStyle = new Label.LabelStyle(
                (hudSkin != null) ? hudSkin.getFontHeader() : font, HudSkin.COL_GOLD_BRIGHT);
        Label.LabelStyle bodyStyle = new Label.LabelStyle(
                (hudSkin != null) ? hudSkin.getFontMain() : font, Color.WHITE);
        Label.LabelStyle headerStyle = new Label.LabelStyle(
                (hudSkin != null) ? hudSkin.getFontSmall() : font, HudSkin.COL_GOLD_MUTED);
        Label.LabelStyle statusStyle = new Label.LabelStyle(
                (hudSkin != null) ? hudSkin.getFontSmall() : font, HudSkin.COL_GOLD_ANTIQUE);

        if (hudSkin != null) {
            this.setBackground(hudSkin.getDoubleBorderPanel());
            rowNormal = hudSkin.getSlotRecessed();
            rowSelected = hudSkin.getSlotActive();
            this.pad(24f);
        } else {
            bgTexture = new Texture(Gdx.files.internal("images/hud_bg.png"));
            int split = 60;
            com.badlogic.gdx.graphics.g2d.NinePatch patch = new com.badlogic.gdx.graphics.g2d.NinePatch(bgTexture, split,
                    split, split, split);
            this.setBackground(new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(patch));
            rowNormal = makeRowDrawable(new Color(0.08f, 0.08f, 0.08f, 0.9f));
            rowSelected = makeRowDrawable(new Color(0.25f, 0.20f, 0.05f, 0.9f));
            this.pad(split);
        }

        this.defaults().space(6);
        this.align(Align.top);

        // ─── Row 1: Title + gold ──────────────────────────────────────────
        titleLabel = new Label("TRAVELING MERCHANT", titleStyle);
        titleLabel.setAlignment(Align.center);
        goldLabel = new Label("Gold: 0g", bodyStyle);
        goldLabel.setAlignment(Align.right);

        Table topRow = new Table();
        topRow.add(titleLabel).expandX().left();
        topRow.add(goldLabel).right().padLeft(20);
        this.add(topRow).growX().padBottom(10).row();

        // ─── Row 2: Two panels ────────────────────────────────────────────
        Label shopHeader = new Label("[ SHOP STOCK ]", titleStyle);
        Label playerHeader = new Label("[ YOUR BACKPACK ]", titleStyle);

        shopListTable = new Table();
        playerListTable = new Table();

        shopScroll = new ScrollPane(shopListTable, makePaneStyle());
        playerScroll = new ScrollPane(playerListTable, makePaneStyle());

        Table panelsRow = new Table();
        panelsRow.add(shopHeader).center().expandX().padBottom(4);
        panelsRow.add(playerHeader).center().expandX().padBottom(4);
        panelsRow.row();
        // Column headings, laid out with the same price-column width as the rows below
        // so the numbers sit under their label rather than wherever the name ends.
        panelsRow.add(columnHeader("ITEM", "BUY", headerStyle)).width(PANEL_WIDTH).padRight(20);
        panelsRow.add(columnHeader("ITEM", "SELL", headerStyle)).width(PANEL_WIDTH);
        panelsRow.row();
        panelsRow.add(shopScroll).width(PANEL_WIDTH).height(410).top().padRight(20);
        panelsRow.add(playerScroll).width(PANEL_WIDTH).height(410).top();
        this.add(panelsRow).growX().row();

        // ─── Row 3: Buttons ───────────────────────────────────────────────
        TextButton.TextButtonStyle primaryBtnStyle = new TextButton.TextButtonStyle();
        primaryBtnStyle.font = (hudSkin != null) ? hudSkin.getFontHeader() : font;
        primaryBtnStyle.fontColor = HudSkin.COL_TEXT_ON_GOLD;
        primaryBtnStyle.up = (hudSkin != null) ? hudSkin.getPrimaryButtonUp() : makeRowDrawable(new Color(0.85f, 0.70f, 0.25f, 1f));
        primaryBtnStyle.down = (hudSkin != null) ? hudSkin.getPrimaryButtonDown() : makeRowDrawable(new Color(0.65f, 0.50f, 0.15f, 1f));

        TextButton.TextButtonStyle secBtnStyle = makeButtonStyle();

        TextButton buyBtn = new TextButton("[B] BUY", primaryBtnStyle);
        TextButton sellBtn = new TextButton("[V] SELL", secBtnStyle);
        TextButton tabBtn = new TextButton("[TAB] SWITCH PANEL", secBtnStyle);
        TextButton gemBtn = new TextButton("[G] EXCHANGE GEMS", secBtnStyle);
        TextButton closeBtn = new TextButton("[ESC] CLOSE", secBtnStyle);

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
        gemBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                doExchangeGems();
            }
        });
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                close();
            }
        });

        Table btnRow = new Table();
        btnRow.add(buyBtn).width(180).padRight(12);
        btnRow.add(sellBtn).width(180).padRight(12);
        btnRow.add(gemBtn).width(230).padRight(12);
        btnRow.add(tabBtn).width(250).padRight(12);
        btnRow.add(closeBtn).width(180);
        this.add(btnRow).padTop(12).row();

        // ─── Row 4: Status ────────────────────────────────────────────────
        statusLabel = new Label("", statusStyle);
        statusLabel.setAlignment(Align.center);
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
        float w = 1240f;
        float h = 760f;
        this.setSize(w, h);

        if (getStage() != null) {
            setPosition(getStage().getWidth() / 2f, getStage().getHeight() / 2f, Align.center);
            getStage().setKeyboardFocus(this);
        }

        this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
        this.setVisible(true);
        this.toFront();

        Gdx.input.setCursorCatched(false);
        status("Select an item to buy or sell.");
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
            case Input.Keys.G:
                doExchangeGems();
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
        int price = effectiveBuyPrice(item);
        // Restitution: after one of his strays clips the player, the next purchase is on
        // better terms -- spent the moment a trade goes through, not on every trade after.
        boolean applyingRestitution = shopkeeper.getRestitutionDiscount() > 0f;
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
        if (applyingRestitution) {
            shopkeeper.consumeRestitutionDiscount();
            eventManager.addEvent(new GameEvent("Bought " + item.getDisplayName() + " for " + price
                    + "g. (Merchant's apology discount applied)", 2.5f));
            status("Purchased " + item.getDisplayName() + " (-" + price + "g, apology discount applied).");
        } else {
            eventManager.addEvent(new GameEvent("Bought " + item.getDisplayName() + " for " + price + "g.", 2.5f));
            status("Purchased " + item.getDisplayName() + " (-" + price + "g).");
        }
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

    /**
     * Exchanges every RUBY / SAPPHIRE / EMERALD in the player's inventory for gold
     * at the merchant's stated base value (no markup on this particular trade).
     */
    private void doExchangeGems() {
        java.util.List<Item> gems = new java.util.ArrayList<>();
        for (Item it : player.getInventory().getMainInventory()) {
            if (it.isGem()) {
                gems.add(it);
            }
        }
        if (gems.isEmpty()) {
            status("You have no gems to exchange.");
            return;
        }

        int totalGold = 0;
        for (Item gem : gems) {
            int value = ShopInventory.getSellPrice(gem, itemDataManager) * 2; // Full base value, not buyback-discounted
            totalGold += value;
            player.getInventory().removeItem(gem);
        }

        player.getStats().setTreasureScore(player.getStats().getTreasureScore() + totalGold);
        eventManager.addEvent(new GameEvent(
                "Exchanged " + gems.size() + " gem(s) for " + totalGold + "g.", 2.5f));
        status("Exchanged " + gems.size() + " gem(s) for " + totalGold + "g.");
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

    /**
     * What this item will actually cost, restitution discount included.
     *
     * <p>Shared by the list and by {@code doBuy} on purpose: a shop that advertises one
     * number and charges another is worse than one that shows no prices at all, and the
     * discount is exactly the sort of thing that drifts when two places compute it.
     */
    private int effectiveBuyPrice(Item item) {
        float discount = (shopkeeper != null) ? shopkeeper.getRestitutionDiscount() : 0f;
        return ShopInventory.getEffectiveBuyPrice(item, itemDataManager, discount);
    }

    /**
     * One list row: the item's name on the left, its price in a fixed column on the right.
     *
     * <p>The two live in separate cells rather than one padded string. A display name
     * carries its prefix, enchantment and suffix, so it can run well past any fixed pad
     * -- and when it did, the price was pushed off the panel and clipped. Here a long
     * name ellipsises and the price stays put.
     */
    private Table priceRow(String name, int price, boolean selected, Color priceColor,
            com.badlogic.gdx.scenes.scene2d.utils.Drawable background) {
        BitmapFont usedFont = (hudSkin != null) ? hudSkin.getFontMain() : font;
        Label nameLabel = new Label(name, new Label.LabelStyle(usedFont,
                selected ? HudSkin.COL_GOLD_BRIGHT : Color.WHITE));
        nameLabel.setEllipsis(true);

        Label priceLabel = new Label(price + "g", new Label.LabelStyle(usedFont, priceColor));
        priceLabel.setAlignment(Align.right);

        Table row = new Table();
        row.setBackground(background);
        // The name cell must be allowed to shrink, or the ellipsis never engages and the
        // row simply overflows the panel again.
        row.add(nameLabel).expandX().fillX().left().minWidth(0).pad(6, 10, 6, 10);
        row.add(priceLabel).width(PRICE_COLUMN_WIDTH).right().pad(6, 0, 6, 10);
        return row;
    }

    /** Column headings for a list panel, aligned to the same price column as its rows. */
    private Table columnHeader(String nameHeading, String priceHeading, Label.LabelStyle style) {
        Label nameLabel = new Label(nameHeading, style);
        Label priceLabel = new Label(priceHeading, style);
        priceLabel.setAlignment(Align.right);

        Table header = new Table();
        header.add(nameLabel).expandX().fillX().left().minWidth(0).pad(0, 8, 2, 8);
        header.add(priceLabel).width(PRICE_COLUMN_WIDTH).right().pad(0, 0, 2, 8);
        return header;
    }

    private void refresh() {
        if (player == null || shopkeeper == null)
            return;

        goldLabel.setText("Gold: " + player.getStats().getTreasureScore() + "g");

        // ── Shop stock ──
        shopItems = shopkeeper.getInventory().getMainInventory();
        shopListTable.clearChildren();
        for (int i = 0; i < shopItems.size(); i++) {
            Item it = shopItems.get(i);
            boolean selected = focusOnShop && i == shopSelection;
            int price = effectiveBuyPrice(it);
            boolean affordable = player.getStats().getTreasureScore() >= price;

            com.badlogic.gdx.scenes.scene2d.utils.Drawable bg = selected ? rowSelected : rowNormal;
            Table row = priceRow(it.getDisplayName(), price, selected,
                    // What you cannot afford is greyed, so the constraint is visible
                    // before the player commits to a purchase that will be refused.
                    affordable ? HudSkin.COL_GOLD_ANTIQUE : Color.GRAY, bg);
            shopListTable.add(row).growX().padBottom(2).row();
        }
        if (shopItems.isEmpty()) {
            BitmapFont emptyFont = (hudSkin != null) ? hudSkin.getFontSmall() : font;
            shopListTable.add(new Label("Out of stock.", new Label.LabelStyle(emptyFont, HudSkin.COL_GOLD_MUTED))).row();
        }

        // ── Player items ──
        playerItems = player.getInventory().getMainInventory();
        playerListTable.clearChildren();
        for (int i = 0; i < playerItems.size(); i++) {
            Item it = playerItems.get(i);
            boolean selected = !focusOnShop && i == playerSelection;
            int price = ShopInventory.getSellPrice(it, itemDataManager);

            com.badlogic.gdx.scenes.scene2d.utils.Drawable bg = selected ? rowSelected : rowNormal;
            Table row = priceRow(it.getDisplayName(), price, selected, HudSkin.COL_GOLD_ANTIQUE, bg);
            playerListTable.add(row).growX().padBottom(2).row();
        }
        if (playerItems.isEmpty()) {
            BitmapFont emptyFont = (hudSkin != null) ? hudSkin.getFontSmall() : font;
            playerListTable.add(new Label("Nothing to sell.", new Label.LabelStyle(emptyFont, HudSkin.COL_GOLD_MUTED))).row();
        }
    }

    public void close() {
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
        if (hudSkin != null) {
            style.background = hudSkin.getSlotRecessed();
        }
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
