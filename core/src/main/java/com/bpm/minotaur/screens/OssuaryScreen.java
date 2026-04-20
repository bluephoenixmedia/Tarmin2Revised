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
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.dice.Die;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.Item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.managers.OssuaryManager;
import java.util.ArrayList;
import java.util.List;

public class OssuaryScreen extends BaseScreen {

    private final GameScreen parentScreen;
    private final Player player;
    private final OssuaryManager ossuaryManager;

    private Stage stage;
    private Texture slotBg;
    private Texture whitePixel;
    private BitmapFont font;

    // CRAFTING SLOTS
    private CraftingSlot slotStructure;
    private CraftingSlot slotEdge;
    private CraftingSlot slotCore;

    // UI ELEMENTS
    private Label previewLabel;
    private TextButton craftButton;
    private Table inventoryTable;

    public OssuaryScreen(Tarmin2 game, GameScreen parentScreen, Player player, OssuaryManager manager) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.ossuaryManager = manager;
    }

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        // Assets
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);

        Pixmap slotPix = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        slotPix.setColor(0.3f, 0.3f, 0.3f, 1f);
        slotPix.fill();
        slotPix.setColor(0.6f, 0.6f, 0.6f, 1f);
        slotPix.drawRectangle(0, 0, 64, 64);
        slotBg = new Texture(slotPix);

        pixmap.dispose();
        slotPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.5f);

        buildUI();
    }

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(50);

        // --- HEADER ---
        Label title = new Label("The Ossuary", new Label.LabelStyle(font, Color.GOLD));
        title.setFontScale(2.5f);
        root.add(title).colspan(2).padBottom(50).row();

        // --- LEFT: INVENTORY ---
        inventoryTable = new Table();
        inventoryTable.top().left();
        populateInventoryList();

        Table invContainer = new Table();
        invContainer.add(new Label("Bone Inventory", new Label.LabelStyle(font, Color.LIGHT_GRAY))).padBottom(10).row();
        invContainer.add(inventoryTable).expand().fill();

        root.add(invContainer).width(600).expandY().fillY();

        // --- RIGHT: ALTAR ---
        Table altarTable = new Table();
        altarTable.setBackground(new TextureRegionDrawable(whitePixel).tint(new Color(0.1f, 0.1f, 0.15f, 0.9f)));

        Label altarTitle = new Label("Crafting Altar", new Label.LabelStyle(font, Color.CYAN));
        altarTable.add(altarTitle).colspan(3).padBottom(30).row();

        // Slots
        slotStructure = new CraftingSlot("Structure\n(Color/Poles)");
        slotEdge = new CraftingSlot("Edge\n(Offense)");
        slotCore = new CraftingSlot("Core\n(Defense)");

        altarTable.add(slotStructure).size(128, 128).pad(20);
        altarTable.add(slotEdge).size(128, 128).pad(20);
        altarTable.add(slotCore).size(128, 128).pad(20).row();

        // Preview
        previewLabel = new Label("Place 3 Bones to Preview...", new Label.LabelStyle(font, Color.GRAY));
        previewLabel.setWrap(true);
        previewLabel.setAlignment(Align.center);
        altarTable.add(previewLabel).colspan(3).width(600).padTop(30).row();

        // Craft Button
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.up = new TextureRegionDrawable(whitePixel).tint(Color.DARK_GRAY);
        btnStyle.down = new TextureRegionDrawable(whitePixel).tint(Color.GRAY);
        btnStyle.over = new TextureRegionDrawable(whitePixel).tint(Color.LIGHT_GRAY);
        btnStyle.disabled = new TextureRegionDrawable(whitePixel).tint(new Color(0.2f, 0.2f, 0.2f, 0.5f));

        craftButton = new TextButton("Carve Die", btnStyle);
        craftButton.setDisabled(true);
        craftButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!craftButton.isDisabled()) {
                    craftDie();
                }
            }
        });

        altarTable.add(craftButton).size(200, 60).colspan(3).padTop(30);

        root.add(altarTable).expand().fill();

        stage.addActor(root);
    }

    private void populateInventoryList() {
        inventoryTable.clear();
        List<Item> bones = new ArrayList<>();

        // Filter inventory for Bones
        for (Item item : player.getInventory().getMainInventory()) {
            if (item.getType() == ItemType.BONE) {
                bones.add(item);
            }
        }

        if (bones.isEmpty()) {
            inventoryTable.add(new Label("No bones found.", new Label.LabelStyle(font, Color.GRAY)));
        } else {
            Table grid = new Table();
            int col = 0;
            for (final Item bone : bones) {
                Table itemCell = new Table();
                itemCell.setBackground(new TextureRegionDrawable(slotBg));
                Label l = new Label(bone.getDisplayName(), new Label.LabelStyle(font, Color.WHITE));
                l.setFontScale(0.8f);
                l.setWrap(true);
                l.setAlignment(Align.center);

                itemCell.add(l).width(100).pad(5);

                itemCell.setTouchable(Touchable.enabled);
                itemCell.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        onInventoryItemClicked(bone);
                    }
                });

                grid.add(itemCell).size(110, 80).pad(5);
                col++;
                if (col >= 4) {
                    col = 0;
                    grid.row();
                }
            }
            inventoryTable.add(grid).top().left();
        }
    }

    private void onInventoryItemClicked(Item item) {
        // Try filling slots in order
        if (slotStructure.getItem() == null) {
            slotStructure.setItem(item);
        } else if (slotEdge.getItem() == null) {
            slotEdge.setItem(item);
        } else if (slotCore.getItem() == null) {
            slotCore.setItem(item);
        } else {
            // All full, maybe replace structure?
            // For now, no action if full.
        }
        updatePreview();
    }

    private void onSlotClicked(CraftingSlot slot) {
        if (slot.getItem() != null) {
            slot.setItem(null); // Remove from slot (inventory is read-only here until craft)
            updatePreview();
        }
    }

    private void updatePreview() {
        // Check slots
        Item s = slotStructure.getItem();
        Item e = slotEdge.getItem();
        Item c = slotCore.getItem();

        if (s != null && e != null && c != null) {
            // Valid recipe
            Die preview = ossuaryManager.craftBoneDie(s, e, c);
            if (preview != null) {
                previewLabel.setText("Result: " + preview.getName() + "\n" +
                        "Color: " + preview.getDieColor().toString());
                previewLabel.setColor(Color.GREEN);
                craftButton.setDisabled(false);
            }
        } else {
            previewLabel.setText("Select 3 Bones to see result...");
            previewLabel.setColor(Color.GRAY);
            craftButton.setDisabled(true);
        }
    }

    // --- InputProcessor Stubs ---
    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    private void craftDie() {
        Item s = slotStructure.getItem();
        Item e = slotEdge.getItem();
        Item c = slotCore.getItem();

        if (s == null || e == null || c == null)
            return;

        // Perform Craft
        Die newDie = ossuaryManager.craftBoneDie(s, e, c);

        if (newDie != null) {
            // 1. Add to Player Dice Pool
            player.getStats().getDicePool().add(newDie);

            // 2. Consume Items from Inventory
            player.getInventory().getMainInventory().remove(s);
            player.getInventory().getMainInventory().remove(e);
            player.getInventory().getMainInventory().remove(c);

            // 3. Reset Slots
            slotStructure.setItem(null);
            slotEdge.setItem(null);
            slotCore.setItem(null);

            // 4. Refresh Inventory UI
            populateInventoryList();
            updatePreview();

            // 5. Notify
            previewLabel.setText("Crafted " + newDie.getName() + "!");
            previewLabel.setColor(Color.YELLOW);
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.05f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.I) {
            game.setScreen(parentScreen);
            return true;
        }
        return false;
    }

    @Override
    public void dispose() {
        stage.dispose();
        whitePixel.dispose();
        slotBg.dispose();
        font.dispose();
    }

    // --- INNER CLASS ---
    private class CraftingSlot extends Table {
        private Item item;
        private final Label label;
        private final String title;

        public CraftingSlot(String title) {
            this.title = title;
            setBackground(new TextureRegionDrawable(slotBg));

            label = new Label(title, new Label.LabelStyle(font, Color.GRAY));
            label.setAlignment(Align.center);
            label.setWrap(true);
            add(label).expand().fill();

            setTouchable(Touchable.enabled);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onSlotClicked(CraftingSlot.this);
                }
            });
        }

        public void setItem(Item item) {
            this.item = item;
            if (item == null) {
                label.setText(title);
                label.setColor(Color.GRAY);
            } else {
                label.setText(item.getDisplayName());
                label.setColor(Color.WHITE);
            }
        }

        public Item getItem() {
            return item;
        }
    }
}
