package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
// No longer needed: import com.bpm.minotaur.generation.ChunkGenerator;
import com.bpm.minotaur.generation.Biome;
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
            // Save the current chunk (e.g., L1_0_0) before we change levels
            worldManager.saveCurrentChunk(maze);
        }

        // Increment the level counter in GameScreen
        currentLevel++;
        Gdx.app.log("GameScreen", "Descending to level " + currentLevel);

        if (worldManager != null) {
            // --- [THE FIX] ---

            // 1. Tell WorldManager the new level. This ensures any *new*
            //    save/load/generation calls use the new level number.
            worldManager.setCurrentLevel(currentLevel);

            // 2. CRITICAL: Clear the 2D chunk cache. This forces
            //    loadChunk(0,0) to fail its cache check and proceed to
            //    file-checking and generation for the new level.
            worldManager.clearLoadedChunks();

            // --- [END FIX] ---
        }

        // This call will now work.
        // generateLevel() -> getInitialMaze() -> loadChunk(0,0)
        // -> Cache is empty (miss)
        // -> File check looks for "chunk_L2_0_0.json" (miss)
        // -> Triggers new chunk generation using currentLevel=2
        generateLevel(currentLevel);
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
            player.setMaze(this.maze); // <-- [FIX] Player needs initial maze reference
        } else {
            // Player already exists, reset position to the start of the newly loaded/generated maze
            resetPlayerPosition();
            player.setMaze(this.maze); // <-- [FIX] Player needs new maze reference
        }

        // Initialize systems that depend on the player and maze
        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager, worldManager);
        hud = new Hud(game.batch, player, maze, combatManager, eventManager, worldManager, game, debugManager, gameMode);

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

        // --- [NEW] PROACTIVE CHUNK LOADING ---
        if (gameMode == GameMode.ADVANCED) {
            checkForProactiveChunkLoading();
        }
        // --- END NEW ---

        // --- WORLD RENDERING ---
        ScreenUtils.clear(0, 0, 0, 1);
        shapeRenderer.setProjectionMatrix(game.viewport.getCamera().combined);

        // Ensure player and maze are not null before rendering
        if (player != null && maze != null) {
            firstPersonRenderer.render(shapeRenderer, player, maze, game.viewport, worldManager, currentLevel, gameMode);

            // --- [FIX] Pass WorldManager to EntityRenderer for Fog of War check ---
            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                entityRenderer.render(shapeRenderer, player, maze, game.viewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, worldManager);
            } else {
                entityRenderer.renderSingleMonster(shapeRenderer, player, combatManager.getMonster(), game.viewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, maze, worldManager);
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

        animationManager.renderDamageText(game.batch, game.viewport);


        font.setColor(Color.WHITE);
        font.draw(game.batch, "Level: " + currentLevel, 10, game.viewport.getWorldHeight() - 10);
        game.batch.end();
        // --- [NEW] REORGANIZED DEBUG TEXT ---

    }

    /**
     * [NEW] Checks player proximity to borders and requests adjacent chunks to be loaded.
     */
    private void checkForProactiveChunkLoading() {
        if (player == null || worldManager == null || maze == null) { // <-- [FIX] Added maze null check
            return;
        }

        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());

        // Only run this for seamless biomes
        if (!biome.isSeamless()) {
            return;
        }

        int triggerDistance;
        if (biome.hasFogOfWar()) {
            // Trigger load when player is 1 tile *before* the border becomes visible
            triggerDistance = biome.getFogDistance() + 1;
        } else {
            triggerDistance = 5; // Default trigger distance for seamless biomes without fog
        }

        // Ensure trigger distance is at least 2 tiles away
        triggerDistance = Math.max(2, triggerDistance);

        GridPoint2 playerPos = new GridPoint2((int)player.getPosition().x, (int)player.getPosition().y);
        GridPoint2 currentChunkId = worldManager.getCurrentPlayerChunkId();

        // --- [FIX] Use maze.getHeight() and maze.getWidth() ---
        int height = maze.getHeight();
        int width = maze.getWidth();

        // Check North
        if (playerPos.y >= height - 1 - triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x, currentChunkId.y + 1));
        }
        // Check South
        if (playerPos.y <= triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x, currentChunkId.y - 1));
        }
        // Check East
        if (playerPos.x >= width - 1 - triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x + 1, currentChunkId.y));
        }
        // Check West
        if (playerPos.x <= triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x - 1, currentChunkId.y));
        }
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
     * [NEW] Refactored logic to hot-swap the active maze.
     * @param newMaze The newly loaded maze to make active.
     */
    private void swapToChunk(Maze newMaze) {
        this.maze = newMaze; // Hot-swap the maze object
        player.setMaze(newMaze); // Update player's maze reference

        // Re-initialize systems that depend on the maze
        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager, worldManager);
        hud = new Hud(game.batch, player, maze, combatManager, eventManager, worldManager, game, debugManager, gameMode); // Recreate HUD

        Gdx.app.log("GameScreen", "Swap complete. New maze loaded for chunk " + worldManager.getCurrentPlayerChunkId());
        DebugRenderer.printMazeToConsole(maze); // For debugging
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
            // This means the target biome was impassable (e.g., Ocean)
            Gdx.app.error("GameScreen", "Failed to load target chunk (impassable?): " + transitionGate.getTargetChunkId());
            eventManager.addEvent(new GameEvent("A strange force blocks your path.", 2f));
            // IMPORTANT: Close the gate the player just tried to open
            transitionGate.close(); // <-- We need to add this method!
            return; // Abort the transition
        }

        // 3. Teleport the player
        player.getPosition().set(
            transitionGate.getTargetPlayerPos().x + 0.5f,
            transitionGate.getTargetPlayerPos().y + 0.5f
        );
        Gdx.app.log("GameScreen", "Player teleported to: " + transitionGate.getTargetPlayerPos());

        // 4. Update WorldManager's internal state *before* swapping
        worldManager.setCurrentChunk(transitionGate.getTargetChunkId());

        // 5. Hot-swap maze and re-initialize systems
        swapToChunk(newMaze);
    }


    @Override
    public void resize(int width, int height) {
        game.viewport.update(width, height, true);
        if (hud != null) { // Update HUD viewport too
            hud.resize(width, height);
        }
    }

    /**
     * [NEW] Helper to check if a player's move would cross a chunk border.
     * [FIXED] Now checks against dynamic maze height/width.
     * @param playerPos The player's current grid position.
     * @param moveDir The direction of movement (e.g., player.getFacing()).
     * @return True if the move would exit the current chunk, false otherwise.
     */
    private boolean isMoveSeamlessTransition(GridPoint2 playerPos, Direction moveDir) {
        if (maze == null) return false;

        int height = maze.getHeight();
        int width = maze.getWidth();

        if (moveDir == Direction.NORTH && playerPos.y == height - 1) return true;
        if (moveDir == Direction.SOUTH && playerPos.y == 0) return true;
        if (moveDir == Direction.EAST && playerPos.x == width - 1) return true;
        if (moveDir == Direction.WEST && playerPos.x == 0) return true;

        return false;
    }

    /**
     * [NEW] Calculates the player's new position in the adjacent chunk.
     * [FIXED] Now uses dynamic maze height/width.
     * @param playerPos The player's current position (before crossing).
     * @param moveDir The direction of crossing.
     * @return The new GridPoint2 for the player in the new chunk.
     */
    private GridPoint2 getSeamlessTargetPlayerPos(GridPoint2 playerPos, Direction moveDir) {
        if (maze == null) return playerPos; // Safety check

        int height = maze.getHeight();
        int width = maze.getWidth();

        if (moveDir == Direction.NORTH) return new GridPoint2(playerPos.x, 0);
        if (moveDir == Direction.SOUTH) return new GridPoint2(playerPos.x, height - 1);
        if (moveDir == Direction.EAST)  return new GridPoint2(0, playerPos.y);
        if (moveDir == Direction.WEST)  return new GridPoint2(width - 1, playerPos.y);
        return playerPos; // Should not happen
    }


    @Override
    public boolean keyDown(int keycode) {
        // Ensure player and maze exist before processing input
        if (player == null || maze == null) return false;

        // --- Actions available during player's turn in combat AND out of combat ---
        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            switch (keycode) {
                case Input.Keys.S:
                    player.getInventory().swapHands();
                    // If in combat, this counts as a turn
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
                        combatManager.passTurnToMonster();
                    }
                    return true;
                case Input.Keys.E:
                    player.getInventory().swapWithPack();
                    // If in combat, this counts as a turn
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
                        combatManager.passTurnToMonster();
                    }
                    return true;
                case Input.Keys.T:
                    player.getInventory().rotatePack();
                    // If in combat, this counts as a turn
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
                        combatManager.passTurnToMonster();
                    }
                    return true;
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
                case Input.Keys.DOWN:
                    // --- [FIXED] SEAMLESS TRANSITION LOGIC (Forward/Back) ---
                    // --- [FIXED] SEAMLESS TRANSITION LOGIC (Forward/Back) ---
                    if (gameMode == GameMode.ADVANCED) {
                        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
                        if (biome.isSeamless()) {
                            Direction moveDir = (keycode == Input.Keys.UP) ? player.getFacing() : player.getFacing().getOpposite();
                            GridPoint2 playerPos = new GridPoint2((int)player.getPosition().x, (int)player.getPosition().y);

                            if (isMoveSeamlessTransition(playerPos, moveDir)) {
                                GridPoint2 targetChunkId = worldManager.getAdjacentChunkId(moveDir);
                                GridPoint2 targetPlayerPos = getSeamlessTargetPlayerPos(playerPos, moveDir);

                                Maze targetChunk = worldManager.getChunk(targetChunkId);
                                if (targetChunk != null && targetChunk.isPassable(targetPlayerPos.x, targetPlayerPos.y)) {
                                    Gdx.app.log("GameScreen", "Seamless transition triggered to " + targetChunkId);
                                    worldManager.transitionPlayerToChunk(player, targetChunkId, targetPlayerPos);
                                    swapToChunk(targetChunk);
                                    combatManager.checkForAdjacentMonsters();
                                    return true; // Consume key
                                } else if (targetChunk == null) {
                                    Gdx.app.log("GameScreen", "Seamless transition failed: Target chunk not loaded (or PCL hasn't run).");
                                    return true; // Block move
                                } else {
                                    Gdx.app.log("GameScreen", "Seamless transition blocked by impassable tile at target.");
                                    // --- [THE FIX] ---
                                    // We MUST return true here to block the move and prevent
                                    // the player from walking out of bounds.
                                    return true;
                                }
                            }
                        }
                    }
                    // --- END TRANSITION LOGIC ---

                    // Standard movement
                    if (keycode == Input.Keys.UP) {
                        player.moveForward(maze, eventManager, gameMode);
                    } else {
                        player.moveBackward(maze, eventManager, gameMode);
                    }
                    combatManager.checkForAdjacentMonsters(); // Check for combat after moving
                    needsAsciiRender = false;
                    return true;

                case Input.Keys.LEFT:
                case Input.Keys.RIGHT:
                    // --- [FIXED] Removed faulty strafe-transition logic ---
                    // Standard turning
                    if (keycode == Input.Keys.LEFT) {
                        player.turnLeft();
                    } else {
                        player.turnRight();
                    }
                    needsAsciiRender = false;
                    return true;

                // ... (rest of keyDown method) ...
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
                    // 1. Check player's CURRENT tile (at-feet)
                    int playerX = (int) player.getPosition().x;
                    int playerY = (int) player.getPosition().y;
                    GridPoint2 playerTile = new GridPoint2(playerX, playerY);

                    if (maze.getLadders().containsKey(playerTile)) {
                        descendToNextLevel();
                        return true; // Found ladder at feet, descend
                    }

                    // 2. If no ladder at feet, check tile IN FRONT
                    int targetX = (int) (player.getPosition().x + player.getFacing().getVector().x);
                    int targetY = (int) (player.getPosition().y + player.getFacing().getVector().y);
                    GridPoint2 targetTile = new GridPoint2(targetX, targetY);

                    if (maze.getLadders().containsKey(targetTile)) {
                        descendToNextLevel();
                        return true; // Found ladder in front, descend
                    }
                    return true; // Added return true
                case Input.Keys.R:
                    player.rest(eventManager);
                    return true; // Added return true
            }
        }

        // --- Global actions ---
        switch (keycode) {
            case Input.Keys.F1: debugManager.toggleOverlay(); return true;
            case Input.Keys.F2: debugManager.toggleRenderMode(); return true;
            case Input.Keys.F3:
                SpawnManager.DEBUG_FORCE_MODIFIERS = !SpawnManager.DEBUG_FORCE_MODIFIERS;
                String status = SpawnManager.DEBUG_FORCE_MODIFIERS ? "ON" : "OFF";
                eventManager.addEvent(new GameEvent("Debug Force Modifiers: " + status, 2f));
                return true;
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
