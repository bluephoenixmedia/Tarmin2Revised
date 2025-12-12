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
    private BitmapFont debugFont; // Default font for debug overlay
    private final BitmapFont directionFont;
    private final SpriteBatch spriteBatch;
    private final ShapeRenderer shapeRenderer;
    private final Texture hudBackground;

    private final Label warStrengthValueLabel;
    private final Label spiritualStrengthValueLabel;
    private final Label foodValueLabel;
    private final Label arrowsValueLabel;
    private final Label directionLabel;
    private final Label dungeonLevelLabel;
    private final Label monsterStrengthLabel;
    private final Label combatStatusLabel;
    private final Label messageLabel;
    private final Label treasureValueLabel;
    private final Label levelLabel, xpLabel;
    private Label heldItemLabel;
    private Label rightHandStatsLabel;

    private final Actor[] backpackSlots = new Actor[6];
    private final Actor leftHandSlot;
    private final Actor rightHandSlot;

    private final Texture messageBackgroundTexture;

    private final TextureRegionDrawable messageBackgroundDrawable;

    private final GlyphLayout glyphLayout = new GlyphLayout();

    // --- Table References for Debug Toggle ---
    private final Table mainContentTable;
    private final Table leftTable;
    private final Table rightTable;
    private final Table warTable;
    private final Table spiritualTable;
    private final Table foodTable;
    private final Table arrowsTable;
    private final Table treasureTable;
    private final Table lvlExpTable;
    private final Table handsAndCompassTable;
    private final Table levelTable;
    private final Table messageTable;
    private final Table mainContainer;
    private final WorldManager worldManager;

    private String equippedWeapon = "NOTHING";
    private String damage = "0";
    private String range = "0";
    private String isRanged = "N/A";
    private String weaponColor = "NONE";
    private String weaponType = "NULL";

    private final GameMode gameMode;

    private static final Color GLOW_COLOR_UI = new Color(1.0f, 0.9f, 0.2f, 0.7f);


    public Hud(SpriteBatch sb, Player player, Maze maze, CombatManager combatManager, GameEventManager eventManager, WorldManager worldManager, Tarmin2 game, DebugManager debugManager, GameMode gameMode) {
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

        hudBackground = new Texture(Gdx.files.internal("images/hud_background.png"));

        // --- Create a semi-transparent background for the message table ---
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1, 1, 1, 0.3f); // White, 30% opaque
        pixmap.fill();
        messageBackgroundTexture = new Texture(pixmap);
        pixmap.dispose();


        messageBackgroundDrawable = new TextureRegionDrawable(new TextureRegion(messageBackgroundTexture));
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

        // --- Label Styles ---
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle directionLabelStyle = new Label.LabelStyle(directionFont, Color.GOLD);
        Label.LabelStyle messageLabelStyle = new Label.LabelStyle(font, Color.BLACK);

        Label.LabelStyle heldItemStyle = new Label.LabelStyle(font, Color.WHITE);
        heldItemStyle.font.getData().setScale(0.8f);


        // --- Layout Setup (Tables) ---
        mainContentTable = new Table();

        // --- Left Table (Backpack) ---
        leftTable = new Table();

        float slotSize = 60f;
        for(int i = 0; i < 6; i++) { backpackSlots[i] = new Actor(); }

        leftTable.add(backpackSlots[0]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[1]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[2]).width(slotSize).height(slotSize);
        leftTable.row();
        leftTable.add(backpackSlots[3]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[4]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[5]).width(slotSize).height(slotSize);

        int standardPadLeft = 180;

        // --- Right Table (Player Stats) ---
        rightTable = new Table();


        // War Strength
        warStrengthValueLabel = new Label("", labelStyle);
        warTable = new Table();
        warTable.padLeft(standardPadLeft);
        warTable.add(new Label("WS", labelStyle)).padRight(10);
        warTable.padBottom(-10);
        warTable.add(warStrengthValueLabel);

        // Spiritual Strength
        spiritualStrengthValueLabel = new Label("", labelStyle);
        spiritualTable = new Table();
        spiritualTable.padLeft(standardPadLeft);
        spiritualTable.add(new Label("SS", labelStyle)).padRight(10);
        spiritualTable.padBottom(-10);
        spiritualTable.add(spiritualStrengthValueLabel);

        // Food
        foodValueLabel = new Label("", labelStyle);
        foodTable = new Table();
        foodTable.padLeft(standardPadLeft);
        foodTable.add(new Label("FOOD", labelStyle)).padRight(10);
        foodTable.padBottom(-10);
        foodTable.add(foodValueLabel);

        // Arrows
        arrowsValueLabel = new Label("", labelStyle);
        arrowsTable = new Table();
        arrowsTable.padLeft(100);
        arrowsTable.add(new Label("ARROWS", labelStyle)).padRight(10);
        arrowsTable.padBottom(-10);
        arrowsTable.add(arrowsValueLabel);

        // Treasure
        treasureValueLabel = new Label("", labelStyle);
        treasureTable = new Table();
        treasureTable.padLeft(standardPadLeft);
        treasureTable.add(new Label("TREASURE", labelStyle)).padRight(30);
        treasureTable.padBottom(-50);
        treasureTable.add(treasureValueLabel);

        // Level & Experience
        levelLabel = new Label("", labelStyle);
        xpLabel = new Label("", labelStyle);

        lvlExpTable = new Table();
        lvlExpTable.padLeft(standardPadLeft);
        lvlExpTable.add(new Label("LVL:", labelStyle)).padRight(10);
        lvlExpTable.add(levelLabel).padRight(30);
        lvlExpTable.add(new Label("EXP:", labelStyle)).padRight(10);
        lvlExpTable.add(xpLabel);


        // --- Assemble Right Table ---
        rightTable.add(warTable).left().padTop(10).padBottom(20).padRight(10);
        rightTable.add(spiritualTable).right().expandX().padTop(10).padBottom(20).padRight(10);
        rightTable.row();

        rightTable.add(foodTable).left();
        rightTable.add(arrowsTable).right().expandX().padRight(10);
        rightTable.row();

        rightTable.add(treasureTable).left().padTop(10);

        // Combat Info
        monsterStrengthLabel = new Label("", labelStyle);
        combatStatusLabel = new Label("", labelStyle);
        rightTable.row();
        rightTable.add(new Label("MONSTER", labelStyle)).left().padTop(50).padLeft(standardPadLeft);
        rightTable.add(monsterStrengthLabel).right().padTop(50).padRight(10);
        rightTable.row();
        rightTable.row();
        rightTable.add(lvlExpTable).left().padTop(10);

        rightHandStatsLabel = new Label("", labelStyle);
        rightTable.row();
        rightTable.add(rightHandStatsLabel).left().padTop(10).padRight(10);



        // --- Assemble Main Content Table ---
        mainContentTable.add(leftTable).width(500).padLeft(50).spaceTop(10).left();
        mainContentTable.add(rightTable).expandX().right().spaceTop(10);

        // --- Hands and Compass Table ---
        handsAndCompassTable = new Table();

        leftHandSlot = new Actor();
        rightHandSlot = new Actor();
        directionLabel = new Label("", directionLabelStyle);

        handsAndCompassTable.add(leftHandSlot).width(slotSize).height(slotSize).padRight(20);
        handsAndCompassTable.add(directionLabel).width(slotSize).height(slotSize);
        handsAndCompassTable.add(rightHandSlot).width(slotSize).height(slotSize).padLeft(20);


        handsAndCompassTable.row();
        heldItemLabel = new Label("Empty", heldItemStyle);
        handsAndCompassTable.add(heldItemLabel).colspan(3).padTop(10).center();



        // --- Dungeon Level Table ---
        dungeonLevelLabel = new Label("", labelStyle);
        levelTable = new Table();
        levelTable.add(dungeonLevelLabel);

        // --- Message Table ---
        messageLabel = new Label("", messageLabelStyle);
        messageTable = new Table();

        messageTable.setBackground(messageBackgroundDrawable);

        messageTable.add(messageLabel).center().pad(5f);

        // --- Root Table (Main Container) ---
        mainContainer = new Table();
        mainContainer.bottom();
        mainContainer.setFillParent(true);

        mainContainer.add(messageTable).colspan(3).expandX().padBottom(20);
        mainContainer.row();

        mainContainer.add(mainContentTable).colspan(3).expandX().fillX();
        mainContainer.row();

        mainContainer.add(new Actor()).expandX(); // Left spacer
        mainContainer.add(handsAndCompassTable).padBottom(1);
        mainContainer.add(new Actor()).expandX(); // Right spacer
        mainContainer.row();

        mainContainer.add(levelTable).colspan(3).expandX().padBottom(10);

        stage.addActor(mainContainer);

        messageLabel.setVisible(false);
        messageTable.setBackground((Drawable)null);
    }

    public void update(float dt) {

        warStrengthValueLabel.setText(String.format("%d / %d", player.getWarStrength(), player.getEffectiveMaxWarStrength()));
        spiritualStrengthValueLabel.setText(String.format("%d / %d", player.getSpiritualStrength(), player.getEffectiveMaxSpiritualStrength()));


        foodValueLabel.setText(String.format("%d", player.getFood()));
        arrowsValueLabel.setText(String.format("%d", player.getArrows()));
        directionLabel.setText(player.getFacing().name().substring(0,1));
        dungeonLevelLabel.setText("DUNGEON LEVEL " + maze.getLevel());
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
        if(combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE && combatManager.getMonster() != null) {
            Monster monster = combatManager.getMonster();
            monsterStrengthLabel.setText("WS:" + monster.getWarStrength() + " SS:" + monster.getSpiritualStrength());
            monsterStrengthLabel.setVisible(true);
            combatStatusLabel.setVisible(true);

            // Display different status messages based on whose turn it is
            switch(combatManager.getCurrentState()){
                case PLAYER_TURN:
                    eventManager.addEvent((new GameEvent("PLAYER TURN - PRESS A to Attack!", 1f)));
                    break;
                case MONSTER_TURN:
                    eventManager.addEvent((new GameEvent(monster.getType() + " ATTACKS!", 1f)));
                    break;
                default:
                    break;
            }
        } else {
            // Hide combat labels when not in combat
            monsterStrengthLabel.setVisible(false);
            combatStatusLabel.setVisible(false);
        }


        // Get only the message events
        List<GameEvent> messageEvents = eventManager.getMessageEvents();

        // Display the oldest game event message, or an empty string if there are no events
        if (!messageEvents.isEmpty()) {
            messageLabel.setText(messageEvents.get(0).message);

            messageLabel.setVisible(true);
            messageTable.setBackground(messageBackgroundDrawable);
        } else {
            messageLabel.setText("");
            messageLabel.setVisible(false);
            messageTable.setBackground((Drawable)null);
        }
    }

    public void render() {
        viewport.apply(); // Apply the viewport settings

        // Draw the background image
        spriteBatch.setProjectionMatrix(stage.getCamera().combined);
        spriteBatch.begin();
        spriteBatch.draw(hudBackground, 0, 0, 1920, 1080);
        spriteBatch.end();

        // Draw the 2D inventory items
        drawInventory();

        // --- FIX: Only draw standard automap if debug overlay is NOT visible ---
        if (!debugManager.isDebugOverlayVisible()) {
            drawAutomap();
        }

        // Toggle debug lines based on global DebugManager state
        boolean isDebug = debugManager.isDebugOverlayVisible();

        // Apply the debug status to all tables
        mainContainer.setDebug(isDebug);
        mainContentTable.setDebug(isDebug);
        leftTable.setDebug(isDebug);
        rightTable.setDebug(isDebug);
        warTable.setDebug(isDebug);
        spiritualTable.setDebug(isDebug);
        foodTable.setDebug(isDebug);
        arrowsTable.setDebug(isDebug);
        treasureTable.setDebug(isDebug);
        lvlExpTable.setDebug(isDebug);
        handsAndCompassTable.setDebug(isDebug);
        levelTable.setDebug(isDebug);
        messageTable.setDebug(isDebug);


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
            defaultFont.draw(spriteBatch, "SYSTEM & CONTROLS", leftColX, yPos); yPos -= lineGap;
            defaultFont.setColor(Color.WHITE);
            defaultFont.draw(spriteBatch, "DEBUG MODE (F1)", leftColX, yPos); yPos -= lineGap;
            defaultFont.draw(spriteBatch, "RENDER MODE: " + debugManager.getRenderMode() + " (F2)", leftColX, yPos); yPos -= lineGap;
            defaultFont.draw(spriteBatch, "FORCE MODIFIERS: " + SpawnManager.DEBUG_FORCE_MODIFIERS + " (F3)", leftColX, yPos); yPos -= lineGap;

            yPos -= 10;
            defaultFont.setColor(Color.CYAN);
            defaultFont.draw(spriteBatch, "KEYBINDS", leftColX, yPos); yPos -= lineGap;
            defaultFont.setColor(Color.WHITE);

            String[] keyMappings = {
                "UP/DOWN : Move",
                "LEFT/RIGHT: Turn",
                "O : Interact (Door/Gate)",
                "P : Pickup Item",
                "U : Use Item",
                "D : Descend Ladder",
                "R : Rest",
                "S : Swap Hands",
                "E : Swap with Pack",
                "T : Rotate Pack",
                "A/SPACE : Attack / Fire",
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
                defaultFont.draw(spriteBatch, "PLAYER STATS", midColX, midY); midY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                int playerGridX = (int)player.getPosition().x;
                int playerGridY = (int)player.getPosition().y;
                defaultFont.draw(spriteBatch, "Grid Pos: (" + playerGridX + ", " + playerGridY + ")", midColX, midY); midY -= lineGap;
                defaultFont.draw(spriteBatch, "Facing: " + player.getFacing().name(), midColX, midY); midY -= lineGap;
                defaultFont.draw(spriteBatch, "Defense: " + player.getArmorDefense(), midColX, midY); midY -= lineGap;
                defaultFont.draw(spriteBatch, "War Str: " + player.getWarStrength() + "/" + player.getEffectiveMaxWarStrength(), midColX, midY); midY -= lineGap;
                defaultFont.draw(spriteBatch, "Spirit: " + player.getSpiritualStrength() + "/" + player.getEffectiveMaxSpiritualStrength(), midColX, midY); midY -= lineGap;

                midY -= 20;

                // --- Equipped Item ---
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(spriteBatch, "RIGHT HAND ITEM", midColX, midY); midY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                Item rightHandItem = player.getInventory().getRightHand();
                if (rightHandItem != null) {
                    defaultFont.draw(spriteBatch, "Name: " + rightHandItem.getDisplayName(), midColX, midY); midY -= lineGap;
                    defaultFont.draw(spriteBatch, "Category: " + rightHandItem.getCategory(), midColX, midY); midY -= lineGap;

                    if (rightHandItem.getCategory() == ItemCategory.WAR_WEAPON) {
                        defaultFont.draw(spriteBatch, "Damage (War): " + rightHandItem.getWarDamage(), midColX, midY); midY -= lineGap;
                        defaultFont.draw(spriteBatch, "Range: " + rightHandItem.getRange(), midColX, midY); midY -= lineGap;
                    } else if (rightHandItem.getCategory() == ItemCategory.SPIRITUAL_WEAPON) {
                        defaultFont.draw(spriteBatch, "Damage (Spirit): " + rightHandItem.getSpiritDamage(), midColX, midY); midY -= lineGap;
                    }
                    defaultFont.draw(spriteBatch, "Is Ranged: " + rightHandItem.isRanged(), midColX, midY); midY -= lineGap;
                } else {
                    defaultFont.draw(spriteBatch, "Empty", midColX, midY); midY -= lineGap;
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
                defaultFont.draw(spriteBatch, "WORLD DEBUG", rightColX, rightY); rightY -= lineGap;
                defaultFont.setColor(Color.WHITE);
                defaultFont.draw(spriteBatch, "Chunk ID: (" + chunkId.x + ", " + chunkId.y + ")", rightColX, rightY); rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Biome: " + biome.name(), rightColX, rightY); rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Theme: " + themeName, rightColX, rightY); rightY -= lineGap;
                defaultFont.draw(spriteBatch, "Loaded Chunks: " + chunkCount, rightColX, rightY); rightY -= lineGap;
            }

            rightY -= 20;

            // Item Modifiers
            if (player != null) {
                defaultFont.setColor(Color.LIME);
                defaultFont.draw(spriteBatch, "ITEM MODIFIERS", rightColX, rightY); rightY -= lineGap;

                rightY = drawItemModsDebug(spriteBatch, defaultFont, "Right Hand", player.getInventory().getRightHand(), rightColX, rightY);
                rightY = drawItemModsDebug(spriteBatch, defaultFont, "Left Hand", player.getInventory().getLeftHand(), rightColX, rightY);

                // Only show first 3 backpack slots to save space
                for (int i = 0; i < 3; i++) {
                    rightY = drawItemModsDebug(spriteBatch, defaultFont, "Pack " + i, player.getInventory().getBackpack()[i], rightColX, rightY);
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
                    modText+= "+" + mod.value;
                } else {
                    modText+= " " + mod.displayName + " ";
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
        if (biome == null) return "?";
        switch (biome) {
            case MAZE: return "M";
            case FOREST: return "F";
            case PLAINS: return "P";
            case DESERT: return "D";
            case MOUNTAINS: return "A";
            case LAKELANDS: return "L";
            case OCEAN: return "O";
            default: return "?";
        }
    }

    /**
     * Draws the player's inventory (backpack and hands) using ShapeRenderer.
     */
    private void drawInventory() {
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Draw backpack items
        Item[] backpack = player.getInventory().getBackpack();
        for (int i = 0; i < backpack.length; i++) {
            Item item = backpack[i];
            Actor slot = backpackSlots[i];
            if (item != null) {
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                ItemTemplate template = item.getTemplate();
                if (template != null && template.spriteData != null) {
                    drawItemSprite(shapeRenderer, item, template.spriteData, pos.x, pos.y, slot.getWidth(), slot.getHeight(), item.getColor());
                }
            }
        }

        // Draw left hand item
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            ItemTemplate template = leftHand.getTemplate();
            if (template != null && template.spriteData != null) {
                drawItemSprite(shapeRenderer, leftHand, template.spriteData, pos.x, pos.y, leftHandSlot.getWidth(), leftHandSlot.getHeight(), leftHand.getColor());
            }
        }

        // Draw right hand item
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            ItemTemplate template = rightHand.getTemplate();
            if (template != null && template.spriteData != null) {
                drawItemSprite(shapeRenderer, rightHand, template.spriteData, pos.x, pos.y, rightHandSlot.getWidth(), rightHandSlot.getHeight(), rightHand.getColor());
            }
        }

        shapeRenderer.end();
    }

    private void drawItemSprite(ShapeRenderer shapeRenderer, Item item, String[] spriteData, float x, float y, float width, float height, Color color) {

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
        if (worldManager == null) return;

        java.util.Set<GridPoint2> loadedChunks = worldManager.getLoadedChunkIds();
        GridPoint2 currentChunk = worldManager.getCurrentPlayerChunkId();
        BiomeManager biomeManager = worldManager.getBiomeManager();

        if (loadedChunks == null || currentChunk == null || biomeManager == null) return;

        float chunkSize = 20; // Size of each chunk square
        float gap = 4;        // Gap between chunks

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
        if (maze == null) return;

        // Configuration
        float maxMapSize = 300f; // Maximum dimension (width or height)
        float mapRightMargin = 20f;
        float mapTopMargin = 20f;

        int mazeW = maze.getWidth();
        int mazeH = maze.getHeight();

        // Prevent division by zero
        if (mazeW == 0 || mazeH == 0) return;

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
        int WALL_EAST  = 0b00000100;
        int WALL_SOUTH = 0b00010000;
        int WALL_WEST  = 0b00000001;
        int DOOR_NORTH = 0b10000000;
        int DOOR_EAST  = 0b00001000;
        int DOOR_SOUTH = 0b00100000;
        int DOOR_WEST  = 0b00000010;

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
                if ((mask & WALL_NORTH) != 0) shapeRenderer.line(cx, cy + cellSize, cx + cellSize, cy + cellSize);
                if ((mask & WALL_EAST)  != 0) shapeRenderer.line(cx + cellSize, cy, cx + cellSize, cy + cellSize);
                if ((mask & WALL_SOUTH) != 0) shapeRenderer.line(cx, cy, cx + cellSize, cy);
                if ((mask & WALL_WEST)  != 0) shapeRenderer.line(cx, cy, cx, cy + cellSize);

                // Draw Doors (Gold/Yellow)
                shapeRenderer.setColor(Color.GOLD);
                if ((mask & DOOR_NORTH) != 0) shapeRenderer.line(cx, cy + cellSize, cx + cellSize, cy + cellSize);
                if ((mask & DOOR_EAST)  != 0) shapeRenderer.line(cx + cellSize, cy, cx + cellSize, cy + cellSize);
                if ((mask & DOOR_SOUTH) != 0) shapeRenderer.line(cx, cy, cx + cellSize, cy);
                if ((mask & DOOR_WEST)  != 0) shapeRenderer.line(cx, cy, cx, cy + cellSize);
            }
        }
        shapeRenderer.end();

        // --- 3. Draw Objects (Gates, Ladders, Player) ---
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int y = 0; y < mazeH; y++) {
            for (int x = 0; x < mazeW; x++) {
                if (!maze.isVisited(x, y)) continue;

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
    }

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
        hudBackground.dispose();
        shapeRenderer.dispose();
        messageBackgroundTexture.dispose();
        if (debugFont != null) debugFont.dispose(); // Clean up debug font
    }
}
