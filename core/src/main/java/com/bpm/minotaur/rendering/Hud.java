package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
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
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.*;

import java.util.List; // <-- IMPORT ADDED FOR LIST

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
    private BitmapFont debugFont;
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
    private Label heldItemLabel; // --- NEW ---
    private Label rightHandStatsLabel; // <-- ADD THIS LINE

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

    // --- NEW: Color for modified item glow in UI ---
    private static final Color GLOW_COLOR_UI = new Color(1.0f, 0.9f, 0.2f, 0.7f);
    // --- END NEW ---


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
        // --- NEW: Style for held item ---
        Label.LabelStyle heldItemStyle = new Label.LabelStyle(font, Color.WHITE);
        heldItemStyle.font.getData().setScale(0.8f); // Make it slightly smaller
        // --- END NEW ---

        // --- Layout Setup (Tables) ---
        mainContentTable = new Table();

        // --- Left Table (Backpack) ---
        leftTable = new Table();

        float slotSize = 60f; // Tweak this to change the size of all inventory slots
        for(int i = 0; i < 6; i++) { backpackSlots[i] = new Actor(); }

        leftTable.add(backpackSlots[0]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[1]).width(slotSize).height(slotSize);
        leftTable.add(backpackSlots[2]).width(slotSize).height(slotSize);
        leftTable.row(); // Move to the next row
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

        // --- NEW: Add row for held item label ---
        handsAndCompassTable.row();
        heldItemLabel = new Label("Empty", heldItemStyle);
        handsAndCompassTable.add(heldItemLabel).colspan(3).padTop(10).center();
        // --- END NEW ---


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
        // --- MODIFIED: Update stat labels to show Current / Max ---
        warStrengthValueLabel.setText(String.format("%d / %d", player.getWarStrength(), player.getEffectiveMaxWarStrength()));
        spiritualStrengthValueLabel.setText(String.format("%d / %d", player.getSpiritualStrength(), player.getEffectiveMaxSpiritualStrength()));
        // --- END MODIFIED ---

        foodValueLabel.setText(String.format("%d", player.getFood()));
        arrowsValueLabel.setText(String.format("%d", player.getArrows()));
        directionLabel.setText(player.getFacing().name().substring(0,1));
        dungeonLevelLabel.setText("DUNGEON LEVEL " + maze.getLevel());
        treasureValueLabel.setText(String.format("%d", player.getTreasureScore()));
        xpLabel.setText(String.format("%d", player.getExperience()));
        levelLabel.setText(String.format("%d", player.getLevel()));

        Item rightHandItem = player.getInventory().getRightHand();



        // --- NEW: Update Held Item Label ---
        Item itemInHand = player.getInventory().getRightHand();
        if (rightHandItem != null) {
            equippedWeapon = rightHandItem.getType() != null ? rightHandItem.getType().toString() : "UNKNOWN";
            weaponColor = rightHandItem.getItemColor() != null ? rightHandItem.getItemColor().name() : "NONE";
            weaponType = rightHandItem.getCategory() != null ? rightHandItem.getCategory().toString() : "NULL";
        }

        //String weaponDisplay = player.getInventory().getRightHand().getDisplayName();
        String mods = returnModsString(itemInHand);


        rightHandStatsLabel.setText(weaponColor + " " + itemInHand.getType().toString() + mods);


        if (itemInHand != null) {
            heldItemLabel.setText(itemInHand.getDisplayName());
        } else {
            heldItemLabel.setText("Empty");
        }
        // --- END NEW ---

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

        // --- MODIFICATION HERE ---
        // Get only the message events
        List<GameEvent> messageEvents = eventManager.getMessageEvents();

        // Display the oldest game event message, or an empty string if there are no events
        if (!messageEvents.isEmpty()) {
            messageLabel.setText(messageEvents.get(0).message);
            // --- END MODIFICATION ---
            messageLabel.setVisible(true); // <-- Show text
            messageTable.setBackground(messageBackgroundDrawable); // <-- Show background
        } else {
            messageLabel.setText("");
            messageLabel.setVisible(false); // <-- Hide text
            messageTable.setBackground((Drawable)null); // <-- Hide background
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

        // --- ADDED: Toggle debug lines based on global DebugManager state ---
        boolean isDebug = DebugManager.getInstance().isDebugOverlayVisible();

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
        // --- END ADDED BLOCK ---

        // This renders all actors (labels, etc.) *and* the debug lines (if enabled)
        stage.draw();

        if (isDebug) {
            // --- 1. PREPARE DEBUG FONT (moved to top) ---
            spriteBatch.setProjectionMatrix(stage.getCamera().combined);
            shapeRenderer.setProjectionMatrix(stage.getCamera().combined);

            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 10;
            parameter.color = Color.WHITE;
            parameter.minFilter = Texture.TextureFilter.Nearest;
            parameter.magFilter = Texture.TextureFilter.Nearest;
            debugFont = generator.generateFont(parameter);

            // --- 2. Draw the new minimap (uses ShapeRenderer AND SpriteBatch) ---
            drawWorldMinimap(1300, this.viewport.getWorldHeight() - 240, debugFont, spriteBatch);

            // --- 3. Draw all debug text (with SpriteBatch) ---
            spriteBatch.begin(); // Begin batch for text drawing
            debugFont.setColor(Color.YELLOW);

            // Draw table names
            drawTableName(mainContainer, "mainContainer");
            drawTableName(mainContentTable, "mainContentTable");
            drawTableName(leftTable, "leftTable");
            drawTableName(rightTable, "rightTable");
            drawTableName(warTable, "warTable");
            drawTableName(spiritualTable, "spiritualTable");
            drawTableName(foodTable, "foodTable");
            drawTableName(arrowsTable, "arrowsTable");
            drawTableName(treasureTable, "treasureTable");
            drawTableName(lvlExpTable, "lvlExpTable");
            drawTableName(handsAndCompassTable, "handsAndCompassTable");
            drawTableName(levelTable, "levelTable");
            drawTableName(messageTable, "messageTable");



            BitmapFont defaultFont = new BitmapFont();
            defaultFont.setColor(Color.WHITE); // Reset main font color

            //BEGIN DEBUG MIGRATION
            float leftColX = 10;
            float rightColX = 350; // X position for the second column
            float yPos = game.viewport.getWorldHeight() - 30; // Shared starting Y
            float lineGap = 20;


            defaultFont.draw(game.batch, "DEBUG MODE (F1)", leftColX, yPos); yPos -= lineGap;
            defaultFont.draw(game.batch, "RENDER MODE: " + debugManager.getRenderMode() + " (F2)", leftColX, yPos); yPos -= lineGap;
            defaultFont.draw(game.batch, "FORCE MODIFIERS: " + SpawnManager.DEBUG_FORCE_MODIFIERS + " (F3)", leftColX, yPos); yPos -= lineGap;

            yPos -= lineGap; // Add a spacer
            defaultFont.setColor(Color.YELLOW);
            defaultFont.draw(game.batch, "--- CONTROLS ---", leftColX, yPos); yPos -= lineGap;
            defaultFont.setColor(Color.WHITE);

            String[] keyMappings = {
                "UP/DOWN : Move",
                "LEFT/RIGHT: Turn",
                "O : Interact",
                "P : Pickup/Drop",
                "U : Use Item",
                "D : Descend Ladder",
                "R : Rest",
                "S : Swap Hands",
                "E : Swap with Pack",
                "T : Rotate Pack",
                "A : Attack (Combat)",
                "M : Castle Map"
            };

            for (String mapping : keyMappings) {
                defaultFont.draw(game.batch, mapping, leftColX, yPos);
                yPos -= lineGap;
            }

            // --- RIGHT COLUMN (PLAYER & WORLD INFO) ---
            float rightColY = game.viewport.getWorldHeight() - 30; // Reset Y for this column

            if (player != null) {
                defaultFont.setColor(Color.YELLOW);
                rightColX = rightColX - 100;
                defaultFont.draw(game.batch, "--- PLAYER ---", rightColX, rightColY); rightColY -= lineGap;

                int playerGridX = (int)player.getPosition().x;
                int playerGridY = (int)player.getPosition().y;
                defaultFont.draw(game.batch, "Pos (Grid): (" + playerGridX + ", " + playerGridY + ")", rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Facing: " + player.getFacing().name(), rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Defense: " + player.getArmorDefense(), rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "War Str: " + player.getWarStrength(), rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Spirit Str: " + player.getSpiritualStrength(), rightColX, rightColY); rightColY -= lineGap;

                rightColY -= lineGap; // Spacer

                // --- Equipped Item ---
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(game.batch, "--- EQUIPPED ITEM ---", rightColX, rightColY); rightColY -= lineGap;
                defaultFont.setColor(Color.WHITE);


                Item rightHandItem = player.getInventory().getRightHand();

                if (rightHandItem != null) {
                    equippedWeapon = rightHandItem.getType() != null ? rightHandItem.getType().toString() : "UNKNOWN";
                    weaponColor = rightHandItem.getItemColor() != null ? rightHandItem.getItemColor().name() : "NONE";
                    weaponType = rightHandItem.getCategory() != null ? rightHandItem.getCategory().toString() : "NULL";

                    if (rightHandItem.getCategory() == Item.ItemCategory.WAR_WEAPON && rightHandItem.getWeaponStats() != null) {
                        damage = String.valueOf(rightHandItem.getWeaponStats().damage);
                        range = String.valueOf(rightHandItem.getWeaponStats().range);
                        isRanged = String.valueOf(rightHandItem.getWeaponStats().isRanged);
                    } else if (rightHandItem.getCategory() == Item.ItemCategory.SPIRITUAL_WEAPON && rightHandItem.getSpiritualWeaponStats() != null) {
                        damage = String.valueOf(rightHandItem.getSpiritualWeaponStats().damage);
                        range = "N/A";
                        isRanged = "N/A";
                    }
                }

                defaultFont.draw(game.batch, "Name: " + equippedWeapon, rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Type: " + weaponType, rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Damage: " + damage, rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Ranged: " + isRanged, rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Range: " + range, rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Color: " + weaponColor, rightColX, rightColY); rightColY -= lineGap;
            }

            // --- World Info ---
            if (gameMode == GameMode.ADVANCED && worldManager != null) {
                rightColY -= lineGap; // Spacer
                defaultFont.setColor(Color.YELLOW);
                defaultFont.draw(game.batch, "--- WORLD (ADVANCED) ---", rightColX, rightColY); rightColY -= lineGap;
                defaultFont.setColor(Color.WHITE);

                GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
                Biome biome = worldManager.getBiomeManager().getBiome(chunkId);

                defaultFont.draw(game.batch, "Chunk ID: (" + chunkId.x + ", " + chunkId.y + ")", rightColX, rightColY); rightColY -= lineGap;
                defaultFont.draw(game.batch, "Biome: " + biome.name(), rightColX, rightColY); rightColY -= lineGap;
            }

            //END DEBUG MIGRATION

            // Draw "PLAYER INFO" text block
            if (player != null) {
                String equippedWeapon = "NOTHING";
                String damage = "0";
                String range = "0";
                String isRanged = "N/A";
                String weaponColor = "NONE";
                String weaponType = "NULL";
                Item rightHandItem = player.getInventory().getRightHand();
                if (rightHandItem != null) {
                    // --- MODIFIED: Use getDisplayName ---
                    equippedWeapon = rightHandItem.getDisplayName();
                    // --- END MODIFIED ---
                    weaponColor = rightHandItem.getItemColor() != null ? rightHandItem.getItemColor().name() : "NONE";
                    weaponType = rightHandItem.getCategory() != null ? rightHandItem.getCategory().toString() : "NULL";

                    if (rightHandItem.getCategory() == Item.ItemCategory.WAR_WEAPON && rightHandItem.getWeaponStats() != null) {
                        damage = String.valueOf(rightHandItem.getWeaponStats().damage);
                        range = String.valueOf(rightHandItem.getWeaponStats().range);
                        isRanged = String.valueOf(rightHandItem.getWeaponStats().isRanged);
                    } else if (rightHandItem.getCategory() == Item.ItemCategory.SPIRITUAL_WEAPON && rightHandItem.getSpiritualWeaponStats() != null) {
                        damage = String.valueOf(rightHandItem.getSpiritualWeaponStats().damage);
                        range = "N/A";
                        isRanged = "N/A";
                    }
                }
                float infoX = 450;
                float infoY = this.viewport.getWorldHeight() - 100;
                // --- MODIFIED: Use Effective Max Stats ---
                defaultFont.draw(spriteBatch, "PLAYER INFO: DEFENSE = " + player.getArmorDefense(), infoX, infoY); infoY -= 20;
                defaultFont.draw(spriteBatch, "PLAYER INFO: SPIRITUAL STRENGTH = " + player.getSpiritualStrength() + " / " + player.getEffectiveMaxSpiritualStrength(), infoX, infoY); infoY -= 20;
                defaultFont.draw(spriteBatch, "PLAYER INFO: WAR STRENGTH = " + player.getWarStrength() + " / " + player.getEffectiveMaxWarStrength(), infoX, infoY); infoY -= 20;
                // --- END MODIFIED ---
                defaultFont.draw(spriteBatch, "PLAYER INFO: EQUIPPED WEAPON = " + equippedWeapon, infoX, infoY); infoY -= 20;
                defaultFont.draw(spriteBatch, "PLAYER INFO: EQUIPPED WEAPON DAMAGE = " + damage, infoX, infoY); infoY -= 20;
                defaultFont.draw(spriteBatch, "PLAYER INFO: EQUIPPED WEAPON ISRANGED = " + isRanged, infoX, infoY); infoY -= 20;
                defaultFont.draw(spriteBatch, "PLAYER INFO: EQUIPPED WEAPON RANGE = " + range, infoX, infoY); infoY -= 20;
                defaultFont.draw(spriteBatch, "PLAYER INFO: EQUIPPED WEAPON COLOR = " + weaponColor, infoX, infoY); infoY -= 20;
                defaultFont.draw(spriteBatch, "PLAYER INFO: EQUIPPED WEAPON TYPE = " + weaponType, infoX, infoY);
            }

            // --- 4. NEW: World Debug Text ---
            if (worldManager != null) {
                GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
                int chunkCount = worldManager.getLoadedChunkIds().size();
                Biome biome = worldManager.getBiomeManager().getBiome(chunkId); // Get current biome

                float worldDebugX = 1300; // X position for this block
                float worldDebugY = this.viewport.getWorldHeight() - 100; // Y position

                defaultFont.setColor(Color.CYAN); // Different color for this block
                defaultFont.draw(spriteBatch, "WORLD DEBUG", worldDebugX, worldDebugY); worldDebugY -= 20;
                defaultFont.draw(spriteBatch, "Chunk: " + (chunkId != null ? chunkId.toString() : "N/A"), worldDebugX, worldDebugY); worldDebugY -= 20;
                defaultFont.draw(spriteBatch, "Biome: " + (biome != null ? biome.name() : "N/A"), worldDebugX, worldDebugY); worldDebugY -= 20;

                defaultFont.draw(spriteBatch, "Loaded: " + chunkCount, worldDebugX, worldDebugY);
                defaultFont.setColor(Color.YELLOW); // Reset to default debug color
            }

            // --- 5. NEW: ITEM MODIFIER DEBUG (as requested) ---
            if (player != null) {
                float itemDebugX = 950; // New column for this info
                float itemDebugY = this.viewport.getWorldHeight() - 100;
                defaultFont.setColor(Color.LIME);
                defaultFont.draw(spriteBatch, "ITEM MODIFIER DEBUG", itemDebugX, itemDebugY); itemDebugY -= 20;

                // Helper to draw item mods
                itemDebugY = drawItemModsDebug(spriteBatch, defaultFont, "Right Hand", player.getInventory().getRightHand(), itemDebugX, itemDebugY);
                itemDebugY = drawItemModsDebug(spriteBatch, defaultFont, "Left Hand", player.getInventory().getLeftHand(), itemDebugX, itemDebugY);

                Item[] backpack = player.getInventory().getBackpack();
                for (int i = 0; i < backpack.length; i++) {
                    itemDebugY = drawItemModsDebug(spriteBatch, defaultFont, "Backpack " + i, backpack[i], itemDebugX, itemDebugY);
                }
                defaultFont.setColor(Color.YELLOW); // Reset
            }



            spriteBatch.end(); // End batch for text drawing

            // Dispose the font (matches existing code's logic)
            debugFont.dispose();
            defaultFont.dispose();
            generator.dispose();
        }

    }

    private String returnModsString(Item item) {
        String modText = "";
      //  Integer modValue = 0;
        if (item != null && item.isModified()) {

            String test = "";
            // Draw all its modifiers
            for (ItemModifier mod : item.getModifiers()) {
               // Gdx.app.log("type ", mod.type.name());
               // Gdx.app.log("name ", mod.displayName);
               // Gdx.app.log("value ", Integer.toString(mod.value));


                if (mod.type.name().equals("BONUS_DAMAGE")) {
                    modText+= "+" + mod.value;
                } else {
                    modText+= " " + mod.displayName + " ";
                }


            }
        }
        return modText; // Return the new y-position
    }

    // --- NEW: Helper method for drawing item modifier debug text ---
    private float drawItemModsDebug(SpriteBatch batch, BitmapFont font, String slotName, Item item, float x, float y) {
        if (item != null && item.isModified()) {
            // Draw the item name first
            font.draw(batch, slotName + ": " + item.getDisplayName(), x, y);
            y -= 15;

            // Draw all its modifiers
            for (ItemModifier mod : item.getModifiers()) {
                String modText = "  - " + mod.type.name() + " (" + mod.value + ") [" + mod.displayName + "]";
                font.draw(batch, modText, x, y);
                y -= 15;
            }
        } else if (item != null) {
            // Optional: Show unmodified items
            // font.draw(batch, slotName + ": " + item.getDisplayName() + " (Unmodified)", x, y);
            // y -= 15;
        }
        return y; // Return the new y-position
    }


    /**
     * Gets a single-character representation for a biome.
     * @param biome The biome.
     * @return A string letter for the biome.
     */
    private String getBiomeLetter(Biome biome) {
        if (biome == null) return "?";
        switch (biome) {
            case MAZE: return "M";
            case FOREST: return "F";
            case PLAINS: return "P";
            case DESERT: return "D";
            case MOUNTAINS: return "A"; // 'A' for 'Alpine'/'Mountain'
            case LAKELANDS: return "L";
            case OCEAN: return "O";
            default: return "?";
        }
    }

    /**
     * Helper method to draw a table's variable name at its top-left corner.
     * Must be called between spriteBatch.begin() and spriteBatch.end().
     * @param table The table actor.
     * @param name The string name to draw.
     */
    private void drawTableName(Table table, String name) {
        if (table == null) return;
        // Get the top-left corner of the table in stage coordinates
        Vector2 pos = table.localToStageCoordinates(new Vector2(0, table.getHeight()));
        // Draw the text. The 'y' coordinate is the top of the text.
        debugFont.draw(spriteBatch, name, pos.x, pos.y);
    }

    /**
     * Draws the player's inventory (backpack and hands) using ShapeRenderer.
     * It gets the position of the placeholder Actors from the Stage.
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
                // Get the stage (screen) coordinates of the slot Actor
                Vector2 pos = slot.localToStageCoordinates(new Vector2(0, 0));
                String[] spriteData = ItemSpriteData.getSpriteByType(item.getType().name());
                if (spriteData != null) {
                    // --- MODIFIED: Pass 'item' ---
                    drawItemSprite(shapeRenderer, item, spriteData, pos.x, pos.y, slot.getWidth(), slot.getHeight(), item.getColor());
                }
            }
        }

        // Draw left hand item
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            String[] spriteData = ItemSpriteData.getSpriteByType(leftHand.getType().name());
            if (spriteData != null) {
                // --- MODIFIED: Pass 'leftHand' item ---
                drawItemSprite(shapeRenderer, leftHand, spriteData, pos.x, pos.y, leftHandSlot.getWidth(), leftHandSlot.getHeight(), leftHand.getColor());
            }
        }

        // Draw right hand item
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            String[] spriteData = ItemSpriteData.getSpriteByType(rightHand.getType().name());
            if (spriteData != null) {
                // --- MODIFIED: Pass 'rightHand' item ---
                drawItemSprite(shapeRenderer, rightHand, spriteData, pos.x, pos.y, rightHandSlot.getWidth(), rightHandSlot.getHeight(), rightHand.getColor());
            }
        }

        shapeRenderer.end();
    }

    /**
     * Draws a 24x24 sprite (defined by string data) into a given rectangle.
     * @param shapeRenderer The ShapeRenderer to use (must be active).
     * @param item The Item object (for checking modifiers).  // --- NEW ---
     * @param spriteData The string array defining the sprite pixels.
     * @param x The bottom-left x coordinate of the target rectangle.
     * @param y The bottom-left y coordinate of the target rectangle.
     * @param width The width of the target rectangle.
     * @param height The height of the target rectangle.
     * @param color The color to draw the sprite.
     */
    // --- MODIFIED: Signature changed to accept 'Item' ---
    private void drawItemSprite(ShapeRenderer shapeRenderer, Item item, String[] spriteData, float x, float y, float width, float height, Color color) {

        // --- NEW: Glow effect ---
        if (item.isModified()) {
            shapeRenderer.setColor(GLOW_COLOR_UI);
            // Draw a simple glowing box behind the item
            shapeRenderer.rect(x - 2, y - 2, width + 4, height + 4);
        }
        // --- END NEW ---

        shapeRenderer.setColor(color);
        // Calculate the size of a single "pixel" in the sprite based on the target area
        float pixelWidth = width / 24.0f;
        float pixelHeight = height / 24.0f;

        for (int row = 0; row < 24; row++) {
            for (int col = 0; col < 24; col++) {
                if (spriteData[row].charAt(col) == '#') {
                    // Y-coordinate is flipped because sprite data is top-to-bottom,
                    // but ShapeRenderer draws bottom-to-top.
                    shapeRenderer.rect(x + col * pixelWidth, y + (23 - row) * pixelHeight, pixelWidth, pixelHeight);
                }
            }
        }
    }

    /**
     * Draws the new world minimap in the debug (F1) view.
     * @param centerX The screen X coordinate to center the map on.
     * @param centerY The screen Y coordinate to center the map on.
     * @param debugFont The font to use for drawing biome letters.
     * @param spriteBatch The SpriteBatch to use for drawing text.
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
        // Update the viewport when the window is resized
        stage.getViewport().update(width, height, true);
    }



    @Override
    public void dispose() {
        // Dispose of all disposable resources
        stage.dispose();
        font.dispose();
        directionFont.dispose();
        hudBackground.dispose();
        shapeRenderer.dispose();
        messageBackgroundTexture.dispose();
    }
}
