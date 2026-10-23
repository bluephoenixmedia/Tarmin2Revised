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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector3;
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
    /** Gameplay keys are read through their Settings binding, never as literals (#171). */
    private final ActionKeys actionKeys = ActionKeys.live();
    /** Keys the open combat menu eats so they cannot walk or turn the player. */
    private static final java.util.List<String> MENU_SWALLOWED_ACTIONS = java.util.List.of(
            "MOVE_FORWARD", "MOVE_BACK", "TURN_LEFT", "TURN_RIGHT", "ATTACK");
    private static final java.util.List<String> MOVEMENT_ACTIONS = java.util.List.of(
            "MOVE_FORWARD", "MOVE_BACK", "TURN_LEFT", "TURN_RIGHT");
    private final FirstPersonRenderer firstPersonRenderer = new FirstPersonRenderer();
    private final World3DRenderer world3DRenderer = new World3DRenderer();
    private final com.bpm.minotaur.rendering.EmberOverlay emberOverlay =
            new com.bpm.minotaur.rendering.EmberOverlay();
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
    /**
     * The dungeon depth, read from WorldManager rather than cached.
     *
     * <p>This used to be a field kept in sync by hand at three call sites, and
     * respawnInShelter was not one of them: dying below the surface left it deep
     * forever, which made canRender3DSky false and stopped the skybox being
     * drawn at all -- the frame cleared to near-black and looked like a dark sky.
     */
    private int currentLevel() {
        return worldManager != null ? worldManager.getCurrentLevel() : 1;
    }
    private CombatManager combatManager;
    private CombatDiceOverlay combatDiceOverlay; // NEW
    private MonsterAiManager monsterAiManager;
    private DiscoveryManager discoveryManager;

    private FrameBuffer fbo;
    private ShaderProgram crtShader;
    private boolean useCrtFilter = false;
    private FilmicCasPostProcessor filmicCasPostProcessor;
    private SpellPostProcessor spellPostProcessor;
    private SpellCastOverlay spellCastOverlay;
    private final com.bpm.minotaur.managers.AlertMonitor alertMonitor = new com.bpm.minotaur.managers.AlertMonitor();
    private final com.bpm.minotaur.rendering.AlertOverlay alertOverlay = new com.bpm.minotaur.rendering.AlertOverlay();
    private final com.bpm.minotaur.rendering.ScreenFxOverlay screenFx = new com.bpm.minotaur.rendering.ScreenFxOverlay();
    private final com.bpm.minotaur.rendering.vfx.DamageFlash damageFlash = new com.bpm.minotaur.rendering.vfx.DamageFlash();
    private int lastPlayerHp = -1;
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

    /** World turns taken since this screen opened. */
    public int getTurnCount() {
        return turnCount;
    }

    // --- Death Idempotency & Run Tracking (NetHack Progression Reboot) ---
    private String activeExpeditionRunId = java.util.UUID.randomUUID().toString();
    private boolean isDeathTransitionTriggered = false;
    private final com.bpm.minotaur.managers.DeathWatch deathWatch =
            new com.bpm.minotaur.managers.DeathWatch();
    private final com.bpm.minotaur.rendering.DeathSequence deathSequence =
            new com.bpm.minotaur.rendering.DeathSequence();
    /**
     * The screen to hand over to once the blood is opaque. Built at the moment of death so any
     * loading cost is paid while the world is still visible, not during the swap.
     */
    private com.badlogic.gdx.Screen pendingDeathScreen;
    private boolean deathImpactCuePlayed;

    /** Spatter placement, fixed when the sequence starts so it does not strobe frame to frame. */
    private static final int DEATH_SPATTER_COUNT = 9;
    private final float[] deathSpatterX = new float[DEATH_SPATTER_COUNT];
    private final float[] deathSpatterY = new float[DEATH_SPATTER_COUNT];
    private final float[] deathSpatterSize = new float[DEATH_SPATTER_COUNT];
    private final float[] deathSpatterRot = new float[DEATH_SPATTER_COUNT];
    private final int[] deathSpatterTex = new int[DEATH_SPATTER_COUNT];

    private final TurnManager turnManager; // NEW

    // --- NEW: Visceral Feedback Components ---
    private FirstPersonWeaponOverlay weaponOverlay;
    private com.bpm.minotaur.rendering.weaponview.WeaponViewTunerPanel weaponTunerPanel;
    private final com.bpm.minotaur.rendering.gate.GateUvTunerPanel gateUvTunerPanel =
            new com.bpm.minotaur.rendering.gate.GateUvTunerPanel(world3DRenderer.getGateUv(), world3DRenderer::applyGateUv);
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

    // --- Periodic Auto-Save (Every 60 seconds) ---
    public static final float AUTOSAVE_INTERVAL_SECONDS = 60f;
    private float autoSaveTimer = 0f;
    private float ambientMonsterTimer = 18f;

    // --- Debug UI ---
    private DebugSpawnOverlay debugSpawnOverlay;
    private com.bpm.minotaur.debug.MonsterDebugOverlay monsterDebugOverlay;
    private com.badlogic.gdx.InputMultiplexer inputMultiplexer;

    public GameScreen(Tarmin2 game, int level, Difficulty difficulty, GameMode gameMode) {
        super(game);
        // Merchant claims are per-run session state. Clearing them here covers
        // every way a run can begin -- new game, load, and the capture screen --
        // where clearing only on respawn would leave a fresh game inheriting the
        // previous run's claims and never spawning a merchant at all.
        com.bpm.minotaur.generation.ShopkeeperTracker.reset();
        this.difficulty = difficulty;
        this.gameMode = gameMode;
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

        // A themed chunk seals its gates on entry; the player has to be told,
        // or a locked gate reads as a broken gate.
        this.worldManager.setSealAnnouncer((headline, goal) -> {
            if (eventManager != null) {
                eventManager.addEvent(new GameEvent(headline, 4.0f));
                eventManager.addEvent(new GameEvent(goal, 5.0f));
            }
        });

        this.monsterAiManager = new MonsterAiManager();
        this.monsterAiManager.setFactionMatrix(this.worldManager.getFactionMatrix());
        // The Maze's history is built now, so it hears the Doom Clock from the first turn.
        worldManager.getHistory();
        // Walking into one of a town's folk opens a conversation (Houses of the Maze T4.3).
        com.bpm.minotaur.gamedata.shelter.SealedGates.setTalker(this::talkTo);
        // Castle Tarmin's breach in a siege, or its opened gate, leads down into its halls (Living War W35, W36).
        com.bpm.minotaur.gamedata.shelter.SealedGates.setCastleWay(this::intoTheCastle);
        // A seal site's gate names the lord who holds the gash beneath it.
        com.bpm.minotaur.gamedata.shelter.SealedGates.setSealSiteVoice(() -> {
            int road = worldManager.getBiomeManager().getSealRoad(worldManager.getCurrentPlayerChunkId());
            return SealCourt.knockLine(worldManager.getHistory().world(), road,
                    com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance(),
                    com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().hasSeal(road));
        });
        // The Void's glyphs tell how Tarmin-Zul came through; reading one enters it in the chronicle.
        DimensionalManager.getInstance().setOnLoreRead(() -> {
            worldManager.getHistory().readFragment(com.bpm.minotaur.gamedata.history.FragmentKind.VOID_GLYPH);
            // The Void's glyphs also give up the myth, a layer at a time (#288).
            revealIfDue();
        });
        this.monsterAiManager.setOnPlayerNoticed(alertMonitor::noteMonsterNoticed);

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
        // The 3D engine draws projectiles itself. AnimationManager.render is only reached in the
        // raycaster branch, so without this every shot is invisible in the default renderer.
        world3DRenderer.setAnimationManager(animationManager);
        world3DRenderer.setProjectileAssets(game.getAssetManager());
        world3DRenderer.setWeaponOverlay(weaponOverlay);
        if (eventManager == null) {
            eventManager = new GameEventManager();
        }
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().setExpeditionEndListener(returnUnlockRoller);

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

        if (filmicCasPostProcessor == null) {
            filmicCasPostProcessor = new FilmicCasPostProcessor();
        }
        if (spellPostProcessor == null) {
            spellPostProcessor = new SpellPostProcessor();
        }
        if (spellCastOverlay == null) {
            spellCastOverlay = new SpellCastOverlay();
        }

        if (!hasLoadedLevel) {
            generateLevel(currentLevel());
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
            player.setItemPickupListener(this::onItemPickedUp);
            inputMultiplexer.clear();
            // First in line so a key or click anywhere, HUD included, only breaks a study's
            // concentration instead of also moving, attacking or pressing a button.
            inputMultiplexer.addProcessor(tomeStudyBreaker);
            // The F11 weapon tuner passes everything through while closed, and while open
            // has to see the arrows before the game turns them into movement.
            inputMultiplexer.addProcessor(weaponTunerPanel);
            inputMultiplexer.addProcessor(gateUvTunerPanel);
            inputMultiplexer.addProcessor(hud.stage); // UI First
            inputMultiplexer.addProcessor(this); // Game Second
            Gdx.input.setInputProcessor(inputMultiplexer);
        }
        if (weaponOverlay != null && player != null && player.getInventory() != null) {
            weaponOverlay.setEquipment(player.getInventory().getRightHand(), player.getInventory().getLeftHand());
        }
        updateMusicTrackForCurrentZone();
        checkArrivalHint();
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
        boolean isShelter = maze.isSanctuaryTile(px, py);

        if (isShelter) {
            MusicManager.getInstance().playShelterMusic("sounds/music/tarmin_ambient.ogg");
        } else {
            int strataDepth = Math.max(1, (currentLevel() - 1) / 3 + 1);
            if (strataDepth >= 3 || currentLevel() >= 4) {
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
        endWindowLook();
        warAudio.stop();
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
        com.bpm.minotaur.gamedata.gore.WoundDecalRegistry.getInstance().loadFromAtlas(goreAtlas);

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
        player.setItemPickupListener(this::onItemPickedUp);
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
            HintManager.getInstance().checkDivinity(eventManager);
        }

        if (worldManager != null && player != null && maze != null) {
            worldManager.updateExploration(player, maze);
            if (worldManager.getWeatherManager() != null && worldManager.getBiomeManager() != null) {
                Biome currentBiome = worldManager.getBiomeManager().getBiome(cid);
                worldManager.getWeatherManager().onBiomeChanged(currentBiome);
            }
        }
        checkArrivalHint();
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
        listenForKnell();
        tendKnellSky(delta);
        warAudio.update(delta, hud != null && hud.getKnellOverlay() != null && hud.getKnellOverlay().isSpeaking());
        MusicManager.getInstance().update(delta);

        updateDeathSequence(delta);
        updateWindowLook(delta);
        updateAutoSave(delta);
        updateAlert(delta);
        placePendingAllies();

        // Trigger Level-Up Attribute Allocation modal when safe (outside active combat)
        if (player != null && player.hasPendingLevelUpModal() && (combatManager == null || !combatManager.isInCombat())) {
            player.setPendingLevelUpModal(false);
            if (hud != null && hud.getLevelUpModal() != null && !hud.getLevelUpModal().isVisible()) {
                hud.showLevelUpModal();
            }
        }

        // --- VISCERAL HIT PAUSE ---
        if (hitPauseTimer > 0) {
            hitPauseTimer -= delta;
            // Freeze game logic during hit pause, but keep rendering the static frame (and
            // shake!)
            // We do NOT update time, combatManager, etc.
            if (hitPauseTimer <= 0)
                hitPauseTimer = 0;
        } else if (isDying()) {
            // Death is active: freeze world updates, combat updates, and turn processing while the death sequence runs
            time += delta;
            animationManager.update(delta);
            if (hud != null)
                hud.update(delta);
            eventManager.update(delta);
            handleSystemEvents();
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
            if (maze != null && maze.getMonsters() != null) {
                for (Monster m : maze.getMonsters().values()) {
                    if (m != null) m.updateAnimation(delta);
                }
            }
            if (combatManager != null && combatManager.getMonster() != null) {
                combatManager.getMonster().updateAnimation(delta);
            }
            if (maze != null && maze.getShopkeeper() != null) {
                maze.getShopkeeper().updateAnimation(delta);
            }
            if (maze != null)
                maze.update(delta);
            if (hud != null)
                hud.update(delta);
            eventManager.update(delta);
            handleSystemEvents();
            updateTomeStudy(delta);
            updateHearthLighting(delta);
            if (player != null && player.getPendingTomeChoice() != null) {
                game.setScreen(new SpellbookScreen(game, this, player, maze));
                return;
            }
            // Tarmin crossed with the last death: an old god must be spent before the soul wakes (#290).
            if (DoomManager.getInstance().isCrossingPending() && worldManager != null && worldManager.getHistory() != null
                    && !isDeathTransitionTriggered) {
                game.setScreen(new GodChoiceScreen(game, this, worldManager.getHistory()));
                return;
            }
            // Who the soul wakes in (#279), before the trait: a new game, a rebirth, or an older save.
            com.bpm.minotaur.managers.HistoryManager lifeHistory = worldManager != null ? worldManager.getHistory() : null;
            if (player != null && lifeHistory != null && lifeHistory.world() != null
                    && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                if (player.needsLife()) {
                    player.beginLife(lifeHistory.world(), new java.util.Random().nextLong(),
                            com.bpm.minotaur.managers.WorldLogManager.getInstance().lifeNames());
                }
                if (!isDeathTransitionTriggered) {
                    // The World Log follows whoever the player is: a new body, a reroll, a loaded save (#280).
                    com.bpm.minotaur.managers.WorldLogManager.getInstance().keepUpWith(player.getLife());
                }
                if (player.isLifeRevealPending()) {
                    game.setScreen(new RebirthScreen(game, this, player, lifeHistory.world(), soulName()));
                    return;
                }
            }
            if (player != null && player.needsTraitOffer()) {
                player.offerTraits(); // a new game, or a save from before traits
            }
            if (player != null && !player.getPendingTraitOffer().isEmpty()
                    && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                game.setScreen(new TraitChoiceScreen(game, this, player));
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

            if (soundManager != null) {
                soundManager.update(delta);
            }

            if (combatManager == null || combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                ambientMonsterTimer -= delta;
                if (ambientMonsterTimer <= 0f) {
                    ambientMonsterTimer = com.badlogic.gdx.math.MathUtils.random(15.0f, 30.0f);
                    triggerAmbientMonsterSound();
                }
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

        boolean renderToFbo = true; // Always render to FBO for Filmic CAS or CRT post-processing
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
                world3DRenderer.render(player, maze, currentViewport, worldManager, currentLevel(), gameMode, combatManager);
                // The merchant's Void chain laser only renders in the modern planar-3D
                // engine, whose camera-space convention (x, height, -y) LaserBeamRenderer
                // is built against; the retro raycaster does not get beam VFX.
                laserBeamRenderer.update(Gdx.graphics.getDeltaTime());
                laserBeamRenderer.render(world3DRenderer.getCamera());
            } else {
                firstPersonRenderer.render(shapeRenderer, player, maze, currentViewport, worldManager, currentLevel(),
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
                if (windowLook == null) weaponOverlay.renderRetro(shapeRenderer, game.getViewport());
            } else {
                shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
                weaponOverlay.renderTrails(shapeRenderer);

                game.getBatch().begin();
                // A handless form has no hand to hold the weapon in; a face at the bars has
                // them down at its sides.
                if (player.handsFree() && windowLook == null) {
                    weaponOverlay.render(game.getBatch(), game.getViewport());
                }
                weaponTunerPanel.render(game.getBatch(), font, game.getViewport());
                gateUvTunerPanel.render(game.getBatch(), font, game.getViewport());
                game.getBatch().end();
            }

            damageFlash.render(shapeRenderer, game.getViewport());
            game.getBatch().begin();
            alertOverlay.render(game.getBatch(), game.getViewport());
            screenFx.render(game.getBatch(), game.getViewport());
            game.getBatch().end();
            renderDebugLegend(); // runs its own shape and batch passes, so only between batches

            if (combatManager.getAttackIndicatorMonster() != null
                    && combatManager.getAttackIndicatorVariant() == CombatManager.AttackIndicatorVariant.SCREEN_SLASH) {
                shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
                renderScreenSlashOverlay(combatManager.getAttackIndicatorProgress());
            }

            shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
            firstPersonRenderer.renderTemperatureVignette(shapeRenderer, game.getViewport(),
                    player.getStats().getBodyTemperature(), time);

            renderObscuringFogWash();

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

        // --- HIGH-DOOM EMBERS (in the play area, over the world, inside the CRT pass) ---
        // Only under open sky on the surface: these are cinders falling out of the burning sky,
        // so they have no business underground or beneath a roof.
        float emberDoom = 0f;
        if (player != null && maze != null && currentLevel() == 1
                && !maze.isIndoors((int) player.getPosition().x, (int) player.getPosition().y)) {
            emberDoom = com.badlogic.gdx.math.MathUtils.clamp(
                    com.bpm.minotaur.managers.DoomManager.getInstance().getBridgeIntegrity() / 100f,
                    0f, 1f);
        }
        emberOverlay.update(Gdx.graphics.getDeltaTime(), emberDoom, VIRTUAL_WIDTH, HUD_HEIGHT, GAME_HEIGHT);
        if (emberDoom > 0f) {
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            emberOverlay.render(game.getBatch(), emberDoom);
            game.getBatch().end();
        }

        if (renderToFbo) {
            fbo.end();
            game.getViewport().apply();
            ScreenUtils.clear(0, 0, 0, 1);
            postProcessBatch.setProjectionMatrix(game.getViewport().getCamera().combined);
            postProcessBatch.begin();
            if (useCrtFilter) {
                postProcessBatch.setShader(crtShader);
                crtShader.setUniformf("u_time", time);
                crtShader.setUniformf("u_heatShimmer", heatShimmer());
                crtShader.setUniformf("u_waterMist", waterMist());
                if (spellPostProcessor != null) {
                    spellPostProcessor.applyUniforms(crtShader, time);
                }
                // Draw FBO offset by HUD_HEIGHT (180)
                postProcessBatch.draw(fbo.getColorBufferTexture(), 0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT, 0, 0, 1, 1);
            } else if (filmicCasPostProcessor != null && filmicCasPostProcessor.isCompiled()) {
                filmicCasPostProcessor.render(postProcessBatch, fbo.getColorBufferTexture(), 0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT, time, spellPostProcessor);
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

        // The HUD clears early so the fall has the whole frame. A full HP bar reading 0/16 while
        // you collapse is clutter, not poignancy.
        float hudAlpha = deathSequence.isActive() ? deathSequence.getHudAlpha() : 1f;
        if (hud != null && hudAlpha > 0.01f) {
            // --- NEW: Sync Combat Menu Visibility ---
            if (combatManager != null && hud.combatMenu != null) {
                hud.combatMenu.setVisible(combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU);
            }
            hud.setGlobalAlpha(hudAlpha);
            hud.render();
        }

        // --- DEATH BLOOD WIPE (over everything, including the HUD) ---
        if (deathSequence.isActive()) {
            renderDeathBlood(deathSequence.getBloodAlpha());
        }

        DivinityOrbManager.getInstance().render(shapeRenderer, game.getViewport());

        game.getBatch().setProjectionMatrix(currentViewport.getCamera().combined);
        game.getBatch().begin();
        animationManager.renderDamageText(game.getBatch(), currentViewport, player, firstPersonRenderer.getDepthBuffer());
        animationManager.renderMonsterSpeech(game.getBatch(), currentViewport, player, maze, firstPersonRenderer.getDepthBuffer());
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
        if (!com.bpm.minotaur.gamedata.gore.GoreLevel.current().enabled()) return;
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

    /** Midday heat over the surface desert, for the CRT pass; 0 anywhere else. */
    private float heatShimmer() {
        if (worldManager == null || maze == null || player == null
                || maze.getBiome() != com.bpm.minotaur.generation.Biome.DESERT
                || maze.getLevel() != 1 || worldManager.getDayNightManager() == null
                || maze.isIndoors((int) player.getPosition().x, (int) player.getPosition().y)) {
            return 0f;
        }
        com.bpm.minotaur.weather.WeatherManager wm = worldManager.getWeatherManager();
        return com.bpm.minotaur.rendering.DesertAtmosphere.heatShimmer(
                worldManager.getDayNightManager().getTimeOfDay(),
                (wm != null) ? wm.getCurrentWeather() : null);
    }

    /** Low rolling ground mist over the surface Lakelands, for the CRT pass; 0 anywhere else. */
    private float waterMist() {
        if (worldManager == null || maze == null || player == null
                || maze.getBiome() != com.bpm.minotaur.generation.Biome.LAKELANDS
                || maze.getLevel() != 1 || worldManager.getDayNightManager() == null
                || maze.isIndoors((int) player.getPosition().x, (int) player.getPosition().y)) {
            return 0f;
        }
        com.bpm.minotaur.weather.WeatherManager wm = worldManager.getWeatherManager();
        return com.bpm.minotaur.rendering.LakelandsAtmosphere.waterMist(
                worldManager.getDayNightManager().getTimeOfDay(),
                (wm != null) ? wm.getCurrentWeather() : null);
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

        while ((event = eventManager.findAndConsume(GameEvent.EventType.BIOME_PORTAL_WARP)) != null) {
            if (event.payload instanceof WorldManager.PortalWarp) {
                WorldManager.PortalWarp warp = (WorldManager.PortalWarp) event.payload;
                Gdx.app.log("GameScreen", "Biome portal warp to chunk " + warp.chunkId);

                collectEscortAllies();
                if (maze != null) {
                    worldManager.saveCurrentChunk(maze);
                }
                Maze destination = worldManager.loadChunk(warp.chunkId);
                if (destination == null) {
                    hud.addMessage("The rift collapses before you can step through.");
                } else {
                    player.getPosition().set(warp.playerPos.x + 0.5f, warp.playerPos.y + 0.5f);
                    worldManager.setCurrentChunk(warp.chunkId);
                    swapToChunk(destination);
                    // The shear, but not the dimension swap: triggerDimensionalWarp
                    // forces RETRO and the raycaster, which is the Void's identity,
                    // not a travel effect. Walking into a forest must not drop the
                    // player into ASCII rendering.
                    debugManager.triggerTravelWarp();
                    soundManager.playDimensionalWarpSound();
                    onChunkEntered();
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
                    // A statue is an old god's shrine, dark if its god is spent (#287).
                    com.bpm.minotaur.gamedata.history.OldGod god =
                            com.bpm.minotaur.gamedata.history.OldGod.forEncounter(encounter.id);
                    if (god != null) {
                        boolean dark = worldManager != null && worldManager.getHistory() != null
                                && worldManager.getHistory().world().isSpent(god);
                        encounter = com.bpm.minotaur.gamedata.history.Shrine.view(encounter, god, dark,
                                com.bpm.minotaur.gamedata.history.Reveal.godsKnown(
                                        com.bpm.minotaur.managers.WorldLogManager.getInstance().log()));
                    }
                    hud.getEncounterWindow().show(encounter);
                }
            }
        }

        while ((event = eventManager.findAndConsume(GameEvent.EventType.CHOICE_EVENT_TRIGGERED)) != null) {
            openChoiceEvent((String) event.payload);
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
                        false, player.getPosition(), currentLevel(), worldManager.getCurrentPlayerChunkId());
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

        // Central death check. Damage sources each used to be responsible for noticing they had
        // killed the player, and InjuryManager never did, so bleeding out did not kill you. This
        // runs regardless of cause; duplicate events are already harmless.
        if (player != null && player.getStats() != null
                && deathWatch.observe(player.getStats().getCurrentHP())) {
            eventManager.addEvent(new GameEvent(GameEvent.EventType.PLAYER_DIED, null));
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

            // 1. Advance Doom Clock ("Tarmin's Hunger") -- idempotent per expedition run
            String killer = killerForTheLog();
            if (DoomManager.getInstance().recordDeath(activeExpeditionRunId)) {
                // Once per run, like the clock: the Maze remembers the fallen, and who felled them.
                com.bpm.minotaur.gamedata.history.Nemesis nemesis =
                        worldManager.getHistory().recordSeekerFell(seekerDeath(killer, curLvl));
                // A killer the history has just named is remembered by that name (#283), and keeps
                // what the life had in hand and on its back, to give up when it falls (#284).
                if (nemesis != null) {
                    killer = nemesis.name;
                    hoardGearOf(nemesis.id);
                }
                // The life's record closes with its death (#280).
                com.bpm.minotaur.managers.WorldLogManager.getInstance().die(epitaph, killer, "level " + curLvl,
                        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().getExpeditionTurns());
                // The Houses it touched, and the Knell at a new stage, tell the life (#285).
                com.bpm.minotaur.managers.WorldLogManager.getInstance().tellLastLife(
                        worldManager.getHistory().world(), worldManager.getHistory().lastLifeDeeds(),
                        DoomManager.getInstance().getDeathCount());
                if (DoomManager.getInstance().isCrossingPending()) {
                    // A death while the Warden stood: Tarmin crosses (#276, #290).
                    worldManager.getHistory().recordCrossing();
                    if (com.bpm.minotaur.gamedata.history.OldGod.spendable(worldManager.getHistory().world()).isEmpty()) {
                        lastCrossing = com.bpm.minotaur.managers.WorldLogManager.getInstance()
                                .lastCrossing(worldManager.getHistory());
                        DoomManager.getInstance().clearCrossing();
                    }
                }
            }

            // Record Bones File for Advanced Mode (full player snapshot before item retention)
            if (gameMode == GameMode.ADVANCED) {
                BonesManager.getInstance().recordBonesOnDeath(player, curLvl, epitaph, gameMode, killer);
            }
            int deaths = DoomManager.getInstance().getDeathCount();
            float bridge = DoomManager.getInstance().getBridgeIntegrity();
            Gdx.app.log("GameScreen", "Doom updated on death. Count: " + deaths + " (" + (int) bridge + "%)");

            // 2. The Last Crossing (#278, #290): no old god was left to hold Tarmin. The world ends;
            //    the slot is finished, and can only be read in the World Log from now on.
            if (lastCrossing != null) {
                Gdx.app.log("GameScreen", "The Last Crossing. The slot is finished.");
                SaveManager.getInstance().markActiveSlotFinished();
                beginDeathSequence(new GameOverScreen(game, lastCrossing));
                return;
            }

            if (combatManager != null) {
                combatManager.endCombat();
            }

            // 3. Full Expedition Reset: lose equipped gear, weapons, offhand, backpack items,
            //    and quickslots upon death. Only items previously secured in the Shelter Chest survive.
            int lostCount = 0;
            if (player.getInventory().getRightHand() != null) {
                lostCount++;
                player.getInventory().setRightHand(null);
            }
            if (player.getInventory().getLeftHand() != null) {
                lostCount++;
                player.getInventory().setLeftHand(null);
            }
            if (player.getEquipment() != null) {
                lostCount += player.getEquipment().getAllEquipped().size();
                player.getEquipment().stripAllEquipped();
            }

            lostCount += player.getInventory().getMainInventory().size();
            player.getInventory().getMainInventory().clear();

            for (Item qs : player.getInventory().getQuickSlots()) {
                if (qs != null) lostCount++;
            }
            player.getInventory().clearQuickSlots();

            int retainedCount = 0;

            // Put the stripped character on disk now. The death screen can be left by quitting
            // or crashing, and the last pre-death autosave would otherwise return the whole kit
            // at the next load. The flag makes that load finish the respawn.
            SaveManager.getInstance().setRespawnPending(true);
            SaveManager.getInstance().saveActiveSlot(player, worldManager);

            // 4. Finalize run telemetry & build the run epitaph
            com.bpm.minotaur.telemetry.TelemetryManager telemetry = com.bpm.minotaur.telemetry.TelemetryManager.getInstance();
            if (player.getInjuryManager() != null) {
                telemetry.setBleedDamageTaken(player.getInjuryManager().getBleedDamageThisRun());
            }
            String epitaphCause = buildEpitaph(telemetry);
            int depthReached = Math.max(1, telemetry.getStrataReached());
            int monstersSlain = telemetry.getTotalMonstersKilled();
            int divinitiesEarnedThisRun = telemetry.getDivinitiesEarned();

            // Roll discoveries unlocked for future expeditions
            // Types, not just names: the death screen shows each discovery as a card with its
            // icon, and icons are keyed on the item type.
            java.util.List<com.bpm.minotaur.gamedata.item.Item.ItemType> newUnlockTypes =
                    com.bpm.minotaur.managers.UnlockManager.getInstance()
                            .rollRunUnlockTypes(telemetry, telemetry.getExpeditionMaxDepth());
            telemetry.recordUnlocksEarned(newUnlockTypes != null ? newUnlockTypes.size() : 0);
            telemetry.exportRun(epitaphCause);
            // 5. Play visceral death audio and transition to PlayerDeathScreen
            PlayerDeathScreen deathScreen = new PlayerDeathScreen(game, this, deaths, 50, bridge,
                    lostCount, retainedCount, epitaphCause, epitaphCause, depthReached, monstersSlain,
                    divinitiesEarnedThisRun, null, newUnlockTypes);
            beginDeathSequence(deathScreen);
            return;
        }
    }

    /**
     * Rolls the unlocks a safe return earned (#184) while telemetry still holds the
     * expedition's counters. A death rolls its own, for the death screen, so only a return
     * is handled here; the log carries the news because no screen follows a return.
     */
    private final com.bpm.minotaur.telemetry.TelemetryManager.ExpeditionEndListener returnUnlockRoller = outcome -> {
        if (outcome != com.bpm.minotaur.telemetry.TelemetryManager.Outcome.RETURNED) {
            return;
        }
        com.bpm.minotaur.telemetry.TelemetryManager telemetry = com.bpm.minotaur.telemetry.TelemetryManager.getInstance();
        com.bpm.minotaur.managers.UnlockManager unlocks = com.bpm.minotaur.managers.UnlockManager.getInstance();
        java.util.List<com.bpm.minotaur.gamedata.item.Item.ItemType> earned =
                unlocks.rollExpeditionUnlockTypes(telemetry, telemetry.getExpeditionMaxDepth(), outcome);
        telemetry.recordUnlocksEarned(earned.size());
        if (eventManager == null) {
            return;
        }
        for (com.bpm.minotaur.gamedata.item.Item.ItemType type : earned) {
            eventManager.addEvent(new GameEvent(
                    "Your return is remembered: " + unlocks.displayNameFor(type) + " now appears in the maze.", 4f));
        }
    };

    /**
     * Starts the death cinematic and defers the screen swap until the blood is opaque.
     *
     * <p>The target screen is constructed here, while the world is still on display, so that any
     * cost of building it is paid before the handover rather than showing as a hitch during it.
     */
    private void beginDeathSequence(com.badlogic.gdx.Screen target) {
        pendingDeathScreen = target;
        deathImpactCuePlayed = false;

        boolean violent = player != null && player.wasLastDamageViolent();
        float roll = com.badlogic.gdx.math.MathUtils.random(25f, 60f)
                * (com.badlogic.gdx.math.MathUtils.randomBoolean() ? 1f : -1f);
        deathSequence.begin(violent, roll);
        world3DRenderer.setDeathSequence(deathSequence);

        float vw = game.getViewport().getWorldWidth();
        float vh = game.getViewport().getWorldHeight();
        for (int i = 0; i < DEATH_SPATTER_COUNT; i++) {
            deathSpatterX[i] = com.badlogic.gdx.math.MathUtils.random(-0.1f, 0.9f) * vw;
            deathSpatterY[i] = com.badlogic.gdx.math.MathUtils.random(-0.1f, 0.9f) * vh;
            deathSpatterSize[i] = com.badlogic.gdx.math.MathUtils.random(0.30f, 0.85f) * vw;
            deathSpatterRot[i] = com.badlogic.gdx.math.MathUtils.random(0f, 360f);
            deathSpatterTex[i] = visorDropletTextures.isEmpty()
                    ? 0
                    : com.badlogic.gdx.math.MathUtils.random(visorDropletTextures.size() - 1);
        }

        soundManager.playDeathGrunt(violent);
    }

    /**
     * Advances the death cinematic and performs the handover.
     *
     * <p>Called every frame while dying. Actors are already still -- the game is turn-based and
     * input is refused below -- so nothing needs explicit freezing, but ambience keeps running:
     * rain stopped in mid-air reads as a crash rather than a death.
     */
    private void updateDeathSequence(float delta) {
        if (!deathSequence.isActive()) return;

        deathSequence.update(delta);

        if (!deathImpactCuePlayed && deathSequence.hasLanded()) {
            deathImpactCuePlayed = true;
            soundManager.playDeathImpact();
        }

        if (deathSequence.shouldHandOver() && pendingDeathScreen != null) {
            com.badlogic.gdx.Screen target = pendingDeathScreen;
            pendingDeathScreen = null;
            soundManager.playDeathReveal();
            if (target instanceof PlayerDeathScreen) {
                // Hand the blood over so it recedes onto the new screen instead of cutting.
                ((PlayerDeathScreen) target).beginBloodReveal();
            }
            world3DRenderer.setDeathSequence(null);
            deathSequence.reset();
            game.setScreen(target);
        }
    }

    /** Feeds the alert monitor what the player's state is, and flashes the symbol when it says to. */
    private void updateAlert(float delta) {
        alertOverlay.update(delta);
        screenFx.update(delta);
        damageFlash.update(delta);
        if (player == null || player.getStats() == null) {
            return;
        }
        if (isDying()) {
            // What was pending belongs to the life that is ending; the respawn takes a new baseline.
            alertMonitor.reset();
            damageFlash.reset();
            lastPlayerHp = -1;
            return;
        }
        PlayerStats stats = player.getStats();

        // A short red flash at the screen edges whenever the player loses health.
        int hp = stats.getCurrentHP();
        if (lastPlayerHp >= 0 && hp < lastPlayerHp) {
            damageFlash.trigger((lastPlayerHp - hp) / (float) Math.max(1, stats.getMaxHP()));
        }
        lastPlayerHp = hp;

        com.bpm.minotaur.managers.AlertMonitor.Snapshot snapshot = com.bpm.minotaur.managers.AlertMonitor.Snapshot.of(stats);
        com.bpm.minotaur.managers.AlertMonitor.Reason alert = alertMonitor.update(delta, snapshot);
        if (alert != null) {
            alertOverlay.trigger();
            soundManager.playEvent("alert");
            // #173: the flash says something is wrong; the chronicle says what.
            if (hud != null) {
                hud.addMessage(com.bpm.minotaur.managers.AlertMonitor.message(alert, snapshot));
            }
            if (alert == com.bpm.minotaur.managers.AlertMonitor.Reason.HUNGER) {
                HintManager.getInstance().checkHunger(eventManager);
            } else if (alert == com.bpm.minotaur.managers.AlertMonitor.Reason.THIRST) {
                HintManager.getInstance().checkThirst(eventManager);
            }
        }
        checkMonsterHints();
    }

    private void checkArrivalHint() {
        if (player != null && maze != null && eventManager != null) {
            int px = (int) player.getPosition().x;
            int py = (int) player.getPosition().y;
            if (maze.isSanctuaryTile(px, py)) {
                HintManager.getInstance().checkArrival(eventManager);
            }
        }
    }

    private void checkMonsterHints() {
        HintManager hm = HintManager.getInstance();
        boolean needBad = !hm.hasSeen(HintManager.HintId.BAD_MONSTER);
        boolean needNasty = !hm.hasSeen(HintManager.HintId.NASTY_MONSTER);
        if (!needBad && !needNasty) return;
        if (!SettingsManager.getInstance().isShowHints()) return;
        if (maze == null || player == null || eventManager == null) return;

        Vector2 from = player.getPosition();
        for (Monster monster : maze.getMonsters().values()) {
            if (monster == null || !monster.isAlive() || monster.getTameness() > 0
                    || monster.isHidden() || monster.isInvisible()) {
                continue;
            }
            Vector2 at = monster.getPosition();
            if (from.dst(at) <= com.bpm.minotaur.gamedata.monster.HostileSight.SIGHT_RANGE_TILES
                    && com.bpm.minotaur.gamedata.laser.LaserBurst.hasLineOfSight(maze, from, at)) {
                Monster.MonsterType mt = monster.getType();
                if (mt != null) {
                    if (needBad && mt.getCategory() == Monster.Category.BAD) {
                        hm.checkBadMonster(monster, eventManager);
                        needBad = false;
                    } else if (needNasty && mt.getCategory() == Monster.Category.NASTY) {
                        hm.checkNastyMonster(monster, eventManager);
                        needNasty = false;
                    }
                    if (!needBad && !needNasty) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Periodic auto-save every 60 seconds during active gameplay.
     * Guarantees players never lose progress on unexpected crashes or hangs.
     */

    public void updateAutoSave(float delta) {
        if (player == null || worldManager == null) return;
        if (deathSequence != null && deathSequence.isActive()) return;
        if (player.getStats() != null && player.getStats().getCurrentHP() <= 0) return;

        autoSaveTimer += delta;
        if (autoSaveTimer >= AUTOSAVE_INTERVAL_SECONDS) {
            autoSaveTimer = 0f;
            try {
                SaveManager.getInstance().saveActiveSlot(player, worldManager);
                Gdx.app.log("GameScreen", "Periodic autosave completed.");
            } catch (Exception e) {
                Gdx.app.error("GameScreen", "Periodic autosave failed", e);
            }
        }
    }

    public float getAutoSaveTimer() {
        return autoSaveTimer;
    }

    public void setAutoSaveTimer(float autoSaveTimer) {
        this.autoSaveTimer = autoSaveTimer;
    }

    public void triggerAutoSave() {
        if (player != null && worldManager != null) {
            SaveManager.getInstance().saveActiveSlot(player, worldManager);
            autoSaveTimer = 0f;
        }
    }

    /**
     * The raycaster's version of standing in fog: a flat grey wash over the play area.
     *
     * <p>The 3D engine collapses its own distance fog for this, which is both cheaper and
     * better-looking, so this only runs for the raycaster. Volumetric fog was not built twice
     * -- the deliberate trade is that the retro engine gets the mechanics exactly and a plainer
     * picture of them. What it must not be is invisible: a cloud that blinds you without
     * showing itself is a bug report.
     */
    private void renderObscuringFogWash() {
        if (debugManager.getRenderEngine() == DebugManager.RenderEngine.PLANAR_3D) {
            return;
        }
        if (player == null || maze == null
                || !maze.isObscured((int) player.getPosition().x, (int) player.getPosition().y)) {
            return;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.62f, 0.64f, 0.67f, 0.66f);
        shapeRenderer.rect(0, HUD_HEIGHT, VIRTUAL_WIDTH, GAME_HEIGHT);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * The blood taking the screen.
     *
     * <p>Two layers, because either alone falls short: the deepening wash carries the blacking-out
     * read and guarantees full opacity on schedule for the handover, while the spatter is what
     * makes it blood rather than a red filter. Spatter arrives in staggered waves so the screen
     * fills rather than flashing.
     */
    private void renderDeathBlood(float alpha) {
        if (alpha <= 0f) return;

        float vw = game.getViewport().getWorldWidth();
        float vh = game.getViewport().getWorldHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);

        if (!visorDropletTextures.isEmpty()) {
            game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
            game.getBatch().begin();
            for (int i = 0; i < DEATH_SPATTER_COUNT; i++) {
                float arrival = (i / (float) DEATH_SPATTER_COUNT) * 0.65f;
                if (alpha < arrival) continue;
                float local = Math.min(1f, (alpha - arrival) / 0.20f);
                game.getBatch().setColor(0.55f, 0.02f, 0.03f, local * 0.95f);
                com.badlogic.gdx.graphics.g2d.TextureRegion reg =
                        visorDropletTextures.get(deathSpatterTex[i] % visorDropletTextures.size());
                float size = deathSpatterSize[i];
                game.getBatch().draw(reg,
                        deathSpatterX[i], deathSpatterY[i],
                        size * 0.5f, size * 0.5f,
                        size, size,
                        1f, 1f,
                        deathSpatterRot[i]);
            }
            game.getBatch().setColor(com.badlogic.gdx.graphics.Color.WHITE);
            game.getBatch().end();
        }

        // The wash darkens as it thickens, so it lands on the death screen's near-black rather
        // than on a flat red.
        float r = com.badlogic.gdx.math.MathUtils.lerp(0.42f, 0.06f, alpha);
        float gCh = com.badlogic.gdx.math.MathUtils.lerp(0.02f, 0.01f, alpha);
        float b = com.badlogic.gdx.math.MathUtils.lerp(0.03f, 0.02f, alpha);
        shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
        shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(r, gCh, b, alpha);
        shapeRenderer.rect(0f, 0f, vw, vh);
        shapeRenderer.end();
    }

    /** True while the death cinematic owns the screen and gameplay input must be refused. */
    public boolean isDying() {
        return (deathSequence != null && deathSequence.isActive())
                || isDeathTransitionTriggered
                || (player != null && player.getCurrentHP() <= 0);
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
        // #199: any timed buff (potion, spell) or an enhanced weapon glows; see BuffGlow.
        Item rightHand = player.getInventory() != null ? player.getInventory().getRightHand() : null;
        return com.bpm.minotaur.rendering.BuffGlow.colorFor(player.getStatusManager().getActiveEffects(),
                rightHand != null ? rightHand.getEnhancementLevel() : 0);
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
                false, player.getPosition(), currentLevel(), worldManager.getCurrentPlayerChunkId());
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
        // Checked before the killer, not after: the killer name is never cleared once set, so any
        // earlier fight masked every genuine bleed-out and this epitaph was effectively dead text.
        boolean bledOut = telemetry.getBleedDamageTaken() > 0
                && player != null
                // A tick of bleeding right after a bite is still the biter's kill (#236).
                && player.bledOutLongAfterTheLastBlow()
                && player.getInjuryManager() != null
                && player.getInjuryManager().hasUntreatedInjuries();
        String survival = telemetry.getSurvivalDeath();
        if ("thirst".equals(survival)) {
            sb.append("Died of thirst");
        } else if ("starvation".equals(survival)) {
            sb.append("Starved to death");
        } else if (bledOut) {
            sb.append("Bled out from untended wounds");
        } else if (killer != null && !killer.trim().isEmpty()) {
            String niceName = formatMonsterName(killer);
            String article = niceName.matches("^[AEIOU].*") ? "an" : "a";
            sb.append("Fell to ").append(article).append(" ").append(niceName);
        } else {
            sb.append("Perished in the depths");
        }
        sb.append(" at Strata Depth ").append(Math.max(1, telemetry.getStrataReached()));
        sb.append(" on turn ").append(telemetry.getTurnsLived());

        if (survival == null && player != null && player.getStats() != null) {
            PlayerStats stats = player.getStats();
            if (stats.getHungerStage().isWeakened()) {
                sb.append(" while starving");
            } else if (stats.getThirstStage().isDehydrated()) {
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
     * The death as the history takes it (#283): the killing monster, if a monster killed, may
     * become a Nemesis in this level and chunk. A megabeast, the Warden and a shade are already
     * named, so they are remembered by name only.
     */
    private com.bpm.minotaur.gamedata.history.SeekerDeath seekerDeath(String killer, int level) {
        com.bpm.minotaur.telemetry.TelemetryManager telemetry = com.bpm.minotaur.telemetry.TelemetryManager.getInstance();
        Object ref = telemetry.getSurvivalDeath() == null ? telemetry.getKillerRef() : null;
        com.bpm.minotaur.gamedata.monster.Monster m = ref instanceof com.bpm.minotaur.gamedata.monster.Monster
                ? (com.bpm.minotaur.gamedata.monster.Monster) ref : null;
        // Only a monster standing in this maze: a bleed or a trap long after some other fight is no one's kill.
        boolean nameable = m != null && maze != null && maze.getMonsters().containsValue(m)
                && m.getMegabeastId() < 0 && !m.isBridgeBoss()
                && m.getType() != com.bpm.minotaur.gamedata.monster.Monster.MonsterType.PLAYER_GHOST;
        com.bpm.minotaur.gamedata.history.SeekerDeath death = com.bpm.minotaur.gamedata.history.SeekerDeath.by(
                nameable && m.getNemesisId() >= 0 ? m.getName() : killer,
                nameable ? m.getType().name() : null, nameable ? m.getHouseId() : telemetry.getKillerHouse());
        if (nameable) death.figure(m.getFigureId()).nemesis(m.getNemesisId());
        GridPoint2 chunk = worldManager.getCurrentPlayerChunkId();
        return death.of(player.getLife() != null ? player.getLife().name : null)
                .at(level, chunk != null ? chunk.x : 0, chunk != null ? chunk.y : 0);
    }

    /** The dying life's gear in hand and equipped goes to the Nemesis that killed it (#284). */
    private void hoardGearOf(int nemesisId) {
        com.bpm.minotaur.gamedata.history.NemesisHoard hoard = com.bpm.minotaur.managers.NemesisHoardManager.getInstance().hoard();
        java.util.List<Item> gear = new java.util.ArrayList<>();
        gear.add(player.getInventory().getRightHand());
        gear.add(player.getInventory().getLeftHand());
        if (player.getEquipment() != null) gear.addAll(player.getEquipment().getAllEquipped());
        for (Item item : gear) {
            if (item != null) hoard.take(nemesisId, new com.bpm.minotaur.gamedata.save.ItemSaveData(item));
        }
        com.bpm.minotaur.managers.NemesisHoardManager.getInstance().save();
    }

    /** What killed the player, in words: a monster's name, a survival cause, or the maze itself. */
    private static String killerForTheLog() {
        com.bpm.minotaur.telemetry.TelemetryManager telemetry = com.bpm.minotaur.telemetry.TelemetryManager.getInstance();
        String killer = telemetry.getKillerMonster();
        if (killer == null || killer.isEmpty()) killer = telemetry.getSurvivalDeath();
        if (killer == null || killer.isEmpty()) return "the maze";
        if (!killer.matches("[A-Z_]+")) return killer;
        // A monster's kind, not a name: "a Kobold", "an Ogre".
        String kind = com.bpm.minotaur.ui.UiNames.fromConstant(killer);
        return ("AEIOU".indexOf(Character.toUpperCase(kind.charAt(0))) >= 0 ? "an " : "a ") + kind;
    }

    /** The Last Crossing's Reckoning, once no god was left to hold Tarmin; null until then. */
    private com.bpm.minotaur.gamedata.worldlog.Reckoning lastCrossing;

    /** How many old gods still keep the soul, or -1 when there is no history to ask (#290). */
    int keepers() {
        return worldManager != null && worldManager.getHistory() != null
                ? com.bpm.minotaur.gamedata.history.OldGod.keepers(worldManager.getHistory().world()) : -1;
    }

    /** The soul's name, the one typed or generated at a new game (#268). */
    static String soulName() {
        com.bpm.minotaur.managers.SaveManager saves = com.bpm.minotaur.managers.SaveManager.getInstance();
        com.bpm.minotaur.gamedata.save.SlotMetadata meta = saves.getSlotMetadata(saves.getActiveSlotIndex());
        return meta != null && meta.characterName != null ? meta.characterName : "Hero";
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
        // A death event still queued from the last life's final frames would kill this one on
        // waking, with no wound at all (playtest 2026-10-22: Nell of the Ash, dead on turn 0).
        eventManager.consumeAll(GameEvent.EventType.PLAYER_DIED);
        this.deathWatch.reset();
        this.deathSequence.reset();
        this.pendingDeathScreen = null;
        this.alertMonitor.reset();
        this.damageFlash.reset();
        this.screenFx.clear();
        player.leaveForm(null, null); // a new expedition starts in the player's own body
        this.lastPlayerHp = -1;
        this.world3DRenderer.setDeathSequence(null);
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().startNewRun();

        // 1. Wipe the explored world -- every chunk (including chunk 0,0) is wiped and reseeded
        worldManager.wipeExploredWorldOnDeath();
        if (worldManager.getHistory() != null) worldManager.getHistory().endHunt();
        DivinityManager.getInstance().onWorldReset();
        // A new expedition starts the escalation clock over; only the sleep and altar paths did.
        DoomManager.getInstance().resetExpeditionTurns();
        if (worldManager.getDayNightManager() != null) {
            worldManager.getDayNightManager().setTimeOfDay(com.bpm.minotaur.managers.DayNightManager.DAWN_SUNRISE);
        }
        if (worldManager.getWeatherManager() != null) {
            worldManager.getWeatherManager().randomizeStartingWeather();
        }

        // 2. Restore Player
        com.bpm.minotaur.managers.DimensionalManager.getInstance().reset();
        debugManager.setRenderEngine(com.bpm.minotaur.managers.DebugManager.RenderEngine.PLANAR_3D);
        debugManager.setRenderModeDirect(com.bpm.minotaur.managers.DebugManager.RenderMode.MODERN);
        // Death is permanent for the Incarnation (#187): level, XP, stats, points and the HP/MP
        // maxima die with it. Known Spells, unlocks, the altar, the chest and Tarmin's Hunger stay.
        player.beginNewIncarnation(difficulty);
        player.getStats().setCurrentHP(player.getStats().getMaxHP());
        player.getStats().setCurrentMP(player.getStats().getMaxMP());
        player.getStats().setBodyTemperature(PlayerStats.BODY_TEMP_NORMAL);
        player.getStats().setWetness(0f); // a new expedition starts dry (#194)
        player.getStats().setSatiety(PlayerStats.STARTING_SATIETY);
        player.getStats().setHydration(PlayerStats.STARTING_HYDRATION);
        player.getStats().setToxicity(0);
        player.getStats().setTaint(0f); // a new expedition starts clean of the Blight
        player.getStatusManager().clearEffects();
        player.abandonTomeStudy();
        player.clearRunSpellsOnDeath();
        player.prepareStartingSpells(); // a new expedition starts with Mote of Light ready, as a new game does
        if (player.noteRespawn()) {
            player.offerTraits(); // every fifth respawn: a new personality to choose
        }
        // The Player instance survives death, so anatomical trauma must be wiped
        // explicitly -- otherwise open wounds, bleeding, and fever follow the
        // character into the next expedition and can bleed them out before their
        // first encounter.
        if (player.getInjuryManager() != null) {
            player.getInjuryManager().cureAll();
        }

        // Strip any equipped weapons and armor upon death. The Father's Sword returns with every
        // Incarnation (#187); its Keepsake perk is in FathersSword.
        if (player.getEquipment() != null) {
            player.getEquipment().stripAllEquipped();
        }
        player.getInventory().setLeftHand(null);
        Item starterWeapon = game.getItemDataManager().createItem(
                com.bpm.minotaur.gamedata.item.FathersSword.TYPE, 0, 0, ItemColor.GRAY, game.getAssetManager());
        player.getInventory().setRightHand(starterWeapon);
        player.giveStarterLantern(game.getItemDataManager().createItem(
                Item.ItemType.BRASS_LANTERN, 0, 0, ItemColor.GOLD, game.getAssetManager()),
                com.bpm.minotaur.gamedata.item.ShelterChest.getInstance().getItems());
        player.giveStarterWaterskin(game.getItemDataManager().createItem(
                Item.ItemType.WATERSKIN, 0, 0, ItemColor.TAN, game.getAssetManager()));

        // Reset the combat dice pool so it cleanly contains the starter Rusty Iron Die
        if (player.getStats() != null && player.getStats().getDicePool() != null) {
            player.getStats().getDicePool().clear();
            com.bpm.minotaur.gamedata.dice.Die starterDie = starterWeapon.getGrantedDie() != null
                    ? starterWeapon.getGrantedDie()
                    : com.bpm.minotaur.gamedata.dice.DiceFactory.create("Rusty Iron Die");
            player.getStats().getDicePool().add(starterDie);
        }

        // He wakes in the Shelter washed: the blood of the last expedition does not carry into the next.
        player.washBlood();

        // Clean weapon state upon shelter awakening: reset attack timer/combos, clear blood decals,
        // clear old equipment references, and force re-synchronization of equipped items and motion profiles.
        if (weaponOverlay != null) {
            weaponOverlay.reset();
            weaponOverlay.clearBloodDecals();
            weaponOverlay.clearEquipment();
            weaponOverlay.forceRefreshEquipment(player.getInventory().getRightHand(), player.getInventory().getLeftHand());
        }

        // A new Incarnation carries what a new game starts with: STARTING_RATIONS rations
        // and a Waterskin (#191, #193).
        boolean hasFood = false;
        boolean hasWaterskin = false;
        for (Item itm : player.getInventory().getMainInventory()) {
            if (itm != null) {
                if (itm.getType() == Item.ItemType.FOOD) hasFood = true;
                if (itm.getType() == Item.ItemType.WATERSKIN) hasWaterskin = true;
            }
        }
        for (Item itm : player.getInventory().getQuickSlots()) {
            if (itm != null) {
                if (itm.getType() == Item.ItemType.FOOD) hasFood = true;
                if (itm.getType() == Item.ItemType.WATERSKIN) hasWaterskin = true;
            }
        }
        if (!hasFood) {
            Item starterFood = game.getItemDataManager().createItem(Item.ItemType.FOOD, 0, 0, ItemColor.TAN, game.getAssetManager());
            if (starterFood != null) {
                starterFood.setStackCount(Player.STARTING_RATIONS);
                player.getInventory().pickupToBackpack(starterFood);
            }
        }
        if (!hasWaterskin) {
            Item starterWater = game.getItemDataManager().createItem(Item.ItemType.WATERSKIN, 0, 0, ItemColor.TAN, game.getAssetManager());
            if (starterWater != null) player.getInventory().pickupToBackpack(starterWater);
        }
        // Never a hard lock on the roads: a new expedition always carries fire for one shelter.
        if (!player.getInventory().hasItemOfType(Item.ItemType.TINDER_BUNDLE)) {
            Item tinder = game.getItemDataManager().createItem(Item.ItemType.TINDER_BUNDLE, 0, 0, ItemColor.TAN, game.getAssetManager());
            if (tinder != null) player.getInventory().pickupToBackpack(tinder);
        }

        // Travel kits: replace any the player has paid for at the Altar but no longer
        // carries. They are not a death handout -- owning the Crafting Bench or Fire Pot
        // is what earns the portable version, and this is only the top-up for one lost
        // in the field.
        grantOwedFieldKits();

        // Shelter Altar Provisions tier: a respawn is a new expedition, so its rations are
        // handed over on the first step outside (TurnManager), like every expedition's.
        com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().packProvisionsForNewExpedition();

        // A new expedition gets a fresh merchant on each level; without this the
        // claims from the dead run would keep merchants out of the new one.
        com.bpm.minotaur.generation.ShopkeeperTracker.reset();

        // 3. Wake in the remembered shelter on Level 1: the road shelter last rested in, or home.
        worldManager.setCurrentLevel(1);
        Maze shelterMaze = loadShelterChunk(worldManager.getRespawnChunk());
        swapToChunk(shelterMaze);

        // Position player at bed / safe start point in shelter
        GridPoint2 bedPos = shelterWakeTile(shelterMaze);
        player.setPosition(bedPos);
        worldManager.saveCurrentChunk(shelterMaze);

        // Re-hydrate Shelter Chest
        ShelterChest.getInstance().load(game.getItemDataManager(), game.getAssetManager());

        // 4. Feedback & return to game screen
        if (hud != null) {
            hud.addMessage("You awaken back in the Shelter Bed.");
            if (lostCount > 0) {
                hud.addMessage("Lost " + lostCount + " item" + (lostCount == 1 ? "" : "s") + " upon falling.");
            }
            hud.addMessage("The world beyond the Shelter has changed -- a new expedition awaits.");
            hud.addMessage(String.format("Tarmin's Hunger grows: death %d of 50, the Bridge at %d%%.", deaths, (int) bridge));
        }
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("You awaken back at the Shelter... Tarmin's hunger grows.", 4f));
            HintManager.getInstance().checkDoom(eventManager);
        }
        soundManager.playDoorOpenSound();

        // The death is now fully resolved: clear the pending marker and persist the new run.
        SaveManager.getInstance().setRespawnPending(false);
        SaveManager.getInstance().saveActiveSlot(player, worldManager);

        game.setScreen(this);
    }

    /**
     * Makes a shelter chunk current, falling back to the home shelter if it cannot load
     * (a remembered shelter in a world that no longer holds it).
     */
    private Maze loadShelterChunk(GridPoint2 chunk) {
        Maze m = chunk != null ? worldManager.loadChunk(chunk) : null;
        if (m == null) {
            chunk = new GridPoint2(0, 0);
            m = worldManager.loadChunk(chunk);
        }
        worldManager.setCurrentChunk(chunk);
        return m;
    }

    /** Where to stand on waking in a shelter: beside the bed, else beside the hearth, else the door. */
    private GridPoint2 shelterWakeTile(Maze shelterMaze) {
        for (Item item : shelterMaze.getItems().values()) {
            if (item.getType() == Item.ItemType.HOME_SLEEPING_BAG) {
                int bx = (int) item.getPosition().x;
                int by = (int) item.getPosition().y;
                if (shelterMaze.isPassable(bx - 1, by)) return new GridPoint2(bx - 1, by);
                if (shelterMaze.isPassable(bx, by + 1)) return new GridPoint2(bx, by + 1);
                return new GridPoint2(bx, by);
            }
        }
        GridPoint2 hearth = shelterMaze.getHearthTile();
        if (hearth != null) {
            GridPoint2 near = WorldManager.findSafeArrivalTile(shelterMaze, hearth.x, hearth.y - 1);
            if (near != null) return near;
        }
        if (shelterMaze.getShelterEntry() != null) return shelterMaze.getShelterEntry();
        return worldManager.getInitialPlayerStartPos();
    }

    /** Opens a conversation with the town folk standing at {@code s}. */
    private void talkTo(com.bpm.minotaur.gamedata.Scenery s) {
        com.bpm.minotaur.gamedata.history.town.Town town = worldManager.getHistory().town(s.getTownKey());
        if (s.getFolkIndex() < 0 || s.getFolkIndex() >= town.folk.size()) return;
        com.bpm.minotaur.gamedata.history.town.Town.Folk folk = town.folk.get(s.getFolkIndex());
        TownTalk.Pack pack = new TownTalk.Pack() {
            @Override
            public boolean hasSignet(int houseId) {
                return signet(houseId) != null;
            }

            @Override
            public boolean giveSignet(int houseId) {
                Item ring = signet(houseId);
                return ring != null && player.getInventory().removeItem(ring);
            }

            @Override
            public void reward(String townName) {
                Item gift = game.getItemDataManager().createItem(Item.ItemType.POTION_OF_HEALING,
                        (int) player.getPosition().x, (int) player.getPosition().y, ItemColor.YELLOW, game.getAssetManager());
                if (gift != null && !player.pickupItem(gift)) maze.addItem(gift);
                eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(townName + " pays you in kind."), 3f));
            }

            private Item signet(int houseId) {
                for (Item i : player.getInventory().getAllItems()) {
                    if (i != null && i.getType() == Item.ItemType.SIGNET_RING && i.getTrophyHouseId() == houseId) return i;
                }
                return null;
            }
        };
        TownTalk talk = new TownTalk(worldManager.getHistory(), town, folk, worldManager.nearbyTowns(town.key),
                com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance(),
                com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.getInstance(), pack);
        com.bpm.minotaur.gamedata.ShopkeeperNpc merchant = maze.getShopkeeper();
        Runnable trade = merchant == null ? null
                : () -> eventManager.addEvent(new GameEvent(GameEvent.EventType.SHOPKEEPER_INTERACTION, merchant));
        game.setScreen(new TalkScreen(game, this, talk, trade));
    }

    private final WarManager warManager = new WarManager(com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance());

    /** A front over the player's chunk sounds the horns; staying fights the battle (plan T2.3-T2.7). */
    private com.bpm.minotaur.managers.KnellCrier knellCrier;

    /**
     * Tarmin's Knell (plan K4, K5): whatever great thing has happened since he last spoke tolls the
     * gong over the whole Maze and speaks in red in the seeker's head, the music ducking under it.
     */
    private void tollKnell() {
        listenForKnell();
        if (knellCrier == null || hud == null || hud.getKnellOverlay() == null) return;
        java.util.List<String> lines = knellCrier.listen();
        if (lines.isEmpty()) return;
        if (soundManager != null) soundManager.playKnell();
        if (currentLevel() == 1) {
            // Above ground the sky answers the gong: lightning out of the castle, over the maze.
            firstPersonRenderer.getSkybox3DRenderer().tollKnell();
            knellOverheadIn = KNELL_OVERHEAD;
        }
        MusicManager.getInstance().duckMusic(KNELL_DUCK);
        for (String line : lines) hud.addMessage(line);
        hud.getKnellOverlay().toll(lines, SettingsManager.getInstance().isKnellVignette(),
                () -> MusicManager.getInstance().restoreMusicVolume());
    }

    /**
     * Makes sure a crier is listening to the history now in play -- from the first frame, so a
     * great event in the first turn is not mistaken for old news; and anew for a new world.
     */
    private void listenForKnell() {
        com.bpm.minotaur.managers.HistoryManager history = worldManager != null ? worldManager.getHistory() : null;
        if (history != null && (knellCrier == null || !knellCrier.listensTo(history))) {
            knellCrier = new com.bpm.minotaur.managers.KnellCrier(history,
                    com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.getInstance(),
                    com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance());
        }
    }

    /** Seconds until the knell's lightning passes overhead and lights the maze; negative when not coming. */
    private float knellOverheadIn = -1f;
    /** When the sweep's front crosses the zenith, from its start at the castle's horizon. */
    private static final float KNELL_OVERHEAD = 0.65f;
    private static final float KNELL_GROUND_FLASH = 0.6f;

    /** Lights the maze as the knell's lightning crosses the sky above it. */
    private void tendKnellSky(float delta) {
        if (knellOverheadIn < 0f) return;
        knellOverheadIn -= delta;
        if (knellOverheadIn < 0f && worldManager != null && worldManager.getWeatherManager() != null) {
            worldManager.getWeatherManager().knellFlash(KNELL_GROUND_FLASH);
        }
    }

    /** How far the music sinks while the knell rings. */
    private static final float KNELL_DUCK = 0.2f;

    private void tickWar() {
        WarManager.Ground g = new WarManager.Ground();
        g.chunk = worldManager.getCurrentPlayerChunkId();
        g.level = currentLevel();
        // A shelter's warded ground. Maze.isSanctuary() alone is true for every chunk -- it says
        // whether home tiles, if a chunk has any, are warded -- and reading it as "a shelter" kept
        // every battle and encounter off the overland.
        g.sanctuary = maze != null && !maze.getSanctuaryTiles().isEmpty();
        g.front = worldManager.frontHere();
        String carried = worldManager.getHistory().onFront(g.front);
        if (carried != null) eventManager.addEvent(new GameEvent(carried, 4f));
        g.seats = g.front != null ? worldManager.houseSeats() : null;
        g.maze = maze;
        g.playerTile = player != null ? new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y) : null;
        WarManager.Turn t = warManager.onTurn(worldManager.getHistory(), g, worldManager::recruit,
                new BattleSpoils(game.getItemDataManager(), game.getAssetManager(),
                        worldManager.getHistory().warClock() ^ worldManager.getWorldSeed(),
                        id -> {
                            com.bpm.minotaur.gamedata.history.House h = worldManager.getHistory().world().house(id);
                            return h != null ? h.name : null;
                        }));
        for (String line : t.messages) {
            eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(line), 5f));
        }
        if (soundManager != null) {
            for (WarManager.Cue cue : t.cues) soundManager.playWarCue(cue);
            if (t.cues.contains(WarManager.Cue.HORNS)) {
                soundManager.playWarZoneAlarm();
            }
        }
        if (t.volleyDamage > 0 && player != null) {
            player.takeDamage(t.volleyDamage, com.bpm.minotaur.gamedata.DamageType.PHYSICAL);
        }
        if (t.winner >= 0 && t.foughtFor == t.winner) foughtFor(t.winner);
        tickEncounters(g);
    }

    private final com.bpm.minotaur.managers.EncounterDirector encounterDirector =
            new com.bpm.minotaur.managers.EncounterDirector(com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance());

    /** The surface war's skirmishes, columns, raids and camps, in the chunk the player stands in (Living War W2). */
    private void tickEncounters(WarManager.Ground g) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.town.Town town = g.level >= 2 ? worldManager.townHere() : null;
        com.bpm.minotaur.managers.EncounterDirector.Turn t = encounterDirector.onTurn(history, g.chunk, g.level,
                g.sanctuary, g.maze, g.playerTile, g.level == 1 ? worldManager.currentEncounters() : belowEncounters(g, town),
                g.level == 1 ? worldManager.currentCamps() : java.util.Collections.emptyList(),
                worldManager::recruit, com.bpm.minotaur.gamedata.Scenery::fromProp,
                new BattleSpoils(game.getItemDataManager(), game.getAssetManager(),
                        history.warClock() ^ worldManager.getWorldSeed(),
                        id -> {
                            com.bpm.minotaur.gamedata.history.House h = history.world().house(id);
                            return h != null ? h.name : null;
                        }), town != null ? town.key : null);
        for (String line : t.messages) {
            eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(line), 5f));
        }
        if (t.cried != null) {
            // A herald cries the news: told as its own house tells it (W29).
            com.bpm.minotaur.gamedata.history.HistoryWorld world = history.world();
            com.bpm.minotaur.gamedata.history.text.Chronicler voice = com.bpm.minotaur.gamedata.history.text.Chronicler.of(
                    world, t.cried, com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.Bias.NEUTRAL,
                    com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance());
            String said = com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.getInstance().render(world, t.cried, voice);
            com.bpm.minotaur.gamedata.history.House by = world.house(t.criedBy);
            eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(
                    "The herald of " + (by != null ? by.name : "a house") + " cries: " + said), 8f));
            if (soundManager != null) soundManager.playSound("horn_battle", 0.5f);
        }
        if (soundManager != null) {
            for (com.bpm.minotaur.managers.EncounterDirector.Cue cue : t.cues) soundManager.playEncounterCue(cue);
        }
        hearTheWar(g);
        monsterAiManager.setPlayerInTheFight(encounterDirector.playerInTheFight()
                || (warManager.active() != null && warManager.active().playerJoined()));
        monsterAiManager.setTakesPlayerForKin(disguise());
        monsterAiManager.setHoldsItsPeace(m -> {
            int house = worldManager.houseOf(m);
            return house >= 0 && history.stanceOf(house).ordinal()
                    >= com.bpm.minotaur.gamedata.history.favour.Favour.Tier.TOLERATED.ordinal();
        });
        tendTheSworn(g, t);
        tendTheSiege(g);
        if (hud != null) {
            com.bpm.minotaur.gamedata.history.war.WarTally tally = warManager.active() != null ? warManager.active().tally(maze) : null;
            hud.setWarTally(tally != null ? tally : encounterDirector.tally(maze));
        }
    }

    /** Below ground, the war comes as raiders: into a gash whose house is at war, or at a town's gate (W32). */
    private java.util.List<com.bpm.minotaur.gamedata.history.war.Encounter> belowEncounters(WarManager.Ground g,
            com.bpm.minotaur.gamedata.history.town.Town town) {
        java.util.List<com.bpm.minotaur.gamedata.history.war.Encounter> out = new java.util.ArrayList<>();
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        if (g.chunk == null) return out;
        if (town != null) {
            com.bpm.minotaur.gamedata.history.war.Encounter e = com.bpm.minotaur.gamedata.history.war.EncounterScheduler
                    .townRaid(history.world(), history.warClock(), town.key, g.chunk);
            if (e != null) out.add(e);
        }
        com.bpm.minotaur.gamedata.history.House holder = gashHolderHere(g);
        if (holder != null) {
            com.bpm.minotaur.gamedata.history.war.Encounter e = com.bpm.minotaur.gamedata.history.war.EncounterScheduler
                    .gashRaid(history.world(), history.warClock(), g.chunk, g.level, holder.id);
            if (e != null) out.add(e);
        }
        // In the castle's halls during a siege, the assault and the Legion fight it out (W36).
        com.bpm.minotaur.gamedata.history.War siege = history.world().activeSiege();
        if (siege != null && inTheHalls(g) && history.world().tarminHouse() != null) {
            int tarmin = history.world().tarminHouse().id;
            out.add(new com.bpm.minotaur.gamedata.history.war.Encounter(com.bpm.minotaur.gamedata.history.war.Encounter.Kind.SKIRMISH,
                    -1000 - siege.id, siege.id, siege.enemyOf(tarmin), tarmin, 0, Long.MAX_VALUE, g.chunk, g.chunk));
        }
        return out;
    }

    /** The house holding the gash the player is inside, or null outside one. */
    private com.bpm.minotaur.gamedata.history.House gashHolderHere(WarManager.Ground g) {
        if (g.level < 2 || g.level > SealCourt.COURT_LEVEL || worldManager.getBiomeManager() == null || g.chunk == null) return null;
        int gash = com.bpm.minotaur.gamedata.boss.SealLord.gashIndexForRoad(worldManager.getBiomeManager().getSealRoad(g.chunk));
        return gash >= 0 ? worldManager.getHistory().world().gashHolder(gash) : null;
    }

    /** The camp whose props stand on {@code tile} in this chunk, or -1. */
    private int campAt(GridPoint2 tile) {
        GridPoint2 chunk = worldManager.getCurrentPlayerChunkId();
        if (chunk == null) return -1;
        for (com.bpm.minotaur.gamedata.history.war.EncounterLedger.Dressing d : worldManager.getHistory().encounterLedger().dressings) {
            if (!"CAMP".equals(d.kind) || d.chunkX != chunk.x || d.chunkY != chunk.y) continue;
            for (int i = 0; i + 1 < d.tiles.size(); i += 2) {
                if (d.tiles.get(i) == tile.x && d.tiles.get(i + 1) == tile.y) return d.houseId;
            }
        }
        return -1;
    }

    private String houseName(int houseId) {
        com.bpm.minotaur.gamedata.history.House h = worldManager.getHistory().world().house(houseId);
        return h != null ? h.name : "a house of the Maze";
    }

    private void say(String line, float seconds) {
        eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(line), seconds));
    }

    /** The player fought for {@code houseId} and it held the ground: it remembers (W19). */
    private void foughtFor(int houseId) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        history.favour().change(history.world(), houseId, com.bpm.minotaur.gamedata.history.favour.Favour.WON_FOR);
        say(houseName(houseId) + " will remember that you stood with it.", 4f);
    }

    /** The camp the player was last greeted in, so its sentries speak once a visit. */
    private String campGreeted;

    /**
     * Each turn, what the houses make of the player (Living War W18-W26): favour for the fights they
     * joined and the heralds they cut down, signets returned and greetings at a camp, an oath broken
     * by striking its house, contracts kept, and hunters sent by a house that counts them an enemy.
     */
    private void tendTheSworn(WarManager.Ground g, com.bpm.minotaur.managers.EncounterDirector.Turn t) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.HistoryWorld world = history.world();
        com.bpm.minotaur.gamedata.history.favour.Favour favour = history.favour();
        com.bpm.minotaur.gamedata.history.favour.Oath oath = history.oath();
        if (t.foughtFor >= 0) foughtFor(t.foughtFor);
        if (t.heraldSlain >= 0) favour.change(world, t.heraldSlain, com.bpm.minotaur.gamedata.history.favour.Favour.HERALD_SLAIN);

        // Struck a soldier of the sworn house: the oath is broken (W21).
        if (oath.sworn() && maze != null) {
            for (com.bpm.minotaur.gamedata.monster.Monster m : maze.getMonsters().values()) {
                if (m != null && m.seekerDrewBlood() && worldManager.houseOf(m) == oath.houseId) {
                    String betrayed = houseName(oath.houseId);
                    history.breakOath();
                    say("You have struck a soldier of " + betrayed + ". Your oath is broken, and every house hears of it.", 6f);
                    break;
                }
            }
        }

        if (t.campHouse >= 0) atCamp(t.campHouse, g);
        else campGreeted = null;

        com.bpm.minotaur.gamedata.history.favour.Contract c = oath.contract;
        if (c != null && !c.done) {
            boolean kept = c.held(g.chunk)
                    || (c.kind == com.bpm.minotaur.gamedata.history.favour.Contract.Kind.ESCORT_COLUMN && t.escorted == oath.houseId)
                    || (c.kind == com.bpm.minotaur.gamedata.history.favour.Contract.Kind.BURN_CAMP && t.campBroken == c.targetId);
            if (kept) {
                c.done = true;
                say("A contract kept. Return to a war camp of " + houseName(oath.houseId) + " to be paid.", 5f);
            }
        }

        // Hunters: once a slot, a house that counts the player its enemy may send them (W18).
        long clock = history.warClock();
        if (g.level == 1 && !g.sanctuary && clock % com.bpm.minotaur.gamedata.history.war.EncounterScheduler.SLOT == 0) {
            for (com.bpm.minotaur.gamedata.history.House h : world.livingHouses()) {
                if (history.stanceOf(h.id) != com.bpm.minotaur.gamedata.history.favour.Favour.Tier.ENEMY) continue;
                java.util.Random r = new java.util.Random(world.seed ^ (clock * 0x9E3779B97F4A7C15L) ^ (h.id * 31L));
                if (r.nextFloat() >= HUNT_CHANCE) continue;
                com.bpm.minotaur.managers.EncounterDirector.Turn hunted = new com.bpm.minotaur.managers.EncounterDirector.Turn();
                encounterDirector.hunt(history, maze, h.id, g.playerTile, worldManager::recruit, hunted);
                for (String line : hunted.messages) say(line, 5f);
                break;
            }
        }
    }

    /** Chance a slot brings hunters from a house that counts the player its enemy (W18). */
    private static final float HUNT_CHANCE = 0.3f;

    /** Standing in a house's war camp: its signets returned, its sentries' greeting, its quartermaster (W18-W20). */
    private void atCamp(int houseId, WarManager.Ground g) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.favour.Favour favour = history.favour();
        // Its signets, taken from its dead, are given back to it (W19).
        for (Item i : new java.util.ArrayList<>(player.getInventory().getAllItems())) {
            if (i != null && i.getType() == Item.ItemType.SIGNET_RING && i.getTrophyHouseId() == houseId) {
                player.getInventory().removeItem(i);
                favour.change(history.world(), houseId, com.bpm.minotaur.gamedata.history.favour.Favour.SIGNET_RETURNED);
                say("The captain of " + houseName(houseId) + " takes back its signet, and bows.", 4f);
            }
        }
        com.bpm.minotaur.gamedata.history.favour.Favour.Tier tier = history.stanceOf(houseId);
        String key = g.chunk + ":" + houseId + ":" + tier;
        if (key.equals(campGreeted)) return;
        campGreeted = key;
        boolean sworn = history.oath().houseId == houseId;
        if (sworn) {
            say("The sentries of " + houseName(houseId) + " raise their spears to you. Your banner is here.", 5f);
        } else if (tier == com.bpm.minotaur.gamedata.history.favour.Favour.Tier.FRIEND) {
            say("The captain of " + houseName(houseId) + " greets you as a friend, and offers you its oath. "
                    + "Stand at its war banner and press O to swear.", 7f);
        } else if (tier == com.bpm.minotaur.gamedata.history.favour.Favour.Tier.TOLERATED) {
            say("The sentries of " + houseName(houseId) + " watch you pass, and let you be.", 4f);
        }
        // A friend may trade at the camp: its quartermaster sets out his wares (W18).
        if ((sworn || tier == com.bpm.minotaur.gamedata.history.favour.Favour.Tier.FRIEND)
                && maze != null && maze.getShopkeeper() == null && g.playerTile != null) {
            GridPoint2 at = WorldManager.findSafeArrivalTile(maze, g.playerTile.x + 2, g.playerTile.y);
            if (at != null) {
                com.bpm.minotaur.gamedata.ShopkeeperNpc quartermaster = new com.bpm.minotaur.gamedata.ShopkeeperNpc(
                        at.x, at.y, game.getAssetManager());
                quartermaster.setCustomDisplayName("Quartermaster of " + houseName(houseId));
                com.bpm.minotaur.gamedata.history.House h = history.world().house(houseId);
                String docId = h != null ? h.doctrineId : null;
                new com.bpm.minotaur.gamedata.ShopInventory().stockQuartermaster(quartermaster, game.getItemDataManager(),
                        game.getAssetManager(), maze.getLevel(), docId, houseId, houseName(houseId));
                if (sworn) {
                    quartermaster.setMilitaryDiscount(0.20f); // 20% sworn military discount
                }
                maze.setShopkeeper(quartermaster);
                say("The quartermaster of " + houseName(houseId) + " will trade with you.", 4f);
            }
        }
    }

    /**
     * At a camp's war banner (W20-W26): a friend swears the house's oath here; the sworn are paid for
     * contracts kept, set the next, and granted the seal when the chain is done.
     */
    private void atCampBanner(int houseId) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.HistoryWorld world = history.world();
        com.bpm.minotaur.gamedata.history.favour.Oath oath = history.oath();
        com.bpm.minotaur.gamedata.history.favour.Favour.Tier tier = history.stanceOf(houseId);
        String name = houseName(houseId);
        if (oath.houseId != houseId) {
            if (tier != com.bpm.minotaur.gamedata.history.favour.Favour.Tier.FRIEND) {
                say("The banner of " + name + ". Its sentries watch your hands. ("
                        + com.bpm.minotaur.gamedata.history.favour.Favour.word(tier) + ": "
                        + history.favour().of(houseId) + ")", 4f);
                return;
            }
            String former = oath.sworn() ? houseName(oath.houseId) : null;
            history.swear(houseId);
            if (former != null) say("You forsake " + former + ". It will not forget.", 5f);
            say("You kneel at the banner of " + name + " and swear to it. Its colours are yours now.", 6f);
            giveTabard(houseId);
            if (soundManager != null) soundManager.playSound("horn_battle", 0.6f);
        }
        com.bpm.minotaur.gamedata.history.favour.Contract c = oath.contract;
        if (c != null && c.done) pay(houseId, c);
        if (oath.sealDue()) grantSeal(houseId);
        if (oath.contract == null) {
            com.bpm.minotaur.gamedata.history.favour.Contract next = history.offerContract(worldManager.currentFronts(),
                    worldManager.getCurrentPlayerChunkId());
            if (next != null) say("The captain sets you a task. " + next.describe(world), 7f);
            else say("The captain has no task for you while " + name + " is at peace.", 4f);
        } else {
            say("Your task: " + oath.contract.describe(world), 6f);
        }
    }

    /** Resting by a war camp's campfire (W18-W20): heals HP/MP, passes a season, and saves. */
    private void atCampFire(int houseId) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.favour.Favour.Tier tier = history.stanceOf(houseId);
        boolean sworn = history.oath().houseId == houseId;
        String name = houseName(houseId);
        if (!sworn && tier != com.bpm.minotaur.gamedata.history.favour.Favour.Tier.FRIEND) {
            say("The sentries of " + name + " watch you coldly. Only friends of the house may rest by its fire.", 4f);
            return;
        }

        float rest = Math.min(1f, com.bpm.minotaur.gamedata.trait.TraitEffects.mult("bedRestMult"));
        if (player.getStats().getTaint() > 0f) {
            player.getStats().setTaint(0f);
            say("The sacred fire burns the Blight out of your bones.", 2.5f);
        }
        int hp = player.getStats().getCurrentHP();
        int mp = player.getStats().getCurrentMP();
        player.getStats().setCurrentHP(hp + Math.round((player.getStats().getMaxHP() - hp) * rest));
        player.getStats().setCurrentMP(mp + Math.round((player.getStats().getMaxMP() - mp) * rest));
        player.getStatusManager().clearEffects();
        DoomManager.getInstance().resetExpeditionTurns();

        java.util.List<com.bpm.minotaur.gamedata.history.HistoryEvent> news = history.onSleep();
        if (worldManager != null && worldManager.getDayNightManager() != null) {
            com.bpm.minotaur.managers.DayNightManager dnm = worldManager.getDayNightManager();
            if (dnm.getPhase() == com.bpm.minotaur.managers.DayNightManager.Phase.NIGHT
                    || dnm.getPhase() == com.bpm.minotaur.managers.DayNightManager.Phase.DUSK) {
                dnm.setTimeOfDay(com.bpm.minotaur.managers.DayNightManager.DAWN_SUNRISE);
            } else {
                dnm.advanceMinutes(180f);
            }
        }
        SaveManager.getInstance().saveActiveSlot(player, worldManager);
        SaveManager.getInstance().backupActiveSlot();
        autoSaveTimer = 0f;
        say("You rest by the fire of " + name + ". Health and mana restored. A season passes in the Maze. Game saved.", 5f);
        if (hud != null) {
            hud.addMessage("Rested at war camp. HP/MP restored. Game saved.");
        }
        tollKnell();
        news = new java.util.ArrayList<>(news);
        news.removeIf(e -> com.bpm.minotaur.gamedata.history.text.Knell.tolls(history.world(), e));
        for (com.bpm.minotaur.gamedata.history.HistoryEvent heard : com.bpm.minotaur.gamedata.history.text.Headlines.pick(news, 3)) {
            history.unlock(heard.id);
        }
        for (String rumour : com.bpm.minotaur.gamedata.history.text.Headlines.of(history.world(), news, 3,
                com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.getInstance(),
                com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance())) {
            say(rumour, 6f);
        }
        playerTurnTakesAction();
        needsAsciiRender = true;
    }

    /** The house pays for a contract kept: favour and coin, and every third one a relic (W25). */
    private void pay(int houseId, com.bpm.minotaur.gamedata.history.favour.Contract c) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        history.favour().change(history.world(), houseId, c.favour());
        player.getStats().setTreasureScore(player.getStats().getTreasureScore() + c.coin());
        boolean relic = history.oath().complete();
        say(houseName(houseId) + " pays you " + c.coin() + " in coin, and thinks the better of you.", 5f);
        if (!relic) return;
        com.bpm.minotaur.gamedata.history.House h = history.world().house(houseId);
        Item forged = com.bpm.minotaur.managers.Relics.forge(game.getItemDataManager(), game.getAssetManager(),
                h != null ? h.doctrineId : null, houseName(houseId), player.getStats().getLevel(),
                new java.util.Random(history.world().seed ^ history.oath().done * 0x9E3779B97F4A7C15L), 0, 0);
        if (forged != null && player.getInventory().pickup(forged)) {
            say("For your service, " + houseName(houseId) + " gives you a relic: " + forged.getDisplayName() + ".", 6f);
        }
    }

    /** The lord of a great house grants its sworn seeker the seal of its gash (W26). */
    private void grantSeal(int houseId) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.House h = history.world().house(houseId);
        if (h == null || !h.isGreat()) return;
        for (int road = 0; road < com.bpm.minotaur.generation.ShelterRoads.ROAD_COUNT; road++) {
            if (com.bpm.minotaur.gamedata.boss.SealLord.gashIndexForRoad(road) != h.gashIndex) continue;
            com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().awardSeal(road);
            history.recordSealGranted(houseId);
            say("The lord of " + h.name + " puts the seal of " + history.world().gashName(h.gashIndex)
                    + " in your hand. Go, and bring the Antlered Lord down.", 8f);
            if (soundManager != null) soundManager.playSound("bell_cathedral", 0.7f);
            return;
        }
    }

    /** A sworn seeker wears its house's colours (W24). */
    private void giveTabard(int houseId) {
        com.bpm.minotaur.gamedata.history.House h = worldManager.getHistory().world().house(houseId);
        if (h == null) return;
        Item tabard = game.getItemDataManager().createItem(Item.ItemType.TABARD, 0, 0,
                com.bpm.minotaur.gamedata.item.ItemColor.TAN, game.getAssetManager());
        if (tabard == null) return;
        tabard.setHeraldry(h.doctrineId);
        tabard.setTrophyHouseId(houseId);
        tabard.setFriendlyName(com.bpm.minotaur.ui.UiGlyphs.sanitize("Tabard of " + h.name));
        if (player.getInventory().pickup(tabard)) say("You are given the tabard of " + h.name + ".", 4f);
    }

    /** Whether the player stands in Castle Tarmin's halls, beneath the castle (W36). */
    private boolean inTheHalls(WarManager.Ground g) {
        return g.level == com.bpm.minotaur.gamedata.history.war.Siege.HALLS_LEVEL && g.chunk != null
                && g.chunk.equals(worldManager.castleSite());
    }

    /**
     * Bumping the breach in a siege, or the castle gate once three seals open it: down into the
     * castle's halls (W35, W36). The breach asks one seal of a friend of the besieger, two of anyone
     * else. True when the bump was answered, entered or not.
     */
    private boolean intoTheCastle(Scenery s) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        int held = com.bpm.minotaur.gamedata.blight.CastleGate.sealsHeld();
        if (com.bpm.minotaur.gamedata.shelter.SealedGates.BREACH_PROP.equals(s.getPropId())) {
            int besieger = com.bpm.minotaur.gamedata.history.war.Siege.besieger(history.world());
            if (besieger < 0) return false;
            int need = com.bpm.minotaur.gamedata.history.war.Siege.sealsForBreach(history.stanceOf(besieger),
                    history.oath().houseId == besieger);
            if (held < need) {
                say("The breach is choked with the Legion's living and dead. Forcing it takes " + need
                        + (need == 1 ? " seal" : " seals") + "; you hold " + held + ".", 5f);
                return true;
            }
            say("You go in through the breach, under the siege fire, into the halls of Castle Tarmin.", 5f);
        } else {
            say("The gate of Castle Tarmin grinds open for the seals you carry. You go down into its halls.", 5f);
        }
        if (combatManager != null) combatManager.endCombat();
        GridPoint2 at = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        worldManager.descendLevel(at);
        worldManager.clearLoadedChunks();
        generateLevel(currentLevel());
        GridPoint2 safe = WorldManager.findSafeArrivalTile(maze, at.x, at.y);
        if (safe != null) player.getPosition().set(safe.x + 0.5f, safe.y + 0.5f);
        dressTheHalls();
        // Joining the assault is the besieger's contract (W37).
        com.bpm.minotaur.gamedata.history.favour.Contract c = history.oath().contract;
        if (c != null && !c.done && c.kind == com.bpm.minotaur.gamedata.history.favour.Contract.Kind.SIEGE_ASSAULT) {
            c.done = true;
            say("You have joined the assault. Return to a war camp of your house to be paid.", 5f);
        }
        return true;
    }

    /**
     * The halls (W36): the throne room's inner door, sealed; and while a siege holds, the Legion
     * thinned by the fighting on the walls -- once a siege.
     */
    private void dressTheHalls() {
        if (maze == null) return;
        boolean door = false;
        for (Scenery sc : maze.getScenery().values()) {
            if (com.bpm.minotaur.gamedata.shelter.SealedGates.THRONE_DOOR_PROP.equals(sc.getPropId())) door = true;
        }
        if (!door) {
            GridPoint2 at = WorldManager.findSafeArrivalTile(maze, maze.getWidth() / 2, maze.getHeight() / 2);
            Scenery inner = at == null ? null : Scenery.fromProp(com.bpm.minotaur.gamedata.shelter.SealedGates.THRONE_DOOR_PROP, at.x, at.y);
            if (inner != null) maze.addScenery(inner);
        }
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.War siege = history.world().activeSiege();
        if (siege == null) return;
        String key = "HALLS:" + siege.id + ":" + siege.siegeSince;
        com.bpm.minotaur.gamedata.history.war.EncounterLedger ledger = history.encounterLedger();
        if (ledger.spent.contains(key)) return;
        ledger.spent.add(key);
        int i = 0;
        for (com.bpm.minotaur.gamedata.monster.Monster m : new java.util.ArrayList<>(maze.getMonsters().values())) {
            if (m == null || m.isThemeChampion() || m.isWarBand()) continue;
            if (i++ % 2 == 0) maze.removeMonster(m);
        }
        say("Half the Legion is on the walls. The halls are thinly held.", 4f);
    }

    /** Chunks from the castle the red lightning is seen when the Antlered Lord answers a siege (W34). */
    private static final int RED_LIGHTNING_REACH = 8;
    private static final int RED_LIGHTNING_EVERY = 40;

    /** A siege's presence on the castle's ground: the breach in its wall, and Tarmin answering in red lightning. */
    private void tendTheSiege(WarManager.Ground g) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.War siege = history.world().activeSiege();
        GridPoint2 castle = worldManager.castleSite();
        if (castle == null || g.chunk == null || g.level != 1) return;
        if (g.chunk.equals(castle) && maze != null) keepTheBreach(siege != null);
        if (siege == null) return;
        long clock = history.warClock();
        if (com.bpm.minotaur.gamedata.history.war.EncounterScheduler.distance(g.chunk, castle) <= RED_LIGHTNING_REACH
                && clock % RED_LIGHTNING_EVERY == 0) {
            if (worldManager.getWeatherManager() != null) worldManager.getWeatherManager().knellFlash(0.5f);
            if (soundManager != null) soundManager.playSound("lightning_crash_2", 0.7f);
            say("Red lightning breaks over Castle Tarmin. The Antlered Lord answers the siege.", 3f);
        }
    }

    /** The nearest clear tile reachable from {@code from} without crossing a wall edge, two or more steps from {@code gate}. */
    private GridPoint2 nearestClear(GridPoint2 from, GridPoint2 gate) {
        java.util.ArrayDeque<GridPoint2> queue = new java.util.ArrayDeque<>();
        java.util.Set<GridPoint2> seen = new java.util.HashSet<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty() && seen.size() < 200) {
            GridPoint2 c = queue.poll();
            boolean clear = !maze.getScenery().containsKey(c) && !maze.getMonsters().containsKey(c) && !maze.getItems().containsKey(c);
            if (clear && Math.abs(c.x - gate.x) + Math.abs(c.y - gate.y) >= 2) return c;
            for (com.bpm.minotaur.gamedata.Direction d : com.bpm.minotaur.gamedata.Direction.values()) {
                GridPoint2 n = new GridPoint2(c.x + (int) d.getVector().x, c.y + (int) d.getVector().y);
                if (seen.contains(n) || maze.isWallBlocking(c.x, c.y, d) || !maze.isPassable(n.x, n.y)) continue;
                seen.add(n);
                queue.add(n);
            }
        }
        return null;
    }

    /** The breach stands beside the castle gate while a siege holds, and is gone when it ends (W35). */
    private void keepTheBreach(boolean siege) {
        GridPoint2 breach = null, gate = null;
        for (java.util.Map.Entry<GridPoint2, Scenery> e : maze.getScenery().entrySet()) {
            if (com.bpm.minotaur.gamedata.shelter.SealedGates.BREACH_PROP.equals(e.getValue().getPropId())) breach = e.getKey();
            if (com.bpm.minotaur.gamedata.blight.CastleGate.isCastleGate(e.getValue())) gate = e.getKey();
        }
        if (!siege) {
            if (breach != null) maze.removeScenery(breach.x, breach.y);
            return;
        }
        if (breach != null || gate == null) return;
        // Outside the wall, near the gate: the nearest clear tile at least two steps from it that can be
        // walked to from its doorstep. Walls here are edges, so the search never crosses one.
        GridPoint2 at = null;
        for (com.bpm.minotaur.gamedata.Direction d : com.bpm.minotaur.gamedata.Direction.values()) {
            int sx = gate.x + (int) d.getVector().x, sy = gate.y + (int) d.getVector().y;
            if (maze.isWallBlocking(gate.x, gate.y, d) || !maze.isPassable(sx, sy)) continue;
            at = nearestClear(new GridPoint2(sx, sy), gate);
            if (at != null) break;
        }
        Scenery rubble = at == null ? null : Scenery.fromProp(com.bpm.minotaur.gamedata.shelter.SealedGates.BREACH_PROP, at.x, at.y);
        if (rubble != null) {
            maze.addScenery(rubble);
            say("The siege has breached the castle wall. Rubble and fire mark the gap.", 4f);
        }
    }

    /** Which soldiers take the player for kin, if the player wears a body their house fields (W16). */
    private java.util.function.Predicate<com.bpm.minotaur.gamedata.monster.Monster> disguise() {
        com.bpm.minotaur.gamedata.polymorph.PlayerForm form = player != null ? player.getForm() : null;
        String type = form != null ? form.monsterType() : null;
        if (type == null) return null;
        com.bpm.minotaur.gamedata.history.HistoryWorld world = worldManager.getHistory().world();
        com.bpm.minotaur.gamedata.history.DoctrineCatalog catalog = com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance();
        return m -> {
            int house = worldManager.houseOf(m);
            return house >= 0 && com.bpm.minotaur.gamedata.history.war.Disguise.passesAmong(world, catalog, house, type);
        };
    }

    private final com.bpm.minotaur.managers.WarAudio warAudio = new com.bpm.minotaur.managers.WarAudio();

    /** The war heard from where the player stands, this turn (Living War W9). */
    private void hearTheWar(WarManager.Ground g) {
        com.bpm.minotaur.managers.HistoryManager history = worldManager.getHistory();
        com.bpm.minotaur.gamedata.history.House holder = gashHolderHere(g);
        boolean gashAtWar = holder != null && history.world().activeWarCount(holder.id) > 0;
        com.bpm.minotaur.gamedata.history.war.WarSoundscape.Mix mix = com.bpm.minotaur.gamedata.history.war.WarSoundscape.mix(
                worldManager.currentEncounters(), worldManager.currentFronts(), history.warClock(), g.chunk, g.level,
                player != null ? player.getFacing() : null, gashAtWar);
        // The din of a fight here or next door covers the player's footsteps (W13).
        monsterAiManager.setHearingMuffle(g.level == 1 && mix.volume(com.bpm.minotaur.gamedata.history.war.WarSoundscape.Bed.NEAR) > 0f
                ? com.bpm.minotaur.managers.MonsterAiManager.BATTLE_DIN : 1f);
        boolean shook = warAudio.onTurn(mix, soundManager);
        if (shook && g.level >= 2) {
            if (worldManager.getLightingManager() != null) worldManager.getLightingManager().tremble(0.9f);
            if (com.badlogic.gdx.math.MathUtils.random() < 0.35f) {
                eventManager.addEvent(new GameEvent("The stone shudders. Dust sifts down from the ceiling.", 3f));
            }
        }
    }

    private void triggerAmbientMonsterSound() {
        if (soundManager == null || maze == null || player == null) return;
        GridPoint2 pp = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        List<com.bpm.minotaur.gamedata.monster.Monster> candidates = new ArrayList<>();
        for (Map.Entry<GridPoint2, com.bpm.minotaur.gamedata.monster.Monster> entry : maze.getMonsters().entrySet()) {
            com.bpm.minotaur.gamedata.monster.Monster m = entry.getValue();
            if (m != null && m.getCurrentHP() > 0 && !m.isAlly()) {
                int dist = Math.abs(entry.getKey().x - pp.x) + Math.abs(entry.getKey().y - pp.y);
                if (dist >= 2 && dist <= 14) {
                    candidates.add(m);
                }
            }
        }
        if (!candidates.isEmpty()) {
            com.bpm.minotaur.gamedata.monster.Monster chosen = candidates.get(com.badlogic.gdx.math.MathUtils.random(candidates.size() - 1));
            soundManager.playAmbientMonsterSound(
                    chosen.getPosition().x, chosen.getPosition().y,
                    soundManager.isBossOrMegabeast(chosen),
                    player.getPosition().x, player.getPosition().y,
                    player.getFacing());
        }
    }

    /** How much of the myth the soul may know now: by the Hunger, or all of it at the last stand (#288). */
    private static com.bpm.minotaur.gamedata.history.Reveal.Layer revealLayer() {
        DoomManager doom = DoomManager.getInstance();
        return com.bpm.minotaur.gamedata.history.Reveal.layerFor(doom.getDeathCount(), doom.isBridgeBossActive());
    }

    /** Tells the next revelation the soul has earned and not read; false if there is none. */
    private boolean revealIfDue() {
        String revelation = com.bpm.minotaur.gamedata.history.Reveal.readNext(
                com.bpm.minotaur.managers.WorldLogManager.getInstance().log(), revealLayer());
        if (revelation == null) return false;
        com.bpm.minotaur.managers.WorldLogManager.getInstance().save();
        eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize("You understand: " + revelation), 10f));
        return true;
    }

    /** A House's account of one of the soul's lives (#285): read now, kept in the Book of Lives. */
    private void readTelling(Item item) {
        player.getInventory().removeItem(item);
        com.bpm.minotaur.gamedata.worldlog.Telling t =
                com.bpm.minotaur.managers.WorldLogManager.getInstance().findTelling(item.getTrophyHouseId());
        if (t == null) {
            eventManager.addEvent(new GameEvent("The writing is too far gone to read.", 3f));
            return;
        }
        eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(t.teller + ": " + t.plain), 8f));
    }

    /** Every pickup is toasted; a fragment of the Maze's history is read on the spot (plan T1.10). */
    private void onItemPickedUp(Item item) {
        hud.showPickupToast(item);
        // A Telling is a fragment of one of the soul's lives, not of the history's events: it is
        // read from the World Log, where the Tellings are kept (#285).
        if (item != null && item.getType() == Item.ItemType.HOUSE_TELLING) {
            readTelling(item);
            return;
        }
        if (item == null || item.fragmentKind() == null || item.isTrophyRead()) return;
        if (item.isChronicleFragment()) player.getInventory().removeItem(item);
        item.markTrophyRead();
        // A page or proclamation may also give up a revelation of the myth the soul has earned,
        // before the history's own news (#288). A trophy tells only its own story.
        if (item.isChronicleFragment()) revealIfDue();
        com.bpm.minotaur.gamedata.history.HistoryEvent told = worldManager.getHistory().readFragment(item.fragmentKind());
        if (told == null) {
            eventManager.addEvent(new GameEvent("The writing is too far gone to read.", 3f));
            return;
        }
        if (told.type == com.bpm.minotaur.gamedata.history.EventType.SEAT_SEIZED || told.type == com.bpm.minotaur.gamedata.history.EventType.HOUSE_EXTINGUISHED) {
            if (soundManager != null) soundManager.playHouseBreachWarning();
        } else if (told.type == com.bpm.minotaur.gamedata.history.EventType.WAR_DECLARED) {
            if (soundManager != null) soundManager.playGashesWarWail(false);
        } else if (told.type == com.bpm.minotaur.gamedata.history.EventType.MEGABEAST_STIRS || told.type == com.bpm.minotaur.gamedata.history.EventType.MEGABEAST_RAID) {
            if (soundManager != null) soundManager.playBossWarningAlarm();
        }
        com.bpm.minotaur.gamedata.history.HistoryWorld world = worldManager.getHistory().world();
        // Whoever wrote it took a side; which side depends on the event, so a reload reads the same.
        com.bpm.minotaur.gamedata.history.text.Chronicler teller = com.bpm.minotaur.gamedata.history.text.Chronicler.of(
                world, told, told.id % 2 == 0 ? com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.Bias.FOR
                        : com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.Bias.AGAINST,
                com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance());
        String text = com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.getInstance().render(world, told, teller);
        eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(teller.byline + ": " + text), 8f));
    }

    /**
     * Instantly returns the player to the bed of the shelter they last rested in (home if none),
     * ending active combat and saving world state. Used by Word of Recall and safe return mechanisms.
     */
    public void returnToShelter() {
        if (combatManager != null) {
            combatManager.endCombat();
        }
        if (worldManager != null && maze != null) {
            worldManager.saveCurrentChunk(maze);
        }
        worldManager.setCurrentLevel(1);
        GridPoint2 restChunk = com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().getRestChunk();
        Maze shelterMaze = loadShelterChunk(restChunk != null ? restChunk : new GridPoint2(0, 0));
        swapToChunk(shelterMaze);

        GridPoint2 bedPos = shelterWakeTile(shelterMaze);
        player.setPosition(bedPos);
        worldManager.saveCurrentChunk(shelterMaze);
        triggerAutoSave();

        if (hud != null) {
            hud.addMessage("You recite the Word of Recall and return safely to the Shelter.");
        }
        if (eventManager != null) {
            eventManager.addEvent(new GameEvent("Returned safely to the Shelter.", 3f));
        }
    }

    /** Play-tests: moves the player into a surface chunk at once, as a walk through its gate would. */
    public void travelToChunk(GridPoint2 chunk, GridPoint2 tile) {
        worldManager.transitionPlayerToChunk(player, chunk, tile);
        if (worldManager.getCurrentMaze() != null && worldManager.getCurrentMaze() != maze) {
            swapToChunk(worldManager.getCurrentMaze());
        }
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
        hud.setGameScreen(this); // the quick-slot menu and the silhouette widget need it after a chunk swap too
        player.setItemPickupListener(this::onItemPickedUp);
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        combatManager.setHud(hud);
        DebugRenderer.printMazeToConsole(maze);
    }

    /**
     * Ages the lingering clouds in this chunk by one world turn.
     *
     * <p>Wind only reaches a cloud that is out under the sky: on the surface, and not beneath a
     * roof. Underground there is no weather, so fog holds for its full duration -- which is the
     * difference the player is meant to feel between casting it in a storm and casting it in a
     * corridor.
     */
    private void tickAreaEffects() {
        if (maze == null || !maze.hasAreaEffects()) {
            return;
        }
        // The wind is the weather's, not the player's. Asking whether the *player* is under
        // the sky would have stopped an outdoor cloud dispersing the moment they stepped
        // indoors; the manager tests each tile itself.
        float windSpeed = 0f;
        if (worldManager != null && worldManager.getWeatherManager() != null) {
            worldManager.getWeatherManager().getWindVector(scratchWind);
            windSpeed = scratchWind.len();
        }
        maze.getAreaEffects().tick(maze, windSpeed);
        syncObscuredStatus();
    }

    /**
     * Keeps the player's OBSCURED status matching the tile they are standing on.
     *
     * <p>Fog is a place, not something cast on you, so the status is derived rather than
     * inflicted -- walk in and it appears, walk out and it clears. Without it the player has
     * no on-screen answer to "why did my shot miss".
     *
     * <p>The entering and leaving lines fire on the transition only, and the transition is
     * tracked here rather than read back off the status. The status cannot be the source of
     * truth: {@code StatusManager.updateTurn()} runs earlier in {@code playerTurnTakesAction}
     * than this does, so whatever this applies has already been aged once before the next turn
     * reads it. Reading it back made every turn in a cloud look like a fresh entry, which
     * re-announced the fog every turn and meant leaving it was never announced at all.
     */
    private void syncObscuredStatus() {
        if (player == null || player.getStatusManager() == null) {
            return;
        }
        boolean inFog = maze != null
                && maze.isObscured((int) player.getPosition().x, (int) player.getPosition().y);

        if (inFog == playerWasObscured) {
            if (inFog) {
                // Hold the pill lit. StatusManager.updateTurn() runs earlier in the same turn
                // than this does, so a status applied here with a duration of 1 would already
                // have been decremented to nothing by the time the next turn asked about it.
                player.getStatusManager().addEffect(StatusEffectType.OBSCURED, OBSCURED_STATUS_TURNS, 1, false);
            }
            return;
        }

        playerWasObscured = inFog;
        if (inFog) {
            player.getStatusManager().addEffect(StatusEffectType.OBSCURED, OBSCURED_STATUS_TURNS, 1, false);
            eventManager.addEvent(new GameEvent("Fog closes over you. You can see nothing beyond arm's reach.", 2f));
        } else {
            player.getStatusManager().removeEffect(StatusEffectType.OBSCURED);
            eventManager.addEvent(new GameEvent("You step clear of the fog.", 1.5f));
        }
    }

    /**
     * Whether the player was standing in fog at the end of the last world turn.
     *
     * <p>The transition, not the status, is what the chronicle lines key off -- see
     * {@link #syncObscuredStatus}.
     */
    private boolean playerWasObscured;

    /** Turns the OBSCURED pill is applied for, so it survives one StatusManager.updateTurn(). */
    private static final int OBSCURED_STATUS_TURNS = 2;

    /** Reused: the wind vector is read once a turn. */
    private final Vector3 scratchWind = new Vector3();

    /**
     * Quick-slot menu: throws the item straight ahead. Exploration only, like the A key; in a fight
     * the combat menu owns the turn.
     */
    public void throwQuickSlotItem(int slotIdx) {
        Item item = player.getInventory().getQuickSlots()[slotIdx];
        if (item == null) {
            return;
        }
        if (combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE) {
            eventManager.addEvent(new GameEvent("You cannot throw that in the middle of a fight.", 1.5f));
            return;
        }
        if (combatManager.throwItem(item)) {
            playerTurnTakesAction();
        }
    }

    /** Quick-slot menu: drops the item at your feet (or the nearest free tile). */
    public void dropQuickSlotItem(int slotIdx) {
        Item item = player.getInventory().getQuickSlots()[slotIdx];
        if (item == null) {
            return;
        }
        if (player.dropItem(maze, item)) {
            player.getInventory().getQuickSlots()[slotIdx] = null;
            eventManager.addEvent(new GameEvent("Dropped " + item.getDisplayName(), 2f));
        } else {
            eventManager.addEvent(new GameEvent("No space to drop here.", 2f));
        }
    }

    private void playerTurnTakesAction() {
        if (MusicManager.getInstance().isResting()) {
            MusicManager.getInstance().setResting(false);
        }
        updateMusicTrackForCurrentZone();
        processPlayerStatusEffects();
        player.getStatusManager().updateTurn();
        checkForMimicInFront();
        combatManager.tickReload();
        tickPowderDampness();
        tickAreaEffects();
        if (player.getInjuryManager() != null) {
            player.getInjuryManager().updateStep(player, maze, eventManager);
        }
        if (monsterAiManager != null && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
            turnManager.processTurn(maze, player, monsterAiManager, combatManager, worldManager, eventManager, game.getItemDataManager(), game.getAssetManager());

            // Wading costs a second tick: the monsters act again while the
            // player is still hauling themselves through the water.
            int wadeTurns = player.consumePendingWadeTurns();
            for (int i = 0; i < wadeTurns
                    && combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE; i++) {
                turnManager.processTurn(maze, player, monsterAiManager, combatManager, worldManager, eventManager,
                        game.getItemDataManager(), game.getAssetManager());
            }
        }
        combatManager.checkForAdjacentMonsters();

        // --- Periodic Spawning Hook ---
        turnCount++;
        worldManager.processTurn(player, turnCount);
        // The wars go on, on the surface and in the strata alike, and may reach the player.
        worldManager.getHistory().tickWarClock();
        tickWar();
        tollKnell();
        com.bpm.minotaur.gamedata.history.town.Town town = worldManager.townHere();
        if (town != null && worldManager.getHistory().findTown(town.key)) {
            eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(
                    "You come to " + town.name + ", a town of " + town.allegiance.displayName + "."), 5f));
        }
        String beast = worldManager.tendMegabeasts(maze, player != null ? player.getPosition() : null);
        if (beast != null) eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(beast), 4f));
        String nemesis = worldManager.tendNemeses(maze, player != null ? player.getPosition() : null);
        if (nemesis != null) eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(nemesis), 4f));
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

    /** Allies this close to the player come with them through a ladder or gate. */
    private static final int ALLY_ESCORT_RADIUS = 3;
    private final List<Monster> pendingAllies = new ArrayList<>();

    /**
     * Takes the allies near the player out of the current maze, to arrive with them in the next. Must
     * run before the chunk is saved, or the saved chunk would keep a second copy.
     */
    private void collectEscortAllies() {
        if (maze == null || player == null) {
            return;
        }
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        List<GridPoint2> taken = new ArrayList<>();
        for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
            Monster m = entry.getValue();
            if (m != null && m.isAlly() && m.getCurrentHP() > 0
                    && Math.abs(entry.getKey().x - px) + Math.abs(entry.getKey().y - py) <= ALLY_ESCORT_RADIUS) {
                pendingAllies.add(m);
                taken.add(entry.getKey());
            }
        }
        for (GridPoint2 pt : taken) {
            maze.getMonsters().remove(pt);
        }
    }

    /** Sets the escorted allies down on free tiles beside the player, once the new maze is in place. */
    private void placePendingAllies() {
        if (pendingAllies.isEmpty() || maze == null || player == null) {
            return;
        }
        int px = (int) player.getPosition().x;
        int py = (int) player.getPosition().y;
        for (Monster ally : pendingAllies) {
            GridPoint2 spot = null;
            for (int r = 1; r <= ALLY_ESCORT_RADIUS && spot == null; r++) {
                for (int dx = -r; dx <= r && spot == null; dx++) {
                    for (int dy = -r; dy <= r && spot == null; dy++) {
                        int x = px + dx;
                        int y = py + dy;
                        if ((dx == 0 && dy == 0) || x < 0 || y < 0 || x >= maze.getWidth() || y >= maze.getHeight()) {
                            continue;
                        }
                        GridPoint2 tile = new GridPoint2(x, y);
                        if (maze.isPassable(x, y) && !maze.getMonsters().containsKey(tile)) {
                            spot = tile;
                        }
                    }
                }
            }
            if (spot != null) {
                ally.getPosition().set(spot.x + 0.5f, spot.y + 0.5f);
                maze.getMonsters().put(spot, ally);
            }
        }
        pendingAllies.clear();
    }

    private Monster resolveBumpTarget(int tx, int ty) {
        if (maze == null || player == null) return null;

        int px = (int) Math.floor(player.getPosition().x);
        int py = (int) Math.floor(player.getPosition().y);

        // A player cannot melee-strike through a solid wall, closed door, or closed gate
        if (!maze.canMeleeInto(px, py, tx, ty)) {
            return null;
        }

        GridPoint2 tile = new GridPoint2(tx, ty);

        Monster existing = maze.getMonsters().get(tile);
        if (existing != null) {
            // Walking into an ally swaps places (Player.move); it is never a target.
            return existing.isAlly() ? null : existing;
        }

        Item chest = com.bpm.minotaur.gamedata.monster.MimicReveal.disguisedMimicAt(maze, tile);
        if (chest != null && chest.isMimicSeen() && combatManager != null) {
            return combatManager.revealMimicPreEmptively(tile, currentLevel());
        }
        return null;
    }

    private final com.badlogic.gdx.InputAdapter tomeStudyBreaker = new com.badlogic.gdx.InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            // Face at a barred window: the view owns the keys until the player steps back.
            if (windowLook != null) {
                return handleWindowLookKey(keycode);
            }
            if (hearthLighting != null) {
                if (keycode == Input.Keys.ESCAPE) breakHearthLighting();
                return true;
            }
            if (player == null || player.getActiveTomeStudy() == null) {
                return false;
            }
            if (keycode == Input.Keys.ESCAPE) {
                return breakTomeStudy();
            }
            // General keys are ignored and consumed during channeled study
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            // The death cinematic owns the screen: a click must not drive gameplay mid-collapse.
            if (isDying()) {
                deathSequence.skip();
                return true;
            }
            if ((player != null && player.getActiveTomeStudy() != null) || hearthLighting != null
                    || windowLook != null) {
                // Mouse clicks are ignored during channeled study, and while looking out a window
                return true;
            }
            return false;
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

    /**
     * Fires whatever ranged attack the right hand holds: zaps a wand, shoots a bow, crossbow or
     * gun, or throws a thrown weapon. Bound to the fire key (F by default) and to A.
     *
     * @return true if something was fired
     */
    private boolean fireHeldRangedWeapon() {
        Item weapon = player.getInventory().getRightHand();
        if (weapon == null) return false;
        if (weapon.isWand()) {
            player.zap(weapon, player.getFacing(), discoveryManager, eventManager, maze, combatManager);
            playerTurnTakesAction();
            return true;
        }
        if (weapon.isRanged()) {
            boolean wasExploring = combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE;
            combatManager.playerAttackInstant();
            // Firing costs a turn. Opening fire out of exploration used to be
            // free: ammunition spent, the level woken, and no turn passed --
            // so the reload never advanced either. In-combat shots pass their
            // turn through the combat state machine instead.
            if (wasExploring) {
                playerTurnTakesAction();
            }
            return true;
        }
        if (weapon.isThrown() && combatManager.throwWeapon(weapon)) {
            playerTurnTakesAction();
            return true;
        }
        return false;
    }

    /** The hearth being lit, or null. Channelled like a Tome study: each world turn passes on its own. */
    private com.bpm.minotaur.gamedata.shelter.HearthLighting hearthLighting;
    private float hearthTimer;

    /** Starts lighting the cold hearth in front of the player, if they carry tinder. */
    private void beginHearthLighting() {
        if (hearthLighting != null) return;
        if (findTinder() == null) {
            eventManager.addEvent(new GameEvent("The hearth is cold. You need a Tinder Bundle to light it.", 2.5f));
            return;
        }
        hearthLighting = new com.bpm.minotaur.gamedata.shelter.HearthLighting(player.getStats().getWoundsTaken());
        hearthTimer = 0f;
        eventManager.addEvent(new GameEvent("You kneel at the cold hearth and work the tinder... ("
                + com.bpm.minotaur.gamedata.shelter.HearthLighting.TURNS + " turns). ESCAPE stops.", 2.5f));
    }

    private void updateHearthLighting(float delta) {
        if (hearthLighting == null || player == null) return;
        hearthTimer += delta;
        if (hearthTimer < TOME_STUDY_SECONDS_PER_TURN) return;
        hearthTimer = 0f;
        playerTurnTakesAction();
        com.bpm.minotaur.gamedata.shelter.HearthLighting.Step step =
                hearthLighting.afterTurn(player.getStats().getWoundsTaken());
        if (step == com.bpm.minotaur.gamedata.shelter.HearthLighting.Step.INTERRUPTED) {
            hearthLighting = null;
            eventManager.addEvent(new GameEvent("You are struck and the tinder scatters. The hearth stays cold.", 2.5f));
            return;
        }
        if (step != com.bpm.minotaur.gamedata.shelter.HearthLighting.Step.COMPLETE) return;
        hearthLighting = null;
        Item tinder = findTinder();
        if (tinder == null || !worldManager.claimShelter(maze)) {
            eventManager.addEvent(new GameEvent("The tinder will not catch.", 2f));
            return;
        }
        player.getInventory().consumeOne(tinder);
        worldManager.syncLightsForChunk(maze);
        if (soundManager != null) soundManager.playDoorOpenSound();
        eventManager.addEvent(new GameEvent("The hearth catches. This shelter is yours.", 3f));
        if (hud != null) hud.addMessage("Shelter claimed. Every station you have unlocked stands here now.");
        needsAsciiRender = true;
    }

    private Item findTinder() {
        for (Item it : player.getInventory().getAllItems()) {
            if (it != null && it.getType() == Item.ItemType.TINDER_BUNDLE) return it;
        }
        return null;
    }

    private boolean breakHearthLighting() {
        if (hearthLighting == null) return false;
        hearthLighting = null;
        eventManager.addEvent(new GameEvent("You stop. The hearth stays cold.", 2f));
        return true;
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

                // The bridge guardian follows from anywhere in the chunk, and is
                // never crowded out. Ordinary pursuit takes at most two monsters
                // in HashMap order from within 8 tiles of the gate, so without
                // this a boss chasing from across the chunk would simply be left
                // behind, and two goblins could win its slot by luck.
                for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
                    Monster m = entry.getValue();
                    if (m != null && m.isBridgeBoss()) {
                        pursuers.add(m);
                        toRemove.add(entry.getKey());
                    }
                }

                for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
                    Monster m = entry.getValue();
                    if (m != null && !m.isBridgeBoss() && !m.holdsCourt() && m.getMegabeastId() < 0
                            && !m.isAlly() && m.getState() == Monster.MonsterState.HUNTING && m.canOperateDoors()) {
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

        collectEscortAllies();
        worldManager.megabeastsFollow(maze, player.getPosition(), transitionGate.getTargetChunkId(), currentLevel());
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
            MonsterPursuitManager.getInstance().registerGatePursuit(pursuers, originChunk, targetChunk, arrivalTile, currentLevel());
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
                monster.scaleStats(currentLevel()); // Scale to current level just in case
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
        // --- Death cinematic owns the screen: no gameplay input, any key skips ---
        if (deathSequence.isActive()) {
            deathSequence.skip();
            return true;
        }

        // --- Forward keyboard input to active EncounterWindow modal ---
        if (hud != null && hud.getEncounterWindow() != null && hud.getEncounterWindow().isVisible()) {
            hud.getEncounterWindow().handleInput(keycode);
            return true;
        }

        // --- Forward keyboard input to an open choice event ---
        if (hud != null && hud.getEventWindow() != null && hud.getEventWindow().isVisible()) {
            hud.getEventWindow().handleInput(keycode);
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

        // --- Forward keyboard input to active LevelUpModal ---
        if (hud != null && hud.getLevelUpModal() != null && hud.getLevelUpModal().isVisible()) {
            hud.getLevelUpModal().handleInput(keycode);
            return true;
        }

        if (keycode == Input.Keys.PAGE_UP) {
            if (hud != null) {
                int step = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) ? 5 : 1;
                hud.scrollChronicle(step);
                return true;
            }
        }

        if (keycode == Input.Keys.PAGE_DOWN) {
            if (hud != null) {
                int step = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) ? 5 : 1;
                hud.scrollChronicle(-step);
                return true;
            }
        }

        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
            if (keycode == Input.Keys.UP) { // fixed: Ctrl chord scrolls the chronicle
                if (hud != null) {
                    hud.scrollChronicle(1);
                    return true;
                }
            } else if (keycode == Input.Keys.DOWN) { // fixed: Ctrl chord scrolls the chronicle
                if (hud != null) {
                    hud.scrollChronicle(-1);
                    return true;
                }
            }
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
                // The fire key shoots a held bow, crossbow or gun straight from the menu.
                if (keycode == SettingsManager.getInstance().getKey("FIRE_RANGED")) {
                    Item held = player.getInventory().getRightHand();
                    if (held != null && held.isRanged()) {
                        combatManager.playerAttackInstant();
                    } else {
                        hud.addMessage("You have no ranged weapon in hand.");
                    }
                    return true;
                }
                if (actionKeys.is(keycode, "SPELLBOOK")) {
                    openSpellbook();
                    return true;
                }
                if (actionKeys.is(keycode, "MOVE_FORWARD")) {
                    hud.combatMenu.navigateUp();
                    return true;
                }
                if (actionKeys.is(keycode, "MOVE_BACK")) {
                    hud.combatMenu.navigateDown();
                    return true;
                }
                // The attack key confirms ATTACK whatever the menu has selected.
                boolean attackKey = actionKeys.is(keycode, "ATTACK");
                switch (attackKey ? Input.Keys.ENTER : keycode) {
                    case Input.Keys.I:
                        InventoryScreen invScreen = new InventoryScreen(game, this, player, maze,
                                InventoryScreen.InventoryMode.NORMAL);
                        game.setScreen(invScreen);
                        return true;
                    case Input.Keys.ENTER:
                    case Input.Keys.SPACE:
                        int selection = hud.combatMenu.getSelectedIndex();
                        if (attackKey)
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
            // Movement must not walk the player out of the menu. The hand and pack keys
            // fall through: they work in the menu without spending the turn.
            if (keycode == Input.Keys.W
                    || actionKeys.resolve(keycode, MENU_SWALLOWED_ACTIONS) != null
                    || keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER) {
                return true;
            }
        }

        // --- Debug Shortcuts ---
        if (debugManager.isDebugOverlayVisible()) {
            if (keycode == Input.Keys.A && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) // fixed: debug chord
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))) {
                giveAllArmor();
                return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE
                || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN
                || combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU) {
            String handAction = actionKeys.resolve(keycode, ActionKeys.HAND_ACTIONS);
            switch (handAction == null ? "" : handAction) {
                case "SWAP_HANDS":
                    player.getInventory().swapHands();
                    // If in Menu, we don't pass turn, just update UI
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN)
                        combatManager.passTurnToMonster();
                    return true;
                case "SWAP_PACK":
                    if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                        Vector2 dir = player.getFacing().getVector();
                        int fx = (int) (player.getPosition().x + dir.x);
                        int fy = (int) (player.getPosition().y + dir.y);
                        GridPoint2 targetTile = new GridPoint2(fx, fy);
                        GridPoint2 currentTile = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);

                        // Check Decomposing Corpse (Hero Remains / NetHack bones)
                        Scenery scFront = (maze != null && maze.getScenery() != null) ? maze.getScenery().get(targetTile) : null;
                        Scenery scFeet = (maze != null && maze.getScenery() != null) ? maze.getScenery().get(currentTile) : null;
                        if ((scFront != null && scFront.isCorpse()) || (scFeet != null && scFeet.isCorpse())) {
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
                case "ROTATE_PACK":
                    player.getInventory().rotatePack();
                    if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN)
                        combatManager.passTurnToMonster();
                    return true;
            }
        }

        if (combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            // Quick heal (#190) spends the combat turn when it uses something.
            if (actionKeys.is(keycode, "QUICK_HEAL")) {
                if (player.quickHeal(discoveryManager, eventManager, maze, combatManager)) {
                    combatManager.passTurnToMonster();
                }
                return true;
            }
            // --- Attack key / SPACE = Instant Attack ---
            if (actionKeys.is(keycode, "ATTACK") || keycode == Input.Keys.SPACE) {
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
            String worldAction = actionKeys.resolve(keycode, ActionKeys.WORLD_ACTIONS);
            boolean steering = worldAction != null && MOVEMENT_ACTIONS.contains(worldAction);

            // --- CONFUSION LOGIC ---
            if (steering && player.getStatusManager().hasEffect(com.bpm.minotaur.gamedata.effects.StatusEffectType.CONFUSED)) {
                // Simple random walk
                worldAction = MOVEMENT_ACTIONS.get(com.badlogic.gdx.math.MathUtils.random(MOVEMENT_ACTIONS.size() - 1));
            }
            // -----------------------

            // --- NEW: Open Combat Menu Logic ---
            if (keycode == Input.Keys.SPACE) {
                combatManager.openMenu();
                return true;
            }

            boolean isMovementKey = (steering ||
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

            boolean fireKey = actionKeys.is(keycode, "FIRE_RANGED");
            if (actionKeys.is(keycode, "ATTACK") || fireKey) {
                if (fireHeldRangedWeapon()) {
                    return true;
                }
                if (fireKey) {
                    hud.addMessage("You have nothing in hand to fire or throw.");
                    return true;
                }
            }

            if (keycode == Input.Keys.PERIOD) {
                hud.addMessage("You wait...");
                playerTurnTakesAction();
                return true;
            }

            // Rebindable actions, read through their Settings binding (#171).
            switch (worldAction == null ? "" : worldAction) {
                case "MOVE_FORWARD": {
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
                            // A chunk gate moves the player a frame later, when
                            // the queued transition event is handled, so the
                            // position is still unchanged here. Without this the
                            // game clanks and reports a solid wall every single
                            // time the player walks through a gate.
                            boolean gateTransition = player.consumeQueuedChunkTransition();
                            boolean harvestedScenery = player.consumeHarvestedScenery();
                            if (gateTransition
                                    || harvestedScenery
                                    || player.getPosition().x != prevX
                                    || player.getPosition().y != prevY) {
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
                case "MOVE_BACK": {
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
                        boolean gateTransition = player.consumeQueuedChunkTransition();
                        boolean harvestedScenery = player.consumeHarvestedScenery();
                        if (gateTransition
                                || harvestedScenery
                                || player.getPosition().x != prevX
                                || player.getPosition().y != prevY) {
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
                case "TURN_LEFT":
                    weaponOverlay.addTurnSway(-1.0f);
                    player.turnLeft();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                case "TURN_RIGHT":
                    weaponOverlay.addTurnSway(1.0f);
                    player.turnRight();
                    playerTurnTakesAction();
                    needsAsciiRender = false;
                    return true;
                case "INTERACT":
                    interactWithWorldObject();
                    return true;
                case "PICKUP_DROP":
                    pickupWorldItem();
                    return true;
                case "USE_ITEM":
                    player.useItem(player.getInventory().getRightHand(), eventManager, this.discoveryManager, maze, combatManager);
                    // A Tome study spends its own turns as it is channelled.
                    if (player.getActiveTomeStudy() == null) {
                        playerTurnTakesAction();
                    }
                    return true;
                case "DESCEND":
                    ascendOrDescendLadder();
                    return true;
                case "FIRST_AID":
                    // Pressing the First Aid key with nothing to tend is the exact muscle-memory
                    // moment that caused the "way off again" balance confusion -- R used to be the
                    // general rest-heal before it was repurposed to open First Aid, and that removal
                    // shipped with no in-fiction sign anything had changed. Turning this specific
                    // keypress into the teaching moment costs nothing extra: this branch already
                    // needed to check for an active injury to decide what to do.
                    if (player.getInjuryManager() != null && player.getInjuryManager().hasAnyInjuries()) {
                        openFirstAidModal();
                    } else {
                        eventManager.addEvent(new GameEvent(
                                "Nothing here needs tending. Press " + actionKeys.keyName("QUICK_HEAL")
                                        + " to heal: Cure Wounds first, then a poultice or potion.", 2.5f));
                    }
                    return true;
                case "QUICK_HEAL":
                    if (player.quickHeal(discoveryManager, eventManager, maze, combatManager)) {
                        playerTurnTakesAction();
                    }
                    return true;
                case "FIELD_CRAFTING":
                    openFieldCrafting();
                    return true;
                case "FIELD_COOKING":
                    openFieldCooking();
                    return true;
            }

            switch (keycode) {
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
            }
        }

        if (keycode == SettingsManager.getInstance().getKey("MAP")) {
            if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE) {
                game.setScreen(gameMode == GameMode.CLASSIC
                        ? new ClassicMapScreen(game, player, maze, this)
                        : new com.bpm.minotaur.screens.map.ExpeditionMapScreen(game, player, maze, this));
            }
            return true;
        }

        if (keycode == SettingsManager.getInstance().getKey("SPELLBOOK")) {
            openSpellbook();
            return true;
        }

        if (keycode == SettingsManager.getInstance().getKey("SKILL_TREE")) {
            openSkillTree();
            return true;
        }

        if (keycode == SettingsManager.getInstance().getKey("TOGGLE_LANTERN")) {
            if (player.hasLantern()) {
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

        if (handleDebugKey(keycode)) {
            return true;
        }

        switch (keycode)

        {
            case Input.Keys.TAB:
                // With several items in the pickup toast, Tab swaps between them; otherwise it opens the legend.
                if (!hud.cycleToast()) {
                    hud.toggleControlsLegend();
                }
                return true;
            case Input.Keys.F1:
                debugManager.toggleOverlay();
                return true;
            case Input.Keys.F2:
                debugManager.toggleRenderMode();
                // Shifting into Retro is a dimensional shift; the toggle is a no-op inside the Void.
                if (debugManager.isTransitioning() && debugManager.isModernToRetroTransition()) {
                    soundManager.playDimensionalShiftSound();
                }
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
            case Input.Keys.F4: {
                // Cycle through the themed chunks so each one can actually be
                // walked. The seal and the wade cost are feel calls, not test
                // assertions.
                com.bpm.minotaur.generation.theme.ChunkTheme warped =
                        worldManager.debugWarpToTheme(player);
                maze = worldManager.getCurrentMaze();
                eventManager.addEvent(new GameEvent(
                        warped != null
                                ? "Debug warp: " + warped.getDisplayName()
                                : "Debug warp failed.",
                        2.5f));
                return true;
            }
            case Input.Keys.F5: {
                // Session-only: toggles the comprehensive shelter debug sandbox.
                // Materialises all 10 stations, maxes Altar blessing trees and ascension stats,
                // unlocks skill tree, opens all biome portals, and grants divinities/crests.
                com.bpm.minotaur.gamedata.progression.ShelterAltar altar =
                        com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance();
                boolean debugOn = !altar.isDebugAllUnlocked();
                altar.setDebugAllUnlocked(debugOn);

                if (debugOn) {
                    altar.materialiseAllStations(maze, game.getItemDataManager(), game.getAssetManager());
                    com.bpm.minotaur.managers.DivinityManager.getInstance().addDivinities(100);
                    altar.addCrestsOfValor(20);
                    java.util.List<String> allSpellIds = new java.util.ArrayList<>();
                    for (com.bpm.minotaur.gamedata.spells.SpellTemplate spell :
                            com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance().getAllSpells()) {
                        if (spell != null && spell.id != null) {
                            allSpellIds.add(spell.id);
                        }
                    }
                    com.bpm.minotaur.debug.DebugCheats.maxOutPlayer(player, allSpellIds);
                }
                eventManager.addEvent(new GameEvent(
                        "DEBUG MODE: " + (debugOn ? "ON - all shelter features unlocked; level 99 & all spells; debug keys listed top right" : "OFF (existing stations remain this session)"),
                        3f));
                return true;
            }
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
                if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) {
                    // Ctrl+F11 lines up the Sector Passage Gate texture; the panel closes itself.
                    gateUvTunerPanel.open();
                    return true;
                }
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
                    int targetLevel = currentLevel() + i;
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
                eventManager.addEvent(new GameEvent(String.format("3D FOV: %.0f deg", fov), 1.5f));
                return true;
            }
            case Input.Keys.RIGHT_BRACKET: {
                float fov = debugManager.adjustFov3d(1.0f);
                eventManager.addEvent(new GameEvent(String.format("3D FOV: %.0f deg", fov), 1.5f));
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
        if (isDying()) {
            return true;
        }
        if (hud != null && hud.stage != null) {
            Vector2 stageCoord = hud.stage.screenToStageCoordinates(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
            // The HUD dashboard is the bottom 200px (y in [0, 210] in 1920x1080 stage)
            if (stageCoord.y >= 0 && stageCoord.y <= 210) {
                int step = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) ? 3 : 1;
                int delta = (amountY < 0) ? step : -step;
                hud.scrollChronicle(delta);
                return true;
            }
        }
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
                if (item.getType().name().startsWith("SCROLL") || item.isScroll()) {
                    player.read(item, discoveryManager, eventManager, maze, combatManager);
                    playerTurnTakesAction();
                } else {
                    eventManager.addEvent(new GameEvent("You cannot read that!", 1.5f));
                }
                break;
            case ZAP:
                if (item.getType().name().startsWith("WAND") || item.isWand()) {
                    player.zap(item, player.getFacing(), discoveryManager, eventManager, maze, combatManager);
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
                player.useItem(item, eventManager, discoveryManager, maze, combatManager);
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

    private boolean isDisposed = false;

    @Override
    public void dispose() {
        if (isDisposed) return;
        isDisposed = true;
        com.bpm.minotaur.telemetry.TelemetryManager telemetryOnDispose = com.bpm.minotaur.telemetry.TelemetryManager.getInstance();
        if (telemetryOnDispose.getExpeditionEndListener() == returnUnlockRoller) {
            telemetryOnDispose.setExpeditionEndListener(null);
        }
        warAudio.dispose();
        if (debugLegendFont != null) {
            debugLegendFont.dispose();
        }
        emberOverlay.dispose();
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

    public World3DRenderer getWorld3DRenderer() {
        return world3DRenderer;
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

                collectEscortAllies();
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
        // A war camp's banner: where a house's oath is sworn and its contracts paid (Living War W20, W25).
        if (sceneryInFront != null && "war_banner".equals(sceneryInFront.getPropId())) {
            int house = campAt(target);
            if (house >= 0) {
                atCampBanner(house);
                return;
            }
        }
        Scenery sceneryAtFeet = (maze != null && maze.getScenery() != null) ? maze.getScenery().get(currentTile) : null;
        // A war camp's campfire: where friends and the sworn rest by the fire (Living War W18-W20).
        GridPoint2 fireTile = (sceneryInFront != null && "campfire".equals(sceneryInFront.getPropId())) ? target
                : (sceneryAtFeet != null && "campfire".equals(sceneryAtFeet.getPropId())) ? currentTile : null;
        if (fireTile != null) {
            int house = campAt(fireTile);
            if (house >= 0) {
                atCampFire(house);
                return;
            }
        }
        Scenery corpseScenery = (sceneryInFront != null && sceneryInFront.isCorpse()) ? sceneryInFront
                : (sceneryAtFeet != null && sceneryAtFeet.isCorpse()) ? sceneryAtFeet : null;

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

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.SHELTER_HEARTH_COLD) {
            beginHearthLighting();
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.SHELTER_HEARTH_LIT) {
            eventManager.addEvent(new GameEvent("Your fire burns. This shelter is yours.", 2f));
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_ALTAR) {
            ShelterAltarScreen altarScreen = new ShelterAltarScreen(game, this, player);
            game.setScreen(altarScreen);
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_ARCHIVE_LECTERN) {
            if (soundManager != null) {
                soundManager.playBookFlipSound();
            }
            CodexScreen codexScreen = new CodexScreen(game, this, player);
            game.setScreen(codexScreen);
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_BOOK_OF_LIVES) {
            if (soundManager != null) {
                soundManager.playBookFlipSound();
            }
            game.setScreen(new WorldLogScreen(game, this, soulName(), keepers()));
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_WAR_TABLE) {
            if (soundManager != null) soundManager.playMapOpen();
            game.setScreen(new WarTableScreen(game, this, worldManager));
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_CARTOGRAPHER_DESK) {
            if (soundManager != null) soundManager.playMapOpen();
            game.setScreen(new CartographerDeskScreen(game, this, player, maze, worldManager));
            return;
        }

        if ((itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_TRAINING_DUMMY)
                || (itemOnTile != null && itemOnTile.getType() == Item.ItemType.HOME_TRAINING_DUMMY)) {
            openSkillTree();
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_CHEST) {
            ShelterChest chest;
            if (com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().isDebugAllUnlocked()) {
                chest = com.bpm.minotaur.debug.DebugCheats.allItemsChest(
                        game.getItemDataManager().getLoadedTypes(),
                        type -> game.getItemDataManager().createItem(type, 0, 0, ItemColor.GRAY, game.getAssetManager())
                );
            } else {
                chest = ShelterChest.getInstance();
                if (chest.isEmpty()) {
                    chest.load(game.getItemDataManager(), game.getAssetManager());
                }
            }
            ShelterChestScreen chestScreen = new ShelterChestScreen(game, this, player, chest);
            game.setScreen(chestScreen);
            return;
        }

        if (itemInFront != null && itemInFront.getType() == Item.ItemType.HOME_SLEEPING_BAG) {
            // A trait can make the bed less restful (Paranoid Scout: half).
            float rest = Math.min(1f, com.bpm.minotaur.gamedata.trait.TraitEffects.mult("bedRestMult"));
            // Taint first: it shrinks max HP, so clearing it after would leave the bed's heal short.
            if (player.getStats().getTaint() > 0f) {
                player.getStats().setTaint(0f);
                eventManager.addEvent(new GameEvent("You sleep the Blight out of your bones.", 2.5f));
            }
            int hp = player.getStats().getCurrentHP();
            int mp = player.getStats().getCurrentMP();
            player.getStats().setCurrentHP(hp + Math.round((player.getStats().getMaxHP() - hp) * rest));
            player.getStats().setCurrentMP(mp + Math.round((player.getStats().getMaxMP() - mp) * rest));
            player.getStatusManager().clearEffects();
            DoomManager.getInstance().resetExpeditionTurns();
            // A season passes in the Maze while the player sleeps; they wake to its news.
            java.util.List<com.bpm.minotaur.gamedata.history.HistoryEvent> news = worldManager.getHistory().onSleep();
            // This shelter is now the one the player wakes in.
            if (currentLevel() == 1) {
                com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance()
                        .recordRest(worldManager.getCurrentPlayerChunkId());
            }
            if (worldManager != null && worldManager.getDayNightManager() != null) {
                com.bpm.minotaur.managers.DayNightManager dnm = worldManager.getDayNightManager();
                if (dnm.getPhase() == com.bpm.minotaur.managers.DayNightManager.Phase.NIGHT
                        || dnm.getPhase() == com.bpm.minotaur.managers.DayNightManager.Phase.DUSK) {
                    dnm.setTimeOfDay(com.bpm.minotaur.managers.DayNightManager.DAWN_SUNRISE);
                } else {
                    dnm.advanceMinutes(180f);
                }
            }
            SaveManager.getInstance().saveActiveSlot(player, worldManager);
            SaveManager.getInstance().backupActiveSlot();
            autoSaveTimer = 0f;
            soundManager.playDoorOpenSound();
            eventManager.addEvent(new GameEvent("You rest in the shelter bed. Health and mana restored. Game saved.", 3f));
            hud.addMessage("Rested in bed. HP/MP restored. Game saved.");
            // The season's great events toll Tarmin's Knell (plan K4); the rest is shelter rumour.
            tollKnell();
            news = new java.util.ArrayList<>(news);
            news.removeIf(e -> com.bpm.minotaur.gamedata.history.text.Knell.tolls(worldManager.getHistory().world(), e));
            // The news is a shelter rumour: it becomes known history, readable at the Lectern.
            for (com.bpm.minotaur.gamedata.history.HistoryEvent heard : com.bpm.minotaur.gamedata.history.text.Headlines.pick(news, 3)) {
                worldManager.getHistory().unlock(heard.id);
            }
            for (String rumour : com.bpm.minotaur.gamedata.history.text.Headlines.of(worldManager.getHistory().world(), news, 3,
                    com.bpm.minotaur.gamedata.history.text.ChronicleGrammar.getInstance(),
                    com.bpm.minotaur.gamedata.history.DoctrineCatalog.getInstance())) {
                eventManager.addEvent(new GameEvent(rumour, 6f));
            }
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

        // [O] Drink at a liquid tile (#193), when nothing else ahead takes the key.
        if (itemInFront == null && maze.getGameObjectAt(target.x, target.y) == null
                && Player.liquidSourceAt(maze, player.getPosition(), player.getFacing()) != null) {
            if (player.drinkFromMap(maze, eventManager)) {
                playerTurnTakesAction();
            }
            needsAsciiRender = true;
            return;
        }

        // Looking outside: in the 3D engine the view moves to the bars. The retro raycaster
        // has no free camera, so there the window only describes what lies beyond.
        if (maze.getGameObjectAt(target.x, target.y) instanceof Window
                && debugManager.getRenderEngine() == DebugManager.RenderEngine.PLANAR_3D) {
            startWindowLook(target);
        }

        player.interact(maze, eventManager, soundManager, gameMode, worldManager);
        playerTurnTakesAction();
        needsAsciiRender = true;
    }

    // ------------------------------------------------------------------
    // Looking out through a barred window
    // ------------------------------------------------------------------

    /** The player's face at a barred window, or null. The 3D view looks out through it. */
    private com.bpm.minotaur.rendering.WindowLook windowLook;
    /** Head turn from the arrow keys, in degrees per second. */
    private static final float WINDOW_LOOK_KEY_SPEED = 70f;
    /** Head turn from the mouse, in degrees per pixel. */
    private static final float WINDOW_LOOK_MOUSE_SPEED = 0.15f;

    public boolean isLookingOutWindow() {
        return windowLook != null;
    }

    /** Turns the head at the window, as the mouse would; for captures. No-op when not looking. */
    public void turnWindowLook(float yawDegrees, float pitchDegrees) {
        if (windowLook != null) windowLook.turn(yawDegrees, pitchDegrees);
    }

    private void startWindowLook(GridPoint2 windowTile) {
        windowLook = new com.bpm.minotaur.rendering.WindowLook(windowTile, player.getFacing());
        world3DRenderer.setWindowLook(windowLook);
        Gdx.input.setCursorCatched(true);
        eventManager.addEvent(new GameEvent("Mouse or arrows to look about. Esc to step back.", 4f));
    }

    private void endWindowLook() {
        if (windowLook == null) return;
        windowLook = null;
        world3DRenderer.setWindowLook(null);
        Gdx.input.setCursorCatched(false);
    }

    /** Turns the head at the window; anything that needs the player steps them back. */
    private void updateWindowLook(float delta) {
        if (windowLook == null) return;
        if (deathSequence.isActive()
                || debugManager.getRenderEngine() != DebugManager.RenderEngine.PLANAR_3D
                || (combatManager != null && combatManager.getCurrentState() != CombatManager.CombatState.INACTIVE)) {
            endWindowLook();
            return;
        }
        float yaw = 0f;
        float pitch = 0f;
        SettingsManager keys = SettingsManager.getInstance();
        if (Gdx.input.isKeyPressed(keys.getKey("TURN_LEFT"))) yaw += WINDOW_LOOK_KEY_SPEED * delta;
        if (Gdx.input.isKeyPressed(keys.getKey("TURN_RIGHT"))) yaw -= WINDOW_LOOK_KEY_SPEED * delta;
        if (Gdx.input.isKeyPressed(keys.getKey("MOVE_FORWARD"))) pitch += WINDOW_LOOK_KEY_SPEED * delta;
        if (Gdx.input.isKeyPressed(keys.getKey("MOVE_BACK"))) pitch -= WINDOW_LOOK_KEY_SPEED * delta;
        yaw -= Gdx.input.getDeltaX() * WINDOW_LOOK_MOUSE_SPEED;
        pitch -= Gdx.input.getDeltaY() * WINDOW_LOOK_MOUSE_SPEED;
        windowLook.turn(yaw, pitch);
    }

    /** At the window the arrows turn the head; the leave keys step back; nothing else acts. */
    private boolean handleWindowLookKey(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.SPACE
                || actionKeys.is(keycode, "SWAP_PACK") || actionKeys.is(keycode, "INTERACT")) {
            endWindowLook();
        }
        return true;
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
                        ShelterChest.getInstance(), // a stashed kit is owned, not lost (#102)
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
     * pack, or a field fire beside the player, and works with carried ingredients only (no
     * Shelter Chest access).
     */
    public void openFieldCooking() {
        if (!player.getInventory().hasItemOfType(Item.ItemType.COOKING_KIT) && !isBesideFieldFire()) {
            hud.addMessage("You need a Cooking Kit, or a field fire beside you, to cook in the field.");
            return;
        }
        try {
            CookingScreen cookingScreen = new CookingScreen(game, this, player, worldManager, true);
            game.setScreen(cookingScreen);
        } catch (Exception e) {
            Gdx.app.error("GameScreen", "Failed to open field cooking", e);
        }
    }

    /**
     * Cooking over a Tinder Bundle field fire (#191, #194): true while a burning field fire's
     * warmth reaches the player. One fire warms and cooks.
     */
    boolean isBesideFieldFire() {
        return maze != null && player != null
                && maze.fieldFireWarming((int) player.getPosition().x, (int) player.getPosition().y) != null;
    }

    public void openSpellbook() {
        if (combatManager != null && (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE ||
                combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN ||
                combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_MENU)) {
            game.setScreen(new SpellbookScreen(game, this, player, maze));
        }
    }

    public void openSkillTree() {
        if (combatManager.getCurrentState() == CombatManager.CombatState.INACTIVE ||
                combatManager.getCurrentState() == CombatManager.CombatState.PLAYER_TURN) {
            game.setScreen(new SkillTreeScreen(game, this, player, maze));
        }
    }

    public void pickupWorldItem() {
        // [P] Fill waterskin at a liquid tile (#193); a ground item there still comes first.
        if (!hasGroundItemAheadOrAtFeet()
                && Player.liquidSourceAt(maze, player.getPosition(), player.getFacing()) != null) {
            if (player.fillWaterskinFromMap(maze, eventManager)) {
                playerTurnTakesAction();
            }
            return;
        }
        player.interactWithItem(maze, eventManager, soundManager, discoveryManager);
        playerTurnTakesAction();
    }

    private boolean hasGroundItemAheadOrAtFeet() {
        Vector2 v = player.getFacing().getVector();
        GridPoint2 ahead = new GridPoint2((int) (player.getPosition().x + v.x), (int) (player.getPosition().y + v.y));
        GridPoint2 feet = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        return maze.getItems().get(ahead) != null || maze.getItems().get(feet) != null;
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
            soundManager.playLadderTransition();
            collectEscortAllies();
            GridPoint2 originLadderPos = new GridPoint2((int) ladder.getPosition().x, (int) ladder.getPosition().y);
            List<Monster> pursuers = new ArrayList<>();
            if (this.maze != null) {
                List<GridPoint2> toRemove = new ArrayList<>();
                for (Map.Entry<GridPoint2, Monster> entry : maze.getMonsters().entrySet()) {
                    Monster m = entry.getValue();
                    if (m != null && !m.isAlly() && !m.holdsCourt() && m.getMegabeastId() < 0 && m.getState() == Monster.MonsterState.HUNTING && m.canClimbLadders()) {
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

            int originLevel = currentLevel();
            worldManager.megabeastsFollow(maze, player.getPosition(), worldManager.getCurrentPlayerChunkId(),
                    ladder.getType() == Ladder.LadderType.DOWN ? originLevel + 1 : originLevel - 1);

            if (ladder.getType() == Ladder.LadderType.DOWN) {
                GridPoint2 ladderPos = new GridPoint2((int) ladder.getPosition().x,
                        (int) ladder.getPosition().y);
                worldManager.descendLevel(ladderPos);
                worldManager.clearLoadedChunks();
                generateLevel(currentLevel());
                player.getPosition().set(ladderPos.x + 0.5f, ladderPos.y + 0.5f);
                hud.addMessage("Descended into Strata (Depth " + (currentLevel() - 1) + ")");

                if (!pursuers.isEmpty()) {
                    MonsterPursuitManager.getInstance().registerLadderPursuit(pursuers, originLevel, currentLevel(), ladderPos, true);
                }
            } else {
                boolean success = worldManager.ascendLevel();
                if (success) {
                    worldManager.clearLoadedChunks();
                    generateLevel(currentLevel());
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
                    if (currentLevel() == 1) {
                        hud.addMessage("Ascended to the Overland Surface.");
                    } else {
                        hud.addMessage("Ascended to Strata (Depth " + (currentLevel() - 1) + ")");
                    }

                    if (!pursuers.isEmpty()) {
                        MonsterPursuitManager.getInstance().registerLadderPursuit(pursuers, originLevel, currentLevel(), arrivalPos, false);
                    }
                } else {
                    hud.addMessage("You cannot ascend any higher.");
                }
            }
            updateMusicTrackForCurrentZone();
            playerTurnTakesAction();
        }
    }

    /** The animation list, so the capture harness can stage an in-flight projectile. */
    public AnimationManager getAnimationManager() {
        return animationManager;
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

    private com.badlogic.gdx.graphics.g2d.BitmapFont debugLegendFont;

    /**
     * The debug-mode keys and what they do, in a plain readable font (not the game's pixel font) at
     * a small size, top right beside the minimap's reserved box (it used to take the same corner, so
     * the map was drawn over it). Only drawn while debug mode (F5) is on. The text comes from
     * DebugKeys, the same table that handles the keys.
     */
    private void renderDebugLegend() {
        if (!com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().isDebugAllUnlocked()) {
            return;
        }
        if (debugLegendFont == null) {
            debugLegendFont = new com.badlogic.gdx.graphics.g2d.BitmapFont(); // libGDX default Arial: crisp at 15px
            debugLegendFont.getRegion().getTexture().setFilter(
                    com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        }
        java.util.List<String> lines = com.bpm.minotaur.debug.DebugKeys.legend(true);
        float lineH = debugLegendFont.getLineHeight() + 2f;
        float width = 330f;
        float height = lines.size() * lineH + 16f;
        float x = com.bpm.minotaur.rendering.Hud.STAGE_WIDTH - com.bpm.minotaur.rendering.Hud.MINIMAP_MARGIN_RIGHT
                - com.bpm.minotaur.rendering.Hud.MINIMAP_MAX_SIZE - 10f - width;
        float top = com.bpm.minotaur.rendering.Hud.STAGE_HEIGHT - com.bpm.minotaur.rendering.Hud.MINIMAP_MARGIN_TOP;

        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(game.getViewport().getCamera().combined);
        shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.65f);
        shapeRenderer.rect(x, top - height, width, height);
        shapeRenderer.end();

        game.getBatch().setProjectionMatrix(game.getViewport().getCamera().combined);
        game.getBatch().begin();
        float y = top - 8f;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean heading = i == 0 || line.endsWith(":");
            debugLegendFont.setColor(heading ? com.badlogic.gdx.graphics.Color.GOLD : com.badlogic.gdx.graphics.Color.WHITE);
            debugLegendFont.draw(game.getBatch(), line, x + 8f, y);
            y -= lineH;
        }
        game.getBatch().end();
    }

    /** Opens a choice event (data/events.json) in the event window. */
    private void openChoiceEvent(String eventId) {
        com.bpm.minotaur.gamedata.events.EventDefinition def =
                com.bpm.minotaur.gamedata.events.EventCatalog.getInstance().get(eventId);
        if (def == null || hud == null || hud.getEventWindow() == null) {
            return;
        }
        if (weaponOverlay != null) {
            weaponOverlay.reset();
        }
        hud.getEventWindow().configure(player, maze, eventManager, game.getItemDataManager(),
                game.getMonsterDataManager(), game.getAssetManager());
        hud.getEventWindow().show(def, maze.getBiome().name());
    }

    /** Runs a debug-only key when debug mode is on; returns whether the key was one. */
    private boolean handleDebugKey(int keycode) {
        boolean debugMode = com.bpm.minotaur.gamedata.progression.ShelterAltar.getInstance().isDebugAllUnlocked();
        com.bpm.minotaur.debug.DebugKeys.Action action = com.bpm.minotaur.debug.DebugKeys.actionFor(keycode, debugMode);
        if (action == null) {
            return false;
        }
        switch (action) {
            case LEARN_ALL_SPELLS: {
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (com.bpm.minotaur.gamedata.spells.SpellTemplate t
                        : com.bpm.minotaur.gamedata.spells.SpellDataManager.getInstance().getAllSpells()) {
                    ids.add(t.getId());
                }
                int learned = com.bpm.minotaur.debug.DebugCheats.learnAllSpells(player, ids);
                eventManager.addEvent(new GameEvent("Debug: learned " + learned + " spells (" + ids.size() + " total)", 2.5f));
                return true;
            }
            case LEVEL_UP:
                com.bpm.minotaur.debug.DebugCheats.levelUp(player, eventManager);
                return true;
            case OPEN_ALL_ITEMS_CHEST: {
                game.setScreen(new ShelterChestScreen(game, this, player, com.bpm.minotaur.debug.DebugCheats.allItemsChest(
                        game.getItemDataManager().getLoadedTypes(),
                        type -> game.getItemDataManager().createItem(type, 0, 0, ItemColor.GRAY, game.getAssetManager()))));
                return true;
            }
            case REFILL:
                com.bpm.minotaur.debug.DebugCheats.refill(player);
                eventManager.addEvent(new GameEvent("Debug: HP, MP, food and water refilled", 2f));
                return true;
            case FIRE_CHOICE_EVENT: {
                com.bpm.minotaur.gamedata.events.EventDefinition def = com.bpm.minotaur.debug.DebugCheats.choiceEventFor(
                        com.bpm.minotaur.gamedata.events.EventCatalog.getInstance(), maze.getBiome().name(),
                        maze.getLevel(), new java.util.Random());
                if (def == null) {
                    eventManager.addEvent(new GameEvent("Debug: no choice event fits this place", 2f));
                } else {
                    openChoiceEvent(def.id);
                }
                return true;
            }
            case WARP_CASTLE_ROAD_SHELTER: {
                com.bpm.minotaur.generation.ShelterRoads roads = worldManager.getBiomeManager().getRoads();
                debugWarpRoad = com.bpm.minotaur.generation.ShelterRoads.CASTLE_ROAD;
                debugWarp(com.bpm.minotaur.debug.DebugCheats.roadShelter(roads, debugWarpRoad, debugWarpStop++),
                        "castle-road shelter " + debugWarpStop);
                return true;
            }
            case DESCEND_HERE:
                debugDescend();
                return true;
            case WARP_SEAL_COURT: {
                com.bpm.minotaur.generation.ShelterRoads roads = worldManager.getBiomeManager().getRoads();
                int road = -1;
                for (int k = 0; k < com.bpm.minotaur.generation.ShelterRoads.ROAD_COUNT; k++) {
                    int r = com.bpm.minotaur.debug.DebugCheats.sealRoad(k);
                    if (!com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().hasSeal(r)) {
                        road = r;
                        break;
                    }
                }
                if (roads == null || road < 0) {
                    eventManager.addEvent(new GameEvent("Debug: no seal lord left to face", 2f));
                    return true;
                }
                debugTeleport(roads.getRoad(road).getEnd(), SealCourt.COURT_LEVEL, m -> {
                    for (Monster mon : m.getMonsters().values()) {
                        if (mon != null && mon.getSealRole() == Monster.SEAL_LORD) {
                            return WorldManager.findSafeArrivalTile(m, (int) mon.getPosition().x + 4, (int) mon.getPosition().y);
                        }
                    }
                    return WorldManager.findSafeArrivalTile(m, m.getWidth() / 2, m.getHeight() / 2);
                });
                return true;
            }
            case WARP_TOWN: {
                GridPoint2 here = worldManager.getCurrentPlayerChunkId();
                for (int radius = 0; radius < 30; radius++) {
                    for (int level = com.bpm.minotaur.gamedata.history.town.TownSites.MIN_LEVEL;
                         level <= com.bpm.minotaur.gamedata.history.town.TownSites.MAX_LEVEL; level++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dy = -radius; dy <= radius; dy++) {
                                if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                                GridPoint2 c = new GridPoint2(here.x + dx, here.y + dy);
                                if (!com.bpm.minotaur.gamedata.history.town.TownSites.isTown(worldManager.getWorldSeed(), c, level)) continue;
                                debugTeleport(c, level, m -> WorldManager.findSafeArrivalTile(m, m.getWidth() / 2, m.getHeight() / 2));
                                return true;
                            }
                        }
                    }
                }
                eventManager.addEvent(new GameEvent("Debug: no town within 30 chunks", 2f));
                return true;
            }
            case BATTLE_HERE: {
                if (currentLevel() != 1) {
                    eventManager.addEvent(new GameEvent("Debug: battles are fought on the surface", 2f));
                    return true;
                }
                com.bpm.minotaur.gamedata.history.HistoryWorld hw = worldManager.getHistory().world();
                GridPoint2 here = worldManager.getCurrentPlayerChunkId();
                com.bpm.minotaur.gamedata.history.war.Front front;
                if (!hw.activeWars().isEmpty()) {
                    com.bpm.minotaur.gamedata.history.War war = hw.activeWars().get(0);
                    front = new com.bpm.minotaur.gamedata.history.war.Front(war.id, war.attackerId, war.defenderId, here);
                } else {
                    // No war under way: the first two houses fight anyway, and the history will not hear of it.
                    java.util.List<com.bpm.minotaur.gamedata.history.House> living = hw.livingHouses();
                    front = new com.bpm.minotaur.gamedata.history.war.Front(-1, living.get(0).id, living.get(1).id, here);
                }
                String horns = warManager.soundHorns(worldManager.getHistory(), front, here, worldManager.houseSeats());
                if (soundManager != null) soundManager.playWarCue(WarManager.Cue.HORNS);
                eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize(horns), 5f));
                return true;
            }
            case MEGABEAST_HERE: {
                for (com.bpm.minotaur.gamedata.history.Megabeast b : worldManager.getHistory().world().megabeasts()) {
                    if (!b.isAlive() || !b.isAwake(worldManager.getHistory().world().season())) continue;
                    worldManager.getHistory().startHunt(b.id, worldManager.getCurrentPlayerChunkId(), currentLevel());
                    worldManager.getHistory().hunt().readyAt = worldManager.getHistory().warClock();
                    eventManager.addEvent(new GameEvent(com.bpm.minotaur.ui.UiGlyphs.sanitize("Debug: " + b.name + " is coming"), 2f));
                    return true;
                }
                eventManager.addEvent(new GameEvent("Debug: no megabeast is awake", 2f));
                return true;
            }
            case WARP_SEAL_SITE: {
                com.bpm.minotaur.generation.ShelterRoads roads = worldManager.getBiomeManager().getRoads();
                debugWarpRoad = com.bpm.minotaur.debug.DebugCheats.sealRoad(debugWarpSeal++);
                debugWarp(roads == null ? null : roads.getRoad(debugWarpRoad).getEnd(), "seal site of road " + debugWarpRoad);
                return true;
            }
            case GRANT_SEAL: {
                com.bpm.minotaur.gamedata.shelter.ShelterNetwork net = com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance();
                int road = debugWarpRoad;
                for (int k = 0; road == com.bpm.minotaur.generation.ShelterRoads.CASTLE_ROAD || net.hasSeal(road); k++) {
                    if (k >= com.bpm.minotaur.generation.ShelterRoads.ROAD_COUNT) {
                        eventManager.addEvent(new GameEvent("Debug: every seal is already held", 2f));
                        return true;
                    }
                    road = com.bpm.minotaur.debug.DebugCheats.sealRoad(k);
                }
                net.awardSeal(road);
                eventManager.addEvent(new GameEvent("Debug: granted the seal of road " + road + " ("
                        + net.getSealCount() + "/" + com.bpm.minotaur.gamedata.blight.CastleGate.SEALS_REQUIRED + ")", 2.5f));
                return true;
            }
            case CLAIM_ROAD: {
                com.bpm.minotaur.generation.ShelterRoads roads = worldManager.getBiomeManager().getRoads();
                if (roads == null) {
                    eventManager.addEvent(new GameEvent("Debug: this world has no shelter roads", 2f));
                    return true;
                }
                com.bpm.minotaur.gamedata.shelter.ShelterNetwork.getInstance().claimRoad(roads.getRoad(debugWarpRoad));
                eventManager.addEvent(new GameEvent("Debug: claimed every shelter on road " + debugWarpRoad
                        + " (they light as they load)", 2.5f));
                return true;
            }
            default:
                return false;
        }
    }

    /** Debug warp cursors: which road, which stop on it, which seal site comes next. */
    private int debugWarpRoad = com.bpm.minotaur.generation.ShelterRoads.CASTLE_ROAD;
    private int debugWarpStop;
    private int debugWarpSeal;

    /** Sends the player to a surface chunk through the portal-warp path. */
    /** Debug: down one stratum, standing where the player stands. */
    private void debugDescend() {
        GridPoint2 at = new GridPoint2((int) player.getPosition().x, (int) player.getPosition().y);
        if (combatManager != null) combatManager.endCombat();
        worldManager.descendLevel(at);
        worldManager.clearLoadedChunks();
        generateLevel(currentLevel());
        GridPoint2 safe = WorldManager.findSafeArrivalTile(maze, at.x, at.y);
        if (safe != null) player.getPosition().set(safe.x + 0.5f, safe.y + 0.5f);
        hud.addMessage("Debug: descended to level " + currentLevel());
    }

    /** Debug: straight to a chunk of any level, standing where {@code arrival} says. */
    private void debugTeleport(GridPoint2 chunk, int level, java.util.function.Function<Maze, GridPoint2> arrival) {
        com.bpm.minotaur.telemetry.TelemetryManager.getInstance().markCheatsUsed();
        if (combatManager != null) combatManager.endCombat();
        if (maze != null) worldManager.saveCurrentChunk(maze);
        worldManager.setCurrentLevel(level);
        worldManager.clearLoadedChunks();
        Maze destination = worldManager.loadChunk(chunk);
        if (destination == null) {
            eventManager.addEvent(new GameEvent("Debug: " + chunk + " cannot be entered", 2f));
            return;
        }
        worldManager.setCurrentChunk(chunk);
        swapToChunk(destination);
        GridPoint2 at = arrival.apply(destination);
        if (at != null) player.setPosition(at);
        needsAsciiRender = true;
        eventManager.addEvent(new GameEvent("Debug: teleported to " + chunk + " on level " + level, 2f));
    }

    /** The war manager, for the play-test driver. */
    public WarManager getWarManager() {
        return warManager;
    }

    private void debugWarp(GridPoint2 chunk, String label) {
        if (chunk == null) {
            eventManager.addEvent(new GameEvent("Debug: this world has no shelter roads", 2f));
            return;
        }
        if (currentLevel() != 1) {
            eventManager.addEvent(new GameEvent("Debug: warp only works on the surface", 2f));
            return;
        }
        Maze destination = worldManager.loadChunk(chunk);
        if (destination == null) {
            eventManager.addEvent(new GameEvent("Debug: " + chunk + " cannot be entered", 2f));
            return;
        }
        GridPoint2 arrival = WorldManager.arrivalBesideShelter(destination);
        if (arrival == null) return;
        eventManager.addEvent(new GameEvent("Debug: warping to " + label + " at " + chunk, 2f));
        eventManager.addEvent(new GameEvent(GameEvent.EventType.BIOME_PORTAL_WARP, new WorldManager.PortalWarp(chunk, arrival)));
    }

    /** Screen-space clips: self-cast glows, the casting charge, warp flashes. */
    public com.bpm.minotaur.rendering.ScreenFxOverlay getScreenFx() {
        return screenFx;
    }

    public SpellCastOverlay getSpellCastOverlay() {
        return spellCastOverlay;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public Hud getHud() {
        return hud;
    }

    public CraftingManager getCraftingManager() {
        if (craftingManager == null) {
            craftingManager = new CraftingManager(game.getItemDataManager(), game.getAssetManager());
        }
        return craftingManager;
    }

    public CombatDiceOverlay getCombatDiceOverlay() {
        return combatDiceOverlay;
    }

    public void invalidateMeshCache() {
        if (world3DRenderer != null) {
            world3DRenderer.invalidateMeshCache();
        }
    }

    /**
     * Debug kill. Routes through the normal death path, so it replays the full cinematic --
     * which is the only practical way to review the collapse without dying for real.
     */
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
        autoSaveTimer = 0f;
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

    /** Turns spent hacking at a carcass; monsters act while you work. */
    private static final int BUTCHER_TURNS = 3;

    private final java.util.Random butcherRandom = new java.util.Random();

    /** 0-99. */
    private int butcherRoll() {
        return butcherRandom.nextInt(100);
    }

    /**
     * Harvests a carcass, slowly and unreliably.
     *
     * <p>Butchering used to be instant and guaranteed: one meat and one bone
     * from every kill. With a cooking and crafting chain downstream, a
     * free-and-certain yield on every corpse makes food and reagents
     * effectively unlimited. It now costs turns, during which the level keeps
     * acting, and usually yields little.
     */
    private void butcherRemains(Scenery corpse) {
        String mName = corpse.getCorpseMonsterName() != null ? corpse.getCorpseMonsterName() : "Creature";
        boolean mangled = corpse.getType() == Scenery.SceneryType.GORE_PILE;

        if (!player.hasButcheringTool()) {
            if (hud != null) {
                hud.addMessage("You examine the remains of " + mName
                        + ". Without a bladed tool, you cannot harvest them.");
            }
            return;
        }

        int cx = (int) corpse.getPosition().x;
        int cy = (int) corpse.getPosition().y;

        if (hud != null) {
            hud.addMessage(mangled
                    ? "You pick through what is left of " + mName + "..."
                    : "You butcher the corpse of " + mName + "...");
        }

        // A torn-apart body has less worth carving, which gives the
        // dismemberment system a consequence beyond the visual.
        int meatChance = mangled ? 15 : 35;
        int boneChance = mangled ? 30 : 50;
        boolean gotAnything = false;

        try {
            if (butcherRoll() < meatChance) {
                Item meat = game.getItemDataManager().createItem(Item.ItemType.MEAT, cx, cy,
                        ItemColor.RED, game.getAssetManager());
                if (meat != null) {
                    meat.setFriendlyName("Raw " + mName + " Meat");
                    if (!player.getInventory().pickupToBackpack(meat)) maze.addItem(meat);
                    gotAnything = true;
                }
            }
            if (butcherRoll() < boneChance) {
                Item bone = game.getItemDataManager().createItem(Item.ItemType.BONE, cx, cy,
                        ItemColor.WHITE, game.getAssetManager());
                if (bone != null) {
                    if (!player.getInventory().pickupToBackpack(bone)) maze.addItem(bone);
                    gotAnything = true;
                }
            }
        } catch (Exception ignored) {
        }

        if (soundManager != null) {
            soundManager.playSound("meat_slice");
        }
        maze.removeScenery(cx, cy);

        if (hud != null && !gotAnything) {
            hud.addMessage("...nothing worth keeping.");
        }

        // Charged last, so the message order reads as cause then consequence.
        // The level keeps acting while the player works, which is the real cost.
        for (int i = 0; i < BUTCHER_TURNS; i++) {
            turnManager.processTurn(maze, player, monsterAiManager, combatManager, worldManager,
                    eventManager, game.getItemDataManager(), game.getAssetManager());
        }
    }

    private void handleCorpseInteraction(Scenery corpse) {
        // Dispatch on what the remains ARE, not on whether a field happens to be
        // null. The old test treated any corpse without bones data as a monster,
        // so a hero's bones that failed to round-trip through a save became
        // butcherable meat.
        if (corpse.isMonsterRemains()) {
            butcherRemains(corpse);
            return;
        }

        BonesData bData = corpse.getBonesData();
        if (bData == null) {
            // Hero bones with no data: damaged or from an older save. Say so
            // rather than silently reclassifying them as a carcass.
            if (hud != null) {
                hud.addMessage("The remains are too disturbed to read.");
            }
            return;
        }

        if (!bData.awakened) {
            if (hud != null && hud.getBonesAwakenModal() != null) {
                hud.getBonesAwakenModal().configureAndShow(
                        corpse, bData, maze, player, eventManager, soundManager, game.getAssetManager()
                );
            }
        } else if (!bData.defeated) {
            if (hud != null) hud.addMessage("The wrathful shade of " + bData.name() + " blocks you from disturbing the remains!");
            if (soundManager != null) {
                soundManager.playSound("player_spiritual_attack");
            }
        } else {
            GraveLootScreen lootScreen = new GraveLootScreen(game, this, player, bData);
            game.setScreen(lootScreen);
        }
    }
}
