package com.bpm.minotaur.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.managers.CombatManager;
import com.bpm.minotaur.managers.DebugManager; // <-- ADDED IMPORT
import com.bpm.minotaur.managers.GameEventManager;
import com.bpm.minotaur.managers.WorldManager;

import java.util.List; // <-- IMPORT ADDED FOR LIST

public class Hud implements Disposable {

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

    private final Actor[] backpackSlots = new Actor[6];
    private final Actor leftHandSlot;
    private final Actor rightHandSlot;

    private final Texture messageBackgroundTexture;

    private final TextureRegionDrawable messageBackgroundDrawable;

    // --- Table References for Debug Toggle ---
    // We store references to all tables to toggle their debug lines in render()
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
    private final WorldManager worldManager; // <-- ADD THIS

    public Hud(SpriteBatch sb, Player player, Maze maze, CombatManager combatManager, GameEventManager eventManager, WorldManager worldManager) {
        this.player = player;
        this.maze = maze;
        this.combatManager = combatManager;
        this.eventManager = eventManager;
        this.spriteBatch = sb;
        this.worldManager = worldManager; // <-- ADD THIS
        this.shapeRenderer = new ShapeRenderer();

        // --- Viewport and Stage Setup ---
        // We use a FitViewport to maintain a 1920x1080 virtual resolution,
        // scaling it to fit the screen while preserving the aspect ratio.
        viewport = new FitViewport(1920, 1080, new OrthographicCamera());
        stage = new Stage(viewport, sb);

        hudBackground = new Texture(Gdx.files.internal("images/hud_background.png"));

        // --- Create a semi-transparent background for the message table ---
        // We create a 1x1 white pixmap with 30% alpha (0.3f)
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1, 1, 1, 0.3f); // White, 30% opaque
        pixmap.fill();
        messageBackgroundTexture = new Texture(pixmap);
        pixmap.dispose(); // We're done with the pixmap, so dispose it


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

        // --- Layout Setup (Tables) ---
        // Debug borders for all tables are now controlled by the DebugManager (F1 key)
        // and are set in the render() method.

        // This table will hold the left (backpack) and right (stats) sections
        mainContentTable = new Table();


        // --- Left Table (Backpack) ---
        leftTable = new Table();

        float slotSize = 60f; // Tweak this to change the size of all inventory slots
        for(int i = 0; i < 6; i++) { backpackSlots[i] = new Actor(); }

        // Add backpack slots in 2 rows of 3
        // Tweak .width() and .height() to change slot size
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
        warTable.padLeft(standardPadLeft); // ADD THIS
        warTable.add(new Label("WS", labelStyle)).padRight(10);
        // warTable.add(new Label("[S]", labelStyle)).padRight(10);
        warTable.padBottom(-10);
        warTable.add(warStrengthValueLabel);

// Spiritual Strength
        spiritualStrengthValueLabel = new Label("", labelStyle);
        spiritualTable = new Table();
        spiritualTable.padLeft(standardPadLeft); // ADD THIS
        spiritualTable.add(new Label("SS", labelStyle)).padRight(10);
        // spiritualTable.add(new Label("[+]", labelStyle)).padRight(10);
        spiritualTable.padBottom(-10);
        spiritualTable.add(spiritualStrengthValueLabel);

// Food
        foodValueLabel = new Label("", labelStyle);
        foodTable = new Table();
        foodTable.padLeft(standardPadLeft); // ADD THIS
        foodTable.add(new Label("FOOD", labelStyle)).padRight(10);
        //  foodTable.add(new Label("[F]", labelStyle)).padRight(10);
        foodTable.padBottom(-10);
        foodTable.add(foodValueLabel);

// Arrows
        arrowsValueLabel = new Label("", labelStyle);
        arrowsTable = new Table();
        arrowsTable.padLeft(100); // ADD THIS
        arrowsTable.add(new Label("ARROWS", labelStyle)).padRight(10);
        //arrowsTable.add(new Label("[>]", labelStyle)).padRight(10);
        arrowsTable.padBottom(-10);
        arrowsTable.add(arrowsValueLabel);

// Treasure
        treasureValueLabel = new Label("", labelStyle);
        treasureTable = new Table();
        treasureTable.padLeft(standardPadLeft); // ADD THIS
        treasureTable.add(new Label("TREASURE", labelStyle)).padRight(30);
        treasureTable.padBottom(-50);
        treasureTable.add(treasureValueLabel);

// Level & Experience
        levelLabel = new Label("", labelStyle);
        xpLabel = new Label("", labelStyle);

        lvlExpTable = new Table();
        lvlExpTable.padLeft(standardPadLeft); // ADD THIS
        lvlExpTable.add(new Label("LVL:", labelStyle)).padRight(10);
        lvlExpTable.add(levelLabel).padRight(30);
        lvlExpTable.add(new Label("EXP:", labelStyle)).padRight(10);
        lvlExpTable.add(xpLabel);


        // --- Assemble Right Table ---
        // Add WS and SS tables to the first row of rightTable

        rightTable.add(warTable).left().padTop(10).padBottom(20).padRight(10); // .left() aligns this cell's content
        rightTable.add(spiritualTable).right().expandX().padTop(10).padBottom(20).padRight(10); // .expandX() pushes it right
        rightTable.row(); // Next row

        // Add Food and Arrows tables
        rightTable.add(foodTable).left(); // Tweak .padRight() to adjust space between food/arrows
        rightTable.add(arrowsTable).right().expandX().padRight(10);
        rightTable.row(); // Next row

        // Add Treasure table
        rightTable.add(treasureTable).left().padTop(10);//.padTop(30).padBottom(20).padRight(10); // .colspan(2) makes it span both columns

        // Combat Info
        monsterStrengthLabel = new Label("", labelStyle);
        combatStatusLabel = new Label("", labelStyle);
        rightTable.row();
        rightTable.add(new Label("MONSTER", labelStyle)).left().padTop(50).padLeft(standardPadLeft); // Tweak .padTop() for spacing
        rightTable.add(monsterStrengthLabel).right().padTop(50).padRight(10);
        rightTable.row();
        rightTable.row();
        rightTable.add(lvlExpTable).left().padTop(10); // Tweak .padTop()


        // --- Assemble Main Content Table ---
        // This table holds the backpack (left) and stats (right)

        // Left table: fixed width, aligned left
        mainContentTable.add(leftTable).width(500).padLeft(50).spaceTop(10).left();

        // Right table: expands to fill space, content aligned right
        mainContentTable.add(rightTable).expandX().right().spaceTop(10);
        // --- Hands and Compass Table ---
        // This table holds [Left Hand] [Compass] [Right Hand]
        handsAndCompassTable = new Table();

        leftHandSlot = new Actor();
        rightHandSlot = new Actor();
        directionLabel = new Label("", directionLabelStyle);

        handsAndCompassTable.add(leftHandSlot).width(slotSize).height(slotSize).padRight(20); // Tweak .padRight()
        handsAndCompassTable.add(directionLabel).width(slotSize).height(slotSize);
        handsAndCompassTable.add(rightHandSlot).width(slotSize).height(slotSize).padLeft(20); // Tweak .padLeft()


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
        // This is the main table that organizes all other sections vertically.
        // It is anchored to the BOTTOM of the screen.
        mainContainer = new Table();
        mainContainer.bottom(); // Anchors the whole table to the bottom of the stage
        mainContainer.setFillParent(true); // Makes the table fill the entire stage

        // Row 1: Message Label (at the bottom, but added first)
        // Tweak .padBottom(10) to adjust space from the Main Content area
        mainContainer.add(messageTable).colspan(3).expandX().padBottom(20);
        mainContainer.row();

        // Row 2: Main Content (Backpack & Stats)
        mainContainer.add(mainContentTable).colspan(3).expandX().fillX();
        mainContainer.row();

        // Row 3: Hands & Compass (centered)
        // We use empty Actors with .expandX() as "spacers" to center the compass table
        mainContainer.add(new Actor()).expandX(); // Left spacer
        mainContainer.add(handsAndCompassTable).padBottom(1); // Tweak .padBottom() for spacing
        mainContainer.add(new Actor()).expandX(); // Right spacer
        mainContainer.row();

        // Row 4: Dungeon Level (at the very bottom)
        // Tweak .padBottom(10) to adjust space from the bottom of the screen
        mainContainer.add(levelTable).colspan(3).expandX().padBottom(10);

        // Add the fully constructed root table to the stage
        stage.addActor(mainContainer);

        messageLabel.setVisible(false);
        messageTable.setBackground((Drawable)null);
    }

    public void update(float dt) {
        // Update all the label text with the latest player/game data
        warStrengthValueLabel.setText(String.format("%d", player.getWarStrength()));
        spiritualStrengthValueLabel.setText(String.format("%d", player.getSpiritualStrength()));
        foodValueLabel.setText(String.format("%d", player.getFood()));
        arrowsValueLabel.setText(String.format("%d", player.getArrows()));
        directionLabel.setText(player.getFacing().name().substring(0,1));
        dungeonLevelLabel.setText("DUNGEON LEVEL " + maze.getLevel());
        treasureValueLabel.setText(String.format("%d", player.getTreasureScore()));
        xpLabel.setText(String.format("%d", player.getExperience()));
        levelLabel.setText(String.format("%d", player.getLevel()));

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
        // Get the debug status from the singleton DebugManager
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

            // --- 1. Draw the new minimap (with ShapeRenderer) ---
            shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
            // We draw the map centered at (X=600, Y=..._
            drawWorldMinimap(1500, this.viewport.getWorldHeight() - 240);
            // We must set the projection matrix again for the sprite batch
            // because stage.draw() might have changed it.
            spriteBatch.setProjectionMatrix(stage.getCamera().combined);
            spriteBatch.begin();


            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/intellivision.ttf"));
            FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 12; // Base font size
            parameter.color = Color.WHITE;
            parameter.minFilter = Texture.TextureFilter.Nearest;
            parameter.magFilter = Texture.TextureFilter.Nearest;
            debugFont = generator.generateFont(parameter);

            // Set a color for the debug text (e.g., bright green)
            debugFont.setColor(Color.YELLOW);


            // Draw each table's name at its top-left corner
            // localToStageCoordinates(0, height) gets the top-left corner
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

            // Reset font color to default (white)
            font.setColor(Color.WHITE);
            spriteBatch.end();
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
                    drawItemSprite(shapeRenderer, spriteData, pos.x, pos.y, slot.getWidth(), slot.getHeight(), item.getColor());
                }
            }
        }

        // Draw left hand item
        Item leftHand = player.getInventory().getLeftHand();
        if (leftHand != null) {
            Vector2 pos = leftHandSlot.localToStageCoordinates(new Vector2(0, 0));
            String[] spriteData = ItemSpriteData.getSpriteByType(leftHand.getType().name());
            if (spriteData != null) {
                drawItemSprite(shapeRenderer, spriteData, pos.x, pos.y, leftHandSlot.getWidth(), leftHandSlot.getHeight(), leftHand.getColor());
            }
        }

        // Draw right hand item
        Item rightHand = player.getInventory().getRightHand();
        if (rightHand != null) {
            Vector2 pos = rightHandSlot.localToStageCoordinates(new Vector2(0, 0));
            String[] spriteData = ItemSpriteData.getSpriteByType(rightHand.getType().name());
            if (spriteData != null) {
                drawItemSprite(shapeRenderer, spriteData, pos.x, pos.y, rightHandSlot.getWidth(), rightHandSlot.getHeight(), rightHand.getColor());
            }
        }

        shapeRenderer.end();
    }

    /**
     * Draws a 24x24 sprite (defined by string data) into a given rectangle.
     * @param shapeRenderer The ShapeRenderer to use (must be active).
     * @param spriteData The string array defining the sprite pixels.
     * @param x The bottom-left x coordinate of the target rectangle.
     * @param y The bottom-left y coordinate of the target rectangle.
     * @param width The width of the target rectangle.
     * @param height The height of the target rectangle.
     * @param color The color to draw the sprite.
     */
    private void drawItemSprite(ShapeRenderer shapeRenderer, String[] spriteData, float x, float y, float width, float height, Color color) {
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
     */
    private void drawWorldMinimap(float centerX, float centerY) {
        if (worldManager == null) return;

        java.util.Set<GridPoint2> loadedChunks = worldManager.getLoadedChunkIds();
        GridPoint2 currentChunk = worldManager.getCurrentPlayerChunkId();
        if (loadedChunks == null || currentChunk == null) return;

        float chunkSize = 20; // Size of each chunk square
        float gap = 4;        // Gap between chunks

        // Begin drawing filled shapes
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (GridPoint2 id : loadedChunks) {
            // Calculate position relative to the current chunk
            int relX = id.x - currentChunk.x;
            int relY = id.y - currentChunk.y;

            // Calculate final screen position
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
