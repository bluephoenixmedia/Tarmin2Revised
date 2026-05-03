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
import com.bpm.minotaur.gamedata.DamageType;
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
import com.bpm.minotaur.screens.inventory.ModernInventoryUI;

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
    private BitmapFont smallFont;
    private Table statsTable;
    private Table leftStatsCol;
    private Table rightStatsCol;
    private Table resistancesRow;
    private Table statusFxRow;

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
    private DebugManager.RenderMode renderMode;

    // Modern inventory UI (replaces buildUI() when not in RETRO mode)
    private ModernInventoryUI modernUI;

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

        renderMode = DebugManager.getInstance().getRenderMode();

        // ── MODERN mode: delegate entirely to the new UI system ────────────────
        if (renderMode != DebugManager.RenderMode.RETRO) {
            // Initialise a dummy tooltipLabel so legacy methods don't NPE if called
            font = new BitmapFont();
            tooltipLabel = new Label("", new Label.LabelStyle(font, Color.WHITE));

            modernUI = new ModernInventoryUI(
                    player, maze, game.getAssetManager(), game.getItemDataManager());
            modernUI.addToStage(stage);
            modernUI.refresh();
            return;
        }

        // Load background texture (retro uses its own inventory PNG)
        String dollPath = (renderMode == DebugManager.RenderMode.RETRO)
                ? "images/ui/retro_inventory.png"
                : "images/inventory_paper_doll.png";
        try {
            paperDollTexture = new Texture(Gdx.files.internal(dollPath));
        } catch (Exception e) {
            Gdx.app.error("Inventory", "Could not load paper doll image", e);
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

        // Retro: transparent slot bg – the PNG already draws the slot boxes
        Pixmap slotPix;
        if (renderMode == DebugManager.RenderMode.RETRO) {
            slotPix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            slotPix.setColor(0f, 0f, 0f, 0f);
            slotPix.fill();
        } else {
            slotPix = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
            slotPix.setColor(0.2f, 0.2f, 0.2f, 0.8f);
            slotPix.fill();
            slotPix.setColor(0.5f, 0.5f, 0.5f, 1f);
            slotPix.drawRectangle(0, 0, 64, 64);
        }
        slotBg = new Texture(slotPix);

        pixmap.dispose();
        slotPix.dispose();

        font = new BitmapFont();
        font.getData().setScale(1.5f);

        smallFont = new BitmapFont();
        smallFont.getData().setScale(1.0f);

        dragAndDrop = new DragAndDrop();
        dragAndDrop.setTapSquareSize(5);
        dragAndDrop.setDragTime(0);

        buildUI();
    }

    // ─────────────────────────────────────────────────────────────────
    // RETRO INVENTORY UI
    // ─────────────────────────────────────────────────────────────────

    /** Adds a transparent, drag-and-drop-enabled equipment slot to a Group. */
    private void addSlotRetro(Group group, String name, Item.ItemType type, float x, float y, float sz) {
        InventorySlot slot = createEquipSlot(name, type);
        slot.setSize(sz, sz);
        slot.setPosition(x, y);
        group.addActor(slot);
    }

    private void buildRetroUI() {
        rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.pad(50);

        // Scale the retro inventory image to fit the left panel
        float targetWidth = 900f;
        float stageScale  = targetWidth / paperDollTexture.getWidth();
        float scaledHeight = paperDollTexture.getHeight() * stageScale;
        if (scaledHeight > 900f) {
            scaledHeight  = 900f;
            stageScale    = scaledHeight / paperDollTexture.getHeight();
            targetWidth   = paperDollTexture.getWidth() * stageScale;
        }

        // Slot layout is derived from the Python generator constants:
        //   LW=342, LH=256, SCALE=7, S=36, G=4
        // Each logical unit maps to (7 * stageScale) stage pixels.
        float ps     = 7f * stageScale;   // one Python-logical-pixel in stage coords
        float slotSz = 36f * ps;          // slot size in stage coords (~95 px)

        // Column X positions (left-to-right in stage coords)
        float lx1 = 11f  * ps;   // left col, single/first slot
        float lx2 = 51f  * ps;   // left col, second slot (gloves row 2, boots row 2)
        float rx1 = 255f * ps;   // right col, first slot
        float rx2 = 295f * ps;   // right col, second slot

        // Row Y positions (LibGDX: 0 = bottom, flip from image top-origin)
        float ry1 = scaledHeight - 12f  * ps - slotSz;
        float ry2 = scaledHeight - 52f  * ps - slotSz;
        float ry3 = scaledHeight - 92f  * ps - slotSz;
        float ry4 = scaledHeight - 132f * ps - slotSz;
        float ry5 = scaledHeight - 172f * ps - slotSz;

        // Background image + slot group
        Group retroGroup = new Group();
        retroGroup.setSize(targetWidth, scaledHeight);
        Image bgImage = new Image(paperDollTexture);
        bgImage.setSize(targetWidth, scaledHeight);
        retroGroup.addActor(bgImage);

        // ── Left column – armour ──────────────────────────────────────
        addSlotRetro(retroGroup, "Head",  Item.ItemType.HELMET,    lx1, ry1, slotSz);
        addSlotRetro(retroGroup, "Chest", Item.ItemType.HAUBERK,   lx1, ry2, slotSz);
        addSlotRetro(retroGroup, "Hands", Item.ItemType.GAUNTLETS, lx1, ry3, slotSz);
        addSlotRetro(retroGroup, "Arms",  Item.ItemType.ARMS,      lx2, ry3, slotSz);
        addSlotRetro(retroGroup, "Legs",  Item.ItemType.LEGS,      lx1, ry4, slotSz);
        addSlotRetro(retroGroup, "Feet",  Item.ItemType.BOOTS,     lx1, ry5, slotSz);
        addSlotRetro(retroGroup, null,    Item.ItemType.BOOTS,     lx2, ry5, slotSz); // decorative

        // ── Right column – accessories ────────────────────────────────
        addSlotRetro(retroGroup, "Neck",     Item.ItemType.AMULET,   rx2, ry1, slotSz);
        addSlotRetro(retroGroup, "Backpack", Item.ItemType.BACKPACK, rx1, ry2, slotSz);
        addSlotRetro(retroGroup, "Back",     Item.ItemType.CLOAK,    rx2, ry2, slotSz);
        addSlotRetro(retroGroup, "R.Hand",   Item.ItemType.SWORD,    rx1, ry3, slotSz);
        addSlotRetro(retroGroup, "L.Hand",   Item.ItemType.SHIELD,   rx2, ry3, slotSz);
        addSlotRetro(retroGroup, "Ring",     Item.ItemType.RING,     rx1, ry4, slotSz);
        addSlotRetro(retroGroup, "Ring 2",   Item.ItemType.RING,     rx2, ry4, slotSz);
        addSlotRetro(retroGroup, "Eyes",     Item.ItemType.EYES,     rx1, ry5, slotSz);
        addSlotRetro(retroGroup, null,       Item.ItemType.EYES,     rx2, ry5, slotSz); // decorative

        // Stats label at bottom of group
        statsLabel = new Label("", new Label.LabelStyle(font, Color.GOLD));
        statsLabel.setAlignment(Align.center);
        statsLabel.setPosition(targetWidth / 2f - 50f, 5f);
        retroGroup.addActor(statsLabel);

        Container<Group> dollContainer = new Container<>(retroGroup);

        // ── Right panel – backpack / quick slots / spells / alchemy ──
        backpackTable = new Table();
        backpackTable.top().right();
        String title      = "Backpack Storage (Right-Click to Drop)";
        Color  titleColor = Color.GOLD;
        if (mode != InventoryMode.NORMAL) {
            title      = "SELECT ITEM TO " + mode.name() + " (Click to select)";
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
                    StringBuilder sb = new StringBuilder("Requires: ");
                    java.util.Map<Item.ItemType, Integer> counts = new java.util.HashMap<>();
                    for (Item.ItemType t : recipe.inputs)
                        counts.put(t, counts.getOrDefault(t, 0) + 1);
                    int i = 0;
                    for (java.util.Map.Entry<Item.ItemType, Integer> entry : counts.entrySet()) {
                        if (i++ > 0) sb.append(", ");
                        sb.append(entry.getKey().name()).append(" x").append(entry.getValue());
                    }
                    Label l = new Label(recipe.description + " [" + sb + "]", new Label.LabelStyle(font, c));
                    l.addListener(new ClickListener() {
                        @Override public void clicked(InputEvent event, float x, float y) { craftItem(recipe); }
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
                if ((i + 1) % 5 == 0) grid.row();
            }
            backpackTable.add(grid).right();
        }

        quickSlotTable = new Table();
        quickSlotTable.add(new Label("Quick Slots (HUD)", new Label.LabelStyle(font, Color.CYAN)))
                .padBottom(10).right().row();
        Table quickGrid = new Table();
        for (int i = 0; i < 6; i++)
            quickGrid.add(createQuickSlot(i)).size(64, 64).pad(10);
        quickSlotTable.add(quickGrid).right();

        // Spells + alchemy — left side below the doll
        Table spellsTable = new Table();
        spellsTable.add(new Label("Known Spells", new Label.LabelStyle(font, Color.CYAN))).padBottom(5).left().row();
        if (player.getKnownSpells() == null || player.getKnownSpells().isEmpty()) {
            spellsTable.add(new Label("None", new Label.LabelStyle(font, Color.GRAY))).left();
        } else {
            for (com.bpm.minotaur.gamedata.spells.SpellType spell : player.getKnownSpells()) {
                spellsTable.add(new Label(spell.getDisplayName() + " [" + spell.getMpCost() + " MP]",
                        new Label.LabelStyle(font, Color.WHITE))).left().padRight(5).row();
            }
        }

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font      = font;
        btnStyle.up        = new TextureRegionDrawable(whitePixel).tint(new Color(0.15f, 0.12f, 0.05f, 0.9f));
        btnStyle.fontColor = Color.GOLD;
        TextButton alchemyBtn = new TextButton("Alchemy", btnStyle);
        alchemyBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AlchemyScreen(game, InventoryScreen.this.parentScreen, player));
                dispose();
            }
        });

        Table leftSide = new Table();
        leftSide.add(dollContainer).top().expandY().fillY().row();
        leftSide.add(spellsTable).bottom().left().fillX().padTop(5).row();
        leftSide.add(alchemyBtn).bottom().left().pad(5);

        Table rightSide = new Table();
        rightSide.add(backpackTable).top().right().expandX().fillX().row();
        rightSide.add(quickSlotTable).right().fillX().padTop(5).row();
        rightSide.add(buildStatsTable()).bottom().fillX().padTop(5);

        Table mainSplit = new Table();
        mainSplit.add(leftSide).left().expandY().fillY().padRight(10);
        mainSplit.add(rightSide).expand().fill().right();

        rootTable.add(mainSplit).expand().fill();
        stage.addActor(rootTable);

        // Tooltip layer
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

    // ─────────────────────────────────────────────────────────────────
    // MODERN INVENTORY UI
    // ─────────────────────────────────────────────────────────────────

    private void buildUI() {
        if (renderMode == DebugManager.RenderMode.RETRO) {
            buildRetroUI();
            return;
        }

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
        // Spells + alchemy move to left side below the doll
        Table spellsTable = new Table();
        Label spellsLabel = new Label("Known Spells", new Label.LabelStyle(font, Color.CYAN));
        spellsLabel.setAlignment(Align.left);
        spellsTable.add(spellsLabel).padBottom(5).left().row();
        if (player.getKnownSpells() == null || player.getKnownSpells().isEmpty()) {
            spellsTable.add(new Label("None", new Label.LabelStyle(font, Color.GRAY))).left();
        } else {
            for (com.bpm.minotaur.gamedata.spells.SpellType spell : player.getKnownSpells()) {
                spellsTable.add(new Label(spell.getDisplayName() + " [" + spell.getMpCost() + " MP]",
                        new Label.LabelStyle(font, Color.WHITE))).left().padRight(5).row();
            }
        }

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.up = new TextureRegionDrawable(new TextureRegionDrawable(slotBg));
        btnStyle.fontColor = Color.GREEN;
        TextButton alchemyBtn = new TextButton("Alchemy", btnStyle);
        alchemyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AlchemyScreen(game, InventoryScreen.this.parentScreen, player));
                dispose();
            }
        });

        Table leftSide = new Table();
        leftSide.add(dollContainer).top().expandY().fillY().row();
        leftSide.add(spellsTable).bottom().left().fillX().padTop(5).row();
        leftSide.add(alchemyBtn).bottom().left().pad(5);

        Table rightSide = new Table();
        rightSide.add(backpackTable).top().right().expandX().fillX().row();
        rightSide.add(quickSlotTable).right().fillX().padTop(5).row();
        rightSide.add(buildStatsTable()).bottom().fillX().padTop(5);

        Table mainSplit = new Table();
        mainSplit.add(leftSide).left().expandY().fillY().padRight(10);
        mainSplit.add(rightSide).expand().fill().right();

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
        refreshStatsTable();
    }

    private void updateStatsLabel() {
        statsLabel.setText(
            "HP: " + player.getCurrentHP() + "/" + player.getEffectiveMaxHP() +
            "  MP: " + player.getCurrentMP() + "/" + player.getEffectiveMaxMP());
    }

    // ─────────────────────────────────────────────────────────────────
    // STATS PANEL
    // ─────────────────────────────────────────────────────────────────

    private Table buildStatsTable() {
        leftStatsCol   = new Table();
        rightStatsCol  = new Table();
        resistancesRow = new Table();
        statusFxRow    = new Table();

        Table twoCol = new Table();
        twoCol.add(leftStatsCol).top().left().expandX().fillX().padRight(20);
        twoCol.add(rightStatsCol).top().left().expandX().fillX();

        statsTable = new Table();
        statsTable.top().left();
        statsTable.add(twoCol).fillX().row();
        statsTable.add(resistancesRow).fillX().padTop(4).row();
        statsTable.add(statusFxRow).fillX().padTop(4);
        return statsTable;
    }

    private void refreshStatsTable() {
        if (leftStatsCol == null) return;
        leftStatsCol.clear();
        rightStatsCol.clear();
        resistancesRow.clear();
        statusFxRow.clear();

        // ── LEFT COLUMN ──────────────────────────────────────────────
        sRow(leftStatsCol, "Level", String.valueOf(player.getLevel()), Color.WHITE);
        sRow(leftStatsCol, "Exp", player.getExperience() + " / " + player.getExperienceToNextLevel(), Color.WHITE);

        int bonusAC = player.getEquipment().getEquippedModifierSum(com.bpm.minotaur.gamedata.ModifierType.BONUS_AC);
        sRowDelta(leftStatsCol, "Armor Class", player.getArmorClass() - bonusAC, bonusAC);
        sRow(leftStatsCol, "Absorb",       String.valueOf(player.getAbsorb()),           Color.WHITE);
        sRow(leftStatsCol, "Spiritual Def",String.valueOf(player.getSpiritualDefense()), Color.WHITE);

        int currentHP = player.getCurrentHP();
        int baseMaxHP = player.getStats().getMaxHP();
        int bonusHP   = player.getEquipment().getEquippedModifierSum(com.bpm.minotaur.gamedata.ModifierType.BONUS_MAX_HP);
        int effectiveMaxHP = baseMaxHP + bonusHP;
        Color hpCol = currentHP < effectiveMaxHP * 0.3f ? Color.RED : currentHP < effectiveMaxHP * 0.6f ? Color.YELLOW : Color.WHITE;
        String hpVal = currentHP + " / " + effectiveMaxHP + (bonusHP != 0 ? "  (+" + bonusHP + " bonus)" : "");
        sRow(leftStatsCol, "HP", hpVal, hpCol);

        int currentMP = player.getCurrentMP();
        int baseMaxMP = player.getStats().getMaxMP();
        int bonusMP   = player.getEquipment().getEquippedModifierSum(com.bpm.minotaur.gamedata.ModifierType.BONUS_MAX_MP);
        Color mpCol = currentMP < 2 ? Color.RED : Color.WHITE;
        String mpVal = currentMP + " / " + (baseMaxMP + bonusMP) + (bonusMP != 0 ? "  (+" + bonusMP + " bonus)" : "");
        sRow(leftStatsCol, "MP", mpVal, mpCol);

        int bonusStr = player.getEquipment().getEquippedModifierSum(com.bpm.minotaur.gamedata.ModifierType.BONUS_STRENGTH);
        sRowDelta(leftStatsCol, "Strength",  player.getStats().getStrength(), bonusStr);
        sRow(leftStatsCol, "Dexterity", String.valueOf(player.getEffectiveDexterity()), Color.WHITE);
        sRow(leftStatsCol, "Luck",      String.valueOf(player.getLuck()), Color.WHITE);

        int tox   = player.getStats().getToxicity();
        int shift = player.getToxicityThresholdShift();
        String toxTier; Color toxCol;
        if      (tox >= 76 + shift) { toxTier = " [CRITICAL]"; toxCol = Color.RED;    }
        else if (tox >= 26 + shift) { toxTier = " [MEDIUM]";   toxCol = Color.ORANGE; }
        else                        { toxTier = " [CLEAN]";    toxCol = Color.GREEN;  }
        sRow(leftStatsCol, "Toxicity", tox + "%" + toxTier, toxCol);

        sRow(leftStatsCol, "Stamina",    player.getEffectiveStamina() + " dice",                        Color.WHITE);
        sRow(leftStatsCol, "Speed",      String.valueOf(player.getEffectiveSpeed()),                     Color.WHITE);
        sRow(leftStatsCol, "Crit Chance",Math.round(player.getCritChance() * 100) + "%",                Color.WHITE);
        sRow(leftStatsCol, "Crit Damage",String.format("%.1f×", player.getCritMultiplier()),       Color.WHITE);
        sRow(leftStatsCol, "Dodge",      Math.round(player.getDodgeChance() * 100) + "%",               Color.WHITE);
        sRow(leftStatsCol, "Spell Power","+" + player.getSpellPower(),                                   Color.WHITE);

        // ── RIGHT COLUMN ─────────────────────────────────────────────
        int sat = player.getStats().getSatiety();
        sRow(rightStatsCol, "Satiety",   sat + "%",
             sat < 20 ? Color.RED : sat < 50 ? Color.YELLOW : Color.WHITE);

        float temp = player.getStats().getBodyTemperature();
        sRow(rightStatsCol, "Body Temp", String.format("%.1f°C", temp),
             (temp < 33f || temp > 41f) ? Color.RED : (temp < 35f || temp > 39f) ? Color.YELLOW : Color.WHITE);

        int hyd = player.getStats().getHydration();
        sRow(rightStatsCol, "Hydration", hyd + "%",
             hyd < 20 ? Color.RED : hyd < 50 ? Color.YELLOW : Color.WHITE);

        sRow(rightStatsCol, "Arrows",       String.valueOf(player.getArrows()),               Color.WHITE);
        sRow(rightStatsCol, "Treasure",     String.valueOf(player.getTreasureScore()),         Color.GOLD);
        sRow(rightStatsCol, "Cooking Skill",String.valueOf(player.getStats().getCookingSkill()),Color.WHITE);
        sRow(rightStatsCol, "Agility",      String.valueOf(player.getEffectiveAgility()),      Color.WHITE);
        sRow(rightStatsCol, "Intelligence", String.valueOf(player.getEffectiveIntelligence()), Color.WHITE);
        sRow(rightStatsCol, "Wisdom",       String.valueOf(player.getEffectiveWisdom()),       Color.WHITE);
        sRow(rightStatsCol, "Constitution", String.valueOf(player.getEffectiveConstitution()), Color.WHITE);
        sRow(rightStatsCol, "Charisma",     String.valueOf(player.getEffectiveCharisma()),     Color.WHITE);

        // ── RESISTANCES (horizontal row) ─────────────────────────────
        resistancesRow.add(new Label("Resist:", new Label.LabelStyle(smallFont, Color.GOLD))).left().padRight(6);
        DamageType[] resTypes = { DamageType.FIRE, DamageType.ICE, DamageType.POISON, DamageType.BLEED,
                                  DamageType.DISEASE, DamageType.DARK, DamageType.LIGHT, DamageType.SORCERY };
        String[]     resNames = { "Fire", "Ice", "Poison", "Bleed", "Disease", "Dark", "Light", "Sorcery" };
        for (int i = 0; i < resTypes.length; i++) {
            int v = player.getElementalResistance(resTypes[i]);
            Color c = v > 0 ? Color.CYAN : Color.DARK_GRAY;
            resistancesRow.add(new Label(resNames[i] + ": " + (v > 0 ? "+" : "") + v,
                    new Label.LabelStyle(smallFont, c))).left().padRight(10);
        }

        // ── STATUS EFFECTS (horizontal row) ──────────────────────────
        statusFxRow.add(new Label("Status:", new Label.LabelStyle(smallFont, Color.GOLD))).left().padRight(6);
        java.util.List<com.bpm.minotaur.gamedata.effects.ActiveStatusEffect> fxList = new java.util.ArrayList<>();
        for (com.bpm.minotaur.gamedata.effects.ActiveStatusEffect fx : player.getStatusManager().getActiveEffects()) {
            fxList.add(fx);
        }
        if (fxList.isEmpty()) {
            statusFxRow.add(new Label("None", new Label.LabelStyle(smallFont, Color.DARK_GRAY))).left();
        } else {
            int focusedOrdinal = com.bpm.minotaur.gamedata.effects.StatusEffectType.FOCUSED.ordinal();
            for (com.bpm.minotaur.gamedata.effects.ActiveStatusEffect fx : fxList) {
                boolean isDebuff = fx.getType().ordinal() < focusedOrdinal;
                Color fxCol = isDebuff ? Color.ORANGE : Color.CYAN;
                String dur  = fx.getDuration() == -1 ? "perm" : fx.getDuration() + "t";
                statusFxRow.add(new Label(fx.getType().name().replace('_', ' ') + " (" + dur + ")",
                        new Label.LabelStyle(smallFont, fxCol))).left().padRight(8);
            }
        }
    }

    private void sRow(Table col, String label, String value, Color valueColor) {
        col.add(new Label(label + ":", new Label.LabelStyle(smallFont, Color.LIGHT_GRAY))).left().padBottom(1);
        col.add(new Label(value, new Label.LabelStyle(smallFont, valueColor))).right().padLeft(6).padBottom(1).row();
    }

    private void sRowDelta(Table col, String label, int base, int bonus) {
        col.add(new Label(label + ":", new Label.LabelStyle(smallFont, Color.LIGHT_GRAY))).left().padBottom(1);
        if (bonus != 0) {
            Color bc = bonus > 0 ? Color.CYAN : Color.RED;
            Table inline = new Table();
            inline.add(new Label(String.valueOf(base), new Label.LabelStyle(smallFont, Color.WHITE)));
            inline.add(new Label((bonus > 0 ? " +" : " ") + bonus, new Label.LabelStyle(smallFont, bc)));
            col.add(inline).right().padLeft(6).padBottom(1).row();
        } else {
            col.add(new Label(String.valueOf(base), new Label.LabelStyle(smallFont, Color.WHITE))).right().padLeft(6).padBottom(1).row();
        }
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
        if (modernUI != null) {
            modernUI.dispose();
            modernUI = null;
        }
        if (whitePixel != null)       whitePixel.dispose();
        if (slotBg != null)           slotBg.dispose();
        if (font != null)             font.dispose();
        if (smallFont != null)        smallFont.dispose();
        if (paperDollTexture != null) paperDollTexture.dispose();
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
