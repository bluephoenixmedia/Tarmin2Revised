package com.bpm.minotaur.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;

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
import com.bpm.minotaur.gamedata.monster.Monster;
import com.bpm.minotaur.rendering.SpellPostProcessor;
import com.bpm.minotaur.rendering.SpellCastOverlay;

import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.bpm.minotaur.Tarmin2;
import com.bpm.minotaur.gamedata.*;
import com.bpm.minotaur.gamedata.effects.ActiveStatusEffect;
import com.bpm.minotaur.gamedata.effects.StatusEffectType;
import com.bpm.minotaur.gamedata.item.Item;
import com.bpm.minotaur.gamedata.item.ItemColor;
import com.bpm.minotaur.gamedata.item.ShelterChest;

import com.bpm.minotaur.gamedata.player.Player;
import com.bpm.minotaur.gamedata.player.PlayerStats;
import com.bpm.minotaur.generation.Biome;
import com.bpm.minotaur.managers.*;
import com.bpm.minotaur.gamedata.bones.BonesData;
import com.bpm.minotaur.rendering.*;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableData;
import com.bpm.minotaur.gamedata.spawntables.SpawnTableEntry;
import com.bpm.minotaur.screens.firstaid.FirstAidModal;
import com.bpm.minotaur.gamedata.spawntables.WeightedRandomList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final World3DRenderer world3DRenderer = new World3DRenderer();
    private final EntityRenderer entityRenderer = new EntityRenderer(game.getItemDataManager(), game.getAssetManager());
    private final com.bpm.minotaur.rendering.vfx.LaserBeamRenderer laserBeamRenderer =
            new com.bpm.minotaur.rendering.vfx.LaserBeamRenderer();
    private final com.bpm.minotaur.rendering.vfx.StatusVignetteRenderer statusVignetteRenderer =
            new com.bpm.minotaur.rendering.vfx.StatusVignetteRenderer();
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
    private SpellPostProcessor spellPostProcessor;
    private SpellCastOverlay spellCastOverlay;
    private final SpriteBatch postProcessBatch = new SpriteBatch();
    private float time = 0f;

    private final Viewport fboViewport;
    private static final int VIRTUAL_WIDTH = 1920;
    private static final int VIRTUAL_HEIGHT = 1080;
    private static final int HUD_HEIGHT = 200;
    private static final int GAME_HEIGHT = VIRTUAL_HEIGHT - HUD_HEIGHT; // 880px

    private float trauma = 0f;
    private final Vector2 originalDir = new Vector2();
    private final Vector2 originalPlane = new Vector2();
    private final java.util.Random rng = new java.util.Random();

    private boolean hasLoadedLevel = false;
    private int turnCount = 0;

    // --- Death Idempotency & Run Tracking (NetHack Progression Reboot) ---
    private String activeExpeditionRunId = java.util.UUID.randomUUID().toString();
    private boolean isDeathTransitionTriggered = false;

    private final TurnManager turnManager; // NEW

    // --- NEW: Visceral Feedback Components ---
    private FirstPersonWeaponOverlay weaponOverlay;
    private com.bpm.minotaur.rendering.weaponview.WeaponViewTunerPanel weaponTunerPanel;
    private CraftingManager craftingManager;

    public static class VisorDroplet {
        public float x, y;
        public float dripSpeed;
        public float timer;
        public float size;
        public com.badlogic.gdx.graphics.g2d.TextureRegion region;
        public Color color = new Color(0.85f, 0.12f, 0.12f, 1f);
    }
    private final java.util.List<VisorDroplet> visorDroplets = new ArrayList<>();
    private final java.util.List<com.badlogic.gdx.graphics.g2d.TextureRegion> visorDropletTextures = new ArrayList<>();

    private float hitPauseTimer = 0f;
    private float sleepTimer = 0f;

    // --- Debug UI ---
    private DebugSpawnOverlay debugSpawnOverlay;
    private com.bpm.minotaur.debug.MonsterDebugOverlay monsterDebugOverlay;
    private com.badlogic.gdx.InputMultiplexer inputMultiplexer;

    public GameScreen(Tarmin2 game, int level, Difficulty difficulty, GameMode gameMode) {
        super(game);
        this.difficulty = difficulty;
        this.gameMode = gameMode;
        this.currentLevel = level;
        this.stochasticManager = new StochasticManager();

        this.fboViewport = new FitViewport(VIRTUAL_WIDTH, GAME_HEIGHT); // Use GAME_HEIGHT
        this.fboViewport.update(VIRTUAL_WIDTH, GAME_HEIGHT, true);
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
        this.monsterAiManager.setFactionMatrix(this.worldManager.getFactionMatrix());

        // Initialize Input Multiplexer
        inputMultiplexer = new com.badlogic.gdx.InputMultiplexer();

        // --- NEW: Weapon Overlay ---
        this.weaponOverlay = new FirstPersonWeaponOverlay(game.getItemDataManager(), game.getAssetManager());
        this.weaponOverlay.getViewCalibration().load();
        this.weaponTunerPanel = new com.bpm.minotaur.rendering.weaponview.WeaponViewTunerPanel(
                weaponOverlay, game.getItemDataManager());

        // Void chain laser: each beam gets an impact flash and scorch mark the instant it
        // appears, in sync with the staggered reveal that shows the burst's climb.
        this.laserBeamRenderer.setBeamStartListener(this::onLaserBeamStart);
    }

    /**
     * Fires the instant one beam of a merchant burst appears on screen. A beam
     * that struck something gets a violet impact flash there; a stray that
     * burned into a wall instead leaves a scorch mark. If the beam is the one
     * that clipped the player, their screen flinches too.
     */
    private void onLaserBeamStart(com.bpm.minotaur.gamedata.laser.LaserBurst.Beam beam) {
        com.badlogic.gdx.math.Vector2 end = beam.getEnd();
        com.badlogic.gdx.math.Vector3 impact3d = new com.badlogic.gdx.math.Vector3(end.x, 0.42f, end.y);

        if (soundManager != null) {
            soundManager.playVoidLaser();
        }

        if (beam.getStruck() == com.bpm.minotaur.gamedata.laser.LaserBurst.Struck.WALL) {
            if (maze != null && maze.getGoreManager() != null) {
                maze.getGoreManager().spawnElementalScorch(impact3d,
                        new com.badlogic.gdx.graphics.Color(0.35f, 0.1f, 0.5f, 0.9f), 0.30f);
            }
        } else if (combatManager != null && combatManager.getAnimationManager() != null) {
            combatManager.getAnimationManager().spawnExplosion(
                    com.bpm.minotaur.rendering.vfx.SpellExplosionRegistry.ExplosionType.VOID, impact3d, 0.9f, 0.35f);
        }

        if (beam.getStruck() == com.bpm.minotaur.gamedata.laser.LaserBurst.Struck.PLAYER
                && getSpellPostProcessor() != null) {
            getSpellPostProcessor().triggerChromaticAberration(0.5f, 0.25f);
            getSpellPostProcessor().triggerVignette(
                    new com.badlogic.gdx.graphics.Color(0.6f, 0.2f, 0.8f, 1f), 0.35f, 0.3f);
        }
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
            fbo = new FrameBuffer(Pixmap.Format.RGB888, VIRTUAL_WIDTH, GAME_HEIGHT, true); // Use GAME_HEIGHT
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

        if (spellPostProcessor == null) {
            spellPostProcessor = new SpellPostProcessor();
        }
        if (spellCastOverlay == null) {
            spellCastOverlay = new SpellCastOverlay();
        }

        if (!hasLoadedLevel) {
            generateLevel(currentLevel);
            hasLoadedLevel = true;
        }

        camera3d = new PerspectiveCamera(67, VIRTUAL_WIDTH, GAME_HEIGHT); // Use GAME_HEIGHT
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

        // --- Init Monster Debug Overlay (F12) ---
        if (monsterDebugOverlay == null) {
            monsterDebugOverlay = new com.bpm.minotaur.debug.MonsterDebugOverlay(
                    game.getMonsterDataManager(), game.getAssetManager());
        }

        // --- Setup Input Multiplexer (CRITICAL for resuming from Inventory) ---
        if (hud != null) {
            hud.setGameScreen(this);
            hud.setDiscoveryManager(this.discoveryManager);
            player.setItemPickupListener(item -> hud.showPickupToast(item));
            inputMultiplexer.clear();
            // First in line so a key or click anywhere, HUD included, only breaks a study's
            // concentration instead of also moving, attacking or pressing a button.
            inputMultiplexer.addProcessor(tomeStudyBreaker);
            // The F11 weapon tuner passes everything through while closed, and while open
            // has to see the arrows before the game turns them into movement.
            inputMultiplexer.addProcessor(weaponTunerPanel);
            inputMultiplexer.addProcessor(hud.stage); // UI First
            inputMultiplexer.addProcessor(this); // Game Second
            Gdx.input.setInputProcessor(inputMultiplexer);
        }
        if (weaponOverlay != null && player != null && player.getInventory() != null) {
            weaponOverlay.setEquipment(player.getInventory().getRightHand(), player.getInventory().getLeftHand());
        }
        updateMusicTrackForCurrentZone();
    }

    public void updateMusicTrackForCurrentZone() {
        if (player == null || maze == null) return;
        // Don't override combat music if combat is in progress
        if (combatManager != null && (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU
                || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN
                || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_SELECT_DICE
                || combatManager.getCurrentState() == CombatManager.CombatState.PHYSICS_RESOLUTION
                || combatManager.getCurrentState() == CombatManager.CombatState.PHYSICS_DELAY
                || combatManager.getCurrentState() == CombatManager.CombatState.MONSTER_TURN
                || combatManager.getCurrentState() == CombatManager.CombatState.MONSTER_REVEAL)) {
            return;
        }

        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        boolean isShelter = maze.isHomeTile(px, py);

        if (isShelter) {
            MusicManager.getInstance().playShelterMusic("sounds/music/tarmin_ambient.ogg");
        } else {
            int strataDepth = Math.max(1, (currentLevel - 1) / 3 + 1);
            if (strataDepth >= 3 || currentLevel >= 4) {
                MusicManager.getInstance().playExplorationMusic("sounds/music/tarmin_catacombs_drone.wav");
            } else {
                MusicManager.getInstance().playExplorationMusic("sounds/music/tarmin_maze.mp3");
            }
        }
    }

    public GameEventManager getEventManager() {
        return eventManager;
    }

    public com.bpm.minotaur.managers.DiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
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
            player.getStatusManager().initialize(this.eventManager, player);
            player.setMaze(this.maze);
            worldManager.setPlayerReference(player);
        } else {
            this.maze = worldManager.getInitialMaze();
            resetPlayerPosition();
            player.setMaze(this.maze);
            worldManager.setPlayerReference(player);
        }

        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager,
                game.getItemDataManager(), stochasticManager, turnManager, monsterAiManager, worldManager);

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

        visorDropletTextures.clear();
        for (com.badlogic.gdx.graphics.g2d.TextureRegion dr : dropRegs) {
            if (dr != null) visorDropletTextures.add(dr);
        }
        if (spatterReg != null) visorDropletTextures.add(spatterReg);

        // --- DICE UI INTEGRATION ---
        this.combatDiceOverlay = new CombatDiceOverlay(player, combatManager, game.getViewport());
        // ---------------------------

        hud = new Hud(game.getBatch(), player, maze, combatManager, eventManager, worldManager, game, debugManager,
                gameMode);
        hud.setGameScreen(this);
        hud.setDiscoveryManager(this.discoveryManager);
        player.setItemPickupListener(item -> hud.showPickupToast(item));
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        if (this.gameMode == GameMode.ADVANCED && levelNumber == 1) {
            firstPersonRenderer.setTheme(RetroTheme.ADVANCED_COLOR_THEME_BLUE);
        } else {
            firstPersonRenderer.setTheme(RetroTheme.STANDARD_THEME);
        }
        DebugRenderer.printMazeToConsole(maze);
        onChunkEntered();

        // Input setup moved to show()
    }

    // ---- Divinity helpers ----

    private void onChunkEntered() {
        if (hud == null || maze == null) return;
        DivinityManager dm = DivinityManager.getInstance();
        int level = worldManager.getCurrentLevel();
        GridPoint2 cid = worldManager.getCurrentPlayerChunkId();
        String key = DivinityManager.buildChunkKey(level, cid.x, cid.y);

        int gained = dm.tryAwardChunkDivinities(key, level);
        if (gained > 0) {
            hud.addMessage("+" + gained + " " + DivinityManager.DIVINITY_NAME + " (new area)");
        }

        if (worldManager != null && player != null && maze != null) {
            worldManager.updateExploration(player, maze);
        }
    }

    // ---- End Divinity helpers ----

    private void resetPlayerPosition() {
        GridPoint2 startPos = worldManager.getInitialPlayerStartPos();
        player.getPosition().set(startPos.x + 0.5f, startPos.y + 0.5f);
        worldManager.setPlayerReference(player);
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
        MusicManager.getInstance().update(delta);

        // --- VISCERAL HIT PAUSE ---
        if (hitPauseTimer > 0) {
            hitPauseTimer -= delta;
            // Freeze game logic during hit pause, but keep rendering the static frame (and
            // shake!)
            // We do NOT update time, combatManager, etc.
            if (hitPauseTimer <= 0)
                hitPauseTimer = 0;
        } else if (player != null && player.getStatusManager().hasEffect(StatusEffectType.SLEEP) &&
                (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE
                        || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN
                        || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU)) {
            // --- SLEEP LOGIC ---
            sleepTimer += delta;
            if (sleepTimer > 0.5f) {
                sleepTimer = 0f;
                tryDreamDimensionShift();
                // Force Pass Turn
                if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN
                        || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU) {
                    combatManager.playerGuard();
                    eventManager.addEvent(new com.bpm.minotaur.gamedata.GameEvent("Zzz...", 1f));
                } else if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                    player.getStatusManager().updateTurn();
                    turnManager.processTurn(maze, player, monsterAiManager, combatManager, worldManager, eventManager, game.getItemDataManager(), game.getAssetManager());
                    eventManager.addEvent(new com.bpm.minotaur.gamedata.GameEvent("Zzz...", 1f));

                    // Update World
                    if (worldManager != null) {
                        worldManager.update(delta);
                        // Weather trauma update
                        if (worldManager.getWeatherManager() != null) {
                            float targetTrauma = worldManager.getWeatherManager().getTraumaLevel();
                            this.trauma = com.badlogic.gdx.math.MathUtils.lerp(this.trauma, targetTrauma, 2.0f * delta);
                        }
                    }
                }
            }
            animationManager.update(delta); // Keep animations running

            // Allow minimal updates?
            if (hud != null)
                hud.update(delta);
            eventManager.update(delta);

        } else {
            // Normal Update Loop
            time += delta;
            combatManager.update(delta);
            if (combatDiceOverlay != null)
                combatDiceOverlay.update(delta);

            // --- SEAMLESS LOADING LOGIC (Proximity Only) ---
            updateSeamlessChunkLoading(delta);

            animationManager.update(delta);
            if (maze != null)
                maze.update(delta);
            if (hud != null)
                hud.update(delta);
            eventManager.update(delta);
            handleSystemEvents();
            updateTomeStudy(delta);
            if (player != null && player.getPendingTomeChoice() != null) {
                game.setScreen(new SpellbookScreen(game, this, player, maze));
                return;
            }

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

            // Update Overlay Animation and Equipment
            if (player != null && player.getInventory() != null) {
                weaponOverlay.setEquipment(player.getInventory().getRightHand(), player.getInventory().getLeftHand());
                weaponOverlay.setBuffGlow(getActiveBuffGlowColor(player));
            }
            weaponOverlay.update(delta);
            DivinityOrbManager.getInstance().update(delta);
        }

        // Advance render mode transition every frame, regardless of game state
        debugManager.update(delta);

        if (spellPostProcessor != null) {
            spellPostProcessor.update(delta);
        }
        if (spellCastOverlay != null) {
            spellCastOverlay.update(delta);
        }
        if (statusVignetteRenderer != null) {
            statusVignetteRenderer.update(delta);
        }

        boolean renderToFbo = useCrtFilter || (spellPostProcessor != null && spellPostProcessor.isActive());
        if (renderToFbo) {
            fbo.begin();
            fboViewport.apply();
            shapeRenderer.setProjectionMatrix(fboViewport.getCamera().combined);
        }

        ScreenUtils.clear(0, 0, 0, 1);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_DEPTH_BUFFER_BIT);
        Viewport currentViewport = renderToFbo ? fboViewport : game.getViewport();

        boolean isShaking = (player != null && trauma > 0.01f);
        if (isShaking) {
            originalDir.set(player.getDirectionVector());
            originalPlane.set(player.getCameraPlane());
            float shakePower = trauma * 0.1f;
            float angle = (rng.nextFloat() - 0.5f) * shakePower;
            player.getDirectionVector().rotateRad(angle);
            player.getCameraPlane().rotateRad(angle);
            player.getCameraPlane().rotateRad(angle);
        }

        // --- NEW: Dizzy Effect (Camera Roll) ---
        if (player != null && player.getStatusManager().hasEffect(StatusEffectType.CONFUSED)) {
            // Apply a gentle sway/roll
            float dizzyAngle = 2.0f * com.badlogic.gdx.math.MathUtils.sin(time * 2.0f);

            if (renderToFbo) {
                // If using FBO, rotate FBO viewport camera UP vector.
                fboViewport.getCamera().up.set(0, 1, 0); // Reset first
                fboViewport.getCamera().up.rotate(fboViewport.getCamera().direction, dizzyAngle);
                fboViewport.getCamera().update();
            } else {
                game.getViewport().getCamera().up.set(0, 1, 0);
                game.getViewport().getCamera().up.rotate(game.getViewport().getCamera().direction, dizzyAngle);
                game.getViewport().getCamera().update();
            }
        } else {
            // Reset Camera Up to ensure it doesn't get stuck
            if (renderToFbo) {
                fboViewport.getCamera().up.set(0, 1, 0);
                fboViewport.getCamera().update();
            } else {
                game.getViewport().getCamera().up.set(0, 1, 0);
                game.getViewport().getCamera().update();
            }
        }

        boolean isBlind = (player != null
                && player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.BLIND));

        if (isBlind) {
            game.getBatch().begin();
            // Blindness rendering (black screen)
            game.getBatch().end();
        } else if (player != null && maze != null) {
            if (debugManager.getRenderEngine() == DebugManager.RenderEngine.PLANAR_3D) {
                world3DRenderer.render(player, maze, currentViewport, worldManager, currentLevel, gameMode, combatManager);
                // The merchant's Void chain laser only renders in the modern planar-3D
                // engine, whose camera-space convention (x, height, -y) LaserBeamRenderer
                // is built against; the retro raycaster does not get beam VFX.
                laserBeamRenderer.update(Gdx.graphics.getDeltaTime());
                laserBeamRenderer.render(world3DRenderer.getCamera());
            } else {
                firstPersonRenderer.render(shapeRenderer, player, maze, currentViewport, worldManager, currentLevel,
                        gameMode);

                if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE
                        || combatManager.getMonster() == null) {
                    entityRenderer.render(shapeRenderer, player, maze, currentViewport,
                            firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, worldManager);
                } else {
                    entityRenderer.render(shapeRenderer, player, maze, currentViewport,
                            firstPersonRenderer.getDepthBuffer(), firstPersonRenderer, worldManager,
                            combatManager.getMonster());
                }

                // --- FIX: Window Overlay Pass (Clips entities at window base/top) ---
                game.getBatch().setProjectionMatrix(currentViewport.getCamera().combined);
                firstPersonRenderer.renderWindowOverlays(game.getBatch(), currentViewport);

                animationManager.render(shapeRenderer, player, currentViewport, firstPersonRenderer.getDepthBuffer(),
                        firstPersonRenderer, maze);
            }

            // --- VISCERAL: Weapon Overlay ---
            // Render 2D weapon swipe on top of 3D world but before HUD/PostProcess?
            // Actually best to do it before CRT so it gets filtered.
            // We need a SpriteBatch for this. Game has one.

            // Use UI viewport (game.getViewport()) for overlay, NOT world viewport
            // (currentViewport)
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);

            if (debugManager.getRenderMode() == DebugManager.RenderMode.RETRO) {
                shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
                weaponOverlay.renderRetro(shapeRenderer, game.getViewport());
            } else {
                shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
                weaponOverlay.renderTrails(shapeRenderer);

                game.getBatch().begin();
                weaponOverlay.render(game.getBatch(), game.getViewport());
                weaponTunerPanel.render(game.getBatch(), font, game.getViewport());
                game.getBatch().end();
            }

            if (combatManager.getAttackIndicatorMonster() != null
                    && combatManager.getAttackIndicatorVariant() == CombatManager.AttackIndicatorVariant.SCREEN_SLASH) {
                shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
                renderScreenSlashOverlay(combatManager.getAttackIndicatorProgress());
            }

            shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
            firstPersonRenderer.renderTemperatureVignette(shapeRenderer, game.getViewport(),
                    player.getStats().getBodyTemperature(), time);

            if (spellCastOverlay != null && spellCastOverlay.isActive()) {
                shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                spellCastOverlay.render(shapeRenderer, game.getViewport());
                shapeRenderer.end();
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

        if (renderToFbo) {
            fbo.end();
            game.getViewport().apply();
            ScreenUtils.clear(0, 0, 0, 1);
            postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
            postProcessBatch.begin();
            if (useCrtFilter) {
                postProcessBatch.setShader(crtShader);
                crtShader.setUniformf("u_time", time);
                if (spellPostProcessor != null) {
                    spellPostProcessor.applyUniforms(crtShader, time);
                }
                // Draw FBO offset by HUD_HEIGHT (180)
                postProcessBatch.draw(fbo.getColorBufferTexture(), 0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT, 0, 0, 1, 1);
            } else if (spellPostProcessor != null && spellPostProcessor.isActive()) {
                spellPostProcessor.renderModern(postProcessBatch, fbo.getColorBufferTexture(), 0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT);
            } else {
                postProcessBatch.setShader(null);
                postProcessBatch.draw(fbo.getColorBufferTexture(), 0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT, 0, 0, 1, 1);
            }
            postProcessBatch.end();
            postProcessBatch.setShader(null);
            // --- CRT FIX: Reset view when CRT is off ---
            game.getViewport().apply();
        }

        // --- RENDER MODE TRANSITION OVERLAY (over world, under HUD) ---
        if (debugManager.isTransitioning()) {
            renderModeTransitionOverlay(
                    debugManager.getTransitionOverlayAlpha(),
                    debugManager.getTransitionPeakFactor(),
                    debugManager.isModernToRetroTransition(),
                    debugManager.isDimensionalWarp());
        }

        if (hud != null) {
            // --- NEW: Sync Combat Menu Visibility ---
            if (combatManager != null && hud.combatMenu != null) {
                hud.combatMenu.setVisible(combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU);
            }
            hud.render();
        }

        DivinityOrbManager.getInstance().render(shapeRenderer, game.getViewport());

        game.getBatch().setProjectionMatrix(currentViewport.getCamera().combined);
        game.getBatch().begin();
        animationManager.renderDamageText(game.getBatch(), currentViewport, player, firstPersonRenderer.getDepthBuffer());
        game.getBatch().end();

        renderCombatOverlay();

        if (monsterDebugOverlay != null) {
            monsterDebugOverlay.render(delta);
        }
    }

    private void renderCombatOverlay() {
        // --- ATMOSPHERIC STATUS VIGNETTES (Bleed, Cold, Poison, Fever, Starvation, Berzerk, Iron Skin) ---
        if (statusVignetteRenderer != null && player != null) {
            statusVignetteRenderer.render(shapeRenderer, game.getViewport(), player);
        }

        // --- VISOR BLOOD SPLATTERS ---
        if (!visorDroplets.isEmpty()) {
            float delta = Gdx.graphics.getDeltaTime();
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            for (int i = visorDroplets.size() - 1; i >= 0; i--) {
                VisorDroplet vd = visorDroplets.get(i);
                vd.timer -= delta;
                vd.y -= vd.dripSpeed * delta;
                if (vd.timer <= 0) {
                    visorDroplets.remove(i);
                    continue;
                }
                float alpha = com.badlogic.gdx.math.MathUtils.clamp(vd.timer / 1.5f, 0f, 1f);
                vd.color.a = alpha;
                game.getBatch().setColor(vd.color);
                game.getBatch().draw(vd.region, vd.x, vd.y, vd.size, vd.size);
            }
            game.getBatch().setColor(Color.WHITE);
            game.getBatch().end();
        }

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

    public void triggerVisorSplatter() {
        if (visorDropletTextures.isEmpty() || game.getViewport() == null) return;
        int count = com.badlogic.gdx.math.MathUtils.random(1, 3);
        float vw = game.getViewport().getWorldWidth();
        float vh = game.getViewport().getWorldHeight();

        for (int i = 0; i < count; i++) {
            VisorDroplet d = new VisorDroplet();
            boolean leftSide = com.badlogic.gdx.math.MathUtils.randomBoolean();
            d.x = leftSide
                    ? com.badlogic.gdx.math.MathUtils.random(vw * 0.05f, vw * 0.35f)
                    : com.badlogic.gdx.math.MathUtils.random(vw * 0.65f, vw * 0.95f);
            d.y = com.badlogic.gdx.math.MathUtils.random(vh * 0.35f, vh * 0.85f);
            d.dripSpeed = com.badlogic.gdx.math.MathUtils.random(12f, 26f);
            d.timer = 1.5f;
            d.size = com.badlogic.gdx.math.MathUtils.random(48f, 76f);
            d.region = visorDropletTextures.get(com.badlogic.gdx.math.MathUtils.random(visorDropletTextures.size() - 1));
            visorDroplets.add(d);
        }
    }

    /**
     * Draws a full-screen CRT-style transition overlay over the game viewport
     * (not the HUD). The overlay fades through black with horizontal scanlines to
     * give a distinctive retro monitor power-cycle feel.
     *
     * @param blackAlpha  0–1 opacity of the black base layer
     * @param peakFactor  0–1 how close we are to the transition midpoint (drives scanline intensity)
     * @param toRetro     true when transitioning MODERN→RETRO
     */
    /**
     * Ultra-fast screen-space slash overlay (claw scratches / blade arc) that flashes
     * across the viewport as one of the randomized monster attack telegraph variants.
     */
    private void renderScreenSlashOverlay(float progress) {
        float alpha = Math.max(0f, 1f - progress);
        if (alpha <= 0f) return;

        float w = game.getViewport().getWorldWidth();
        float h = game.getViewport().getWorldHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.85f, 0.05f, 0.05f, alpha * 0.6f);

        float thickness = h * 0.02f;
        for (int i = -1; i <= 1; i++) {
            float offset = i * h * 0.12f;
            shapeRenderer.rectLine(0, h * 0.65f + offset, w, h * 0.35f + offset, thickness);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderModeTransitionOverlay(float blackAlpha, float peakFactor, boolean toRetro, boolean isWarp) {
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
        shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);

        // Base black overlay covers only the game area (above the HUD strip)
        shapeRenderer.setColor(0f, 0f, 0f, blackAlpha);
        shapeRenderer.rect(0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT);

        if (isWarp) {
            // --- REALITY DECONSTRUCTION WARP ---
            // Chromatic scanline shear across horizontal bands
            int bands = 36;
            float bandH = (float) GAME_HEIGHT / bands;
            for (int i = 0; i < bands; i++) {
                float y = HUD_HEIGHT + i * bandH;
                float shear = (float) Math.sin((time * 18.0f) + i * 0.4f) * peakFactor * 24.0f;
                if (i % 2 == 0) {
                    // Cyan / Ethereal blue band offset
                    shapeRenderer.setColor(0.15f, 0.75f, 0.95f, peakFactor * 0.40f);
                    shapeRenderer.rect(shear, y, VIRTUAL_WIDTH, bandH * 0.5f);
                } else {
                    // Mystic Violet / Tarmin-Zul purple band offset
                    shapeRenderer.setColor(0.72f, 0.20f, 0.90f, peakFactor * 0.40f);
                    shapeRenderer.rect(-shear, y, VIRTUAL_WIDTH, bandH * 0.5f);
                }
            }

            // Flashing stag skull sigil of Tarmin-Zul at transition peak
            if (peakFactor > 0.40f) {
                float sigilAlpha = (peakFactor - 0.40f) / 0.60f;
                shapeRenderer.setColor(0.85f, 0.35f, 0.95f, sigilAlpha * 0.85f);

                float cx = VIRTUAL_WIDTH * 0.5f;
                float cy = HUD_HEIGHT + GAME_HEIGHT * 0.5f;

                // Central stag skull / rune sigil lines
                // Skull forehead
                shapeRenderer.rect(cx - 14, cy - 10, 28, 20);
                // Elongated snout
                shapeRenderer.triangle(cx - 10, cy - 10, cx + 10, cy - 10, cx, cy - 42);

                // Antler Main Beams
                shapeRenderer.rectLine(cx - 10, cy + 8, cx - 45, cy + 50, 4);
                shapeRenderer.rectLine(cx + 10, cy + 8, cx + 45, cy + 50, 4);

                // Antler Tines
                shapeRenderer.rectLine(cx - 24, cy + 26, cx - 50, cy + 28, 3);
                shapeRenderer.rectLine(cx - 36, cy + 40, cx - 62, cy + 46, 3);
                shapeRenderer.rectLine(cx + 24, cy + 26, cx + 50, cy + 28, 3);
                shapeRenderer.rectLine(cx + 36, cy + 40, cx + 62, cy + 46, 3);
            }
        } else {
            // Standard scanlines
            if (peakFactor > 0.05f) {
                int lineCount = 60;
                float lineH = (float) GAME_HEIGHT / lineCount;
                float scanAlpha = peakFactor * 0.55f;
                shapeRenderer.setColor(0f, 0f, 0f, scanAlpha);
                for (int i = 0; i < lineCount; i += 2) {
                    shapeRenderer.rect(0, HUD_HEIGHT + i * lineH, VIRTUAL_WIDTH, lineH * 0.6f);
                }
            }

            // When transitioning to RETRO, add a faint amber tint near the midpoint to
            // evoke an old phosphor monitor warming up.
            if (toRetro && peakFactor > 0.6f) {
                float tintAlpha = (peakFactor - 0.6f) / 0.4f * 0.18f; // 0→0.18
                shapeRenderer.setColor(0.9f, 0.55f, 0.05f, tintAlpha);
                shapeRenderer.rect(0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT);
            }
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
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

        // NetHack-style bones ghostly presence notification check on chunk entry
        checkBonesPresenceNotification();

        // Centralized Player Death Check: any damage (combat, environmental, fatigue) reducing HP to 0 triggers death
        if (player != null && player.getCurrentHP() <= 0) {
            killPlayer();
        }

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
                    if (weaponOverlay != null) {
                        weaponOverlay.reset();
                    }
                    hud.getEncounterWindow().configure(player, game.getEncounterManager(), eventManager,
                            game.getItemDataManager(), game.getMonsterDataManager(), game.getAssetManager(),
                            maze, null);
                    hud.getEncounterWindow().show(encounter);
                }
            }
        }

        while ((event = eventManager.findAndConsume(GameEvent.EventType.SHOPKEEPER_INTERACTION)) != null) {
            if (event.payload instanceof com.bpm.minotaur.gamedata.ShopkeeperNpc) {
                com.bpm.minotaur.gamedata.ShopkeeperNpc shopkeeper = (com.bpm.minotaur.gamedata.ShopkeeperNpc) event.payload;
                if (hud != null && hud.getShopkeeperWindow() != null) {
                    if (weaponOverlay != null) {
                        weaponOverlay.reset();
                    }
                    hud.getShopkeeperWindow().configure(player, shopkeeper, eventManager,
                            game.getItemDataManager(), () -> {});
                    hud.getShopkeeperWindow().show();
                }
            }
        }

        // --- Merchant Void Chain Laser: the resolution already happened in
        // ShopkeeperAiManager; here we only make it visible. ---
        while ((event = eventManager.findAndConsume(GameEvent.EventType.LASER_BURST)) != null) {
            if (event.payload instanceof com.bpm.minotaur.gamedata.laser.LaserBurst.BurstResult) {
                laserBeamRenderer.spawnBurst(
                        (com.bpm.minotaur.gamedata.laser.LaserBurst.BurstResult) event.payload);
            }
        }
        // SHOPKEEPER_RESTITUTION carries no rendering of its own -- the discount it
        // sets on the ShopkeeperNpc is read directly by ShopkeeperWindow at purchase
        // time -- so it is only consumed here to keep it from lingering in the queue.
        eventManager.consumeAll(GameEvent.EventType.SHOPKEEPER_RESTITUTION);

        // --- Portal & Dimensional Warp Handling ---
        while ((event = eventManager.findAndConsume(GameEvent.EventType.PORTAL_ACTIVATED)) != null) {
            boolean toVoid = !com.bpm.minotaur.managers.DimensionalManager.getInstance().isInVoid();
            Gdx.app.log("GameScreen", "PORTAL_ACTIVATED detected. Dimensional warp toVoid=" + toVoid);

            if (toVoid) {
                com.bpm.minotaur.managers.DimensionalManager.getInstance().enterVoid(
                        false, player.getPosition(), currentLevel, worldManager.getCurrentPlayerChunkId());
                debugManager.triggerDimensionalWarp(true);
                soundManager.playDimensionalWarpSound();
                hud.addMessage("Reality shears! Entering the Ancient Raycast Void of Tarmin-Zul.");
            } else {
                boolean recoveredSoul = com.bpm.minotaur.managers.DimensionalManager.getInstance().isHollowShade();
                com.bpm.minotaur.managers.DimensionalManager.getInstance().exitVoid(true);
                debugManager.triggerDimensionalWarp(false);
                soundManager.playDimensionalWarpSound();
                if (recoveredSoul) {
                    player.getStats().setCurrentHP(player.getStats().getMaxHP());
                    hud.addMessage("Your mortal soul is restored at the Rift Anchor!");
                }
                hud.addMessage("The Void dissolves! Returning to the Mortal Realm.");
            }
        }

        while ((event = eventManager.findAndConsume(GameEvent.EventType.PLAYER_DIED)) != null) {
            Gdx.app.log("GameScreen", "PLAYER_DIED event received.");

            if (isDeathTransitionTriggered) {
                Gdx.app.log("GameScreen", "Duplicate PLAYER_DIED event suppressed; death transition already in progress.");
                continue;
            }
            isDeathTransitionTriggered = true;
            // Purge any further death events queued this frame (e.g. stray monster
            // attacks or status ticks landing the same instant) so only one demise
            // is ever processed per expedition run.
            eventManager.consumeAll(GameEvent.EventType.PLAYER_DIED);

            // Record Bones File for Advanced Mode (full player snapshot before item retention)
            if (gameMode == GameMode.ADVANCED) {
                int curLvl = (worldManager != null) ? worldManager.getCurrentLevel() : 1;
                String epitaph = "Fell in the depths";
                try {
                    com.bpm.minotaur.telemetry.TelemetryManager telemetry = com.bpm.minotaur.telemetry.TelemetryManager.getInstance();
                    if (player.getInjuryManager() != null) {
                        telemetry.setBleedDamageTaken(player.getInjuryManager().getBleedDamageThisRun());
                    }
                    epitaph = buildEpitaph(telemetry);
                } catch (Exception ignored) {
                }
                BonesManager.getInstance().recordBonesOnDeath(player, curLvl, epitaph, gameMode);
            }

            // 1. Advance Doom Clock ("Tarmin's Hunger") -- idempotent per expedition run
            DoomManager.getInstance().recordDeath(activeExpeditionRunId);
            int deaths = DoomManager.getInstance().getDeathCount();
            float bridge = DoomManager.getInstance().getBridgeIntegrity();
            Gdx.app.log("GameScreen", "Doom updated on death. Count: " + deaths + " (" + (int) bridge + "%)");

            // 2. Check for Apocalypse Wipe (50 deaths reached)
            if (DoomManager.getInstance().isApocalypse()) {
                Gdx.app.log("GameScreen", "Apocalypse condition met! Triggering GameOverScreen.");
                game.setScreen(new GameOverScreen(game));
                return;
            }

            if (combatManager != null) {
                combatManager.endCombat();
            }

            // 3. Lose unequipped items (capped by the Loot Retention upgrade); equipped
            //    weapon/offhand and worn equipment are protected and simply carry over.
            //    Travel crafting kits (CRAFTING_TOOLKIT and COOKING_KIT) are permanently retained on death.
            int retentionCap = DivinityManager.getInstance().getLootRetentionCap();
            List<Item> allUnequipped = new ArrayList<>(player.getInventory().getMainInventory());
            Item[] quickSlots = player.getInventory().getQuickSlots();
            for (int i = 0; i < quickSlots.length; i++) {
                if (quickSlots[i] != null) {
                    allUnequipped.add(quickSlots[i]);
                    quickSlots[i] = null;
                }
            }
            player.getInventory().getMainInventory().clear();

            List<Item> permanentKits = new ArrayList<>();
            List<Item> atRiskItems = new ArrayList<>();
            for (Item itm : allUnequipped) {
                if (itm != null && (itm.getType() == Item.ItemType.CRAFTING_TOOLKIT || itm.getType() == Item.ItemType.COOKING_KIT)) {
                    permanentKits.add(itm);
                } else {
                    atRiskItems.add(itm);
                }
            }

            for (Item kit : permanentKits) {
                player.getInventory().pickupToBackpack(kit);
            }

            int retainedCount = Math.min(retentionCap, atRiskItems.size());
            for (int i = 0; i < retainedCount; i++) {
                player.getInventory().pickupToBackpack(atRiskItems.get(i));
            }
            int lostCount = atRiskItems.size() - retainedCount;

            // 4. Finalize run telemetry & build the run epitaph
            com.bpm.minotaur.telemetry.TelemetryManager telemetry = com.bpm.minotaur.telemetry.TelemetryManager.getInstance();
            if (player.getInjuryManager() != null) {
                telemetry.setBleedDamageTaken(player.getInjuryManager().getBleedDamageThisRun());
            }
            String epitaphCause = buildEpitaph(telemetry);
            int depthReached = Math.max(1, telemetry.getStrataReached());
            int monstersSlain = telemetry.getTotalMonstersKilled();
            int divinitiesEarnedThisRun = telemetry.getDivinitiesEarned();
            telemetry.exportRun(epitaphCause);

            // Roll discoveries unlocked for future expeditions
            java.util.List<String> newUnlocks = com.bpm.minotaur.managers.UnlockManager.getInstance()
                    .rollRunUnlocks(telemetry, depthReached);

            // 5. Play visceral death audio and transition to PlayerDeathScreen
            soundManager.playPlayerDeathSound();
            PlayerDeathScreen deathScreen = new PlayerDeathScreen(game, this, deaths, 50, bridge,
                    lostCount, retainedCount, epitaphCause, epitaphCause, depthReached, monstersSlain,
                    divinitiesEarnedThisRun, newUnlocks);
            game.setScreen(deathScreen);
            return;
        }
    }

    /**
     * Buff Aura Glow: picks a pulsating weapon tint color matching the player's
     * strongest active offensive buff (strength, speed, or a heroic/holy blessing),
     * or null if none are active.
     */
    private Color getActiveBuffGlowColor(Player player) {
        if (player == null || player.getStatusManager() == null) {
            return null;
        }
        com.bpm.minotaur.managers.StatusManager sm = player.getStatusManager();
        if (sm.hasEffect(StatusEffectType.HEROISM)) {
            return new Color(1f, 0.85f, 0.3f, 1f); // Holy gold
        }
        if (sm.hasEffect(StatusEffectType.TEMP_STRENGTH) || sm.hasEffect(StatusEffectType.GIANT_STRENGTH)) {
            return new Color(1f, 0.35f, 0.15f, 1f); // Fiery strength orange
        }
        if (sm.hasEffect(StatusEffectType.HASTED) || sm.hasEffect(StatusEffectType.SUPER_SPEED)
                || sm.hasEffect(StatusEffectType.TEMP_SPEED)) {
            return new Color(0.4f, 0.9f, 1f, 1f); // Cyan speed
        }
        return null;
    }

    /** Chance per potion-sleep "Zzz" tick of slipping into the Ancient Void while dreaming. */
    private static final float DREAM_SHIFT_CHANCE_PER_SLEEP_TICK = 0.01f;
    /** Chance per full night's rest in the shelter bed of the same dream-shift event. */
    private static final float DREAM_SHIFT_CHANCE_PER_BED_REST = 0.02f;

    /**
     * While asleep -- either from a sleep-inducing potion or resting in the shelter
     * bed -- there is a small chance of slipping through the veil into the Ancient
     * Void, exactly as if a Mysterious Portal had been triggered. The player keeps
     * their mortal body (not a Hollow Shade) and can find their way back the same
     * way any other Void visit ends.
     */
    private void tryDreamDimensionShift(float chance) {
        if (com.bpm.minotaur.managers.DimensionalManager.getInstance().isInVoid()) {
            return; // Already elsewhere; nothing to slip into.
        }
        if (rng.nextFloat() >= chance) {
            return;
        }
        eventManager.addEvent(new GameEvent("Your dreams pull you through the veil...", 2.5f));
        com.bpm.minotaur.managers.DimensionalManager.getInstance().enterVoid(
                false, player.getPosition(), currentLevel, worldManager.getCurrentPlayerChunkId());
        debugManager.triggerDimensionalWarp(true);
        soundManager.playDimensionalWarpSound();
        hud.addMessage("Reality shears! You slip into the Ancient Void of Tarmin-Zul.");
    }

    private void tryDreamDimensionShift() {
        tryDreamDimensionShift(DREAM_SHIFT_CHANCE_PER_SLEEP_TICK);
    }

    /**
     * Builds a NetHack-style epitaph line, e.g. "Fell to an Umber Hulk at Strata
     * Depth 3 on turn 412 while parched", from the finalized run's telemetry.
     */
    private String buildEpitaph(com.bpm.minotaur.telemetry.TelemetryManager telemetry) {
        StringBuilder sb = new StringBuilder();
        String killer = telemetry.getKillerMonster();
        if (killer != null && !killer.trim().isEmpty()) {
            String niceName = formatMonsterName(killer);
            String article = niceName.matches("^[AEIOU].*") ? "an" : "a";
            sb.append("Fell to ").append(article).append(" ").append(niceName);
        } else if (telemetry.getBleedDamageTaken() > 0
                && player != null
                && player.getInjuryManager() != null
                && player.getInjuryManager().hasUntreatedInjuries()) {
            // No killer on record but open wounds draining HP: name the real cause
            // rather than burying a bleed-out under "Perished in the depths".
            sb.append("Bled out from untended wounds");
        } else {
            sb.append("Perished in the depths");
        }
        sb.append(" at Strata Depth ").append(Math.max(1, telemetry.getStrataReached()));
        sb.append(" on turn ").append(telemetry.getTurnsLived());

        if (player != null && player.getStats() != null) {
            PlayerStats stats = player.getStats();
            if (stats.getSatiationState() == PlayerStats.SatiationState.STARVING) {
                sb.append(" while starving");
            } else if (stats.getHydrationFloat() <= 0) {
                sb.append(" while parched");
            }
        }
        return sb.toString();
    }

    private String formatMonsterName(String rawType) {
        if (rawType == null || rawType.isEmpty()) {
            return "unknown foe";
        }
        String[] parts = rawType.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /**
     * Awakens the player in the Starting Shelter bed following death:
     * fully regenerates the entire world from a new seed, restores vitals,
     * rehydrates the shelter chest, and switches the display back to GameScreen.
     */
    public void respawnInShelter(int lostCount, int retainedCount, int deaths, float bridge) {
        // Begin a fresh expedition run: reset the death idempotency lock and start
        // a new telemetry run so the next demise is tracked independently.
        this.activeExpeditionRunId = java.util.UUID.randomUUID().toString();
        this.isDeathTransitionTriggered = false;
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().startNewRun();

        // 1. Wipe the explored world -- every chunk (including chunk 0,0) is wiped and reseeded
        worldManager.wipeExploredWorldOnDeath();
        DivinityManager.getInstance().onWorldReset();

        // 2. Restore Player
        com.bpm.minotaur.managers.DimensionalManager.getInstance().reset();
        debugManager.setRenderEngine(com.bpm.minotaur.managers.DebugManager.RenderEngine.PLANAR_3D);
        debugManager.setRenderModeDirect(com.bpm.minotaur.managers.DebugManager.RenderMode.MODERN);
        player.getStats().setCurrentHP(player.getStats().getMaxHP());
        player.getStats().setCurrentMP(player.getStats().getMaxMP());
        player.getStats().setBodyTemperature(PlayerStats.BODY_TEMP_NORMAL);
        player.getStats().setSatiety(80.0f);
        player.getStats().setHydration(80.0f);
        player.getStats().setToxicity(0);
        player.getStatusManager().clearEffects();
        player.abandonTomeStudy();
        // The Player instance survives death, so anatomical trauma must be wiped
        // explicitly -- otherwise open wounds, bleeding, and fever follow the
        // character into the next expedition and can bleed them out before their
        // first encounter.
        if (player.getInjuryManager() != null) {
            player.getInjuryManager().cureAll();
        }

        // Only re-arm a starter weapon if the player somehow has nothing in hand. The
        // off-hand is deliberately left empty: a new game starts with an empty left hand,
        // so re-arming it here handed out a free WOODEN_CROSS on essentially every death.
        // That is not cosmetic -- the cross is a Spiritual weapon, and Bad monsters are
        // immune to War damage, so it quietly gifted the counter to a whole category.
        if (player.getInventory().getRightHand() == null) {
            Item starterWeapon = game.getItemDataManager().createItem(Item.ItemType.RUSTY_SWORD, 0, 0, ItemColor.GRAY, game.getAssetManager());
            player.getInventory().setRightHand(starterWeapon);
        }

        // He wakes in the Shelter washed: the blood of the last expedition does not carry into the next.
        player.washBlood();

        // Clean weapon state upon shelter awakening: reset attack timer/combos, clear blood decals,
        // and force re-synchronization of equipped items and motion profiles.
        if (weaponOverlay != null) {
            weaponOverlay.reset();
            weaponOverlay.clearBloodDecals();
            weaponOverlay.forceRefreshEquipment(player.getInventory().getRightHand(), player.getInventory().getLeftHand());
        }

        // Travel kits: replace any the player has paid for at the Altar but no longer
        // carries. They are not a death handout -- owning the Crafting Bench or Fire Pot
        // is what earns the portable version, and this is only the top-up for one lost
        // in the field.
        grantOwedFieldKits();

        // Shelter Altar Provisions tier: extra rations at the start of each expedition
        int bonusProvisions = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getBonusProvisionCount();
        for (int i = 0; i < bonusProvisions; i++) {
            Item bonusFood = game.getItemDataManager().createItem(Item.ItemType.FOOD, 0, 0, ItemColor.TAN, game.getAssetManager());
            player.getInventory().pickupToBackpack(bonusFood);
        }

        // 3. Respawn in Starting Shelter (Level 1, Chunk 0, 0)
        worldManager.setCurrentLevel(1);
        worldManager.setCurrentChunk(new GridPoint2(0, 0));
        Maze shelterMaze = worldManager.loadChunk(new GridPoint2(0, 0));
        swapToChunk(shelterMaze);

        // Position player at bed / safe start point in shelter
        GridPoint2 bedPos = null;
        for (Item item : shelterMaze.getItems().values()) {
            if (item.getType() == Item.ItemType.HOME_SLEEPING_BAG) {
                int bx = (int) item.getPosition().x;
                int by = (int) item.getPosition().y;
                if (shelterMaze.isPassable(bx - 1, by)) {
                    bedPos = new GridPoint2(bx - 1, by);
                } else if (shelterMaze.isPassable(bx, by + 1)) {
                    bedPos = new GridPoint2(bx, by + 1);
                } else {
                    bedPos = new GridPoint2(bx, by);
                }
                break;
            }
        }
        if (bedPos == null) {
            bedPos = worldManager.getInitialPlayerStartPos();
        }
        player.setPosition(bedPos);
        worldManager.saveCurrentChunk(shelterMaze);

        // Re-hydrate Shelter Chest
        ShelterChest.getInstance().load(game.getItemDataManager(), game.getAssetManager());

        // 4. Feedback & return to game screen
        if (hud != null) {
            hud.addMessage("You awaken back in the Shelter Bed.");
            if (lostCount > 0) {
                hud.addMessage("Lost " + lostCount + " unequipped item" + (lostCount == 1 ? "" : "s") + " to the fall."
                        + (retainedCount > 0 ? " Kept " + retainedCount + " (Loot Retention)." : ""));
            }
            hud.addMessage("The world beyond the Shelter has changed -- a new expedition awaits.");
            hud.addMessage(String.format("Tarmin's Hunger grows: Doom at %d%% (Death %d/50).", (int) bridge, deaths));
        }
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("You awaken back at the Shelter... Tarmin's hunger grows.", 4f));
        }
        soundManager.playDoorOpenSound();

        game.setScreen(this);
    }

    private void swapToChunk(Maze newMaze) {
        this.maze = newMaze;
        player.setMaze(newMaze);
        combatManager = new CombatManager(player, maze, game, animationManager, eventManager, soundManager,
                game.getItemDataManager(), stochasticManager, turnManager, monsterAiManager, worldManager);
        // --- FIX: Re-initialize Dice UI with new Manager ---
        this.combatDiceOverlay = new CombatDiceOverlay(player, combatManager, game.getViewport());
        // ---------------------------------------------------
        hud = new Hud(game.getBatch(), player, maze, combatManager, eventManager, worldManager, game, debugManager,
                gameMode);
        hud.setDiscoveryManager(this.discoveryManager);
        player.setItemPickupListener(item -> hud.showPickupToast(item));
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        combatManager.setHud(hud);
        DebugRenderer.printMazeToConsole(maze);
    }

    private void playerTurnTakesAction() {
        if (MusicManager.getInstance().isResting()) {
            MusicManager.getInstance().setResting(false);
        }
        updateMusicTrackForCurrentZone();
        processPlayerStatusEffects();
        player.getStatusManager().updateTurn();
        player.tickFieldRestCooldown();
        checkForMimicInFront();
        combatManager.tickReload();
        tickPowderDampness();
        if (player.getInjuryManager() != null) {
            player.getInjuryManager().updateStep(player, maze, eventManager);
        }
        if (monsterAiManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            turnManager.processTurn(maze, player, monsterAiManager, combatManager, worldManager, eventManager, game.getItemDataManager(), game.getAssetManager());
        }
        combatManager.checkForAdjacentMonsters();

        // --- Periodic Spawning Hook ---
        turnCount++;
        worldManager.processTurn(player, turnCount);
        // Blood on him and his gear dries from crimson toward black as the delve goes on.
        if (player != null) {
            player.ageBlood(1);
        }

        if (worldManager != null && player != null && maze != null) {
            worldManager.updateExploration(player, maze);
        }
    }

    /**
     * Rolls the mimic perception check against whatever chest the player is now facing.
     *
     * <p>Hooked to the turn rather than the render loop: at 60fps a per-frame roll would
     * make detection certain the instant the player looked at a mimic. The roll is also
     * spent on the Item, so walking away and back does not buy a second one.
     */
    private void checkForMimicInFront() {
        if (player == null || maze == null) {
            return;
        }
        Vector2 v = player.getFacing().getVector();
        GridPoint2 front = new GridPoint2(
                (int) (player.getPosition().x + v.x),
                (int) (player.getPosition().y + v.y));

        Item chest = com.bpm.minotaur.gamedata.monster.MimicReveal.disguisedMimicAt(maze, front);
        if (chest == null) {
            return;
        }

        int wisdom = (player.getStats() != null) ? player.getStats().getWisdom() : 10;
        if (com.bpm.minotaur.gamedata.monster.MimicDetection.attempt(chest, wisdom, mimicPerceptionRng)) {
            eventManager.addEvent(new GameEvent("Something about that chest is wrong.", 3f));
            hud.addMessage("Something about that chest is wrong.");
        }
    }

    /**
     * Carries the player's powder dampness for the turn: soaked by rain or by wading,
     * drying slowly once out of it.
     *
     * <p>Dampness lives on the player rather than being read from the weather at the
     * moment a shot is fired. Weather wetness is outdoor-only and nearly all play is in
     * the strata, so a live read would mean firearms never misfire in practice and the
     * mechanic would never be seen.
     */
    private void tickPowderDampness() {
        if (player == null || player.getStats() == null) {
            return;
        }

        boolean exposed = false;

        if (worldManager != null && worldManager.getWeatherManager() != null) {
            exposed = com.bpm.minotaur.gamedata.firearm.PowderDampness
                    .isSoakingWeather(worldManager.getWeatherManager().getWetness());
        }

        if (!exposed && maze != null && maze.getLiquidManager() != null) {
            exposed = maze.getLiquidManager().hasLiquidAt(
                    (int) player.getPosition().x, (int) player.getPosition().y);
        }

        float dampness = player.getStats().getPowderDampness();
        player.getStats().setPowderDampness(exposed
                ? com.bpm.minotaur.gamedata.firearm.PowderDampness.afterExposure()
                : com.bpm.minotaur.gamedata.firearm.PowderDampness.afterDryTurn(dampness));
    }

    private final java.util.Random mimicPerceptionRng = new java.util.Random();

    /**
     * Resolves what the player just bumped into, dropping the disguise of a mimic they
     * have already seen through.
     *
     * <p>Only a detected mimic can be struck this way. An unspotted one is still a chest
     * as far as the player knows, and swinging at it would be knowledge they have not
     * earned -- without that restriction a paranoid player could simply attack every
     * chest in the dungeon and skip the encounter entirely.
     *
     * @return the monster to strike, or null if this is not a fight.
     */
    private Monster resolveBumpTarget(int tx, int ty) {
        GridPoint2 tile = new GridPoint2(tx, ty);

        Monster existing = maze.getMonsters().get(tile);
        if (existing != null) {
            return existing;
        }

        Item chest = com.bpm.minotaur.gamedata.monster.MimicReveal.disguisedMimicAt(maze, tile);
        if (chest != null && chest.isMimicSeen() && combatManager != null) {
            return combatManager.revealMimicPreEmptively(tile, currentLevel);
        }
        return null;
    }

    private final com.badlogic.gdx.InputAdapter tomeStudyBreaker = new com.badlogic.gdx.InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            return breakTomeStudy();
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            return breakTomeStudy();
        }
    };

    private boolean breakTomeStudy() {
        if (player == null || player.getActiveTomeStudy() == null) {
            return false;
        }
        player.cancelTomeStudy(eventManager);
        return true;
    }

    /** Real-time seconds between the world turns of a Tome being studied in the field. */
    private static final float TOME_STUDY_SECONDS_PER_TURN = 0.25f;
    private float tomeStudyTimer;

    /**
     * Advances a channelled Tome study one world turn at a time, so monsters act
     * and the HUD bar fills while the player reads. Combat breaking out stops it.
     */
    private void updateTomeStudy(float delta) {
        if (player == null || player.getActiveTomeStudy() == null) {
            tomeStudyTimer = 0f;
            return;
        }
        tomeStudyTimer += delta;
        if (tomeStudyTimer < TOME_STUDY_SECONDS_PER_TURN) {
            return;
        }
        tomeStudyTimer = 0f;
        playerTurnTakesAction();
        player.advanceTomeStudy(maze, eventManager);
        // A monster that engaged without being seen (e.g. invisible) still ends the study.
        if (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE) {
            player.cancelTomeStudy(eventManager);
        }
    }

    /** World ticks a field Rest (H) advances per press, so a nearby monster can close in during it. */
    private static final int FIELD_REST_TICKS_PER_PRESS = 5;

    /**
     * Field rest & recover (H): a bounded, cooldown-gated HP top-up. Reintroduced
     * after the "way off again" balance investigation found that the original
     * R-bound rest was deleted wholesale (not deliberately redesigned) when R was
     * repurposed to open First Aid, leaving no field-accessible War Strength recovery
     * besides slow passive regen, rare potions, and shelter-only rest. The actual
     * heal/cooldown/cost logic lives on Player (attemptFieldRest) so it is
     * testable without a screen; this method only owns the same
     * multi-tick-with-interruption risk the original had, so a monster can still
     * close in mid-rest even though spamming it is no longer possible.
     */
    private void performFieldRestAction() {
        MusicManager.getInstance().setResting(true);
        Player.FieldRestResult result = player.attemptFieldRest(eventManager);
        if (!result.success) {
            MusicManager.getInstance().setResting(false);
            return; // The refusal message was already queued by attemptFieldRest.
        }

        int hpBeforeTick = player.getCurrentHP();
        int ticksElapsed = 0;
        for (int i = 0; i < FIELD_REST_TICKS_PER_PRESS; i++) {
            playerTurnTakesAction();
            ticksElapsed++;

            if (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE) {
                eventManager.addEvent(new GameEvent("Your rest is interrupted!", 2f));
                break;
            }
            if (player.getCurrentHP() < hpBeforeTick) {
                eventManager.addEvent(new GameEvent("Something attacks you as you rest!", 2f));
                break;
            }
            if (player.getCurrentHP() <= 0) {
                break;
            }
            hpBeforeTick = player.getCurrentHP();
        }
        // Each of the ticks just advanced also decremented the cooldown attemptFieldRest just
        // set, via the same per-turn hook every other action uses -- restore what this rest's
        // own ticks consumed so the intended full cooldown still holds afterward, whether the
        // loop ran to completion or was cut short by an interruption above.
        player.restoreFieldRestCooldown(ticksElapsed);
    }

    /**
     * Opens the field surgery & first aid triage interface (R).
     */
    public void openFirstAidModal() {
        if (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE) {
            eventManager.addEvent(new GameEvent("Cannot perform surgery while locked in combat!", 2f));
            return;
        }
        game.setScreen(new FirstAidModal(game, this, player, maze, soundManager, spellCastOverlay));
    }

    /**
     * Simulates passing world turns during field surgery and first aid.
     * Wandering monsters have opportunities to close in and violently interrupt the player.
     */
    public void simulateFirstAidTurnAdvance(int turns) {
        if (turns <= 0) return;
        int hpBefore = player.getCurrentHP();
        boolean interrupted = false;

        for (int i = 0; i < turns; i++) {
            playerTurnTakesAction();

            if (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE) {
                eventManager.addEvent(new GameEvent("AMBUSH! A monster lunges while you tend your wounds!", 3f));
                interrupted = true;
                break;
            }
            if (player.getCurrentHP() < hpBefore) {
                eventManager.addEvent(new GameEvent("INTERRUPTED! You take damage while tending wounds!", 3f));
                interrupted = true;
                break;
            }
            if (player.getCurrentHP() <= 0) {
                break;
            }
            hpBefore = player.getCurrentHP();
        }

        if (!interrupted && player.getCurrentHP() > 0) {
            hud.addMessage("Wound care complete. (" + turns + " turns elapsed)");
        }
    }

    private void performChunkTransition(Gate transitionGate) {
        if (player == null)
            return;

        // Collect hunting monsters that can operate doors within 8 tiles of transitionGate
        List<Monster> pursuers = new ArrayList<>();
        if (this.maze != null && transitionGate != null) {
            Vector2 gPos = transitionGate.getPosition();
            GridPoint2 gatePos = (gPos != null) ? new GridPoint2((int) gPos.x, (int) gPos.y) : null;
            if (gatePos == null) {
                for (Map.Entry<GridPoint2, Gate> entry : maze.getGates().entrySet()) {
                    if (entry.getValue() == transitionGate) {
                        gatePos = entry.getKey();
                        break;
                    }
                }
            }
            if (gatePos != null) {
                List<GridPoint2> toRemove = new ArrayList<>();
                for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
                    Monster m = entry.getValue();
                    if (m != null && m.getState() == Monster.MonsterState.HUNTING && m.canOperateDoors()) {
                        int dist = Math.abs(entry.getKey().x - gatePos.x) + Math.abs(entry.getKey().y - gatePos.y);
                        if (dist <= 8) {
                            pursuers.add(m);
                            toRemove.add(entry.getKey());
                            if (pursuers.size() >= 2) break;
                        }
                    }
                }
                for (GridPoint2 pt : toRemove) {
                    maze.getMonsters().remove(pt);
                }
            }
        }

        if (maze != null)
            worldManager.saveCurrentChunk(this.maze);
        Maze newMaze = worldManager.loadChunk(transitionGate.getTargetChunkId());
        if (newMaze == null) {
            eventManager.addEvent(new GameEvent("A strange force blocks your path.", 2f));
            transitionGate.close();
            return;
        }

        if (!pursuers.isEmpty()) {
            GridPoint2 originChunk = (worldManager != null) ? worldManager.getCurrentPlayerChunkId() : null;
            GridPoint2 targetChunk = transitionGate.getTargetChunkId();
            GridPoint2 arrivalTile = transitionGate.getTargetPlayerPos();
            MonsterPursuitManager.getInstance().registerGatePursuit(pursuers, originChunk, targetChunk, arrivalTile, this.currentLevel);
        }

        player.getPosition().set(transitionGate.getTargetPlayerPos().x + 0.5f,
                transitionGate.getTargetPlayerPos().y + 0.5f);
        worldManager.setCurrentChunk(transitionGate.getTargetChunkId());
        swapToChunk(newMaze);
        onChunkEntered();
    }

    @Override
    public void resize(int width, int height) {
        game.getViewport().update(width, height, true);
        postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
        if (hud != null) {
            // Fix: Always resize HUD to window size for correct input mapping
            hud.resize(width, height);
        }
        if (monsterDebugOverlay != null) {
            monsterDebugOverlay.resize(width, height);
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
        // --- Forward keyboard input to active EncounterWindow modal ---
        if (hud != null && hud.getEncounterWindow() != null && hud.getEncounterWindow().isVisible()) {
            hud.getEncounterWindow().handleInput(keycode);
            return true;
        }

        // --- Forward keyboard input to active ShopkeeperWindow modal ---
        if (hud != null && hud.getShopkeeperWindow() != null && hud.getShopkeeperWindow().isVisible()) {
            hud.getShopkeeperWindow().handleInput(keycode);
            return true;
        }

        // --- Forward keyboard input to active BonesAwakenModal ---
        if (hud != null && hud.getBonesAwakenModal() != null && hud.getBonesAwakenModal().isVisible()) {
            hud.getBonesAwakenModal().handleInput(keycode);
            return true;
        }

        if (keycode == Input.Keys.ESCAPE) {
            game.setScreen(new PauseScreen(game, this));
            return true;
        }

        if (worldManager == null || player == null || maze == null)
            return false;

        // --- FIX: Block Input if Sleeping ---
        if (player != null && player.getStatusManager().hasEffect(StatusEffectType.SLEEP)) {
            // Allow Debug Keys (F1-F12) to pass through?
            // F-keys are handled at the bottom of the method.
            // If we return true here, we block F-keys too unless we specifically allow them
            // or move the check.
            // Let's allow F-keys by checking if keycode is NOT an F-key.
            boolean isFunctionKey = (keycode >= Input.Keys.F1 && keycode <= Input.Keys.F12);
            if (!isFunctionKey) {
                // Only spam message if not holding down keys?
                // eventManager.addEvent(new GameEvent("You are asleep...", 0.5f));
                return true;
            }
        }
        // ------------------------------------

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
                                combatManager.playerUseItem(discoveryManager);
                                break;
                            case 4: // BLOCK
                                combatManager.playerGuard();
                                break;
                            case 5: // COOK
                                combatManager.endCombat();
                                game.setScreen(new CookingScreen(game, this, player, worldManager));
                                break;
                        }
                        return true;
                    case Input.Keys.Z:
                        player.castPreparedSpell(0, maze, eventManager, combatManager);
                        return true;
                    case Input.Keys.X:
                        player.castPreparedSpell(1, maze, eventManager, combatManager);
                        return true;
                    case Input.Keys.V:
                        player.castPreparedSpell(2, maze, eventManager, combatManager);
                        return true;
                    case Input.Keys.B:
                        player.castPreparedSpell(3, maze, eventManager, combatManager);
                        return true;
                    case Input.Keys.N:
                        player.castPreparedSpell(4, maze, eventManager, combatManager);
                        return true;
                    case Input.Keys.NUM_1:
                    case Input.Keys.NUMPAD_1:
                        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                            player.castPreparedSpell(0, maze, eventManager, combatManager);
                        } else {
                            combatManager.playerUseItem(0, discoveryManager);
                        }
                        return true;
                    case Input.Keys.NUM_2:
                    case Input.Keys.NUMPAD_2:
                        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                            player.castPreparedSpell(1, maze, eventManager, combatManager);
                        } else {
                            combatManager.playerUseItem(1, discoveryManager);
                        }
                        return true;
                    case Input.Keys.NUM_3:
                    case Input.Keys.NUMPAD_3:
                        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                            player.castPreparedSpell(2, maze, eventManager, combatManager);
                        } else {
                            combatManager.playerUseItem(2, discoveryManager);
                        }
                        return true;
                    case Input.Keys.NUM_4:
                    case Input.Keys.NUMPAD_4:
                        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                            player.castPreparedSpell(3, maze, eventManager, combatManager);
                        } else {
                            combatManager.playerUseItem(3, discoveryManager);
                        }
                        return true;
                    case Input.Keys.NUM_5:
                    case Input.Keys.NUMPAD_5:
                        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                            player.castPreparedSpell(4, maze, eventManager, combatManager);
                        } else {
                            combatManager.playerUseItem(4, discoveryManager);
                        }
                        return true;
                    case Input.Keys.NUM_6:
                    case Input.Keys.NUMPAD_6:
                        combatManager.playerUseItem(5, discoveryManager);
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
                    if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                        Vector2 dir = player.getFacing().getVector();
                        int fx = (int) (player.getPosition().x + dir.x);
                        int fy = (int) (player.getPosition().y + dir.y);
                        GridPoint2 targetTile = new GridPoint2(fx, fy);
                        GridPoint2 currentTile = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

                        // Check Decomposing Corpse (Hero Remains / NetHack bones)
                        Scenery scFront = (maze != null && maze.getScenery() != null) ? maze.getScenery().get(targetTile) : null;
                        Scenery scFeet = (maze != null && maze.getScenery() != null) ? maze.getScenery().get(currentTile) : null;
                        if ((scFront != null && scFront.isDecomposingCorpse()) || (scFeet != null && scFeet.isDecomposingCorpse())) {
                            interactWithWorldObject();
                            return true;
                        }

                        if (maze.getGameObjectAt(fx, fy) instanceof Window) {
                            interactWithWorldObject();
                            return true;
                        }
                        if (player.quickEquipOrConsumeGroundItem(maze, eventManager, this.discoveryManager, soundManager)) {
                            playerTurnTakesAction();
                            return true;
                        }
                    }
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
            if (keycode == Input.Keys.Z) {
                if (player.castPreparedSpell(0, maze, eventManager, combatManager)) {
                    combatManager.passTurnToMonster();
                }
                return true;
            }
            if (keycode == Input.Keys.X) {
                if (player.castPreparedSpell(1, maze, eventManager, combatManager)) {
                    combatManager.passTurnToMonster();
                }
                return true;
            }
            if (keycode == Input.Keys.V) {
                if (player.castPreparedSpell(2, maze, eventManager, combatManager)) {
                    combatManager.passTurnToMonster();
                }
                return true;
            }
            if (keycode == Input.Keys.B) {
                if (player.castPreparedSpell(3, maze, eventManager, combatManager)) {
                    combatManager.passTurnToMonster();
                }
                return true;
            }
            if (keycode == Input.Keys.N) {
                if (player.castPreparedSpell(4, maze, eventManager, combatManager)) {
                    combatManager.passTurnToMonster();
                }
                return true;
            }

            // --- Quick Slots 1-6 in Combat Turn ---
            boolean isShiftCombat = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
            if (keycode == Input.Keys.NUM_1 || keycode == Input.Keys.NUMPAD_1) {
                if (isShiftCombat) {
                    if (player.castPreparedSpell(0, maze, eventManager, combatManager)) {
                        combatManager.passTurnToMonster();
                    }
                } else {
                    combatManager.playerUseItem(0, discoveryManager);
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_2 || keycode == Input.Keys.NUMPAD_2) {
                if (isShiftCombat) {
                    if (player.castPreparedSpell(1, maze, eventManager, combatManager)) {
                        combatManager.passTurnToMonster();
                    }
                } else {
                    combatManager.playerUseItem(1, discoveryManager);
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_3 || keycode == Input.Keys.NUMPAD_3) {
                if (isShiftCombat) {
                    if (player.castPreparedSpell(2, maze, eventManager, combatManager)) {
                        combatManager.passTurnToMonster();
                    }
                } else {
                    combatManager.playerUseItem(2, discoveryManager);
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_4 || keycode == Input.Keys.NUMPAD_4) {
                if (isShiftCombat) {
                    if (player.castPreparedSpell(3, maze, eventManager, combatManager)) {
                        combatManager.passTurnToMonster();
                    }
                } else {
                    combatManager.playerUseItem(3, discoveryManager);
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_5 || keycode == Input.Keys.NUMPAD_5) {
                if (isShiftCombat) {
                    if (player.castPreparedSpell(4, maze, eventManager, combatManager)) {
                        combatManager.passTurnToMonster();
                    }
                } else {
                    combatManager.playerUseItem(4, discoveryManager);
                }
                return true;
            }
            if (keycode == Input.Keys.NUM_6 || keycode == Input.Keys.NUMPAD_6) {
                combatManager.playerUseItem(5, discoveryManager);
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
                }
            }
            // -----------------------

            // --- NEW: Open Combat Menu Logic ---
            if (keycode == Input.Keys.SPACE) {
                combatManager.openMenu();
                return true;
            }

            if (keycode == Input.Keys.A) {
                // Keep 'A' for quick attack or just duplicate space?
                // Let's map 'A' to open menu too? Or Instant Attack?
                // Prompt says "access combat menu ANYTIME... pressing space bar".
                // "The player can then attack anytime... and they can cast any spell".
                // This implies Space -> Menu -> Attack/Cast.
                // So "A" might be redundant or legacy.
                // Let's leave A as "Use Quick Slot" or "Ranged".
                // Actually existing logic handles A/SPACE as attack.
                // I should overriding SPACE to open menu.
                // And A can remain as Quick Slot / Ranged?
            }

            boolean isMovementKey = (keycode == Input.Keys.UP || keycode == Input.Keys.DOWN ||
                    keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT ||
                    keycode == Input.Keys.NUMPAD_8 || keycode == Input.Keys.NUMPAD_2 ||
                    keycode == Input.Keys.NUMPAD_4 || keycode == Input.Keys.NUMPAD_6);
            if (isMovementKey && weaponOverlay != null && weaponOverlay.isMovementLocked()) {
                // Directional movement locked during anticipation windup (~0.10s)
                return true;
            }

            if (keycode == Input.Keys.G) {
                // Shield Guard / Shield Bash
                Item leftHand = player.getInventory().getLeftHand();
                if (leftHand != null && leftHand.isShield()) {
                    Vector2 dir = player.getFacing().getVector();
                    int tx = (int) Math.floor(player.getPosition().x + dir.x);
                    int ty = (int) Math.floor(player.getPosition().y + dir.y);
                    combatManager.abandonReload();
                    Monster bumpTarget = resolveBumpTarget(tx, ty);
                    if (bumpTarget != null) {
                        combatManager.playerShieldBash(bumpTarget);
                    } else {
                        combatManager.playerGuard();
                        weaponOverlay.triggerGuardFlinch();
                    }
                    playerTurnTakesAction();
                    return true;
                }
            }

            if (keycode == Input.Keys.A) {
                // Ranged Attack or Thrown Weapon
                Item weapon = player.getInventory().getRightHand();
                if (weapon != null) {
                    if (weapon.isRanged()) {
                        boolean wasExploring =
                                combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE;
                        combatManager.playerAttackInstant();
                        // Firing costs a turn. Opening fire out of exploration used to be
                        // free: ammunition spent, the level woken, and no turn passed --
                        // so the reload never advanced either. In-combat shots pass their
                        // turn through the combat state machine instead.
                        if (wasExploring) {
                            playerTurnTakesAction();
                        }
                        return true;
                    } else if (weapon.isThrown()) {
                        if (combatManager.throwWeapon(weapon)) {
                            playerTurnTakesAction();
                            return true;
                        }
                    }
                }
            }

            switch (keycode) {
                case Input.Keys.PERIOD:
                    hud.addMessage("You wait...");
                    playerTurnTakesAction();
                    return true;
                case Input.Keys.UP: {
                    Vector2 dir = player.getFacing().getVector();
                    int tx = (int) Math.floor(player.getPosition().x + dir.x);
                    int ty = (int) Math.floor(player.getPosition().y + dir.y);

                    if (checkAndPerformSeamlessTransition(tx, ty)) {
                        return true;
                    }

                    combatManager.abandonReload();
                    Monster bumpTarget = resolveBumpTarget(tx, ty);

                    if (bumpTarget != null) {
                        combatManager.playerMeleeStrike(bumpTarget);
                    } else {
                        Object obj = maze.getGameObjectAt(tx, ty);
                        boolean isClosedDoor = (obj instanceof Door door) &&
                                (door.getState() == Door.DoorState.CLOSED || door.getState() == Door.DoorState.CLOSING);

                        if (isClosedDoor) {
                            // Bump into door to open it!
                            player.moveForward(maze, eventManager, gameMode, soundManager);
                        } else {
                            float prevX = player.getPosition().x;
                            float prevY = player.getPosition().y;
                            player.moveForward(maze, eventManager, gameMode, soundManager);
                            if (player.getPosition().x != prevX || player.getPosition().y != prevY) {
                                weaponOverlay.setWalking(true);
                            } else {
                                soundManager.playWeaponImpact(false);
                                weaponOverlay.triggerWallClank();
                                addTrauma(0.12f);
                                eventManager.addEvent(new GameEvent("Thud! You strike a solid wall.", 1.0f));
                            }
                        }
                    }
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                }
                case Input.Keys.DOWN: {
                    Vector2 dir = player.getFacing().getVector();
                    int tx = (int) Math.floor(player.getPosition().x - dir.x);
                    int ty = (int) Math.floor(player.getPosition().y - dir.y);

                    if (checkAndPerformSeamlessTransition(tx, ty)) {
                        return true;
                    }

                    combatManager.abandonReload();
                    Monster bumpTarget = resolveBumpTarget(tx, ty);
                    if (bumpTarget != null) {
                        combatManager.playerMeleeStrike(bumpTarget);
                    } else {
                        float prevX = player.getPosition().x;
                        float prevY = player.getPosition().y;
                        player.moveBackward(maze, eventManager, gameMode, soundManager);
                        if (player.getPosition().x != prevX || player.getPosition().y != prevY) {
                            weaponOverlay.setWalking(true);
                        } else {
                            soundManager.playWeaponImpact(false);
                            weaponOverlay.triggerWallClank();
                            addTrauma(0.12f);
                        }
                    }
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                }
                case Input.Keys.LEFT:
                    weaponOverlay.addTurnSway(-1.0f);
                    player.turnLeft();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                case Input.Keys.RIGHT:
                    weaponOverlay.addTurnSway(1.0f);
                    player.turnRight();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                case Input.Keys.O:
                    interactWithWorldObject();
                    return true;
                case Input.Keys.P:
                    pickupWorldItem();
                    return true;
                case Input.Keys.U:
                    player.useItem(player.getInventory().getRightHand(), eventManager, this.discoveryManager, maze);
                    // A Tome study spends its own turns as it is channelled.
                    if (player.getActiveTomeStudy() == null) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.Z:
                    if (player.castPreparedSpell(0, maze, eventManager, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.X:
                    if (player.castPreparedSpell(1, maze, eventManager, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.V:
                    if (player.castPreparedSpell(2, maze, eventManager, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.B:
                    if (player.castPreparedSpell(3, maze, eventManager, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.N:
                    if (player.castPreparedSpell(4, maze, eventManager, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.NUM_1:
                case Input.Keys.NUMPAD_1:
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        if (player.castPreparedSpell(0, maze, eventManager, combatManager)) {
                            playerTurnTakesAction();
                        }
                    } else if (player.useQuickSlot(0, eventManager, this.discoveryManager, maze, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.NUM_2:
                case Input.Keys.NUMPAD_2:
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        if (player.castPreparedSpell(1, maze, eventManager, combatManager)) {
                            playerTurnTakesAction();
                        }
                    } else if (player.useQuickSlot(1, eventManager, this.discoveryManager, maze, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.NUM_3:
                case Input.Keys.NUMPAD_3:
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        if (player.castPreparedSpell(2, maze, eventManager, combatManager)) {
                            playerTurnTakesAction();
                        }
                    } else if (player.useQuickSlot(2, eventManager, this.discoveryManager, maze, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.NUM_4:
                case Input.Keys.NUMPAD_4:
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        if (player.castPreparedSpell(3, maze, eventManager, combatManager)) {
                            playerTurnTakesAction();
                        }
                    } else if (player.useQuickSlot(3, eventManager, this.discoveryManager, maze, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.NUM_5:
                case Input.Keys.NUMPAD_5:
                    if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                        if (player.castPreparedSpell(4, maze, eventManager, combatManager)) {
                            playerTurnTakesAction();
                        }
                    } else if (player.useQuickSlot(4, eventManager, this.discoveryManager, maze, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.NUM_6:
                case Input.Keys.NUMPAD_6:
                    if (player.useQuickSlot(5, eventManager, this.discoveryManager, maze, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case Input.Keys.I:
                    InventoryScreen invScreen = new InventoryScreen(game, this, player, maze,
                            InventoryScreen.InventoryMode.NORMAL);
                    game.setScreen(invScreen);
                    return true;
                case Input.Keys.D:
                    ascendOrDescendLadder();
                    return true;
                case Input.Keys.R:
                    // Pressing R with nothing to tend is the exact muscle-memory moment that
                    // caused the "way off again" balance confusion -- R used to be the general
                    // rest-heal before it was repurposed to open First Aid, and that removal
                    // shipped with no in-fiction sign anything had changed. Turning this specific
                    // keypress into the teaching moment costs nothing extra: this branch already
                    // needed to check for an active injury to decide what to do.
                    if (player.getInjuryManager() != null && player.getInjuryManager().hasAnyInjuries()) {
                        openFirstAidModal();
                    } else {
                        eventManager.addEvent(new GameEvent(
                                "Nothing here needs tending. Press H to rest and recover strength.", 2.5f));
                    }
                    return true;
                case Input.Keys.H:
                    performFieldRestAction();
                    return true;
                case Input.Keys.C:
                    openFieldCrafting();
                    return true;
                case Input.Keys.K:
                    openSkillTree();
                    return true;
                case Input.Keys.J:
                    openFieldCooking();
                    return true;
            }
        }

        if (keycode == SettingsManager.getInstance().getKey("MAP")) {
            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                game.setScreen(new CastleMapScreen(game, player, maze, this));
            }
            return true;
        }

        if (keycode == SettingsManager.getInstance().getKey("SPELLBOOK")) {
            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE ||
                    combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
                game.setScreen(new SpellbookScreen(game, this, player, maze));
            }
            return true;
        }

        if (keycode == SettingsManager.getInstance().getKey("SKILL_TREE")) {
            openSkillTree();
            return true;
        }

        if (keycode == SettingsManager.getInstance().getKey("TOGGLE_LANTERN")) {
            boolean hasLantern = player.getInventory() != null &&
                    ((player.getInventory().getLeftHand() != null && player.getInventory().getLeftHand().getType() == Item.ItemType.BRASS_LANTERN)
                            || (player.getInventory().getRightHand() != null && player.getInventory().getRightHand().getType() == Item.ItemType.BRASS_LANTERN));
            if (hasLantern) {
                boolean lit = worldManager.getLightingManager().toggleLantern();
                soundManager.playDoorOpenSound();
                String msg = "Lantern flame " + (lit ? "kindled." : "snuffed out.");
                eventManager.addEvent(new GameEvent(msg, 2f));
                hud.addMessage(msg);
                needsAsciiRender = true;
            } else {
                eventManager.addEvent(new GameEvent("You must equip a Brass Lantern to use it.", 2f));
                hud.addMessage("You must equip a Brass Lantern to use it.");
            }
            return true;
        }

        switch (keycode)

        {
            case Input.Keys.TAB:
                hud.toggleControlsLegend();
                return true;
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
                debugManager.toggleRenderEngine();
                eventManager.addEvent(new GameEvent("Render Engine: " + debugManager.getRenderEngine(), 2f));
                return true;
            case Input.Keys.F10:
                if (worldManager.getWeatherManager() != null) {
                    worldManager.getWeatherManager().debugCycleIntensity();
                    eventManager.addEvent(new GameEvent(
                            "Debug Weather Intensity: " + worldManager.getWeatherManager().getCurrentIntensity(), 2f));
                }
                return true;
            case Input.Keys.F11:
                // Plain F11 tunes the weapon view; shift+F11 keeps the weather debug. Not
                // shift+F10: Tarmin2 opens the paperdoll editor on F10 whatever the modifiers.
                if (!Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
                    // Closing is handled by the panel itself, which sees F11 first while open.
                    weaponTunerPanel.open();
                    return true;
                }
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
                if (monsterDebugOverlay != null) {
                    monsterDebugOverlay.toggle();
                    if (monsterDebugOverlay.isVisible()) {
                        inputMultiplexer.addProcessor(0, monsterDebugOverlay.getStage());
                    } else {
                        inputMultiplexer.removeProcessor(monsterDebugOverlay.getStage());
                    }
                }
                return true;
            case Input.Keys.LEFT_BRACKET: {
                float fov = debugManager.adjustFov3d(-1.0f);
                eventManager.addEvent(new GameEvent(String.format("3D FOV: %.0f°", fov), 1.5f));
                return true;
            }
            case Input.Keys.RIGHT_BRACKET: {
                float fov = debugManager.adjustFov3d(1.0f);
                eventManager.addEvent(new GameEvent(String.format("3D FOV: %.0f°", fov), 1.5f));
                return true;
            }
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
        if (button == Input.Buttons.RIGHT && combatManager != null) {
            combatManager.setPlayerGuardStance(true);
            if (weaponOverlay != null) weaponOverlay.setGuarding(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT && combatManager != null) {
            combatManager.setPlayerGuardStance(false);
            if (weaponOverlay != null) weaponOverlay.setGuarding(false);
            return true;
        }
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
                if (player.getActiveTomeStudy() == null) {
                    playerTurnTakesAction();
                }
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
        if (player != null && worldManager != null) {
            SaveManager.getInstance().saveActiveSlot(player, worldManager);
        }
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
        if (spellPostProcessor != null) {
            spellPostProcessor.dispose();
        }
        if (stochasticManager != null)
            stochasticManager.dispose();
        postProcessBatch.dispose();
        if (monsterDebugOverlay != null) {
            monsterDebugOverlay.dispose();
        }
        world3DRenderer.dispose();
        laserBeamRenderer.dispose();
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

    private void updateSeamlessChunkLoading(float delta) {
        if (worldManager == null || player == null || maze == null)
            return;

        GridPoint2 currentC = worldManager.getCurrentPlayerChunkId();
        com.bpm.minotaur.generation.Biome currentBiome = worldManager.getBiomeManager().getBiome(currentC);

        if (currentBiome == null || !currentBiome.isSeamless())
            return;

        float x = player.getPosition().x;
        float y = player.getPosition().y;
        int width = maze.getWidth();
        int height = maze.getHeight();

        // Proximity Pre-Load (3 tiles)
        if (x < 3) {
            preloadChunk(currentC.x - 1, currentC.y);
        }
        if (x > width - 3) {
            preloadChunk(currentC.x + 1, currentC.y);
        }
        if (y < 3) {
            preloadChunk(currentC.x, currentC.y - 1);
        }
        if (y > height - 3) {
            preloadChunk(currentC.x, currentC.y + 1);
        }
    }

    private void preloadChunk(int x, int y) {
        GridPoint2 neighbor = new GridPoint2(x, y);
        if (!worldManager.getLoadedChunkIds().contains(neighbor)) {
            Gdx.app.log("Seamless", "Pre-loading neighbor chunk: " + neighbor);
            worldManager.requestLoadChunk(neighbor);
        }
    }

    /**
     * Checks if the target coordinate is Out of Bounds (OOB) and if so,
     * seamlessly transitions the player to the adjacent chunk.
     * 
     * @return true if a transition occurred.
     */
    private boolean checkAndPerformSeamlessTransition(int targetX, int targetY) {
        if (worldManager == null || player == null || maze == null)
            return false;

        GridPoint2 currentC = worldManager.getCurrentPlayerChunkId();
        com.bpm.minotaur.generation.Biome currentBiome = worldManager.getBiomeManager().getBiome(currentC);

        if (currentBiome == null || !currentBiome.isSeamless())
            return false; // Not seamless, treat as wall

        int width = maze.getWidth();
        int height = maze.getHeight();

        // Check if actually OOB
        if (targetX >= 0 && targetX < width && targetY >= 0 && targetY < height) {
            return false; // Not OOB, let normal movement logic handle it
        }

        GridPoint2 newChunkId = null;
        String directionLog = "";
        float newPlayerX = targetX; // Temp, will be wrapped
        float newPlayerY = targetY; // Temp, will be wrapped

        if (targetX < 0) {
            newChunkId = new GridPoint2(currentC.x - 1, currentC.y);
            directionLog = "WEST";
        } else if (targetX >= width) {
            newChunkId = new GridPoint2(currentC.x + 1, currentC.y);
            directionLog = "EAST";
        } else if (targetY < 0) {
            newChunkId = new GridPoint2(currentC.x, currentC.y - 1);
            directionLog = "SOUTH";
        } else if (targetY >= height) {
            newChunkId = new GridPoint2(currentC.x, currentC.y + 1);
            directionLog = "NORTH";
        }

        if (newChunkId != null) {
            Maze neighbor = worldManager.getChunk(newChunkId);
            if (neighbor == null)
                neighbor = worldManager.requestLoadChunk(newChunkId);

            if (neighbor != null) {
                Gdx.app.log("Seamless", "Crossing border " + directionLog + " to chunk " + newChunkId);

                if (directionLog.equals("WEST")) {
                    newPlayerX = neighbor.getWidth() - 0.5f;
                    // Keep Y relative? Yes, assuming aligned sizes.
                    newPlayerY = player.getPosition().y;
                } else if (directionLog.equals("EAST")) {
                    newPlayerX = 0.5f;
                    newPlayerY = player.getPosition().y;
                } else if (directionLog.equals("SOUTH")) {
                    newPlayerY = neighbor.getHeight() - 0.5f;
                    newPlayerX = player.getPosition().x;
                } else if (directionLog.equals("NORTH")) {
                    newPlayerY = 0.5f;
                    newPlayerX = player.getPosition().x;
                }

                worldManager.setCurrentChunk(newChunkId);
                swapToChunk(neighbor);
                player.getPosition().set(newPlayerX, newPlayerY);

                // Important: Trigger action and ascii render update manually since we bypassed
                // moveForward
                playerTurnTakesAction();
                needsAsciiRender = true;
                return true;
            }
        }
        return false;
    }

    public void interactWithWorldObject() {
        Vector2 v = player.getFacing().getVector();
        GridPoint2 target = new GridPoint2(
                (int) (player.getPosition().x + v.x),
                (int) (player.getPosition().y + v.y));

        GridPoint2 currentTile = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

        // MIMIC: this must run before Player.interact for two reasons. Every
        // REGULAR_CHEST is force-locked at construction (Item.java) and the container
        // branch returns early when the player has no key, so a check placed after it
        // would only ever fire for key-carrying players. And that same branch does
        // `maze.getItems().remove(targetTile)` -- letting a mimic reach it would delete
        // the creature outright, so this path always returns rather than falling through.
        Item mimicChest = com.bpm.minotaur.gamedata.monster.MimicReveal.disguisedMimicAt(maze, target);
        if (mimicChest != null) {
            if (combatManager != null
                    && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                if (mimicChest.isMimicSeen()) {
                    // Already seen through: reaching for it IS an attack, and the player
                    // keeps the initiative their perception check bought them.
                    Monster spotted = combatManager.revealMimicPreEmptively(target, maze.getLevel());
                    if (spotted != null) {
                        combatManager.playerMeleeStrike(spotted);
                    }
                } else {
                    // Deliberately does not advance the turn: the mimic's free blow is
                    // the cost of reaching for the chest, and letting the rest of the
                    // level act while the reveal state is blocking invites the two state
                    // machines to interleave.
                    combatManager.triggerMimicAmbush(target, maze.getLevel());
                }
            }
            needsAsciiRender = true;
            return;
        }

        // Check Decomposing Corpse (NetHack-style Bones remains)
        Scenery sceneryInFront = (maze != null && maze.getScenery() != null) ? maze.getScenery().get(target) : null;
        Scenery sceneryAtFeet = (maze != null && maze.getScenery() != null) ? maze.getScenery().get(currentTile) : null;
        Scenery corpseScenery = (sceneryInFront != null && sceneryInFront.isDecomposingCorpse()) ? sceneryInFront
                : (sceneryAtFeet != null && sceneryAtFeet.isDecomposingCorpse()) ? sceneryAtFeet : null;

        if (corpseScenery != null) {
            handleCorpseInteraction(corpseScenery);
            return;
        }

        Item itemOnTile = maze.getItems().get(currentTile);
        if (itemOnTile != null && itemOnTile.getType() == Item.ItemType.CORPSE) {
            CorpseLootScreen corpseScreen = new CorpseLootScreen(game, this, player, itemOnTile, maze);
            game.setScreen(corpseScreen);
            return;
        }

        Item itemInFront = maze.getItems().get(target);

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.CORPSE) {
            CorpseLootScreen corpseScreen = new CorpseLootScreen(game, this, player, itemInFront, maze);
            game.setScreen(corpseScreen);
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_ALTAR) {
            ShelterAltarScreen altarScreen = new ShelterAltarScreen(game, this, player);
            game.setScreen(altarScreen);
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_CHEST) {
            ShelterChest chest = ShelterChest.getInstance();
            if (chest.isEmpty()) {
                chest.load(game.getItemDataManager(), game.getAssetManager());
            }
            ShelterChestScreen chestScreen = new ShelterChestScreen(game, this, player, chest);
            game.setScreen(chestScreen);
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_SLEEPING_BAG) {
            player.getStats().setCurrentHP(player.getStats().getMaxHP());
            player.getStats().setCurrentMP(player.getStats().getMaxMP());
            player.getStatusManager().clearEffects();
            DoomManager.getInstance().resetExpeditionTurns();
            SaveManager.getInstance().saveActiveSlot(player, worldManager);
            SaveManager.getInstance().backupActiveSlot();
            soundManager.playDoorOpenSound();
            eventManager.addEvent(new GameEvent("You rest in the shelter bed. Health and mana restored. Game saved.", 3f));
            hud.addMessage("Rested in bed. HP/MP restored. Game saved.");
            playerTurnTakesAction();
            needsAsciiRender = true;
            tryDreamDimensionShift(DREAM_SHIFT_CHANCE_PER_BED_REST);
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_CRAFTING_BENCH) {
            try {
                if (craftingManager == null) {
                    craftingManager = new CraftingManager(game.getItemDataManager(), game.getAssetManager());
                }
                CraftingScreen craftingScreen = new CraftingScreen(game, this, player, craftingManager);
                game.setScreen(craftingScreen);
                return;
            } catch (Exception e) {
                Gdx.app.error("GameScreen", "Failed to open Crafting Bench", e);
            }
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_FIRE_POT) {
            try {
                CookingScreen cookingScreen = new CookingScreen(game, this, player, worldManager);
                game.setScreen(cookingScreen);
                return;
            } catch (Exception e) {
                Gdx.app.error("GameScreen", "Failed to open Cooking Hearth", e);
            }
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.BRASS_LANTERN) {
            com.bpm.minotaur.lighting.LightSource foundLight = null;
            for (com.bpm.minotaur.lighting.LightSource ls : maze.getLights()) {
                if (Math.abs(ls.getPosition().x - (target.x + 0.5f)) < 0.6f && Math.abs(ls.getPosition().y - (target.y + 0.5f)) < 0.6f) {
                    foundLight = ls;
                    break;
                }
            }
            if (foundLight != null) {
                foundLight.setActive(!foundLight.isActive());
                soundManager.playDoorOpenSound();
                eventManager.addEvent(new GameEvent("Lantern flame " + (foundLight.isActive() ? "kindled." : "snuffed out."), 2f));
                hud.addMessage("Lantern flame " + (foundLight.isActive() ? "kindled." : "snuffed out."));
            }
            playerTurnTakesAction();
            needsAsciiRender = true;
            return;
        }

        player.interact(maze, eventManager, soundManager, gameMode, worldManager);
        playerTurnTakesAction();
        needsAsciiRender = true;
    }

    /**
     * Tops the player up with any portable field kit they unlocked at the Altar but are no
     * longer carrying, reporting anything their pack had no room for.
     *
     * <p>The purchase path grants separately, at the Altar, so buying a station does not
     * require dying to collect the kit it teaches.
     */
    private void grantOwedFieldKits() {
        if (player == null) {
            return;
        }

        com.bpm.minotaur.gamedata.progression.FieldKitGrant.Result result =
                com.bpm.minotaur.gamedata.progression.FieldKitGrant.grantOwed(
                        com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().getUnlockedStations(),
                        player.getInventory(),
                        game.getItemDataManager(),
                        game.getAssetManager());

        // A full pack used to swallow the grant without a word, which is why the kits
        // seemed to appear only sometimes. Say so instead.
        if (hud != null) {
            for (Item kit : result.getNoRoom()) {
                hud.addMessage("No room for your " + kit.getDisplayName() + " -- free a pack slot.");
            }
        }
    }

    /**
     * Opens the crafting workshop in portable field-kit mode: requires a Crafting Toolkit
     * in the pack, and works with carried materials only (no Shelter Chest access).
     */
    public void openFieldCrafting() {
        if (!player.getInventory().hasItemOfType(Item.ItemType.CRAFTING_TOOLKIT)) {
            hud.addMessage("You need a Crafting Toolkit in your pack to work materials in the field.");
            return;
        }
        try {
            if (craftingManager == null) {
                craftingManager = new CraftingManager(game.getItemDataManager(), game.getAssetManager());
            }
            CraftingScreen craftingScreen = new CraftingScreen(game, this, player, craftingManager, true);
            game.setScreen(craftingScreen);
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to open field crafting", e);
        }
    }

    /**
     * Opens the cooking hearth in portable field-kit mode: requires a Cooking Kit in the
     * pack, and works with carried ingredients only (no Shelter Chest access).
     */
    public void openFieldCooking() {
        if (!player.getInventory().hasItemOfType(Item.ItemType.COOKING_KIT)) {
            hud.addMessage("You need a Cooking Kit in your pack to prepare meals in the field.");
            return;
        }
        try {
            CookingScreen cookingScreen = new CookingScreen(game, this, player, worldManager, true);
            game.setScreen(cookingScreen);
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to open field cooking", e);
        }
    }

    public void openSkillTree() {
        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE ||
                combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            game.setScreen(new SkillTreeScreen(game, this, player, maze));
        }
    }

    public void pickupWorldItem() {
        player.interactWithItem(maze, eventManager, soundManager, discoveryManager);
        playerTurnTakesAction();
    }

    public void quickEquipOrConsumeWorldItem() {
        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            if (player.quickEquipOrConsumeGroundItem(maze, eventManager, this.discoveryManager, soundManager)) {
                playerTurnTakesAction();
            }
        }
    }

    public void ascendOrDescendLadder() {
        GridPoint2 atFeet = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        GridPoint2 inFront = new GridPoint2(
                (int) (player.getPosition().x + player.getFacing().getVector().x),
                (int) (player.getPosition().y + player.getFacing().getVector().y));
        Ladder ladder = maze.getLadders().get(atFeet);
        if (ladder == null)
            ladder = maze.getLadders().get(inFront);

        if (ladder != null) {
            MusicManager.getInstance().playStinger("sounds/music/tarmin_enter_fx.ogg");
            GridPoint2 originLadderPos = new GridPoint2((int) ladder.getPosition().x, (int) ladder.getPosition().y);
            List<Monster> pursuers = new ArrayList<>();
            if (this.maze != null) {
                List<GridPoint2> toRemove = new ArrayList<>();
                for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
                    Monster m = entry.getValue();
                    if (m != null && m.getState() == Monster.MonsterState.HUNTING && m.canClimbLadders()) {
                        int dist = Math.abs(entry.getKey().x - originLadderPos.x) + Math.abs(entry.getKey().y - originLadderPos.y);
                        if (dist <= 6) {
                            pursuers.add(m);
                            toRemove.add(entry.getKey());
                            if (pursuers.size() >= 2) break;
                        }
                    }
                }
                for (GridPoint2 pt : toRemove) {
                    maze.getMonsters().remove(pt);
                }
            }

            int originLevel = this.currentLevel;

            if (ladder.getType() == Ladder.LadderType.DOWN) {
                GridPoint2 ladderPos = new GridPoint2((int) ladder.getPosition().x,
                        (int) ladder.getPosition().y);
                worldManager.descendLevel(ladderPos);
                this.currentLevel = worldManager.getCurrentLevel();
                worldManager.clearLoadedChunks();
                generateLevel(this.currentLevel);
                player.getPosition().set(ladderPos.x + 0.5f, ladderPos.y + 0.5f);
                hud.addMessage("Descended into Strata (Depth " + (currentLevel - 1) + ")");

                if (!pursuers.isEmpty()) {
                    MonsterPursuitManager.getInstance().registerLadderPursuit(pursuers, originLevel, this.currentLevel, ladderPos, true);
                }
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
                    GridPoint2 arrivalPos = (foundDownLadderPos != null)
                            ? new GridPoint2((int) foundDownLadderPos.x, (int) foundDownLadderPos.y)
                            : originLadderPos;
                    player.getPosition().set(arrivalPos.x + 0.5f, arrivalPos.y + 0.5f);
                    if (currentLevel == 1) {
                        hud.addMessage("Ascended to the Overland Surface.");
                    } else {
                        hud.addMessage("Ascended to Strata (Depth " + (currentLevel - 1) + ")");
                    }

                    if (!pursuers.isEmpty()) {
                        MonsterPursuitManager.getInstance().registerLadderPursuit(pursuers, originLevel, this.currentLevel, arrivalPos, false);
                    }
                } else {
                    hud.addMessage("You cannot ascend any higher.");
                }
            }
            updateMusicTrackForCurrentZone();
            playerTurnTakesAction();
        }
    }

    public Player getPlayer() {
        return player;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public SpellPostProcessor getSpellPostProcessor() {
        return spellPostProcessor;
    }

    public SpellCastOverlay getSpellCastOverlay() {
        return spellCastOverlay;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public void killPlayer() {
        if (player != null && player.getStats() != null) {
            player.getStats().setCurrentHP(0);
        }
        if (eventManager != null) {
            eventManager.addEvent(new com.bpm.minotaur.gamedata.GameEvent(com.bpm.minotaur.gamedata.GameEvent.EventType.PLAYER_DIED, null));
        }
    }

    @Override
    public void pause() {
        if (player != null && worldManager != null) {
            SaveManager.getInstance().saveActiveSlot(player, worldManager);
        }
    }

    private void checkBonesPresenceNotification() {
        if (gameMode != GameMode.ADVANCED || worldManager == null) return;
        BonesData activeBones = BonesManager.getInstance().getActiveFloorBones();
        if (activeBones != null && !BonesManager.getInstance().isNotificationTriggered()) {
            GridPoint2 curChunk = worldManager.getCurrentPlayerChunkId();
            if (curChunk != null && curChunk.x == activeBones.chunkX && curChunk.y == activeBones.chunkY) {
                BonesManager.getInstance().setNotificationTriggered(true);
                if (eventManager != null) {
                    eventManager.addEvent(new GameEvent("You sense a familiar ghostly presence...", 3.5f));
                }
                if (hud != null) {
                    hud.addMessage("A spectral chill prickles your skin... You sense a familiar ghostly presence.");
                }
                if (soundManager != null) {
                    soundManager.playThunder();
                }
            }
        }
    }

    private void handleCorpseInteraction(Scenery corpse) {
        BonesData bData = corpse.getBonesData();
        if (bData == null) {
            if (hud != null) hud.addMessage("The weathered remains crumble silently to dust.");
            return;
        }

        if (!bData.awakened) {
            if (hud != null && hud.getBonesAwakenModal() != null) {
                hud.getBonesAwakenModal().configureAndShow(
                        corpse, bData, maze, player, eventManager, soundManager, game.getAssetManager()
                );
            }
        } else if (!bData.defeated) {
            if (hud != null) hud.addMessage("The wrathful ghost of " + bData.playerName + " blocks you from disturbing the remains!");
            if (soundManager != null) {
                soundManager.playSound("player_spiritual_attack");
            }
        } else {
            GraveLootScreen lootScreen = new GraveLootScreen(game, this, player, bData);
            game.setScreen(lootScreen);
        }
    }
}
