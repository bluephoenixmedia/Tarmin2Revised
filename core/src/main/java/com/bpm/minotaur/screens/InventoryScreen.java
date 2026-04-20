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
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.Group;
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
import com.bpm.minotaur.managers.*;

import java.util.ArrayList;
import java.util.List;
import com.bpm.minotaur.managers.CraftingManager;
import com.bpm.minotaur.managers.CraftingManager.Recipe;
import com.bpm.minotaur.paperdoll.PaperDollWidget;

import com.bpm.minotaur.paperdoll.data.FragmentResolver;
import com.bpm.minotaur.paperdoll.data.SkeletonData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

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
    // private Table paperDollTable; // Removed
    private Table backpackTable;
    private Table quickSlotTable;

    // Paper Doll Components
    private PaperDollWidget paperDollWidget;
    private SkeletonData skeletonData;
    private FragmentResolver fragmentResolver;

    private List<InventorySlot> allSlots = new ArrayList<>();

    private boolean hasLoggedCoordinates = false;

    public enum InventoryMode {
        NORMAL, QUAFF, READ, ZAP, APPLY, WIELD, WEAR, TAKEOFF, CRAFT, COOK
    }

    private void cookItem(InventorySlot slot) {
        Item item = slot.getItem();
        if (item == null)
            return;

        Gdx.app.log("Inventory", "Attempting to cook: " + item.getType().name());

        if (item.getType() != ItemType.CORPSE && item.getType() != ItemType.MEAT)
            return;

        removeItemFromSource(slot);

        com.bpm.minotaur.gamedata.item.ItemDataManager dm = game.getItemDataManager();
        com.badlogic.gdx.assets.AssetManager am = game.getAssetManager();

        // 1. Cooked Meat with Random Effect
        Item cooked = dm.createItem(ItemType.COOKED_MEAT, (int) player.getPosition().x, (int) player.getPosition().y,
                com.bpm.minotaur.gamedata.item.ItemColor.RED, am);

        com.bpm.minotaur.gamedata.item.PotionEffectType[] effects = com.bpm.minotaur.gamedata.item.PotionEffectType
                .values();
        com.bpm.minotaur.gamedata.item.PotionEffectType effect = effects[(int) (Math.random() * effects.length)];
        cooked.setTrueEffect(effect);
        cooked.setIdentified(false);

        String msg = "Cooked " + cooked.getFriendlyName();
        if (player.getInventory().pickupToBackpack(cooked)) {
            msg += " (Added to Pack)";
        } else {
            maze.addItem(cooked);
            msg += " (Dropped)";
        }

        // 2. Monster Specific Loot
        com.bpm.minotaur.gamedata.monster.Monster.MonsterType source = item.getCorpseSource();
        Item loot = null;
        if (source != null) {
            switch (source) {
                case WERERAT:
                    loot = dm.createItem(ItemType.TOOTH, (int) player.getPosition().x, (int) player.getPosition().y,
                            com.bpm.minotaur.gamedata.item.ItemColor.WHITE, am);
                    break;
                case SPIDER:
                    loot = dm.createItem(ItemType.CLAW, (int) player.getPosition().x, (int) player.getPosition().y,
                            com.bpm.minotaur.gamedata.item.ItemColor.GRAY, am);
                    break;
                case SKELETON:
                case CLOAKED_SKELETON:
                    loot = dm.createItem(ItemType.BONE, (int) player.getPosition().x, (int) player.getPosition().y,
                            com.bpm.minotaur.gamedata.item.ItemColor.WHITE, am);
                    break;
                case ZOMBIE:
                    loot = dm.createItem(ItemType.BLOOD_VIAL, (int) player.getPosition().x,
                            (int) player.getPosition().y,
                            com.bpm.minotaur.gamedata.item.ItemColor.RED, am);
                    break;
                case HARPY:
                    loot = dm.createItem(ItemType.EYES, (int) player.getPosition().x, (int) player.getPosition().y,
                            com.bpm.minotaur.gamedata.item.ItemColor.RED, am);
                    break;
                default:
                    loot = dm.createItem(ItemType.BONE, (int) player.getPosition().x, (int) player.getPosition().y,
                            com.bpm.minotaur.gamedata.item.ItemColor.WHITE, am);
                    break;
            }
        } else {
            loot = dm.createItem(ItemType.BONE, (int) player.getPosition().x, (int) player.getPosition().y,
                    com.bpm.minotaur.gamedata.item.ItemColor.WHITE, am);
        }

        if (loot != null) {
            if (player.getInventory().pickupToBackpack(loot)) {
                // Already picked up
            } else {
                maze.addItem(loot); // Drop if full
            }
        }

        tooltipLabel.setText(msg);
        refreshSlots();
    }

    private final InventoryMode mode;
    private CraftingManager craftingManager;

    public InventoryScreen(Tarmin2 game, GameScreen parentScreen, Player player, Maze maze) {
        this(game, parentScreen, player, maze, InventoryMode.NORMAL);
    }

    public InventoryScreen(Tarmin2 game, GameScreen parentScreen, Player player, Maze maze, InventoryMode mode) {
        super(game);
        this.parentScreen = parentScreen;
        this.player = player;
        this.maze = maze;
        this.mode = mode;
    }

    public void setCraftingManager(CraftingManager cm) {
        this.craftingManager = cm;
    }

    private Texture paperDollTexture;
    private Texture headTexture;

    // ... existing fields ...

    @Override
    public void show() {
        stage = new Stage(new FitViewport(1920, 1080), game.getBatch());

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);

        // Load Paper Doll
        try {
            paperDollTexture = new Texture(Gdx.files.internal("images/inventory_paper_doll.png"));
        } catch (Exception e) {
            Gdx.app.error("Inventory", "Could not load paper doll image", e);
            // Fallback to a simple color if missing, to prevent crash
            Pixmap p = new Pixmap(400, 600, Pixmap.Format.RGBA8888);
            p.setColor(Color.DARK_GRAY);
            p.fill();
            paperDollTexture = new Texture(p);
            p.dispose();
        }

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        whitePixel = new Texture(pixmap);

        Pixmap slotPix = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        slotPix.setColor(0.2f, 0.2f, 0.2f, 0.8f);
        slotPix.fill();
        slotPix.setColor(0.5f, 0.5f, 0.5f, 1f);
        slotPix.drawRectangle(0, 0, 64, 64);
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
        // We use a WidgetGroup or just a Table with a Container/Group to allow absolute
        // positioning
        // layout: Root -> Split -> Left(GroupContainer) / Right(Table)

        // --- SCALING LOGIC ---
        // Target width for the paper doll to fit nicely in the UI
        float targetWidth = 900f; // Increased from 550f. 1100f might be too tall for 1080p, 900 is safer but
                                  // larger.
        float scale = targetWidth / paperDollTexture.getWidth();
        float scaledHeight = paperDollTexture.getHeight() * scale;

        // Clamp to screen height with some padding if needed
        if (scaledHeight > 900) {
            scaledHeight = 900;
            scale = scaledHeight / paperDollTexture.getHeight();
            targetWidth = paperDollTexture.getWidth() * scale;
        }

        // Initialize Paper Doll Components
        // ALWAYS Reload to pick up Editor changes (skeleton.json)
        skeletonData = new SkeletonData();
        // Live Reload: Check for source file first
        if (Gdx.files.local("assets/data/skeleton.json").exists()) {
            skeletonData.load(Gdx.files.local("assets/data/skeleton.json"));
            Gdx.app.log("Inventory", "Live Reloading skeleton.json from source.");
        } else {
            skeletonData.load(Gdx.files.internal("data/skeleton.json"));
        }

        TextureAtlas armorAtlas = game.getAssetManager().get("packed/armor.atlas", TextureAtlas.class);

        TextureAtlas weaponsAtlas = game.getAssetManager().get("packed/weapons.atlas", TextureAtlas.class);

        fragmentResolver = new FragmentResolver(armorAtlas, weaponsAtlas);
        paperDollWidget = new PaperDollWidget(skeletonData, fragmentResolver);

        // Sync Inventory Items with freshly loaded Templates (from Editor, via
        // armor.json)
        // This ensures existing items update their visuals immediately.
        com.bpm.minotaur.gamedata.item.ItemDataManager dm = game.getItemDataManager();
        if (player != null && player.getInventory() != null) {
            java.util.List<Item> itemsToSync = new java.util.ArrayList<>();
            if (player.getInventory() != null)
                itemsToSync.addAll(player.getInventory().getAllItems());
            if (player.getEquipment() != null)
                itemsToSync.addAll(player.getEquipment().getAllEquipped());

            for (Item item : itemsToSync) {
                if (item == null)
                    continue;
                try {
                    com.bpm.minotaur.gamedata.item.ItemTemplate t = dm.getTemplate(item.getType());
                    item.setOffsetX(t.offsetX);
                    item.setOffsetY(t.offsetY);
                    item.setRotation(t.rotation); // Sync rotation
                    if (item.getScale() != null)
                        item.getScale().set(t.scaleX, t.scaleY);
                } catch (Exception e) {
                }
            }
        }

        // Pass debug assets (texture and font are already created in show())
        if (whitePixel != null && font != null) {
            paperDollWidget.setDebugAssets(whitePixel, font);
        }

        Group paperDollGroup = new Group();
        Image paperDollImage = new Image(paperDollTexture); // Old Static Image
        paperDollImage.setSize(targetWidth, scaledHeight);

        // Setup Widget Size and Scale
        paperDollWidget.setSize(targetWidth, scaledHeight);
        // We might need to scale the widget's content?
        // The widget draws fragments at offsets defined in skeleton.json.
        // If skeleton.json coordinates are in pixels matching the original image size,
        // we need to scale the Group to match targetWidth/ImageWidth.
        paperDollWidget.setScale(scale);

        // Add a background placeholder if needed, or the base body
        paperDollGroup.setSize(targetWidth, scaledHeight);

        // Add static image first (Background/Body)
        paperDollGroup.addActor(paperDollImage);
        // Add widget second (Armor/Equipment Layers)
        paperDollGroup.addActor(paperDollWidget);

        // --- Head Overlay removed from here (moved to PaperDollWidget fragments for
        // Z-sorting) ---

        // Define slot positions (relative to the paper doll image bottom-left)
        // These are ESTIMATES and should be tuned based on the actual image.
        // We center the slots horizontally based on the image width.

        float slotSize = 64;

        // Define standard column X positions (relative to 0..1 scale of targetWidth)
        float leftColX = targetWidth * 0.14f; // Moved slightly in from 0.15 to avoid edge
        float rightColX = targetWidth * 0.82f;

        // Define row Y positions (relative to 0..1 scale of scaledHeight)
        // Adjust these to match the visual "boxes" in the image.
        float row1Y = scaledHeight * 0.76f; // Head / Neck
        float row2Y = scaledHeight * 0.59f; // Chest / Back
        float row3Y = scaledHeight * 0.40f; // Hands / MainHand
        float row4Y = scaledHeight * 0.24f; // Legs / OffHand
        float row5Y = scaledHeight * 0.06f; // Feet / Rings

        // --- Left Column ---
        addSlotToGroup(paperDollGroup, "Head", ItemType.HELMET, (leftColX - slotSize / 2), row1Y);
        addSlotToGroup(paperDollGroup, "Chest", ItemType.HAUBERK, (leftColX - slotSize / 2), row2Y);
        addSlotToGroup(paperDollGroup, "Hands", ItemType.GAUNTLETS, (leftColX - slotSize / 2) - 50, row3Y);
        addSlotToGroup(paperDollGroup, "Legs", ItemType.LEGS, (leftColX - slotSize / 2), row4Y);
        addSlotToGroup(paperDollGroup, "Feet", ItemType.BOOTS, (leftColX - slotSize / 2), row5Y);

        // --- Right Column ---
        addSlotToGroup(paperDollGroup, "Neck", ItemType.AMULET, (rightColX - slotSize / 2) - 15, row1Y);
        addSlotToGroup(paperDollGroup, "Backpack", ItemType.BACKPACK, (rightColX - slotSize / 2) - 70, row2Y);
        addSlotToGroup(paperDollGroup, "Back", ItemType.CLOAK, rightColX - slotSize / 2, row2Y);
        addSlotToGroup(paperDollGroup, "R.Hand", ItemType.SWORD, rightColX - slotSize / 2, row3Y); // Main Hand (Sword
                                                                                                   // icon)
        addSlotToGroup(paperDollGroup, "L.Hand", ItemType.SHIELD, rightColX - slotSize / 2, row4Y); // Off Hand (Shield
                                                                                                    // icon)

        // Rings / Accessories (Row 5 split?)
        // The screenshot shows two slots side-by-side or close at the bottom right?
        // Let's put Ring and Eyes (Diamond) there.
        addSlotToGroup(paperDollGroup, "Ring", ItemType.RING, (rightColX - slotSize - 10) - 70, row5Y);
        addSlotToGroup(paperDollGroup, "Ring 2", ItemType.RING, rightColX - slotSize - 10, row5Y);
        addSlotToGroup(paperDollGroup, "Eyes", ItemType.EYES, rightColX + 10, row5Y);

        // Extra Slots
        // "Arms" (Bracers) - Not clearly visible, let's put it next to Hands (Left
        // Column) slightly offset?
        // Or visually hide it if it's not important? No, it's equipment.
        // Let's put it to the right of Hands (towards body)
        addSlotToGroup(paperDollGroup, "Arms", ItemType.ARMS, (leftColX + slotSize + 10) - 50, row3Y);

        statsLabel = new Label("Stats...", new Label.LabelStyle(font, Color.LIME));
        statsLabel.setAlignment(Align.center);
        // Position stats label at bottom
        statsLabel.setPosition(targetWidth / 2f - 50, 20); // Re-using cx logic for stats label
        paperDollGroup.addActor(statsLabel);

        // Put the group in a container to center it in the left table cell
        Container<Group> dollContainer = new Container<>(paperDollGroup);
        // dollContainer.setBackground(new TextureRegionDrawable(whitePixel).tint(new
        // Color(0.1f, 0.1f, 0.15f, 0.5f))); // Optional dark backing

        // --- Right Panel: Backpack ---
        backpackTable = new Table();
        backpackTable.top().right(); // Align content to right
        String title = "Backpack Storage (Right-Click to Drop)";
        Color titleColor = Color.GOLD;

        if (mode != InventoryMode.NORMAL) {
            title = "SELECT ITEM TO " + mode.name() + " (Click to select)";
            titleColor = Color.GREEN;
        }

        Label packLabel = new Label(title, new Label.LabelStyle(font, titleColor));
        packLabel.setAlignment(Align.right);
        backpackTable.add(packLabel).padBottom(20).right().row();

        if (mode == InventoryMode.CRAFT) {
            Table recipeList = new Table();
            if (craftingManager != null) {
                for (final Recipe recipe : craftingManager.getAllRecipes()) {
                    boolean canCraft = craftingManager.canCraft(player.getInventory(), recipe);
                    Color c = canCraft ? Color.GREEN : Color.GRAY;
                    StringBuilder inputsBuilder = new StringBuilder();
                    inputsBuilder.append("Requires: ");
                    java.util.Map<com.bpm.minotaur.gamedata.item.Item.ItemType, Integer> counts = new java.util.HashMap<>();
                    for (com.bpm.minotaur.gamedata.item.Item.ItemType t : recipe.inputs) {
                        counts.put(t, counts.getOrDefault(t, 0) + 1);
                    }
                    int i = 0;
                    for (java.util.Map.Entry<com.bpm.minotaur.gamedata.item.Item.ItemType, Integer> entry : counts
                            .entrySet()) {
                        if (i > 0)
                            inputsBuilder.append(", ");
                        inputsBuilder.append(entry.getKey().name()).append(" x").append(entry.getValue());
                        i++;
                    }
                    String labelText = recipe.description + " [" + inputsBuilder.toString() + "]";

                    Label l = new Label(labelText, new Label.LabelStyle(font, c));
                    l.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            craftItem(recipe);
                        }
                    });
                    recipeList.add(l).right().pad(10).row();
                }
            } else {
                recipeList.add(new Label("No Crafting Manager Linked", new Label.LabelStyle(font, Color.RED)));
            }
            backpackTable.add(recipeList).right();
        } else {
            Table grid = new Table();
            for (int i = 0; i < 30; i++) {
                grid.add(createBackpackSlot(i)).size(64, 64).pad(5);
                if ((i + 1) % 5 == 0)
                    grid.row();
            }
            backpackTable.add(grid).right();
        }

        // --- Bottom Panel: Quick Slots ---
        quickSlotTable = new Table();
        Label quickLabel = new Label("Quick Slots (HUD)", new Label.LabelStyle(font, Color.CYAN));
        quickLabel.setAlignment(Align.right);
        quickSlotTable.add(quickLabel).padBottom(10).right().row();
        Table quickGrid = new Table();
        for (int i = 0; i < 6; i++) {
            quickGrid.add(createQuickSlot(i)).size(64, 64).pad(10);
        }
        quickSlotTable.add(quickGrid).right();

        // --- Assembly ---
        Table mainSplit = new Table();
        mainSplit.add(dollContainer).left().expandY().fillY().padRight(50); // Left align doll

        Table rightSide = new Table();
        rightSide.add(backpackTable).top().right().expand().fillX().row();

        // --- Known Spells ---
        Table spellsTable = new Table();
        Label spellsLabel = new Label("Known Spells", new Label.LabelStyle(font, Color.CYAN));
        spellsLabel.setAlignment(Align.right);
        spellsTable.add(spellsLabel).padBottom(5).right().row();

        if (player.getKnownSpells() == null || player.getKnownSpells().isEmpty()) {
            spellsTable.add(new Label("None", new Label.LabelStyle(font, Color.GRAY))).right();
        } else {
            for (com.bpm.minotaur.gamedata.spells.SpellType spell : player.getKnownSpells()) {
                Label l = new Label(spell.getDisplayName() + " [" + spell.getMpCost() + " MP]",
                        new Label.LabelStyle(font, Color.WHITE));
                spellsTable.add(l).right().padRight(5).row();
            }
        }
        rightSide.add(spellsTable).right().padTop(10).padBottom(10).row();

        // Alchemy Button
        TextButton alchemyBtn = new TextButton("Alchemy", new TextButton.TextButtonStyle(null, null, null, font));
        // Simple style reuse or create new one if needed, InventoryScreen likely has
        // basic styles?
        // Actually InventoryScreen uses buildUI and likely has styles/fonts setup.
        // But here I'm using `game.getSkin()` in previous failed attempt?
        // InventoryScreen doesn't seem to have a global skin variable.
        // It constructs things manually?
        // Let's check how other buttons are made or just use a basic one.
        // The previous code utilized `game.getSkin()` which was wrong.
        // Let's make a style using the exiting font/textures in InventoryScreen if
        // possible.
        // "whitePixel" and "font" are available fields in InventoryScreen? Yes.

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.up = new TextureRegionDrawable(new TextureRegionDrawable(slotBg)); // Reuse slotBg
        btnStyle.fontColor = Color.GREEN;

        alchemyBtn.setStyle(btnStyle);

        alchemyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AlchemyScreen(game, InventoryScreen.this.parentScreen, player));
                dispose();
            }
        });
        rightSide.add(alchemyBtn).right().pad(5).row();

        rightSide.add(quickSlotTable).bottom().right().height(150).fillX();

        mainSplit.add(rightSide).expand().fill().right(); // Right align right side

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

    private void addSlotToGroup(Group group, String name, ItemType restrictType, float x, float y) {
        InventorySlot slot = createEquipSlot(name, restrictType);
        slot.setSize(110, 110);
        slot.setPosition(x, y);
        group.addActor(slot);
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
        InventorySlot slot = new InventorySlot(SlotType.QUICK_SLOT, index, "Q" + (index + 1), null);
        setupDragAndDrop(slot);
        allSlots.add(slot);
        return slot;
    }

    private void refreshSlots() {
        PlayerEquipment equip = player.getEquipment();
        Inventory inv = player.getInventory();

        // --- Update Paper Doll Widget ---
        if (paperDollWidget != null) {
            paperDollWidget.clearEquipment();

            // --- HEAD OVERLAY (Z-Sorted) ---
            if (headTexture == null) {
                try {
                    headTexture = new Texture(Gdx.files.internal("images/inventory_doll_head.png"));
                } catch (Exception e) {
                }
            }
            if (headTexture != null) {
                com.badlogic.gdx.graphics.g2d.TextureRegion headRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(
                        headTexture);
                // Z=53 (Plate=50, Helm=60).
                // Use "face" socket from Editor if available, otherwise fallback.
                com.badlogic.gdx.math.Vector2 facePos = skeletonData.getSocketPosition("face");

                com.bpm.minotaur.paperdoll.data.DollFragment headFrag;
                if (facePos != null && (facePos.x != 0 || facePos.y != 0)) {
                    // Editor Mode: Absolute coordinates (Socket "face")
                    // Z-Index 80 (As requested: Layer 2, below Helmet 90)
                    headFrag = new com.bpm.minotaur.paperdoll.data.DollFragment(headRegion, 80, "face", 1f, 1f);
                    headFrag.localOffset.set(0, 0);
                    // The widget uses skeleton data for the "head" socket position.
                    // We just need to ensure the fragment is added.
                    paperDollWidget.setBody(headFrag);
                } else {
                    // Fallback Mode: Relative to "head" socket
                    // Z=53
                    headFrag = new com.bpm.minotaur.paperdoll.data.DollFragment(headRegion, 53, "head", 1f, 1f);
                    headFrag.localOffset.set(385, 220);
                    paperDollWidget.setBody(headFrag);
                }
            }

            // Add Base Body (Hardcoded for now, could be dynamic based on race/gender)
            // paperDollWidget.setBody(new DollFragment(bodyRegion, ...));

            // Iterate all equipment and equip to doll
            // Note: We need to map slot items to widget logic.
            // The widget's equip() method resolves items by Type.

            if (equip.getWornHelmet() != null)
                paperDollWidget.equip(equip.getWornHelmet());
            if (equip.getWornChest() != null)
                paperDollWidget.equip(equip.getWornChest());
            if (equip.getWornLegs() != null)
                paperDollWidget.equip(equip.getWornLegs());
            if (equip.getWornBoots() != null)
                paperDollWidget.equip(equip.getWornBoots());
            if (equip.getWornGauntlets() != null)
                paperDollWidget.equip(equip.getWornGauntlets());
            if (equip.getWornArms() != null)
                paperDollWidget.equip(equip.getWornArms());
            if (equip.getWornBack() != null)
                paperDollWidget.equip(equip.getWornBack());
            if (equip.getWornEyes() != null)
                paperDollWidget.equip(equip.getWornEyes());
            if (equip.getWornNeck() != null)
                paperDollWidget.equip(equip.getWornNeck());
            if (equip.getWornRing() != null)
                paperDollWidget.equip(equip.getWornRing());
            if (equip.getWornRing2() != null)
                paperDollWidget.equip(equip.getWornRing2());

            // Weapons
            if (inv.getRightHand() != null)
                paperDollWidget.equip(inv.getRightHand());
            if (inv.getLeftHand() != null)
                paperDollWidget.equip(inv.getLeftHand()); // Shield or Offhand
        }

        for (InventorySlot slot : allSlots) {
            slot.setItem(null);

            if (slot.type == SlotType.EQUIPMENT) {
                if ("Head".equals(slot.name))
                    slot.setItem(equip.getWornHelmet());
                else if ("Eyes".equals(slot.name))
                    slot.setItem(equip.getWornEyes());
                else if ("Neck".equals(slot.name))
                    slot.setItem(equip.getWornNeck());
                else if ("Chest".equals(slot.name))
                    slot.setItem(equip.getWornChest());
                else if ("Back".equals(slot.name))
                    slot.setItem(equip.getWornBack());
                else if ("Arms".equals(slot.name))
                    slot.setItem(equip.getWornArms());
                else if ("Hands".equals(slot.name))
                    slot.setItem(equip.getWornGauntlets());
                else if ("Legs".equals(slot.name))
                    slot.setItem(equip.getWornLegs());
                else if ("Feet".equals(slot.name))
                    slot.setItem(equip.getWornBoots());
                else if ("Ring".equals(slot.name))
                    slot.setItem(equip.getWornRing());
                else if ("Ring 2".equals(slot.name))
                    slot.setItem(equip.getWornRing2());
                else if ("L.Hand".equals(slot.name))
                    slot.setItem(inv.getLeftHand());
                else if ("R.Hand".equals(slot.name))
                    slot.setItem(inv.getRightHand());
            } else if (slot.type == SlotType.QUICK_SLOT) {
                Item[] quick = inv.getQuickSlots();
                if (slot.index < quick.length)
                    slot.setItem(quick[slot.index]);
            } else if (slot.type == SlotType.BACKPACK) {
                List<Item> main = inv.getMainInventory();
                if (slot.index < main.size())
                    slot.setItem(main.get(slot.index));
            }
        }

        updateStatsLabel();
    }

    private void updateStatsLabel() {
        // This triggers the new debug logging in PlayerEquipment
        int armor = player.getArmorClass();
        int war = player.getCurrentHP();
        int spirit = player.getCurrentMP();
        int gold = player.getTreasureScore();

        statsLabel.setText(
                "Armor Class: " + armor + "\n" +
                        "Current HP: " + war + "\n" +
                        "Current MP: " + spirit + "\n" +
                        "Treasure: " + gold);
    }

    private void dropItem(InventorySlot slot) {
        Item item = slot.getItem();
        if (item == null)
            return;

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
        if (sourceItem == null)
            return;

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

        if ("Head".equals(slotName))
            eq.setWornHelmet(null);
        else if ("Eyes".equals(slotName))
            eq.setWornEyes(null);
        else if ("Neck".equals(slotName))
            eq.setWornNeck(null);
        else if ("Chest".equals(slotName))
            eq.setWornChest(null);
        else if ("Back".equals(slotName))
            eq.setWornBack(null);
        else if ("Arms".equals(slotName))
            eq.setWornArms(null);
        else if ("Hands".equals(slotName))
            eq.setWornGauntlets(null);
        else if ("Legs".equals(slotName))
            eq.setWornLegs(null);
        else if ("Feet".equals(slotName))
            eq.setWornBoots(null);
        else if ("Ring".equals(slotName))
            eq.setWornRing(null);
        else if ("Ring 2".equals(slotName))
            eq.setWornRing2(null);
        else if ("L.Hand".equals(slotName)) {
            inv.setLeftHand(null);
            // --- FIX: Sync shield status ---
            eq.setWornShield(null);
        } else if ("R.Hand".equals(slotName))
            inv.setRightHand(null);
    }

    private void equipItem(String slotName, Item item) {
        PlayerEquipment eq = player.getEquipment();
        Inventory inv = player.getInventory();

        if ("Head".equals(slotName))
            eq.setWornHelmet(item);
        else if ("Eyes".equals(slotName))
            eq.setWornEyes(item);
        else if ("Neck".equals(slotName))
            eq.setWornNeck(item);
        else if ("Chest".equals(slotName))
            eq.setWornChest(item);
        else if ("Back".equals(slotName))
            eq.setWornBack(item);
        else if ("Arms".equals(slotName))
            eq.setWornArms(item);
        else if ("Hands".equals(slotName))
            eq.setWornGauntlets(item);
        else if ("Legs".equals(slotName))
            eq.setWornLegs(item);
        else if ("Feet".equals(slotName))
            eq.setWornBoots(item);
        else if ("Ring".equals(slotName))
            eq.setWornRing(item);
        else if ("Ring 2".equals(slotName))
            eq.setWornRing2(item);
        else if ("L.Hand".equals(slotName)) {
            inv.setLeftHand(item);
            // --- FIX: If this item is a shield, also set it in PlayerEquipment so armor
            // calcs work ---
            boolean isShield = item != null && item.isShield();
            if (item != null && !isShield) {
                try {
                    com.bpm.minotaur.gamedata.item.ItemTemplate t = InventoryScreen.this.game.getItemDataManager()
                            .getTemplate(item.getType());
                    if (t != null && t.isShield)
                        isShield = true;
                } catch (Exception e) {
                }
            }
            if (isShield) {
                eq.setWornShield(item);
            } else {
                eq.setWornShield(null);
            }
        } else if ("R.Hand".equals(slotName))
            inv.setRightHand(item);
    }

    private String getTooltipText(Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getDisplayName()).append("\n");

        if (item.getCategory() != null) {
            sb.append("[").append(item.getCategory().name()).append("]\n");
        }

        if (item.isWeapon()) {
            sb.append("Dmg: ").append(item.getDamageDice()).append("\n");
        }

        if (item.getArmorClassBonus() > 0) {
            sb.append("AC: +").append(item.getArmorClassBonus()).append("\n");
        }

        return sb.toString();
    }

    private void handleSmartClick(InventorySlot slot) {
        if (DebugManager.getInstance().isDebugOverlayVisible()) {
            Gdx.app.log("Inventory", "Double Click detected on " + slot.getDebugName());
        }
        Item item = slot.getItem();
        if (item == null)
            return;

        if (slot.type == SlotType.EQUIPMENT) {
            for (InventorySlot packSlot : allSlots) {
                if (packSlot.type == SlotType.BACKPACK && packSlot.getItem() == null) {
                    moveItem(slot, packSlot);
                    return;
                }
            }
        } else {
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

                if (mode != InventoryMode.NORMAL) {
                    if (event.getButton() == Input.Buttons.LEFT) {
                        handleSelection(slot);
                    }
                    return;
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
                Vector2 v = s.localToStageCoordinates(new Vector2(0, 0));
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
        if (whitePixel != null)
            whitePixel.dispose();
        if (slotBg != null)
            slotBg.dispose();
        if (font != null)
            font.dispose();
        if (paperDollTexture != null)
            paperDollTexture.dispose();
    }

    private enum SlotType {
        EQUIPMENT, BACKPACK, QUICK_SLOT
    }

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
            if (type == SlotType.EQUIPMENT)
                return "Equip:" + name;
            if (type == SlotType.QUICK_SLOT)
                return "Quick:" + index;
            return "Pack:" + index;
        }

        public void setItem(Item item) {
            this.item = item;
        }

        public Item getItem() {
            return item;
        }

        public boolean accepts(Item item) {
            if (item == null)
                return false;

            if (type == SlotType.EQUIPMENT) {
                if ("L.Hand".equals(name)) {
                    if (restrictType == ItemType.SHIELD)
                        return item.isShield();
                    return true;
                }
                if ("R.Hand".equals(name))
                    return true;

                if ("Head".equals(name)) {
                    boolean valid = item.isHelmet();
                    if (!valid) {
                        try {
                            com.bpm.minotaur.gamedata.item.ItemTemplate t = InventoryScreen.this.game
                                    .getItemDataManager().getTemplate(item.getType());
                            if (t != null && t.isHelmet)
                                valid = true;
                        } catch (Exception e) {
                            Gdx.app.error("Inventory", "Error checking template for helmet fallback", e);
                        }
                    }
                    if (!valid) {
                        Gdx.app.log("InventoryDebug",
                                "REJECTED Head slot. Type: " + item.getType() + " isHelmet: " + item.isHelmet());
                    }
                    return valid;
                }
                if ("Eyes".equals(name)) // Assuming isEyes exists or check type
                    return item.getType() == ItemType.EYES; // EYES seems to be single type still
                if ("Neck".equals(name))
                    return item.getType() == ItemType.AMULET || item.getType() == ItemType.NECKLACE;
                if ("Back".equals(name))
                    return item.isCloak(); // Use boolean flag
                if ("Chest".equals(name))
                    return item.isArmor() && !item.isHelmet() && !item.isShield() && !item.isRing()
                            && !item.isGauntlets() && !item.isBoots() && !item.isLegs() && !item.isArms()
                            && !item.isCloak();
                if ("Arms".equals(name))
                    return item.isArms();
                if ("Hands".equals(name))
                    return item.isGauntlets();
                if ("Legs".equals(name))
                    return item.isLegs();
                if ("Feet".equals(name))
                    return item.isBoots();
                if ("Ring".equals(name) || "Ring 2".equals(name)) {
                    return item.isRing();
                }

                // Fallback for strict matches if not covered above
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
            // Check for Modern Rendering
            // Check for Modern Rendering
            if (DebugManager.getInstance().getRenderMode() == DebugManager.RenderMode.MODERN) {
                boolean drawn = false;
                if (item.getTextureRegion() != null) {
                    float pad = 8f;
                    batch.draw(item.getTextureRegion(), x + pad, y + pad, width - pad * 2, height - pad * 2);
                    drawn = true;
                } else if (item.getTexture() != null) {
                    float pad = 8f;
                    batch.draw(item.getTexture(), x + pad, y + pad, width - pad * 2, height - pad * 2);
                    drawn = true;
                }

                if (drawn) {
                    if (item.isModified()) {
                        batch.setColor(1, 0.9f, 0.2f, 0.3f);
                        batch.draw(whitePixel, x, y, width, height);
                        batch.setColor(Color.WHITE);
                    }
                    return;
                }
            }

            // Retro Rendering (Sprite Data)
            String[] spriteData = item.getSpriteData();
            if (spriteData == null)
                return;

            Color c = item.getColor();
            batch.setColor(c.r, c.g, c.b, 1f);

            float pad = 8f;
            float drawW = width - pad * 2;
            float drawH = height - pad * 2;
            float drawX = x + pad;
            float drawY = y + pad;

            float pixelWidth = drawW / 24.0f;
            float pixelHeight = drawH / 24.0f;

            for (int row = 0; row < 24; row++) {
                for (int col = 0; col < 24; col++) {
                    if (row < spriteData.length && col < spriteData[row].length()
                            && spriteData[row].charAt(col) == '#') {
                        batch.draw(whitePixel, drawX + col * pixelWidth, drawY + (23 - row) * pixelHeight, pixelWidth,
                                pixelHeight);
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

    private void handleSelection(InventorySlot slot) {
        Item item = slot.getItem();
        if (item == null)
            return;

        if (mode == InventoryMode.COOK) {
            Gdx.app.log("Inventory", "Cooking interaction: Clicked on " + item.getType().name());
            if (item.getType() == ItemType.CORPSE || item.getType() == ItemType.MEAT) {
                Gdx.app.log("Inventory", "Valid cooking item found.");
                cookItem(slot);
            } else {
                Gdx.app.log("Inventory", "Invalid cooking item: " + item.getType().name());
                tooltipLabel.setText("You can only cook Corpses or Meat!");
            }
            return;
        }

        parentScreen.handleInventorySelection(item, mode);
        game.setScreen(parentScreen);
    }

    private void craftItem(Recipe recipe) {
        if (craftingManager == null)
            return;
        Item result = craftingManager.craft(player.getInventory(), recipe);
        if (result != null) {
            String msg = "Crafted " + result.getFriendlyName();
            if (!player.getInventory().pickup(result)) {
                // Inventory Full: Drop to ground
                GridPoint2 pos = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
                maze.getItems().put(pos, result);
                msg += " (Dropped)";
            }
            tooltipLabel.setText(msg);

            // Rebuild UI to update ingredient status (colors)
            stage.clear();
            buildUI();
        } else {
            tooltipLabel.setText("Missing Ingredients!");
        }
    }

}
