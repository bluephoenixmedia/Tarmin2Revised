package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ShelterChest;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.DivinityManager;
import com.bpm.minotaur.rendering.HudSkin;

import java.util.List;

/**
 * Interface for the persistent Starting Shelter Stash Chest.
 * Allows depositing and withdrawing items between the player's pack and the chest.
 * Styled via HudSkin's "Forged Iron & Ember" theme, matching the Artisan's Workshop
 * and Cooking Hearth screens: a chest column and a backpack column flanking a
 * central transfer-button rail, with click-to-select + explicit STORE/TAKE actions
 * (or a double-click for an instant transfer).
 */
public class ShelterChestScreen extends BaseScreen {

    private enum Side { CHEST, PACK }

    private final GameScreen parentScreen;
    private final Player player;
    private final ShelterChest chest;
    private final HudSkin hudSkin;

    private Stage stage;

    private Table chestRows;
    private Table packRows;
    private Label chestCountLabel;
    private Label packCountLabel;
    private Label statusLabel;

    private TextButton storeBtn;
    private TextButton takeBtn;
    private TextButton storeAllBtn;

    private Label divinityBarLabel;
    private TextButton upgradeLootRetentionBtn;

    private Item selectedItem;
    private Side selectedSide;

    public ShelterChestScreen(Tarmin2 game, GameScreen parentScreen, Player player, ShelterChest chest) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.chest = chest;
        this.hudSkin = new HudSkin();
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

        buildUI();
    }

    private void buildUI() {
        Table backdrop = new Table();
        backdrop.setFillParent(true);
        backdrop.setBackground(hudSkin.getScreenBackdrop());
        stage.addActor(backdrop);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(40);

        // --- HEADER ---
        Table header = new Table();
        header.setBackground(hudSkin.getDoubleBorderPanel());
        header.pad(18, 24, 18, 24);
        Label title = new Label("THE SHELTER STASH CHEST", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        title.setFontScale(1.3f);
        header.add(title).center().row();
        Label subtitle = new Label("Items stored here persist across expeditions and survive death",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        header.add(subtitle).center().padTop(6).row();
        root.add(header).fillX().padBottom(20).row();

        // --- DIVINITY SHRINE: spend banked Divinities on permanent upgrades ---
        Table shrine = new Table();
        shrine.setBackground(hudSkin.getPanelBg());
        shrine.pad(14, 22, 14, 22);
        divinityBarLabel = new Label("", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_BRIGHT));
        shrine.add(divinityBarLabel).left().expandX();
        upgradeLootRetentionBtn = createActionButton("UPGRADE LOOT RETENTION");
        upgradeLootRetentionBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                purchaseLootRetentionUpgrade();
            }
        });
        shrine.add(upgradeLootRetentionBtn).width(320).height(48).right();
        root.add(shrine).fillX().padBottom(20).row();

        // --- BODY: Chest | Transfer Rail | Backpack ---
        Table body = new Table();

        Table chestPanel = buildColumnPanel();
        chestCountLabel = new Label("", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        Table chestHeaderRow = new Table();
        chestHeaderRow.add(new Label("STASH CHEST", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_MUTED))).left().expandX();
        chestHeaderRow.add(chestCountLabel).right();
        chestPanel.add(chestHeaderRow).fillX().padBottom(14).row();

        chestRows = new Table();
        chestRows.top().left();
        ScrollPane chestScroll = new ScrollPane(chestRows);
        chestScroll.setFadeScrollBars(false);
        chestPanel.add(chestScroll).expand().fill().row();

        Label chestHint = new Label("Store gibs and spare gear before descending -- the forge and hearth draw from this chest as well as your pack.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        chestHint.setWrap(true);
        chestPanel.add(chestHint).width(680).left().padTop(14).row();

        body.add(chestPanel).width(760).expandY().fillY().padRight(20);

        // Center transfer rail
        Table centerCol = new Table();
        storeBtn = createActionButton("<- STORE");
        storeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                transferSelected();
            }
        });
        takeBtn = createActionButton("TAKE ->");
        takeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                transferSelected();
            }
        });
        storeAllBtn = createActionButton("STORE ALL GIBS");
        storeAllBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                storeAllGibs();
            }
        });
        centerCol.add(storeBtn).width(210).height(56).padBottom(18).row();
        centerCol.add(takeBtn).width(210).height(56).padBottom(34).row();
        centerCol.add(storeAllBtn).width(210).height(64).row();
        body.add(centerCol).width(230).center();

        Table packPanel = buildColumnPanel();
        packCountLabel = new Label("", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_BRIGHT));
        Table packHeaderRow = new Table();
        packHeaderRow.add(new Label("BACKPACK", new Label.LabelStyle(hudSkin.getFontHeader(), HudSkin.COL_GOLD_MUTED))).left().expandX();
        packHeaderRow.add(packCountLabel).right();
        packPanel.add(packHeaderRow).fillX().padBottom(14).row();

        packRows = new Table();
        packRows.top().left();
        ScrollPane packScroll = new ScrollPane(packRows);
        packScroll.setFadeScrollBars(false);
        packPanel.add(packScroll).expand().fill().row();

        Label packHint = new Label("Click an item, then STORE/TAKE -- or double-click to transfer instantly.",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        packHint.setWrap(true);
        packPanel.add(packHint).width(680).left().padTop(14).row();

        body.add(packPanel).width(760).expandY().fillY();

        root.add(body).expand().fill().padBottom(16).row();

        // --- FOOTER ---
        Table footer = new Table();
        footer.setBackground(hudSkin.getPanelBg());
        footer.pad(14, 22, 14, 22);
        Label keyHints = new Label("[↑↓] SELECT   [ENTER] TRANSFER   [A] STORE ALL GIBS   [ESC] CLOSE CHEST",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        footer.add(keyHints).left().expandX();
        statusLabel = new Label("Click an item, then [STORE] or [TAKE].",
                new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_FOOD_GREEN));
        footer.add(statusLabel).right();
        root.add(footer).fillX();

        stage.addActor(root);

        refreshLists();
    }

    private Table buildColumnPanel() {
        Table panel = new Table();
        panel.setBackground(hudSkin.getDoubleBorderPanel());
        panel.top().left();
        panel.pad(20);
        return panel;
    }

    private TextButton createActionButton(String text) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = hudSkin.getFontHeader();
        style.disabled = hudSkin.getSlotRecessed();
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(0.78f);
        setButtonEnabled(btn, false);
        return btn;
    }

    private void setButtonEnabled(TextButton btn, boolean enabled) {
        TextButton.TextButtonStyle style = btn.getStyle();
        if (enabled) {
            style.up = hudSkin.getPrimaryButtonUp();
            style.down = hudSkin.getPrimaryButtonDown();
            style.over = hudSkin.getPrimaryButtonDown();
            style.fontColor = HudSkin.COL_TEXT_ON_GOLD;
            style.overFontColor = HudSkin.COL_TEXT_ON_GOLD;
        } else {
            style.up = hudSkin.getSlotRecessed();
            style.down = hudSkin.getSlotRecessed();
            style.over = hudSkin.getSlotRecessed();
            style.fontColor = HudSkin.COL_GOLD_MUTED;
            style.overFontColor = HudSkin.COL_GOLD_MUTED;
        }
        btn.setDisabled(!enabled);
    }

    private Table createItemRow(final Item item, final Side side) {
        Table row = new Table();
        if (item == null) {
            return row;
        }

        boolean isSelected = (item == selectedItem && side == selectedSide);
        row.setBackground(isSelected ? hudSkin.getSlotActive() : hudSkin.getSlotRecessed());
        row.pad(10);

        Stack iconStack = new Stack();
        iconStack.add(new Image(hudSkin.getHazardStripeIcon()));
        try {
            if (item.getTextureRegion() != null) {
                iconStack.add(new Image(item.getTextureRegion()));
            } else if (item.getTexture() != null) {
                iconStack.add(new Image(item.getTexture()));
            }
        } catch (Exception e) {
            Gdx.app.error("ShelterChestScreen", "Could not render icon for item", e);
        }
        row.add(iconStack).size(48).padRight(16);

        Table infoCol = new Table();
        infoCol.left();
        String displayName = item.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = item.getType() != null ? item.getType().name() : "Unknown Item";
        }
        Label nameLbl = new Label(displayName,
                new Label.LabelStyle(hudSkin.getFontMain(), isSelected ? HudSkin.COL_GOLD_BRIGHT : HudSkin.COL_GOLD_ANTIQUE));
        String typeInfo = item.getType() != null ? item.getType().name() : "";
        Label codeLbl = new Label(typeInfo, new Label.LabelStyle(hudSkin.getFontSmall(), HudSkin.COL_GOLD_MUTED));
        infoCol.add(nameLbl).left().row();
        infoCol.add(codeLbl).left();
        row.add(infoCol).expandX().left();

        row.setTouchable(Touchable.enabled);
        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (getTapCount() >= 2) {
                    if (side == Side.CHEST) withdrawItem(item); else storeItem(item);
                } else {
                    selectedItem = item;
                    selectedSide = side;
                    refreshLists();
                }
            }
        });

        return row;
    }

    private void refreshLists() {
        chestRows.clear();
        packRows.clear();

        List<Item> chestItems = chest.getItems();
        if (chestItems.isEmpty()) {
            chestRows.add(new Label("EMPTY STASH", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED))).pad(30);
        } else {
            for (Item item : chestItems) {
                if (item != null) {
                    chestRows.add(createItemRow(item, Side.CHEST)).expandX().fillX().padBottom(8).row();
                }
            }
        }

        List<Item> packItems = player.getInventory().getMainInventory();
        if (packItems.isEmpty()) {
            packRows.add(new Label("BACKPACK IS EMPTY", new Label.LabelStyle(hudSkin.getFontMain(), HudSkin.COL_GOLD_MUTED))).pad(30);
        } else {
            for (Item item : packItems) {
                if (item != null) {
                    packRows.add(createItemRow(item, Side.PACK)).expandX().fillX().padBottom(8).row();
                }
            }
        }

        chestCountLabel.setText(String.format("%02d / %02d", chest.getItemCount(), chest.getCapacity()));
        packCountLabel.setText(String.format("%02d / %02d", packItems.size(), player.getInventory().getMaxBackpackSize()));

        updateActionButtons();
        refreshDivinityBar();
    }

    private void refreshDivinityBar() {
        DivinityManager dm = DivinityManager.getInstance();
        int divinities = dm.getCurrentDivinities();
        int cap = dm.getLootRetentionCap();
        if (dm.isLootRetentionMaxed()) {
            divinityBarLabel.setText(String.format(
                    "%s: %d   |   Loot Retention MAXED -- keep %d unequipped items on death",
                    DivinityManager.DIVINITY_NAME, divinities, cap));
            setButtonEnabled(upgradeLootRetentionBtn, false);
        } else {
            int nextCost = dm.getNextLootRetentionUpgradeCost();
            divinityBarLabel.setText(String.format(
                    "%s: %d   |   Loot Retention Lv %d (keep %d on death)   |   Next: %d %s",
                    DivinityManager.DIVINITY_NAME, divinities, dm.getLootRetentionUpgradeLevel(), cap,
                    nextCost, DivinityManager.DIVINITY_NAME));
            setButtonEnabled(upgradeLootRetentionBtn, divinities >= nextCost);
        }
    }

    private void purchaseLootRetentionUpgrade() {
        DivinityManager dm = DivinityManager.getInstance();
        if (dm.purchaseLootRetentionUpgrade()) {
            if (parentScreen != null && parentScreen.getSoundManager() != null) {
                parentScreen.getSoundManager().playCoins();
            }
            statusLabel.setText("Loot Retention upgraded! You'll now keep " + dm.getLootRetentionCap()
                    + " unequipped items on death.");
        } else {
            statusLabel.setText("Not enough Divinities for that upgrade.");
        }
        refreshDivinityBar();
    }

    private void updateActionButtons() {
        boolean canStore = selectedItem != null && selectedSide == Side.PACK && !chest.isFull();
        boolean canTake = selectedItem != null && selectedSide == Side.CHEST
                && player.getInventory().getCarriedCount() < player.getInventory().getMaxBackpackSize();

        boolean anyGibs = false;
        for (Item it : player.getInventory().getMainInventory()) {
            if (it != null && it.getType() != null && it.getType().name().startsWith("GIB_")) {
                anyGibs = true;
                break;
            }
        }

        setButtonEnabled(storeBtn, canStore);
        setButtonEnabled(takeBtn, canTake);
        setButtonEnabled(storeAllBtn, anyGibs && !chest.isFull());
    }

    private void transferSelected() {
        if (selectedItem == null) {
            statusLabel.setText("Select an item first.");
            return;
        }
        if (parentScreen != null && parentScreen.getSoundManager() != null) {
            parentScreen.getSoundManager().playUiClick();
        }
        if (selectedSide == Side.PACK) {
            storeItem(selectedItem);
        } else {
            withdrawItem(selectedItem);
        }
    }

    private void storeAllGibs() {
        List<Item> pack = player.getInventory().getMainInventory();
        java.util.List<Item> toMove = new java.util.ArrayList<>();
        for (Item it : pack) {
            if (it != null && it.getType() != null && it.getType().name().startsWith("GIB_")) {
                toMove.add(it);
            }
        }
        if (toMove.isEmpty()) {
            statusLabel.setText("No gibs in backpack to store.");
            return;
        }

        int moved = 0;
        for (Item it : toMove) {
            if (chest.isFull()) break;
            player.getInventory().removeItem(it);
            chest.addItem(it);
            moved++;
        }

        selectedItem = null;
        statusLabel.setText("Stored " + moved + " gib" + (moved == 1 ? "" : "s") + " in the chest.");
        refreshLists();
    }

    private void withdrawItem(Item item) {
        if (player.getInventory().getCarriedCount() >= player.getInventory().getMaxBackpackSize()) {
            statusLabel.setText("Cannot withdraw: backpack is full!");
            return;
        }

        chest.removeItem(item);
        player.getInventory().pickupToBackpack(item);
        statusLabel.setText("Withdrew " + item.getDisplayName() + " to backpack.");
        selectedItem = null;
        refreshLists();
    }

    private void storeItem(Item item) {
        if (chest.isFull()) {
            statusLabel.setText("Cannot store: shelter chest is full!");
            return;
        }

        player.getInventory().removeItem(item);
        chest.addItem(item);
        statusLabel.setText("Stored " + item.getDisplayName() + " in chest.");
        selectedItem = null;
        refreshLists();
    }

    private void moveSelection(int delta) {
        Side side = selectedSide != null ? selectedSide : Side.CHEST;
        List<Item> list = side == Side.CHEST ? chest.getItems() : player.getInventory().getMainInventory();
        if (list.isEmpty()) return;
        int idx = selectedItem != null ? list.indexOf(selectedItem) : -1;
        idx = Math.floorMod(idx < 0 ? 0 : idx + delta, list.size());
        selectedItem = list.get(idx);
        selectedSide = side;
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
        if (keycode == Input.Keys.ENTER) {
            transferSelected();
            return true;
        }
        if (keycode == Input.Keys.A) {
            storeAllGibs();
            return true;
        }
        if (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN) {
            moveSelection(keycode == Input.Keys.UP ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(10f / 255f, 8f / 255f, 6f / 255f, 1f);
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
        if (hudSkin != null) hudSkin.dispose();
    }
}
