package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemDataManager;
import com.bpm.minotaur.gamedata.item.ItemTemplate;
import com.bpm.minotaur.gamedata.item.ItemType;
import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.*;
import com.bpm.minotaur.rendering.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // --- NEW: 3D Rendering Components ---
    private PerspectiveCamera camera3d;
    private Environment environment;
    // Cache map to store a ModelInstance for every 3D Item
    private final Map<Item, ModelInstance> item3dCache = new HashMap<>();

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
        this.fboViewport.update(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);

        // [MOVED] Initialize SoundManager HERE so we can pass it to WorldManager
        this.soundManager = new SoundManager(debugManager);

        // --- Initialize WorldManager ---
        this.worldManager = new WorldManager(gameMode, difficulty, level,
            game.getMonsterDataManager(),
            game.getItemDataManager(),
            game.getAssetManager(),
            game.getSpawnTableData(),
            this.soundManager);

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

        if (!hasLoadedLevel) {
            generateLevel(currentLevel);
            hasLoadedLevel = true;
        }

        // --- NEW: Initialize 3D Camera & Environment ---
        camera3d = new PerspectiveCamera(67, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        camera3d.near = 0.01f;
        camera3d.far = 100f;

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // Manual model loading and prop creation removed.
        // 3D models are now loaded via ItemDataManager and instanced in render().
    }

    @Override
    public void hide() {
        if (worldManager != null && maze != null && gameMode == GameMode.ADVANCED) {
            worldManager.saveCurrentChunk(maze);
        }

        if (potionManager != null && gameMode == GameMode.ADVANCED) {
            potionManager.saveState();
        }

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

    private void generateLevel(int levelNumber) {
        Gdx.app.log("GameScreen [DEBUG]", "generateLevel START. Player is " + (player == null ? "NULL" : "NOT NULL"));

        if (player == null) {
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

            Gdx.app.log("GameScreen [DEBUG]", "Calling getInitialMaze()...");
            this.maze = worldManager.getInitialMaze();

            Gdx.app.log("GameScreen [DEBUG]", "Creating NEW Player...");
            GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
            player = new Player(startPos.x, startPos.y, difficulty,
                game.getItemDataManager(), game.getAssetManager());
            player.getStatusManager().initialize(this.eventManager);

            Gdx.app.log("GameScreen [DEBUG]", "Setting maze reference on player.");
            player.setMaze(this.maze);

        } else {
            Gdx.app.log("GameScreen [DEBUG]", "Player exists. Calling getInitialMaze() for new level...");
            this.maze = worldManager.getInitialMaze();
            resetPlayerPosition();
            player.setMaze(this.maze);
        }

        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager, worldManager, game.getItemDataManager(), stochasticManager);
        hud = new Hud(game.getBatch(), player, maze, combatManager, eventManager, worldManager, game, debugManager, gameMode);
        hud.resize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);

        Gdx.app.log("GameScreen", "Level " + levelNumber + " loaded/generated.");

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

    /**
     * Checks if a 3D object at targetPos should be rendered.
     * It checks distance (fog) and Line of Sight (walls).
     */
    private boolean isVisible(Vector2 targetPos) {
        if (player == null || maze == null) return false;

        // 1. Fog/Distance Check (Simple distance from player)
        float dstToPlayer = player.getPosition().dst(targetPos);
        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());

        if (biome.hasFogOfWar()) {
            if (dstToPlayer > biome.getFogDistance()) return false;
        } else {
            if (dstToPlayer > 20) return false;
        }

        // 2. Wall Occlusion Check
        // We must calculate the ray start position EXACTLY as FirstPersonRenderer does.
        // The Raycaster shifts the "camera" backwards to avoid wall clipping.
        Vector2 renderPosition;
        int playerX = (int) player.getPosition().x;
        int playerY = (int) player.getPosition().y;
        boolean isBehindBlocked = maze.isWallBlocking(playerX, playerY, player.getFacing().getOpposite());

        if (isBehindBlocked) {
            renderPosition = player.getPosition().cpy();
        } else {
            renderPosition = player.getPosition().cpy().sub(player.getDirectionVector());
        }

        // hitDist: Distance from renderPosition to the first obstacle (wall OR the target itself)
        float hitDist = firstPersonRenderer.checkLineOfSight(player, maze, targetPos);

        // trueDist: Actual distance from renderPosition to the target
        float trueDist = renderPosition.dst(targetPos);

        // If the ray hit something significantly closer than the target, it's a wall.
        // We use a buffer (0.8f) because floating point math + object volume.
        if (hitDist < trueDist - 0.8f) {
            return false;
        }

        return true;
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
            fboViewport.apply();
            shapeRenderer.setProjectionMatrix(fboViewport.getCamera().combined);
        }

        ScreenUtils.clear(0, 0, 0, 1);
        Gdx.gl.glClear(Gdx.gl.GL_DEPTH_BUFFER_BIT);

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
            firstPersonRenderer.render(shapeRenderer, player, maze, currentViewport, worldManager, currentLevel, gameMode);

            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                entityRenderer.render(shapeRenderer, player, maze, currentViewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, worldManager);
            } else {
                entityRenderer.renderSingleMonster(shapeRenderer, player, combatManager.getMonster(), currentViewport, firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, maze, worldManager);
            }

            // --- NEW: RENDER 3D MODELS WITH OCCLUSION CULLING ---
            if (camera3d != null) {
                camera3d.position.set(player.getPosition().x, 0.5f, player.getPosition().y);
                camera3d.direction.set(player.getDirectionVector().x, 0, player.getDirectionVector().y);
                camera3d.up.set(0, 1, 0);
                camera3d.update();

                ModelBatch modelBatch = ((Tarmin2)game).getModelBatch();
                if (modelBatch != null) {
                    modelBatch.begin(camera3d);

                    // Render Data-Driven Items
                    if (maze != null) {
                        for (Item item : maze.getItems().values()) {
                            ItemTemplate template = item.getTemplate();

                            // Check if this item has a 3D model defined in JSON
                            if (template != null && template.modelPath != null) {

                                // Occlusion Culling
                                if (!isVisible(item.getPosition())) {
                                    continue;
                                }

                                // Retrieve or Create Instance
                                ModelInstance inst = item3dCache.get(item);
                                if (inst == null) {
                                    // Fetch the model from AssetManager using the path in the JSON
                                    Model model = game.getAssetManager().get(template.modelPath, Model.class);
                                    if (model != null) {
                                        inst = new ModelInstance(model);
                                        // Apply scale from JSON
                                        inst.transform.scale(template.modelScale, template.modelScale, template.modelScale);
                                        item3dCache.put(item, inst);
                                    }
                                }

                                if (inst != null) {
                                    // Update Position
                                    inst.transform.setTranslation(
                                        item.getPosition().x,
                                        template.modelYOffset, // Use Y offset from JSON
                                        item.getPosition().y
                                    );
                                    modelBatch.render(inst, environment);
                                }
                            }
                        }
                    }

                    modelBatch.end();
                }
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

        if (isShaking) {
            player.getDirectionVector().set(originalDir);
            player.getCameraPlane().set(originalPlane);
        }

        if (stochasticManager != null && combatManager.getCurrentState() == CombatManager.CombatState.PHYSICS_RESOLUTION) {
            stochasticManager.render();
        }

        if (hud != null) {
            hud.render();
        }

        if (stochasticManager != null) {
            CombatManager.CombatState state = combatManager.getCurrentState();
            if (state == CombatManager.CombatState.PHYSICS_RESOLUTION ||
                state == CombatManager.CombatState.PHYSICS_DELAY) {
                stochasticManager.render();
            }
        }

        game.getBatch().setProjectionMatrix(currentViewport.getCamera().combined);
        game.getBatch().begin();
        animationManager.renderDamageText(game.getBatch(), currentViewport);
        font.setColor(Color.WHITE);
        font.draw(game.getBatch(), "Level: " + currentLevel, 10, currentViewport.getWorldHeight() - 10);
        game.getBatch().end();

        if (useCrtFilter) {
            fbo.end();
            game.getViewport().apply();
            ScreenUtils.clear(0, 0, 0, 1);
            postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
            postProcessBatch.begin();
            postProcessBatch.setShader(crtShader);
            crtShader.setUniformf("u_time", time);
            Texture fboTexture = fbo.getColorBufferTexture();
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
        game.getViewport().update(width, height, true);
        postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
        if (hud != null) {
            hud.resize(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        }
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
