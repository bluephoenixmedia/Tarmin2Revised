package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
// No longer needed: import com.bpm.minotaur.generation.ChunkGenerator;
import com.bpm.minotaur.managers.*;
import com.bpm.minotaur.rendering.*;

// No longer needed: import java.util.ArrayList;
// No longer needed: import java.util.Collections;
// No longer needed: import java.util.List;
// No longer needed: import java.util.Random;

public class GameScreen extends BaseScreen implements InputProcessor, Disposable {

    // --- Core Dependencies ---
    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GameMode gameMode; // <-- 3. CHANGE THIS (from isAdvancedMode boolean)

    private boolean needsAsciiRender = false;

    // --- NEW: World/Game Mode ---
    private final WorldManager worldManager;
    private final int level; // Initial dungeon level

    // --- Renderers ---
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final EntityRenderer entityRenderer = new EntityRenderer();
    private final Difficulty difficulty;

    private Hud hud;
    private AnimationManager animationManager;
    private GameEventManager eventManager;
    private SoundManager soundManager;

    // --- Game State ---
    private Player player;
    private Maze maze; // This is now the "active" maze/chunk
    private int currentLevel; // Current level number
    private CombatManager combatManager;

    // --- ALL TILE AND CORRIDOR DEFINITIONS HAVE BEEN REMOVED ---
    // (Moved to ChunkGenerator.java)

    // --- Constructor Updated ---
    public GameScreen(Tarmin2 game, int level, Difficulty difficulty, GameMode gameMode) { // <-- Added gameMode
        super(game);
        this.difficulty = difficulty;
        this.gameMode = gameMode; // <-- Store gameMode
        this.level = level;       // Store initial level
        this.currentLevel = level; // Set current level
        // --- NEW: Initialize WorldManager ---
        this.worldManager = new WorldManager(gameMode, difficulty, level);

        // Music based on initial level
        switch (level) {
            case 1:
                MusicManager.getInstance().playTrack("sounds/music/tarmin_fuxx.ogg");
                break;
            // Add more cases for other levels and tracks
            default:
             //   MusicManager.getInstance().stop(); // Or play a default track
                MusicManager.getInstance().playTrack("sounds/music/tarmin_fuxx.ogg");
                break;
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        animationManager = new AnimationManager();
        eventManager = new GameEventManager();
        soundManager = new SoundManager(debugManager);

        // --- MODIFIED: Maze Generation is now handled by WorldManager ---
        generateLevel(currentLevel); // Generate/Load the starting chunk/level
    }

    @Override
    public void hide() {
        if (worldManager != null && maze != null && gameMode == GameMode.ADVANCED) {
            worldManager.saveCurrentChunk(maze);
        }
        MusicManager.getInstance().stop();
    }

    private void descendToNextLevel() {
        if (worldManager != null && maze != null && gameMode == GameMode.ADVANCED) {
            worldManager.saveCurrentChunk(maze);
        }
        currentLevel++;
        Gdx.app.log("GameScreen", "Descending to level " + currentLevel);


        // For now, it just re-generates the current chunk at a new level.
        // This logic will need further refinement based on how levels and chunks interact.
        generateLevel(currentLevel); // Re-generate/reload based on the new level number
    }

    /**
     * Initializes or re-initializes the game state for a specific level.
     * In ADVANCED mode, this might involve loading a chunk; in CLASSIC, it generates a single maze.
     * @param levelNumber The level to load/generate.
     */
    private void generateLevel(int levelNumber) {
        // --- ALL MAZE GENERATION LOGIC REMOVED ---

        // --- NEW: Get initial maze from WorldManager ---
        // The WorldManager handles whether to load or generate based on GameMode and chunkId (implicit for now)
        this.maze = worldManager.getInitialMaze(); // Or potentially worldManager.loadChunkForLevel(levelNumber)? Needs more design.

        if (player == null) {
            // Get start position from WorldManager/ChunkGenerator
            GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
            player = new Player(startPos.x, startPos.y, difficulty);
        } else {
            // Player already exists, reset position to the start of the newly loaded/generated maze
            resetPlayerPosition();
        }

        // Initialize systems that depend on the player and maze
        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager);
        hud = new Hud(game.batch, player, maze, combatManager, eventManager);

        Gdx.app.log("GameScreen", "Level " + levelNumber + " loaded/generated.");


        // Set the visual theme based on level and game mode
        if (this.gameMode == GameMode.ADVANCED && levelNumber == 1) {
            firstPersonRenderer.setTheme(RetroTheme.ADVANCED_COLOR_THEME_BLUE);
        } else {
            // Add more rules here for other levels/modes if you want
            // e.g., if (isAdvancedMode && levelNumber == 2) { ... }

            // Default to standard theme for all other cases
            firstPersonRenderer.setTheme(RetroTheme.STANDARD_THEME);
        }

        DebugRenderer.printMazeToConsole(maze); // For debugging
    }

    // --- createPlayerAtStart() REMOVED (Logic moved to generateLevel) ---

    private void resetPlayerPosition() {
        // Get start position from WorldManager (which gets it from ChunkGenerator)
        GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
        player.getPosition().set(startPos.x + 0.5f, startPos.y + 0.5f);
        Gdx.app.log("GameScreen", "Reset player position to: " + startPos);
    }

    // --- ALL MAZE/CHUNK GENERATION METHODS REMOVED ---
    // (spawnGate, spawnLadder, createMazeFromArrayTiles, mergeTileRow,
    // getCorridorTemplate, isWall, rotateTile, createMazeFromText,
    // cleanUpOrphanedDoors)

    @Override
    public void render(float delta) {
        // --- UPDATE LOGIC ---
        combatManager.update(delta);
        animationManager.update(delta);
        if (maze != null) { // Add null check for safety during transitions
            maze.update(delta);
        }
        if (hud != null) { // Add null check
            hud.update(delta);
        }
        eventManager.update(delta); // Processes message timers

        // --- NEW: Check for System Events (like CHUNK_TRANSITION) ---
        handleSystemEvents();
        // --- END NEW ---

        // --- WORLD RENDERING ---
        ScreenUtils.clear(0, 0, 0, 1);
        shapeRenderer.setProjectionMatrix(game.viewport.getCamera().combined);

        // Ensure player and maze are not null before rendering
        if (player != null && maze != null) {
            firstPersonRenderer.render(shapeRenderer, player, maze, game.viewport, worldManager, currentLevel);
            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                entityRenderer.render(shapeRenderer, player, maze, game.viewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer);
            } else {
                entityRenderer.renderSingleMonster(shapeRenderer, player, combatManager.getMonster(), game.viewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, maze);
            }

            animationManager.render(shapeRenderer, player, game.viewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, maze);

            if (debugManager.isDebugOverlayVisible()) {
                debugRenderer.render(shapeRenderer, player, maze, game.viewport);
                if (needsAsciiRender) {
                    firstPersonRenderer.renderAsciiViewToConsole(player, maze);
                    needsAsciiRender = false;
                }
            }
        }

        // --- HUD RENDERING ---
        if (hud != null) { // Add null check
            hud.render();
        }

        // --- SCREEN-LEVEL TEXT RENDERING ---
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        font.setColor(Color.WHITE);
        font.draw(game.batch, "Level: " + currentLevel, 10, game.viewport.getWorldHeight() - 10);

        // Debug text rendering remains the same
        if (debugManager.isDebugOverlayVisible()) {
            font.draw(game.batch, "DEBUG MODE - (F1 to toggle)", 10, game.viewport.getWorldHeight() - 30);
            font.draw(game.batch, "RENDER MODE: " + debugManager.getRenderMode() + " (F2 to toggle)", 10, game.viewport.getWorldHeight() - 50); // Adjusted position
            font.setColor(Color.YELLOW);
            // (Key mappings and Player Info debug text remain unchanged)
            String[] keyMappings = {
                "--- CONTROLS ---",
                "UP   : Move Forward", "DOWN : Move Backward", "LEFT : Turn Left", "RIGHT: Turn Right",
                "", "O    : Open/Interact", "P    : Pickup/Drop Item", "U    : Use Item",
                "D    : Descend Ladder", "R    : Rest", "", "S    : Swap Hands",
                "E    : Swap with Pack", "T    : Rotate Pack", "", "A    : Attack (Combat)", "M    : Castle Map"
            };
            float yPos = game.viewport.getWorldHeight() - 80;
            for (String mapping : keyMappings) {
                font.draw(game.batch, mapping, 10, yPos);
                yPos -= 20;
            }

            if (player != null) { // Add null check for player
                String equippedWeapon = "NOTHING";
                String damage = "0";
                String range = "0";
                String isRanged = "N/A"; // Changed default
                String weaponColor = "NONE"; // Changed default
                String weaponType = "NULL";
                Item rightHandItem = player.getInventory().getRightHand();
                if (rightHandItem != null) {
                    equippedWeapon = rightHandItem.getType() != null ? rightHandItem.getType().toString() : "UNKNOWN"; // Changed default
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
                float infoX = 250;
                float infoY = game.viewport.getWorldHeight() - 100;
                font.draw(game.batch, "PLAYER INFO: DEFENSE = " + player.getArmorDefense(), infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: SPIRITUAL STRENGTH = " + player.getSpiritualStrength(), infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: WAR STRENGTH = " + player.getWarStrength(), infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: EQUIPPED WEAPON = " + equippedWeapon, infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: EQUIPPED WEAPON DAMAGE = " + damage, infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: EQUIPPED WEAPON ISRANGED = " + isRanged, infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: EQUIPPED WEAPON RANGE = " + range, infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: EQUIPPED WEAPON COLOR = " + weaponColor, infoX, infoY); infoY -= 20;
                font.draw(game.batch, "PLAYER INFO: EQUIPPED WEAPON TYPE = " + weaponType, infoX, infoY);
            }
        }

        game.batch.end();
    }

    /**
     * NEW: Checks for and processes system events from the event manager.
     */
    private void handleSystemEvents() {
        GameEvent event;

        // Check for chunk transitions only in ADVANCED mode
        if (gameMode == GameMode.ADVANCED) {
            while ((event = eventManager.findAndConsume(GameEvent.EventType.CHUNK_TRANSITION)) != null) {
                if (event.payload instanceof Gate) {
                    Gate transitionGate = (Gate) event.payload;
                    // Ensure it's a valid transition gate before proceeding
                    Gdx.app.log("GameScreen", "CHUNK_TRANSITION event received. Performing transition."); // <-- New log
                    performChunkTransition(transitionGate); // <-- This is the logic we want!
                }
            }
        }

        // ... (Check for other future system events here) ...
    }

    /**
     * NEW: Handles the "hot-swap" of the maze when moving between chunks.
     * @param transitionGate The gate the player is transitioning through.
     */
    private void performChunkTransition(Gate transitionGate) {
        if (player == null) return; // Cannot transition without a player

        Gdx.app.log("GameScreen", "Performing chunk transition via gate at ("
            + (int)transitionGate.getPosition().x + "," + (int)transitionGate.getPosition().y + ") to chunk "
            + transitionGate.getTargetChunkId());

        // 1. Save the state of the current chunk (STUBBED)
        if (maze != null) { // Only save if there's a maze to save
            worldManager.saveCurrentChunk(this.maze);
        }

        // 2. Load the new chunk
        Maze newMaze = worldManager.loadChunk(transitionGate.getTargetChunkId());
        if (newMaze == null) {
            Gdx.app.error("GameScreen", "Failed to load target chunk: " + transitionGate.getTargetChunkId());
            // Optionally, prevent the player from moving or show an error
            return;
        }
        this.maze = newMaze; // Hot-swap the maze object

        // 3. Teleport the player
        player.getPosition().set(
            transitionGate.getTargetPlayerPos().x + 0.5f,
            transitionGate.getTargetPlayerPos().y + 0.5f
        );
        Gdx.app.log("GameScreen", "Player teleported to: " + transitionGate.getTargetPlayerPos());

        // 4. Update/Re-initialize systems that depend on the maze
        // TODO: Implement proper setMaze(Maze newMaze) methods in these classes for cleaner transitions.
        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager);
        hud = new Hud(game.batch, player, maze, combatManager, eventManager); // Recreate HUD

        // Force a redraw/reset of renderers if necessary (might not be needed depending on implementation)
        // firstPersonRenderer.reset();
        // entityRenderer.clearEntities();

        Gdx.app.log("GameScreen", "Transition complete. New maze loaded for chunk " + transitionGate.getTargetChunkId());
        DebugRenderer.printMazeToConsole(maze); // For debugging
    }


    @Override
    public void resize(int width, int height) {
        game.viewport.update(width, height, true);
        if (hud != null) { // Update HUD viewport too
            hud.resize(width, height);
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        // Ensure player and maze exist before processing input
        if (player == null || maze == null) return false;

        // --- Actions available during player's turn in combat AND out of combat ---
        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            switch (keycode) {
                case Input.Keys.S: player.getInventory().swapHands(); return true;
                case Input.Keys.E: player.getInventory().swapWithPack(); return true;
                case Input.Keys.T: player.getInventory().rotatePack(); return true;
            }
        }

        // --- Actions available ONLY during combat ---
        if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            if (keycode == Input.Keys.A) {
                combatManager.playerAttack();
                return true;
            }
        }

        // --- Actions available ONLY when NOT in combat ---
        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            switch (keycode) {
                case Input.Keys.UP:
                    player.moveForward(maze, eventManager, gameMode);
                    combatManager.checkForAdjacentMonsters(); // Check for combat after moving
                    needsAsciiRender = false;
                    return true; // Added return true
                case Input.Keys.DOWN:
                    player.moveBackward(maze, eventManager, gameMode);
                    combatManager.checkForAdjacentMonsters();
                    needsAsciiRender = false;
                    return true; // Added return true
                case Input.Keys.LEFT:
                    player.turnLeft();
                    needsAsciiRender = false;
                    return true; // Added return true
                case Input.Keys.RIGHT:
                    player.turnRight();
                    needsAsciiRender = false;
                    return true; // Added return true
                case Input.Keys.O:
                    // --- MODIFIED CALL: Pass gameMode ---
                    player.interact(maze, eventManager, soundManager, gameMode, worldManager);
                    needsAsciiRender = true;
                    return true;
                case Input.Keys.P:
                    player.interactWithItem(maze, eventManager, soundManager);
                    return true; // Added return true
                case Input.Keys.U:
                    player.useItem(eventManager);
                    return true; // Added return true
                case Input.Keys.D:
                    int targetX = (int) (player.getPosition().x + player.getFacing().getVector().x);
                    int targetY = (int) (player.getPosition().y + player.getFacing().getVector().y);
                    GridPoint2 targetTile = new GridPoint2(targetX, targetY);
                    if (maze.getLadders().containsKey(targetTile)) {
                        descendToNextLevel();
                    }
                    return true; // Added return true
                case Input.Keys.R:
                    player.rest();
                    return true; // Added return true
            }
        }

        // --- Global actions ---
        switch (keycode) {
            case Input.Keys.F1: debugManager.toggleOverlay(); return true;
            case Input.Keys.F2: debugManager.toggleRenderMode(); return true;
            case Input.Keys.M:
                if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                    game.setScreen(new CastleMapScreen(game, player, maze, this));
                }
                return true;
        }

        return false; // Return false if key wasn't handled
    }

    // --- cleanUpOrphanedDoors() REMOVED ---

    // --- Unused InputProcessor methods ---
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        font.dispose();
        if (hud != null) {
            hud.dispose();
        }
        // Dispose renderers if they own disposable resources
        // firstPersonRenderer.dispose(); // If needed
        if (entityRenderer != null) {
            entityRenderer.dispose();
        }
        // debugRenderer.dispose(); // If needed
        if (soundManager != null) {
            soundManager.dispose();
        }
        // Dispose animationManager? Depends if it holds textures directly
    }
}
