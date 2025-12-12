package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Payload;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Source;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop.Target;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.Inventory;
import com.bpm.minotaur.gamedata.Maze;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerEquipment;
import com.bpm.minotaur.managers.DebugManager;

import java.util.ArrayList;
import java.util.List;

public class InventoryScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final Maze maze;
    private Stage stage;
    private Texture whitePixel;
    private Texture slotBg;
    private BitmapFont font;

    private Label tooltipLabel;
    private Label statsLabel;

    private DragAndDrop dragAndDrop;

    private Table rootTable;
    private Table paperDollTable;
    private Table backpackTable;
    private Table quickSlotTable;

    private List<InventorySlot> allSlots = new ArrayList<>();

    private boolean hasLoggedCoordinates = false;

    public InventoryScreen(Tarmin2 game, GameScreen parentScreen, Player player, Maze maze) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
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

        Pixmap slotPix = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        slotPix.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        slotPix.fill();
        slotPix.setColor(0.5f, 0.5f, 0.5f, 1f);
        slotPix.drawRectangle(0,0,64,64);
        slotBg = new Texture(slotPix);

        pixmap.dispose();
        slotPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.5f);

        dragAndDrop = new DragAndDrop();
        dragAndDrop.setTapSquareSize(5);
        dragAndDrop.setDragTime(0);

        buildUI();
    }

    private void buildUI() {
        rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(50);

        // --- Left Panel: Paper Doll ---
        paperDollTable = new Table();
        paperDollTable.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.1f, 0.1f, 0.15f, 0.9f)));

        paperDollTable.add(createEquipSlot("Head", ItemType.HELMET)).size(64,64).colspan(3).padBottom(10).row();
        paperDollTable.add(createEquipSlot("Eyes", ItemType.EYES)).size(64,64).colspan(3).padBottom(10).row();
        paperDollTable.add(createEquipSlot("Neck", ItemType.AMULET)).size(64,64).colspan(3).padBottom(20).row();

        Table midSection = new Table();
        midSection.add(createEquipSlot("L.Hand", null)).size(64,64).padRight(20);

        Table chestColumn = new Table();
        chestColumn.add(createEquipSlot("Back", ItemType.CLOAK)).size(64,64).padBottom(5).row();
        chestColumn.add(createEquipSlot("Chest", ItemType.HAUBERK)).size(64,64);
        midSection.add(chestColumn).padRight(20);

        midSection.add(createEquipSlot("R.Hand", null)).size(64,64).row();
        paperDollTable.add(midSection).colspan(3).padBottom(20).row();

        Table lowerMid = new Table();
        lowerMid.add(createEquipSlot("Arms", ItemType.ARMS)).size(64,64).padRight(20);
        lowerMid.add(createEquipSlot("Hands", ItemType.GAUNTLETS)).size(64,64).padRight(20);
        lowerMid.add(createEquipSlot("Ring", null)).size(64,64);
        paperDollTable.add(lowerMid).colspan(3).padBottom(20).row();

        paperDollTable.add(createEquipSlot("Legs", ItemType.LEGS)).size(64,64).colspan(3).padBottom(10).row();
        paperDollTable.add(createEquipSlot("Feet", ItemType.BOOTS)).size(64,64).colspan(3).padBottom(20).row();

        statsLabel = new Label("Stats...", new Label.LabelStyle(font, Color.LIME));
        statsLabel.setAlignment(Align.center);
        paperDollTable.add(statsLabel).colspan(3).padTop(20).row();


        // --- Right Panel: Backpack ---
        backpackTable = new Table();
        backpackTable.top().left();
        Label packLabel = new Label("Backpack Storage (Right-Click to Drop)", new Label.LabelStyle(font, Color.GOLD));
        backpackTable.add(packLabel).padBottom(20).row();

        Table grid = new Table();
        for (int i = 0; i < 30; i++) {
            grid.add(createBackpackSlot(i)).size(64, 64).pad(5);
            if ((i + 1) % 5 == 0) grid.row();
        }
        backpackTable.add(grid);


        // --- Bottom Panel: Quick Slots ---
        quickSlotTable = new Table();
        Label quickLabel = new Label("Quick Slots (HUD)", new Label.LabelStyle(font, Color.CYAN));
        quickSlotTable.add(quickLabel).padBottom(10).row();
        Table quickGrid = new Table();
        for (int i = 0; i < 6; i++) {
            quickGrid.add(createQuickSlot(i)).size(64, 64).pad(10);
        }
        quickSlotTable.add(quickGrid);


        // --- Assembly ---
        Table mainSplit = new Table();
        mainSplit.add(paperDollTable).expandY().fillY().width(600).padRight(50);

        Table rightSide = new Table();
        rightSide.add(backpackTable).expand().fill().row();
        rightSide.add(quickSlotTable).height(150).fillX();

        mainSplit.add(rightSide).expand().fill();

        rootTable.add(mainSplit).expand().fill();
        stage.addActor(rootTable);

        // --- Tooltip Layer ---
        Table tooltipTable = new Table();
        tooltipTable.setFillParent(true);
        tooltipTable.bottom();
        tooltipTable.setTouchable(Touchable.disabled);

        tooltipLabel = new Label("", new Label.LabelStyle(font, Color.WHITE));
        tooltipLabel.setAlignment(Align.center);

        tooltipTable.add(tooltipLabel).padBottom(20);
        stage.addActor(tooltipTable);

        refreshSlots();
    }

    private InventorySlot createEquipSlot(String name, ItemType restrictType) {
        InventorySlot slot = new InventorySlot(SlotType.EQUIPMENT, -1, name, restrictType);
        setupDragAndDrop(slot);
        allSlots.add(slot);
        return slot;
    }

    private InventorySlot createBackpackSlot(int index) {
        InventorySlot slot = new InventorySlot(SlotType.BACKPACK, index, null, null);
        setupDragAndDrop(slot);
        allSlots.add(slot);
        return slot;
    }

    private InventorySlot createQuickSlot(int index) {
        InventorySlot slot = new InventorySlot(SlotType.QUICK_SLOT, index, "Q" + (index+1), null);
        setupDragAndDrop(slot);
        allSlots.add(slot);
        return slot;
    }

    private void refreshSlots() {
        PlayerEquipment equip = player.getEquipment();
        Inventory inv = player.getInventory();

        for (InventorySlot slot : allSlots) {
            slot.setItem(null);

            if (slot.type == SlotType.EQUIPMENT) {
                if ("Head".equals(slot.name)) slot.setItem(equip.getWornHelmet());
                else if ("Eyes".equals(slot.name)) slot.setItem(equip.getWornEyes());
                else if ("Neck".equals(slot.name)) slot.setItem(equip.getWornNeck());
                else if ("Chest".equals(slot.name)) slot.setItem(equip.getWornChest());
                else if ("Back".equals(slot.name)) slot.setItem(equip.getWornBack());
                else if ("Arms".equals(slot.name)) slot.setItem(equip.getWornArms());
                else if ("Hands".equals(slot.name)) slot.setItem(equip.getWornGauntlets());
                else if ("Legs".equals(slot.name)) slot.setItem(equip.getWornLegs());
                else if ("Feet".equals(slot.name)) slot.setItem(equip.getWornBoots());
                else if ("Ring".equals(slot.name)) slot.setItem(equip.getWornRing());
                else if ("L.Hand".equals(slot.name)) slot.setItem(inv.getLeftHand());
                else if ("R.Hand".equals(slot.name)) slot.setItem(inv.getRightHand());
            }
            else if (slot.type == SlotType.QUICK_SLOT) {
                Item[] quick = inv.getQuickSlots();
                if (slot.index < quick.length) slot.setItem(quick[slot.index]);
            }
            else if (slot.type == SlotType.BACKPACK) {
                List<Item> main = inv.getMainInventory();
                if (slot.index < main.size()) slot.setItem(main.get(slot.index));
            }
        }

        updateStatsLabel();
    }

    private void updateStatsLabel() {
        // This triggers the new debug logging in PlayerEquipment
        int armor = player.getArmorDefense();
        int war = player.getWarStrength();
        int spirit = player.getSpiritualStrength();
        int gold = player.getTreasureScore();

        statsLabel.setText(
            "Total Armor: " + armor + "\n" +
                "War Strength: " + war + "\n" +
                "Spirit Strength: " + spirit + "\n" +
                "Treasure: " + gold
        );
    }

    private void dropItem(InventorySlot slot) {
        Item item = slot.getItem();
        if (item == null) return;

        boolean success = player.dropItem(maze, item);

        if (success) {
            if (DebugManager.getInstance().isDebugOverlayVisible()) {
                Gdx.app.log("Inventory", "Dropping item success: " + item.getDisplayName());
            }
            removeItemFromSource(slot);
            refreshSlots();
        } else {
            if (DebugManager.getInstance().isDebugOverlayVisible()) {
                Gdx.app.log("Inventory", "Drop failed - No space around player.");
            }
        }
    }

    private void moveItem(InventorySlot source, InventorySlot target) {
        if (DebugManager.getInstance().isDebugOverlayVisible()) {
            Gdx.app.log("Inventory", "Moving item from " + source.getDebugName() + " to " + target.getDebugName());
        }
        Item sourceItem = source.getItem();
        if (sourceItem == null) return;

        Item targetItem = target.getItem();

        if (targetItem != null) {
            if (!source.accepts(targetItem)) {
                if (DebugManager.getInstance().isDebugOverlayVisible()) {
                    Gdx.app.log("Inventory", "Move rejected: Source cannot accept swap");
                }
                return;
            }
        }

        removeItemFromSource(source);

        if (targetItem != null) {
            addItemToSlot(source, targetItem);
        }

        addItemToSlot(target, sourceItem);
        refreshSlots();
    }

    private void removeItemFromSource(InventorySlot source) {
        if (source.type == SlotType.BACKPACK) {
            player.getInventory().getMainInventory().remove(source.getItem());
        } else if (source.type == SlotType.QUICK_SLOT) {
            player.getInventory().getQuickSlots()[source.index] = null;
        } else if (source.type == SlotType.EQUIPMENT) {
            unequipItem(source.name);
        }
    }

    private void addItemToSlot(InventorySlot target, Item item) {
        if (target.type == SlotType.BACKPACK) {
            player.getInventory().getMainInventory().add(item);
        } else if (target.type == SlotType.QUICK_SLOT) {
            player.getInventory().getQuickSlots()[target.index] = item;
        } else if (target.type == SlotType.EQUIPMENT) {
            equipItem(target.name, item);
        }
    }

    private void unequipItem(String slotName) {
        PlayerEquipment eq = player.getEquipment();
        Inventory inv = player.getInventory();

        if ("Head".equals(slotName)) eq.setWornHelmet(null);
        else if ("Eyes".equals(slotName)) eq.setWornEyes(null);
        else if ("Neck".equals(slotName)) eq.setWornNeck(null);
        else if ("Chest".equals(slotName)) eq.setWornChest(null);
        else if ("Back".equals(slotName)) eq.setWornBack(null);
        else if ("Arms".equals(slotName)) eq.setWornArms(null);
        else if ("Hands".equals(slotName)) eq.setWornGauntlets(null);
        else if ("Legs".equals(slotName)) eq.setWornLegs(null);
        else if ("Feet".equals(slotName)) eq.setWornBoots(null);
        else if ("Ring".equals(slotName)) eq.setWornRing(null);
        else if ("L.Hand".equals(slotName)) {
            inv.setLeftHand(null);
            // --- FIX: Sync shield status ---
            eq.setWornShield(null);
        }
        else if ("R.Hand".equals(slotName)) inv.setRightHand(null);
    }

    private void equipItem(String slotName, Item item) {
        PlayerEquipment eq = player.getEquipment();
        Inventory inv = player.getInventory();

        if ("Head".equals(slotName)) eq.setWornHelmet(item);
        else if ("Eyes".equals(slotName)) eq.setWornEyes(item);
        else if ("Neck".equals(slotName)) eq.setWornNeck(item);
        else if ("Chest".equals(slotName)) eq.setWornChest(item);
        else if ("Back".equals(slotName)) eq.setWornBack(item);
        else if ("Arms".equals(slotName)) eq.setWornArms(item);
        else if ("Hands".equals(slotName)) eq.setWornGauntlets(item);
        else if ("Legs".equals(slotName)) eq.setWornLegs(item);
        else if ("Feet".equals(slotName)) eq.setWornBoots(item);
        else if ("Ring".equals(slotName)) eq.setWornRing(item);
        else if ("L.Hand".equals(slotName)) {
            inv.setLeftHand(item);
            // --- FIX: If this item is a shield, also set it in PlayerEquipment so armor calcs work ---
            if (item != null && (item.getType() == ItemType.SMALL_SHIELD || item.getType() == ItemType.LARGE_SHIELD)) {
                eq.setWornShield(item);
            } else {
                eq.setWornShield(null);
            }
        }
        else if ("R.Hand".equals(slotName)) inv.setRightHand(item);
    }

    private String getTooltipText(Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getDisplayName()).append("\n");

        if (item.getCategory() != null) {
            sb.append("[").append(item.getCategory().name()).append("]\n");
        }

        if (item.isWeapon()) {
            if (item.getWarDamage() > 0) sb.append("War Dmg: ").append(item.getWarDamage()).append("  ");
            if (item.getSpiritDamage() > 0) sb.append("Spirit Dmg: ").append(item.getSpiritDamage());
            sb.append("\n");
        }

        if (item.getArmorDefense() > 0) {
            sb.append("Armor: ").append(item.getArmorDefense()).append("\n");
        }

        return sb.toString();
    }

    private void handleSmartClick(InventorySlot slot) {
        if (DebugManager.getInstance().isDebugOverlayVisible()) {
            Gdx.app.log("Inventory", "Double Click detected on " + slot.getDebugName());
        }
        Item item = slot.getItem();
        if (item == null) return;

        if (slot.type == SlotType.EQUIPMENT) {
            for (InventorySlot packSlot : allSlots) {
                if (packSlot.type == SlotType.BACKPACK && packSlot.getItem() == null) {
                    moveItem(slot, packSlot);
                    return;
                }
            }
        }
        else {
            for (InventorySlot equipSlot : allSlots) {
                if (equipSlot.type == SlotType.EQUIPMENT && equipSlot.accepts(item)) {
                    moveItem(slot, equipSlot);
                    return;
                }
            }
        }
    }


    private void setupDragAndDrop(final InventorySlot slot) {

        dragAndDrop.addSource(new Source(slot) {
            @Override
            public Payload dragStart(InputEvent event, float x, float y, int pointer) {
                if (DebugManager.getInstance().isDebugOverlayVisible()) {
                    Gdx.app.log("D&D", "DragStart on " + slot.getDebugName() + " @ " + x + "," + y);
                }

                if (slot.getItem() == null) {
                    return null;
                }

                Payload payload = new Payload();
                payload.setObject(slot);

                InventorySlot dragActor = new InventorySlot(slot.type, slot.index, slot.name, slot.restrictType);
                dragActor.setItem(slot.getItem());
                dragActor.setSize(64, 64);
                payload.setDragActor(dragActor);

                dragAndDrop.setDragActorPosition(32, -32);
                return payload;
            }
        });

        dragAndDrop.addTarget(new Target(slot) {
            @Override
            public boolean drag(Source source, Payload payload, float x, float y, int pointer) {
                InventorySlot sourceSlot = (InventorySlot) payload.getObject();

                if (slot.accepts(sourceSlot.getItem())) {
                    slot.setColor(Color.GREEN);
                    return true;
                }
                slot.setColor(Color.RED);
                return false;
            }

            @Override
            public void reset(Source source, Payload payload) {
                slot.setColor(Color.WHITE);
            }

            @Override
            public void drop(Source source, Payload payload, float x, float y, int pointer) {
                if (DebugManager.getInstance().isDebugOverlayVisible()) {
                    Gdx.app.log("D&D", "Dropped on " + slot.getDebugName());
                }
                InventorySlot sourceSlot = (InventorySlot) payload.getObject();
                moveItem(sourceSlot, slot);
            }
        });

        ClickListener clickListener = new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (slot.getItem() != null) {
                    tooltipLabel.setText(getTooltipText(slot.getItem()));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                tooltipLabel.setText("");
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (DebugManager.getInstance().isDebugOverlayVisible()) {
                    Gdx.app.log("Input", "Clicked " + slot.getDebugName() + " Btn=" + event.getButton());
                }

                // Right Click to Drop
                if (event.getButton() == Input.Buttons.RIGHT) {
                    dropItem(slot);
                    return;
                }

                // Double Click to Equip
                if (getTapCount() == 2 && event.getButton() == Input.Buttons.LEFT) {
                    handleSmartClick(slot);
                }
            }
        };

        clickListener.setButton(-1);
        slot.addListener(clickListener);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        boolean isDebug = DebugManager.getInstance().isDebugOverlayVisible();
        stage.setDebugAll(isDebug); // Toggle visuals dynamically

        stage.act(delta);
        stage.draw();

        // --- DEBUG: Log Coordinates Once ---
        if (isDebug && !hasLoggedCoordinates) {
            Gdx.app.log("InventoryDebug", "--- Slot Coordinate Dump ---");
            for (InventorySlot s : allSlots) {
                Vector2 v = s.localToStageCoordinates(new Vector2(0,0));
                Gdx.app.log("InventoryDebug", String.format("Slot: %s | ScreenXY: (%.1f, %.1f) | Size: %.1f x %.1f",
                    s.getDebugName(), v.x, v.y, s.getWidth(), s.getHeight()));
            }
            Gdx.app.log("InventoryDebug", "--- End Dump ---");
            hasLoggedCoordinates = true;
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.I || keycode == Input.Keys.ESCAPE) {
            game.setScreen(parentScreen);
            return true;
        }
        return false;
    }

    @Override
    public void dispose() {
        stage.dispose();
        if (whitePixel != null) whitePixel.dispose();
        if (slotBg != null) slotBg.dispose();
        if (font != null) font.dispose();
    }

    private enum SlotType { EQUIPMENT, BACKPACK, QUICK_SLOT }

    private class InventorySlot extends Table {
        final SlotType type;
        final int index;
        final String name;
        final ItemType restrictType;

        private Item item;

        public InventorySlot(SlotType type, int index, String name, ItemType restrictType) {
            this.type = type;
            this.index = index;
            this.name = name;
            this.restrictType = restrictType;

            setBackground(new TextureRegionDrawable(slotBg));
            setTouchable(Touchable.enabled);

            if (name != null) {
                Label l = new Label(name, new Label.LabelStyle(font, Color.GRAY));
                l.setFontScale(0.6f);
                l.setTouchable(Touchable.disabled);
                add(l).top().center().expandX().row();
            }
        }

        public String getDebugName() {
            if (type == SlotType.EQUIPMENT) return "Equip:" + name;
            if (type == SlotType.QUICK_SLOT) return "Quick:" + index;
            return "Pack:" + index;
        }

        public void setItem(Item item) {
            this.item = item;
        }

        public Item getItem() {
            return item;
        }

        public boolean accepts(Item item) {
            if (item == null) return false;

            if (type == SlotType.EQUIPMENT) {
                if ("L.Hand".equals(name) || "R.Hand".equals(name)) return true;
                if ("Chest".equals(name)) {
                    return item.getType() == ItemType.HAUBERK || item.getType() == ItemType.BREASTPLATE;
                }
                if ("Ring".equals(name)) {
                    return item.isRing();
                }
                return item.getType() == restrictType;
            }
            return true;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);

            if (item != null) {
                drawItemPixels(batch, item, getX(), getY(), getWidth(), getHeight());
            }
        }

        private void drawItemPixels(Batch batch, Item item, float x, float y, float width, float height) {
            String[] spriteData = item.getSpriteData();
            if (spriteData == null) return;

            Color c = item.getColor();
            batch.setColor(c.r, c.g, c.b, 1f);

            float pad = 8f;
            float drawW = width - pad*2;
            float drawH = height - pad*2;
            float drawX = x + pad;
            float drawY = y + pad;

            float pixelWidth = drawW / 24.0f;
            float pixelHeight = drawH / 24.0f;

            for (int row = 0; row < 24; row++) {
                for (int col = 0; col < 24; col++) {
                    if (row < spriteData.length && col < spriteData[row].length() && spriteData[row].charAt(col) == '#') {
                        batch.draw(whitePixel, drawX + col * pixelWidth, drawY + (23 - row) * pixelHeight, pixelWidth, pixelHeight);
                    }
                }
            }

            if (item.isModified()) {
                batch.setColor(1, 0.9f, 0.2f, 0.3f);
                batch.draw(whitePixel, x, y, width, height);
            }

            batch.setColor(Color.WHITE);
        }
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}
