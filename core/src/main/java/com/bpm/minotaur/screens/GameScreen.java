package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;

import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.*;

import com.bpm.minotaur.rendering.*;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.gamedata.spawntables.WeightedRandomList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;

public class GameScreen extends BaseScreen {

    // --- Core Dependencies ---
    private final DebugManager debugManager = DebugManager.getInstance();
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private StochasticManager stochasticManager;
    private final BitmapFont font = new BitmapFont();
    private final GameMode gameMode;

    private boolean needsAsciiRender = false;

    private final WorldManager worldManager;

    // --- Renderers ---
    private final DebugRenderer debugRenderer = new DebugRenderer();
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final EntityRenderer entityRenderer = new EntityRenderer(game.getItemDataManager(), game.getAssetManager());
    private final Difficulty difficulty;

    // --- 3D Rendering Components ---
    private PerspectiveCamera camera3d;
    private Environment environment;
    private final Map<Item, ModelInstance> item3dCache = new HashMap<>();

    private Hud hud;
    private AnimationManager animationManager;
    private GameEventManager eventManager;
    private SoundManager soundManager;

    // --- Game State ---
    private Player player;
    private Maze maze;
    private int currentLevel;
    private CombatManager combatManager;
    private CombatDiceOverlay combatDiceOverlay; // NEW
    private MonsterAiManager monsterAiManager;
    private DiscoveryManager discoveryManager;

    private FrameBuffer fbo;
    private ShaderProgram crtShader;
    private boolean useCrtFilter = true;
    private final SpriteBatch postProcessBatch = new SpriteBatch();
    private float time = 0f;

    private final Viewport fboViewport;
    private static final int VIRTUAL_WIDTH = 1920;
    private static final int VIRTUAL_HEIGHT = 1080;

    private float trauma = 0f;
    private final Vector2 originalDir = new Vector2();
    private final Vector2 originalPlane = new Vector2();
    private final java.util.Random rng = new java.util.Random();

    private boolean hasLoadedLevel = false;
    private int turnCount = 0;

    private final TurnManager turnManager; // NEW

    // --- NEW: Visceral Feedback Components ---
    private FirstPersonWeaponOverlay weaponOverlay;
    private float hitPauseTimer = 0f;

    // --- Debug UI ---
    private DebugSpawnOverlay debugSpawnOverlay;
    private com.badlogic.gdx.InputMultiplexer inputMultiplexer;

    public GameScreen(Tarmin2 game, int level, Difficulty difficulty, GameMode gameMode) {
        super(game);
        this.difficulty = difficulty;
        this.gameMode = gameMode;
        this.currentLevel = level;
        this.stochasticManager = new StochasticManager();

        this.fboViewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        this.fboViewport.update(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);
        this.soundManager = new SoundManager(debugManager);
        this.turnManager = new TurnManager(); // NEW

        this.worldManager = new WorldManager(gameMode, difficulty, level,
                game.getMonsterDataManager(),
                game.getItemDataManager(),
                game.getAssetManager(),
                game.getEncounterManager(),
                game.getSpawnTableData(),
                this.soundManager);

        this.monsterAiManager = new MonsterAiManager();

        switch (level) {
            case 1:
                MusicManager.getInstance().playTrack("sounds/music/tarmin_maze.mp3");
                break;
            default:
                MusicManager.getInstance().playTrack("sounds/music/tarmin_fuxx.ogg");
                break;
        }

        // Initialize Input Multiplexer
        inputMultiplexer = new com.badlogic.gdx.InputMultiplexer();

        // --- NEW: Weapon Overlay ---
        this.weaponOverlay = new FirstPersonWeaponOverlay(game.getItemDataManager(), game.getAssetManager());
    }

    @Override
    public void show() {

        // Gdx.input.setInputProcessor(this); // Handled below via Multiplexer

        if (animationManager == null) {
            animationManager = new AnimationManager(entityRenderer);
        }
        if (eventManager == null) {
            eventManager = new GameEventManager();
        }

        if (fbo == null) {
            fbo = new FrameBuffer(Pixmap.Format.RGB888, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, true);
        }

        if (crtShader == null) {
            ShaderProgram.pedantic = false;
            crtShader = new ShaderProgram(Gdx.files.internal("shaders/crt.vert"),
                    Gdx.files.internal("shaders/crt.frag"));
            if (!crtShader.isCompiled()) {
                Gdx.app.error("Shader", "Compilation failed:\n" + crtShader.getLog());
                useCrtFilter = false;
            }
        }

        if (!hasLoadedLevel) {
            generateLevel(currentLevel);
            hasLoadedLevel = true;
        }

        camera3d = new PerspectiveCamera(67, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        camera3d.near = 0.01f;
        camera3d.far = 100f;

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // --- Init Debug Overlay (Ensure it exists and is added to HUD) ---
        if (hud != null) {
            if (debugSpawnOverlay == null) {
                debugSpawnOverlay = new DebugSpawnOverlay(this, null);
                debugSpawnOverlay.setVisible(false); // Hidden by default
                debugSpawnOverlay.setPosition(VIRTUAL_WIDTH / 2f - 150, VIRTUAL_HEIGHT / 2f);
            }
            // Ensure actor is in stage (idempotent or check logic could be added, but safe
            // to add if not present)
            // Scene2D actors can only have one parent, so adding it again just moves it or
            // does nothing if same.
            // But we should be careful.
            if (debugSpawnOverlay.getStage() == null) {
                hud.stage.addActor(debugSpawnOverlay);
            }
        }

        // --- Setup Input Multiplexer (CRITICAL for resuming from Inventory) ---
        if (hud != null) {
            inputMultiplexer.clear();
            inputMultiplexer.addProcessor(hud.stage); // UI First
            inputMultiplexer.addProcessor(this); // Game Second
            Gdx.input.setInputProcessor(inputMultiplexer);
        }
    }

    public GameEventManager getEventManager() {
        return eventManager;
    }

    @Override
    public void hide() {
        if (worldManager != null && maze != null && gameMode == GameMode.ADVANCED) {
            worldManager.saveCurrentChunk(maze);
        }
        if (discoveryManager != null && gameMode == GameMode.ADVANCED) {
            discoveryManager.saveState();
        }
        // MusicManager.getInstance().stop(); // Don't stop music when switching screens
        // (like to Inventory)
    }

    private void generateLevel(int levelNumber) {
        Gdx.app.log("GameScreen [DEBUG]", "generateLevel START. Player is " + (player == null ? "NULL" : "NOT NULL"));

        if (player == null) {
            if (this.discoveryManager == null) {
                this.discoveryManager = new DiscoveryManager(this.eventManager);
                game.getItemDataManager().setDiscoveryManager(this.discoveryManager);

                if (this.discoveryManager.hasSaveState()) {
                    this.discoveryManager.loadState();
                } else {
                    List<com.bpm.minotaur.gamedata.item.Item.ItemType> potionTypes = game.getItemDataManager()
                            .getAllPotionAppearanceTypes();
                    this.discoveryManager.initializeNewGame(potionTypes);
                }
            }
            this.maze = worldManager.getInitialMaze();
            GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
            player = new Player(startPos.x, startPos.y, difficulty,
                    game.getItemDataManager(), game.getMonsterDataManager(), game.getAssetManager());
            player.getStatusManager().initialize(this.eventManager);
            player.setMaze(this.maze);
        } else {
            this.maze = worldManager.getInitialMaze();
            resetPlayerPosition();
            player.setMaze(this.maze);
        }

        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager,
                game.getItemDataManager(), stochasticManager, turnManager, monsterAiManager);

        // --- LOAD BLOOD ASSETS (Packed) ---
        com.badlogic.gdx.assets.AssetManager am = game.getAssetManager();
        String goreAtlasPath = "packed/gore.atlas";

        if (!am.isLoaded(goreAtlasPath)) {
            am.load(goreAtlasPath, com.badlogic.gdx.graphics.g2d.TextureAtlas.class);
            am.finishLoading();
        }

        com.badlogic.gdx.graphics.g2d.TextureAtlas goreAtlas = am.get(goreAtlasPath,
                com.badlogic.gdx.graphics.g2d.TextureAtlas.class);

        com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.g2d.TextureRegion> dropRegs = new com.badlogic.gdx.utils.Array<>();
        for (int i = 1; i <= 4; i++)
            dropRegs.add(goreAtlas.findRegion("blood_drop" + i));

        com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.g2d.TextureRegion> smearRegs = new com.badlogic.gdx.utils.Array<>();
        for (int i = 1; i <= 3; i++)
            smearRegs.add(goreAtlas.findRegion("blood_smear" + i));

        com.badlogic.gdx.graphics.g2d.TextureRegion spatterReg = goreAtlas.findRegion("blood_spatter");

        com.badlogic.gdx.utils.Array<com.badlogic.gdx.graphics.g2d.TextureRegion> gibRegs = new com.badlogic.gdx.utils.Array<>();
        for (int i = 1; i <= 10; i++)
            gibRegs.add(goreAtlas.findRegion("gib" + i));

        maze.getGoreManager().setTextures(dropRegs, smearRegs, spatterReg, gibRegs);

        // --- DICE UI INTEGRATION ---
        this.combatDiceOverlay = new CombatDiceOverlay(player, combatManager, game.getViewport());
        // ---------------------------

        hud = new Hud(game.getBatch(), player, maze, combatManager, eventManager, worldManager, game, debugManager,
                gameMode);
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        if (this.gameMode == GameMode.ADVANCED && levelNumber == 1) {
            firstPersonRenderer.setTheme(RetroTheme.ADVANCED_COLOR_THEME_BLUE);
        } else {
            firstPersonRenderer.setTheme(RetroTheme.STANDARD_THEME);
        }
        DebugRenderer.printMazeToConsole(maze);

        // Input setup moved to show()
    }

    private void resetPlayerPosition() {
        GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
        player.getPosition().set(startPos.x + 0.5f, startPos.y + 0.5f);
    }

    private boolean isVisible(Vector2 targetPos) {
        if (player == null || maze == null)
            return false;
        float dstToPlayer = player.getPosition().dst(targetPos);
        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());

        if (biome.hasFogOfWar()) {
            if (dstToPlayer > biome.getFogDistance())
                return false;
        } else {
            if (dstToPlayer > 20)
                return false;
        }

        Vector2 renderPosition;
        int playerX = (int) player.getPosition().x;
        int playerY = (int) player.getPosition().y;
        boolean isBehindBlocked = maze.isWallBlocking(playerX, playerY, player.getFacing().getOpposite());

        if (isBehindBlocked) {
            renderPosition = player.getPosition().cpy();
        } else {
            renderPosition = player.getPosition().cpy().sub(player.getDirectionVector());
        }

        float hitDist = firstPersonRenderer.checkLineOfSight(player, maze, targetPos);
        float trueDist = renderPosition.dst(targetPos);
        if (hitDist < trueDist - 0.8f) {
            return false;
        }
        return true;
    }

    @Override
    public void render(float delta) {
        // --- VISCERAL HIT PAUSE ---
        if (hitPauseTimer > 0) {
            hitPauseTimer -= delta;
            // Freeze game logic during hit pause, but keep rendering the static frame (and
            // shake!)
            // We do NOT update time, combatManager, etc.
            if (hitPauseTimer <= 0)
                hitPauseTimer = 0;
        } else {
            // Normal Update Loop
            time += delta;
            combatManager.update(delta);
            if (combatDiceOverlay != null)
                combatDiceOverlay.update(delta);
            animationManager.update(delta);
            if (maze != null)
                maze.update(delta);
            if (hud != null)
                hud.update(delta);
            eventManager.update(delta);
            handleSystemEvents();

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

            // Update Overlay Animation
            weaponOverlay.update(delta);
        }

        if (useCrtFilter) {
            fbo.begin();
            fboViewport.apply();
            shapeRenderer.setProjectionMatrix(fboViewport.getCamera().combined);
        }

        ScreenUtils.clear(0, 0, 0, 1);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_DEPTH_BUFFER_BIT);
        Viewport currentViewport = useCrtFilter ? fboViewport : game.getViewport();

        boolean isShaking = (player != null && trauma > 0.01f);
        if (isShaking) {
            originalDir.set(player.getDirectionVector());
            originalPlane.set(player.getCameraPlane());
            float shakePower = trauma * 0.1f;
            float angle = (rng.nextFloat() - 0.5f) * shakePower;
            player.getDirectionVector().rotateRad(angle);
            player.getCameraPlane().rotateRad(angle);
        }

        boolean isBlind = (player != null
                && player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.BLIND));

        if (isBlind) {
            game.getBatch().begin();
            // Blindness rendering (black screen)
            game.getBatch().end();
        } else if (player != null && maze != null) {
            firstPersonRenderer.render(shapeRenderer, player, maze, currentViewport, worldManager, currentLevel,
                    gameMode);

            // --- 3D RENDER FIX: NEGATE Z COORDINATES ---
            // DISABLED FOR NOW: Reverting to 2D Sprites/Textures for all items as per user
            // request.
            /*
             * if (camera3d != null) {
             * // Negate Y here to convert to standard 3D forward (-Z)
             * camera3d.position.set(player.getPosition().x, 0.5f, -player.getPosition().y);
             * camera3d.direction.set(player.getDirectionVector().x, 0,
             * -player.getDirectionVector().y);
             * camera3d.up.set(0, 1, 0);
             * camera3d.update();
             * 
             * ModelBatch modelBatch = game.getModelBatch();
             * boolean batchBegun = false;
             * 
             * if (modelBatch != null && maze != null) {
             * for (Item item : maze.getItems().values()) {
             * ItemTemplate template = item.getTemplate();
             * if (template != null && template.modelPath != null) {
             * if (!isVisible(item.getPosition()))
             * continue;
             * ModelInstance inst = item3dCache.get(item);
             * if (inst == null) {
             * Model model = game.getAssetManager().get(template.modelPath, Model.class);
             * if (model != null) {
             * inst = new ModelInstance(model);
             * item3dCache.put(item, inst);
             * }
             * }
             * if (inst != null) {
             * if (!batchBegun) {
             * modelBatch.begin(camera3d);
             * batchBegun = true;
             * }
             * inst.transform.idt();
             * // Negate Y here as well for item position
             * inst.transform.translate(
             * item.getPosition().x,
             * template.modelYOffset,
             * -item.getPosition().y);
             * 
             * if (template.modelRotation != 0f) {
             * inst.transform.rotate(Vector3.Y, template.modelRotation);
             * }
             * inst.transform.scale(template.modelScale, template.modelScale,
             * template.modelScale);
             * modelBatch.render(inst, environment);
             * }
             * }
             * }
             * if (batchBegun)
             * modelBatch.end();
             * }
             * }
             */

            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                entityRenderer.render(shapeRenderer, player, maze, currentViewport,
                        firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, worldManager);
            } else {
                entityRenderer.renderSingleMonster(shapeRenderer, player, combatManager.getMonster(), currentViewport,
                        firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, maze, worldManager);
            }

            // 3D Rendering Moved Above EntityRenderer

            // 3D Rendering Moved Above EntityRenderer

            animationManager.render(shapeRenderer, player, currentViewport, firstPersonRenderer.getDepthBuffer(),
                    firstPersonRenderer, maze);

            // --- VISCERAL: Weapon Overlay ---
            // Render 2D weapon swipe on top of 3D world but before HUD/PostProcess?
            // Actually best to do it before CRT so it gets filtered.
            // We need a SpriteBatch for this. Game has one.

            // Use UI viewport (game.getViewport()) for overlay, NOT world viewport
            // (currentViewport)
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);

            if (weaponOverlay.isActive()) {
                game.getBatch().begin();
                weaponOverlay.render(game.getBatch(), game.getViewport());
                game.getBatch().end();
            }

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

        if (stochasticManager != null) {
            CombatManager.CombatState state = combatManager.getCurrentState();
            if (state == CombatManager.CombatState.PHYSICS_RESOLUTION ||
                    state == CombatManager.CombatState.PHYSICS_DELAY) {
                stochasticManager.render();
            }
        }

        // Moved CombatDiceOverlay to end of frame

        if (useCrtFilter) {
            fbo.end();
            game.getViewport().apply();
            ScreenUtils.clear(0, 0, 0, 1);
            postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
            postProcessBatch.begin();
            postProcessBatch.setShader(crtShader);
            crtShader.setUniformf("u_time", time);
            postProcessBatch.draw(fbo.getColorBufferTexture(), 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, 0, 0, 1, 1);
            postProcessBatch.end();
            // --- CRT FIX: Reset view when CRT is off ---
            game.getViewport().apply();
        }

        if (hud != null) {
            // --- NEW: Sync Combat Menu Visibility ---
            if (combatManager != null && hud.combatMenu != null) {
                hud.combatMenu.setVisible(combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU);
            }
            hud.render();
        }

        game.getBatch().setProjectionMatrix(currentViewport.getCamera().combined);
        game.getBatch().begin();
        animationManager.renderDamageText(game.getBatch(), currentViewport);
        font.setColor(Color.WHITE);
        font.draw(game.getBatch(), "Level: " + currentLevel, 10, currentViewport.getWorldHeight() - 10);

        game.getBatch().end();

        renderCombatOverlay();
    }

    private void renderCombatOverlay() {
        // Log errors but don't spam trace logs
        if (combatDiceOverlay == null)
            return;

        try {
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            combatDiceOverlay.render(game.getBatch());
            game.getBatch().end();
        } catch (Exception e) {
            com.bpm.minotaur.managers.BalanceLogger.getInstance().log("UI_ERROR",
                    "Crash in CombatDiceOverlay: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkForProactiveChunkLoading() {
        if (player == null || worldManager == null || maze == null)
            return;
        Biome biome = worldManager.getBiomeManager().getBiome(worldManager.getCurrentPlayerChunkId());
        if (!biome.isSeamless())
            return;

        int triggerDistance = biome.hasFogOfWar() ? biome.getFogDistance() + 1 : 5;
        triggerDistance = Math.max(2, triggerDistance);

        GridPoint2 playerPos = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        GridPoint2 currentChunkId = worldManager.getCurrentPlayerChunkId();
        int height = maze.getHeight();
        int width = maze.getWidth();

        if (playerPos.y >= height - 1 - triggerDistance)
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x, currentChunkId.y + 1));
        if (playerPos.y <= triggerDistance)
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x, currentChunkId.y - 1));
        if (playerPos.x >= width - 1 - triggerDistance)
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x + 1, currentChunkId.y));
        if (playerPos.x <= triggerDistance)
            worldManager.requestLoadChunk(new GridPoint2(currentChunkId.x - 1, currentChunkId.y));
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

        while ((event = eventManager.findAndConsume(GameEvent.EventType.ENCOUNTER_TRIGGERED)) != null) {
            String eventId = (String) event.payload;
            if (game.getEncounterManager() != null) {
                com.bpm.minotaur.gamedata.encounters.Encounter encounter = game.getEncounterManager()
                        .getEncounter(eventId);
                if (encounter != null && hud != null && hud.getEncounterWindow() != null) {
                    hud.getEncounterWindow().configure(player, game.getEncounterManager(), eventManager,
                            game.getItemDataManager(), game.getMonsterDataManager(), game.getAssetManager(),
                            maze, null);
                    hud.getEncounterWindow().show(encounter);
                }
            }
        }

        // --- NEW: Portal Reset Handling ---
        while ((event = eventManager.findAndConsume(GameEvent.EventType.PORTAL_ACTIVATED)) != null) {
            Gdx.app.log("GameScreen", "PORTAL_ACTIVATED detected. Resetting world.");

            // 1. Reset World Data
            worldManager.resetWorldKeepDifficulty();
            this.currentLevel = worldManager.getCurrentLevel(); // Should be 1

            // 2. Clear Rendering State
            worldManager.clearLoadedChunks();

            // 3. Regenerate Level 1 (Simulate new game start)
            generateLevel(this.currentLevel);

            // 4. Feedback
            hud.addMessage("The world shifts...");
            hud.addMessage("You are back at the start, but stronger.");
        }
    }

    private void swapToChunk(Maze newMaze) {
        this.maze = newMaze;
        player.setMaze(newMaze);
        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager,
                game.getItemDataManager(), stochasticManager, turnManager, monsterAiManager);
        // --- FIX: Re-initialize Dice UI with new Manager ---
        this.combatDiceOverlay = new CombatDiceOverlay(player, combatManager, game.getViewport());
        // ---------------------------------------------------
        hud = new Hud(game.getBatch(), player, maze, combatManager, eventManager, worldManager, game, debugManager,
                gameMode);
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        combatManager.setHud(hud);
        DebugRenderer.printMazeToConsole(maze);
    }

    private void playerTurnTakesAction() {
        processPlayerStatusEffects();
        player.getStatusManager().updateTurn();
        if (monsterAiManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            turnManager.processTurn(maze, player, monsterAiManager, combatManager);
        }
        combatManager.checkForAdjacentMonsters();

        // --- Periodic Spawning Hook ---
        turnCount++;
        worldManager.processTurn(player, turnCount);
    }

    private void performChunkTransition(Gate transitionGate) {
        if (player == null)
            return;
        if (maze != null)
            worldManager.saveCurrentChunk(this.maze);
        Maze newMaze = worldManager.loadChunk(transitionGate.getTargetChunkId());
        if (newMaze == null) {
            eventManager.addEvent(new GameEvent("A strange force blocks your path.", 2f));
            transitionGate.close();
            return;
        }
        player.getPosition().set(transitionGate.getTargetPlayerPos().x + 0.5f,
                transitionGate.getTargetPlayerPos().y + 0.5f);
        worldManager.setCurrentChunk(transitionGate.getTargetChunkId());
        swapToChunk(newMaze);
    }

    @Override
    public void resize(int width, int height) {
        game.getViewport().update(width, height, true);
        postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
        if (hud != null) {
            // Fix: Always resize HUD to window size for correct input mapping
            hud.resize(width, height);
        }
    }

    private void processPlayerStatusEffects() {
        if (player == null)
            return;
        if (player.getStatusManager().hasEffect(StatusEffectType.POISONED)) {
            ActiveStatusEffect poison = player.getStatusManager().getEffect(StatusEffectType.POISONED);
            int damage = poison.getPotency();
            player.takeStatusEffectDamage(damage, DamageType.POISON);
            eventManager.addEvent(new GameEvent("You take " + damage + " poison damage!", 2f));
        }
    }

    // Helper to spawn one of each armor type (Debug)
    private void giveAllArmor() {
        Item.ItemType[] armorTypes = {
                Item.ItemType.HELMET, // Head (Generic)
                Item.ItemType.LEATHER_HELM, // Head (Specific Bug Test)
                Item.ItemType.HAUBERK, // Chest (Heavy)
                Item.ItemType.LEATHER_ARMOR, // Chest (Light)
                Item.ItemType.LEGS, // Legs
                Item.ItemType.BOOTS, // Feet
                Item.ItemType.GAUNTLETS, // Hands
                Item.ItemType.SMALL_SHIELD, // Left Hand
                Item.ItemType.CLOAK, // Back
                Item.ItemType.AMULET, // Neck
                Item.ItemType.RING_GOLD, // Ring
                Item.ItemType.EYES // Eyes (if available) - ItemType.EYES exists in enum
        };

        for (Item.ItemType type : armorTypes) {
            try {
                Item item = game.getItemDataManager().createItem(type, (int) player.getPosition().x,
                        (int) player.getPosition().y, com.bpm.minotaur.gamedata.item.ItemColor.WHITE,
                        game.getAssetManager());
                if (item != null) {
                    if (!player.getInventory().pickupToBackpack(item)) {
                        hud.addMessage("Inventory Full! Could not add " + type);
                    }
                } else {
                    Gdx.app.log("Debug", "Failed to create item: " + type);
                }
            } catch (Exception e) {
                Gdx.app.error("Debug", "Error creating debug armor: " + type, e);
            }
        }
        hud.addMessage("Debug: Spawned Armor Set");
    }

    public void spawnDebugItem(Item.ItemType type) {
        if (player == null || maze == null)
            return;

        // Spawn 1 tile in front
        Vector2 dir = player.getDirectionVector();
        int tx = (int) (player.getPosition().x + dir.x);
        int ty = (int) (player.getPosition().y + dir.y);

        if (maze.isPassable(tx, ty)) {
            try {
                // Create default variant for debug
                Item item = game.getItemDataManager().createItem(type, tx, ty,
                        com.bpm.minotaur.gamedata.item.ItemColor.WHITE, game.getAssetManager());
                maze.addItem(item);
                hud.addMessage("Spawned: " + type.name());
            } catch (Exception e) {
                Gdx.app.error("Debug", "Failed to spawn item: " + type, e);
            }
        } else {
            hud.addMessage("Cannot spawn here (Blocked)");
        }
    }

    public void spawnDebugMonster(com.bpm.minotaur.gamedata.monster.Monster.MonsterType type) {
        if (player == null || maze == null)
            return;

        // Spawn 1 tile in front
        Vector2 dir = player.getDirectionVector();
        int tx = (int) (player.getPosition().x + dir.x);
        int ty = (int) (player.getPosition().y + dir.y);

        if (maze.isPassable(tx, ty) && !maze.getMonsters().containsKey(new GridPoint2(tx, ty))) {
            try {
                com.bpm.minotaur.gamedata.monster.Monster monster = new com.bpm.minotaur.gamedata.monster.Monster(type,
                        tx, ty, com.bpm.minotaur.gamedata.monster.MonsterColor.WHITE, game.getMonsterDataManager(),
                        game.getAssetManager());
                monster.scaleStats(currentLevel); // Scale to current level just in case
                maze.addMonster(monster);
                hud.addMessage("Spawned: " + type.name());
            } catch (Exception e) {
                Gdx.app.error("Debug", "Failed to spawn monster: " + type, e);
            }
        } else {
            hud.addMessage("Cannot spawn here (Blocked or Occupied)");
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (worldManager == null || player == null || maze == null)
            return false;

        if (hud != null && hud.getEncounterWindow() != null && hud.getEncounterWindow().isVisible()) {
            return true;
        }

        // --- NEW: Combat Menu Input Interception ---
        if (combatManager != null && combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU) {
            if (hud != null && hud.combatMenu != null) {
                switch (keycode) {
                    case Input.Keys.I:
                        InventoryScreen invScreen = new InventoryScreen(game, this, player, maze,
                                InventoryScreen.InventoryMode.NORMAL);
                        game.setScreen(invScreen);
                        return true;
                    case Input.Keys.UP:
                        hud.combatMenu.navigateUp();
                        return true;
                    case Input.Keys.DOWN:
                        hud.combatMenu.navigateDown();
                        return true;
                    case Input.Keys.ENTER:
                    case Input.Keys.SPACE:
                    case Input.Keys.A: // Binding 'A' to Attack for convenience
                        int selection = hud.combatMenu.getSelectedIndex();
                        // If 'A' pressed, assume Attack regardless of menu unless we want strict nav
                        if (keycode == Input.Keys.A)
                            selection = 0;

                        switch (selection) {
                            case 0: // ATTACK
                                // USER FEEDBACK: "Attack" should be instant (Standard Weapon Attack)
                                combatManager.playerAttackInstant();
                                break;
                            case 1: // CAST
                                combatManager.playerCast();
                                break;
                            case 2: // ROLL
                                // USER FEEDBACK: "Roll" mapped to Dice Mechanics / Skill check
                                combatManager.playerAttackWithDice();
                                break;
                            case 3: // USE
                                hud.addMessage("Combat Items not yet implemented.");
                                break;
                            case 4: // BLOCK
                                combatManager.playerGuard();
                                break;
                        }
                        return true;
                }
            }
            // Block all other input during menu (except maybe Debug keys?)
            // We'll allow F-keys to fall through by not returning true for default?
            // Actually, let's just return true for "handled" or keys we want to block
            // (WASD, SPACE).
            // But checking every key is annoying.
            // Better to return true for "handled" or keys we want to block (WASD, SPACE).
            // For now, let's just return true for everything except F-keys?
            // Hard to filter easily. Let's just block the main ones if we didn't handle
            // navigation.
            if (keycode == Input.Keys.W || keycode == Input.Keys.A || keycode == Input.Keys.S || keycode == Input.Keys.D
                    ||
                    keycode == Input.Keys.UP || keycode == Input.Keys.LEFT || keycode == Input.Keys.DOWN
                    || keycode == Input.Keys.RIGHT ||
                    keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) {
                return true;
            }
        }

        // --- Debug Shortcuts ---
        if (debugManager.isDebugOverlayVisible()) {
            if (keycode == Input.Keys.A && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))) {
                giveAllArmor();
                return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE
                || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN
                || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU) {
            switch (keycode) {
                case Input.Keys.S:
                    player.getInventory().swapHands();
                    // If in Menu, we don't pass turn, just update UI
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN)
                        combatManager.passTurnToMonster();
                    return true;
                case Input.Keys.E:
                    player.getInventory().swapWithPack();
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN)
                        combatManager.passTurnToMonster();
                    return true;
                case Input.Keys.T:
                    player.getInventory().rotatePack();
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN)
                        combatManager.passTurnToMonster();
                    return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            // --- NEW: A/SPACE = Instant Attack ---
            if (keycode == Input.Keys.A || keycode == Input.Keys.SPACE) {
                combatManager.playerAttackInstant();
                return true;
            }
            // --- NEW: NUM_7 = Dice Roll Attack ---
            if (keycode == Input.Keys.NUM_7) {
                combatManager.playerAttackWithDice();
                return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {

            // --- CONFUSION LOGIC ---
            if (player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.CONFUSED)) {
                if (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN ||
                        keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT) {

                    // Simple random walk
                    int[] dirs = { Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT };
                    keycode = dirs[com.badlogic.gdx.math.MathUtils.random(dirs.length - 1)];

                    // Optional: Add a "Stumble" message or sound?
                    // Let's keep it subtle for now, just the erratic movement.
                }
            }
            // -----------------------

            if (keycode == Input.Keys.A || keycode == Input.Keys.SPACE) {
                if (player.getInventory().getRightHand() != null && player.getInventory().getRightHand().isRanged()) {
                    boolean attacked = combatManager.performRangedAttack();
                    if (attacked)
                        playerTurnTakesAction();
                    return true;
                }
            }

            switch (keycode) {
                case Input.Keys.PERIOD:
                    hud.addMessage("You wait...");
                    playerTurnTakesAction();
                    return true;
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
                    // Check for Crafting Bench
                    Vector2 v = player.getFacing().getVector();
                    GridPoint2 target = new GridPoint2(
                            (int) (player.getPosition().x + v.x),
                            (int) (player.getPosition().y + v.y));

                    Item itemInFront = maze.getItems().get(target);

                    if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_CRAFTING_BENCH) {
                        try {
                            OssuaryManager oMgr = new OssuaryManager(); // Create dynamically for now, or fetch from
                                                                        // game
                            OssuaryScreen ossuaryScreen = new OssuaryScreen(game, this, player, oMgr);
                            game.setScreen(ossuaryScreen);
                            return true;
                        } catch (Exception e) {
                            Gdx.app.error("GameScreen", "Failed to open Ossuary", e);
                        }
                    }

                    if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_FIRE_POT) {
                        InventoryScreen invScreen = new InventoryScreen(game, this, player, maze,
                                InventoryScreen.InventoryMode.COOK);
                        game.setScreen(invScreen);
                        return true;
                    }

                    player.interact(maze, eventManager, soundManager, gameMode, worldManager);
                    playerTurnTakesAction();
                    needsAsciiRender = true;
                    return true;
                case Input.Keys.P:
                    player.interactWithItem(maze, eventManager, soundManager);
                    playerTurnTakesAction();
                    return true;
                case Input.Keys.U:
                    player.useItem(player.getInventory().getRightHand(), eventManager, this.discoveryManager, maze);
                    playerTurnTakesAction();
                    return true;
                case Input.Keys.I:
                    InventoryScreen invScreen = new InventoryScreen(game, this, player, maze,
                            InventoryScreen.InventoryMode.NORMAL);
                    game.setScreen(invScreen);
                    return true;
                case Input.Keys.D:
                    GridPoint2 atFeet = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
                    GridPoint2 inFront = new GridPoint2(
                            (int) (player.getPosition().x + player.getFacing().getVector().x),
                            (int) (player.getPosition().y + player.getFacing().getVector().y));
                    Ladder ladder = maze.getLadders().get(atFeet);
                    if (ladder == null)
                        ladder = maze.getLadders().get(inFront);

                    if (ladder != null) {
                        // soundManager.playSound("level_up");
                        if (ladder.getType() == Ladder.LadderType.DOWN) {
                            GridPoint2 ladderPos = new GridPoint2((int) ladder.getPosition().x,
                                    (int) ladder.getPosition().y);
                            worldManager.descendLevel(ladderPos);
                            this.currentLevel = worldManager.getCurrentLevel();
                            worldManager.clearLoadedChunks();
                            generateLevel(this.currentLevel);
                            hud.addMessage("Descended to Level " + currentLevel);
                        } else {
                            boolean success = worldManager.ascendLevel();
                            if (success) {
                                this.currentLevel = worldManager.getCurrentLevel();
                                worldManager.clearLoadedChunks();
                                generateLevel(this.currentLevel);
                                Vector2 foundDownLadderPos = null;
                                for (Ladder l : maze.getLadders().values()) {
                                    if (l.getType() == Ladder.LadderType.DOWN) {
                                        foundDownLadderPos = l.getPosition();
                                        break;
                                    }
                                }
                                if (foundDownLadderPos != null) {
                                    player.setPosition(
                                            new GridPoint2((int) foundDownLadderPos.x, (int) foundDownLadderPos.y));
                                }
                                hud.addMessage("Ascended to Level " + currentLevel);
                            } else {
                                hud.addMessage("You cannot ascend any higher.");
                            }
                        }
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

        switch (keycode)

        {
            case Input.Keys.F1:
                debugManager.toggleOverlay();
                return true;
            case Input.Keys.F2:
                debugManager.toggleRenderMode();
                return true;
            case Input.Keys.F3:
                SpawnManager.DEBUG_FORCE_MODIFIERS = !SpawnManager.DEBUG_FORCE_MODIFIERS;
                eventManager.addEvent(new GameEvent(
                        "Debug Force Modifiers: " + (SpawnManager.DEBUG_FORCE_MODIFIERS ? "ON" : "OFF"), 2f));
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
                    eventManager.addEvent(new GameEvent(
                            "Debug Weather: " + worldManager.getWeatherManager().getCurrentWeather(), 2f));
                }
                return true;
            case Input.Keys.F8:
                Gdx.app.log("GameScreen", "--- PREDICTING PORTALS ---");
                eventManager.addEvent(new GameEvent("Predicting Portals (Check Log)", 2f));

                SpawnTableData data = game.getSpawnTableData();
                GridPoint2 chunkId = worldManager.getCurrentPlayerChunkId();
                StringBuilder found = new StringBuilder();

                for (int i = 1; i <= 10; i++) {
                    int targetLevel = currentLevel + i;
                    long seed = worldManager.getChunkSeed(targetLevel, chunkId.x, chunkId.y);
                    WeightedRandomList<SpawnTableEntry> pool = SpawnManager.buildDebrisPool(data, targetLevel);

                    // Use exact budget from data
                    int heuristicBudget = SpawnManager.getDebrisBudget(data, targetLevel);

                    // Derive the same seed as MazeChunkGenerator uses
                    long spawnSeed = seed ^ 0xDEADBEEF12345678L;

                    boolean hasPortal = SpawnManager.predictPortalSpawn(spawnSeed, heuristicBudget, pool);
                    if (hasPortal) {
                        Gdx.app.log("GameScreen", "FOUND PORTAL at Level " + targetLevel);
                        found.append("L").append(targetLevel).append(" ");
                    }
                }

                if (found.length() > 0) {
                    eventManager.addEvent(new GameEvent("Portals nearby: " + found.toString(), 5f));
                } else {
                    eventManager.addEvent(new GameEvent("No portals in next 10 levels.", 3f));
                }
                return true;
            case Input.Keys.F9:
                Gdx.app.log("GameScreen", "Dumping Exploration Memory to Console...");
                DebugRenderer.printExplorationToConsole(maze);
                eventManager.addEvent(new GameEvent("Exploration Dumped to Console", 2f));
                return true;
            case Input.Keys.F12:
                if (debugSpawnOverlay != null) {
                    boolean isVisible = !debugSpawnOverlay.isVisible();
                    debugSpawnOverlay.setVisible(isVisible); // Toggle
                }
                return true;
        }

        return false;
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

    public void handleInventorySelection(com.bpm.minotaur.gamedata.item.Item item, InventoryScreen.InventoryMode mode) {
        if (item == null)
            return;

        switch (mode) {
            case QUAFF:
                if (item.isPotion()) {
                    player.quaff(item, discoveryManager, eventManager);
                    playerTurnTakesAction();
                } else {
                    eventManager.addEvent(new GameEvent("You cannot quaff that!", 1.5f));
                }
                break;
            case READ:
                if (item.getType().name().startsWith("SCROLL")) {
                    player.read(item, discoveryManager, eventManager, maze);
                    playerTurnTakesAction();
                } else {
                    eventManager.addEvent(new GameEvent("You cannot read that!", 1.5f));
                }
                break;
            case ZAP:
                if (item.getType().name().startsWith("WAND")) {
                    player.zap(item, player.getFacing(), discoveryManager, eventManager, maze);
                    playerTurnTakesAction();
                } else {
                    eventManager.addEvent(new GameEvent("You cannot zap that!", 1.5f));
                }
                break;
            case WIELD:
                // Standard Wield
                player.wield(item, eventManager);
                playerTurnTakesAction();
                break;
            case WEAR:
                if (item.isArmor() || item.isRing()) {
                    player.wear(item, eventManager);
                    playerTurnTakesAction();
                } else {
                    eventManager.addEvent(new GameEvent("You cannot wear that!", 1.5f));
                }
                break;
            case TAKEOFF:
                player.takeOff(item, eventManager);
                playerTurnTakesAction();
                break;
            case NORMAL:
                player.useItem(item, eventManager, discoveryManager, maze);
                playerTurnTakesAction();
                break;
            default:
                break;
        }
    }

    public Maze getMaze() {
        return maze;
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        font.dispose();
        if (hud != null)
            hud.dispose();
        if (entityRenderer != null)
            entityRenderer.dispose();
        if (soundManager != null)
            soundManager.dispose();
        if (fbo != null)
            fbo.dispose();
        if (crtShader != null)
            crtShader.dispose();
        if (stochasticManager != null)
            stochasticManager.dispose();
        postProcessBatch.dispose();
    }

    // --- NEW: Visceral API ---
    public void addTrauma(float amount) {
        this.trauma = Math.min(1.0f, this.trauma + amount);
    }

    public void triggerHitPause(float duration) {
        this.hitPauseTimer = duration;
    }

    public FirstPersonWeaponOverlay getWeaponOverlay() {
        return weaponOverlay;
    }
}
