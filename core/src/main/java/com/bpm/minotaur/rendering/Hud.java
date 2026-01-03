package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.item.*;
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.*;

import java.util.List;

public class Hud implements Disposable {

    private final Tarmin2 game;
    private final DebugManager debugManager;
    public Stage stage;
    private final Viewport viewport;
    private final Player player;
    private final Maze maze;
    private final CombatManager combatManager;
    private final GameEventManager eventManager;
    private final BitmapFont font;
    private final BitmapFont logFont; // Separate font for game log
    private BitmapFont debugFont; // Default font for debug overlay
    private final BitmapFont directionFont;
    private final SpriteBatch spriteBatch;
    private final ShapeRenderer shapeRenderer;
    private final Texture bottomBarBg;
    private final TextureRegionDrawable bottomBarDrawable;

    private final Label warStrengthValueLabel;
    private final Label spiritualStrengthValueLabel;
    private final Label foodValueLabel;
    private final Label arrowsValueLabel;
    private final Label directionLabel;
    private final Label dungeonLevelLabel;
    private final Label monsterStrengthLabel;
    private final Label combatStatusLabel;
    private final Label logLabel; // Replaces messageLabel for the log area
    private final Label treasureValueLabel;
    private final Label levelLabel, xpLabel;
    private Label heldItemLabel;
    private Label rightHandStatsLabel;

    private final Actor[] backpackSlots = new Actor[6];
    private final Actor leftHandSlot;
    private final Actor rightHandSlot;

    // --- Layout Tables ---
    private final Table mainContainer;
    private final Table bottomBarTable;
    private final Table statsTable;
    private final Table logTable;
    private final Table inventoryTable;

    private final WorldManager worldManager;
    private final EncounterWindow encounterWindow;

    private String equippedWeapon = "NOTHING";
    private String damage = "0";
    private String range = "0";
    private String isRanged = "N/A";
    private String weaponColor = "NONE";
    private String weaponType = "NULL";

    private final GameMode gameMode;

    private static final Color GLOW_COLOR_UI = new Color(1.0f, 0.9f, 0.2f, 0.7f);
    private static final Color BG_COLOR_UI = new Color(0f, 0f, 0f, 0.8f);

    // --- Attack Indicators ---
    private static class AttackIndicator {
        Direction direction;
        float duration;

        public AttackIndicator(Direction direction) {
            this.direction = direction;
            this.duration = 1.0f; // 1 second fade
        }
    }

    private java.util.List<AttackIndicator> attackIndicators = new java.util.ArrayList<>();

    public void showAttackIndicator(Direction dir) {
        if (dir == null)
            return;
        attackIndicators.add(new AttackIndicator(dir));
    }

    private final GlyphLayout glyphLayout = new GlyphLayout();
    // -------------------------

    public Hud(SpriteBatch sb, Player player, Maze maze, CombatManager combatManager, GameEventManager eventManager,
            WorldManager worldManager, Tarmin2 game, DebugManager debugManager, GameMode gameMode) {
        this.game = game;
        this.debugManager = debugManager;
        this.gameMode = gameMode;
        this.player = player;
        this.maze = maze;
        this.combatManager = combatManager;
        this.eventManager = eventManager;
        this.spriteBatch = sb;
        this.worldManager = worldManager;
        this.shapeRenderer = new ShapeRenderer();

        // --- Viewport and Stage Setup ---
        viewport = new FitViewport(1920, 1080, new OrthographicCamera());
        stage = new Stage(viewport, sb);

        // --- Create Background ---
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(BG_COLOR_UI);
        pixmap.fill();
        bottomBarBg = new Texture(pixmap);
        bottomBarDrawable = new TextureRegionDrawable(new TextureRegion(bottomBarBg));
        pixmap.dispose();

        // --- Font Loading ---
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 24; // Base font size
        parameter.color = Color.WHITE;
        parameter.minFilter = Texture.TextureFilter.Nearest;
        parameter.magFilter = Texture.TextureFilter.Nearest;
        font = generator.generateFont(parameter);

        parameter.size = 48; // Larger font size for the compass
        directionFont = generator.generateFont(parameter);
        generator.dispose();

        attackIndicators = new java.util.ArrayList<>();

        // --- Game Log Font ---
        logFont = new BitmapFont();
        logFont.getData().setScale(1.0f);

        // --- Label Styles ---
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle headerStyle = new Label.LabelStyle(font, Color.GOLD);
        Label.LabelStyle directionLabelStyle = new Label.LabelStyle(directionFont, Color.GOLD);
        Label.LabelStyle logLabelStyle = new Label.LabelStyle(font, Color.LIGHT_GRAY);

        Label.LabelStyle heldItemStyle = new Label.LabelStyle(font, Color.WHITE);
        heldItemStyle.font.getData().setScale(0.8f);

        // --- Initialize Tables ---
        bottomBarTable = new Table();
        bottomBarTable.setBackground(bottomBarDrawable);

        statsTable = new Table();
        logTable = new Table();
        inventoryTable = new Table();

        // --- Stats Section (Left) ---
        warStrengthValueLabel = new Label("", labelStyle);
        spiritualStrengthValueLabel = new Label("", labelStyle);
        foodValueLabel = new Label("", labelStyle);
        levelLabel = new Label("", labelStyle);
        xpLabel = new Label("", labelStyle);
        dungeonLevelLabel = new Label("", labelStyle);
        directionLabel = new Label("", directionLabelStyle); // Compass

        statsTable.top().left().pad(20);

        // Row 1: HP & MP
        statsTable.add(new Label("HP:", headerStyle)).left();
        statsTable.add(warStrengthValueLabel).left().padLeft(10).width(150);
        statsTable.add(new Label("MP:", headerStyle)).left().padLeft(20);
        statsTable.add(spiritualStrengthValueLabel).left().padLeft(10).expandX();
        statsTable.row().padTop(10);

        // Row 2: Food & Level
        statsTable.add(new Label("FOOD:", headerStyle)).left();
        statsTable.add(foodValueLabel).left().padLeft(10);
        statsTable.add(new Label("LVL:", headerStyle)).left().padLeft(20);
        statsTable.add(levelLabel).left().padLeft(10);
        statsTable.row().padTop(10);

        // Row 3: XP & Dungeon Level
        statsTable.add(new Label("EXP:", headerStyle)).left();
        statsTable.add(xpLabel).left().padLeft(10);
        statsTable.add(dungeonLevelLabel).colspan(2).left().padLeft(20);
        statsTable.row().padTop(10);

        // Compass (Bottom of Left Panel)
        statsTable.add(new Label("FACING:", headerStyle)).left().padTop(10);
        statsTable.add(directionLabel).left().padLeft(10).padTop(10);

        // --- Log Section (Center) ---
        logLabel = new Label("", logLabelStyle);
        logLabel.setWrap(true);
        logLabel.setAlignment(com.badlogic.gdx.utils.Align.bottomLeft);

        logTable.add(logLabel).grow().pad(10);

        // --- Inventory Section (Right) ---
        float slotSize = 60f;

        // Init Actors
        for (int i = 0; i < 6; i++) {
            backpackSlots[i] = new Actor();
        }
        leftHandSlot = new Actor();
        rightHandSlot = new Actor();

        Table handsTable = new Table();
        handsTable.add(new Label("L", headerStyle)).padRight(5);
        handsTable.add(leftHandSlot).size(slotSize).padRight(20);
        handsTable.add(rightHandSlot).size(slotSize).padLeft(20);
        handsTable.add(new Label("R", headerStyle)).padLeft(5);

        Table backpackTable = new Table();
        backpackTable.add(backpackSlots[0]).size(slotSize).pad(4);
        backpackTable.add(backpackSlots[1]).size(slotSize).pad(4);
        backpackTable.add(backpackSlots[2]).size(slotSize).pad(4);
        backpackTable.row();
        backpackTable.add(backpackSlots[3]).size(slotSize).pad(4);
        backpackTable.add(backpackSlots[4]).size(slotSize).pad(4);
        backpackTable.add(backpackSlots[5]).size(slotSize).pad(4);

        inventoryTable.add(handsTable).padBottom(10).row();
        inventoryTable.add(backpackTable);

        // --- Combat/Extra Labels (Hidden by default, overlaid or integrated) ---
        // For now, keeping them simple.
        monsterStrengthLabel = new Label("", labelStyle);
        combatStatusLabel = new Label("", labelStyle);
        treasureValueLabel = new Label("", labelStyle);
        arrowsValueLabel = new Label("", labelStyle);
        rightHandStatsLabel = new Label("", labelStyle);
        heldItemLabel = new Label("", heldItemStyle);

        // Add combat info to stats table for now?
        statsTable.row();
        statsTable.add(monsterStrengthLabel).colspan(4).left().padTop(10);

        // --- Assemble Bottom Bar ---
        bottomBarTable.add(statsTable).width(500).left().top().pad(10).padLeft(320); // Shifted right for Combat Menu
        bottomBarTable.add(inventoryTable).width(400).center().pad(10); // Inventory in center
        bottomBarTable.add(logTable).expandX().fill().pad(10); // Log on right

        // --- Main Container ---
        mainContainer = new Table();
        mainContainer.setFillParent(true);
        mainContainer.bottom();
        // Add Bottom Bar Height = 250px
        mainContainer.add(bottomBarTable).growX().height(250);

        stage.addActor(mainContainer);

        // Initialize Combat Menu
        combatMenu = new CombatMenu(font);
        combatMenu.setVisible(false);
        stage.addActor(combatMenu);

        // Encounter Window
        com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle btnStyle = new com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        btnStyle.downFontColor = Color.GRAY;

        encounterWindow = new EncounterWindow(
                new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(font, Color.WHITE),
                new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(font, Color.WHITE),
                btnStyle);
        encounterWindow.setSize(1000, 800);
        encounterWindow.setPosition((viewport.getWorldWidth() - 1000) / 2f, (viewport.getWorldHeight() - 800) / 2f);
        stage.addActor(encounterWindow);
    }

    public void update(float dt) {
        stage.act(dt);

        warStrengthValueLabel
                .setText(String.format("%d / %d", player.getCurrentHP(), player.getMaxHP()));
        spiritualStrengthValueLabel.setText(
                String.format("%d / %d", player.getCurrentMP(), player.getMaxMP()));

        foodValueLabel.setText(String.format("%d", player.getFood()));
        arrowsValueLabel.setText(String.format("%d", player.getArrows()));
        directionLabel.setText(player.getFacing().name().substring(0, 1));
        dungeonLevelLabel.setText("DUNGEON LVL " + maze.getLevel());
        treasureValueLabel.setText(String.format("%d", player.getTreasureScore()));
        xpLabel.setText(String.format("%d", player.getExperience()));
        levelLabel.setText(String.format("%d", player.getLevel()));

        Item rightHandItem = player.getInventory().getRightHand();

        // Update Held Item Label
        Item itemInHand = player.getInventory().getRightHand();
        if (rightHandItem != null) {
            equippedWeapon = rightHandItem.getType() != null ? rightHandItem.getType().toString() : "UNKNOWN";
            weaponColor = rightHandItem.getItemColor() != null ? rightHandItem.getItemColor().name() : "NONE";
            weaponType = rightHandItem.getCategory() != null ? rightHandItem.getCategory().toString() : "NULL";
        }

        String mods = returnModsString(itemInHand);

        if (itemInHand != null && itemInHand.getType() != null) {
            rightHandStatsLabel.setText(weaponColor + " " + itemInHand.getType().toString() + mods);
        }

        if (itemInHand != null) {
            heldItemLabel.setText(itemInHand.getDisplayName());
        } else {
            heldItemLabel.setText("Empty");
        }

        // Show combat information only if combat is active
        if (combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE
                && combatManager.getMonster() != null) {
            Monster monster = combatManager.getMonster();
            monsterStrengthLabel.setText("HP:" + monster.getCurrentHP() + " MP:" + monster.getCurrentMP());
            monsterStrengthLabel.setVisible(true);
            combatStatusLabel.setVisible(true);

            // Display different status messages based on whose turn it is
            switch (combatManager.getCurrentState()) {
                case PLAYER_TURN:
                    combatStatusLabel.setText("PLAYER TURN");
                    break;
                case MONSTER_TURN:
                    combatStatusLabel.setText("MONSTER ATTACKS!");
                    break;
                default:
                    combatStatusLabel.setText(combatManager.getCurrentState().toString());
                    break;
            }
        } else {
            // Hide combat labels when not in combat
            monsterStrengthLabel.setText(""); // clear text instead of hide to avoid layout shifts if we want
                                              // consistency
            monsterStrengthLabel.setVisible(false);
            combatStatusLabel.setVisible(false);
        }

        // --- Update Log Label with History ---
        List<String> messageEvents = eventManager.getMessageHistory();
        if (messageEvents != null && !messageEvents.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(messageEvents.size(), 6); // Show last 6 messages
            // messages are usually newest first? eventManager.getMessageHistory() returns
            // history.
            // Let's assume list order.
            for (int i = 0; i < limit; i++) {
                sb.append(messageEvents.get(i)).append("\n");
            }
            logLabel.setText(sb.toString());
        } else {
            logLabel.setText("");
        }
    }

    public void render() {
        viewport.apply(); // Apply the viewport settings

        // Removed background drawing

        // Draw the 2D inventory items
        drawInventory();

        // --- FIX: Only draw standard automap if debug overlay is NOT visible ---
        if (!debugManager.isDebugOverlayVisible()) {
            drawAutomap();
            drawBridgeIntegrityBar(); // NEW: Tarmin's Hunger UI
        }
        // Toggle debug lines based on global DebugManager state
        boolean isDebug = debugManager.isDebugOverlayVisible();

        // Apply the debug status to all tables
        mainContainer.setDebug(isDebug);
        bottomBarTable.setDebug(isDebug);
        statsTable.setDebug(isDebug);
        logTable.setDebug(isDebug);
        inventoryTable.setDebug(isDebug);

        // This renders all actors (labels, etc.) *and* the debug lines (if enabled)
        stage.draw();

        if (isDebug) {

            // --- 1. PREPARE DEBUG FONT (Default) ---
            spriteBatch.setProjectionMatrix(stage.getCamera().combined);
            shapeRenderer.setProjectionMatrix(stage.getCamera().combined);

            // Lazy-load the default font if needed
            if (debugFont == null) {
                debugFont = new BitmapFont(); // Uses default LibGDX Arial font
                debugFont.getData().setScale(1.0f); // Make it smaller and cleaner
                debugFont.setColor(Color.WHITE);
            }

            // --- 2. Draw Backgrounds for Debug Text ---
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0f, 0f, 0f, 0.6f); // Darker semi-transparent black

            // Left Column Background (Controls)
            shapeRenderer.rect(10, 400, 340, 650);

            // Middle Column Background (Player Info)
            shapeRenderer.rect(360, 400, 340, 650);

            // New Column Background (Entity List)
            shapeRenderer.rect(720, 400, 260, 650);

            // Right Column Background (World/Items/Minimap)
            // Spanning width to include minimap area
            shapeRenderer.rect(1000, 400, 300, 650);
            shapeRenderer.end();

            // --- 3. Draw all debug text ---
            spriteBatch.begin();

            BitmapFont defaultFont = debugFont;

            // --- COLUMN 1: CONTROLS & SYSTEM ---
            float leftColX = 20;
            float yPos = 1030;
            float lineGap = 25;

            defaultFont.setColor(Color.YELLOW);
            defaultFont.draw(spriteBatch, "SYSTEM & CONTROLS", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.setColor(Color.WHITE);
            defaultFont.draw(spriteBatch, "DEBUG MODE (F1)", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.draw(spriteBatch, "RENDER MODE: " + debugManager.getRenderMode() + " (F2)", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.draw(spriteBatch, "FORCE MODIFIERS: " + SpawnManager.DEBUG_FORCE_MODIFIERS + " (F3)", leftColX,
                    yPos);
            yPos -= lineGap;
            defaultFont.draw(spriteBatch, "FPS: " + Gdx.graphics.getFramesPerSecond(), leftColX, yPos);
            yPos -= lineGap;

            yPos -= 10;
            defaultFont.setColor(Color.CYAN);
            defaultFont.draw(spriteBatch, "KEYBINDS", leftColX, yPos);
            yPos -= lineGap;
            defaultFont.setColor(Color.WHITE);

            String[] keyMappings = {
                    "UP/DOWN : Move",
                    "LEFT/RIGHT: Turn",
                    "O : Interact / Craft / Butcher",
                    "P : Pickup Item",
                    "U : Use Item",
                    "D : Climb Ladder",
                    "R : Rest",
                    "S : Swap Hands",
                    "E : Swap with Pack",
                    "T : Rotate Pack",
                    "A/SPACE : Instant Attack",
                    "NUM 7 : Dice Attack",
                    "M : Castle Map"
            };

            for (String mapping : keyMappings) {
                defaultFont.draw(spriteBatch, mapping, leftColX, yPos);
                yPos -= lineGap;
            }

            // --- COLUMN 2: PLAYER & EQUIPMENT ---
            float midColX = 370;
            float midY = 1030;

            if (player != null) {
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "PLAYER STATS", midColX, midY);
                midY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                int playerGridX = (int) player.getPosition().x;
                int playerGridY = (int) player.getPosition().y;
                defaultFont.draw(spriteBatch, "Grid Pos: (" + playerGridX + ", " + playerGridY + ")", midColX, midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch, "Facing: " + player.getFacing().name(), midColX, midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch, "AC: " + player.getArmorClass(), midColX, midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch,
                        "HP: " + player.getCurrentHP() + "/" + player.getMaxHP(), midColX,
                        midY);
                midY -= lineGap;
                defaultFont.draw(spriteBatch,
                        "MP: " + player.getCurrentMP() + "/" + player.getMaxMP(),
                        midColX, midY);
                midY -= lineGap;

                midY -= 20;

                // --- Equipped Item ---
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "RIGHT HAND ITEM", midColX, midY);
                midY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                Item rightHandItem = player.getInventory().getRightHand();
                if (rightHandItem != null) {
                    defaultFont.draw(spriteBatch, "Name: " + rightHandItem.getDisplayName(), midColX, midY);
                    midY -= lineGap;
                    defaultFont.draw(spriteBatch, "Category: " + rightHandItem.getCategory(), midColX, midY);
                    midY -= lineGap;

                    if (rightHandItem.isWeapon()) {
                        defaultFont.draw(spriteBatch, "Damage: " + rightHandItem.getDamageDice(), midColX, midY);
                        midY -= lineGap;
                        defaultFont.draw(spriteBatch, "Range: " + rightHandItem.getRange(), midColX, midY);
                        midY -= lineGap;
                    }
                    defaultFont.draw(spriteBatch, "Is Ranged: " + rightHandItem.isRanged(), midColX, midY);
                    midY -= lineGap;
                } else {
                    defaultFont.draw(spriteBatch, "Empty", midColX, midY);
                    midY -= lineGap;
                }
            }

            // --- COLUMN 2.5: ENTITY LIST ---
            float entityColX = 730;
            float entityY = 1030;

            if (maze != null) {
                // 1. Check for Portal
                boolean hasPortal = false;
                for (Item item : maze.getItems().values()) {
                    if (item.getType() == Item.ItemType.MYSTERIOUS_PORTAL) {
                        hasPortal = true;
                        break;
                    }
                }

                if (hasPortal) {
                    defaultFont.setColor(Color.LIME);
                    defaultFont.draw(spriteBatch, "PORTAL DETECTED!", entityColX, entityY);
                    entityY -= lineGap;
                } else {
                    defaultFont.setColor(Color.GRAY);
                    defaultFont.draw(spriteBatch, "No Portal", entityColX, entityY);
                    entityY -= lineGap;
                }

                entityY -= 10;

                // 2. List Monsters
                defaultFont.setColor(Color.RED);
                defaultFont.draw(spriteBatch, "MONSTERS", entityColX, entityY);
                entityY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                java.util.Map<String, Integer> monsterCounts = new java.util.HashMap<>();
                for (com.bpm.minotaur.gamedata.monster.Monster m : maze.getMonsters().values()) {
                    String name = m.getType().name();
                    monsterCounts.put(name, monsterCounts.getOrDefault(name, 0) + 1);
                }
                for (java.util.Map.Entry<String, Integer> entry : monsterCounts.entrySet()) {
                    if (entityY < 420)
                        break;
                    defaultFont.draw(spriteBatch, entry.getValue() + "x " + entry.getKey(), entityColX, entityY);
                    entityY -= lineGap;
                }

                entityY -= 10;

                // 3. List Items
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "ITEMS (REVEALED)", entityColX, entityY);
                entityY -= lineGap;

                defaultFont.setColor(Color.CYAN);
                java.util.Map<String, Integer> itemCounts = new java.util.HashMap<>();
                java.util.Map<String, Item> sampleItems = new java.util.HashMap<>();

                for (Item item : maze.getItems().values()) {
                    // Skip Portal in this list as it has its own dedicated status line above
                    if (item.getType() == Item.ItemType.MYSTERIOUS_PORTAL)
                        continue;

                    String name = item.getFriendlyName();
                    if (name == null || name.isEmpty())
                        name = item.getType().name();

                    if (item.getType().name().contains("SCROLL")) {
                        if (item.getScrollEffect() != null) {
                            name = "Scroll: " + item.getScrollEffect().getBaseName();
                        }
                    } else if (item.isPotion() && item.getTrueEffect() != null) {
                        name = "Potion: " + item.getTrueEffect().getBaseName();
                    }

                    itemCounts.put(name, itemCounts.getOrDefault(name, 0) + 1);
                    if (!sampleItems.containsKey(name)) {
                        sampleItems.put(name, item);
                    }
                }
                java.util.List<String> sortedKeys = new java.util.ArrayList<>(itemCounts.keySet());
                java.util.Collections.sort(sortedKeys);

                for (String key : sortedKeys) {
                    if (entityY < 420)
                        break;

                    Item sample = sampleItems.get(key);

                    // --- Draw Icon ---
                    spriteBatch.end();
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

                    float iconSize = 10f;
                    float iconX = entityColX - 15;
                    float iconY = entityY - 10; // Adjust for baseline

                    if (sample.getCategory() == ItemCategory.WAR_WEAPON
                            || sample.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                        shapeRenderer.setColor(Color.ORANGE);
                        shapeRenderer.circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                    } else if (sample.isPotion()) {
                        shapeRenderer.setColor(Color.PINK);
                        shapeRenderer.circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                    } else if (sample.getType().toString().contains("SCROLL")) {
                        shapeRenderer.setColor(Color.CYAN);
                        shapeRenderer.rect(iconX, iconY, iconSize, iconSize);
                    } else {
                        shapeRenderer.setColor(Color.YELLOW);
                        shapeRenderer.circle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2);
                    }

                    shapeRenderer.end();
                    spriteBatch.begin();
                    // -----------------

                    // Truncate if too long
                    String dispKey = key.length() > 22 ? key.substring(0, 22) + "..." : key;
                    defaultFont.setColor(Color.LIGHT_GRAY);
                    defaultFont.draw(spriteBatch, itemCounts.get(key) + "x " + dispKey, entityColX, entityY);
                    entityY -= lineGap;
                }
            }

            // --- COLUMN 3: WORLD & ITEM MODS ---
            float rightColX = 1000;
            float rightY = 800; // Below minimap

            // World Info
            if (worldManager != null) {
                GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
                Biome biome = worldManager.getBiomeManager().getBiome(chunkId);
                String themeName = (maze != null && maze.getTheme() != null) ? maze.getTheme().name : "Unknown";
                int chunkCount = worldManager.getLoadedChunkIds().size();

                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "WORLD DEBUG", rightColX, rightY);
                rightY -= lineGap;
                defaultFont.setColor(Color.WHITE);
                defaultFont.draw(spriteBatch, "Chunk ID: (" + chunkId.x + ", " + chunkId.y + ")", rightColX, rightY);
                rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Biome: " + biome.name(), rightColX, rightY);
                rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Theme: " + themeName, rightColX, rightY);
                rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Loaded Chunks: " + chunkCount, rightColX, rightY);
                rightY -= lineGap;
            }

            rightY -= 20;

            // Item Modifiers
            if (player != null) {
                defaultFont.setColor(Color.LIME);
                defaultFont.draw(spriteBatch, "ITEM MODIFIERS", rightColX, rightY);
                rightY -= lineGap;

                rightY = drawItemModsDebug(spriteBatch, defaultFont, "Right Hand", player.getInventory().getRightHand(),
                        rightColX, rightY);
                rightY = drawItemModsDebug(spriteBatch, defaultFont, "Left Hand", player.getInventory().getLeftHand(),
                        rightColX, rightY);

                // Only show first 3 backpack slots to save space
                Item[] backpack = player.getInventory().getBackpack();
                for (int i = 0; i < 3; i++) {
                    Item item = (i < backpack.length) ? backpack[i] : null;
                    rightY = drawItemModsDebug(spriteBatch, defaultFont, "Pack " + i,
                            item, rightColX, rightY);
                }
            }

            spriteBatch.end(); // End batch for text drawing

            // --- 4. Draw the new minimap (Moved to end to be on top) ---
            // Positioned in the top right area
            drawWorldMinimap(1450, 900, debugFont, spriteBatch);

            // Note: We do NOT dispose the debugFont here anymore,
            // as we want to reuse the same instance.
        }

    }

    private String returnModsString(Item item) {
        String modText = "";
        if (item != null && item.isModified()) {
            for (ItemModifier mod : item.getModifiers()) {
                if (mod.type.name().equals("BONUS_DAMAGE")) {
                    modText += "+" + mod.value;
                } else {
                    modText += " " + mod.displayName + " ";
                }
            }
        }
        return modText;
    }

    private float drawItemModsDebug(SpriteBatch batch, BitmapFont font, String slotName, Item item, float x, float y) {
        if (item != null && item.isModified()) {
            font.setColor(Color.WHITE);
            font.draw(batch, slotName + ": " + item.getDisplayName(), x, y);
            y -= 20;

            font.setColor(Color.LIGHT_GRAY);
            for (ItemModifier mod : item.getModifiers()) {
                String modText = "  - " + mod.type.name() + " (" + mod.value + ")";
                font.draw(batch, modText, x, y);
                y -= 20;
            }
        }
        return y;
    }

    /**
     * Gets a single-character representation for a biome.
     */
    private String getBiomeLetter(Biome biome) {
        if (biome == null)
            return "?";
        switch (biome) {
            case MAZE:
                return "M";
            case FOREST:
                return "F";
            case PLAINS:
                return "P";
            case DESERT:
                return "D";
            case MOUNTAINS:
                return "A";
            case LAKELANDS:
                return "L";
            case OCEAN:
                return "O";
            default:
                return "?";
        }
    }

    /**
     * Draws the player's inventory (backpack and hands) using ShapeRenderer for
     * RETRO
     * mode
     * or SpriteBatch for MODERN mode.
     */
    private void drawInventory() {
        if (debugManager.getRenderMode() == DebugManager.RenderMode.RETRO) {
            renderRetroInventory();
        } else {
            renderModernInventory();
        }
    }

    private void renderRetroInventory() {
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw quick slots items (HUD belt)
        Item[] quickSlots = player.getInventory().getQuickSlots();
        for (int i = 0; i < quickSlots.length; i++) {
            Item item = quickSlots[i];
            Actor slot = backpackSlots[i]; // These actors align with the HUD slots visually
            if (item != null) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                ItemTemplate template = item.getTemplate();
                if (template != null && template.spriteData != null) {
                    drawItemSprite(shapeRenderer, item, template.spriteData, pos.x, pos.y, slot.getWidth(),
                            slot.getHeight(), item.getColor());
                }
            }
        }

        // Draw left hand item
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            ItemTemplate template = leftHand.getTemplate();
            if (template != null && template.spriteData != null) {
                drawItemSprite(shapeRenderer, leftHand, template.spriteData, pos.x, pos.y, leftHandSlot.getWidth(),
                        leftHandSlot.getHeight(), leftHand.getColor());
            }
        }

        // Draw right hand item
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            ItemTemplate template = rightHand.getTemplate();
            if (template != null && template.spriteData != null) {
                drawItemSprite(shapeRenderer, rightHand, template.spriteData, pos.x, pos.y, rightHandSlot.getWidth(),
                        rightHandSlot.getHeight(), rightHand.getColor());
            }
        }

        shapeRenderer.end();
    }

    private void renderModernInventory() {
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.begin();

        // Draw quick slots items (HUD belt)
        Item[] quickSlots = player.getInventory().getQuickSlots();
        for (int i = 0; i < quickSlots.length; i++) {
            Item item = quickSlots[i];
            Actor slot = backpackSlots[i];
            if (item != null) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                drawModernItem(item, pos.x, pos.y, slot.getWidth(), slot.getHeight());
            }
        }

        // Draw left hand item
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawModernItem(leftHand, pos.x, pos.y, leftHandSlot.getWidth(), leftHandSlot.getHeight());
        }

        // Draw right hand item
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawModernItem(rightHand, pos.x, pos.y, rightHandSlot.getWidth(), rightHandSlot.getHeight());
        }

        spriteBatch.end();

        // Render optional glows or overlays if needed (e.g. for modified items)
        // using ShapeRenderer afterward, or integrated above if purely texture based.
        // For now, let's add a simple glow for modified items using ShapeRenderer ON
        // TOP
        renderModernItemOverlays();
    }

    private void renderModernItemOverlays() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Define common slots checks to avoid code duplication if Refactoring,
        // but for now we follow the pattern:

        // Quick Slots
        Item[] quickSlots = player.getInventory().getQuickSlots();
        for (int i = 0; i < quickSlots.length; i++) {
            Item item = quickSlots[i];
            Actor slot = backpackSlots[i];
            if (item != null && item.isModified()) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                drawItemGlow(pos.x, pos.y, slot.getWidth(), slot.getHeight());
            }
        }

        // Left Hand
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null && leftHand.isModified()) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawItemGlow(pos.x, pos.y, leftHandSlot.getWidth(), leftHandSlot.getHeight());
        }

        // Right Hand
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null && rightHand.isModified()) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            drawItemGlow(pos.x, pos.y, rightHandSlot.getWidth(), rightHandSlot.getHeight());
        }

        shapeRenderer.end();
    }

    private void drawItemGlow(float x, float y, float width, float height) {
        shapeRenderer.setColor(GLOW_COLOR_UI);
        shapeRenderer.rect(x - 2, y - 2, width + 4, height + 4);
    }

    private void drawModernItem(Item item, float x, float y, float width, float height) {
        TextureRegion region = item.getTextureRegion();
        Texture texture = item.getTexture();

        if (region != null) {
            spriteBatch.draw(region, x, y, width, height);
        } else if (texture != null) {
            spriteBatch.draw(texture, x, y, width, height);
        } else {
            // Fallback if no texture found?
            // Could render a placeholder or just skip.
            // For now, let's render a small colored rect using a 1x1 white pixel if
            // possible,
            // or just ignore it to avoid breaking the batch.
        }
    }

    private void drawItemSprite(ShapeRenderer shapeRenderer, Item item, String[] spriteData, float x, float y,
            float width, float height, Color color) {

        if (item.isModified()) {
            shapeRenderer.setColor(GLOW_COLOR_UI);
            shapeRenderer.rect(x - 2, y - 2, width + 4, height + 4);
        }

        shapeRenderer.setColor(color);
        float pixelWidth = width / 24.0f;
        float pixelHeight = height / 24.0f;

        for (int row = 0; row < 24; row++) {
            for (int col = 0; col < 24; col++) {
                if (spriteData[row].charAt(col) == '#') {
                    shapeRenderer.rect(x + col * pixelWidth, y + (23 - row) * pixelHeight, pixelWidth, pixelHeight);
                }
            }
        }
    }

    /**
     * Draws the new world minimap in the debug (F1) view.
     */
    private void drawWorldMinimap(float centerX, float centerY, BitmapFont debugFont, SpriteBatch spriteBatch) {
        if (worldManager == null)
            return;

        java.util.Set<GridPoint2> loadedChunks = worldManager.getLoadedChunkIds();
        GridPoint2 currentChunk = worldManager.getCurrentPlayerChunkId();
        BiomeManager biomeManager = worldManager.getBiomeManager();

        if (loadedChunks == null || currentChunk == null || biomeManager == null)
            return;

        float chunkSize = 20; // Size of each chunk square
        float gap = 4; // Gap between chunks

        // --- 1. Draw Squares ---
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (GridPoint2 id : loadedChunks) {
            int relX = id.x - currentChunk.x;
            int relY = id.y - currentChunk.y;
            float rectX = centerX + (relX * (chunkSize + gap));
            float rectY = centerY + (relY * (chunkSize + gap));

            // Set color: Green for current, Gray for others
            if (relX == 0 && relY == 0) {
                shapeRenderer.setColor(Color.GREEN);
            } else {
                shapeRenderer.setColor(Color.GRAY);
            }

            shapeRenderer.rect(rectX, rectY, chunkSize, chunkSize);
        }
        shapeRenderer.end();

        // --- 2. Draw Letters ---
        spriteBatch.begin();
        debugFont.setColor(Color.BLACK); // Use black for high contrast

        for (GridPoint2 id : loadedChunks) {
            int relX = id.x - currentChunk.x;
            int relY = id.y - currentChunk.y;
            float rectX = centerX + (relX * (chunkSize + gap));
            float rectY = centerY + (relY * (chunkSize + gap));

            // Get the biome and its letter
            Biome biome = biomeManager.getBiome(id);
            String letter = getBiomeLetter(biome);
            glyphLayout.setText(debugFont, letter);

            // Calculate centered position
            float textX = rectX + (chunkSize - glyphLayout.width) / 2;
            float textY = rectY + (chunkSize + glyphLayout.height) / 2;

            debugFont.draw(spriteBatch, glyphLayout, textX, textY);
        }
        spriteBatch.end();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    private void drawAutomap() {
        if (maze == null)
            return;

        // Configuration
        float maxMapSize = 300f; // Maximum dimension (width or height)
        float mapRightMargin = 20f;
        float mapTopMargin = 20f;

        int mazeW = maze.getWidth();
        int mazeH = maze.getHeight();

        // Prevent division by zero
        if (mazeW == 0 || mazeH == 0)
            return;

        // Calculate cell size to fit within the maxMapSize box
        float cellSize = maxMapSize / Math.max(mazeW, mazeH);

        // Calculate the ACTUAL size of the map on screen
        float actualMapWidth = mazeW * cellSize;
        float actualMapHeight = mazeH * cellSize;

        // Position: Top Right (Anchored)
        float startX = 1920 - actualMapWidth - mapRightMargin;
        float startY = 1080 - actualMapHeight - mapTopMargin;

        // --- 1. Draw Background (Fitted) ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f); // Semi-transparent black

        // Draw rect with small padding (5px) around the ACTUAL size
        shapeRenderer.rect(startX - 5, startY - 5, actualMapWidth + 10, actualMapHeight + 10);

        // --- 2. Draw Visited Tiles (Walls/Floor) ---
        shapeRenderer.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Wall bitmasks
        int WALL_NORTH = 0b01000000;
        int WALL_EAST = 0b00000100;
        int WALL_SOUTH = 0b00010000;
        int WALL_WEST = 0b00000001;
        int DOOR_NORTH = 0b10000000;
        int DOOR_EAST = 0b00001000;
        int DOOR_SOUTH = 0b00100000;
        int DOOR_WEST = 0b00000010;

        for (int y = 0; y < mazeH; y++) {
            for (int x = 0; x < mazeW; x++) {
                // VISIBILITY CHECK
                if (!maze.isVisited(x, y)) {
                    continue;
                }

                int mask = maze.getWallDataAt(x, y);
                float cx = startX + (x * cellSize);
                float cy = startY + (y * cellSize);

                // Draw Walls (White)
                shapeRenderer.setColor(Color.WHITE);
                if ((mask & WALL_NORTH) != 0)
                    shapeRenderer.line(cx, cy + cellSize, cx + cellSize, cy + cellSize);
                if ((mask & WALL_EAST) != 0)
                    shapeRenderer.line(cx + cellSize, cy, cx + cellSize, cy + cellSize);
                if ((mask & WALL_SOUTH) != 0)
                    shapeRenderer.line(cx, cy, cx + cellSize, cy);
                if ((mask & WALL_WEST) != 0)
                    shapeRenderer.line(cx, cy, cx, cy + cellSize);

                // Draw Doors (Gold/Yellow)
                shapeRenderer.setColor(Color.GOLD);
                if ((mask & DOOR_NORTH) != 0)
                    shapeRenderer.line(cx, cy + cellSize, cx + cellSize, cy + cellSize);
                if ((mask & DOOR_EAST) != 0)
                    shapeRenderer.line(cx + cellSize, cy, cx + cellSize, cy + cellSize);
                if ((mask & DOOR_SOUTH) != 0)
                    shapeRenderer.line(cx, cy, cx + cellSize, cy);
                if ((mask & DOOR_WEST) != 0)
                    shapeRenderer.line(cx, cy, cx, cy + cellSize);
            }
        }
        shapeRenderer.end();

        // --- 3. Draw Objects (Gates, Ladders, Player) ---
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int y = 0; y < mazeH; y++) {
            for (int x = 0; x < mazeW; x++) {
                if (!maze.isVisited(x, y))
                    continue;

                Object obj = maze.getGameObjectAt(x, y);
                float cx = startX + (x * cellSize);
                float cy = startY + (y * cellSize);

                if (obj instanceof Gate) {
                    shapeRenderer.setColor(Color.CYAN);
                    shapeRenderer.rect(cx + cellSize * 0.25f, cy + cellSize * 0.25f, cellSize * 0.5f, cellSize * 0.5f);
                } else if (maze.getLadders().containsKey(new GridPoint2(x, y))) {
                    shapeRenderer.setColor(Color.BROWN);
                    shapeRenderer.rect(cx + cellSize * 0.3f, cy + cellSize * 0.3f, cellSize * 0.4f, cellSize * 0.4f);
                }
            }
        }

        // Player (Green Arrow/Dot)
        if (player != null) {
            float px = startX + (player.getPosition().x * cellSize);
            float py = startY + (player.getPosition().y * cellSize);

            shapeRenderer.setColor(Color.LIME);
            shapeRenderer.circle(px, py, cellSize * 0.3f, 8);

            // Direction Indicator
            Vector2 dir = player.getDirectionVector();
            shapeRenderer.setColor(Color.LIME);
            shapeRenderer.rectLine(px, py, px + (dir.x * cellSize * 0.6f), py + (dir.y * cellSize * 0.6f), 2f);
        }

        shapeRenderer.end();

        // --- 4. Draw Monsters (Red Dots) ---
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.RED);

        GridPoint2 playerPosVal = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        for (java.util.Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
            GridPoint2 mPos = entry.getKey();
            Monster monster = entry.getValue(); // Use monster object if needed later?

            // Distance Check (Hearing) - Radius 4
            float dist = Vector2.dst(playerPosVal.x, playerPosVal.y, mPos.x, mPos.y);
            boolean isAudible = dist <= 4.0f;

            // Visibility Check (Sight)
            // Only strictly needed if outside audible range, but requirements say "Seen OR
            // Audible"
            boolean isVisible = false;

            // Optimization: Only check LOS if NOT audible (since audible is sufficient to
            // show)
            // UPDATED LOGIC: User said "As long as they are in view".
            // If they are strictly visual range (e.g. 8 tiles away) but usually seen?
            // "Once a monster is 'seen'... as long as they are in view" implies immediate
            // update.
            // If audible is true, we show. If audible is false, check LOS.

            if (isAudible) {
                isVisible = true;
            } else if (monster.isTagged()) {
                isVisible = true; // Always visible if tagged
            } else if (dist <= 15) { // Check for new tags
                if (checkhudLineOfSight(playerPosVal, mPos)) {
                    monster.setTagged(true); // Tag it!
                    isVisible = true;
                }
            }

            if (isVisible) {
                float mx = startX + (mPos.x * cellSize);
                float my = startY + (mPos.y * cellSize);
                // Draw slightly larger than walls? Or same size.
                // "Red square".
                shapeRenderer.rect(mx, my, cellSize, cellSize);
            }
        }
        shapeRenderer.end();

        // --- 5. Game Log (New Feature) ---
        // drawGameLog(startX, startY); // Merged into bottom bar
    }

    /**
     * Simple Bresenham LOS check for the HUD.
     */
    private boolean checkhudLineOfSight(GridPoint2 start, GridPoint2 end) {
        int x0 = start.x;
        int y0 = start.y;
        int x1 = end.x;
        int y1 = end.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx - dy;

        int cx = x0;
        int cy = y0;

        while (true) {
            if (cx == x1 && cy == y1)
                return true;

            if (!(cx == start.x && cy == start.y)) {
                // Check walls
                int walls = maze.getWallDataAt(cx, cy);
                // Standard walls have bits set. 0 is empty?
                // In MonsterAiManager, checks were loose.
                // Let's use isPassable for simplicity BUT isPassable checks monsters/items too.
                // We ONLY care about OPAQUE walls/doors.
                // WallDataAt returns 255 if OOB.

                // Inspecting Maze.java: "walls" is raw int.
                // "if ((currentCellData & wallMask) != 0)" -> Blocks.
                // If cell has ANY wall, does it block LOS?
                // No, a cell can have a North wall but allow E-W passage.

                // ACCURATE LOS needs to check crossing edges.
                // BUT for simple minimap, cell-based occlusion (is the cell solid?) is often
                // used.
                // Let's assume non-zero wall data implies *some* structure.
                // However, "isWallBlocking" logic suggests complex bitmasking.

                // Simpler check: Doors.
                // If it's a door and closed, it blocks.
                Object obj = maze.getGameObjectAt(cx, cy);
                if (obj instanceof Door && ((Door) obj).getState() != Door.DoorState.OPEN)
                    return false;

                // Wall check:
                // If it has ALL walls (solid block)?
                // Or simplified: Just check if we can see through.
                // Let's copy the simpler approach: if wallData implies high density or specific
                // blocking?
                // Actually, let's treat any wallData != 0 as potential occlusion?
                // No, floor tiles might be 0.
                // Let's assume if it blocks MOVEMENT it blocks SIGHT?
                // No, Gates block movement but not sight.

                // Best Approximation:
                // If it is a CLOSED DOOR, block.
                // If it is a SOLID WALL (all bits?), block.

                // Re-reading MonsterAiManager:
                // "if (walls != 0) return false;" <- It assumed 0 is open.
                // I will use that for consistency.
                if (walls != 0)
                    return false;
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    // drawGameLog removed as it is now integrated into the bottom bar log table.

    // --- NEW: Combat Menu UI ---
    public class CombatMenu extends Table {
        private final Label attackLabel;
        private final Label castLabel;
        private final Label rollLabel;
        private final Label useLabel;
        private final Label blockLabel;
        private final Label[] options;
        private int selectedIndex = 0;

        public CombatMenu(BitmapFont font) {
            this.setBackground(bottomBarDrawable); // Use bottom bar bg
            this.setSize(300, 180);
            this.setPosition(20, 160); // Bottom Left above message log

            Label.LabelStyle style = new Label.LabelStyle(font, Color.WHITE);
            Label.LabelStyle selectedStyle = new Label.LabelStyle(font, Color.YELLOW);

            attackLabel = new Label("ATTACK", style);
            castLabel = new Label("CAST", style);
            rollLabel = new Label("ROLL", style);
            useLabel = new Label("USE", style);
            blockLabel = new Label("BLOCK", style);

            options = new Label[] { attackLabel, castLabel, rollLabel, useLabel, blockLabel };

            this.add(attackLabel).pad(5).left().row();
            this.add(castLabel).pad(5).left().row();
            this.add(rollLabel).pad(5).left().row();
            this.add(useLabel).pad(5).left().row();
            this.add(blockLabel).pad(5).left().row();

            updateSelection();
        }

        public void navigateUp() {
            selectedIndex--;
            if (selectedIndex < 0)
                selectedIndex = options.length - 1;
            updateSelection();
        }

        public void navigateDown() {
            selectedIndex++;
            if (selectedIndex >= options.length)
                selectedIndex = 0;
            updateSelection();
        }

        private void updateSelection() {
            for (int i = 0; i < options.length; i++) {
                options[i].setColor(i == selectedIndex ? Color.YELLOW : Color.WHITE);
                options[i].setText((i == selectedIndex ? "> " : "  ") + getOptionName(i));
            }
        }

        private String getOptionName(int index) {
            switch (index) {
                case 0:
                    return "ATTACK";
                case 1:
                    return "CAST";
                case 2:
                    return "ROLL";
                case 3:
                    return "USE";
                case 4:
                    return "BLOCK";
                default:
                    return "???";
            }
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }
    }

    public CombatMenu combatMenu; // Exposed for GameScreen

    // --- NEW: Helper method to support GameScreen calls ---
    public void addMessage(String message) {
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent(message, 2f));
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        font.dispose();
        directionFont.dispose();
        bottomBarBg.dispose();
        shapeRenderer.dispose();
        // messageBackgroundTexture.dispose(); // Removed, as we use bottomBarBg now or
        // separate logic?
        // We removed messageBackgroundTexture in constructor, so no need to dispose it.
        if (debugFont != null)
            debugFont.dispose(); // Clean up debug font
    }

    // --- NEW: Tarmin's Hunger UI ---
    private void drawBridgeIntegrityBar() {
        if (com.bpm.minotaur.managers.DoomManager.getInstance().getBridgeIntegrity() <= 0)
            return;

        float integrity = com.bpm.minotaur.managers.DoomManager.getInstance().getBridgeIntegrity();
        float maxW = 400;
        float h = 20;
        float x = (viewport.getWorldWidth() - maxW) / 2;
        float y = viewport.getWorldHeight() - 40;

        // 1. Draw Bar
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Background
        shapeRenderer.setColor(Color.DARK_GRAY);
        shapeRenderer.rect(x, y, maxW, h);

        // Foreground (Purple/Magical)
        shapeRenderer.setColor(Color.PURPLE);
        shapeRenderer.rect(x, y, maxW * (integrity / 100f), h);

        shapeRenderer.end();

        // Border
        Gdx.gl.glLineWidth(2);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(x, y, maxW, h);
        shapeRenderer.end();
        Gdx.gl.glLineWidth(1);

        // 2. Draw Text
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.begin();
        String text = String.format("Bridge Integrity: %.0f%%", integrity);
        glyphLayout.setText(font, text);

        // Center text on bar
        float textX = x + (maxW - glyphLayout.width) / 2;
        float textY = y + (h + glyphLayout.height) / 2 - 2; // -2 for visual alignment

        font.setColor(Color.WHITE);
        font.draw(spriteBatch, text, textX, textY);
        spriteBatch.end();
        // --- Draw Attack Indicators ---
        if (!attackIndicators.isEmpty()) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            java.util.Iterator<AttackIndicator> it = attackIndicators.iterator();
            while (it.hasNext()) {
                AttackIndicator ind = it.next();
                ind.duration -= Gdx.graphics.getDeltaTime();

                if (ind.duration <= 0) {
                    it.remove();
                    continue;
                }

                // Fade out
                float alpha = MathUtils.clamp(ind.duration, 0f, 1f);
                shapeRenderer.setColor(1f, 0f, 0f, alpha);

                // Draw Arrow based on Screen Edges
                float cx = 1920f / 2f;
                float cy = 1080f / 2f;
                float offset = 300f; // Distance from center
                float size = 50f;

                // Transform world direction to screen relative direction based on player facing
                // Only if we want relative (Left/Right) or absolute (North/South)?
                // Request says "from a direction they are not facing", implying relative.
                // But Hud is 2D.
                // Let's assume Screen Top = Direction player is facing?
                // Or Screen Top = North?

                // If 3D view: Top is "Forward".
                // We need relative direction.

                Direction playerFacing = player.getFacing();
                // We need to find rotation Difference.

                // Map Directions to angles (North=0, East=90...)
                // N=0, E=270, S=180, W=90 (GDX rotation? No, let's just use indices)
                // Let N=0, E=1, S=2, W=3
                int pIndex = getDirIndex(playerFacing);
                int aIndex = getDirIndex(ind.direction);

                // Diff: Forward (0), Right (1), Back (2), Left (3)
                int diff = (aIndex - pIndex + 4) % 4;

                // 0=Front (Shouldn't happen for flank usually, but maybe), 1=Right, 2=Back,
                // 3=Left

                float drawX = cx;
                float drawY = cy;

                // Coordinates for Triangle
                float x1 = 0, y1 = 0, x2 = 0, y2 = 0, x3 = 0, y3 = 0;

                switch (diff) {
                    case 0: // Front (Top)
                        drawY += offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX - size / 2;
                        y2 = drawY + size;
                        x3 = drawX + size / 2;
                        y3 = drawY + size;
                        break;
                    case 1: // Right
                        drawX += offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX + size;
                        y2 = drawY + size / 2;
                        x3 = drawX + size;
                        y3 = drawY - size / 2;
                        break;
                    case 2: // Back (Bottom)
                        drawY -= offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX - size / 2;
                        y2 = drawY - size;
                        x3 = drawX + size / 2;
                        y3 = drawY - size;
                        break;
                    case 3: // Left
                        drawX -= offset;
                        x1 = drawX;
                        y1 = drawY;
                        x2 = drawX - size;
                        y2 = drawY + size / 2;
                        x3 = drawX - size;
                        y3 = drawY - size / 2;
                        break;
                }

                shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
            }
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    private int getDirIndex(Direction d) {
        switch (d) {
            case NORTH:
                return 0;
            case EAST:
                return 1;
            case SOUTH:
                return 2;
            case WEST:
                return 3;
        }
        return 0;
    }

    public EncounterWindow getEncounterWindow() {
        return encounterWindow;
    }
}
