package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport; // Ensure this is imported
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.*;
import com.bpm.minotaur.rendering.*;

import java.util.List;

public class GameScreen extends BaseScreen implements InputProcessor, Disposable {

    // --- Core Dependencies ---
    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private StochasticManager stochasticManager;
    private final BitmapFont font = new BitmapFont();
    private final GameMode gameMode;

    private boolean needsAsciiRender = false;

    // --- NEW: World/Game Mode ---
    private final WorldManager worldManager;
    private final int level; // Initial dungeon level

    // --- Renderers ---
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final EntityRenderer entityRenderer = new EntityRenderer(game.getItemDataManager());
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
    private MonsterAiManager monsterAiManager;
    private PotionManager potionManager;

    private FrameBuffer fbo;
    private ShaderProgram crtShader;
    private boolean useCrtFilter = true; // Toggle state
    private final SpriteBatch postProcessBatch = new SpriteBatch(); // Dedicated batch for the final draw
    private float time = 0f;

    // --- VIEWPORT FIX: Dedicated internal viewport for FBO rendering ---
    private final Viewport fboViewport;
    private static final int VIRTUAL_WIDTH = 1920;
    private static final int VIRTUAL_HEIGHT = 1080;

    private float trauma = 0f; // Current shake intensity
    private final Vector2 originalDir = new Vector2();
    private final Vector2 originalPlane = new Vector2();
    private final java.util.Random rng = new java.util.Random();

    // --- Fix for Reset Bug ---
    private boolean hasLoadedLevel = false;

    // --- Constructor Updated ---
    public GameScreen(Tarmin2 game, int level, Difficulty difficulty, GameMode gameMode) {
        super(game);
        this.difficulty = difficulty;
        this.gameMode = gameMode;
        this.level = level;
        this.currentLevel = level;
        this.stochasticManager = new StochasticManager();

        // Initialize FBO Viewport (Locked to 1920x1080)
        this.fboViewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        // We can update it once here to ensure camera is centered relative to 0,0 if needed,
        // though applying it in render is the key.
        this.fboViewport.update(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);

        // [MOVED] Initialize SoundManager HERE so we can pass it to WorldManager
        this.soundManager = new SoundManager(debugManager);

        // --- Initialize WorldManager ---
        // [UPDATED] Now passing soundManager as the last parameter
        this.worldManager = new WorldManager(gameMode, difficulty, level,
            game.getMonsterDataManager(),
            game.getItemDataManager(),
            game.getAssetManager(),
            game.getSpawnTableData(),
            this.soundManager); // <-- Passed here

        this.monsterAiManager = new MonsterAiManager();

        // Music based on initial level
        switch (level) {
            case 1:
                MusicManager.getInstance().playTrack("sounds/music/tarmin_maze.mp3");
                break;
            default:
                MusicManager.getInstance().playTrack("sounds/music/tarmin_fuxx.ogg");
                break;
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);

        // Only initialize these if they don't exist (preserving state on return from Inventory)
        if (animationManager == null) {
            animationManager = new AnimationManager(entityRenderer);
        }
        if (eventManager == null) {
            eventManager = new GameEventManager();
        }

        // --- FBO FIX: Initialize ONCE with static resolution ---
        if (fbo == null) {
            fbo = new FrameBuffer(Pixmap.Format.RGB888, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);
        }

        // Initialize Shader
        if (crtShader == null) {
            ShaderProgram.pedantic = false;
            crtShader = new ShaderProgram(Gdx.files.internal("shaders/crt.vert"), Gdx.files.internal("shaders/crt.frag"));
            if (!crtShader.isCompiled()) {
                Gdx.app.error("Shader", "Compilation failed:\n" + crtShader.getLog());
                useCrtFilter = false;
            }
        }

        // Fix: Only generate the level if we haven't done so yet for this screen instance.
        // This prevents resetting player position when returning from InventoryScreen.
        if (!hasLoadedLevel) {
            generateLevel(currentLevel);
            hasLoadedLevel = true;
        }
    }

    @Override
    public void hide() {
        if (worldManager != null && maze != null && gameMode == GameMode.ADVANCED) {
            worldManager.saveCurrentChunk(maze);
        }

        if (potionManager != null && gameMode == GameMode.ADVANCED) {
            potionManager.saveState();
        }

        // NOTE: This stops music every time you open inventory.
        // If you want music to persist in inventory, comment this out or add logic.
        MusicManager.getInstance().stop();
    }

    private void descendToNextLevel() {
        if (worldManager != null && maze != null && gameMode == GameMode.ADVANCED) {
            worldManager.saveCurrentChunk(maze);
        }

        if (soundManager != null) {
            soundManager.stopWeatherEffects();
        }

        currentLevel++;
        Gdx.app.log("GameScreen", "Descending to level " + currentLevel);

        if (worldManager != null) {
            worldManager.setCurrentLevel(currentLevel);
            worldManager.clearLoadedChunks();
        }

        generateLevel(currentLevel);
    }

    /**
     * Initializes or re-initializes the game state for a specific level.
     * In ADVANCED mode, this might involve loading a chunk; in CLASSIC, it generates a single maze.
     * @param levelNumber The level to load/generate.
     */
    private void generateLevel(int levelNumber) {
        Gdx.app.log("GameScreen [DEBUG]", "generateLevel START. Player is " + (player == null ? "NULL" : "NOT NULL"));

        if (player == null) {
            // 1. Create PotionManager
            if (this.potionManager == null) {
                Gdx.app.log("GameScreen [DEBUG]", "Creating NEW PotionManager...");
                this.potionManager = new PotionManager(this.eventManager);

                Gdx.app.log("GameScreen [DEBUG]", "Setting PotionManager in ItemDataManager...");
                game.getItemDataManager().setPotionManager(this.potionManager);

                if (potionManager.hasSaveState()) {
                    Gdx.app.log("GameScreen [DEBUG]", "Potion save file found. Loading state...");
                    potionManager.loadState();
                } else {
                    Gdx.app.log("GameScreen [DEBUG]", "No potion save file. Initializing NEW potion map...");
                    List<com.bpm.minotaur.gamedata.item.Item.ItemType> potionTypes = game.getItemDataManager().getAllPotionAppearanceTypes();
                    this.potionManager.initializeNewGame(potionTypes);
                }
            }

            // --- REORDERING FIX ---
            // We must generate the initial maze FIRST. This triggers the chunk generator,
            // which calculates the 'home tile' center and updates the start pos.
            // If we fetch startPos before this, we get the default (1,1).

            // 2. Load Maze (Moved UP)
            Gdx.app.log("GameScreen [DEBUG]", "Calling getInitialMaze()...");
            this.maze = worldManager.getInitialMaze();

            // 3. Create Player (Moved DOWN)
            Gdx.app.log("GameScreen [DEBUG]", "Creating NEW Player...");
            // Now this call will return the *updated* spawn point from the generator
            GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
            player = new Player(startPos.x, startPos.y, difficulty,
                game.getItemDataManager(), game.getAssetManager());
            player.getStatusManager().initialize(this.eventManager);

            Gdx.app.log("GameScreen [DEBUG]", "Setting maze reference on player.");
            player.setMaze(this.maze);

        } else {
            // Player already exists (e.g., descending stairs)
            Gdx.app.log("GameScreen [DEBUG]", "Player exists. Calling getInitialMaze() for new level...");
            this.maze = worldManager.getInitialMaze();
            resetPlayerPosition();
            player.setMaze(this.maze);
        }

        // Initialize systems that depend on the player and maze
        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager, worldManager, game.getItemDataManager(), stochasticManager);
        hud = new Hud(game.getBatch(), player, maze, combatManager, eventManager, worldManager, game, debugManager, gameMode);

        // --- FIX: Force HUD to Virtual Resolution immediately ---
        hud.resize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        Gdx.app.log("GameScreen", "Level " + levelNumber + " loaded/generated.");

        // Set the visual theme based on level and game mode
        if (this.gameMode == GameMode.ADVANCED && levelNumber == 1) {
            firstPersonRenderer.setTheme(RetroTheme.ADVANCED_COLOR_THEME_BLUE);
        } else {
            firstPersonRenderer.setTheme(RetroTheme.STANDARD_THEME);
        }

        DebugRenderer.printMazeToConsole(maze);
    }

    private void resetPlayerPosition() {
        GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
        player.getPosition().set(startPos.x + 0.5f, startPos.y + 0.5f);
        Gdx.app.log("GameScreen", "Reset player position to: " + startPos);
    }

    @Override
    public void render(float delta) {
        time += delta;

        // --- UPDATE LOGIC ---
        combatManager.update(delta);
        animationManager.update(delta);
        if (maze != null) maze.update(delta);
        if (hud != null) hud.update(delta);
        eventManager.update(delta);
        handleSystemEvents();

        // Update World & Weather
        if (worldManager != null) {
            worldManager.update(delta);

            // SYNC TRAUMA WITH WEATHER
            if (worldManager.getWeatherManager() != null) {
                float targetTrauma = worldManager.getWeatherManager().getTraumaLevel();
                this.trauma = com.badlogic.gdx.math.MathUtils.lerp(this.trauma, targetTrauma, 2.0f * delta);
            }
        }

        if (stochasticManager != null) {
            stochasticManager.update(delta);
        }

        if (gameMode == GameMode.ADVANCED) {
            checkForProactiveChunkLoading();
        }

        // --- RENDER PASS 1: WORLD -> FBO (Fixed 1920x1080) ---
        if (useCrtFilter) {
            fbo.begin();
            // Apply the Internal/Virtual Viewport (1920x1080)
            fboViewport.apply();
            // shapeRenderer usually needs to know the projection matrix:
            shapeRenderer.setProjectionMatrix(fboViewport.getCamera().combined);
        }

        // Clear Screen (Black) - inside FBO if active, or screen if not
        ScreenUtils.clear(0, 0, 0, 1);

        // Ensure renderers use the correct projection (FBO viewport)
        Viewport currentViewport = useCrtFilter ? fboViewport : game.getViewport();

        // APPLY CAMERA SHAKE
        boolean isShaking = (player != null && trauma > 0.01f);

        if (isShaking) {
            originalDir.set(player.getDirectionVector());
            originalPlane.set(player.getCameraPlane());

            float shakePower = trauma * 0.1f;
            float angle = (rng.nextFloat() - 0.5f) * shakePower;

            player.getDirectionVector().rotateRad(angle);
            player.getCameraPlane().rotateRad(angle);
        }

        // --- RENDER WORLD ---
        if (player != null && maze != null) {
            // Note: We pass the 'currentViewport' (FBO viewport) to renderers so they calculate FOV/rays correctly for 1920px width
            firstPersonRenderer.render(shapeRenderer, player, maze, currentViewport, worldManager, currentLevel, gameMode);

            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                entityRenderer.render(shapeRenderer, player, maze, currentViewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, worldManager);
            } else {
                entityRenderer.renderSingleMonster(shapeRenderer, player, combatManager.getMonster(), currentViewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, maze, worldManager);
            }

            animationManager.render(shapeRenderer, player, currentViewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, maze);

            if (debugManager.isDebugOverlayVisible()) {
                debugRenderer.render(shapeRenderer, player, maze, currentViewport);
                if (needsAsciiRender) {
                    firstPersonRenderer.renderAsciiViewToConsole(player, maze);
                    needsAsciiRender = false;
                }
            }
        }

        // RESTORE CAMERA STATE
        if (isShaking) {
            player.getDirectionVector().set(originalDir);
            player.getCameraPlane().set(originalPlane);
        }

        if (stochasticManager != null && combatManager.getCurrentState() == CombatManager.CombatState.PHYSICS_RESOLUTION) {
            stochasticManager.render();
        }

        // --- HUD RENDERING (Also into FBO) ---
        if (hud != null) {
            // Hud manages its own Stage/Viewport internally.
            // We assume Hud uses a 1920x1080 FitViewport as defined in its constructor.
            hud.render();
        }

        if (stochasticManager != null) {
            CombatManager.CombatState state = combatManager.getCurrentState();
            if (state == CombatManager.CombatState.PHYSICS_RESOLUTION ||
                state == CombatManager.CombatState.PHYSICS_DELAY) {
                stochasticManager.render();
            }
        }

        // Render damage text (animation manager uses sprite batch)
        game.getBatch().setProjectionMatrix(currentViewport.getCamera().combined);
        game.getBatch().begin();
        animationManager.renderDamageText(game.getBatch(), currentViewport);
        font.setColor(Color.WHITE);
        font.draw(game.getBatch(), "Level: " + currentLevel, 10, currentViewport.getWorldHeight() - 10);
        game.getBatch().end();

        // --- RENDER PASS 2: FBO -> SCREEN (Window Resolution) ---
        if (useCrtFilter) {
            fbo.end();

            // Switch to the actual Physical Window Viewport
            game.getViewport().apply();
            ScreenUtils.clear(0, 0, 0, 1);

            postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
            postProcessBatch.begin();
            postProcessBatch.setShader(crtShader);
            crtShader.setUniformf("u_time", time);

            Texture fboTexture = fbo.getColorBufferTexture();

            // Draw the 1920x1080 FBO texture into the Virtual World space of the FitViewport.
            // FitViewport handles the scaling to the physical window (black bars).
            // NOTE: LibGDX FrameBuffers are often flipped on Y. If upside down, flip V coordinate.
            // standard draw(tex, x, y, w, h, u, v, u2, v2). Default u=0, v=0 is usually top-left for FBO?
            // Actually, in standard LibGDX 2D, 0,0 is bottom-left. FBO texture usually comes out with 0,0 at bottom-left IF standard.
            // However, previous code used 0,0,1,1. Let's stick to standard draw first.
            postProcessBatch.draw(fboTexture, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, 0, 0, 1, 1);

            postProcessBatch.end();
        }
    }

    private void checkForProactiveChunkLoading() {
        if (player == null || worldManager == null || maze == null) {
            return;
        }

        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());

        if (!biome.isSeamless()) {
            return;
        }

        int triggerDistance;
        if (biome.hasFogOfWar()) {
            triggerDistance = biome.getFogDistance() + 1;
        } else {
            triggerDistance = 5;
        }

        triggerDistance = Math.max(2, triggerDistance);

        GridPoint2 playerPos = new GridPoint2((int)player.getPosition().x, (int)player.getPosition().y);
        GridPoint2 currentChunkId = worldManager.getCurrentPlayerChunkId();

        int height = maze.getHeight();
        int width = maze.getWidth();

        if (playerPos.y >= height - 1 - triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x, currentChunkId.y + 1));
        }
        if (playerPos.y <= triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x, currentChunkId.y - 1));
        }
        if (playerPos.x >= width - 1 - triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x + 1, currentChunkId.y));
        }
        if (playerPos.x <= triggerDistance) {
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x - 1, currentChunkId.y));
        }
    }

    private void handleSystemEvents() {
        GameEvent event;

        if (gameMode == GameMode.ADVANCED) {
            while ((event = eventManager.findAndConsume(GameEvent.EventType.CHUNK_TRANSITION)) != null) {
                if (event.payload instanceof Gate) {
                    Gate transitionGate = (Gate) event.payload;
                    Gdx.app.log("GameScreen", "CHUNK_TRANSITION event received. Performing transition.");
                    performChunkTransition(transitionGate);
                }
            }
        }
    }

    private void swapToChunk(Maze newMaze) {
        this.maze = newMaze;
        player.setMaze(newMaze);

        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager, worldManager, game.getItemDataManager(), stochasticManager);
        hud = new Hud(game.getBatch(), player, maze, combatManager, eventManager, worldManager, game, debugManager, gameMode);

        hud.resize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        Gdx.app.log("GameScreen", "Swap complete. New maze loaded for chunk " + worldManager.getCurrentPlayerChunkId());
        DebugRenderer.printMazeToConsole(maze);
    }

    private void playerTurnTakesAction() {
        processPlayerStatusEffects();
        player.getStatusManager().updateTurn();

        if (monsterAiManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            monsterAiManager.updateMonsterLogic(maze, player, true, combatManager);
        }
        combatManager.checkForAdjacentMonsters();
    }


    private void performChunkTransition(Gate transitionGate) {
        if (player == null) return;

        Gdx.app.log("GameScreen", "Performing chunk transition via gate at ("
            + (int)transitionGate.getPosition().x + "," + (int)transitionGate.getPosition().y + ") to chunk "
            + transitionGate.getTargetChunkId());

        if (maze != null) {
            worldManager.saveCurrentChunk(this.maze);
        }

        Maze newMaze = worldManager.loadChunk(transitionGate.getTargetChunkId());

        if (newMaze == null) {
            Gdx.app.error("GameScreen", "Failed to load target chunk (impassable?): " + transitionGate.getTargetChunkId());
            eventManager.addEvent(new GameEvent("A strange force blocks your path.", 2f));
            transitionGate.close();
            return;
        }

        player.getPosition().set(
            transitionGate.getTargetPlayerPos().x + 0.5f,
            transitionGate.getTargetPlayerPos().y + 0.5f
        );
        Gdx.app.log("GameScreen", "Player teleported to: " + transitionGate.getTargetPlayerPos());

        worldManager.setCurrentChunk(transitionGate.getTargetChunkId());
        swapToChunk(newMaze);
    }


    @Override
    public void resize(int width, int height) {
        // Update the physical window viewport (handles black bars)
        game.getViewport().update(width, height, true);

        // Update postProcessBatch projection to match the new window viewport
        postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);

        // --- FBO RESIZE FIX ---
        // Do NOT dispose/recreate FBO. We want to keep internal resolution fixed.
        // Do NOT resize fboViewport. It stays 1920x1080.

        if (hud != null) {
            // Force HUD to resize to Virtual Resolution, NOT Window Resolution.
            // This ensures HUD elements stay in the correct place on the FBO.
            hud.resize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        }

        // FBO recreation removed.
    }

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

    private GridPoint2 getSeamlessTargetPlayerPos(GridPoint2 playerPos, Direction moveDir) {
        if (maze == null) return playerPos;

        int height = maze.getHeight();
        int width = maze.getWidth();

        if (moveDir == Direction.NORTH) return new GridPoint2(playerPos.x, 0);
        if (moveDir == Direction.SOUTH) return new GridPoint2(playerPos.x, height - 1);
        if (moveDir == Direction.EAST)  return new GridPoint2(0, playerPos.y);
        if (moveDir == Direction.WEST)  return new GridPoint2(width - 1, playerPos.y);
        return playerPos;
    }


    private void processPlayerStatusEffects() {
        if (player == null) return;

        if (player.getStatusManager().hasEffect(StatusEffectType.POISONED)) {
            ActiveStatusEffect poison = player.getStatusManager().getEffect(StatusEffectType.POISONED);
            int damage = poison.getPotency();

            player.takeStatusEffectDamage(damage, DamageType.POISON);
            eventManager.addEvent(new GameEvent("You take " + damage + " poison damage!", 2f));
            Gdx.app.log("GameScreen", "Player took " + damage + " poison damage.");
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (player == null || maze == null) return false;

        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            switch (keycode) {
                case Input.Keys.S:
                    player.getInventory().swapHands();
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) combatManager.passTurnToMonster();
                    return true;
                case Input.Keys.E:
                    player.getInventory().swapWithPack();
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) combatManager.passTurnToMonster();
                    return true;
                case Input.Keys.T:
                    player.getInventory().rotatePack();
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) combatManager.passTurnToMonster();
                    return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            if (keycode == Input.Keys.A || keycode == Input.Keys.SPACE) {
                combatManager.playerAttack();
                return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {

            if (keycode == Input.Keys.A || keycode == Input.Keys.SPACE) {
                if (player.getInventory().getRightHand() != null && player.getInventory().getRightHand().isRanged()) {
                    boolean attacked = combatManager.performRangedAttack();
                    if (attacked) {
                        playerTurnTakesAction();
                    }
                    return true;
                }
            }

            switch (keycode) {
                case Input.Keys.UP:
                    player.moveForward(maze, eventManager, gameMode);
                    combatManager.checkForAdjacentMonsters();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                case Input.Keys.DOWN:
                    player.moveBackward(maze, eventManager, gameMode);
                    combatManager.checkForAdjacentMonsters();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                case Input.Keys.LEFT:
                    player.turnLeft();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                case Input.Keys.RIGHT:
                    player.turnRight();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;

                case Input.Keys.O:
                    player.interact(maze, eventManager, soundManager, gameMode, worldManager);
                    playerTurnTakesAction();
                    needsAsciiRender = true;
                    return true;
                case Input.Keys.P:
                    player.interactWithItem(maze, eventManager, soundManager);
                    playerTurnTakesAction();
                    return true;
                case Input.Keys.U:
                    player.useItem(eventManager, this.potionManager);
                    playerTurnTakesAction();
                    return true;
                case Input.Keys.D:
                    GridPoint2 atFeet = new GridPoint2((int)player.getPosition().x, (int)player.getPosition().y);
                    GridPoint2 inFront = new GridPoint2(
                        (int)(player.getPosition().x + player.getFacing().getVector().x),
                        (int)(player.getPosition().y + player.getFacing().getVector().y)
                    );

                    if (maze.getLadders().containsKey(atFeet) || maze.getLadders().containsKey(inFront)) {
                        descendToNextLevel();
                        playerTurnTakesAction();
                        return true;
                    }
                    return true;
                case Input.Keys.R:
                    player.rest(eventManager);
                    playerTurnTakesAction();
                    return true;
            }
        }

        switch (keycode) {
            case Input.Keys.F1: debugManager.toggleOverlay(); return true;
            case Input.Keys.F2: debugManager.toggleRenderMode(); return true;
            case Input.Keys.F3:
                SpawnManager.DEBUG_FORCE_MODIFIERS = !SpawnManager.DEBUG_FORCE_MODIFIERS;
                eventManager.addEvent(new GameEvent("Debug Force Modifiers: " + (SpawnManager.DEBUG_FORCE_MODIFIERS ? "ON" : "OFF"), 2f));
                return true;
            case Input.Keys.M:
                if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                    game.setScreen(new CastleMapScreen(game, player, maze, this));
                }
                return true;
            case Input.Keys.I:
                // Allow opening inventory only if not in active combat animation?
                // For now, allow it if INACTIVE or PLAYER_TURN
                if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE ||
                    combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
                    game.setScreen(new InventoryScreen(game, this, player, maze));
                }
                return true;

            case Input.Keys.F6:
                useCrtFilter = !useCrtFilter;
                eventManager.addEvent(new GameEvent("CRT Filter: " + (useCrtFilter ? "ON" : "OFF"), 2f));
                return true;

            case Input.Keys.F7:
                if (worldManager.getWeatherManager() != null) {
                    worldManager.getWeatherManager().debugCycleWeather();
                    eventManager.addEvent(new GameEvent("Debug Weather: " + worldManager.getWeatherManager().getCurrentWeather(), 2f));
                }
                return true;

            case Input.Keys.F8:
                if (worldManager.getWeatherManager() != null) {
                    worldManager.getWeatherManager().debugCycleIntensity();
                    eventManager.addEvent(new GameEvent("Debug Intensity: " + worldManager.getWeatherManager().getCurrentIntensity(), 2f));
                }
                return true;

            case Input.Keys.F9:
                Gdx.app.log("GameScreen", "Dumping Exploration Memory to Console...");
                DebugRenderer.printExplorationToConsole(maze);
                eventManager.addEvent(new GameEvent("Exploration Dumped to Console", 2f));
                return true;
        }

        return false;
    }

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
        if (entityRenderer != null) {
            entityRenderer.dispose();
        }
        if (soundManager != null) {
            soundManager.dispose();
        }

        if (fbo != null) fbo.dispose();
        if (crtShader != null) crtShader.dispose();
        postProcessBatch.dispose();
    }
}
